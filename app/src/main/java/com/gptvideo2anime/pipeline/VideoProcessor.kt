package com.gptvideo2anime.pipeline

import android.content.Context
import android.net.Uri

class VideoProcessor(
    private val context: Context
) {

    private val codecEngine =
        MediaCodecVideoEngine(context)

    data class ProcessingInfo(
        val width: Int,
        val height: Int,
        val frameRate: Int,
        val durationUs: Long,
        val mime: String,
        val decoderAvailable: Boolean,
        val encoderAvailable: Boolean,
        val encoderMime: String
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
                encoderMime
        )
    }

    fun process(
        uri: Uri
    ): ProcessingInfo {
        val result = inspect(uri)

        check(result.decoderAvailable) {
            "No compatible video decoder found for ${result.mime}"
        }

        check(result.encoderAvailable) {
            "No compatible video encoder found for ${result.encoderMime}"
        }

        return result
    }
}
