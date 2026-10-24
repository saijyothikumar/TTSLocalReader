package com.tts.reader.tts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

class SystemTtsEngine(private val context: Context) {

    private val tag = "SystemTtsEngine"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    var currentEnginePackage: String? = null
        private set

    /**
     * When speechClarityMode is TRUE, audio output is routed using USAGE_ASSISTANCE_ACCESSIBILITY.
     * This instructs the OS (especially devices with aggressive DSPs like Vivo/iQOO DeepX/Dolby)
     * to bypass media music equalizers, virtual surround, and heavy bass boosts that muffle human speech.
     */
    var speechClarityMode: Boolean = true
        set(value) {
            field = value
            applyAudioAttributes()
        }

    var speed: Float = 1.0f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
        }

    var pitch: Float = 1.0f
        set(value) {
            field = value
            tts?.setPitch(value)
        }

    var onSentenceStartListener: ((Int) -> Unit)? = null
    var onSentenceDoneListener: ((Int) -> Unit)? = null
    var onChunkStartListener: ((String) -> Unit)? = null
    var onChunkDoneListener: ((String) -> Unit)? = null
    var onRangeStartListener: ((utteranceId: String, start: Int, end: Int) -> Unit)? = null
    var onPlaybackCompletedListener: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun resolveBestEnginePackage(): String? {
        val googleEngine = "com.google.android.tts"
        return try {
            context.packageManager.getPackageInfo(googleEngine, 0)
            googleEngine
        } catch (_: Exception) {
            null // Fallback to system default
        }
    }

    fun initTts(enginePackage: String? = null, onInitComplete: ((Boolean) -> Unit)? = null) {
        val targetEngine = enginePackage ?: currentEnginePackage ?: resolveBestEnginePackage()
        currentEnginePackage = targetEngine

        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyAudioAttributes()

                val langResult = tts?.setLanguage(Locale.US)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }

                // Auto-select the highest quality offline voice
                autoSelectBestVoice()

                tts?.setSpeechRate(speed)
                tts?.setPitch(pitch)
                setupProgressListener()
                isInitialized = true
                _isReady.value = true
                Log.i(tag, "TTS initialized with engine=$currentEnginePackage, clarityMode=$speechClarityMode")
                onInitComplete?.invoke(true)
            } else {
                Log.e(tag, "Failed to initialize TTS engine ($targetEngine), status=$status")
                // If specific engine failed, retry once with system default
                if (targetEngine != null) {
                    Log.w(tag, "Retrying with default system engine fallback...")
                    currentEnginePackage = null
                    initTts(null, onInitComplete)
                } else {
                    _isReady.value = false
                    onInitComplete?.invoke(false)
                }
            }
        }

        tts = if (targetEngine != null) {
            TextToSpeech(context.applicationContext, listener, targetEngine)
        } else {
            TextToSpeech(context.applicationContext, listener)
        }
    }

    private fun applyAudioAttributes() {
        val usage = if (speechClarityMode) {
            AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
        } else {
            AudioAttributes.USAGE_MEDIA
        }
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(usage)
            .build()
        tts?.setAudioAttributes(audioAttributes)
    }

    private fun autoSelectBestVoice() {
        try {
            val bestVoice = getAvailableVoices().firstOrNull()
            if (bestVoice != null) {
                tts?.voice = bestVoice
                Log.i(tag, "Auto-selected high-clarity voice: ${bestVoice.name} (quality=${bestVoice.quality})")
            }
        } catch (e: Exception) {
            Log.w(tag, "Voice auto-selection fallback", e)
        }
    }

    fun getAvailableVoices(): List<Voice> {
        return try {
            tts?.voices?.filter { voice ->
                // Must not require network connection
                !voice.isNetworkConnectionRequired &&
                // Must not be missing local installation files
                !(voice.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) ?: false) &&
                // Filter for English (or primary book language)
                voice.locale.language.startsWith("en")
            }?.sortedWith(
                compareByDescending<Voice> { voice ->
                    var score = voice.quality
                    val nameLower = voice.name.lowercase()
                    if (nameLower.contains("neural")) score += 100
                    if (nameLower.contains("local")) score += 50
                    if (nameLower.contains("high")) score += 30
                    if (nameLower.contains("compact") || nameLower.contains("low")) score -= 100
                    score
                }.thenBy { it.name }
            ) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCurrentVoiceName(): String? = tts?.voice?.name

    fun setVoiceByName(voiceName: String): Boolean {
        return try {
            val voice = tts?.voices?.firstOrNull { it.name == voiceName }
            if (voice != null) {
                tts?.voice = voice
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getAvailableEngines(): List<Pair<String, String>> {
        return try {
            tts?.engines?.map { it.name to it.label } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getCurrentEngineLabel(): String {
        val currentPkg = currentEnginePackage ?: tts?.defaultEngine
        val matched = tts?.engines?.firstOrNull { it.name == currentPkg }
        return matched?.label ?: currentPkg ?: "System Default"
    }

    fun switchEngine(enginePackage: String, onComplete: ((Boolean) -> Unit)? = null) {
        release()
        initTts(enginePackage, onComplete)
    }

    fun openSystemTtsSettings(ctx: Context) {
        try {
            val intent = Intent("com.android.settings.TTS_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                ctx.startActivity(intent)
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != null) {
                    onChunkStartListener?.invoke(utteranceId)
                    utteranceId.toIntOrNull()?.let { index ->
                        onSentenceStartListener?.invoke(index)
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null) {
                    onChunkDoneListener?.invoke(utteranceId)
                    utteranceId.toIntOrNull()?.let { index ->
                        onSentenceDoneListener?.invoke(index)
                    }
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId != null) {
                    onRangeStartListener?.invoke(utteranceId, start, end)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.w(tag, "Utterance error on: $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(tag, "Utterance error on: $utteranceId (code $errorCode)")
            }
        })
    }

    fun speakSentence(sentenceIndex: Int, text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        speakChunk(sentenceIndex.toString(), text, queueMode)
    }

    fun speakChunk(utteranceId: String, text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH) {
        if (!isInitialized || tts == null) return

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, queueMode, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _isReady.value = false
    }
}
