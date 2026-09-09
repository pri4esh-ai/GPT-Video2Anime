package com.gptvideo2anime

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }

        val title =
            TextView(this).apply {
                text = "GPT Video2Anime"
                textSize = 26f
            }

        status =
            TextView(this).apply {
                text = "Preparing models..."
                textSize = 16f
            }

        val choose =
            Button(this).apply {
                text = "Choose Video"
                setOnClickListener {
                    videoPicker.launch("video/*")
                }
            }

        val convert =
            Button(this).apply {
                text = "Convert To Anime"
                setOnClickListener {
                    startVideoPipeline()
                }
            }

        originalPreview =
            ImageView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        280,
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
                        280,
                        1f
                    )
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.DKGRAY)
            }

        originalPreview.setOnClickListener {
            if (originalPreview.drawable != null) {
                showFullPreview(originalPreview)
            }
        }

        animePreview.setOnClickListener {
            if (animePreview.drawable != null) {
                showFullPreview(animePreview)
            }
        }

        val previewRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            }

        val originalBox =
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
                        textSize = 14f
                    }
                )

                addView(originalPreview)
            }

        val animeBox =
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
                        textSize = 14f
                    }
                )

                addView(animePreview)
            }

        previewRow.addView(originalBox)
        previewRow.addView(animeBox)

        logs =
            TextView(this).apply {
                textSize = 13f
            }

        val scroll =
            ScrollView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )

                addView(logs)
            }

        root.addView(title)
        root.addView(status)
        root.addView(choose)
        root.addView(convert)
        root.addView(previewRow)
        root.addView(scroll)

        setContentView(root)

        requestPermissions()

        lifecycleScope.launch {
            try {
                modelManager.ensureModels { message ->
                    runOnUiThread {
                        appendLog(message)
                    }
                }

                runOnUiThread {
                    status.text = modelManager.modelStatus()
                }

            } catch (e: Exception) {

                runOnUiThread {
                    status.text = "Model install failed"
                    appendLog(e.message ?: "Unknown error")
                }
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

                val frame =
                    withContext(Dispatchers.IO) {
                        extractFirstFrame(uri)
                    }

                originalPreview.setImageBitmap(frame)

                val modelPath =
                    modelManager.animeModelPath()
                        ?: throw IllegalStateException(
                            "AnimeGANv3 not installed."
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

                val elapsed =
                    SystemClock.elapsedRealtime() - start

                animePreview.setImageBitmap(anime)

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

        val input =
            selectedVideo ?: run {
                appendLog("Select a video first.")
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

        appendLog("Full video pipeline started.")
    }

    private fun showFullPreview(source: ImageView) {

        val dialog =
            Dialog(
                this,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen
            )

        val container =
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
            }

        val preview =
            ImageView(this).apply {
                setImageDrawable(source.drawable)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams =
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
            }

        container.addView(preview)

        container.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setContentView(container)
        dialog.show()
    }

    private fun appendLog(text: String) {
        runOnUiThread {
            logs.append("$text\n")
        }
    }
}
