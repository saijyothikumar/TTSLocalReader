package com.tts.reader.tts

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioChunk(
    val samples: ShortArray,
    val sampleRate: Int = 24000,
    val sentenceIndex: Int,
    val sentenceText: String
)

class KokoroTtsEngine(
    private val context: Context,
    private val modelManager: ModelManager
) {
    private val tag = "KokoroTtsEngine"
    private var nativeTts: Any? = null
    private var isNativeInitialized = false

    var speed: Float = 1.0f
    var voiceId: Int = 0

    init {
        tryInitNative()
    }

    fun isAvailable(): Boolean {
        if (!isNativeInitialized) {
            tryInitNative()
        }
        return isNativeInitialized && nativeTts != null
    }

    fun tryInitNative(): Boolean {
        if (!modelManager.isModelReady()) {
            return false
        }

        try {
            val configClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsConfig")
            val kokoroConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig")
            val ttsClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")

            val kokoroConfig = kokoroConfigClass.getDeclaredConstructor().newInstance()
            kokoroConfigClass.getField("model").set(kokoroConfig, modelManager.modelFile.absolutePath)
            kokoroConfigClass.getField("voices").set(kokoroConfig, modelManager.voicesFile.absolutePath)
            kokoroConfigClass.getField("tokens").set(kokoroConfig, modelManager.tokensFile.absolutePath)
            kokoroConfigClass.getField("dataDir").set(kokoroConfig, modelManager.espeakDataDir.absolutePath)
            if (modelManager.lexiconFile.exists()) {
                kokoroConfigClass.getField("lexicon").set(kokoroConfig, modelManager.lexiconFile.absolutePath)
            }

            val config = configClass.getDeclaredConstructor().newInstance()
            val modelConfig = configClass.getField("model").get(config)
            modelConfig.javaClass.getField("kokoro").set(modelConfig, kokoroConfig)
            modelConfig.javaClass.getField("numThreads").set(modelConfig, 2)

            nativeTts = ttsClass.getDeclaredConstructor(configClass).newInstance(config)
            isNativeInitialized = true
            Log.i(tag, "Native Sherpa-ONNX Kokoro neural engine initialized.")
            return true
        } catch (e: ClassNotFoundException) {
            Log.d(tag, "Sherpa-ONNX AAR not found. Neural inference inactive.")
            isNativeInitialized = false
            return false
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize native Kokoro engine", e)
            isNativeInitialized = false
            return false
        }
    }

    suspend fun synthesizeSentence(sentenceIndex: Int, text: String): AudioChunk? = withContext(Dispatchers.Default) {
        if (!isAvailable()) return@withContext null

        try {
            val tts = nativeTts ?: return@withContext null
            val generateMethod = tts.javaClass.getMethod("generate", String::class.java, Int::class.java, Float::class.java)
            val audioObj = generateMethod.invoke(tts, text, voiceId, speed)

            val samplesField = audioObj.javaClass.getField("samples")
            val sampleRateField = audioObj.javaClass.getField("sampleRate")

            val floatSamples = samplesField.get(audioObj) as FloatArray
            val sampleRate = sampleRateField.getInt(audioObj)

            val shortSamples = ShortArray(floatSamples.size)
            for (i in floatSamples.indices) {
                val clamped = floatSamples[i].coerceIn(-1.0f, 1.0f)
                shortSamples[i] = (clamped * 32767).toInt().toShort()
            }

            return@withContext AudioChunk(
                samples = shortSamples,
                sampleRate = sampleRate,
                sentenceIndex = sentenceIndex,
                sentenceText = text
            )
        } catch (e: Exception) {
            Log.e(tag, "Kokoro neural synthesis error", e)
            return@withContext null
        }
    }

    fun release() {
        nativeTts = null
        isNativeInitialized = false
    }
}
