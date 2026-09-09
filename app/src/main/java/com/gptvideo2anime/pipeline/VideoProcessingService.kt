package com.gptvideo2anime.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.gptvideo2anime.model.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VideoProcessingService : Service() {

    companion object {
        const val ACTION_START =
            "com.gptvideo2anime.action.START_PROCESSING"

        const val EXTRA_INPUT_URI =
            "com.gptvideo2anime.extra.INPUT_URI"

        private const val CHANNEL_ID =
            "video_processing"

        private const val NOTIFICATION_ID =
            1001
    }

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private lateinit var modelManager: ModelManager
    private lateinit var videoProcessor: VideoProcessor

    override fun onCreate() {
        super.onCreate()

        modelManager =
            ModelManager(this)

        videoProcessor =
            VideoProcessor(this)

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification(
                "Preparing video processing..."
            )
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action != ACTION_START) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val inputUriString =
            intent.getStringExtra(
                EXTRA_INPUT_URI
            )

        if (inputUriString.isNullOrBlank()) {
            updateNotification(
                "No input video selected"
            )

            stopSelf(startId)

            return START_NOT_STICKY
        }

        serviceScope.launch {

            try {

                updateNotification(
                    "Checking anime models..."
                )

                android.util.Log.i(
                    "VideoProcessingService",
                    "Starting model verification/download"
                )

                modelManager.ensureModels {
                    model,
                    downloaded,
                    total ->

                    val progressText =
                        if (total > 0L) {

                            val percent =
                                (
                                    downloaded * 100L / total
                                ).coerceIn(
                                    0L,
                                    100L
                                )

                            "$model: $percent%"

                        } else {

                            "$model: " +
                                "${downloaded / (1024 * 1024)} MB"
                        }

                    updateNotification(
                        progressText
                    )
                }

                android.util.Log.i(
                    "VideoProcessingService",
                    "Model status: " +
                        modelManager.modelStatus()
                )

                android.util.Log.i(
                    "VideoProcessingService",
                    "Anime model path: " +
                        modelManager.animeModelPath()
                )

                android.util.Log.i(
                    "VideoProcessingService",
                    "Enhancer model path: " +
                        modelManager.enhancerModelPath()
                )

                updateNotification(
                    "Models ready"
                )

                val inputUri =
                    android.net.Uri.parse(
                        inputUriString
                    )

                updateNotification(
                    "Inspecting video..."
                )

                val result =
                    videoProcessor.process(
                        inputUri
                    )

                android.util.Log.i(
                    "VideoProcessingService",
                    "Video: " +
                        "${result.width}x${result.height}, " +
                        "${result.frameRate} FPS, " +
                        result.mime
                )

                android.util.Log.i(
                    "VideoProcessingService",
                    "Decoder available: " +
                        result.decoderAvailable
                )

                android.util.Log.i(
                    "VideoProcessingService",
                    "Encoder available: " +
                        result.encoderAvailable
                )

                android.util.Log.i(
                    "VideoProcessingService",
                    "Encoder MIME: " +
                        result.encoderMime
                )

                updateNotification(
                    "Video ready: " +
                        "${result.width}x${result.height} " +
                        "${result.frameRate} FPS"
                )

            } catch (error: Exception) {

                android.util.Log.e(
                    "VideoProcessingService",
                    "Processing failed",
                    error
                )

                updateNotification(
                    "Processing failed: " +
                        (
                            error.message
                                ?: "Unknown error"
                        )
                )

            } finally {

                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotification(
        text: String
    ): Notification {

        val builder =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                Notification.Builder(
                    this,
                    CHANNEL_ID
                )
            } else {
                Notification.Builder(this)
            }

        return builder
            .setContentTitle(
                "GPT Video2Anime"
            )
            .setContentText(text)
            .setSmallIcon(
                android.R.drawable.ic_media_play
            )
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(
        text: String
    ) {

        val manager =
            getSystemService(
                NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            createNotification(text)
        )

        android.util.Log.i(
            "VideoProcessingService",
            text
        )
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Video Processing",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(
                channel
            )
        }
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    override fun onDestroy() {

        serviceScope.cancel()

        super.onDestroy()
    }
}
