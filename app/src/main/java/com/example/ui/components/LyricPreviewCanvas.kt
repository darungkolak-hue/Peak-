package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ai.distributeWordTimings
import com.example.model.BackgroundPreset
import com.example.model.ExportAspectRatio
import com.example.model.LyricAnimation
import com.example.model.LyricProject
import com.example.model.LyricTextPosition
import com.example.model.VideoFilterStyle
import com.example.model.parseHexColor
import com.example.model.visualFilterForPresetName
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.fontFamilyForName
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LyricPreviewCanvas(
    project: LyricProject,
    playbackMs: Long,
    aspectRatio: ExportAspectRatio,
    highQualityBlur: Boolean,
    modifier: Modifier = Modifier
) {
    val ratioFloat = aspectRatio.ratioW.toFloat() / aspectRatio.ratioH.toFloat()
    val duration = project.durationMs.coerceAtLeast(1000L)
    val progress = (playbackMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    val beatPeriodMs = (60_000.0 / project.bpm.coerceIn(50L, 200L)).coerceAtLeast(300.0)
    val beatPhase = ((playbackMs % beatPeriodMs) / beatPeriodMs).toFloat()
    val beatPulse = if (project.beatFlashEnabled) {
        ((1f - beatPhase) * (1f - beatPhase)).coerceIn(0f, 1f)
    } else 0f

    val inSlowMo = project.slowMotionEnabled && project.sceneMarkers.any {
        it.isSlowMotion && playbackMs >= it.timeMs && playbackMs <= it.timeMs + 6000L
    }

    val activeScene = remember(project.sceneMarkers, playbackMs) {
        project.sceneMarkers.lastOrNull { it.timeMs <= playbackMs }
    }
    val effectiveBgKey = activeScene?.backgroundKey ?: project.backgroundPresetKey
    val bgPreset = BackgroundPreset.fromKey(effectiveBgKey)

    val syncedTimeMs = playbackMs + project.syncOffsetMs
    val activeLine = remember(project.lyricLines, syncedTimeMs) {
        project.lyricLines.firstOrNull { syncedTimeMs in it.startMs..it.endMs }
    }

    val primaryColor = remember(project.textColorHex) {
        parseHexColor(project.textColorHex, Color.White)
    }
    val highlightColor = remember(project.highlightColorHex) {
        parseHexColor(project.highlightColorHex, Color(0xFF00F5D4))
    }
    val fontFamily = remember(project.fontStyle) { fontFamilyForName(project.fontStyle) }
    val animationType = remember(project.textAnimation) { LyricAnimation.fromRaw(project.textAnimation) }
    val position = remember(project.textPosition) { LyricTextPosition.fromRaw(project.textPosition) }

    val kenBurnsScale = 1.0f + (if (inSlowMo) 0.05f else 0.14f) * progress + beatPulse * 0.02f
    val blurDp = if (highQualityBlur) (project.blurIntensity * 10f).dp else 0.dp
    val filterStyle = remember(project.presetName) { visualFilterForPresetName(project.presetName) }
    val bgColorFilter = remember(filterStyle) {
        when (filterStyle) {
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

    Box(
        modifier = modifier
            .aspectRatio(ratioFloat)
            .clip(RoundedCornerShape(18.dp))
            .background(ObsidianBg)
            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Background layer
        val bgFile = project.localBackgroundPath.takeIf { it.isNotBlank() }?.let { File(it) }
        val bgModifier = Modifier
            .fillMaxSize()
            .scale(kenBurnsScale)
            .then(if (blurDp > 0.dp) Modifier.blur(blurDp) else Modifier)

        if (bgFile != null && bgFile.exists()) {
            AsyncImage(
                model = bgFile,
                contentDescription = "Project background visual",
                contentScale = ContentScale.Crop,
                colorFilter = bgColorFilter,
                modifier = bgModifier
            )
        } else {
            Image(
                painter = painterResource(id = bgPreset.drawableRes),
                contentDescription = "Cinematic preset background",
                contentScale = ContentScale.Crop,
                colorFilter = bgColorFilter,
                modifier = bgModifier
            )
        }

        // Template filter tint & cinema vignette
        val topTint = when (filterStyle) {
            VideoFilterStyle.NEON_GRADIENT -> Color(0x6600F5D4)
            VideoFilterStyle.VINTAGE_SEPIA -> Color(0x66451A03)
            VideoFilterStyle.MONOCHROME_NOIR -> Color(0xAA000000)
            VideoFilterStyle.DREAMY_HAZE -> Color(0x66312E81)
            VideoFilterStyle.NONE -> Color(0x8807080F)
        }
        val bottomTint = when (filterStyle) {
            VideoFilterStyle.NEON_GRADIENT -> Color(0x88EC4899)
            VideoFilterStyle.VINTAGE_SEPIA -> Color(0x99271306)
            VideoFilterStyle.MONOCHROME_NOIR -> Color(0xCC000000)
            VideoFilterStyle.DREAMY_HAZE -> Color(0x991E1B4B)
            VideoFilterStyle.NONE -> Color(0xB807080F)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(topTint, Color(0x5207080F), bottomTint)
                    )
                )
        )

        if (beatPulse > 0.08f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(highlightColor.copy(alpha = beatPulse * 0.14f))
            )
        }

        // Top HUD badges inside preview
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x9907080F))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = activeLine?.sectionLabel?.uppercase() ?: project.presetName.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = highlightColor
                )
            }
            if (inSlowMo) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x998B5CF6))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SLOW-MO",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }

        // Synchronized Animated Lyrics Layer
        val lyricAlignment = when (position) {
            LyricTextPosition.TOP -> Alignment.TopCenter
            LyricTextPosition.CENTER -> Alignment.Center
            LyricTextPosition.BOTTOM -> Alignment.BottomCenter
        }

        if (activeLine != null) {
            val elapsed = (syncedTimeMs - activeLine.startMs).coerceAtLeast(0L)
            val remaining = (activeLine.endMs - syncedTimeMs).coerceAtLeast(0L)
            val enterFrac = (elapsed / 260f).coerceIn(0f, 1f)
            val exitFrac = (remaining / 200f).coerceIn(0f, 1f)
            val lineAlpha = minOf(enterFrac, exitFrac)

            val animScale = when (animationType) {
                LyricAnimation.ZOOM_IN -> 0.86f + 0.14f * enterFrac
                LyricAnimation.WORD_POP -> 0.94f + 0.06f * enterFrac
                else -> 1f
            }
            val animTranslateY = when (animationType) {
                LyricAnimation.SMOOTH_SLIDE -> (1f - enterFrac) * 18f
                else -> 0f
            }
            val textBlur = if (animationType == LyricAnimation.BLUR_REVEAL && highQualityBlur) {
                ((1f - enterFrac) * 8f).dp
            } else 0.dp

            val words = remember(activeLine) {
                if (activeLine.words.isNotEmpty()) activeLine.words
                else distributeWordTimings(activeLine.text, activeLine.startMs, activeLine.endMs)
            }

            val previewFontScale = when (aspectRatio) {
                ExportAspectRatio.PORTRAIT_9_16 -> 0.72f
                ExportAspectRatio.SQUARE_1_1 -> 0.68f
                ExportAspectRatio.LANDSCAPE_16_9 -> 0.56f
            }
            val displaySizeSp = (project.fontSizeSp.toFloat() * previewFontScale).coerceIn(14f, 34f)

            Box(
                modifier = Modifier
                    .align(lyricAlignment)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 28.dp)
                    .graphicsLayer {
                        alpha = lineAlpha
                        scaleX = animScale
                        scaleY = animScale
                        translationY = animTranslateY
                    }
                    .then(if (textBlur > 0.2.dp) Modifier.blur(textBlur) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    words.forEach { wt ->
                        val isCurrentWord = project.wordHighlightEnabled &&
                            syncedTimeMs in wt.startMs..wt.endMs
                        val wordScale by animateFloatAsState(
                            targetValue = if (isCurrentWord) 1.12f else 1.0f,
                            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
                            label = "word_pop"
                        )
                        val shadowColor = if (isCurrentWord && project.glowIntensity > 0.1) {
                            highlightColor.copy(alpha = (0.85f * project.glowIntensity.toFloat()).coerceIn(0f, 1f))
                        } else {
                            Color.Black.copy(alpha = (0.85f * project.textShadowIntensity.toFloat()).coerceIn(0f, 1f))
                        }
                        val shadowBlur = if (isCurrentWord && project.glowIntensity > 0.1) {
                            (22f * project.glowIntensity.toFloat()).coerceAtLeast(4f)
                        } else {
                            (12f * project.textShadowIntensity.toFloat()).coerceAtLeast(1f)
                        }

                        Text(
                            text = wt.word,
                            style = TextStyle(
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = displaySizeSp.sp,
                                lineHeight = (displaySizeSp * 1.24f).sp,
                                color = if (isCurrentWord) highlightColor else primaryColor,
                                shadow = Shadow(
                                    color = shadowColor,
                                    offset = Offset(0f, if (isCurrentWord) 0f else 3f),
                                    blurRadius = shadowBlur
                                ),
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.scale(wordScale)
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .align(lyricAlignment)
                    .padding(20.dp)
                    .alpha(0.55f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (project.lyricLines.isEmpty()) "Instrumental / No lyrics detected" else "• • •",
                    style = MaterialTheme.typography.labelMedium,
                    color = primaryColor
                )
            }
        }
    }
}
