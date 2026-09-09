package com.gptvideo2anime.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
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

    fun inspect(
        uri: Uri
    ): ProcessingInfo {

        val info =
            codecEngine.inspect(uri)

        val encoderMime =
            codecEngine.bestEncoderMime(
                info.mime
            )

        return ProcessingInfo(
            width = info.width,
            height = info.height,
            frameRate = info.frameRate,
            durationUs = info.durationUs,
            mime = info.mime,
            decoderAvailable =
                codecEngine.findDecoder(info.mime) != null,
            encoderAvailable =
                codecEngine.findEncoder(encoderMime) != null,
            encoderMime = encoderMime,
            testFramePath = null
        )
    }

    fun process(
        uri: Uri,
        onProgress: (
            current: Int,
            total: Int,
            stage: String
        ) -> Unit
    ): ProcessingInfo {

        val result =
            inspect(uri)

        check(result.decoderAvailable) {
            "No compatible decoder found."
        }

        check(result.encoderAvailable) {
            "No compatible encoder found."
        }

        val modelPath =
            modelManager.animeModelPath()
                ?: throw IllegalStateException(
                    "AnimeGANv3 model missing."
                )

        val outputDir =
            File(
                context.filesDir,
                "stage1"
            ).apply {
                mkdirs()
            }

        val outputFile =
            File(
                outputDir,
                "anime_test_frame.png"
            )

        onProgress(
            1,
            5,
            "Extracting first frame..."
        )

        val frame =
            extractFirstFrame(uri)

        onProgress(
            2,
            5,
            "Loading AnimeGANv3..."
        )

        val animeFrame =
            OnnxAnimeEngine(modelPath).use { engine ->

                onProgress(
                    3,
                    5,
                    "Running AnimeGANv3..."
                )

                engine.processFrame(frame)
            }

        onProgress(
            4,
            5,
            "Saving preview..."
        )

        try {

            outputFile.outputStream().use {

                animeFrame.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    it
                )
            }

        } finally {

            animeFrame.recycle()
            frame.recycle()
        }

        onProgress(
            5,
            5,
            "Preview saved."
        )

        return result.copy(
            testFramePath =
                outputFile.absolutePath
        )
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
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: throw IllegalStateException(
                "Unable to decode first frame."
            )

        } finally {

            retriever.release()
        }
    }
}
