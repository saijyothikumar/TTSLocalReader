package com.tts.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class VoiceEngineMode {
    SYSTEM_OFFLINE, // Instant offline built-in Android voice (0 MB download required)
    KOKORO_NEURAL   // Studio-quality Kokoro AI voice
}

data class SpeechChunk(
    val chunkIndex: Int,
    val text: String,
    val sentenceIndices: List<Int>,
    val sentenceCharOffsets: List<Int>
)

class AudioStreamPipeline(
    private val context: Context,
    val systemTts: SystemTtsEngine,
    val kokoroTts: KokoroTtsEngine,
    private val scope: CoroutineScope
) {
    private val tag = "AudioStreamPipeline"
    private val sampleRate = 24000
    private var audioTrack: AudioTrack? = null

    private var sentences: List<String> = emptyList()
    private var speechChunks: List<SpeechChunk> = emptyList()
    private var activeSubChunk: SpeechChunk? = null
    private var currentSentenceIndex = 0
    private var currentChunkIndex = 0

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _activeSentenceIndex = MutableStateFlow(0)
    val activeSentenceIndex: StateFlow<Int> = _activeSentenceIndex

    private val _voiceMode = MutableStateFlow(VoiceEngineMode.SYSTEM_OFFLINE)
    val voiceMode: StateFlow<VoiceEngineMode> = _voiceMode

    var playbackSpeed: Float = 1.0f
        set(value) {
            field = value
            systemTts.speed = value
            kokoroTts.speed = value
        }

    // Cache for Neural ONNX PCM chunks
    private val neuralChunkCache = ConcurrentHashMap<Int, AudioChunk>()

    private var neuralPlaybackJob: Job? = null
    private var neuralLookaheadJob: Job? = null
    private var neuralPositionJob: Job? = null
    private val sentenceFrameRanges = mutableListOf<Pair<Long, Int>>()
    private var totalFramesWritten = 0L

    init {
        setupSystemTtsListeners()
        initAudioTrack()
    }

    private fun setupSystemTtsListeners() {
        systemTts.onChunkStartListener = { utteranceId ->
            if (_voiceMode.value == VoiceEngineMode.SYSTEM_OFFLINE && _isPlaying.value) {
                if (utteranceId.startsWith("subchunk_")) {
                    activeSubChunk?.let { sub ->
                        if (sub.sentenceIndices.isNotEmpty()) {
                            val first = sub.sentenceIndices[0]
                            _activeSentenceIndex.value = first
                            currentSentenceIndex = first
                        }
                    }
                    // Queue next chunk in advance
                    val nextChunkIdx = currentChunkIndex + 1
                    if (nextChunkIdx < speechChunks.size) {
                        systemTts.speakChunk("chunk_$nextChunkIdx", speechChunks[nextChunkIdx].text, TextToSpeech.QUEUE_ADD)
                    }
                } else if (utteranceId.startsWith("chunk_")) {
                    val chunkIdx = utteranceId.removePrefix("chunk_").toIntOrNull()
                    if (chunkIdx != null && chunkIdx in speechChunks.indices) {
                        currentChunkIndex = chunkIdx
                        val chunk = speechChunks[chunkIdx]
                        if (chunk.sentenceIndices.isNotEmpty()) {
                            val first = chunk.sentenceIndices[0]
                            _activeSentenceIndex.value = first
                            currentSentenceIndex = first
                        }

                        // Queue exactly 1 lookahead chunk for continuous gapless playback
                        val nextChunkIdx = chunkIdx + 1
                        if (nextChunkIdx < speechChunks.size) {
                            systemTts.speakChunk("chunk_$nextChunkIdx", speechChunks[nextChunkIdx].text, TextToSpeech.QUEUE_ADD)
                        }
                    }
                }
            }
        }

        systemTts.onRangeStartListener = { utteranceId, start, _ ->
            if (_voiceMode.value == VoiceEngineMode.SYSTEM_OFFLINE && _isPlaying.value) {
                val targetChunk = if (utteranceId.startsWith("subchunk_")) {
                    activeSubChunk
                } else {
                    val chunkIdx = utteranceId.removePrefix("chunk_").toIntOrNull()
                    if (chunkIdx != null && chunkIdx in speechChunks.indices) speechChunks[chunkIdx] else null
                }

                if (targetChunk != null && targetChunk.sentenceIndices.isNotEmpty()) {
                    var matchedSentence = targetChunk.sentenceIndices[0]
                    for (k in targetChunk.sentenceCharOffsets.indices) {
                        if (start >= targetChunk.sentenceCharOffsets[k]) {
                            matchedSentence = targetChunk.sentenceIndices[k]
                        } else {
                            break
                        }
                    }
                    _activeSentenceIndex.value = matchedSentence
                    currentSentenceIndex = matchedSentence
                }
            }
        }

        systemTts.onChunkDoneListener = { utteranceId ->
            if (_voiceMode.value == VoiceEngineMode.SYSTEM_OFFLINE) {
                val isLast = if (utteranceId.startsWith("subchunk_")) {
                    currentChunkIndex >= speechChunks.size - 1
                } else {
                    val chunkIdx = utteranceId.removePrefix("chunk_").toIntOrNull()
                    chunkIdx != null && chunkIdx >= speechChunks.size - 1
                }
                if (isLast) {
                    _isPlaying.value = false
                }
            }
        }
    }

    private fun initAudioTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBufferSize * 4,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }

    fun setEngineMode(mode: VoiceEngineMode) {
        val wasPlaying = _isPlaying.value
        pause()
        _voiceMode.value = mode
        if (wasPlaying) {
            play()
        }
    }

    fun loadContent(
        sentenceList: List<String>,
        sentenceToParagraphMap: List<Int> = emptyList(),
        startIndex: Int = 0
    ) {
        stop()
        sentences = sentenceList
        currentSentenceIndex = startIndex.coerceIn(0, (sentenceList.size - 1).coerceAtLeast(0))
        _activeSentenceIndex.value = currentSentenceIndex
        neuralChunkCache.clear()
        sentenceFrameRanges.clear()
        totalFramesWritten = 0L

        // Group sentences into continuous speech chunks (paragraphs) to eliminate inter-sentence pauses
        val chunkList = mutableListOf<SpeechChunk>()
        if (sentenceList.isNotEmpty()) {
            var currentPIdx = if (sentenceToParagraphMap.isNotEmpty()) sentenceToParagraphMap[0] else 0
            var sb = StringBuilder()
            var currentSentences = mutableListOf<Int>()
            var currentOffsets = mutableListOf<Int>()
            var currentOffset = 0

            for (i in sentenceList.indices) {
                val pIdx = sentenceToParagraphMap.getOrElse(i) { i }
                if (pIdx != currentPIdx && currentSentences.isNotEmpty()) {
                    chunkList.add(
                        SpeechChunk(
                            chunkIndex = chunkList.size,
                            text = sb.toString(),
                            sentenceIndices = currentSentences.toList(),
                            sentenceCharOffsets = currentOffsets.toList()
                        )
                    )
                    currentPIdx = pIdx
                    sb = StringBuilder()
                    currentSentences = mutableListOf()
                    currentOffsets = mutableListOf()
                    currentOffset = 0
                }

                if (sb.isNotEmpty()) {
                    sb.append(" ")
                    currentOffset++
                }
                currentOffsets.add(currentOffset)
                currentSentences.add(i)
                sb.append(sentenceList[i])
                currentOffset += sentenceList[i].length
            }

            if (currentSentences.isNotEmpty()) {
                chunkList.add(
                    SpeechChunk(
                        chunkIndex = chunkList.size,
                        text = sb.toString(),
                        sentenceIndices = currentSentences.toList(),
                        sentenceCharOffsets = currentOffsets.toList()
                    )
                )
            }
        }
        speechChunks = chunkList
        activeSubChunk = null
    }

    fun play() {
        if (sentences.isEmpty()) return
        if (_isPlaying.value) return

        _isPlaying.value = true

        // Check if user requested Kokoro Neural and it is genuinely available
        if (_voiceMode.value == VoiceEngineMode.KOKORO_NEURAL && kokoroTts.isAvailable()) {
            playWithKokoroNeural()
        } else {
            // Default instant offline system playback (works out of the box with zero setup)
            playWithSystemTts()
        }
    }

    private fun playWithSystemTts() {
        if (speechChunks.isEmpty()) return
        systemTts.speed = playbackSpeed

        var targetChunkIdx = 0
        for (i in speechChunks.indices) {
            if (currentSentenceIndex in speechChunks[i].sentenceIndices) {
                targetChunkIdx = i
                break
            }
        }

        currentChunkIndex = targetChunkIdx
        val chunk = speechChunks[targetChunkIdx]
        val sentencePos = chunk.sentenceIndices.indexOf(currentSentenceIndex)

        if (sentencePos > 0) {
            val startChar = chunk.sentenceCharOffsets[sentencePos]
            val remainingText = chunk.text.substring(startChar)
            val subSentenceIndices = chunk.sentenceIndices.subList(sentencePos, chunk.sentenceIndices.size)
            val subOffsets = chunk.sentenceCharOffsets.subList(sentencePos, chunk.sentenceCharOffsets.size).map { it - startChar }
            val subChunk = SpeechChunk(targetChunkIdx, remainingText, subSentenceIndices, subOffsets)
            activeSubChunk = subChunk
            systemTts.speakChunk("subchunk_$targetChunkIdx", remainingText, TextToSpeech.QUEUE_FLUSH)
        } else {
            activeSubChunk = null
            systemTts.speakChunk("chunk_$targetChunkIdx", chunk.text, TextToSpeech.QUEUE_FLUSH)
        }
    }

    private fun playWithKokoroNeural() {
        val track = audioTrack ?: return
        track.play()

        startNeuralLookaheadWorker()
        startNeuralStreamingWorker()
        startNeuralPositionTracker()
    }

    fun pause() {
        _isPlaying.value = false
        systemTts.stop()

        audioTrack?.pause()
        neuralPlaybackJob?.cancel()
        neuralLookaheadJob?.cancel()
        neuralPositionJob?.cancel()
    }

    fun stop() {
        pause()
        systemTts.stop()

        audioTrack?.stop()
        audioTrack?.flush()
        sentenceFrameRanges.clear()
        totalFramesWritten = 0L
    }

    fun seekToSentence(index: Int) {
        if (sentences.isEmpty()) return
        val target = index.coerceIn(0, sentences.size - 1)
        val wasPlaying = _isPlaying.value

        stop()
        currentSentenceIndex = target
        _activeSentenceIndex.value = target

        if (wasPlaying) {
            play()
        }
    }

    private fun startNeuralLookaheadWorker() {
        neuralLookaheadJob?.cancel()
        neuralLookaheadJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isPlaying.value) {
                for (offset in 0..2) {
                    val idx = currentSentenceIndex + offset
                    if (idx < sentences.size && !neuralChunkCache.containsKey(idx)) {
                        kokoroTts.synthesizeSentence(idx, sentences[idx])?.let { chunk ->
                            neuralChunkCache[idx] = chunk
                        }
                    }
                }
                kotlinx.coroutines.delay(120)
            }
        }
    }

    private fun startNeuralStreamingWorker() {
        neuralPlaybackJob?.cancel()
        neuralPlaybackJob = scope.launch(Dispatchers.IO) {
            val track = audioTrack ?: return@launch

            while (isActive && _isPlaying.value && currentSentenceIndex < sentences.size) {
                val idx = currentSentenceIndex

                var chunk = neuralChunkCache[idx]
                if (chunk == null) {
                    chunk = kokoroTts.synthesizeSentence(idx, sentences[idx])
                    if (chunk != null) {
                        neuralChunkCache[idx] = chunk
                    }
                }

                if (chunk != null) {
                    val samples = chunk.samples
                    var written = 0
                    while (written < samples.size && isActive && _isPlaying.value) {
                        val toWrite = samples.size - written
                        val result = track.write(samples, written, toWrite, AudioTrack.WRITE_BLOCKING)
                        if (result > 0) {
                            written += result
                            totalFramesWritten += result
                        }
                    }

                    // Natural 100ms subtle pause between sentences
                    val pause = ShortArray((sampleRate * 0.10).toInt())
                    track.write(pause, 0, pause.size, AudioTrack.WRITE_BLOCKING)
                    totalFramesWritten += pause.size

                    synchronized(sentenceFrameRanges) {
                        sentenceFrameRanges.add(Pair(totalFramesWritten, idx))
                    }

                    neuralChunkCache.remove(idx - 3)
                    currentSentenceIndex++
                } else {
                    // Fallback to System TTS if neural synthesis missed
                    scope.launch(Dispatchers.Main) {
                        playWithSystemTts()
                    }
                    break
                }
            }

            if (currentSentenceIndex >= sentences.size) {
                _isPlaying.value = false
            }
        }
    }

    private fun startNeuralPositionTracker() {
        neuralPositionJob?.cancel()
        neuralPositionJob = scope.launch(Dispatchers.Default) {
            val track = audioTrack ?: return@launch

            while (isActive && _isPlaying.value) {
                val headPosition = track.playbackHeadPosition.toLong()

                synchronized(sentenceFrameRanges) {
                    for ((endFrame, sentenceIdx) in sentenceFrameRanges) {
                        if (headPosition < endFrame) {
                            if (_activeSentenceIndex.value != sentenceIdx) {
                                _activeSentenceIndex.value = sentenceIdx
                            }
                            break
                        }
                    }
                }
                kotlinx.coroutines.delay(40)
            }
        }
    }

    fun release() {
        stop()
        systemTts.release()
        audioTrack?.release()
        audioTrack = null
    }
}
