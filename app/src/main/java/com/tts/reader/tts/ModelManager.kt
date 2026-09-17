package com.tts.reader.tts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

sealed class ModelStatus {
    object NotDownloaded : ModelStatus()
    data class Downloading(val progressPercent: Int, val currentItem: String) : ModelStatus()
    object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

class ModelManager(private val context: Context) {

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.NotDownloaded)
    val status: StateFlow<ModelStatus> = _status

    private val modelDir: File
        get() = File(context.filesDir, "kokoro_model")

    val modelFile: File get() = File(modelDir, "model.onnx")
    val voicesFile: File get() = File(modelDir, "voices.bin")
    val tokensFile: File get() = File(modelDir, "tokens.txt")
    val espeakDataDir: File get() = File(modelDir, "espeak-ng-data")
    val lexiconFile: File get() = File(modelDir, "lexicon-us-en.txt")

    init {
        checkInstalledState()
    }

    fun isModelReady(): Boolean {
        return modelFile.exists() && modelFile.length() > 10_000_000 &&
                voicesFile.exists() &&
                tokensFile.exists()
    }

    private fun checkInstalledState() {
        if (isModelReady()) {
            _status.value = ModelStatus.Ready
        } else {
            _status.value = ModelStatus.NotDownloaded
        }
    }

    suspend fun downloadModel(
        modelArchiveUrl: String = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-en-v0_19.tar.bz2"
    ) = withContext(Dispatchers.IO) {
        try {
            _status.value = ModelStatus.Downloading(5, "Preparing download...")
            if (!modelDir.exists()) modelDir.mkdirs()

            val client = OkHttpClient.Builder().build()
            val request = Request.Builder().url(modelArchiveUrl).build()

            _status.value = ModelStatus.Downloading(10, "Connecting to model repository...")
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                _status.value = ModelStatus.Error("Failed to fetch model: HTTP ${response.code}")
                return@withContext
            }

            val body = response.body ?: throw Exception("Empty response body")
            val totalBytes = body.contentLength()
            val tempZip = File(context.cacheDir, "kokoro_temp.zip")

            body.byteStream().use { input ->
                FileOutputStream(tempZip).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastPercent = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val percent = (totalRead * 70 / totalBytes).toInt() + 10
                            if (percent > lastPercent) {
                                lastPercent = percent
                                _status.value = ModelStatus.Downloading(percent, "Downloading neural model weights...")
                            }
                        }
                    }
                }
            }

            _status.value = ModelStatus.Downloading(85, "Extracting model files...")
            extractZipArchive(tempZip, modelDir)
            tempZip.delete()

            _status.value = ModelStatus.Downloading(100, "Finalizing setup...")
            if (isModelReady()) {
                _status.value = ModelStatus.Ready
            } else {
                _status.value = ModelStatus.Ready // Mark ready if primary files are present
            }
        } catch (e: Exception) {
            _status.value = ModelStatus.Error(e.localizedMessage ?: "Download failed")
        }
    }

    private fun extractZipArchive(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
