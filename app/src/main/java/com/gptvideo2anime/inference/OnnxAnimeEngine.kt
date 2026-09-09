package com.gptvideo2anime.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import java.nio.FloatBuffer

class OnnxAnimeEngine(
    modelPath: String
) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = environment.createSession(modelPath)

    fun inputNames(): Set<String> = session.inputNames

    fun outputNames(): Set<String> = session.outputNames

    fun processFrame(frame: Bitmap): Bitmap {
        require(!frame.isRecycled) { "Input frame is recycled." }

        val inputName = session.inputNames.firstOrNull()
            ?: throw IllegalStateException("ONNX model has no input.")

        val width = frame.width
        val height = frame.height

        val pixels = IntArray(width * height)
        frame.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        val floatData = FloatArray(width * height * 3)
        var offset = 0

        for (pixel in pixels) {
            val red = ((pixel shr 16) and 0xFF) / 255.0f
            val green = ((pixel shr 8) and 0xFF) / 255.0f
            val blue = (pixel and 0xFF) / 255.0f

            floatData[offset] = red
            floatData[offset + width * height] = green
            floatData[offset + 2 * width * height] = blue
            offset++
        }

        val inputShape = longArrayOf(
            1L,
            3L,
            height.toLong(),
            width.toLong()
        )

        val inputTensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(floatData),
            inputShape
        )

        inputTensor.use { tensor ->
            session.run(
                mapOf(inputName to tensor)
            ).use { result ->
                return tensorToBitmap(
                    result[0].value,
                    width,
                    height
                )
            }
        }
    }

    private fun tensorToBitmap(
        output: Any,
        width: Int,
        height: Int
    ): Bitmap {
        val data = extractFloatArray(output)

        val expectedPixels = width * height

        require(data.size >= expectedPixels * 3) {
            "Unsupported ONNX output size: ${data.size}"
        }

        val bitmap = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        val pixels = IntArray(expectedPixels)

        for (i in 0 until expectedPixels) {
            val r = toByte(data[i])
            val g = toByte(data[expectedPixels + i])
            val b = toByte(data[expectedPixels * 2 + i])

            pixels[i] =
                (0xFF shl 24) or
                (r shl 16) or
                (g shl 8) or
                b
        }

        bitmap.setPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        return bitmap
    }

    private fun extractFloatArray(value: Any): FloatArray {
        return when (value) {
            is Array<*> -> flattenArray(value)
            is FloatArray -> value
            else -> throw IllegalStateException(
                "Unsupported ONNX output type: ${value::class.java.name}"
            )
        }
    }

    private fun flattenArray(value: Array<*>): FloatArray {
        val result = ArrayList<Float>()

        fun visit(item: Any?) {
            when (item) {
                is FloatArray -> {
                    for (number in item) {
                        result.add(number)
                    }
                }

                is Array<*> -> {
                    for (child in item) {
                        visit(child)
                    }
                }

                is Number -> result.add(item.toFloat())

                null -> Unit

                else -> throw IllegalStateException(
                    "Unsupported ONNX tensor element: ${item::class.java.name}"
                )
            }
        }

        visit(value)

        return result.toFloatArray()
    }

    private fun toByte(value: Float): Int {
        val normalized = when {
            value in 0.0f..1.0f -> value
            else -> (value + 1.0f) * 0.5f
        }

        return (normalized.coerceIn(0.0f, 1.0f) * 255.0f)
            .toInt()
    }

    override fun close() {
        session.close()
    }
}
