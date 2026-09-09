package com.gptvideo2anime.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.gptvideo2anime.inference.OnnxAnimeEngine
import com.gptvideo2anime.model.ModelManager
import java.io.File

class VideoProcessor(
    private val context: Context
) {

    private val codecEngine =
        MediaCodecVideoEngine(context)

    private val modelManager =
        ModelManager(context)

    data class ProcessingInfo(
        val width: Int,
        val height: Int,
        val frameRate: Int,
        val durationUs: Long,
        val mime: String,
        val decoderAvailable: Boolean,
        val encoderAvailable: Boolean,
        val encoderMime: String,
        val testFramePath: String?
    )

    fun process(
        uri: Uri,
        onProgress: (
            current: Int,
            total: Int,
            stage: String
        ) -> Unit
    ): ProcessingInfo {

        val total = 7

        onProgress(0, total, "Opening video")

        val info = codecEngine.inspect(uri)

        onProgress(1, total, "Checking decoder")

        codecEngine.findDecoder(info.mime)
            ?: throw IllegalStateException("No decoder")

        val encoderMime =
            codecEngine.bestEncoderMime(info.mime)

        onProgress(2, total, "Checking encoder")

        codecEngine.findEncoder(encoderMime)
            ?: throw IllegalStateException("No encoder")

        val model =
            modelManager.animeModelPath()
                ?: throw IllegalStateException("JP Face model missing.")

        onProgress(3, total, "Extracting first frame")

        val frame =
            extractFirstFrame(uri)

        onProgress(4, total, "Running JP Face")

        val output =
            OnnxAnimeEngine(model).use {
                it.processFrame(frame)
            }

        onProgress(5, total, "Saving preview")

        val dir =
            File(context.filesDir, "stage1")

        dir.mkdirs()

        val file =
            File(dir, "anime_test_frame.png")

        try {

            file.outputStream().use {
                output.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    it
                )
            }

        } finally {

            output.recycle()
            frame.recycle()
        }

        Log.i("VideoProcessor", "Saved ${file.absolutePath}")

        onProgress(6, total, "Finalizing")

        onProgress(7, total, "Complete")

        return ProcessingInfo(
            info.width,
            info.height,
            info.frameRate,
            info.durationUs,
            info.mime,
            true,
            true,
            encoderMime,
            file.absolutePath
        )
    }

    private fun extractFirstFrame(uri: Uri): Bitmap {

        val retriever =
            MediaMetadataRetriever()

        try {

            retriever.setDataSource(context, uri)

            return retriever.getFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: throw IllegalStateException("No frame")

        } finally {

            retriever.release()
        }
    }
}
