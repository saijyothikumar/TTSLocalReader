package com.tts.reader.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

sealed class ModelStatus {
    object NotDownloaded : ModelStatus()
    data class Downloading(
        val progressPercent: Int,
        val currentItem: String,
        val bytesDownloaded: Long = 0,
        val totalBytes: Long = 0
    ) : ModelStatus()
    object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

class ModelManager(private val context: Context) {

    private val tag = "ModelManager"

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)
    val status: StateFlow<ModelStatus> = _status

    val modelDir: File
        get() = File(context.filesDir, "kokoro_model")

    val modelFile: File get() = File(modelDir, "model.onnx")
    val voicesFile: File get() = File(modelDir, "voices.bin")
    val tokensFile: File get() = File(modelDir, "tokens.txt")
    val espeakDataDir: File get() = File(modelDir, "espeak-ng-data")
    val lexiconFile: File get() = File(modelDir, "lexicon-us-en.txt")
    private val manifestFile: File get() = File(modelDir, "manifest.json")

    private var isDownloadCancelled = false

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    init {
        checkInstalledState()
    }

    fun isModelReady(): Boolean {
        return manifestFile.exists() &&
                modelFile.exists() && modelFile.length() > 5_000_000 &&
                voicesFile.exists() &&
                tokensFile.exists()
    }

    fun checkInstalledState() {
        if (isModelReady()) {
            _status.value = ModelStatus.Ready
            Log.i(tag, "Kokoro model is verified and ready on disk.")
        } else {
            _status.value = ModelStatus.NotDownloaded
        }
    }

    fun cancelDownload() {
        isDownloadCancelled = true
        _status.value = ModelStatus.NotDownloaded
    }

    /**
     * Resumable download of Kokoro INT8 archive with tar.bz2 and zip support.
     */
    suspend fun downloadModel(
        archiveUrl: String = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-en-v0_19.tar.bz2"
    ) = withContext(Dispatchers.IO) {
        isDownloadCancelled = false
        val tempArchive = File(context.cacheDir, "kokoro_download.archive")

        var attempt = 0
        val maxAttempts = 3
        var success = false

        while (attempt < maxAttempts && !success && !isDownloadCancelled) {
            attempt++
            try {
                _status.value = ModelStatus.Downloading(
                    progressPercent = 5,
                    currentItem = if (attempt > 1) "Retrying download (Attempt $attempt of $maxAttempts)..." else "Connecting to voice server..."
                )

                if (!modelDir.exists()) modelDir.mkdirs()

                val existingBytes = if (tempArchive.exists()) tempArchive.length() else 0L
                val requestBuilder = Request.Builder().url(archiveUrl)

                if (existingBytes > 0) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                    Log.d(tag, "Resuming download from byte: $existingBytes")
                }

                val response = httpClient.newCall(requestBuilder.build()).execute()

                if (response.code != 200 && response.code != 206) {
                    if (response.code == 416) {
                        Log.d(tag, "Range 416: Download already completed.")
                    } else {
                        throw Exception("Server returned HTTP ${response.code}: ${response.message}")
                    }
                } else {
                    val body = response.body ?: throw Exception("Empty response body from voice server")
                    val isResume = (response.code == 206)
                    val streamLength = body.contentLength()
                    val totalExpected = if (isResume) existingBytes + streamLength else streamLength

                    val randomAccess = RandomAccessFile(tempArchive, "rw")
                    if (isResume) {
                        randomAccess.seek(existingBytes)
                    } else {
                        randomAccess.setLength(0)
                        randomAccess.seek(0)
                    }

                    body.byteStream().use { input ->
                        val buffer = ByteArray(32768)
                        var bytesRead: Int
                        var currentTotal = if (isResume) existingBytes else 0L

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            if (isDownloadCancelled) {
                                randomAccess.close()
                                return@withContext
                            }

                            randomAccess.write(buffer, 0, bytesRead)
                            currentTotal += bytesRead

                            if (totalExpected > 0) {
                                val percent = ((currentTotal * 80) / totalExpected).toInt().coerceIn(5, 80)
                                val currentPercent = (_status.value as? ModelStatus.Downloading)?.progressPercent ?: 0
                                if (percent != currentPercent) {
                                    val mbDownloaded = currentTotal / (1024 * 1024)
                                    val mbTotal = totalExpected / (1024 * 1024)
                                    _status.value = ModelStatus.Downloading(
                                        progressPercent = percent,
                                        currentItem = "Downloading neural weights ($mbDownloaded MB / $mbTotal MB)...",
                                        bytesDownloaded = currentTotal,
                                        totalBytes = totalExpected
                                    )
                                }
                            }
                        }
                    }
                    randomAccess.close()
                }

                _status.value = ModelStatus.Downloading(85, "Extracting voice packages...")

                // Extract tar.bz2 or zip archive
                if (archiveUrl.endsWith(".tar.bz2") || archiveUrl.endsWith(".bz2")) {
                    extractTarBz2(tempArchive, modelDir)
                } else {
                    extractZip(tempArchive, modelDir)
                }

                // Flatten directory structure so files are located in modelDir directly
                flattenModelDirectory(modelDir)

                writeManifest()
                tempArchive.delete()

                _status.value = ModelStatus.Ready
                success = true
                Log.i(tag, "Kokoro neural model extracted and manifest saved!")

            } catch (e: Exception) {
                Log.w(tag, "Download attempt $attempt failed: ${e.message}")
                if (attempt >= maxAttempts) {
                    _status.value = ModelStatus.Error(
                        "Download failed: ${e.localizedMessage}. Tap Retry to continue download."
                    )
                } else {
                    delay(2000L * attempt)
                }
            }
        }
    }

    private fun extractTarBz2(archiveFile: File, destDir: File) {
        FileInputStream(archiveFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                BZip2CompressorInputStream(bis).use { bzIn ->
                    TarArchiveInputStream(bzIn).use { tarIn ->
                        var entry = tarIn.nextEntry
                        while (entry != null) {
                            val cleanName = entry.name.replace("\\", "/")
                            val targetFile = File(destDir, cleanName)

                            if (entry.isDirectory) {
                                targetFile.mkdirs()
                            } else {
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { fos ->
                                    tarIn.copyTo(fos)
                                }
                            }
                            entry = tarIn.nextEntry
                        }
                    }
                }
            }
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val cleanName = entry.name.replace("\\", "/")
                val targetFile = File(destDir, cleanName)

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun flattenModelDirectory(dir: File) {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory && f.name != "espeak-ng-data") {
                val subFiles = f.listFiles() ?: continue
                for (sub in subFiles) {
                    val dest = File(dir, sub.name)
                    if (!dest.exists()) {
                        sub.renameTo(dest)
                    }
                }
            }
        }
    }

    private fun writeManifest() {
        try {
            val json = JSONObject().apply {
                put("installedAt", System.currentTimeMillis())
                put("modelSize", modelFile.length())
                put("version", "1.0.0")
                put("status", "VALID")
            }
            manifestFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(tag, "Failed to write manifest", e)
        }
    }

    fun deleteModel() {
        try {
            modelDir.deleteRecursively()
            _status.value = ModelStatus.NotDownloaded
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete model", e)
        }
    }
}
