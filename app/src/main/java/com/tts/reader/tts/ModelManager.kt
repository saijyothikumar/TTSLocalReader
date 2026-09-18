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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

sealed class ModelStatus {
    object NotDownloaded : ModelStatus()
    data class Downloading(val progressPercent: Int, val currentItem: String, val bytesDownloaded: Long = 0, val totalBytes: Long = 0) : ModelStatus()
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

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    init {
        checkInstalledState()
    }

    fun isModelReady(): Boolean {
        // Must have primary onnx model (> 10MB), voices, tokens, and manifest
        return manifestFile.exists() &&
                modelFile.exists() && modelFile.length() > 5_000_000 &&
                voicesFile.exists() &&
                tokensFile.exists()
    }

    fun checkInstalledState() {
        if (isModelReady()) {
            _status.value = ModelStatus.Ready
            Log.i(tag, "Kokoro neural model is verified and ready on disk.")
        } else {
            _status.value = ModelStatus.NotDownloaded
        }
    }

    /**
     * Resumable download with automatic retry and standard ZIP unpacking.
     */
    suspend fun downloadModel(
        zipUrl: String = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.zip"
    ) = withContext(Dispatchers.IO) {
        val tempZip = File(context.cacheDir, "kokoro_download.zip")

        var attempt = 0
        val maxAttempts = 3
        var success = false

        while (attempt < maxAttempts && !success) {
            attempt++
            try {
                _status.value = ModelStatus.Downloading(
                    progressPercent = 5,
                    currentItem = if (attempt > 1) "Retrying download (Attempt $attempt of $maxAttempts)..." else "Connecting to voice repository..."
                )

                if (!modelDir.exists()) modelDir.mkdirs()

                // Check existing partial file for HTTP Range resume
                val existingBytes = if (tempZip.exists()) tempZip.length() else 0L

                val requestBuilder = Request.Builder().url(zipUrl)
                if (existingBytes > 0) {
                    requestBuilder.header("Range", "bytes=$existingBytes-")
                    Log.d(tag, "Resuming download from byte: $existingBytes")
                }

                val response = httpClient.newCall(requestBuilder.build()).execute()

                // HTTP 206 Partial Content or HTTP 200 OK
                if (response.code != 200 && response.code != 206) {
                    if (response.code == 416) {
                        // Range already satisfied / download complete
                        Log.d(tag, "Range 416: Download already completed.")
                    } else {
                        throw Exception("Server returned HTTP ${response.code}: ${response.message}")
                    }
                } else {
                    val body = response.body ?: throw Exception("Empty response body from voice server")
                    val isResume = (response.code == 206)
                    val streamContentLength = body.contentLength()
                    val totalExpectedBytes = if (isResume) existingBytes + streamContentLength else streamContentLength

                    val randomAccess = RandomAccessFile(tempZip, "rw")
                    if (isResume) {
                        randomAccess.seek(existingBytes)
                    } else {
                        randomAccess.setLength(0)
                        randomAccess.seek(0)
                    }

                    body.byteStream().use { input ->
                        val buffer = ByteArray(32768) // 32KB buffer for fast throughput
                        var bytesRead: Int
                        var currentTotal = if (isResume) existingBytes else 0L
                        var lastReportedPercent = 0

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            randomAccess.write(buffer, 0, bytesRead)
                            currentTotal += bytesRead

                            if (totalExpectedBytes > 0) {
                                val percent = ((currentTotal * 80) / totalExpectedBytes).toInt().coerceIn(5, 80)
                                if (percent > lastReportedPercent) {
                                    lastReportedPercent = percent
                                    val mbDownloaded = currentTotal / (1024 * 1024)
                                    val mbTotal = totalExpectedBytes / (1024 * 1024)
                                    _status.value = ModelStatus.Downloading(
                                        progressPercent = percent,
                                        currentItem = "Downloading neural weights ($mbDownloaded MB / $mbTotal MB)...",
                                        bytesDownloaded = currentTotal,
                                        totalBytes = totalExpectedBytes
                                    )
                                }
                            }
                        }
                    }
                    randomAccess.close()
                }

                // Extract Zip files cleanly
                _status.value = ModelStatus.Downloading(85, "Extracting voice packages...")
                extractZipSafely(tempZip, modelDir)

                // Flattens subdirectories if the zip contained a root folder like kokoro-en-v0_19/
                flattenModelDirectory(modelDir)

                // Write persistence manifest
                writeManifest()

                tempZip.delete()
                _status.value = ModelStatus.Ready
                success = true
                Log.i(tag, "Kokoro neural model installed successfully!")

            } catch (e: Exception) {
                Log.w(tag, "Download attempt $attempt failed: ${e.message}")
                if (attempt >= maxAttempts) {
                    _status.value = ModelStatus.Error(
                        "Download failed: ${e.localizedMessage}. Tap to retry or continue using System Voice."
                    )
                } else {
                    delay(2000L * attempt) // Exponential backoff
                }
            }
        }
    }

    private fun extractZipSafely(zipFile: File, destDir: File) {
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

    /**
     * If files were extracted into a nested directory (e.g. modelDir/kokoro-en-v0_19/model.onnx),
     * move them up to modelDir directly so modelFile.exists() finds them cleanly.
     */
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
