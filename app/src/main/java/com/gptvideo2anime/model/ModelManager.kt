package com.gptvideo2anime.model

import android.content.Context
import java.io.File

class ModelManager(
    private val context: Context
) {

    private val modelDirectory =
        File(
            context.filesDir,
            "models"
        )

    private val modelFile =
        File(
            modelDirectory,
            "anime.onnx"
        )

    fun modelStatus(): String {

        return if (
            modelFile.exists() &&
            modelFile.length() > 0
        ) {
            "anime.onnx installed"
        } else {
            "anime.onnx not installed"
        }
    }

    fun modelPath(): String? {

        return if (
            modelFile.exists() &&
            modelFile.length() > 0
        ) {
            modelFile.absolutePath
        } else {
            null
        }
    }

    fun installModel(
        source: File
    ) {

        modelDirectory.mkdirs()

        source.copyTo(
            modelFile,
            overwrite = true
        )
    }
}
