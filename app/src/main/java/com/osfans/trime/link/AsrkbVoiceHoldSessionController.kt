/*
 * SPDX-FileCopyrightText: 2026 BryceWG
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.toast
import kotlinx.coroutines.launch

/**
 * Shared controller for the ASRKB hold-to-talk session and overlay callbacks.
 * It keeps the UI bridge wiring out of keyboard/tool-bar entry points so future merges stay smaller.
 */
internal class AsrkbVoiceHoldSessionController(
    private val service: TrimeInputMethodService,
    private val showOverlay: () -> Unit,
    private val startWave: () -> Unit,
    private val updateAmplitude: (Float) -> Unit,
    private val hideOverlay: () -> Unit,
    private val onSessionFinished: (() -> Unit)? = null,
) {
    private var startedByController = false

    /**
     * The bundled on-device SenseVoice model is preferred: it runs entirely in this process and
     * is immune to the external ASR app crashing. The AIDL client remains as a fallback for
     * builds that ship without the model assets.
     */
    private val useBuiltinEngine: Boolean
        get() = SenseVoiceModel.isBundled(service)

    fun isRunning(): Boolean = startedByController

    fun start() {
        if (startedByController) return

        val executor = ContextCompat.getMainExecutor(service)
        val builtin = useBuiltinEngine

        showOverlay()
        startedByController = true

        VoiceOverlayUiBridge.onRecordingStarted = {
            executor.execute {
                startWave()
            }
        }
        VoiceOverlayUiBridge.onAmplitude = { amplitude ->
            executor.execute {
                updateAmplitude(amplitude)
            }
        }
        VoiceOverlayUiBridge.onDone = {
            startedByController = false
            executor.execute {
                hideOverlay()
                onSessionFinished?.invoke()
            }
            VoiceOverlayUiBridge.clear()
        }

        if (builtin) {
            // First use copies ~228 MB out of the APK; only start recording once ready so the
            // very first hold does not silently produce nothing.
            service.lifecycleScope.launch {
                val ready = SenseVoiceModel.ensureExtracted(service)
                if (!startedByController) return@launch
                if (ready) {
                    SenseVoiceEngine.startHoldSession(service)
                } else {
                    service.toast(com.osfans.trime.R.string.voice_model_missing)
                    hideOverlay()
                    onSessionFinished?.invoke()
                    VoiceOverlayUiBridge.clear()
                    startedByController = false
                }
            }
        } else {
            AsrkbSpeechClient.startHoldSession(service)
        }
    }

    fun stopIfStarted() {
        if (!startedByController) return
        stop()
    }

    fun stop() {
        startedByController = false
        hideOverlay()
        VoiceOverlayUiBridge.clear()
        onSessionFinished?.invoke()
        if (SenseVoiceEngine.isHolding()) {
            SenseVoiceEngine.stopHoldSession()
        }
        if (AsrkbSpeechClient.isHolding()) {
            AsrkbSpeechClient.stopHoldSession()
        }
    }
}
