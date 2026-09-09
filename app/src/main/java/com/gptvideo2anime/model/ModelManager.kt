package com.gptvideo2anime.model
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
class ModelManager(private val context: Context) {
    companion object {
        private const val MODEL_FILE_NAME = "anime.onnx"
        private const val TEMP_FILE_NAME = "anime.onnx.part"
        private const val MODEL_URL = ""
        private const val EXPECTED_SHA256 = ""
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
    }
    private val modelDirectory = File(context.filesDir, "models")
    private val modelFile = File(modelDirectory, MODEL_FILE_NAME)
    private val temporaryFile = File(modelDirectory, TEMP_FILE_NAME)
    fun modelStatus(): String {
        return when {
            isModelInstalled() -> "anime.onnx installed"
            MODEL_URL.isBlank() -> "anime.onnx model URL not configured"
            else -> "anime.onnx not installed"
        }
    }
    fun modelPath(): String? {
        return if (isModelInstalled()) modelFile.absolutePath else null
    }
    fun isModelInstalled(): Boolean {
        if (!modelFile.exists() || modelFile.length() <= 0L) return false
        if (EXPECTED_SHA256.isBlank()) return true
        return sha256(modelFile).equals(EXPECTED_SHA256, ignoreCase = true)
    }
    suspend fun ensureModel(onProgress: ((downloaded: Long, total: Long) -> Unit)? = null): File = withContext(Dispatchers.IO) {
        if (isModelInstalled()) return@withContext modelFile
        require(MODEL_URL.isNotBlank()) { "Anime ONNX model URL is not configured." }
        modelDirectory.mkdirs()
        downloadModel(onProgress)
        if (!isModelInstalled()) {
            modelFile.delete()
            throw IOException("Downloaded anime model failed SHA-256 verification.")
        }
        modelFile
    }
    private fun downloadModel(onProgress: ((downloaded: Long, total: Long) -> Unit)?) {
        modelDirectory.mkdirs()
        val existingBytes = if (temporaryFile.exists()) temporaryFile.length() else 0L
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
            connect()
        }
        try {
            val responseCode = connection.responseCode
            val append = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            val startingBytes = if (append) existingBytes else 0L
            if (!append && temporaryFile.exists()) temporaryFile.delete()
            val totalLength = connection.contentLengthLong.let { length ->
                if (length > 0L) startingBytes + length else -1L
            }
            connection.inputStream.use { input ->
                FileOutputStream(temporaryFile, append).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    var downloaded = startingBytes
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress?.invoke(downloaded, totalLength)
                    }
                    output.flush()
                }
            }
            if (temporaryFile.length() <= 0L) throw IOException("Downloaded model file is empty.")
            if (modelFile.exists()) modelFile.delete()
            if (!temporaryFile.renameTo(modelFile)) {
                temporaryFile.copyTo(modelFile, overwrite = true)
                temporaryFile.delete()
            }
        } finally {
            connection.disconnect()
        }
    }
    fun deleteModel() {
        modelFile.delete()
        temporaryFile.delete()
    }
    fun modelSizeBytes(): Long = if (modelFile.exists()) modelFile.length() else 0L
    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
