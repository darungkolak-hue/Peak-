package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ALL_STYLE_PRESETS
import com.example.model.BUILTIN_STYLE_PRESETS
import com.example.model.BackgroundPreset
import com.example.model.ExportAspectRatio
import com.example.model.LyricAnimation
import com.example.model.LyricTextPosition
import com.example.model.STARTER_TEMPLATES
import com.example.model.parseHexColor
import com.example.ui.AiPipelineState
import com.example.ui.LyrixViewModel
import com.example.ui.components.LyricPreviewCanvas
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MagentaPulse
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletAura
import java.io.File

private val AVAILABLE_FONTS = listOf(
    "Space Grotesk",
    "Cormorant Garamond",
    "Bebas Neue",
    "Syne",
    "Plus Jakarta Sans",
    "JetBrains Mono"
)

private val COLOR_SWATCHES = listOf(
    "#FFFFFF",
    "#00F5D4",
    "#38BDF8",
    "#A78BFA",
    "#FB7185",
    "#F59E0B",
    "#1DB954",
    "#CBD5E1"
)

@Composable
fun EditorScreen(
    viewModel: LyrixViewModel,
    onBack: () -> Unit,
    onNavigateExport: () -> Unit
) {
    val context = LocalContext.current
    val project by viewModel.activeProject.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackMs by viewModel.playbackMs.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val aspectRatio by viewModel.previewAspectRatio.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose { viewModel.pausePlayback() }
    }

    val visualPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setCustomBackgroundImage(context, uri)
        }
    }

    val relinkAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.relinkAudioFile(context, uri)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active project loaded", color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Back to Home") }
            }
        }
        return
    }

    val audioMissing = active.localAudioPath.isBlank() || !File(active.localAudioPath).exists()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 820.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top Studio Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            viewModel.pausePlayback()
                            onBack()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = TextPrimary
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = active.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${active.bpm} BPM • ${active.detectedMood} • ${active.lyricLines.size} lines",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            maxLines = 1
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) TextPrimary else Color(0xFF475569)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) TextPrimary else Color(0xFF475569)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.runAiAutoEdit() },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("auto_edit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "AI Auto Edit",
                            tint = VioletAura
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            viewModel.pausePlayback()
                            onNavigateExport()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("export_nav_button")
                    ) {
                        Text("Export", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (statusBanner != null) {
                Surface(
                    color = Color(0x2600F5D4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.clearBanner() }
                ) {
                    Text(
                        text = statusBanner!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            if (audioMissing) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x26F59E0B))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Local audio file not on this device",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFCD34D),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { relinkAudioPicker.launch(arrayOf("audio/*", "video/*")) }) {
                        Text("Relink Audio", color = ElectricCyan)
                    }
                }
            }

            // Scrollable Studio Body (Preview + Controls + Timeline + Inspector)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Real-time Video Preview Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val previewWidthFraction = when (aspectRatio) {
                        ExportAspectRatio.PORTRAIT_9_16 -> 0.54f
                        ExportAspectRatio.SQUARE_1_1 -> 0.72f
                        ExportAspectRatio.LANDSCAPE_16_9 -> 1.0f
                    }
                    LyricPreviewCanvas(
                        project = active,
                        playbackMs = playbackMs,
                        aspectRatio = aspectRatio,
                        highQualityBlur = settings.highQualityBlurPreview,
                        modifier = Modifier.fillMaxWidth(previewWidthFraction)
                    )
                }

                // Playback + Aspect Ratio Controls Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.togglePlayPause() },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianBg
                            ),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("play_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.runAiAutoEdit() },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, VioletAura),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = VioletAura, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Auto Edit", color = TextPrimary, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ExportAspectRatio.entries.forEach { ratio ->
                            val selected = ratio == aspectRatio
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setPreviewAspectRatio(ratio) },
                                label = { Text(ratio.label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0x3300F5D4),
                                    selectedLabelColor = ElectricCyan
                                )
                            )
                        }
                    }
                }

                // Multi-Track Timeline (Video Scene, Audio Waveform, Lyric Sync)
                MultiTrackTimeline(
                    project = active,
                    playbackMs = playbackMs,
                    onSeek = { viewModel.seekTo(it) },
                    onAddLyricAtPlayhead = { viewModel.addLyricLine(playbackMs) },
                    onEditLyricLine = { idx, txt, s, e, sec ->
                        viewModel.updateLyricLine(idx, txt, s, e, sec)
                    },
                    onDeleteLyricLine = { idx -> viewModel.deleteLyricLine(idx) }
                )

                // Studio Inspector Tabs
                val tabs = listOf("Presets", "Style & Font", "FX & Sync", "Visuals")
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = ObsidianCard,
                    contentColor = ElectricCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedTab == idx,
                            onClick = { selectedTab = idx },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selectedTab == idx) ElectricCyan else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                // Inspector Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ObsidianCard)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedTab) {
                        0 -> PresetsInspector(
                            activePreset = active.presetName,
                            onSelectPreset = { viewModel.applyStylePreset(it) }
                        )
                        1 -> StyleFontInspector(
                            fontStyle = active.fontStyle,
                            fontSizeSp = active.fontSizeSp,
                            textColorHex = active.textColorHex,
                            highlightColorHex = active.highlightColorHex,
                            textPosition = LyricTextPosition.fromRaw(active.textPosition),
                            onSelectFont = { font -> viewModel.updateProjectStyle { it.copy(fontStyle = font) } },
                            onFontSizeChange = { sz -> viewModel.updateProjectStyle { it.copy(fontSizeSp = sz.toDouble()) } },
                            onSelectTextColor = { hex -> viewModel.updateProjectStyle { it.copy(textColorHex = hex) } },
                            onSelectHighlightColor = { hex -> viewModel.updateProjectStyle { it.copy(highlightColorHex = hex) } },
                            onSelectPosition = { pos -> viewModel.updateProjectStyle { it.copy(textPosition = pos.name) } }
                        )
                        2 -> EffectsSyncInspector(
                            animation = LyricAnimation.fromRaw(active.textAnimation),
                            shadow = active.textShadowIntensity.toFloat(),
                            blur = active.blurIntensity.toFloat(),
                            glow = active.glowIntensity.toFloat(),
                            wordHighlight = active.wordHighlightEnabled,
                            syncOffsetMs = active.syncOffsetMs,
                            slowMo = active.slowMotionEnabled,
                            beatFlash = active.beatFlashEnabled,
                            onSelectAnimation = { anim -> viewModel.updateProjectStyle { it.copy(textAnimation = anim.name) } },
                            onShadowChange = { v -> viewModel.updateProjectStyle { it.copy(textShadowIntensity = v.toDouble()) } },
                            onBlurChange = { v -> viewModel.updateProjectStyle { it.copy(blurIntensity = v.toDouble()) } },
                            onGlowChange = { v -> viewModel.updateProjectStyle { it.copy(glowIntensity = v.toDouble()) } },
                            onWordHighlightChange = { v -> viewModel.updateProjectStyle { it.copy(wordHighlightEnabled = v) } },
                            onSyncOffsetChange = { v -> viewModel.updateProjectStyle { it.copy(syncOffsetMs = v) } },
                            onSlowMoChange = { v -> viewModel.updateProjectStyle { it.copy(slowMotionEnabled = v) } },
                            onBeatFlashChange = { v -> viewModel.updateProjectStyle { it.copy(beatFlashEnabled = v) } }
                        )
                        3 -> VisualsInspector(
                            activePresetKey = active.backgroundPresetKey,
                            hasCustomBg = active.localBackgroundPath.isNotBlank(),
                            onSelectPresetBg = { viewModel.selectPresetBackground(it) },
                            onPickCustomBg = {
                                visualPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }

        if (aiState is AiPipelineState.Processing) {
            val step = (aiState as AiPipelineState.Processing).stepLabel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC07080F))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(ObsidianSurface)
                        .border(1.dp, VioletAura, RoundedCornerShape(22.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = ElectricCyan)
                    Spacer(Modifier.height(14.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                }
            }
        }

        if (aiState is AiPipelineState.Error) {
            val msg = (aiState as AiPipelineState.Error).message
            AlertDialog(
                onDismissRequest = { viewModel.clearAiError() },
                containerColor = ObsidianSurface,
                title = { Text("AI Error", color = TextPrimary) },
                text = { Text(msg, color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearAiError() }) {
                        Text("OK", color = ElectricCyan)
                    }
                }
            )
        }
    }
}

@Composable
private fun PresetsInspector(
    activePreset: String,
    onSelectPreset: (String) -> Unit
) {
    Text("Starter Video Templates", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(STARTER_TEMPLATES) { template ->
            val preset = template.preset
            val selected = preset.name.equals(activePreset, ignoreCase = true)
            val accent = parseHexColor(preset.highlightColorHex, ElectricCyan)
            Column(
                modifier = Modifier
                    .width(164.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) accent.copy(alpha = 0.16f) else Color(0xFF191D33))
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accent else ObsidianBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectPreset(preset.name) }
                    .padding(12.dp)
            ) {
                Text(
                    text = template.categoryTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = preset.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
        }
    }

    Text("Lyric Presets", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(BUILTIN_STYLE_PRESETS) { preset ->
            val selected = preset.name.equals(activePreset, ignoreCase = true)
            val accent = parseHexColor(preset.highlightColorHex, ElectricCyan)
            Column(
                modifier = Modifier
                    .width(154.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) accent.copy(alpha = 0.16f) else Color(0xFF191D33))
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accent else ObsidianBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectPreset(preset.name) }
                    .padding(12.dp)
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) accent else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = preset.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun StyleFontInspector(
    fontStyle: String,
    fontSizeSp: Double,
    textColorHex: String,
    highlightColorHex: String,
    textPosition: LyricTextPosition,
    onSelectFont: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onSelectTextColor: (String) -> Unit,
    onSelectHighlightColor: (String) -> Unit,
    onSelectPosition: (LyricTextPosition) -> Unit
) {
    Text("Font Style", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AVAILABLE_FONTS.forEach { font ->
            FilterChip(
                selected = font == fontStyle,
                onClick = { onSelectFont(font) },
                label = { Text(font) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0x3300F5D4),
                    selectedLabelColor = ElectricCyan
                )
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Font Size", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text("${fontSizeSp.toInt()} sp", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
    }
    Slider(
        value = fontSizeSp.toFloat().coerceIn(14f, 52f),
        onValueChange = onFontSizeChange,
        valueRange = 14f..52f,
        colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
    )

    Text("Text Color", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    ColorSwatchRow(selectedHex = textColorHex, onSelect = onSelectTextColor)

    Text("Word Highlight Color", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    ColorSwatchRow(selectedHex = highlightColorHex, onSelect = onSelectHighlightColor)

    Text("Text Position", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LyricTextPosition.entries.forEach { pos ->
            FilterChip(
                selected = pos == textPosition,
                onClick = { onSelectPosition(pos) },
                label = { Text(pos.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0x338B5CF6),
                    selectedLabelColor = VioletAura
                )
            )
        }
    }
}

@Composable
private fun ColorSwatchRow(selectedHex: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        COLOR_SWATCHES.forEach { hex ->
            val selected = hex.equals(selectedHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(hex))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) ElectricCyan else ObsidianBorder,
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}

@Composable
private fun EffectsSyncInspector(
    animation: LyricAnimation,
    shadow: Float,
    blur: Float,
    glow: Float,
    wordHighlight: Boolean,
    syncOffsetMs: Long,
    slowMo: Boolean,
    beatFlash: Boolean,
    onSelectAnimation: (LyricAnimation) -> Unit,
    onShadowChange: (Float) -> Unit,
    onBlurChange: (Float) -> Unit,
    onGlowChange: (Float) -> Unit,
    onWordHighlightChange: (Boolean) -> Unit,
    onSyncOffsetChange: (Long) -> Unit,
    onSlowMoChange: (Boolean) -> Unit,
    onBeatFlashChange: (Boolean) -> Unit
) {
    Text("Text Animation", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LyricAnimation.entries.forEach { anim ->
            FilterChip(
                selected = anim == animation,
                onClick = { onSelectAnimation(anim) },
                label = { Text(anim.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0x3300F5D4),
                    selectedLabelColor = ElectricCyan
                )
            )
        }
    }

    LabeledSlider("Text Shadow", shadow, onShadowChange, "${(shadow * 100).toInt()}%")
    LabeledSlider("Blur Intensity", blur, onBlurChange, "${(blur * 100).toInt()}%")
    LabeledSlider("Glow Intensity", glow, onGlowChange, "${(glow * 100).toInt()}%")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Lyric Sync Offset", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text("${syncOffsetMs} ms", style = MaterialTheme.typography.labelMedium, color = MagentaPulse)
    }
    Slider(
        value = syncOffsetMs.toFloat().coerceIn(-2000f, 2000f),
        onValueChange = { onSyncOffsetChange(it.toLong()) },
        valueRange = -2000f..2000f,
        colors = SliderDefaults.colors(thumbColor = MagentaPulse, activeTrackColor = MagentaPulse)
    )

    ToggleRow("Word-Level Highlight", wordHighlight, onWordHighlightChange)
    ToggleRow("Cinematic Slow Motion", slowMo, onSlowMoChange)
    ToggleRow("Beat-Synced Flash", beatFlash, onBeatFlashChange)
}

@Composable
private fun VisualsInspector(
    activePresetKey: String,
    hasCustomBg: Boolean,
    onSelectPresetBg: (BackgroundPreset) -> Unit,
    onPickCustomBg: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Cinematic Background", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        OutlinedButton(
            onClick = onPickCustomBg,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan)
        ) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (hasCustomBg) "Change Media" else "Upload Photo/Video", color = ElectricCyan)
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(BackgroundPreset.entries) { bg ->
            val selected = !hasCustomBg && bg.key == activePresetKey
            Column(
                modifier = Modifier
                    .width(110.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) ElectricCyan else ObsidianBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectPresetBg(bg) }
            ) {
                Image(
                    painter = painterResource(id = bg.drawableRes),
                    contentDescription = bg.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141728))
                        .padding(8.dp)
                ) {
                    Text(
                        text = bg.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) ElectricCyan else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueText: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
        }
        Slider(
            value = value.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = ElectricCyan, activeTrackColor = ElectricCyan)
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBg,
                checkedTrackColor = ElectricCyan
            )
        )
    }
}
