package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.BackgroundPreset
import com.example.model.LyricProject
import com.example.model.STARTER_TEMPLATES
import com.example.model.StarterTemplate
import com.example.model.VideoFilterStyle
import com.example.model.formatMillisShort
import com.example.model.parseHexColor
import com.example.ui.AiPipelineState
import com.example.ui.LyrixViewModel
import com.example.ui.UiState
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary
import com.example.ui.theme.VioletAura
import com.example.ui.theme.fontFamilyForName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: LyrixViewModel,
    onOpenEditor: (LyricProject) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()

    var projectToDelete by remember { mutableStateOf<LyricProject?>(null) }
    var showNewProjectTemplateSheet by remember { mutableStateOf(false) }
    var pendingTemplateForUpload by remember { mutableStateOf<StarterTemplate?>(null) }
    var activeTemplateSheetTarget by remember { mutableStateOf<StarterTemplate?>(null) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importMediaAndCreateProject(
                context = context,
                uri = uri,
                starterTemplate = pendingTemplateForUpload
            ) { createdProject ->
                pendingTemplateForUpload = null
                onOpenEditor(createdProject)
            }
        } else {
            pendingTemplateForUpload = null
        }
    }

    val recentProjects = (projectsState as? UiState.Success)?.data.orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top App Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(ElectricCyan, VioletAura))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = "LYRIX AI Logo",
                                tint = ObsidianBg,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "LYRIX AI",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Automatic Lyric Video Studio",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ObsidianCard)
                            .border(1.dp, ObsidianBorder, CircleShape)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "App Settings",
                            tint = TextPrimary
                        )
                    }
                }
            }

            // Hero Creation Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0x4400F5D4), RoundedCornerShape(24.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_bg_cinematic_rain),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xEB07080F),
                                        Color(0xBD0E101D),
                                        Color(0x99231942)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        Surface(
                            color = Color(0x2800F5D4),
                            shape = RoundedCornerShape(99.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "PICK TEMPLATE → UPLOAD → EXPORT MP4",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "Create Cinematic\nLyric Videos Automatically",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "Choose a starter style like Neon Night or Vintage Film, or let AI direct everything from your song's mood and beat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showNewProjectTemplateSheet = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("new_project_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "New Project",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val latest = recentProjects.firstOrNull()
                                    if (latest != null) {
                                        onOpenEditor(latest)
                                    } else {
                                        showNewProjectTemplateSheet = true
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, VioletAura),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("ai_lyric_editor_button")
                            ) {
                                Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = VioletAura, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "AI Lyric Editor",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Starter Templates Carousel
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Style,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Starter Templates",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Apply before editing",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("starter_templates_carousel")
                    ) {
                        items(STARTER_TEMPLATES, key = { it.id }) { template ->
                            StarterTemplateCard(
                                template = template,
                                onClick = { activeTemplateSheetTarget = template }
                            )
                        }
                    }
                }
            }

            // Recent Projects Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Projects",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "${recentProjects.size} saved",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            when (val state = projectsState) {
                UiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ElectricCyan)
                        }
                    }
                }
                is UiState.Error -> {
                    item {
                        Surface(
                            color = Color(0x22FB7185),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CoralAlert.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoralAlert,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(ObsidianCard)
                                    .border(1.dp, ObsidianBorder, RoundedCornerShape(20.dp))
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x2200F5D4)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "No lyric edits yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Pick a Starter Template above or tap New Project to upload an MP3, WAV, M4A, or MP4 video.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(state.data, key = { it.id }) { project ->
                            RecentProjectCard(
                                project = project,
                                onClick = { onOpenEditor(project) },
                                onDelete = { projectToDelete = project }
                            )
                        }
                    }
                }
            }
        }

        // Pre-Edit "Choose Starter Template" Sheet for New Project
        if (showNewProjectTemplateSheet) {
            NewProjectTemplatePickerSheet(
                onDismiss = { showNewProjectTemplateSheet = false },
                onStartUpload = { chosenTemplate ->
                    showNewProjectTemplateSheet = false
                    pendingTemplateForUpload = chosenTemplate
                    mediaPickerLauncher.launch(arrayOf("audio/*", "video/*"))
                }
            )
        }

        // Starter Template Action Sheet (Start New or Apply to Recent Project before editing)
        val templateTarget = activeTemplateSheetTarget
        if (templateTarget != null) {
            StarterTemplateActionSheet(
                template = templateTarget,
                recentProjects = recentProjects,
                onDismiss = { activeTemplateSheetTarget = null },
                onCreateNewWithTemplate = {
                    activeTemplateSheetTarget = null
                    pendingTemplateForUpload = templateTarget
                    mediaPickerLauncher.launch(arrayOf("audio/*", "video/*"))
                },
                onApplyToProject = { project ->
                    activeTemplateSheetTarget = null
                    viewModel.applyStarterTemplateAndOpen(project, templateTarget) { updated ->
                        onOpenEditor(updated)
                    }
                }
            )
        }

        // AI Processing Overlay
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
                        .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = ElectricCyan,
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.5.dp
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "LYRIX AI Working...",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ElectricCyan,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (aiState is AiPipelineState.Error) {
            val errMsg = (aiState as AiPipelineState.Error).message
            AlertDialog(
                onDismissRequest = { viewModel.clearAiError() },
                containerColor = ObsidianSurface,
                title = { Text("AI Analysis Error", color = TextPrimary) },
                text = { Text(errMsg, color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearAiError() }) {
                        Text("OK", color = ElectricCyan)
                    }
                }
            )
        }

        if (projectToDelete != null) {
            val target = projectToDelete!!
            AlertDialog(
                onDismissRequest = { projectToDelete = null },
                containerColor = ObsidianSurface,
                title = { Text("Delete Project?", color = TextPrimary) },
                text = { Text("Remove \"${target.title}\" from your LYRIX AI cloud projects?", color = TextSecondary) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteProject(target.id)
                            projectToDelete = null
                        }
                    ) {
                        Text("Delete", color = CoralAlert)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { projectToDelete = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun StarterTemplateCard(
    template: StarterTemplate,
    onClick: () -> Unit
) {
    val bgPreset = BackgroundPreset.fromKey(template.preset.backgroundKey)
    val accent = parseHexColor(template.preset.highlightColorHex, ElectricCyan)
    val textCol = parseHexColor(template.preset.textColorHex, Color.White)
    val font = remember(template.preset.fontStyle) { fontFamilyForName(template.preset.fontStyle) }
    val colorFilter = remember(template.filterStyle) {
        when (template.filterStyle) {
            VideoFilterStyle.MONOCHROME_NOIR -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            VideoFilterStyle.VINTAGE_SEPIA -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.393f, 0.769f, 0.189f, 0f, 12f,
                        0.349f, 0.686f, 0.168f, 0f, 6f,
                        0.272f, 0.534f, 0.131f, 0f, -8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            else -> null
        }
    }

    Column(
        modifier = Modifier
            .width(164.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(ObsidianCard)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("starter_template_${template.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(126.dp)
        ) {
            Image(
                painter = painterResource(id = bgPreset.drawableRes),
                contentDescription = template.name,
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
            val overlayBrush = when (template.filterStyle) {
                VideoFilterStyle.NEON_GRADIENT -> Brush.verticalGradient(listOf(Color(0x4400F5D4), Color(0xBBEC4899)))
                VideoFilterStyle.VINTAGE_SEPIA -> Brush.verticalGradient(listOf(Color(0x44451A03), Color(0xBB271306)))
                VideoFilterStyle.MONOCHROME_NOIR -> Brush.verticalGradient(listOf(Color(0x66000000), Color(0xDD000000)))
                VideoFilterStyle.DREAMY_HAZE -> Brush.verticalGradient(listOf(Color(0x55312E81), Color(0xBB1E1B4B)))
                VideoFilterStyle.NONE -> Brush.verticalGradient(listOf(Color(0x4407080F), Color(0xBB07080F)))
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayBrush)
                    .padding(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xAA07080F))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = template.categoryTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }

                Text(
                    text = template.sampleLyric,
                    fontFamily = font,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    color = textCol,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = template.preset.fontStyle,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewProjectTemplatePickerSheet(
    onDismiss: () -> Unit,
    onStartUpload: (StarterTemplate?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTemplate by remember { mutableStateOf<StarterTemplate?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Choose a Starter Template",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "Pick a video style before uploading your song, or let LYRIX AI auto-direct based on mood and tempo.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            // AI Auto-Direct Option
            val autoSelected = selectedTemplate == null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (autoSelected) Color(0x2400F5D4) else ObsidianCard)
                    .border(
                        width = if (autoSelected) 1.5.dp else 1.dp,
                        color = if (autoSelected) ElectricCyan else ObsidianBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { selectedTemplate = null }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = ElectricCyan)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Auto-Direct (Recommended)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text("AI matches font, colors & visuals to your track's mood", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                if (autoSelected) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = ElectricCyan)
                }
            }

            // Starter Templates List
            STARTER_TEMPLATES.forEach { template ->
                val isSelected = selectedTemplate?.id == template.id
                val accent = parseHexColor(template.preset.highlightColorHex, ElectricCyan)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.16f) else ObsidianCard)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accent else ObsidianBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedTemplate = template }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bg = BackgroundPreset.fromKey(template.preset.backgroundKey)
                    Image(
                        painter = painterResource(id = bg.drawableRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(template.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            TagBadge(template.categoryTag, accent)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(template.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2)
                    }
                    if (isSelected) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = accent)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { onStartUpload(selectedTemplate) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_template_upload_button")
            ) {
                Text(
                    text = if (selectedTemplate == null) "Upload Audio / Video (AI Auto)" else "Upload Audio / Video (${selectedTemplate!!.name})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StarterTemplateActionSheet(
    template: StarterTemplate,
    recentProjects: List<LyricProject>,
    onDismiss: () -> Unit,
    onCreateNewWithTemplate: () -> Unit,
    onApplyToProject: (LyricProject) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent = parseHexColor(template.preset.highlightColorHex, ElectricCyan)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagBadge(template.categoryTag, accent)
                Spacer(Modifier.width(8.dp))
                Text(template.name, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            }
            Text(template.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            Button(
                onClick = onCreateNewWithTemplate,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("use_template_new_project_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New Project with ${template.name}", fontWeight = FontWeight.Bold)
            }

            if (recentProjects.isNotEmpty()) {
                Text(
                    text = "Or apply before opening an existing project:",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                recentProjects.take(4).forEach { project ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianCard)
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                            .clickable { onApplyToProject(project) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1)
                            Text("Currently: ${project.presetName}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Text("Apply & Edit", style = MaterialTheme.typography.labelMedium, color = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentProjectCard(
    project: LyricProject,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val bgPreset = BackgroundPreset.fromKey(project.backgroundPresetKey)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ObsidianCard)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("project_card_${project.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            Image(
                painter = painterResource(id = bgPreset.drawableRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x5507080F)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Open project",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TagBadge(project.presetName, ElectricCyan)
                TagBadge("${project.bpm} BPM", VioletAura)
                Text(
                    text = formatMillisShort(project.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${project.lyricLines.size} lyric lines • ${project.detectedMood}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete project",
                tint = TextTertiary
            )
        }
    }
}

@Composable
private fun TagBadge(label: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1
        )
    }
}
