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

        val decoder =
            codecEngine.findDecoder(
                info.mime
            )

        val encoderMime =
            codecEngine.bestEncoderMime(
                info.mime
            )

        val encoder =
            codecEngine.findEncoder(
                encoderMime
            )

        return ProcessingInfo(
            width = info.width,
            height = info.height,
            frameRate = info.frameRate,
            durationUs = info.durationUs,
            mime = info.mime,
            decoderAvailable =
                decoder != null,
            encoderAvailable =
                encoder != null,
            encoderMime =
                encoderMime,
            testFramePath = null
        )
    }

    fun process(
        uri: Uri
    ): ProcessingInfo {

        val result =
            inspect(uri)

        check(result.decoderAvailable) {
            "No compatible video decoder found for " +
                result.mime
        }

        check(result.encoderAvailable) {
            "No compatible video encoder found for " +
                result.encoderMime
        }

        val modelPath =
            modelManager.animeModelPath()
                ?: throw IllegalStateException(
                    "AnimeGANv3 model is not installed."
                )

        val outputDirectory =
            File(
                context.filesDir,
                "stage1"
            )

        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        val outputFile =
            File(
                outputDirectory,
                "anime_test_frame.png"
            )

        val frame =
            extractFirstFrame(uri)

        val animeFrame =
            OnnxAnimeEngine(
                modelPath
            ).use { engine ->
                engine.processFrame(frame)
            }

        try {

            outputFile.outputStream()
                .use { output ->
                    check(
                        animeFrame.compress(
                            Bitmap.CompressFormat.PNG,
                            100,
                            output
                        )
                    ) {
                        "Unable to save AnimeGANv3 frame."
                    }
                }

        } finally {

            if (!animeFrame.isRecycled) {
                animeFrame.recycle()
            }

            if (!frame.isRecycled) {
                frame.recycle()
            }
        }

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

            val frame =
                retriever.getFrameAtTime(
                    0L,
                    MediaMetadataRetriever
                        .OPTION_CLOSEST
                )

            return frame
                ?: throw IllegalStateException(
                    "Unable to decode the first video frame."
                )

        } finally {

            retriever.release()
        }
    }
}
