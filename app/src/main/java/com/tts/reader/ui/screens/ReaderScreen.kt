package com.tts.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import com.tts.reader.tts.ModelStatus
import com.tts.reader.tts.VoiceEngineMode
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
    var rawInputText by remember { mutableStateOf("") }
    var rawInputTitle by remember { mutableStateOf("") }
    var speedSheetVisible by remember { mutableStateOf(false) }

    // Auto-scroll reading view to keep active sentence visible
    val activeParagraphIdx = uiState.sentenceToParagraphMap.getOrNull(uiState.activeSentenceIndex) ?: 0
    LaunchedEffect(activeParagraphIdx) {
        if (uiState.paragraphs.isNotEmpty() && uiState.isPlaying) {
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
                title = {
                    Column {
                        Text(
                            text = uiState.chapterTitle,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.novelTitle,
                            style = MaterialTheme.typography.bodySmall.copy(color = AmberPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
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
            // URL Input Bar
            Surface(
                color = ObsidianSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp)
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

            // Permanent Voice Engine Selection & Download Card
            VoiceEngineCard(
                selectedMode = uiState.voiceMode,
                modelStatus = uiState.modelStatus,
                isKokoroInstalled = uiState.isKokoroInstalled,
                onSelectMode = { viewModel.setVoiceMode(it) },
                onDownloadClick = { viewModel.downloadKokoroModel() },
                onCancelDownload = { viewModel.cancelKokoroDownload() }
            )

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
                        if (!uiState.isKokoroInstalled && uiState.modelStatus !is ModelStatus.Downloading) {
                            Button(
                                onClick = { viewModel.downloadKokoroModel() },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text("Download", color = ObsidianBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = { viewModel.clearInfoMessage() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = AmberPrimary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Error Display
            uiState.errorMessage?.let { errorMsg ->
                Surface(
                    color = StatusError.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = errorMsg,
                        color = StatusError,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Reading Viewport
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
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    itemsIndexed(uiState.paragraphs) { pIdx, paragraphText ->
                        val isParagraphActive = pIdx == activeParagraphIdx
                        ParagraphCard(
                            text = paragraphText,
                            isActive = isParagraphActive,
                            onClick = {
                                val targetSentence = uiState.sentenceToParagraphMap.indexOf(pIdx)
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

@Composable
fun VoiceEngineCard(
    selectedMode: VoiceEngineMode,
    modelStatus: ModelStatus,
    isKokoroInstalled: Boolean,
    onSelectMode: (VoiceEngineMode) -> Unit,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Segmented Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ObsidianSurfaceVariant)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // System Voice Button
                Surface(
                    color = if (selectedMode == VoiceEngineMode.SYSTEM_OFFLINE) AmberPrimary else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectMode(VoiceEngineMode.SYSTEM_OFFLINE) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (selectedMode == VoiceEngineMode.SYSTEM_OFFLINE) ObsidianBackground else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "System Voice",
                            color = if (selectedMode == VoiceEngineMode.SYSTEM_OFFLINE) ObsidianBackground else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Neural Voice Button
                Surface(
                    color = if (selectedMode == VoiceEngineMode.KOKORO_NEURAL) AmberPrimary else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectMode(VoiceEngineMode.KOKORO_NEURAL) }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = if (selectedMode == VoiceEngineMode.KOKORO_NEURAL) ObsidianBackground else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Neural Voice",
                            color = if (selectedMode == VoiceEngineMode.KOKORO_NEURAL) ObsidianBackground else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Current Mode Active Status Line
            if (selectedMode == VoiceEngineMode.SYSTEM_OFFLINE) {
                Text(
                    text = "Active: Device built-in offline voice (Works 100% offline with zero setup)",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            } else {
                Text(
                    text = if (isKokoroInstalled) {
                        "Active: Kokoro Neural Voice (82M INT8). Studio-quality AI speech."
                    } else {
                        "Selected: Kokoro Neural Voice. Download package below to enable AI voice."
                    },
                    color = if (isKokoroInstalled) AmberPrimary else TextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Kokoro Neural Model Status & Action Card (ALWAYS VISIBLE)
            Surface(
                color = ObsidianSurfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (isKokoroInstalled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Kokoro Neural Model Installed",
                                        color = StatusSuccess,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "103 MB package ready for offline AI playback",
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    } else if (modelStatus is ModelStatus.Downloading) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Downloading Neural Model (${modelStatus.progressPercent}%)",
                                    color = AmberPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = onCancelDownload,
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Cancel", color = StatusError, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { modelStatus.progressPercent / 100f },
                                color = AmberPrimary,
                                trackColor = ObsidianBackground,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = modelStatus.currentItem,
                                color = TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    } else if (modelStatus is ModelStatus.Error) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = modelStatus.message,
                                    color = StatusError,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onDownloadClick,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = ObsidianBackground,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Download (Resumes automatically)", color = ObsidianBackground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Not Downloaded: Always visible full-width action button
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Kokoro Neural AI Voice (103 MB)",
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Studio-quality voice (download once, runs 100% offline)",
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onDownloadClick,
                                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = ObsidianBackground,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Download Kokoro Neural Voice (103 MB)",
                                    color = ObsidianBackground,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ParagraphCard(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
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
    var sliderValue by remember { mutableStateOf(currentSpeed) }

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
                text = "Playback Speed: ${String.format("%.2f", sliderValue)}x",
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
                            sliderValue = speed
                            onSpeedSelected(speed)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                presets.drop(4).forEach { speed ->
                    SpeedChip(
                        speed = speed,
                        isSelected = Math.abs(currentSpeed - speed) < 0.01f,
                        onClick = {
                            sliderValue = speed
                            onSpeedSelected(speed)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Fine-Tuning Slider
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onSpeedSelected((Math.round(it * 20) / 20f))
                },
                valueRange = 0.5f..3.0f,
                colors = SliderDefaults.colors(
                    thumbColor = AmberPrimary,
                    activeTrackColor = AmberPrimary,
                    inactiveTrackColor = ObsidianSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                modifier = Modifier.fillMaxWidth()
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
