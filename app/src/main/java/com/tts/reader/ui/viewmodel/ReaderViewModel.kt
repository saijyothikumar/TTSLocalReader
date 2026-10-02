package com.tts.reader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tts.reader.TTSApp
import com.tts.reader.data.local.AppDatabase
import com.tts.reader.data.local.ChapterEntity
import com.tts.reader.data.scraper.CaptchaChallengeException
import com.tts.reader.data.scraper.NovelScraper
import com.tts.reader.data.scraper.TextSanitizer
import com.tts.reader.service.PlaybackService
import com.tts.reader.tts.AudioStreamPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReaderUiState(
    val isLoading: Boolean = false,
    val novelTitle: String = "",
    val chapterTitle: String = "No Chapter Loaded",
    val currentUrl: String = "",
    val paragraphs: List<String> = emptyList(),
    val sentences: List<String> = emptyList(),
    val sentenceToParagraphMap: List<Int> = emptyList(),
    val activeSentenceIndex: Int = 0,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val selectedVoiceName: String? = null,
    val availableVoices: List<String> = emptyList(),
    val nextChapterUrl: String? = null,
    val prevChapterUrl: String? = null,
    val cachedChapterCount: Int = 0,
    val cachedChapters: List<ChapterEntity> = emptyList(),
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val captchaChallengeUrl: String? = null
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val chapterDao = db.chapterDao()
    private val scraper = NovelScraper()

    val audioPipeline: AudioStreamPipeline
        get() = TTSApp.instance.audioPipeline

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        // Collect cached chapters from Room DB
        viewModelScope.launch {
            chapterDao.getAllCachedChapters().collect { chapters ->
                _uiState.value = _uiState.value.copy(
                    cachedChapterCount = chapters.size,
                    cachedChapters = chapters
                )
            }
        }

        // Collect playback state
        viewModelScope.launch {
            audioPipeline.isPlaying.collect { playing ->
                _uiState.value = _uiState.value.copy(isPlaying = playing)
            }
        }

        // Collect active sentence index & save progress
        viewModelScope.launch {
            audioPipeline.activeSentenceIndex.collect { index ->
                _uiState.value = _uiState.value.copy(activeSentenceIndex = index)
                saveProgress(index)
            }
        }

        // Load available installed system TTS voices
        viewModelScope.launch {
            delay(500) // Allow TextToSpeech engine time to complete initialization
            loadInstalledVoices()
        }
    }

    private fun loadInstalledVoices() {
        try {
            val voices = TTSApp.instance.systemTts.getAvailableVoices().map { it.name }
            val current = TTSApp.instance.systemTts.getCurrentVoiceName()
            _uiState.value = _uiState.value.copy(
                availableVoices = voices,
                selectedVoiceName = current
            )
        } catch (_: Exception) {}
    }

    fun setSystemVoice(voiceName: String) {
        val success = TTSApp.instance.systemTts.setVoiceByName(voiceName)
        if (success) {
            _uiState.value = _uiState.value.copy(selectedVoiceName = voiceName)
        }
    }

    fun setPitch(pitch: Float) {
        audioPipeline.pitch = pitch
        _uiState.value = _uiState.value.copy(pitch = pitch)
    }

    fun setSpeed(speed: Float) {
        audioPipeline.playbackSpeed = speed
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    fun clearErrorMessage() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun dismissCaptchaChallenge() {
        _uiState.value = _uiState.value.copy(captchaChallengeUrl = null)
    }

    fun onCaptchaSolved() {
        val url = _uiState.value.captchaChallengeUrl ?: _uiState.value.currentUrl
        dismissCaptchaChallenge()
        if (url.isNotBlank()) {
            loadUrl(url)
        }
    }

    fun loadUrl(url: String) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return

        // Guard against comments pages being requested directly
        if (!scraper.isValidChapterUrl(trimmedUrl)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Invalid chapter link. The link appears to point to comments or non-chapter content."
            )
            return
        }

        // If user pastes the URL of the chapter that's currently open, don't interrupt playback
        if (trimmedUrl == _uiState.value.currentUrl) {
            _uiState.value = _uiState.value.copy(
                infoMessage = "This chapter is already open."
            )
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, captchaChallengeUrl = null)

            val cached = chapterDao.getChapterByUrl(trimmedUrl)
            if (cached != null) {
                if (isCorruptedChapter(cached)) {
                    chapterDao.deleteChapter(cached.url)
                } else {
                    applyChapter(
                        novelTitle = cached.novelTitle,
                        chapterTitle = cached.chapterTitle,
                        paragraphs = cached.paragraphs,
                        url = cached.url,
                        nextUrl = cached.nextChapterUrl,
                        prevUrl = cached.prevChapterUrl,
                        startIndex = cached.lastSentenceIndex
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    // Continue prefetch chain for subsequent chapters
                    cached.nextChapterUrl?.let { prefetchNextChapters(it) }
                    return@launch
                }
            }

            val previousUrl = _uiState.value.currentUrl.takeIf { it.startsWith("http") }
            val result = scraper.scrape(trimmedUrl, referer = previousUrl)
            result.onSuccess { scraped ->
                val entity = ChapterEntity(
                    url = trimmedUrl,
                    novelTitle = scraped.novelTitle,
                    chapterTitle = scraped.chapterTitle,
                    paragraphs = scraped.paragraphs,
                    nextChapterUrl = scraped.nextChapterUrl,
                    prevChapterUrl = scraped.prevChapterUrl
                )
                chapterDao.insertChapter(entity)

                applyChapter(
                    novelTitle = scraped.novelTitle,
                    chapterTitle = scraped.chapterTitle,
                    paragraphs = scraped.paragraphs,
                    url = trimmedUrl,
                    nextUrl = scraped.nextChapterUrl,
                    prevUrl = scraped.prevChapterUrl,
                    startIndex = 0
                )

                scraped.nextChapterUrl?.let { prefetchNextChapters(it) }
            }.onFailure { error ->
                if (error is CaptchaChallengeException) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        captchaChallengeUrl = error.url,
                        errorMessage = "Bot protection detected. Tap below to solve challenge directly in the app."
                    )
                } else {
                    val friendlyMessage = when {
                        error is java.net.UnknownHostException || error is java.net.ConnectException ->
                            "Unable to connect to the novel website. Please check your internet connection and try again."
                        error is java.net.SocketTimeoutException ->
                            "The website took too long to respond. Tap reload or paste chapter text directly."
                        error.message?.contains("403", ignoreCase = true) == true || error.message?.contains("cloudflare", ignoreCase = true) == true ->
                            "The website has bot protection active. Solve challenge or paste chapter text directly."
                        error.message?.contains("No story content", ignoreCase = true) == true ->
                            "Could not detect story paragraphs on this page. You can paste the chapter text directly using the paste icon above."
                        else ->
                            "Unable to load chapter from this link. You can paste the text directly using the paste icon above."
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = friendlyMessage
                    )
                }
            }
        }
    }

    fun loadRawText(title: String, text: String) {
        val parsed = scraper.parseRawText(title, text)
        applyChapter(
            novelTitle = "",
            chapterTitle = parsed.chapterTitle,
            paragraphs = parsed.paragraphs,
            url = "local_paste_${System.currentTimeMillis()}",
            nextUrl = null,
            prevUrl = null,
            startIndex = 0
        )
    }

    private fun applyChapter(
        novelTitle: String,
        chapterTitle: String,
        paragraphs: List<String>,
        url: String,
        nextUrl: String?,
        prevUrl: String?,
        startIndex: Int
    ) {
        val allSentences = mutableListOf<String>()
        val sentenceMap = mutableListOf<Int>()

        // Prepend chapter title if present so TTS speaks it first as Sentence 0
        val cleanChapterTitle = chapterTitle.trim()
        val hasTitle = cleanChapterTitle.isNotBlank() && cleanChapterTitle != "No Chapter Loaded"
        if (hasTitle) {
            allSentences.add(cleanChapterTitle)
            sentenceMap.add(0) // Maps to chapter header (item 0 in reading list)
        }

        // Intelligently sanitize paragraphs: skip visual dividers (-------, ***, etc.) from voice synthesis
        paragraphs.forEachIndexed { pIdx, paragraph ->
            val speechSanitized = TextSanitizer.sanitizeForSpeech(paragraph)
            if (speechSanitized != null) {
                val sentences = NovelScraper.splitIntoSentences(speechSanitized)
                for (s in sentences) {
                    allSentences.add(s)
                    sentenceMap.add(if (hasTitle) pIdx + 1 else pIdx)
                }
            }
        }

        audioPipeline.loadContent(allSentences, sentenceMap, startIndex)

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            novelTitle = novelTitle,
            chapterTitle = chapterTitle,
            currentUrl = url,
            paragraphs = paragraphs,
            sentences = allSentences,
            sentenceToParagraphMap = sentenceMap,
            activeSentenceIndex = startIndex,
            nextChapterUrl = nextUrl,
            prevChapterUrl = prevUrl
        )
    }

    private fun isCorruptedChapter(chapter: ChapterEntity): Boolean {
        return chapter.url.contains("/comments/") ||
                chapter.chapterTitle.equals("Comments", ignoreCase = true) ||
                chapter.paragraphs.any { it.contains("this page is for comments only", ignoreCase = true) }
    }

    /**
     * Lookahead prefetcher for Next Chapter (N+1) and Next-Next Chapter (N+2).
     * Binds previous chapter links explicitly to guarantee backwards navigation works.
     */
    private fun prefetchNextChapters(firstNextUrl: String) {
        val currentUrl = _uiState.value.currentUrl
        if (!scraper.isValidChapterUrl(firstNextUrl, currentUrl)) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // 1. Prefetch Chapter N+1 with a polite 1-second delay so server sees natural reading cadence
            delay(1000)
            var next1 = chapterDao.getChapterByUrl(firstNextUrl)
            if (next1 != null && isCorruptedChapter(next1)) {
                chapterDao.deleteChapter(firstNextUrl)
                next1 = null
            }

            if (next1 == null) {
                val res1 = scraper.scrape(firstNextUrl, referer = currentUrl).getOrNull()
                if (res1 != null) {
                    val entity1 = ChapterEntity(
                        url = firstNextUrl,
                        novelTitle = res1.novelTitle,
                        chapterTitle = res1.chapterTitle,
                        paragraphs = res1.paragraphs,
                        nextChapterUrl = res1.nextChapterUrl,
                        prevChapterUrl = res1.prevChapterUrl ?: currentUrl
                    )
                    chapterDao.insertChapter(entity1)
                    next1 = entity1
                }
            }

            // 2. Prefetch Chapter N+2 (Next-Next) with a polite 1.2s throttle
            val nextNextUrl = next1?.nextChapterUrl
            if (!nextNextUrl.isNullOrBlank() && scraper.isValidChapterUrl(nextNextUrl, firstNextUrl)) {
                delay(1200)
                var next2 = chapterDao.getChapterByUrl(nextNextUrl)
                if (next2 != null && isCorruptedChapter(next2)) {
                    chapterDao.deleteChapter(nextNextUrl)
                    next2 = null
                }

                if (next2 == null) {
                    scraper.scrape(nextNextUrl, referer = firstNextUrl).getOrNull()?.let { res2 ->
                        val entity2 = ChapterEntity(
                            url = nextNextUrl,
                            novelTitle = res2.novelTitle,
                            chapterTitle = res2.chapterTitle,
                            paragraphs = res2.paragraphs,
                            nextChapterUrl = res2.nextChapterUrl,
                            prevChapterUrl = res2.prevChapterUrl ?: firstNextUrl
                        )
                        chapterDao.insertChapter(entity2)
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            audioPipeline.pause()
            PlaybackService.pausePlayback(getApplication())
        } else {
            PlaybackService.startPlayback(
                getApplication(),
                _uiState.value.novelTitle,
                _uiState.value.chapterTitle
            )
            audioPipeline.play()
        }
    }

    fun seekToSentence(index: Int) {
        audioPipeline.seekToSentence(index)
    }

    fun nextChapter() {
        val next = _uiState.value.nextChapterUrl
        if (!next.isNullOrBlank()) {
            loadUrl(next)
        }
    }

    fun prevChapter() {
        val prev = _uiState.value.prevChapterUrl
        if (!prev.isNullOrBlank()) {
            loadUrl(prev)
        }
    }

    fun selectChapter(url: String) {
        audioPipeline.stop()
        PlaybackService.stopPlayback(getApplication())
        loadUrl(url)
    }

    fun deleteCurrentChapter() {
        val url = _uiState.value.currentUrl
        audioPipeline.stop()
        PlaybackService.stopPlayback(getApplication())
        if (url.isNotBlank() && !url.startsWith("local_paste")) {
            viewModelScope.launch(Dispatchers.IO) {
                chapterDao.deleteChapter(url)
                _uiState.value = ReaderUiState(
                    infoMessage = "Current chapter removed from local cache."
                )
            }
        } else {
            _uiState.value = ReaderUiState(
                infoMessage = "Current content cleared."
            )
        }
    }

    fun deleteChapter(url: String) {
        if (url == _uiState.value.currentUrl) {
            deleteCurrentChapter()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            chapterDao.deleteChapter(url)
            _uiState.value = _uiState.value.copy(
                infoMessage = "Chapter removed from cache."
            )
        }
    }

    fun deleteChapters(urls: List<String>) {
        if (urls.isEmpty()) return
        val containsCurrent = urls.contains(_uiState.value.currentUrl)
        if (containsCurrent) {
            audioPipeline.stop()
            PlaybackService.stopPlayback(getApplication())
        }
        viewModelScope.launch(Dispatchers.IO) {
            chapterDao.deleteChapters(urls)
            if (containsCurrent) {
                _uiState.value = ReaderUiState(
                    infoMessage = "Deleted ${urls.size} chapter(s) from cache."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    infoMessage = "Deleted ${urls.size} chapter(s) from cache."
                )
            }
        }
    }

    fun clearAllCachedChapters() {
        audioPipeline.stop()
        PlaybackService.stopPlayback(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            chapterDao.clearAllChapters()
            _uiState.value = ReaderUiState(
                infoMessage = "All cached chapters cleared from local storage."
            )
        }
    }

    private fun saveProgress(sentenceIndex: Int) {
        val url = _uiState.value.currentUrl
        if (url.isNotBlank() && !url.startsWith("local_paste")) {
            viewModelScope.launch(Dispatchers.IO) {
                chapterDao.updateProgress(url, sentenceIndex, 0)
            }
        }
    }
}
