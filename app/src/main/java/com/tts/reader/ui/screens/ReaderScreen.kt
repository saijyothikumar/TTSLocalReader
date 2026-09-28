package com.tts.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tts.reader.data.scraper.TextSanitizer
import com.tts.reader.ui.theme.*
import com.tts.reader.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var inputUrl by remember { mutableStateOf("") }
    var rawTextDialogVisible by remember { mutableStateOf(false) }
    var cacheDialogVisible by remember { mutableStateOf(false) }
    var voiceSettingsVisible by remember { mutableStateOf(false) }
    var chapterLibraryVisible by remember { mutableStateOf(false) }
    var rawInputText by remember { mutableStateOf("") }
    var rawInputTitle by remember { mutableStateOf("") }
    var speedSheetVisible by remember { mutableStateOf(false) }

    // Auto-scroll reading view to keep active sentence visible (even when paused or scrubbing)
    val activeParagraphIdx = uiState.sentenceToParagraphMap.getOrNull(uiState.activeSentenceIndex) ?: 0
    LaunchedEffect(activeParagraphIdx) {
        if (uiState.paragraphs.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(activeParagraphIdx)
            }
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
                    IconButton(onClick = { chapterLibraryVisible = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Chapter Library",
                            tint = AmberPrimary
                        )
                    }
                },
                title = {
                    val showNovelTitle = uiState.novelTitle.isNotBlank() &&
                            !uiState.novelTitle.equals(uiState.chapterTitle, ignoreCase = true) &&
                            !uiState.chapterTitle.contains(uiState.novelTitle, ignoreCase = true) &&
                            uiState.novelTitle != "Universal Reader" &&
                            uiState.novelTitle != "Web Novel"

                    Column {
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
                    IconButton(onClick = { voiceSettingsVisible = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Voice Engine Settings",
                            tint = AmberPrimary
                        )
                    }
                    IconButton(onClick = { rawTextDialogVisible = true }) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Paste Custom Text",
                            tint = AmberPrimary
                        )
                    }
                    IconButton(onClick = { cacheDialogVisible = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Manage Chapter Cache",
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
                hasNextChapter = !uiState.nextChapterUrl.isNullOrBlank(),
                hasPrevChapter = !uiState.prevChapterUrl.isNullOrBlank(),
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { viewModel.seekToSentence(it) },
                onOpenSpeed = { speedSheetVisible = true },
                onNextChapter = { viewModel.nextChapter() },
                onPrevChapter = { viewModel.prevChapter() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Compact URL Input Bar
            Surface(
                color = ObsidianSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
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

            // Info Notification Banner
            uiState.infoMessage?.let { info ->
                Surface(
                    color = AmberGlow,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
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
                                onClick = { /* Sheet opens automatically or triggers via state */ },
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
                EmptyStateView(onPasteTextClick = { rawTextDialogVisible = true })
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
                                    .clickable { viewModel.seekToSentence(0) }
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

    // Voice & Reading Settings Modal Sheet
    if (voiceSettingsVisible) {
        VoiceSettingsSheet(
            currentSpeed = uiState.playbackSpeed,
            currentPitch = uiState.pitch,
            selectedVoice = uiState.selectedVoiceName,
            availableVoices = uiState.availableVoices,
            onSpeedChange = { viewModel.setSpeed(it) },
            onPitchChange = { viewModel.setPitch(it) },
            onVoiceSelected = { viewModel.setSystemVoice(it) },
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

    // Chapter Library Navigation Drawer / Sheet
    if (chapterLibraryVisible) {
        ChapterLibrarySheet(
            cachedChapters = uiState.cachedChapters,
            currentUrl = uiState.currentUrl,
            onSelectChapter = { viewModel.selectChapter(it) },
            onOpenManageCache = { cacheDialogVisible = true },
            onDismiss = { chapterLibraryVisible = false }
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

    // Chapter Storage & Cache Management Dialog with Checkboxes
    var selectedUrls by remember { mutableStateOf(setOf<String>()) }

    if (cacheDialogVisible) {
        AlertDialog(
            onDismissRequest = { 
                cacheDialogVisible = false
                selectedUrls = emptySet()
            },
            containerColor = ObsidianSurface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = AmberPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Chapter Storage & Cache", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Text(
                        text = "${uiState.cachedChapters.size} saved",
                        color = AmberPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Chapters are stored on device disk (~20 KB each) in Room SQLite. Only the active chapter is loaded in RAM.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (uiState.cachedChapters.isNotEmpty()) {
                        // Action & Selection Toolbar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ObsidianSurfaceVariant, RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val allSelected = selectedUrls.size == uiState.cachedChapters.size && uiState.cachedChapters.isNotEmpty()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    selectedUrls = if (allSelected) emptySet() else uiState.cachedChapters.map { it.url }.toSet()
                                }
                            ) {
                                Checkbox(
                                    checked = allSelected,
                                    onCheckedChange = { checked ->
                                        selectedUrls = if (checked) uiState.cachedChapters.map { it.url }.toSet() else emptySet()
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = AmberPrimary,
                                        uncheckedColor = TextSecondary,
                                        checkmarkColor = ObsidianBackground
                                    )
                                )
                                Text("All", color = TextPrimary, fontSize = 12.sp)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selectedUrls.isNotEmpty()) {
                                    Button(
                                        onClick = {
                                            viewModel.deleteChapters(selectedUrls.toList())
                                            selectedUrls = emptySet()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = StatusError),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Delete (${selectedUrls.size})", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                TextButton(
                                    onClick = {
                                        viewModel.clearAllCachedChapters()
                                        selectedUrls = emptySet()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Clear All", color = StatusError, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Scrollable List of Cached Chapters with Checkboxes
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            itemsIndexed(uiState.cachedChapters) { _, chapter ->
                                val isChecked = selectedUrls.contains(chapter.url)
                                val isCurrent = chapter.url == uiState.currentUrl
                                Surface(
                                    color = if (isCurrent) AmberGlow.copy(alpha = 0.15f) else ObsidianSurfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedUrls = if (checked) {
                                                    selectedUrls + chapter.url
                                                } else {
                                                    selectedUrls - chapter.url
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = AmberPrimary,
                                                uncheckedColor = TextSecondary,
                                                checkmarkColor = ObsidianBackground
                                            ),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    if (!isCurrent) {
                                                        viewModel.loadUrl(chapter.url)
                                                        cacheDialogVisible = false
                                                    }
                                                }
                                        ) {
                                            Text(
                                                text = chapter.chapterTitle.ifBlank { "Untitled Chapter" },
                                                color = if (isCurrent) AmberPrimary else TextPrimary,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${chapter.novelTitle} • ${chapter.paragraphs.size} pars • Sentence ${chapter.lastSentenceIndex + 1}",
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteChapter(chapter.url)
                                                selectedUrls = selectedUrls - chapter.url
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete Chapter",
                                                tint = TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No chapters currently stored in offline cache.", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    cacheDialogVisible = false
                    selectedUrls = emptySet()
                }) {
                    Text("Close", color = TextSecondary)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsSheet(
    currentSpeed: Float,
    currentPitch: Float,
    selectedVoice: String?,
    availableVoices: List<String>,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onVoiceSelected: (String) -> Unit,
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
                    text = "Voice & Reading Settings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Engine Info Banner
            Surface(
                color = ObsidianSurfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Android System TTS",
                            color = AmberPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "100% offline, zero download required, battery efficient.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(18.dp))

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
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Installed System Voice",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
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
                                    Text(
                                        text = displayName,
                                        color = if (isSelected) AmberPrimary else TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
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

            Spacer(modifier = Modifier.height(16.dp))

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterLibrarySheet(
    cachedChapters: List<com.tts.reader.data.local.ChapterEntity>,
    currentUrl: String,
    onSelectChapter: (String) -> Unit,
    onOpenManageCache: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredChapters = remember(searchQuery, cachedChapters) {
        if (searchQuery.isBlank()) {
            cachedChapters
        } else {
            cachedChapters.filter {
                it.chapterTitle.contains(searchQuery, ignoreCase = true) ||
                it.novelTitle.contains(searchQuery, ignoreCase = true)
            }
        }
    }

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Chapter Library (${cachedChapters.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }

                TextButton(
                    onClick = {
                        onDismiss()
                        onOpenManageCache()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Manage", color = AmberPrimary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search filter field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter chapters or novels...", color = TextMuted, fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
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
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matching chapters found." else "No chapters currently saved in cache.\nLoad any chapter to save it here for offline reading.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(filteredChapters) { _, chapter ->
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
                                .clickable {
                                    onSelectChapter(chapter.url)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (chapter.novelTitle.isNotBlank()) {
                                        Text(
                                            text = chapter.novelTitle,
                                            color = AmberPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                    }
                                    Text(
                                        text = chapter.chapterTitle.ifBlank { "Untitled Chapter" },
                                        color = if (isCurrent) AmberPrimary else TextPrimary,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${chapter.paragraphs.size} paragraphs • Last at Sentence ${chapter.lastSentenceIndex + 1}",
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }

                                if (isCurrent) {
                                    Surface(
                                        color = AmberPrimary,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text(
                                            text = "Playing",
                                            color = ObsidianBackground,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Chapter",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
    hasNextChapter: Boolean,
    hasPrevChapter: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onOpenSpeed: () -> Unit,
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
                // Clustered Speed Button Badge
                Surface(
                    color = ObsidianSurfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable { onOpenSpeed() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Playback Speed",
                            tint = AmberPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${playbackSpeed}x",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
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
                        onClick = {
                            onSpeedSelected(speed)
                        }
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
                        onClick = {
                            onSpeedSelected(speed)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
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
fun EmptyStateView(onPasteTextClick: () -> Unit) {
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
                text = "Paste any web novel chapter link in the bar above or paste text directly to start listening.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onPasteTextClick,
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary)
            ) {
                Text("Paste Story Text", color = AmberPrimary)
            }
        }
    }
}
