/*
 * SPDX-FileCopyrightText: 2026 BryceWG
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.osfans.trime.R
import com.osfans.trime.data.prefs.AppPrefs
import com.osfans.trime.ime.core.TrimeInputMethodService
import com.osfans.trime.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Fully in-process speech recognizer backed by SenseVoice (sherpa-onnx).
 *
 * Everything — audio capture, buffering and decoding — happens inside this process:
 * there is no Binder hop to an external ASR app. SenseVoice is a non-streaming model,
 * so text is produced once recording stops and is committed straight into the editor.
 */
internal object SenseVoiceEngine {
    @Volatile
    var onHoldingChanged: ((Boolean) -> Unit)? = null

    private const val SAMPLE_RATE = 16000
    private const val BYTES_PER_SAMPLE = 2

    /** Ignore clips shorter than this; decoding them yields noise or empty output. */
    private const val MIN_AUDIO_MS = 300

    @Volatile
    private var holding: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            val listener = onHoldingChanged ?: return
            runCatching { listener.invoke(value) }
                .onFailure { Timber.w(it, "onHoldingChanged failed") }
        }

    private var ctxRef: TrimeInputMethodService? = null
    private var audioJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var decodeJob: Job? = null
    private val recordingAudioFocusOwner = AsrkbRecordingAudioFocusSessionOwner()

    private var pcmBuffer: ByteArrayOutputStream? = null

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    @Volatile
    private var recognizerLoading: Boolean = false

    fun isHolding(): Boolean = holding

    fun isModelAvailable(service: TrimeInputMethodService): Boolean =
        SenseVoiceModel.filesExist(service)

    fun startHoldSession(service: TrimeInputMethodService) {
        if (holding) return

        if (!isModelAvailable(service)) {
            service.toast(R.string.voice_model_missing)
            return
        }

        ctxRef = service
        holding = true
        pcmBuffer = ByteArrayOutputStream(SAMPLE_RATE * BYTES_PER_SAMPLE * 8)
        warmUpRecognizer(service)
        startAudioStreaming(service)
    }

    fun stopHoldSession() {
        if (!holding) return
        holding = false

        stopAudioStreaming()
        runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
        VoiceOverlayUiBridge.clear()

        val service = ctxRef ?: return
        val pcm = pcmBuffer?.toByteArray() ?: ByteArray(0)
        pcmBuffer = null

        val minBytes = SAMPLE_RATE * BYTES_PER_SAMPLE * MIN_AUDIO_MS / 1000
        if (pcm.size < minBytes) {
            Timber.d("SenseVoice skip: audio too short (${pcm.size} bytes)")
            ctxRef = null
            return
        }

        decodeJob?.cancel()
        decodeJob = service.lifecycleScope.launch {
            val text = try {
                decode(service, pcm)
            } catch (t: Throwable) {
                // Decoding must never take down the IME; just report and continue.
                Timber.e(t, "SenseVoice decode failed")
                null
            }
            ctxRef = null
            if (!text.isNullOrBlank()) {
                service.commitText(text.trim())
            } else {
                Timber.d("SenseVoice decode produced empty text")
            }
        }
    }

    internal fun onServiceDestroyed(service: TrimeInputMethodService) {
        if (ctxRef !== service) return
        cancel()
    }

    /** Drop any in-flight session without committing text. */
    fun cancel() {
        holding = false
        decodeJob?.cancel()
        decodeJob = null
        stopAudioStreaming()
        pcmBuffer = null
        ctxRef = null
        runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
        VoiceOverlayUiBridge.clear()
    }

    // ---------------------------------------------------------------- model

    private fun warmUpRecognizer(service: TrimeInputMethodService) {
        if (recognizer != null || recognizerLoading) return
        recognizerLoading = true
        service.lifecycleScope.launch(Dispatchers.Default) {
            try {
                val started = System.currentTimeMillis()
                recognizer = createRecognizer(service)
                Timber.d("SenseVoice model loaded in ${System.currentTimeMillis() - started} ms")
            } catch (t: Throwable) {
                Timber.e(t, "SenseVoice model load failed")
            } finally {
                recognizerLoading = false
            }
        }
    }

    private fun createRecognizer(service: TrimeInputMethodService): OfflineRecognizer {
        val config =
            OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig =
                    OfflineModelConfig(
                        senseVoice =
                            OfflineSenseVoiceModelConfig(
                                model = SenseVoiceModel.modelPath(service),
                                language = "auto",
                                useInverseTextNormalization = true,
                            ),
                        tokens = SenseVoiceModel.tokensPath(service),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu",
                    ),
            )
        return OfflineRecognizer(config = config, assetManager = null)
    }

    private suspend fun decode(
        service: TrimeInputMethodService,
        pcm: ByteArray,
    ): String? = withContext(Dispatchers.Default) {
        val rec = recognizer ?: createRecognizer(service).also { recognizer = it }
        val samples = pcm16ToFloat(pcm)
        val started = System.currentTimeMillis()
        val stream = rec.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            rec.decode(stream)
            val result = rec.getResult(stream)
            Timber.d(
                "SenseVoice decoded ${samples.size} samples " +
                    "in ${System.currentTimeMillis() - started} ms",
            )
            result.text
        } finally {
            runCatching { stream.release() }
        }
    }

    private fun pcm16ToFloat(pcm: ByteArray): FloatArray {
        val count = pcm.size / BYTES_PER_SAMPLE
        val out = FloatArray(count)
        for (i in 0 until count) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            val s = ((hi shl 8) or lo).toShort().toInt()
            out[i] = s / 32768f
        }
        return out
    }

    // ---------------------------------------------------------------- audio

    private fun startAudioStreaming(service: TrimeInputMethodService) {
        stopAudioStreaming()

        val granted =
            ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) {
            runCatching {
                service.startActivity(
                    Intent(service, MicPermissionActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { Timber.w(it, "Failed to start MicPermissionActivity") }
            service.toast(R.string.asrkb_client_need_mic_permission)
            holding = false
            ctxRef = null
            return
        }

        acquireRecordingAudioFocusIfEnabled(service)

        audioJob =
            service.lifecycleScope.launch(Dispatchers.IO) {
                val minBuf =
                    AudioRecord.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                val chunkBytes = (SAMPLE_RATE * 200 / 1000) * BYTES_PER_SAMPLE
                val bufSize = max(minBuf, chunkBytes * 2)

                fun create(source: Int): AudioRecord? {
                    val rec =
                        try {
                            AudioRecord(
                                source,
                                SAMPLE_RATE,
                                AudioFormat.CHANNEL_IN_MONO,
                                AudioFormat.ENCODING_PCM_16BIT,
                                bufSize,
                            )
                        } catch (t: Throwable) {
                            Timber.w(t, "AudioRecord construct failed (source=$source)")
                            null
                        }
                    if (rec == null) return null
                    if (rec.state != AudioRecord.STATE_INITIALIZED) {
                        Timber.w("AudioRecord not initialized (source=$source, state=${rec.state})")
                        runCatching { rec.release() }
                        return null
                    }
                    return rec
                }

                var source = MediaRecorder.AudioSource.VOICE_RECOGNITION
                var rec =
                    create(source) ?: run {
                        source = MediaRecorder.AudioSource.MIC
                        create(source)
                    }
                if (rec == null) {
                    service.lifecycleScope.launch { service.toast(R.string.asrkb_err_audio_record_failed) }
                    abortSession()
                    return@launch
                }

                audioRecord = rec
                try {
                    rec.startRecording()
                } catch (t: Throwable) {
                    if (source == MediaRecorder.AudioSource.MIC) {
                        Timber.e(t, "AudioRecord MIC failed")
                        service.lifecycleScope.launch { service.toast(R.string.asrkb_err_audio_record_failed) }
                        abortSession()
                        return@launch
                    }
                    Timber.w(t, "AudioRecord start failed, fallback MIC")
                    runCatching { rec.release() }
                    source = MediaRecorder.AudioSource.MIC
                    val micRec = create(source)
                    if (micRec == null) {
                        service.lifecycleScope.launch { service.toast(R.string.asrkb_err_audio_record_failed) }
                        abortSession()
                        return@launch
                    }
                    audioRecord = micRec
                    rec = micRec
                    try {
                        rec.startRecording()
                    } catch (e: Throwable) {
                        Timber.e(e, "AudioRecord MIC failed")
                        service.lifecycleScope.launch { service.toast(R.string.asrkb_err_audio_record_failed) }
                        abortSession()
                        return@launch
                    }
                }

                val chunk = ByteArray(chunkBytes)
                var started = false
                while (holding) {
                    val n =
                        try {
                            audioRecord?.read(chunk, 0, chunk.size) ?: -1
                        } catch (t: Throwable) {
                            Timber.w(t, "AudioRecord read failed")
                            -1
                        }
                    if (n < 0) break
                    if (n == 0) {
                        delay(10)
                        continue
                    }
                    if (!started) {
                        started = true
                        runCatching { VoiceOverlayUiBridge.onRecordingStarted?.invoke() }
                    }
                    runCatching { pcmBuffer?.write(chunk, 0, n) }
                    emitAmplitude(chunk, n)
                }
            }
    }

    private fun abortSession() {
        holding = false
        ctxRef = null
        pcmBuffer = null
        runCatching { VoiceOverlayUiBridge.onDone?.invoke() }
        VoiceOverlayUiBridge.clear()
    }

    private fun emitAmplitude(
        buf: ByteArray,
        len: Int,
    ) {
        val callback = VoiceOverlayUiBridge.onAmplitude ?: return
        val count = len / BYTES_PER_SAMPLE
        if (count <= 0) return
        var sum = 0.0
        for (i in 0 until count) {
            val lo = buf[i * 2].toInt() and 0xFF
            val hi = buf[i * 2 + 1].toInt()
            val s = ((hi shl 8) or lo).toShort().toFloat() / 32768f
            sum += s * s
        }
        val rms = sqrt(sum / count).toFloat()
        runCatching { callback(min(1f, rms * 4f)) }
    }

    private fun stopAudioStreaming() {
        audioJob?.cancel()
        audioJob = null

        val rec = audioRecord
        if (rec != null) {
            runCatching { rec.stop() }.onFailure { Timber.w(it, "AudioRecord stop failed") }
            runCatching { rec.release() }.onFailure { Timber.w(it, "AudioRecord release failed") }
            audioRecord = null
        }

        recordingAudioFocusOwner.release()
    }

    private fun acquireRecordingAudioFocusIfEnabled(service: TrimeInputMethodService) {
        val enabled = AppPrefs.defaultInstance().general.asrkbDuckMediaOnRecordEnabled.getValue()
        if (!enabled) return

        val executor = ContextCompat.getMainExecutor(service)
        lateinit var controller: AsrkbRecordingAudioFocusController
        controller =
            AsrkbRecordingAudioFocusController(service) { loss ->
                Timber.w("SenseVoice recording audio focus lost: $loss")
                executor.execute {
                    if (recordingAudioFocusOwner.owns(controller) && holding) {
                        stopHoldSession()
                    }
                }
            }
        if (!recordingAudioFocusOwner.acquire(controller)) {
            Timber.w("SenseVoice recording continues without audio focus")
        }
    }
}
