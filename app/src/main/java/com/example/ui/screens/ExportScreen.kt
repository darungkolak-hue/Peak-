package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.media.VideoExporter
import com.example.model.ExportAspectRatio
import com.example.model.ExportFps
import com.example.model.ExportResolution
import com.example.model.formatMillisShort
import com.example.ui.ExportUiState
import com.example.ui.LyrixViewModel
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAura

@Composable
fun ExportScreen(
    viewModel: LyrixViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    var selectedResolution by remember { mutableStateOf(settings.defaultResolution) }
    var selectedRatio by remember { mutableStateOf(settings.defaultAspectRatio) }
    var selectedFps by remember { mutableStateOf(settings.defaultFps) }

    LaunchedEffect(settings) {
        if (exportState is ExportUiState.Idle) {
            selectedResolution = settings.defaultResolution
            selectedRatio = settings.defaultAspectRatio
            selectedFps = settings.defaultFps
        }
    }

    val active = project
    if (active == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg),
            contentAlignment = Alignment.Center
        ) {
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }

    val estimatedBytes = remember(active.durationMs, selectedResolution, selectedFps) {
        VideoExporter.estimateOutputBytes(active.durationMs, selectedResolution, selectedFps)
    }
    val estimatedMb = (estimatedBytes / (1024.0 * 1024.0)).coerceAtLeast(0.5)
    val (dimW, dimH) = selectedRatio.dimensionsFor(selectedResolution)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Editor",
                        tint = TextPrimary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Export MP4 Video", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(active.title, style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                }
            }

            // Export Spec Summary Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ObsidianCard)
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Resolution", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExportResolution.entries.forEach { res ->
                        FilterChip(
                            selected = res == selectedResolution,
                            onClick = { selectedResolution = res },
                            label = { Text(res.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0x3300F5D4),
                                selectedLabelColor = ElectricCyan
                            )
                        )
                    }
                }

                Text("Aspect Ratio", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExportAspectRatio.entries.forEach { ratio ->
                        FilterChip(
                            selected = ratio == selectedRatio,
                            onClick = { selectedRatio = ratio },
                            label = { Text(ratio.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0x338B5CF6),
                                selectedLabelColor = VioletAura
                            )
                        )
                    }
                }

                Text("Frame Rate", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExportFps.entries.forEach { fps ->
                        FilterChip(
                            selected = fps == selectedFps,
                            onClick = { selectedFps = fps },
                            label = { Text(fps.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0x3300F5D4),
                                selectedLabelColor = ElectricCyan
                            )
                        )
                    }
                }
            }

            // Estimated Output Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF11162A))
                    .border(1.dp, Color(0x4400F5D4), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Estimated Output", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$dimW × $dimH • ${selectedFps.label} • ${formatMillisShort(active.durationMs)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Est. Size", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Text(
                        text = "~%.1f MB".format(estimatedMb),
                        style = MaterialTheme.typography.titleLarge,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Export Action / Progress / Result
            when (val state = exportState) {
                ExportUiState.Idle -> {
                    Button(
                        onClick = {
                            viewModel.startExportMp4(context, selectedResolution, selectedRatio, selectedFps)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_export_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Render & Export MP4", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                is ExportUiState.Exporting -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(ObsidianCard)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Encoding H.264 MP4...", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = ElectricCyan)
                        }
                        LinearProgressIndicator(
                            progress = { state.progress.coerceIn(0f, 1f) },
                            color = ElectricCyan,
                            trackColor = ObsidianBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(99.dp))
                        )
                        OutlinedButton(
                            onClick = { viewModel.cancelExport() },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Cancel", color = CoralAlert)
                        }
                    }
                }
                is ExportUiState.Success -> {
                    val res = state.result
                    val actualMb = res.fileSizeBytes / (1024.0 * 1024.0)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0x1F00F5D4))
                            .border(1.dp, ElectricCyan, RoundedCornerShape(18.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("MP4 Export Complete!", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                                Text(
                                    text = "${res.actualWidth}×${res.actualHeight} • %.1f MB • Saved to Movies/LYRIX_AI".format(actualMb),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ElectricCyan
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val shareUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        res.internalFile
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(shareUri, "video/mp4")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Play Lyric Video"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Play MP4", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val shareUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        res.internalFile
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "video/mp4"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share LYRIX AI Video"))
                                },
                                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, tint = ElectricCyan)
                                Spacer(Modifier.width(6.dp))
                                Text("Share", color = ElectricCyan)
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetExportState() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Export Another Version", color = TextPrimary)
                        }
                    }
                }
                is ExportUiState.Error -> {
                    Surface(
                        color = Color(0x22FB7185),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CoralAlert, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Export Failed", style = MaterialTheme.typography.titleMedium, color = CoralAlert)
                            Spacer(Modifier.height(6.dp))
                            Text(state.message, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.resetExportState() },
                                colors = ButtonDefaults.buttonColors(containerColor = CoralAlert)
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                }
            }
        }
    }
}
