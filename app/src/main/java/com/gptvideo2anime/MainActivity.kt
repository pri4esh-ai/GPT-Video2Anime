package com.gptvideo2anime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

import com.gptvideo2anime.model.ModelManager
import com.gptvideo2anime.pipeline.VideoProcessingService

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private var selectedVideo: Uri? = null

    private val videoPicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri != null) {
                selectedVideo = uri

                status.text =
                    "Video selected.\nReady to process."
            }
        }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            status.text =
                "Permissions checked.\nChoose a video."
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    40,
                    40,
                    40,
                    40
                )
            }

        val title =
            TextView(this).apply {
                text =
                    "GPT Video2Anime"

                textSize = 28f
            }

        status =
            TextView(this).apply {
                text =
                    "Offline video-to-anime pipeline"

                textSize = 16f

                setPadding(
                    0,
                    30,
                    0,
                    30
                )
            }

        val choose =
            Button(this).apply {
                text =
                    "Choose Video"

                setOnClickListener {
                    videoPicker.launch("video/*")
                }
            }

        val process =
            Button(this).apply {
                text =
                    "Convert to Anime"

                setOnClickListener {
                    startProcessing()
                }
            }

        val model =
            TextView(this).apply {
                text =
                    "Model: ${
                        ModelManager(
                            this@MainActivity
                        ).modelStatus()
                    }"

                setPadding(
                    0,
                    20,
                    0,
                    20
                )
            }

        root.addView(title)
        root.addView(status)
        root.addView(choose)
        root.addView(process)
        root.addView(model)

        setContentView(root)

        requestPermissionsIfNeeded()
    }

    private fun requestPermissionsIfNeeded() {

        val permissions =
            mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33) {
            permissions +=
                Manifest.permission.READ_MEDIA_VIDEO
        } else {
            permissions +=
                Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val missing =
            permissions.filter {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(
                missing.toTypedArray()
            )
        }
    }

    private fun startProcessing() {

        val input =
            selectedVideo

        if (input == null) {
            status.text =
                "Choose a video first."

            return
        }

        val intent =
            Intent(
                this,
                VideoProcessingService::class.java
            ).apply {

                action =
                    VideoProcessingService.ACTION_START

                putExtra(
                    VideoProcessingService.EXTRA_INPUT_URI,
                    input.toString()
                )
            }

        ContextCompat.startForegroundService(
            this,
            intent
        )

        status.text =
            "Processing started.\n" +
            "MediaCodec pipeline is running."
    }
}
