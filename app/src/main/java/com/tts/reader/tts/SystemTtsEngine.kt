package com.tts.reader.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

    private fun initTts() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = tts?.setLanguage(Locale.US)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(speed)
                tts?.setPitch(pitch)
                setupProgressListener()
                isInitialized = true
                _isReady.value = true
                Log.i(tag, "System TTS initialized successfully.")
            } else {
                Log.e(tag, "Failed to initialize System TTS: status=$status")
                _isReady.value = false
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
