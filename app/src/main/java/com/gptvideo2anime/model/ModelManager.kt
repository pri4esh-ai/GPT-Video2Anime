package com.gptvideo2anime.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class ModelManager(private val context: Context) {

    companion object {
        private const val MODEL_DIR = "models"

        private const val ANIME_ASSET = "models/AnimeGANv3_JP_face.onnx"
        private const val ENHANCER_ASSET = "models/RealESR-AnimeVideo-v3_x4.onnx"

        private const val ANIME_NAME = "AnimeGANv3_JP_face.onnx"
        private const val ENHANCER_NAME = "RealESR-AnimeVideo-v3_x4.onnx"

        private const val ANIME_SHA256 = ""

        private const val ENHANCER_SHA256 =
            "00ece3ac21c43ee31459216b5174b2cea0c5325044c5142aeb840f4890e175ff"
    }

    private val modelDirectory = File(context.filesDir, MODEL_DIR)
    private val animeFile = File(modelDirectory, ANIME_NAME)
    private val enhancerFile = File(modelDirectory, ENHANCER_NAME)

    fun animeModelPath() =
        if (animeFile.exists()) animeFile.absolutePath else null

    fun enhancerModelPath() =
        if (enhancerFile.exists()) enhancerFile.absolutePath else null

    fun modelStatus(): String =
        if (animeFile.exists() && enhancerFile.exists())
            "JP Face + RealESR ready"
        else
            "Installing bundled models..."

    suspend fun ensureModels(
        onLog: ((String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {

        modelDirectory.mkdirs()

        copyAssetIfNeeded(
            ANIME_ASSET,
            animeFile,
            ANIME_SHA256,
            onLog
        )

        copyAssetIfNeeded(
            ENHANCER_ASSET,
            enhancerFile,
            ENHANCER_SHA256,
            onLog
        )

        onLog?.invoke("All models are ready.")
    }

    private fun copyAssetIfNeeded(
        assetName: String,
        target: File,
        expectedSha: String,
        onLog: ((String) -> Unit)?
    ) {

        if (target.exists() && target.length() > 0L) {
            if (expectedSha.isBlank() || sha256(target).equals(expectedSha, true)) {
                onLog?.invoke("${target.name} already installed.")
                return
            }
        }

        onLog?.invoke("Installing ${target.name}...")

        val temp = File(target.parentFile, "${target.name}.part")

        context.assets.open(assetName).use { input ->
            FileOutputStream(temp).use {
                input.copyTo(it)
            }
        }

        if (expectedSha.isNotBlank()) {
            require(sha256(temp).equals(expectedSha, true))
        }

        if (target.exists()) target.delete()

        temp.renameTo(target)

        onLog?.invoke("${target.name} installed.")
    }

    private fun sha256(file: File): String {

        val digest =
            MessageDigest.getInstance("SHA-256")

        file.inputStream().use {

            val buffer =
                ByteArray(1024 * 1024)

            while (true) {

                val count =
                    it.read(buffer)

                if (count < 0) break

                if (count > 0)
                    digest.update(buffer, 0, count)
            }
        }

        return digest.digest().joinToString("") {
            "%02x".format(it)
        }
    }
}
