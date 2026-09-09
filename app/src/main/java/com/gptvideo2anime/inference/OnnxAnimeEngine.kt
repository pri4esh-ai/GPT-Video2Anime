package com.gptvideo2anime.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.TensorInfo
import android.graphics.Bitmap
import java.nio.FloatBuffer

class OnnxAnimeEngine(
    modelPath: String
) : AutoCloseable {

    private val env = OrtEnvironment.getEnvironment()
    private val session = env.createSession(modelPath)

    fun processFrame(frame: Bitmap): Bitmap {

        val inputName = session.inputNames.first()
        val info = session.inputInfo[inputName]!!.info as TensorInfo
        val inputShape = info.shape

        val nchw = inputShape[1] == 3L

        // JP Face uses dynamic dimensions (-1)
        val h = if (nchw) {
            if (inputShape[2] > 0) inputShape[2].toInt() else 512
        } else {
            if (inputShape[1] > 0) inputShape[1].toInt() else 512
        }

        val w = if (nchw) {
            if (inputShape[3] > 0) inputShape[3].toInt() else 512
        } else {
            if (inputShape[2] > 0) inputShape[2].toInt() else 512
        }

        val resized = Bitmap.createScaledBitmap(frame, w, h, true)

        val tensorData =
            if (nchw) bitmapToNCHW(resized)
            else bitmapToNHWC(resized)

        val tensorShape =
            if (nchw)
                longArrayOf(1L, 3L, h.toLong(), w.toLong())
            else
                longArrayOf(1L, h.toLong(), w.toLong(), 3L)

        resized.recycle()

        OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(tensorData),
            tensorShape
        ).use { input ->

            session.run(mapOf(inputName to input)).use { result ->

                return outputBitmap(
                    result[0].value,
                    frame.width,
                    frame.height,
                    w,
                    h
                )
            }
        }
    }

    private fun bitmapToNCHW(bitmap: Bitmap): FloatArray {

        val w = bitmap.width
        val h = bitmap.height

        val pixels = IntArray(w * h)

        bitmap.getPixels(
            pixels,
            0,
            w,
            0,
            0,
            w,
            h
        )

        val out = FloatArray(w * h * 3)

        for (i in pixels.indices) {

            val p = pixels[i]

            out[i] = (((p shr 16) and 255) / 255f) * 2f - 1f
            out[w * h + i] = (((p shr 8) and 255) / 255f) * 2f - 1f
            out[w * h * 2 + i] = ((p and 255) / 255f) * 2f - 1f
        }

        return out
    }

    private fun bitmapToNHWC(bitmap: Bitmap): FloatArray {

        val w = bitmap.width
        val h = bitmap.height

        val pixels = IntArray(w * h)

        bitmap.getPixels(
            pixels,
            0,
            w,
            0,
            0,
            w,
            h
        )

        val out = FloatArray(w * h * 3)

        var j = 0

        for (p in pixels) {

            out[j++] = (((p shr 16) and 255) / 255f) * 2f - 1f
            out[j++] = (((p shr 8) and 255) / 255f) * 2f - 1f
            out[j++] = ((p and 255) / 255f) * 2f - 1f
        }

        return out
    }

    private fun outputBitmap(
        value: Any,
        outW: Int,
        outH: Int,
        w: Int,
        h: Int
    ): Bitmap {

        val data = (value as Array<Array<Array<FloatArray>>>)[0]

        val pixels = IntArray(w * h)

        var i = 0

        for (y in 0 until h) {

            for (x in 0 until w) {

                val r =
                    ((data[y][x][0] + 1f) * 127.5f)
                        .toInt()
                        .coerceIn(0, 255)

                val g =
                    ((data[y][x][1] + 1f) * 127.5f)
                        .toInt()
                        .coerceIn(0, 255)

                val b =
                    ((data[y][x][2] + 1f) * 127.5f)
                        .toInt()
                        .coerceIn(0, 255)

                pixels[i++] =
                    -0x1000000 or
                            (r shl 16) or
                            (g shl 8) or
                            b
            }
        }

        val bmp =
            Bitmap.createBitmap(
                w,
                h,
                Bitmap.Config.ARGB_8888
            )

        bmp.setPixels(
            pixels,
            0,
            w,
            0,
            0,
            w,
            h
        )

        val scaled =
            Bitmap.createScaledBitmap(
                bmp,
                outW,
                outH,
                true
            )

        bmp.recycle()

        return scaled
    }

    override fun close() {
        session.close()
    }
}
