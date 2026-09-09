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

        Log.i("VideoProcessor", "Stage 1: Opening video")
        onProgress(0, 7, "Opening video")

        val info =
            codecEngine.inspect(uri)

        Log.i(
            "VideoProcessor",
            "Video ${info.width}x${info.height} ${info.frameRate} FPS"
        )

        onProgress(1, 7, "Finding decoder")

        val decoder =
            codecEngine.findDecoder(info.mime)

        check(decoder != null) {
            "No decoder for ${info.mime}"
        }

        val encoderMime =
            codecEngine.bestEncoderMime(info.mime)

        onProgress(2, 7, "Finding encoder")

        val encoder =
            codecEngine.findEncoder(encoderMime)

        check(encoder != null) {
            "No encoder for $encoderMime"
        }

        val modelPath =
            modelManager.animeModelPath()
                ?: throw IllegalStateException(
                    "AnimeGANv3 model missing."
                )

        onProgress(3, 7, "Extracting first frame")

        val frame =
            extractFirstFrame(uri)

        Log.i("VideoProcessor", "First frame extracted")

        onProgress(4, 7, "Running AnimeGANv3")

        val animeFrame =
            OnnxAnimeEngine(modelPath).use {
                it.processFrame(frame)
            }

        Log.i("VideoProcessor", "AnimeGAN finished")

        onProgress(5, 7, "Saving preview")

        val outputDir =
            File(context.filesDir, "stage1")

        outputDir.mkdirs()

        val outputFile =
            File(
                outputDir,
                "anime_test_frame.png"
            )

        outputFile.outputStream().use {
            animeFrame.compress(
                Bitmap.CompressFormat.PNG,
                100,
                it
            )
        }

        animeFrame.recycle()
        frame.recycle()

        Log.i(
            "VideoProcessor",
            "Preview saved: ${outputFile.absolutePath}"
        )

        onProgress(6, 7, "Finalizing")

        val result =
            ProcessingInfo(
                width = info.width,
                height = info.height,
                frameRate = info.frameRate,
                durationUs = info.durationUs,
                mime = info.mime,
                decoderAvailable = true,
                encoderAvailable = true,
                encoderMime = encoderMime,
                testFramePath = outputFile.absolutePath
            )

        Log.i("VideoProcessor", "Stage 1 complete")

        onProgress(7, 7, "Complete")

        return result
    }

    private fun extractFirstFrame(
        uri: Uri
    ): Bitmap {

        val retriever =
            MediaMetadataRetriever()

        try {

            retriever.setDataSource(
                context,
                uri
            )

            return retriever.getFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: throw IllegalStateException(
                "Unable to extract first frame."
            )

        } finally {

            retriever.release()
        }
    }
}
