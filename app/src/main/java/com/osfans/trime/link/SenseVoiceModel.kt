/*
 * SPDX-FileCopyrightText: 2026 BryceWG
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.link

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Locates and materializes the bundled SenseVoice model.
 *
 * sherpa-onnx loads models from real file paths, so the assets shipped inside the APK are
 * copied into private storage once. The copy is skipped when a file with the expected size
 * already exists.
 *
 * Asset layout: assets/sensevoice/{model.int8.onnx, tokens.txt}
 */
internal object SenseVoiceModel {
    private const val ASSET_DIR = "sensevoice"
    private const val MODEL_NAME = "model.int8.onnx"
    private const val TOKENS_NAME = "tokens.txt"
    private const val TARGET_DIR = "sensevoice"

    fun modelPath(context: Context): String = File(targetDir(context), MODEL_NAME).absolutePath

    fun tokensPath(context: Context): String = File(targetDir(context), TOKENS_NAME).absolutePath

    /**
     * True when the model is either already extracted or bundled in the APK.
     * This decides whether the built-in engine can be used at all.
     */
    fun isBundled(context: Context): Boolean =
        (extractedFilesPresent(context) || assetsPresent(context)) && modelAssetSize(context) > 0

    /** True when both extracted files exist and are non-empty. */
    fun filesExist(context: Context): Boolean = extractedFilesPresent(context)

    /**
     * Copies the bundled assets into private storage. Returns true when the model is ready.
     * Safe to call repeatedly.
     */
    suspend fun ensureExtracted(context: Context): Boolean =
        withContext(Dispatchers.IO) {
            if (extractedFilesPresent(context)) return@withContext true

            val dir = targetDir(context).apply { mkdirs() }
            val ok =
                runCatching {
                    copyAssetIfNeeded(context, MODEL_NAME, File(dir, MODEL_NAME))
                    copyAssetIfNeeded(context, TOKENS_NAME, File(dir, TOKENS_NAME))
                }.onFailure {
                    Timber.e(it, "SenseVoice model extraction failed")
                }.isSuccess
            ok && extractedFilesPresent(context)
        }

    private fun targetDir(context: Context): File = File(context.filesDir, TARGET_DIR)

    private fun extractedFilesPresent(context: Context): Boolean {
        val dir = targetDir(context)
        return File(dir, MODEL_NAME).let { it.isFile && it.length() > 0 } &&
            File(dir, TOKENS_NAME).let { it.isFile && it.length() > 0 }
    }

    private fun assetsPresent(context: Context): Boolean =
        runCatching {
            context.assets.open("$ASSET_DIR/$MODEL_NAME").close()
            context.assets.open("$ASSET_DIR/$TOKENS_NAME").close()
            true
        }.getOrDefault(false)

    /** Uncompressed asset length, or -1 when the asset is missing. Cached after first probe. */
    @Volatile
    private var cachedModelAssetSize: Long? = null

    private fun modelAssetSize(context: Context): Long {
        cachedModelAssetSize?.let { return it }
        val size =
            runCatching {
                context.assets.openFd("$ASSET_DIR/$MODEL_NAME").use { it.declaredLength }
            }.getOrDefault(-1L)
        if (size > 0) cachedModelAssetSize = size
        return size
    }

    private fun copyAssetIfNeeded(
        context: Context,
        name: String,
        target: File,
    ) {
        if (target.isFile && target.length() > 0) return
        context.assets.open("$ASSET_DIR/$name").use { input ->
            target.outputStream().use { output ->
                input.copyTo(output, 1024 * 1024)
            }
        }
        Timber.d("SenseVoice extracted $name (${target.length()} bytes)")
    }
}
