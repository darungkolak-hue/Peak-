package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.LyricLine
import com.example.model.LyricProject
import com.example.model.formatMillis
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MagentaPulse
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VioletAura

@Composable
fun MultiTrackTimeline(
    project: LyricProject,
    playbackMs: Long,
    onSeek: (Long) -> Unit,
    onAddLyricAtPlayhead: () -> Unit,
    onEditLyricLine: (index: Int, newText: String, newStartMs: Long, newEndMs: Long, section: String) -> Unit,
    onDeleteLyricLine: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val durationMs = project.durationMs.coerceAtLeast(10_000L)
    val pxPerSec = 26f
    val totalWidthDp: Dp = ((durationMs / 1000f) * pxPerSec).coerceAtLeast(320f).dp
    val scrollState = rememberScrollState()

    var editingIndex by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        // Scrubber Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatMillis(playbackMs),
                style = MaterialTheme.typography.labelMedium,
                color = ElectricCyan
            )
            Slider(
                value = playbackMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..durationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan,
                    inactiveTrackColor = ObsidianBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
                    .testTag("timeline_scrubber")
            )
            Text(
                text = formatMillis(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            IconButton(
                onClick = onAddLyricAtPlayhead,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("add_lyric_line_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add lyric line at current time",
                    tint = ElectricCyan
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            // Track Labels Column
            Column(
                modifier = Modifier.width(34.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TrackIconBadge(Icons.Rounded.Movie, VioletAura, "Video scene track")
                TrackIconBadge(Icons.Rounded.MusicNote, ElectricCyan, "Audio waveform track")
                TrackIconBadge(Icons.Rounded.Subtitles, MagentaPulse, "Lyric sync track")
            }

            // Scrollable Multi-Track Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier.width(totalWidthDp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Video / Scene Markers Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF15182B))
                    ) {
                        val markers = project.sceneMarkers
                        markers.forEachIndexed { idx, marker ->
                            val nextTime = markers.getOrNull(idx + 1)?.timeMs ?: durationMs
                            val startDp = ((marker.timeMs / 1000f) * pxPerSec).dp
                            val widthDp = (((nextTime - marker.timeMs).coerceAtLeast(800L) / 1000f) * pxPerSec).dp
                            Box(
                                modifier = Modifier
                                    .offset(x = startDp)
                                    .width(widthDp)
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        if (marker.isSlowMotion) Color(0x558B5CF6) else Color(0x3338BDF8)
                                    )
                                    .border(
                                        0.75.dp,
                                        if (marker.isSlowMotion) VioletAura else Color(0x6638BDF8),
                                        RoundedCornerShape(5.dp)
                                    )
                                    .clickable { onSeek(marker.timeMs) }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = if (marker.isSlowMotion) "SLOW • ${marker.transitionType}" else marker.transitionType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }

                    // 2. Audio Waveform Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF101322))
                    ) {
                        val bars = project.waveformBars
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (bars.isNotEmpty()) {
                                val stepW = size.width / bars.size.toFloat()
                                val barW = (stepW * 0.62f).coerceAtLeast(2f)
                                val playedFrac = playbackMs.toFloat() / durationMs.toFloat()
                                bars.forEachIndexed { i, amp ->
                                    val barH = (size.height * amp.toFloat().coerceIn(0.1f, 1f))
                                    val x = i * stepW
                                    val y = (size.height - barH) / 2f
                                    val isPlayed = (i.toFloat() / bars.size) <= playedFrac
                                    drawRoundRect(
                                        color = if (isPlayed) ElectricCyan else Color(0xFF334155),
                                        topLeft = Offset(x, y),
                                        size = Size(barW, barH),
                                        cornerRadius = CornerRadius(3f, 3f)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Dedicated Lyric Sync Track (Tap any block to edit text & timing)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF171326))
                    ) {
                        project.lyricLines.forEachIndexed { idx, line ->
                            val startDp = ((line.startMs / 1000f) * pxPerSec).dp
                            val widthDp = (((line.endMs - line.startMs).coerceAtLeast(700L) / 1000f) * pxPerSec).dp
                            val isActive = (playbackMs + project.syncOffsetMs) in line.startMs..line.endMs
                            Box(
                                modifier = Modifier
                                    .offset(x = startDp)
                                    .width(widthDp)
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isActive) MagentaPulse.copy(alpha = 0.42f) else Color(0x33EC4899)
                                    )
                                    .border(
                                        1.dp,
                                        if (isActive) ElectricCyan else MagentaPulse.copy(alpha = 0.55f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        onSeek(line.startMs)
                                        editingIndex = idx
                                    }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = line.text,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Playhead vertical line
                val playheadX = ((playbackMs / 1000f) * pxPerSec).dp
                Box(
                    modifier = Modifier
                        .offset(x = playheadX)
                        .width(2.dp)
                        .height(106.dp)
                        .background(ElectricCyan)
                )
            }
        }
    }

    // Manual Lyric Correction & Timing Editor Dialog
    val idxToEdit = editingIndex
    if (idxToEdit != null && idxToEdit in project.lyricLines.indices) {
        LyricLineEditDialog(
            line = project.lyricLines[idxToEdit],
            maxDurationMs = durationMs,
            onDismiss = { editingIndex = null },
            onSave = { text, startMs, endMs, section ->
                onEditLyricLine(idxToEdit, text, startMs, endMs, section)
                editingIndex = null
            },
            onDelete = {
                onDeleteLyricLine(idxToEdit)
                editingIndex = null
            }
        )
    }
}

@Composable
private fun TrackIconBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    desc: String
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = desc, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LyricLineEditDialog(
    line: LyricLine,
    maxDurationMs: Long,
    onDismiss: () -> Unit,
    onSave: (String, Long, Long, String) -> Unit,
    onDelete: () -> Unit
) {
    var text by remember(line) { mutableStateOf(line.text) }
    var section by remember(line) { mutableStateOf(line.sectionLabel) }
    var startSec by remember(line) { mutableFloatStateOf(line.startMs / 1000f) }
    var endSec by remember(line) { mutableFloatStateOf(line.endMs / 1000f) }
    val maxSec = (maxDurationMs / 1000f).coerceAtLeast(10f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = {
            Text("Edit Lyric & Timing", color = TextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Lyric Text") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = section,
                    onValueChange = { section = it },
                    label = { Text("Section (Verse / Chorus / Bridge)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Start: ${formatMillis((startSec * 1000).toLong())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
                Slider(
                    value = startSec.coerceIn(0f, maxSec),
                    onValueChange = {
                        startSec = it
                        if (endSec <= startSec + 0.3f) {
                            endSec = (startSec + 0.5f).coerceAtMost(maxSec)
                        }
                    },
                    valueRange = 0f..maxSec
                )
                Text(
                    text = "End: ${formatMillis((endSec * 1000).toLong())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MagentaPulse
                )
                Slider(
                    value = endSec.coerceIn(0f, maxSec),
                    onValueChange = { endSec = it.coerceAtLeast(startSec + 0.3f) },
                    valueRange = 0f..maxSec
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        text,
                        (startSec * 1000f).toLong(),
                        (endSec * 1000f).toLong(),
                        section
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = CoralAlert)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    )
}
