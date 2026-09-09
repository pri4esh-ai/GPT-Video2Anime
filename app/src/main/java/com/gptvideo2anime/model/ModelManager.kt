package com.gptvideo2anime.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ModelManager(private val context: Context) {

    companion object {

        private const val MODEL_DIR = "models"

        private const val ANIME_ASSET =
            "models/AnimeGANv3_JP_face.onnx"

        private const val ENHANCER_ASSET =
            "models/RealESR-AnimeVideo-v3_x4.onnx"

        private const val ANIME_NAME =
            "AnimeGANv3_JP_face.onnx"

        private const val ENHANCER_NAME =
            "RealESR-AnimeVideo-v3_x4.onnx"
    }

    private val modelDirectory =
        File(context.filesDir, MODEL_DIR)

    private val animeFile =
        File(modelDirectory, ANIME_NAME)

    private val enhancerFile =
        File(modelDirectory, ENHANCER_NAME)

    fun animeModelPath(): String? =
        if (animeFile.exists()) animeFile.absolutePath else null

    fun enhancerModelPath(): String? =
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

        copyIfMissing(
            ANIME_ASSET,
            animeFile,
            onLog
        )

        copyIfMissing(
            ENHANCER_ASSET,
            enhancerFile,
            onLog
        )

        onLog?.invoke("All models are ready.")
    }

    private fun copyIfMissing(
        asset: String,
        target: File,
        onLog: ((String) -> Unit)?
    ) {

        if (target.exists() && target.length() > 0L) {

            onLog?.invoke("${target.name} already installed.")
            return
        }

        onLog?.invoke("Installing ${target.name}...")

        val temp =
            File(target.parentFile, "${target.name}.part")

        context.assets.open(asset).use { input ->

            FileOutputStream(temp).use { output ->

                input.copyTo(output)
            }
        }

        if (target.exists()) {
            target.delete()
        }

        if (!temp.renameTo(target)) {

            temp.copyTo(target, overwrite = true)
            temp.delete()
        }

        onLog?.invoke("${target.name} installed.")
    }
}
