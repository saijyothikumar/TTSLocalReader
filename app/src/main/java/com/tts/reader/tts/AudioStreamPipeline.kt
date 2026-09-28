package com.tts.reader.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SpeechChunk(
    val chunkIndex: Int,
    val text: String,
    val sentenceIndices: List<Int>,
    val sentenceCharOffsets: List<Int>
)

class AudioStreamPipeline(
    private val context: Context,
    val systemTts: SystemTtsEngine,
    private val scope: CoroutineScope
) {
    private val tag = "AudioStreamPipeline"

    private var sentences: List<String> = emptyList()
    private var speechChunks: List<SpeechChunk> = emptyList()
    private var activeSubChunk: SpeechChunk? = null
    private var currentSentenceIndex = 0
    private var currentChunkIndex = 0

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _activeSentenceIndex = MutableStateFlow(0)
    val activeSentenceIndex: StateFlow<Int> = _activeSentenceIndex

    var playbackSpeed: Float = 1.0f
        set(value) {
            field = value
            systemTts.speed = value
        }

    var pitch: Float = 1.0f
        set(value) {
            field = value
            systemTts.pitch = value
        }

    init {
        setupSystemTtsListeners()
    }

    private fun setupSystemTtsListeners() {
        systemTts.onChunkStartListener = { utteranceId ->
            if (_isPlaying.value) {
                if (utteranceId.startsWith("subchunk_")) {
                    activeSubChunk?.let { sub ->
                        if (sub.sentenceIndices.isNotEmpty()) {
                            val first = sub.sentenceIndices[0]
                            _activeSentenceIndex.value = first
                            currentSentenceIndex = first
                        }
                    }
                    // Queue next chunk in advance for zero-gap playback
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
            if (_isPlaying.value) {
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

    fun loadContent(
        sentenceList: List<String>,
        sentenceToParagraphMap: List<Int> = emptyList(),
        startIndex: Int = 0
    ) {
        stop()
        sentences = sentenceList
        currentSentenceIndex = startIndex.coerceIn(0, (sentenceList.size - 1).coerceAtLeast(0))
        _activeSentenceIndex.value = currentSentenceIndex

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
        if (sentences.isEmpty() || speechChunks.isEmpty()) return
        if (_isPlaying.value) return

        _isPlaying.value = true
        systemTts.speed = playbackSpeed
        systemTts.pitch = pitch

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

    fun pause() {
        _isPlaying.value = false
        systemTts.stop()
    }

    fun stop() {
        pause()
        systemTts.stop()
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

    fun release() {
        stop()
        systemTts.release()
    }
}
