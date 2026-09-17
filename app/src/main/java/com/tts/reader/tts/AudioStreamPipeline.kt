package com.tts.reader.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AudioStreamPipeline(
    private val ttsEngine: KokoroTtsEngine,
    private val scope: CoroutineScope
) {
    private val tag = "AudioStreamPipeline"
    private val sampleRate = 24000
    private var audioTrack: AudioTrack? = null

    private var sentences: List<String> = emptyList()
    private var currentPlayingIndex = 0

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _activeSentenceIndex = MutableStateFlow(0)
    val activeSentenceIndex: StateFlow<Int> = _activeSentenceIndex

    // Map of sentenceIndex -> AudioChunk
    private val chunkCache = ConcurrentHashMap<Int, AudioChunk>()

    private var playbackJob: Job? = null
    private var lookaheadJob: Job? = null
    private var positionTrackerJob: Job? = null

    // Track frame intervals for sample-accurate highlighting
    private val sentenceFrameRanges = mutableListOf<Pair<Long, Int>>() // Pair<EndFrame, SentenceIndex>
    private var totalFramesWritten = 0L

    init {
        initAudioTrack()
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

    fun loadContent(sentenceList: List<String>, startIndex: Int = 0) {
        stop()
        sentences = sentenceList
        currentPlayingIndex = startIndex.coerceIn(0, (sentenceList.size - 1).coerceAtLeast(0))
        _activeSentenceIndex.value = currentPlayingIndex
        chunkCache.clear()
        sentenceFrameRanges.clear()
        totalFramesWritten = 0L
    }

    fun play() {
        if (sentences.isEmpty()) return
        if (_isPlaying.value) return

        _isPlaying.value = true
        audioTrack?.play()

        startLookaheadWorker()
        startStreamingPlaybackWorker()
        startPositionTracker()
    }

    fun pause() {
        _isPlaying.value = false
        audioTrack?.pause()
        playbackJob?.cancel()
        lookaheadJob?.cancel()
        positionTrackerJob?.cancel()
    }

    fun stop() {
        pause()
        audioTrack?.stop()
        audioTrack?.flush()
        sentenceFrameRanges.clear()
        totalFramesWritten = 0L
    }

    fun seekToSentence(index: Int) {
        val targetIndex = index.coerceIn(0, sentences.size - 1)
        val wasPlaying = _isPlaying.value
        stop()
        currentPlayingIndex = targetIndex
        _activeSentenceIndex.value = targetIndex
        if (wasPlaying) {
            play()
        }
    }

    private fun startLookaheadWorker() {
        lookaheadJob?.cancel()
        lookaheadJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isPlaying.value) {
                // Ensure the next 3 sentences are pre-rendered into the cache
                for (offset in 0..2) {
                    val idx = currentPlayingIndex + offset
                    if (idx < sentences.size && !chunkCache.containsKey(idx)) {
                        val text = sentences[idx]
                        val chunk = ttsEngine.synthesizeSentence(idx, text)
                        chunkCache[idx] = chunk
                    }
                }
                kotlinx.coroutines.delay(100)
            }
        }
    }

    private fun startStreamingPlaybackWorker() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            val track = audioTrack ?: return@launch

            while (isActive && _isPlaying.value && currentPlayingIndex < sentences.size) {
                val idx = currentPlayingIndex

                // Fetch pre-synthesized chunk or wait for it
                var chunk = chunkCache[idx]
                if (chunk == null) {
                    chunk = ttsEngine.synthesizeSentence(idx, sentences[idx])
                    chunkCache[idx] = chunk
                }

                // Write PCM audio data into AudioTrack continuous buffer
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

                // Subtle breath pause between sentences (120ms of blank PCM)
                val pauseSamples = ShortArray((sampleRate * 0.12).toInt())
                track.write(pauseSamples, 0, pauseSamples.size, AudioTrack.WRITE_BLOCKING)
                totalFramesWritten += pauseSamples.size

                synchronized(sentenceFrameRanges) {
                    sentenceFrameRanges.add(Pair(totalFramesWritten, idx))
                }

                // Clean up older cache entries to keep memory low
                chunkCache.remove(idx - 3)
                currentPlayingIndex++
            }

            if (currentPlayingIndex >= sentences.size) {
                _isPlaying.value = false
            }
        }
    }

    private fun startPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = scope.launch(Dispatchers.Default) {
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
                kotlinx.coroutines.delay(40) // 25 fps UI update sync
            }
        }
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }
}
