package com.tts.reader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tts.reader.data.local.AppDatabase
import com.tts.reader.data.local.ChapterEntity
import com.tts.reader.data.scraper.NovelScraper
import com.tts.reader.tts.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReaderUiState(
    val isLoading: Boolean = false,
    val novelTitle: String = "Universal Reader",
    val chapterTitle: String = "No Chapter Loaded",
    val currentUrl: String = "",
    val paragraphs: List<String> = emptyList(),
    val sentences: List<String> = emptyList(),
    val sentenceToParagraphMap: List<Int> = emptyList(),
    val activeSentenceIndex: Int = 0,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val voiceMode: VoiceEngineMode = VoiceEngineMode.SYSTEM_OFFLINE,
    val isKokoroInstalled: Boolean = false,
    val nextChapterUrl: String? = null,
    val prevChapterUrl: String? = null,
    val modelStatus: ModelStatus = ModelStatus.NotDownloaded,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val chapterDao = db.chapterDao()
    private val scraper = NovelScraper()

    val modelManager = ModelManager(application)
    val systemTts = SystemTtsEngine(application)
    val kokoroTts = KokoroTtsEngine(application, modelManager)
    val audioPipeline = AudioStreamPipeline(application, systemTts, kokoroTts, viewModelScope)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelManager.status.collect { status ->
                val isInstalled = status is ModelStatus.Ready
                val currentInfo = _uiState.value.infoMessage
                val newInfo = if (status is ModelStatus.Ready && currentInfo?.contains("downloading", ignoreCase = true) == true) {
                    "Kokoro Neural Voice downloaded & ready! Natural AI speech activated."
                } else {
                    currentInfo
                }
                _uiState.value = _uiState.value.copy(
                    modelStatus = status,
                    isKokoroInstalled = isInstalled,
                    infoMessage = newInfo
                )
            }
        }
        viewModelScope.launch {
            audioPipeline.isPlaying.collect { playing ->
                _uiState.value = _uiState.value.copy(isPlaying = playing)
            }
        }
        viewModelScope.launch {
            audioPipeline.activeSentenceIndex.collect { index ->
                _uiState.value = _uiState.value.copy(activeSentenceIndex = index)
                saveProgress(index)
            }
        }
    }

    fun setVoiceMode(mode: VoiceEngineMode) {
        _uiState.value = _uiState.value.copy(voiceMode = mode)
        audioPipeline.setEngineMode(mode)

        if (mode == VoiceEngineMode.KOKORO_NEURAL && !modelManager.isModelReady()) {
            if (modelManager.status.value !is ModelStatus.Downloading) {
                downloadKokoroModel()
                _uiState.value = _uiState.value.copy(
                    infoMessage = "Downloading Kokoro Neural Voice (103 MB)... Using offline System Voice until download completes."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    infoMessage = "Kokoro Neural Voice is downloading. Using offline System Voice until download completes."
                )
            }
        }
    }

    fun clearInfoMessage() {
        _uiState.value = _uiState.value.copy(infoMessage = null)
    }

    fun loadUrl(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val cached = chapterDao.getChapterByUrl(url)
            if (cached != null) {
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
                return@launch
            }

            val result = scraper.scrape(url)
            result.onSuccess { scraped ->
                val entity = ChapterEntity(
                    url = url,
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
                    url = url,
                    nextUrl = scraped.nextChapterUrl,
                    prevUrl = scraped.prevChapterUrl,
                    startIndex = 0
                )

                scraped.nextChapterUrl?.let { prefetchNextChapter(it) }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load chapter: ${error.localizedMessage}"
                )
            }
        }
    }

    fun loadRawText(title: String, text: String) {
        val parsed = scraper.parseRawText(title, text)
        applyChapter(
            novelTitle = parsed.novelTitle,
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

        paragraphs.forEachIndexed { pIdx, paragraph ->
            val sentences = NovelScraper.splitIntoSentences(paragraph)
            for (s in sentences) {
                allSentences.add(s)
                sentenceMap.add(pIdx)
            }
        }

        audioPipeline.loadContent(allSentences, startIndex)

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

    private fun prefetchNextChapter(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = chapterDao.getChapterByUrl(url)
            if (existing == null) {
                scraper.scrape(url).getOrNull()?.let { nextScraped ->
                    chapterDao.insertChapter(
                        ChapterEntity(
                            url = url,
                            novelTitle = nextScraped.novelTitle,
                            chapterTitle = nextScraped.chapterTitle,
                            paragraphs = nextScraped.paragraphs,
                            nextChapterUrl = nextScraped.nextChapterUrl,
                            prevChapterUrl = nextScraped.prevChapterUrl
                        )
                    )
                }
            }
        }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            audioPipeline.pause()
        } else {
            audioPipeline.play()
        }
    }

    fun seekToSentence(index: Int) {
        audioPipeline.seekToSentence(index)
    }

    fun setSpeed(speed: Float) {
        audioPipeline.playbackSpeed = speed
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
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

    fun downloadKokoroModel() {
        viewModelScope.launch {
            modelManager.downloadModel()
        }
    }

    fun cancelKokoroDownload() {
        modelManager.cancelDownload()
    }

    fun deleteKokoroModel() {
        modelManager.deleteModel()
        setVoiceMode(VoiceEngineMode.SYSTEM_OFFLINE)
    }

    private fun saveProgress(sentenceIndex: Int) {
        val url = _uiState.value.currentUrl
        if (url.isNotBlank() && !url.startsWith("local_paste")) {
            viewModelScope.launch(Dispatchers.IO) {
                chapterDao.updateProgress(url, sentenceIndex, 0)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPipeline.release()
    }
}
