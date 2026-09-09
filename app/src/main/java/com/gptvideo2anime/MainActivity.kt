package com.gptvideo2anime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gptvideo2anime.inference.OnnxAnimeEngine
import com.gptvideo2anime.model.ModelManager
import com.gptvideo2anime.pipeline.VideoProcessingService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var logs: TextView
    private lateinit var originalPreview: ImageView
    private lateinit var animePreview: ImageView

    private lateinit var modelManager: ModelManager

    private var selectedVideo: Uri? = null

    private val videoPicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                selectedVideo = uri
                status.text = "Video selected."
                appendLog("Video selected.")
                generatePreview(uri)
            }
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "GPT Video2Anime"
            textSize = 26f
        }

        status = TextView(this).apply {
            text = "Preparing models..."
            textSize = 16f
        }

        val choose = Button(this).apply {
            text = "Choose Video"
            setOnClickListener {
                videoPicker.launch("video/*")
            }
        }

        val convert = Button(this).apply {
            text = "Convert To Anime"
            setOnClickListener {
                startVideoPipeline()
            }
        }

        originalPreview = ImageView(this).apply {
            adjustViewBounds = true
        }

        animePreview = ImageView(this).apply {
            adjustViewBounds = true
        }

        logs = TextView(this).apply {
            textSize = 13f
        }

        val scroll = ScrollView(this).apply {
            addView(logs)
        }

        root.addView(title)
        root.addView(status)
        root.addView(choose)
        root.addView(convert)
        root.addView(originalPreview)
        root.addView(animePreview)
        root.addView(scroll)

        setContentView(root)

        requestPermissions()

        lifecycleScope.launch {
            try {
                modelManager.ensureModels { message ->
                    runOnUiThread { appendLog(message) }
                }

                status.text = modelManager.modelStatus()

            } catch (e: Exception) {
                status.text = "Model install failed"
                appendLog(e.message ?: "Unknown error")
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= 33) {
            permissions += Manifest.permission.READ_MEDIA_VIDEO
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
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

    private fun generatePreview(uri: Uri) {
        lifecycleScope.launch {
            try {
                appendLog("Extracting first frame...")

                val frame = extractFirstFrame(uri)

                originalPreview.setImageBitmap(frame)

                val modelPath =
                    modelManager.animeModelPath()
                        ?: throw IllegalStateException(
                            "AnimeGANv3 not installed."
                        )

                appendLog("Running AnimeGANv3...")

                val start = SystemClock.elapsedRealtime()

                val anime =
                    OnnxAnimeEngine(modelPath).use {
                        it.processFrame(frame)
                    }

                val elapsed =
                    SystemClock.elapsedRealtime() - start

                animePreview.setImageBitmap(anime)

                status.text =
                    "Preview ready (${elapsed} ms)"

                appendLog(
                    "Preview completed in ${elapsed} ms"
                )

            } catch (e: Exception) {
                status.text = "Preview failed"
                appendLog("ERROR: ${e.message}")
            }
        }
    }

    private fun extractFirstFrame(uri: Uri): Bitmap {
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(this, uri)

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

    private fun startVideoPipeline() {
        val input = selectedVideo ?: return

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

        appendLog("Full video pipeline started.")
    }

    private fun appendLog(text: String) {
        logs.append("$text\n")
    }
}
