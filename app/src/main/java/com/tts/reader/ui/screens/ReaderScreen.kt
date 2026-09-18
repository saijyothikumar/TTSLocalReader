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
import androidx.compose.material.icons.automirrored.filled.MenuBook
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

            // Voice Engine Status & Download Banner
            VoiceModeStatusHeader(
                voiceMode = uiState.voiceMode,
                modelStatus = uiState.modelStatus,
                onDownloadClick = { viewModel.downloadKokoroModel() },
                onSwitchToSystem = { viewModel.setVoiceMode(VoiceEngineMode.SYSTEM_OFFLINE) },
                onSwitchToNeural = { viewModel.setVoiceMode(VoiceEngineMode.KOKORO_NEURAL) }
            )

            // Informational Notification Banner
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

    // Clustered Speed Selector Sheet
    if (speedSheetVisible) {
        SpeedSelectionSheet(
            currentSpeed = uiState.playbackSpeed,
            onSpeedSelected = { viewModel.setSpeed(it) },
            onDismiss = { speedSheetVisible = false }
        )
    }

    // Direct Text Paste Dialog
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
}

@Composable
fun VoiceModeStatusHeader(
    voiceMode: VoiceEngineMode,
    modelStatus: ModelStatus,
    onDownloadClick: () -> Unit,
    onSwitchToSystem: () -> Unit,
    onSwitchToNeural: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Voice Switcher Pills
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ObsidianSurfaceVariant)
                        .padding(3.dp)
                ) {
                    Surface(
                        color = if (voiceMode == VoiceEngineMode.SYSTEM_OFFLINE) AmberPrimary else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { onSwitchToSystem() }
                    ) {
                        Text(
                            text = "🔊 System Voice (Offline)",
                            color = if (voiceMode == VoiceEngineMode.SYSTEM_OFFLINE) ObsidianBackground else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    Surface(
                        color = if (voiceMode == VoiceEngineMode.KOKORO_NEURAL) AmberPrimary else Color.Transparent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable { onSwitchToNeural() }
                    ) {
                        Text(
                            text = "✨ Kokoro AI Voice",
                            color = if (voiceMode == VoiceEngineMode.KOKORO_NEURAL) ObsidianBackground else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // If Kokoro is active or requested but not downloaded, show download / progress helper
            if (voiceMode == VoiceEngineMode.KOKORO_NEURAL && modelStatus !is ModelStatus.Ready) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kokoro Voice (~85MB) not installed",
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Resumable download for studio-quality offline speech",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                    Button(
                        onClick = onDownloadClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Download", color = ObsidianBackground, fontSize = 11.sp)
                    }
                }

                if (modelStatus is ModelStatus.Downloading) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { modelStatus.progressPercent / 100f },
                        color = AmberPrimary,
                        trackColor = ObsidianSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${modelStatus.currentItem} (${modelStatus.progressPercent}%)",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
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
                    onSpeedSelected((Math.round(it * 20) / 20f)) // snap to 0.05 steps
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
