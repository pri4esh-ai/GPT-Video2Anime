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
        private const val ANIME_MODEL_NAME =
            "AnimeGANv3_Hayao_36.onnx"

        private const val ANIME_MODEL_ASSET_PATH =
            "models/AnimeGANv3_Hayao_36.onnx"

        private const val ANIME_MODEL_TEMP =
            "AnimeGANv3_Hayao_36.onnx.part"

        private const val ANIME_MODEL_URL =
            "https://github.com/TachibanaYoshino/AnimeGANv3/releases/download/v1.1.0/AnimeGANv3_Hayao_36.onnx"

        private const val ANIME_MODEL_SHA256 =
            "95ba7b219073fd5b12f569bc38056ffd3019cf4caf15b1feb9f73d1286c9f69d"

        private const val ENHANCER_MODEL_NAME =
            "RealESR-AnimeVideo-v3_x4.onnx"

        private const val ENHANCER_MODEL_TEMP =
            "RealESR-AnimeVideo-v3_x4.onnx.part"

        private const val ENHANCER_MODEL_URL =
            "https://huggingface.co/tidus2102/Real-ESRGAN/resolve/main/RealESR-AnimeVideo-v3_x4.onnx"

        private const val ENHANCER_MODEL_SHA256 =
            "00ece3ac21c43ee31459216b5174b2cea0c5325044c5142aeb840f4890e175ff"

        private const val CONNECT_TIMEOUT_MS =
            30_000

        private const val READ_TIMEOUT_MS =
            60_000
    }

    private val modelDirectory =
        File(context.filesDir, "models")

    private val animeModelFile =
        File(
            modelDirectory,
            ANIME_MODEL_NAME
        )

    private val animeModelTempFile =
        File(
            modelDirectory,
            ANIME_MODEL_TEMP
        )

    private val enhancerModelFile =
        File(
            modelDirectory,
            ENHANCER_MODEL_NAME
        )

    private val enhancerModelTempFile =
        File(
            modelDirectory,
            ENHANCER_MODEL_TEMP
        )

    fun animeModelPath(): String? {
        return if (isAnimeModelInstalled()) {
            animeModelFile.absolutePath
        } else {
            null
        }
    }

    fun enhancerModelPath(): String? {
        return if (isEnhancerModelInstalled()) {
            enhancerModelFile.absolutePath
        } else {
            null
        }
    }

    fun isAnimeModelInstalled(): Boolean {
        return isValidModel(
            animeModelFile,
            ANIME_MODEL_SHA256
        )
    }

    fun isEnhancerModelInstalled(): Boolean {
        return isValidModel(
            enhancerModelFile,
            ENHANCER_MODEL_SHA256
        )
    }

    fun areAllModelsInstalled(): Boolean {
        return isAnimeModelInstalled() &&
            isEnhancerModelInstalled()
    }

    fun modelStatus(): String {
        val anime =
            isAnimeModelInstalled()

        val enhancer =
            isEnhancerModelInstalled()

        return when {
            anime && enhancer ->
                "Anime models ready"

            anime ->
                "AnimeGANv3 ready; enhancer not installed"

            enhancer ->
                "Enhancer ready; AnimeGANv3 not installed"

            else ->
                "Anime models not installed"
        }
    }

    suspend fun ensureModels(
        onProgress:
            ((model: String, downloaded: Long, total: Long) -> Unit)?
            = null
    ) = withContext(Dispatchers.IO) {

        modelDirectory.mkdirs()

        /*
         * AnimeGANv3 is bundled inside the APK by GitHub Actions.
         *
         * The phone does not download this model from the internet.
         * We copy the verified APK asset into internal storage once.
         */
        ensureBundledAnimeModel(
            onProgress = { copied, total ->
                onProgress?.invoke(
                    ANIME_MODEL_NAME,
                    copied,
                    total
                )
            }
        )

        /*
         * RealESRGAN is still handled by the existing downloader.
         * We will move this model to APK bundling in the next step.
         */
        if (!isEnhancerModelInstalled()) {
            downloadModel(
                url = ENHANCER_MODEL_URL,
                target = enhancerModelFile,
                temporary = enhancerModelTempFile,
                expectedSha256 = ENHANCER_MODEL_SHA256
            ) { downloaded, total ->

                onProgress?.invoke(
                    ENHANCER_MODEL_NAME,
                    downloaded,
                    total
                )
            }
        }
    }

    suspend fun ensureModel(
        onProgress:
            ((downloaded: Long, total: Long) -> Unit)?
            = null
    ): File = withContext(Dispatchers.IO) {

        modelDirectory.mkdirs()

        ensureBundledAnimeModel(
            onProgress = onProgress
        )

        if (!isAnimeModelInstalled()) {
            throw IOException(
                "Bundled AnimeGANv3 model could not be installed."
            )
        }

        animeModelFile
    }

    fun deleteModels() {
        animeModelFile.delete()
        animeModelTempFile.delete()

        enhancerModelFile.delete()
        enhancerModelTempFile.delete()
    }

    fun totalModelSizeBytes(): Long {

        val animeSize =
            if (animeModelFile.exists()) {
                animeModelFile.length()
            } else {
                0L
            }

        val enhancerSize =
            if (enhancerModelFile.exists()) {
                enhancerModelFile.length()
            } else {
                0L
            }

        return animeSize + enhancerSize
    }

    private fun ensureBundledAnimeModel(
        onProgress:
            ((downloaded: Long, total: Long) -> Unit)?
            = null
    ) {

        if (isAnimeModelInstalled()) {
            onProgress?.invoke(
                animeModelFile.length(),
                animeModelFile.length()
            )

            return
        }

        modelDirectory.mkdirs()

        val assetManager =
            context.assets

        val assetSize =
            try {
                assetManager.open(
                    ANIME_MODEL_ASSET_PATH
                ).use { input ->
                    input.available().toLong()
                }
            } catch (e: Exception) {
                throw IOException(
                    "Bundled AnimeGANv3 asset not found: " +
                        ANIME_MODEL_ASSET_PATH,
                    e
                )
            }

        if (assetSize <= 0L) {
            throw IOException(
                "Bundled AnimeGANv3 asset is empty."
            )
        }

        if (animeModelTempFile.exists()) {
            animeModelTempFile.delete()
        }

        var copiedBytes = 0L

        assetManager.open(
            ANIME_MODEL_ASSET_PATH
        ).use { input ->

            FileOutputStream(
                animeModelTempFile,
                false
            ).use { output ->

                val buffer =
                    ByteArray(1024 * 1024)

                while (true) {

                    val count =
                        input.read(buffer)

                    if (count < 0) {
                        break
                    }

                    if (count == 0) {
                        continue
                    }

                    output.write(
                        buffer,
                        0,
                        count
                    )

                    copiedBytes += count

                    onProgress?.invoke(
                        copiedBytes,
                        assetSize
                    )
                }

                output.flush()
            }
        }

        if (animeModelTempFile.length() <= 0L) {
            animeModelTempFile.delete()

            throw IOException(
                "Bundled AnimeGANv3 model is empty."
            )
        }

        val actualSha256 =
            sha256(animeModelTempFile)

        if (!actualSha256.equals(
                ANIME_MODEL_SHA256,
                ignoreCase = true
            )
        ) {

            animeModelTempFile.delete()

            throw IOException(
                "Bundled AnimeGANv3 SHA-256 mismatch. " +
                    "Expected=$ANIME_MODEL_SHA256 " +
                    "Actual=$actualSha256"
            )
        }

        if (animeModelFile.exists()) {
            animeModelFile.delete()
        }

        if (!animeModelTempFile.renameTo(
                animeModelFile
            )
        ) {

            animeModelTempFile.copyTo(
                animeModelFile,
                overwrite = true
            )

            animeModelTempFile.delete()
        }

        if (!isAnimeModelInstalled()) {
            animeModelFile.delete()

            throw IOException(
                "Installed AnimeGANv3 model failed verification."
            )
        }

        onProgress?.invoke(
            animeModelFile.length(),
            animeModelFile.length()
        )
    }

    private fun downloadModel(
        url: String,
        target: File,
        temporary: File,
        expectedSha256: String,
        onProgress:
            ((downloaded: Long, total: Long) -> Unit)?
            = null
    ) {

        modelDirectory.mkdirs()

        val existingBytes =
            if (temporary.exists()) {
                temporary.length()
            } else {
                0L
            }

        val connection =
            (URL(url).openConnection()
                as HttpURLConnection).apply {

                connectTimeout =
                    CONNECT_TIMEOUT_MS

                readTimeout =
                    READ_TIMEOUT_MS

                instanceFollowRedirects =
                    true

                requestMethod =
                    "GET"

                if (existingBytes > 0L) {
                    setRequestProperty(
                        "Range",
                        "bytes=$existingBytes-"
                    )
                }

                connect()
            }

        try {

            val responseCode =
                connection.responseCode

            if (responseCode !in 200..299) {
                throw IOException(
                    "Model download failed: HTTP $responseCode"
                )
            }

            val append =
                existingBytes > 0L &&
                    responseCode ==
                    HttpURLConnection.HTTP_PARTIAL

            val startingBytes =
                if (append) {
                    existingBytes
                } else {
                    0L
                }

            if (!append &&
                temporary.exists()
            ) {
                temporary.delete()
            }

            val contentLength =
                connection.contentLengthLong

            val totalLength =
                if (contentLength > 0L) {
                    startingBytes +
                        contentLength
                } else {
                    -1L
                }

            connection.inputStream.use { input ->

                FileOutputStream(
                    temporary,
                    append
                ).use { output ->

                    val buffer =
                        ByteArray(
                            1024 * 1024
                        )

                    var downloaded =
                        startingBytes

                    while (true) {

                        val count =
                            input.read(buffer)

                        if (count < 0) {
                            break
                        }

                        if (count == 0) {
                            continue
                        }

                        output.write(
                            buffer,
                            0,
                            count
                        )

                        downloaded +=
                            count

                        onProgress?.invoke(
                            downloaded,
                            totalLength
                        )
                    }

                    output.flush()
                }
            }

            if (temporary.length() <= 0L) {
                throw IOException(
                    "Downloaded model is empty."
                )
            }

            val actualSha256 =
                sha256(temporary)

            if (!actualSha256.equals(
                    expectedSha256,
                    ignoreCase = true
                )
            ) {

                temporary.delete()

                throw IOException(
                    "Model SHA-256 mismatch. " +
                        "Expected=$expectedSha256 " +
                        "Actual=$actualSha256"
                )
            }

            if (target.exists()) {
                target.delete()
            }

            if (!temporary.renameTo(target)) {

                temporary.copyTo(
                    target,
                    overwrite = true
                )

                temporary.delete()
            }

        } finally {
            connection.disconnect()
        }
    }

    private fun isValidModel(
        file: File,
        expectedSha256: String
    ): Boolean {

        if (!file.exists()) {
            return false
        }

        if (file.length() <= 0L) {
            return false
        }

        return try {

            sha256(file).equals(
                expectedSha256,
                ignoreCase = true
            )

        } catch (_: Exception) {

            false
        }
    }

    private fun sha256(
        file: File
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        file.inputStream().use { input ->

            val buffer =
                ByteArray(
                    1024 * 1024
                )

            while (true) {

                val count =
                    input.read(buffer)

                if (count < 0) {
                    break
                }

                if (count > 0) {
                    digest.update(
                        buffer,
                        0,
                        count
                    )
                }
            }
        }

        return digest.digest()
            .joinToString("") {
                "%02x".format(it)
            }
    }
}
