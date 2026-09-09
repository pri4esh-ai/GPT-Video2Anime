package com.gptvideo2anime.pipeline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

import androidx.core.app.NotificationCompat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VideoProcessingService : Service() {

    companion object {

        const val ACTION_START =
            "com.gptvideo2anime.START"

        const val EXTRA_INPUT_URI =
            "input_uri"

        private const val CHANNEL_ID =
            "video_processing"

        private const val NOTIFICATION_ID =
            1001
    }

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default
        )

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification(
                "GPT Video2Anime is ready"
            )
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            intent?.action ==
            ACTION_START
        ) {

            val uriString =
                intent.getStringExtra(
                    EXTRA_INPUT_URI
                )

            if (uriString != null) {

                scope.launch {

                    try {

                        val processor =
                            VideoProcessor(
                                applicationContext
                            )

                        val result =
                            processor.process(
                                android.net.Uri.parse(
                                    uriString
                                )
                            )

                        updateNotification(
                            "Video detected: " +
                            "${result.width}x${result.height} " +
                            "@ ${result.frameRate} FPS"
                        )

                    } catch (error: Throwable) {

                        updateNotification(
                            "Processing error: " +
                            (error.message
                                ?: "unknown error")
                        )
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotification(
        text: String
    ): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
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
                NotificationManager::class.java
            )

        manager.notify(
            NOTIFICATION_ID,
            createNotification(text)
        )
    }

    private fun createNotificationChannel() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Video Processing",
                NotificationManager.IMPORTANCE_LOW
            )

        manager.createNotificationChannel(
            channel
        )
    }

    override fun onDestroy() {

        scope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
