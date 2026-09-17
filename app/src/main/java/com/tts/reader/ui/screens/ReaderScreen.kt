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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tts.reader.tts.ModelStatus
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
    var rawInputText by remember { mutableStateOf("") }
    var rawInputTitle by remember { mutableStateOf("") }

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
                onSpeedChange = { viewModel.setSpeed(it) },
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text("Paste Ranobes / Web Novel URL...", color = TextMuted, fontSize = 13.sp) },
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
                            contentDescription = "Load Novel",
                            tint = AmberPrimary
                        )
                    }
                }
            }

            // Model Setup Banner (Shown if Kokoro weights need one-tap download)
            AnimatedVisibility(visible = uiState.modelStatus !is ModelStatus.Ready) {
                ModelSetupBanner(
                    status = uiState.modelStatus,
                    onDownloadClick = { viewModel.downloadKokoroModel() }
                )
            }

            // Error Display
            uiState.errorMessage?.let { errorMsg ->
                Surface(
                    color = StatusError.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = errorMsg,
                        color = StatusError,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Reading Content Viewport
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
                    contentPadding = PaddingValues(vertical = 16.dp)
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
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
            }
        }
    }

    // Direct Text Paste Dialog
    if (rawTextDialogVisible) {
        AlertDialog(
            onDismissRequest = { rawTextDialogVisible = false },
            containerColor = ObsidianSurface,
            title = { Text("Paste Chapter Content", color = TextPrimary) },
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
                    Spacer(modifier = Modifier.height(12.dp))
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
                            .height(200.dp)
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
                fontSize = 17.sp,
                lineHeight = 26.sp,
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
    onSpeedChange: (Float) -> Unit,
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Sentence Scrubber Slider
            if (totalSentences > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeSentence + 1}/$totalSentences",
                        color = TextMuted,
                        fontSize = 12.sp,
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

            // Media Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev Chapter
                IconButton(onClick = onPrevChapter, enabled = hasPrevChapter) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Chapter",
                        tint = if (hasPrevChapter) AmberPrimary else TextMuted
                    )
                }

                // Skip Backward 5 sentences
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
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AmberPrimary)
                        .clickable { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = ObsidianBackground,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Skip Forward 5 sentences
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

            Spacer(modifier = Modifier.height(8.dp))

            // Speed Selector Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    val isSelected = playbackSpeed == speed
                    Surface(
                        color = if (isSelected) AmberPrimary else ObsidianSurfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable { onSpeedChange(speed) }
                    ) {
                        Text(
                            text = "${speed}x",
                            color = if (isSelected) ObsidianBackground else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelSetupBanner(
    status: ModelStatus,
    onDownloadClick: () -> Unit
) {
    Surface(
        color = AmberGlow,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(1.dp, AmberPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Kokoro Neural Voice (~85MB)",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Studio-quality natural voice for offline reading",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                if (status is ModelStatus.NotDownloaded || status is ModelStatus.Error) {
                    Button(
                        onClick = onDownloadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Download", color = ObsidianBackground, fontSize = 12.sp)
                    }
                }
            }

            if (status is ModelStatus.Downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { status.progressPercent / 100f },
                    color = AmberPrimary,
                    trackColor = ObsidianSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${status.currentItem} (${status.progressPercent}%)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
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
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                tint = AmberPrimary.copy(alpha = 0.6f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No Novel Chapter Loaded",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Paste a Ranobes chapter link in the search bar above or paste text directly to start listening.",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onPasteTextClick,
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberPrimary)
            ) {
                Text("Paste Raw Text", color = AmberPrimary)
            }
        }
    }
}
