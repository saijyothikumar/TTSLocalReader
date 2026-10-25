package com.tts.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tts.reader.data.local.ChapterEntity
import com.tts.reader.data.local.NovelEntity
import com.tts.reader.data.scraper.TextSanitizer
import com.tts.reader.ui.theme.*
import com.tts.reader.ui.viewmodel.ReaderViewModel
import com.tts.reader.ui.viewmodel.SleepMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

sealed class DeleteTarget {
    data class SingleChapter(val chapter: ChapterEntity) : DeleteTarget()
    data class EntireNovel(val novel: NovelEntity) : DeleteTarget()
    data class SelectedChapters(val urls: List<String>) : DeleteTarget()
    object ClearAll : DeleteTarget()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboardManager = LocalClipboardManager.current

    var inputUrl by remember { mutableStateOf("") }
    var rawTextDialogVisible by remember { mutableStateOf(false) }
    var novelBookshelfVisible by remember { mutableStateOf(false) }
    var voiceSettingsVisible by remember { mutableStateOf(false) }
    var openUrlDialogVisible by remember { mutableStateOf(false) }
    var chapterInfoDialogVisible by remember { mutableStateOf(false) }
    var sleepSheetVisible by remember { mutableStateOf(false) }
    var speedSheetVisible by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DeleteTarget?>(null) }

    var rawInputText by remember { mutableStateOf("") }
    var rawInputTitle by remember { mutableStateOf("") }

    // Auto-scroll reading view to keep active sentence visible
    val activeParagraphIdx = uiState.sentenceToParagraphMap.getOrNull(uiState.activeSentenceIndex) ?: 0
    LaunchedEffect(activeParagraphIdx) {
        if (uiState.paragraphs.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(activeParagraphIdx)
            }
        }
    }

    // Dismiss virtual keyboard automatically when user scrolls the reading list
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    Scaffold(
        containerColor = ObsidianBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianSurface,
                    titleContentColor = TextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.onOpenBookshelf()
                        novelBookshelfVisible = true
                    }) {
                        BadgedBox(
                            badge = {
                                if (uiState.cachedChapterCount > 0) {
                                    Badge(
                                        containerColor = AmberPrimary,
                                        contentColor = ObsidianBackground
                                    ) {
                                        Text(
                                            text = "${uiState.cachedChapterCount}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "Novel Bookshelf",
                                tint = AmberPrimary
                            )
                        }
                    }
                },
                title = {
                    val showNovelTitle = uiState.novelTitle.isNotBlank() &&
                            !uiState.novelTitle.equals(uiState.chapterTitle, ignoreCase = true) &&
                            !uiState.chapterTitle.contains(uiState.novelTitle, ignoreCase = true) &&
                            uiState.novelTitle != "Universal Reader" &&
                            uiState.novelTitle != "Web Novel"

                    Column(
                        modifier = Modifier.clickable {
                            if (uiState.currentUrl.isNotBlank()) {
                                chapterInfoDialogVisible = true
                            }
                        }
                    ) {
                        Text(
                            text = uiState.chapterTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showNovelTitle) {
                            Text(
                                text = uiState.novelTitle,
                                style = MaterialTheme.typography.bodySmall.copy(color = AmberPrimary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    // Show chapter URL copy / info button when chapter is active
                    if (uiState.currentUrl.isNotBlank() && !uiState.currentUrl.startsWith("local_paste")) {
                        IconButton(onClick = { chapterInfoDialogVisible = true }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Chapter Source Info",
                                tint = AmberPrimary
                            )
                        }
                    }

                    // Open / Load URL Dialog Button (clean, uncluttered navigation)
                    IconButton(onClick = { openUrlDialogVisible = true }) {
                        Icon(
                            imageVector = Icons.Default.AddLink,
                            contentDescription = "Load Chapter Link",
                            tint = AmberPrimary
                        )
                    }

                    IconButton(onClick = { voiceSettingsVisible = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Voice Engine Settings",
                            tint = AmberPrimary
                        )
                    }
                }
            )
        },
        bottomBar = {
            PlaybackControlBar(
                isPlaying = uiState.isPlaying,
                activeSentence = uiState.activeSentenceIndex,
                totalSentences = uiState.sentences.size,
                playbackSpeed = uiState.playbackSpeed,
                sleepMode = uiState.sleepMode,
                hasNextChapter = !uiState.nextChapterUrl.isNullOrBlank(),
                hasPrevChapter = !uiState.prevChapterUrl.isNullOrBlank(),
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { viewModel.seekToSentence(it) },
                onOpenSpeed = { speedSheetVisible = true },
                onOpenSleep = { sleepSheetVisible = true },
                onNextChapter = { viewModel.nextChapter() },
                onPrevChapter = { viewModel.prevChapter() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(Unit) {
                    // Tap anywhere on middle/background to clear focus and dismiss keyboard
                    detectTapGestures(
                        onTap = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    )
                }
        ) {
            // URL Input Bar only displays when NO chapter is loaded (keeps reading view 100% uncluttered)
            if (uiState.paragraphs.isEmpty()) {
                Surface(
                    color = ObsidianSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            placeholder = { Text("Paste chapter or novel URL...", color = TextMuted, fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                if (inputUrl.isNotBlank()) {
                                    viewModel.loadUrl(inputUrl.trim())
                                }
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                if (inputUrl.isNotBlank()) {
                                    viewModel.loadUrl(inputUrl.trim())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Load Chapter",
                                tint = AmberPrimary
                            )
                        }
                    }
                }
            }

            // Auto-Dismissing Info Notification Banner (Dismisses after ~3.5s automatically)
            uiState.infoMessage?.let { info ->
                Surface(
                    color = AmberGlow,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = info,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearInfoMessage() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = AmberPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // User-Friendly Error Alert Card
            uiState.errorMessage?.let { errorMsg ->
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, StatusError.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(top = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Chapter Notice",
                                        color = StatusError,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = errorMsg,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.clearErrorMessage() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (uiState.captchaChallengeUrl != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { /* Solver sheet handles challenge */ },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = ObsidianBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Solve Challenge in App", color = ObsidianBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else if (errorMsg.contains("paste", ignoreCase = true)) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    viewModel.clearErrorMessage()
                                    rawTextDialogVisible = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EditNote,
                                    contentDescription = null,
                                    tint = ObsidianBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste Story Text", color = ObsidianBackground, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Maximized Reading Viewport (>90% screen real estate)
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AmberPrimary)
                }
            } else if (uiState.paragraphs.isEmpty()) {
                EmptyStateView(
                    onPasteTextClick = { rawTextDialogVisible = true },
                    onOpenUrlClick = { openUrlDialogVisible = true }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    // Spoken Chapter Title Header (Item 0)
                    val hasTitle = uiState.chapterTitle.isNotBlank() && uiState.chapterTitle != "No Chapter Loaded"
                    if (hasTitle) {
                        item {
                            val isTitleActive = uiState.activeSentenceIndex == 0
                            Surface(
                                color = if (isTitleActive) AmberGlow else ObsidianSurfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isTitleActive) AmberPrimary else Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clickable {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                        viewModel.seekToSentence(0)
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    val showNovelHeader = uiState.novelTitle.isNotBlank() &&
                                            !uiState.novelTitle.equals(uiState.chapterTitle, ignoreCase = true) &&
                                            !uiState.chapterTitle.contains(uiState.novelTitle, ignoreCase = true) &&
                                            uiState.novelTitle != "Universal Reader" &&
                                            uiState.novelTitle != "Web Novel"

                                    if (showNovelHeader) {
                                        Text(
                                            text = uiState.novelTitle.uppercase(),
                                            color = AmberPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                    }
                                    Text(
                                        text = uiState.chapterTitle,
                                        color = if (isTitleActive) AmberPrimary else TextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(uiState.paragraphs) { pIdx, paragraphText ->
                        val targetListIndex = if (hasTitle) pIdx + 1 else pIdx
                        val isParagraphActive = targetListIndex == activeParagraphIdx
                        ParagraphCard(
                            text = paragraphText,
                            isActive = isParagraphActive,
                            onClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                val targetSentence = uiState.sentenceToParagraphMap.indexOf(targetListIndex)
                                if (targetSentence != -1) {
                                    viewModel.seekToSentence(targetSentence)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    // Modal Sleep Mode & Chapter Timer Bottom Sheet
    if (sleepSheetVisible) {
        SleepModeSheet(
            currentSleepMode = uiState.sleepMode,
            onSetSleepMode = { viewModel.setSleepMode(it) },
            onDismiss = { sleepSheetVisible = false }
        )
    }

    // Voice & Reading Settings Modal Sheet (With Engine & Speech Clarity Controls)
    if (voiceSettingsVisible) {
        val context = androidx.compose.ui.platform.LocalContext.current
        VoiceSettingsSheet(
            currentSpeed = uiState.playbackSpeed,
            currentPitch = uiState.pitch,
            selectedVoice = uiState.selectedVoiceName,
            availableVoices = uiState.availableVoices,
            currentEngineLabel = uiState.currentEngineLabel,
            availableEngines = uiState.availableEngines,
            speechClarityMode = uiState.speechClarityMode,
            onSpeedChange = { viewModel.setSpeed(it) },
            onPitchChange = { viewModel.setPitch(it) },
            onVoiceSelected = { viewModel.setSystemVoice(it) },
            onSwitchEngine = { viewModel.switchTtsEngine(it) },
            onToggleSpeechClarity = { viewModel.setSpeechClarityMode(it) },
            onOpenSystemTtsSettings = { viewModel.openSystemTtsSettings(context) },
            onDismiss = { voiceSettingsVisible = false }
        )
    }

    // Bot Protection / Captcha Solver Sheet
    uiState.captchaChallengeUrl?.let { challengeUrl ->
        CaptchaSolverSheet(
            url = challengeUrl,
            onDismiss = { viewModel.dismissCaptchaChallenge() },
            onChallengeSolved = { viewModel.onCaptchaSolved() }
        )
    }

    // Novel Bookshelf Navigation Sheet
    if (novelBookshelfVisible) {
        NovelBookshelfSheet(
            allNovels = uiState.allNovels,
            currentNovelId = uiState.currentNovelId,
            currentUrl = uiState.currentUrl,
            onResumeNovel = { novelId ->
                viewModel.resumeNovel(novelId)
                novelBookshelfVisible = false
            },
            onSelectChapter = { url ->
                viewModel.selectChapter(url)
                novelBookshelfVisible = false
            },
            onCopyUrl = { url ->
                clipboardManager.setText(AnnotatedString(url))
                viewModel.showTemporaryInfo("Chapter URL copied to clipboard.")
            },
            onRequestDeleteNovel = { novel ->
                pendingDelete = DeleteTarget.EntireNovel(novel)
            },
            onRequestDeleteChapter = { chapter ->
                pendingDelete = DeleteTarget.SingleChapter(chapter)
            },
            onRequestClearAll = {
                pendingDelete = DeleteTarget.ClearAll
            },
            getChaptersFlow = { novelId ->
                viewModel.getChaptersForNovel(novelId)
            },
            onDismiss = { novelBookshelfVisible = false }
        )
    }

    // Clustered Speed Selector Modal Sheet
    if (speedSheetVisible) {
        SpeedSelectionSheet(
            currentSpeed = uiState.playbackSpeed,
            onSpeedSelected = { viewModel.setSpeed(it) },
            onDismiss = { speedSheetVisible = false }
        )
    }

    // Dedicated Open / Load URL Dialog
    if (openUrlDialogVisible) {
        OpenUrlDialog(
            initialUrl = "",
            onLoadUrl = { url ->
                viewModel.loadUrl(url)
            },
            onDismiss = { openUrlDialogVisible = false }
        )
    }

    // Dedicated Chapter Information & URL Copy Dialog
    if (chapterInfoDialogVisible) {
        ChapterInfoDialog(
            chapterTitle = uiState.chapterTitle,
            novelTitle = uiState.novelTitle,
            chapterUrl = uiState.currentUrl,
            onCopyUrl = {
                clipboardManager.setText(AnnotatedString(uiState.currentUrl))
                viewModel.showTemporaryInfo("Chapter URL copied to clipboard.")
            },
            onDismiss = { chapterInfoDialogVisible = false }
        )
    }

    // Direct Story Text Paste Dialog
    if (rawTextDialogVisible) {
        AlertDialog(
            onDismissRequest = { rawTextDialogVisible = false },
            containerColor = ObsidianSurface,
            title = { Text("Paste Story Content", color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = rawInputTitle,
                        onValueChange = { rawInputTitle = it },
                        label = { Text("Chapter Title (Optional)", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AmberPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rawInputText,
                        onValueChange = { rawInputText = it },
                        label = { Text("Paste story text here...", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AmberPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rawInputText.isNotBlank()) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            viewModel.loadRawText(rawInputTitle, rawInputText)
                            rawTextDialogVisible = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Text("Load Text", color = ObsidianBackground)
                }
            },
            dismissButton = {
                TextButton(onClick = { rawTextDialogVisible = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Confirmation Popups for Deletion (Single Chapter, Entire Novel, Multi-select, or Clear All)
    pendingDelete?.let { target ->
        DeleteConfirmationDialog(
            target = target,
            onConfirm = {
                when (target) {
                    is DeleteTarget.SingleChapter -> viewModel.deleteChapter(target.chapter.url)
                    is DeleteTarget.EntireNovel -> viewModel.deleteNovel(target.novel.id)
                    is DeleteTarget.SelectedChapters -> viewModel.deleteChapters(target.urls)
                    is DeleteTarget.ClearAll -> viewModel.clearAllCachedChapters()
                }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    target: DeleteTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, message) = when (target) {
        is DeleteTarget.SingleChapter ->
            "Delete Chapter" to "Are you sure you want to remove \"${target.chapter.chapterTitle.ifBlank { "this chapter" }}\" from offline saved storage?"
        is DeleteTarget.EntireNovel ->
            "Delete Novel" to "Are you sure you want to delete \"${target.novel.title}\" and all of its ${target.novel.totalChapters} saved chapters?"
        is DeleteTarget.SelectedChapters ->
            "Delete Selected Chapters" to "Are you sure you want to delete ${target.urls.size} selected chapter(s) from storage?"
        is DeleteTarget.ClearAll ->
            "Clear All Saved Novels" to "Are you sure you want to delete all saved novels and chapters from offline storage? This action cannot be undone."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = StatusError,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Text(message, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun OpenUrlDialog(
    initialUrl: String,
    onLoadUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialUrl) }
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AddLink,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load Chapter URL", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Paste chapter link...", color = TextMuted, fontSize = 12.sp) },
                    trailingIcon = {
                        if (text.isNotBlank()) {
                            IconButton(onClick = { text = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AmberPrimary,
                        unfocusedBorderColor = ObsidianSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text
                            if (!clipText.isNullOrBlank()) {
                                text = clipText.trim()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste from Clipboard", color = AmberPrimary, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                        onLoadUrl(text.trim())
                        onDismiss()
                    }
                },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Load", color = ObsidianBackground, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ChapterInfoDialog(
    chapterTitle: String,
    novelTitle: String,
    chapterUrl: String,
    onCopyUrl: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chapter Information", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (novelTitle.isNotBlank()) {
                    Text("NOVEL", color = AmberPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(novelTitle, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text("CHAPTER", color = AmberPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(chapterTitle, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(10.dp))

                if (chapterUrl.isNotBlank()) {
                    Text("SOURCE URL", color = AmberPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Surface(
                        color = ObsidianSurfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Text(
                            text = chapterUrl,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp),
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (chapterUrl.isNotBlank()) {
                Button(
                    onClick = {
                        onCopyUrl()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = ObsidianBackground, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy URL", color = ObsidianBackground, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelBookshelfSheet(
    allNovels: List<NovelEntity>,
    currentNovelId: String,
    currentUrl: String,
    onResumeNovel: (String) -> Unit,
    onSelectChapter: (String) -> Unit,
    onCopyUrl: (String) -> Unit,
    onRequestDeleteNovel: (NovelEntity) -> Unit,
    onRequestDeleteChapter: (ChapterEntity) -> Unit,
    onRequestClearAll: () -> Unit,
    getChaptersFlow: (String) -> Flow<List<ChapterEntity>>,
    onDismiss: () -> Unit
) {
    var selectedNovel by remember { mutableStateOf<NovelEntity?>(null) }
    var novelSearchQuery by remember { mutableStateOf("") }
    var chapterSearchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AmberPrimary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val activeNovel = selectedNovel
            if (activeNovel == null) {
                // LEVEL 1: Novel Bookshelf Overview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Novel Bookshelf",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = AmberGlow,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${allNovels.size} novels",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (allNovels.isNotEmpty()) {
                        TextButton(
                            onClick = onRequestClearAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear All", color = StatusError, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = novelSearchQuery,
                    onValueChange = { novelSearchQuery = it },
                    placeholder = { Text("Search novels by title...", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (novelSearchQuery.isNotBlank()) {
                            IconButton(onClick = { novelSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AmberPrimary,
                        unfocusedBorderColor = ObsidianSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val filteredNovels = remember(novelSearchQuery, allNovels) {
                    if (novelSearchQuery.isBlank()) allNovels
                    else allNovels.filter { it.title.contains(novelSearchQuery, ignoreCase = true) }
                }

                if (allNovels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = AmberPrimary.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Your bookshelf is empty.",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Load any chapter URL or paste story text to start your library.",
                                color = TextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (filteredNovels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No novels match \"$novelSearchQuery\"", color = TextMuted, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredNovels, key = { it.id }) { novel ->
                            val isCurrent = novel.id == currentNovelId
                            Surface(
                                color = if (isCurrent) AmberGlow.copy(alpha = 0.2f) else ObsidianSurfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = if (isCurrent) 1.5.dp else 0.5.dp,
                                    color = if (isCurrent) AmberPrimary else ObsidianSurfaceVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    // Title and Delete Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                                contentDescription = null,
                                                tint = AmberPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = novel.title,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(
                                            onClick = { onRequestDeleteNovel(novel) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Novel",
                                                tint = StatusError.copy(alpha = 0.8f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Progress & Chapter Count Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            color = ObsidianSurface,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "${novel.totalChapters} saved chapters",
                                                color = AmberPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }

                                        if (!novel.lastReadChapterTitle.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Last: ${novel.lastReadChapterTitle}",
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Action Buttons Row (Resume & Browse Chapters)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (!novel.lastReadUrl.isNullOrBlank()) {
                                            Button(
                                                onClick = { onResumeNovel(novel.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = ObsidianBackground,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isCurrent) "Continue" else "Resume",
                                                    color = ObsidianBackground,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                selectedNovel = novel
                                                chapterSearchQuery = ""
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberPrimary),
                                            border = BorderStroke(1.dp, AmberPrimary.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.List,
                                                contentDescription = null,
                                                tint = AmberPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Chapters",
                                                color = AmberPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // LEVEL 2: Chapter Directory for Selected Novel
                val chaptersFlow = remember(activeNovel.id) { getChaptersFlow(activeNovel.id) }
                val chapters by chaptersFlow.collectAsState(initial = emptyList())

                val filteredChapters = remember(chapterSearchQuery, chapters) {
                    if (chapterSearchQuery.isBlank()) chapters
                    else chapters.filter { it.chapterTitle.contains(chapterSearchQuery, ignoreCase = true) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { selectedNovel = null; chapterSearchQuery = "" },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Novels",
                            tint = AmberPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeNovel.title,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${chapters.size} saved chapters",
                            color = AmberPrimary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = { onRequestDeleteNovel(activeNovel) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Novel",
                            tint = StatusError.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = chapterSearchQuery,
                    onValueChange = { chapterSearchQuery = it },
                    placeholder = { Text("Filter chapters in ${activeNovel.title}...", color = TextMuted, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (chapterSearchQuery.isNotBlank()) {
                            IconButton(onClick = { chapterSearchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AmberPrimary,
                        unfocusedBorderColor = ObsidianSurfaceVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredChapters.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (chapterSearchQuery.isNotBlank()) "No matching chapters found." else "No chapters stored for this novel.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredChapters, key = { it.url }) { chapter ->
                            val isCurrent = chapter.url == currentUrl
                            Surface(
                                color = if (isCurrent) AmberGlow.copy(alpha = 0.25f) else ObsidianSurfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    width = if (isCurrent) 1.5.dp else 0.dp,
                                    color = if (isCurrent) AmberPrimary else Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectChapter(chapter.url) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chapter.chapterTitle.ifBlank { "Untitled Chapter" },
                                            color = if (isCurrent) AmberPrimary else TextPrimary,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val sentenceProgress = if (chapter.lastSentenceIndex > 0) "Sentence ${chapter.lastSentenceIndex + 1} • " else ""
                                        Text(
                                            text = "$sentenceProgress${chapter.paragraphs.size} paragraphs",
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }

                                    // Copy URL Button
                                    IconButton(
                                        onClick = { onCopyUrl(chapter.url) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy URL",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    // Delete Single Chapter Button
                                    IconButton(
                                        onClick = { onRequestDeleteChapter(chapter) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete Chapter",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    if (isCurrent) {
                                        Surface(
                                            color = AmberPrimary,
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.padding(start = 4.dp)
                                        ) {
                                            Text(
                                                text = "Playing",
                                                color = ObsidianBackground,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play Chapter",
                                            tint = TextSecondary,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceVariant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Text("Close", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsSheet(
    currentSpeed: Float,
    currentPitch: Float,
    selectedVoice: String?,
    availableVoices: List<String>,
    currentEngineLabel: String,
    availableEngines: List<Pair<String, String>>,
    speechClarityMode: Boolean,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onVoiceSelected: (String) -> Unit,
    onSwitchEngine: (String) -> Unit,
    onToggleSpeechClarity: (Boolean) -> Unit,
    onOpenSystemTtsSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AmberPrimary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Voice & Speech Engine Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Speech Engine Card
            Surface(
                color = ObsidianSurfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACTIVE TTS ENGINE",
                                color = AmberPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentEngineLabel,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(
                            onClick = onOpenSystemTtsSettings,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("System Settings", color = AmberPrimary, fontSize = 11.sp)
                        }
                    }

                    // If multiple engines are installed, provide 1-tap engine switcher
                    if (availableEngines.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableEngines.forEach { (pkg, label) ->
                                val isActive = currentEngineLabel.contains(label, ignoreCase = true) ||
                                        currentEngineLabel.contains(pkg, ignoreCase = true)
                                Surface(
                                    color = if (isActive) AmberPrimary else ObsidianSurface,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (isActive) AmberPrimary else ObsidianSurfaceVariant),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onSwitchEngine(pkg) }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (pkg.contains("google")) "Google TTS" else label.take(12),
                                            color = if (isActive) ObsidianBackground else TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Speech Clarity Mode Switch (Crucial for OriginOS / Vivo / iQOO / Dolby Atmos DSP bypass)
            Surface(
                color = if (speechClarityMode) AmberGlow.copy(alpha = 0.25f) else ObsidianSurfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (speechClarityMode) AmberPrimary else Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Speech Clarity Mode",
                                color = if (speechClarityMode) AmberPrimary else TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (speechClarityMode) AmberPrimary else ObsidianSurfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (speechClarityMode) "HIGH CLARITY" else "MEDIA",
                                    color = if (speechClarityMode) ObsidianBackground else TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Bypasses device music equalizers & bass boost for crisp, intelligible speech.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = speechClarityMode,
                        onCheckedChange = onToggleSpeechClarity,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBackground,
                            checkedTrackColor = AmberPrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = ObsidianSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Speech Rate Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Reading Speed",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Text(
                    text = String.format(java.util.Locale.US, "%.2fx", currentSpeed),
                    color = AmberPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Slider(
                value = currentSpeed,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..2.5f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = AmberPrimary,
                    activeTrackColor = AmberPrimary,
                    inactiveTrackColor = ObsidianSurfaceVariant
                )
            )

            // Speed Presets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    val isSelected = Math.abs(currentSpeed - speed) < 0.05f
                    Surface(
                        color = if (isSelected) AmberPrimary else ObsidianSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSpeedChange(speed) }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${speed}x",
                                color = if (isSelected) ObsidianBackground else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pitch Control
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Voice Pitch",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                TextButton(
                    onClick = { onPitchChange(1.0f) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Reset (1.0x)", color = AmberPrimary, fontSize = 11.sp)
                }
            }

            Slider(
                value = currentPitch,
                onValueChange = onPitchChange,
                valueRange = 0.5f..1.5f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = AmberPrimary,
                    activeTrackColor = AmberPrimary,
                    inactiveTrackColor = ObsidianSurfaceVariant
                )
            )

            // Installed Voice Selector (if available)
            if (availableVoices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Installed Offline Voices",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${availableVoices.size} available",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(availableVoices.size) { vIdx ->
                            val voiceName = availableVoices[vIdx]
                            val isSelected = voiceName == selectedVoice
                            val isHighQuality = voiceName.contains("local", ignoreCase = true) ||
                                    voiceName.contains("neural", ignoreCase = true)
                            val displayName = voiceName
                                .substringAfterLast("/")
                                .replace("#", " ")
                                .replace("_", " ")

                            Surface(
                                color = if (isSelected) AmberGlow else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVoiceSelected(voiceName) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = displayName,
                                            color = if (isSelected) AmberPrimary else TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isHighQuality) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = AmberGlow,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "HQ",
                                                    color = AmberPrimary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = AmberPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Text("Done", color = ObsidianBackground, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ParagraphCard(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    if (TextSanitizer.isVisualDivider(text)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AmberPrimary.copy(alpha = 0.35f))
                )
                Text(
                    text = "  ✦  ",
                    color = AmberPrimary.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(AmberPrimary.copy(alpha = 0.35f))
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isActive) AmberGlow else Color.Transparent)
                .border(
                    width = if (isActive) 1.5.dp else 0.dp,
                    color = if (isActive) AmberPrimary else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 25.sp,
                    color = if (isActive) TextPrimary else TextSecondary,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
            )
        }
    }
}

@Composable
fun PlaybackControlBar(
    isPlaying: Boolean,
    activeSentence: Int,
    totalSentences: Int,
    playbackSpeed: Float,
    sleepMode: SleepMode,
    hasNextChapter: Boolean,
    hasPrevChapter: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenSleep: () -> Unit,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Sentence Progress Scrubber
            if (totalSentences > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeSentence + 1}/$totalSentences",
                        color = TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.width(55.dp)
                    )
                    Slider(
                        value = activeSentence.toFloat(),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..(totalSentences - 1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = AmberPrimary,
                            activeTrackColor = AmberPrimary,
                            inactiveTrackColor = ObsidianSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Controls Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clustered Badges: Speed & Sleep Timer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Speed Button Badge
                    Surface(
                        color = ObsidianSurfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { onOpenSpeed() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Playback Speed",
                                tint = AmberPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${playbackSpeed}x",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Sleep / Loop Mode Badge
                    val isSleepActive = sleepMode !is SleepMode.Off
                    Surface(
                        color = if (isSleepActive) AmberPrimary else ObsidianSurfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { onOpenSleep() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (sleepMode is SleepMode.LoopChapter) Icons.Default.Repeat else Icons.Default.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (isSleepActive) ObsidianBackground else AmberPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            if (isSleepActive) {
                                Spacer(modifier = Modifier.width(3.dp))
                                val badgeText = when (sleepMode) {
                                    is SleepMode.LoopChapter -> "Loop 1x"
                                    is SleepMode.PlayNChapters -> "${sleepMode.remainingChapters}ch"
                                    is SleepMode.Off -> ""
                                }
                                Text(
                                    text = badgeText,
                                    color = ObsidianBackground,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Prev Chapter
                IconButton(onClick = onPrevChapter, enabled = hasPrevChapter) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Chapter",
                        tint = if (hasPrevChapter) AmberPrimary else TextMuted
                    )
                }

                // Rewind 5 Sentences
                IconButton(onClick = { onSeek((activeSentence - 5).coerceAtLeast(0)) }) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Rewind",
                        tint = TextPrimary
                    )
                }

                // Play / Pause Floating Orb
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = ObsidianBackground,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Forward 5 Sentences
                IconButton(onClick = { onSeek((activeSentence + 5).coerceAtMost(totalSentences - 1)) }) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Fast Forward",
                        tint = TextPrimary
                    )
                }

                // Next Chapter
                IconButton(onClick = onNextChapter, enabled = hasNextChapter) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Chapter",
                        tint = if (hasNextChapter) AmberPrimary else TextMuted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSelectionSheet(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = AmberPrimary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Playback Speed: ${currentSpeed}x",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Preset Chips Grid
            val presets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                presets.take(4).forEach { speed ->
                    SpeedChip(
                        speed = speed,
                        isSelected = Math.abs(currentSpeed - speed) < 0.01f,
                        onClick = { onSpeedSelected(speed) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                presets.drop(4).forEach { speed ->
                    SpeedChip(
                        speed = speed,
                        isSelected = Math.abs(currentSpeed - speed) < 0.01f,
                        onClick = { onSpeedSelected(speed) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
            ) {
                Text("Done", color = ObsidianBackground, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SpeedChip(
    speed: Float,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) AmberPrimary else ObsidianSurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .clickable { onClick() }
            .padding(2.dp)
    ) {
        Text(
            text = "${speed}x",
            color = if (isSelected) ObsidianBackground else TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun EmptyStateView(
    onPasteTextClick: () -> Unit,
    onOpenUrlClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = AmberPrimary.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Chapter Loaded",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Paste any web novel chapter link or paste raw text to start listening.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenUrlClick,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary)
                ) {
                    Icon(Icons.Default.AddLink, contentDescription = null, tint = ObsidianBackground, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load URL", color = ObsidianBackground, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onPasteTextClick,
                    border = BorderStroke(1.dp, AmberPrimary)
                ) {
                    Icon(Icons.Default.EditNote, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste Text", color = AmberPrimary)
                }
            }
        }
    }
}
