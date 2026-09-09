package com.gptvideo2anime.inference

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

class OnnxAnimeEngine(
    modelPath: String
) : AutoCloseable {

    private val environment =
        OrtEnvironment.getEnvironment()

    private val session =
        environment.createSession(
            modelPath
        )

    fun inputNames():
        Set<String> {

        return session.inputNames
    }

    fun outputNames():
        Set<String> {

        return session.outputNames
    }

    override fun close() {

        session.close()
    }
}
