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
        val encoderAvailable: Boolean
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
            when (info.mime) {
                "video/avc" -> "video/avc"
                "video/hevc" -> "video/hevc"
                "video/x-vnd.on2.vp9" ->
                    "video/x-vnd.on2.vp9"
                else ->
                    "video/avc"
            }

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
                encoder != null
        )
    }

    fun process(
        uri: Uri
    ): ProcessingInfo {

        /*
         * Foundation stage:
         *
         * 1. Inspect video.
         * 2. Verify hardware decoder.
         * 3. Verify encoder.
         *
         * The next pipeline stage will connect:
         *
         * MediaCodec decoder
         *       ↓
         * frame preprocessing
         *       ↓
         * MediaPipe tracking
         *       ↓
         * character memory
         *       ↓
         * ONNX anime model
         *       ↓
         * occlusion handling
         *       ↓
         * frame renderer
         *       ↓
         * MediaCodec encoder
         */
        return inspect(uri)
    }
}
