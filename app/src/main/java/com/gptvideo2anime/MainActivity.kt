package com.gptvideo2anime

import android.Manifest
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gptvideo2anime.inference.OnnxAnimeEngine
import com.gptvideo2anime.model.ModelManager
import com.gptvideo2anime.pipeline.VideoProcessingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var logs: TextView
    private lateinit var scroll: ScrollView
    private lateinit var originalPreview: ImageView
    private lateinit var animePreview: ImageView
    private lateinit var convertButton: Button

    private lateinit var modelManager: ModelManager

    private var selectedVideo: Uri? = null
    private var processing = false

    private val progressReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                val stage =
                    intent?.getStringExtra(
                        VideoProcessingService.EXTRA_STAGE
                    ) ?: return

                val current =
                    intent.getIntExtra(
                        VideoProcessingService.EXTRA_CURRENT,
                        0
                    )

                val total =
                    intent.getIntExtra(
                        VideoProcessingService.EXTRA_TOTAL,
                        0
                    )

                runOnUiThread {

                    val text =
                        if (total > 0)
                            "$stage ($current/$total)"
                        else
                            stage

                    status.text = text
                    appendLog(text)

                    if (stage == "Complete") {
                        processing = false
                        convertButton.isEnabled = true
                    }
                }
            }
        }

    private val videoPicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            uri ?: return@registerForActivityResult

            selectedVideo = uri

            status.text = "Video selected."

            appendLog("Video selected.")

            generatePreview(uri)
        }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            appendLog("Permissions checked.")
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        modelManager = ModelManager(this)

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 28, 28, 28)
            }

        root.addView(
            TextView(this).apply {
                text = "GPT Video2Anime"
                textSize = 26f
            }
        )

        status =
            TextView(this).apply {
                text = "Preparing models..."
                textSize = 16f
            }

        root.addView(status)

        root.addView(
            Button(this).apply {
                text = "Choose Video"
                setOnClickListener {
                    videoPicker.launch("video/*")
                }
            }
        )

        convertButton =
            Button(this).apply {
                text = "Convert To Anime"

                setOnClickListener {
                    if (!processing) {
                        processing = true
                        isEnabled = false
                        startVideoPipeline()
                    }
                }
            }

        root.addView(convertButton)

        originalPreview =
            ImageView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        260,
                        1f
                    )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.DKGRAY)
            }

        animePreview =
            ImageView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        260,
                        1f
                    )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.DKGRAY)
            }

        originalPreview.setOnClickListener {
            if (originalPreview.drawable != null)
                showFullPreview(originalPreview)
        }

        animePreview.setOnClickListener {
            if (animePreview.drawable != null)
                showFullPreview(animePreview)
        }

        val row =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

        row.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )

                addView(
                    TextView(context).apply {
                        text = "Original"
                        gravity = Gravity.CENTER
                    }
                )

                addView(originalPreview)
            }
        )

        row.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )

                setPadding(16, 0, 0, 0)

                addView(
                    TextView(context).apply {
                        text = "Anime"
                        gravity = Gravity.CENTER
                    }
                )

                addView(animePreview)
            }
        )

        root.addView(row)

        logs =
            TextView(this).apply {
                textSize = 13f
            }

        scroll =
            ScrollView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                addView(logs)
            }

        root.addView(scroll)

        setContentView(root)

        registerReceiver(
            progressReceiver,
            IntentFilter(VideoProcessingService.ACTION_PROGRESS)
        )

        requestPermissions()

        lifecycleScope.launch {

            try {

                modelManager.ensureModels { message ->
                    appendLog(message)
                }

                runOnUiThread {
                    status.text =
                        modelManager.modelStatus()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    status.text =
                        "Model install failed"

                    appendLog(
                        e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(progressReceiver)
        super.onDestroy()
    }

    private fun requestPermissions() {

        val permission =
            if (Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_VIDEO
            else
                Manifest.permission.READ_EXTERNAL_STORAGE

        if (
            ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(permission))
        }
    }

    private fun generatePreview(uri: Uri) {

        lifecycleScope.launch {

            try {

                appendLog("Extracting first frame...")

                val frame =
                    withContext(Dispatchers.IO) {
                        extractFirstFrame(uri)
                    }

                originalPreview.setImageBitmap(frame)

                val modelPath =
                    modelManager.animeModelPath()
                        ?: throw IllegalStateException(
                            "AnimeGANv3 missing."
                        )

                appendLog("Running AnimeGANv3...")

                val start =
                    SystemClock.elapsedRealtime()

                val anime =
                    withContext(Dispatchers.Default) {
                        OnnxAnimeEngine(modelPath).use {
                            it.processFrame(frame)
                        }
                    }

                animePreview.setImageBitmap(anime)

                val elapsed =
                    SystemClock.elapsedRealtime() - start

                status.text =
                    "Preview ready (${elapsed} ms)"

                appendLog(
                    "Anime preview completed in ${elapsed} ms"
                )

            } catch (e: Exception) {

                status.text = "Preview failed"

                appendLog("ERROR: ${e.message}")
            }
        }
    }

    private fun extractFirstFrame(uri: Uri): Bitmap {

        val retriever =
            MediaMetadataRetriever()

        try {

            retriever.setDataSource(this, uri)

            return retriever.getFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: throw IllegalStateException(
                "Unable to decode first frame."
            )

        } finally {

            retriever.release()
        }
    }

    private fun startVideoPipeline() {

        val input =
            selectedVideo ?: run {

                processing = false
                convertButton.isEnabled = true

                appendLog("Select a video first.")

                return
            }

        ContextCompat.startForegroundService(
            this,
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
        )

        appendLog("Full video pipeline started.")
    }

    private fun showFullPreview(source: ImageView) {

        val dialog =
            Dialog(
                this,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen
            )

        dialog.setContentView(
            FrameLayout(this).apply {

                setBackgroundColor(Color.BLACK)

                addView(
                    ImageView(context).apply {

                        setImageDrawable(source.drawable)

                        scaleType =
                            ImageView.ScaleType.FIT_CENTER

                        layoutParams =
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                    }
                )

                setOnClickListener {
                    dialog.dismiss()
                }
            }
        )

        dialog.show()
    }

    private fun appendLog(text: String) {

        runOnUiThread {

            logs.append("$text\n")

            scroll.post {
                scroll.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }
}
