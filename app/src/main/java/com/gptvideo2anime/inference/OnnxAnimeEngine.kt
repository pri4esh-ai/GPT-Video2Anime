package com.gptvideo2anime.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import java.nio.FloatBuffer

class OnnxAnimeEngine(
    modelPath: String
) : AutoCloseable {

    private val environment =
        OrtEnvironment.getEnvironment()

    private val session =
        environment.createSession(modelPath)

    fun inputNames(): Set<String> =
        session.inputNames

    fun outputNames(): Set<String> =
        session.outputNames

    fun processFrame(
        frame: Bitmap
    ): Bitmap {

        require(!frame.isRecycled) {
            "Input frame is recycled."
        }

        val inputName =
            session.inputNames.firstOrNull()
                ?: throw IllegalStateException(
                    "ONNX model has no input."
                )

        val inputInfo =
            session.inputInfo[inputName]
                ?: throw IllegalStateException(
                    "Unable to inspect ONNX input: $inputName"
                )

        val tensorInfo =
            inputInfo.info as? TensorInfo
                ?: throw IllegalStateException(
                    "ONNX input is not a tensor: $inputName"
                )

        val inputShape =
            tensorInfo.shape

        require(inputShape.size == 4) {
            "Unsupported ONNX input rank: " +
                inputShape.size
        }

        val modelHeight =
            resolveDimension(
                inputShape[2],
                512
            )

        val modelWidth =
            resolveDimension(
                inputShape[3],
                512
            )

        require(
            modelWidth > 0 &&
                modelHeight > 0
        ) {
            "Invalid ONNX input dimensions: " +
                "${modelWidth}x${modelHeight}"
        }

        val resized =
            Bitmap.createScaledBitmap(
                frame,
                modelWidth,
                modelHeight,
                true
            )

        try {

            val tensorData =
                bitmapToNchwFloatArray(
                    resized
                )

            val shape =
                longArrayOf(
                    1L,
                    3L,
                    modelHeight.toLong(),
                    modelWidth.toLong()
                )

            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(
                    tensorData
                ),
                shape
            ).use { inputTensor ->

                session.run(
                    mapOf(
                        inputName to inputTensor
                    )
                ).use { result ->

                    require(result.size() > 0) {
                        "ONNX model returned no output."
                    }

                    return outputToBitmap(
                        result[0].value,
                        frame.width,
                        frame.height,
                        modelWidth,
                        modelHeight
                    )
                }
            }

        } finally {

            if (!resized.isRecycled) {
                resized.recycle()
            }
        }
    }

    private fun bitmapToNchwFloatArray(
        bitmap: Bitmap
    ): FloatArray {

        val width =
            bitmap.width

        val height =
            bitmap.height

        val pixelCount =
            width * height

        val pixels =
            IntArray(pixelCount)

        bitmap.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        val data =
            FloatArray(
                pixelCount * 3
            )

        for (i in 0 until pixelCount) {

            val pixel =
                pixels[i]

            val red =
                ((pixel shr 16) and 0xFF) /
                    255.0f

            val green =
                ((pixel shr 8) and 0xFF) /
                    255.0f

            val blue =
                (pixel and 0xFF) /
                    255.0f

            data[i] =
                red * 2.0f - 1.0f

            data[pixelCount + i] =
                green * 2.0f - 1.0f

            data[pixelCount * 2 + i] =
                blue * 2.0f - 1.0f
        }

        return data
    }

    private fun outputToBitmap(
        output: Any,
        outputWidth: Int,
        outputHeight: Int,
        modelWidth: Int,
        modelHeight: Int
    ): Bitmap {

        val data =
            extractFloatArray(output)

        val pixelCount =
            modelWidth * modelHeight

        require(
            data.size >=
                pixelCount * 3
        ) {
            "Unsupported ONNX output size: " +
                data.size
        }

        val pixels =
            IntArray(pixelCount)

        for (i in 0 until pixelCount) {

            val r =
                outputValueToByte(
                    data[i]
                )

            val g =
                outputValueToByte(
                    data[pixelCount + i]
                )

            val b =
                outputValueToByte(
                    data[pixelCount * 2 + i]
                )

            pixels[i] =
                (255 shl 24) or
                (r shl 16) or
                (g shl 8) or
                b
        }

        val modelBitmap =
            Bitmap.createBitmap(
                modelWidth,
                modelHeight,
                Bitmap.Config.ARGB_8888
            )

        modelBitmap.setPixels(
            pixels,
            0,
            modelWidth,
            0,
            0,
            modelWidth,
            modelHeight
        )

        if (
            modelWidth == outputWidth &&
            modelHeight == outputHeight
        ) {
            return modelBitmap
        }

        val result =
            Bitmap.createScaledBitmap(
                modelBitmap,
                outputWidth,
                outputHeight,
                true
            )

        modelBitmap.recycle()

        return result
    }

    private fun extractFloatArray(
        value: Any
    ): FloatArray {

        return when (value) {

            is FloatArray ->
                value

            is Array<*> -> {

                val result =
                    ArrayList<Float>()

                fun visit(
                    item: Any?
                ) {

                    when (item) {

                        is FloatArray ->
                            item.forEach(
                                result::add
                            )

                        is Array<*> ->
                            item.forEach(
                                ::visit
                            )

                        is Number ->
                            result.add(
                                item.toFloat()
                            )

                        null -> Unit

                        else ->
                            throw IllegalStateException(
                                "Unsupported ONNX " +
                                    "output element: " +
                                    item::class.java.name
                            )
                    }
                }

                visit(value)

                result.toFloatArray()
            }

            else ->
                throw IllegalStateException(
                    "Unsupported ONNX output type: " +
                        value::class.java.name
                )
        }
    }

    private fun resolveDimension(
        dimension: Long,
        fallback: Int
    ): Int {

        return if (
            dimension > 0L
        ) {
            dimension.toInt()
        } else {
            fallback
        }
    }

    private fun outputValueToByte(
        value: Float
    ): Int {

        val normalized =
            (value + 1.0f) * 0.5f

        return (
            normalized.coerceIn(
                0.0f,
                1.0f
            ) * 255.0f
        ).toInt()
    }

    override fun close() {
        session.close()
    }
}
