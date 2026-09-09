package com.gptvideo2anime.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
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

        modelManager = ModelManager(this)
        videoProcessor = VideoProcessor(this)

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification("Preparing video processing...")
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

        val input =
            intent.getStringExtra(EXTRA_INPUT_URI)

        if (input.isNullOrBlank()) {
            updateNotification("No input video selected")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        serviceScope.launch {

            try {

                updateNotification("Checking models...")

                modelManager.ensureModels { message ->
                    updateNotification(message)
                }

                updateNotification("Opening video...")

                val result =
                    videoProcessor.process(
                        android.net.Uri.parse(input)
                    ) { current, total, stage ->

                        val text =
                            if (total > 0) {
                                "$stage (${current.coerceIn(0, total)}/$total)"
                            } else {
                                stage
                            }

                        updateNotification(text)
                    }

                Log.i(
                    "VideoProcessingService",
                    "Saved preview: ${result.testFramePath}"
                )

                updateNotification("Stage 1 complete")

            } catch (e: Exception) {

                Log.e(
                    "VideoProcessingService",
                    "Processing failed",
                    e
                )

                updateNotification(
                    "Failed: ${e.message}"
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
            } else {
                Notification.Builder(this)
            }

        return builder
            .setContentTitle("GPT Video2Anime")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
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

        Log.i("VideoProcessingService", text)
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val manager =
                getSystemService(
                    NOTIFICATION_SERVICE
                ) as NotificationManager

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Video Processing",
                    NotificationManager.IMPORTANCE_LOW
                )
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
