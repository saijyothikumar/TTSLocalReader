package com.tts.reader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tts.reader.ui.theme.*
import com.tts.reader.ui.viewmodel.SleepMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepModeSheet(
    currentSleepMode: SleepMode,
    onSetSleepMode: (SleepMode) -> Unit,
    onDismiss: () -> Unit
) {
    var customNumberText by remember { mutableStateOf("") }

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
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = AmberPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Sleep & Chapter Timer",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Stops playback and releases battery wake locks when reached.",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Option 1: Off (Continuous)
            val isOff = currentSleepMode is SleepMode.Off
            Surface(
                color = if (isOff) AmberGlow.copy(alpha = 0.2f) else ObsidianSurfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isOff) AmberPrimary else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSetSleepMode(SleepMode.Off)
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Continuous Playback (Timer Off)",
                            color = if (isOff) AmberPrimary else TextPrimary,
                            fontWeight = if (isOff) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Keep playing subsequent chapters without auto-sleep.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (isOff) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Option 2: Loop Current Chapter Once Then Sleep
            val isLoop = currentSleepMode is SleepMode.LoopChapter
            Surface(
                color = if (isLoop) AmberGlow.copy(alpha = 0.2f) else ObsidianSurfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isLoop) AmberPrimary else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSetSleepMode(SleepMode.LoopChapter())
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Repeat, contentDescription = null, tint = if (isLoop) AmberPrimary else TextMuted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Loop Current Chapter Once",
                                color = if (isLoop) AmberPrimary else TextPrimary,
                                fontWeight = if (isLoop) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Replays current chapter from start once, then goes to sleep.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (isLoop) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Option 3: Play N Chapters Then Sleep
            Text(
                text = "Play N Chapters Then Sleep",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Preset Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 2, 3, 5).forEach { n ->
                    val isSelected = currentSleepMode is SleepMode.PlayNChapters && currentSleepMode.totalChapters == n
                    Surface(
                        color = if (isSelected) AmberPrimary else ObsidianSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onSetSleepMode(SleepMode.PlayNChapters(totalChapters = n, remainingChapters = n))
                                onDismiss()
                            }
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (n == 1) "1 ch" else "$n ch",
                                color = if (isSelected) ObsidianBackground else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom Chapters Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customNumberText,
                    onValueChange = { customNumberText = it.filter { ch -> ch.isDigit() } },
                    placeholder = { Text("Custom chapters (e.g. 4)", color = TextMuted, fontSize = 12.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AmberPrimary,
                        unfocusedBorderColor = ObsidianSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val num = customNumberText.toIntOrNull()
                        if (num != null && num > 0) {
                            onSetSleepMode(SleepMode.PlayNChapters(totalChapters = num, remainingChapters = num))
                            onDismiss()
                        }
                    },
                    enabled = (customNumberText.toIntOrNull() ?: 0) > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Set", color = ObsidianBackground, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
