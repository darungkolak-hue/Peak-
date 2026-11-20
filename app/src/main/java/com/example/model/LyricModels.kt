package com.example.model

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class WordTiming(
    val word: String = "",
    val startMs: Long = 0L,
    val endMs: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "word" to word,
        "startMs" to startMs,
        "endMs" to endMs
    )
}

@Keep
@IgnoreExtraProperties
data class LyricLine(
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val sectionLabel: String = "Verse",
    val text: String = "",
    val words: List<WordTiming> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "startMs" to startMs,
        "endMs" to endMs,
        "sectionLabel" to sectionLabel,
        "text" to text,
        "words" to words.map { it.toMap() }
    )
}

@Keep
@IgnoreExtraProperties
data class SceneMarker(
    val timeMs: Long = 0L,
    val transitionType: String = "FADE",
    val isSlowMotion: Boolean = false,
    val backgroundKey: String = "cinematic_rain"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "timeMs" to timeMs,
        "transitionType" to transitionType,
        "isSlowMotion" to isSlowMotion,
        "backgroundKey" to backgroundKey
    )
}

enum class LyricTextPosition(val label: String) {
    TOP("Top"),
    CENTER("Center"),
    BOTTOM("Bottom");

    companion object {
        fun fromRaw(raw: String): LyricTextPosition =
            entries.firstOrNull { it.name == raw } ?: CENTER
    }
}

enum class LyricAnimation(val label: String) {
    FADE_IN("Fade In"),
    ZOOM_IN("Zoom In"),
    BLUR_REVEAL("Blur Reveal"),
    SMOOTH_SLIDE("Smooth Slide"),
    WORD_POP("Word Pop");

    companion object {
        fun fromRaw(raw: String): LyricAnimation =
            entries.firstOrNull { it.name == raw } ?: BLUR_REVEAL
    }
}

enum class BackgroundPreset(
    val key: String,
    val title: String,
    val drawableRes: Int
) {
    CINEMATIC_RAIN("cinematic_rain", "Rainy Bokeh", R.drawable.img_bg_cinematic_rain),
    CONCERT_NEON("concert_neon", "Concert Laser", R.drawable.img_bg_concert_neon),
    SUNSET_AESTHETIC("sunset_aesthetic", "Twilight Glow", R.drawable.img_bg_sunset_aesthetic),
    MINIMAL_DARK("minimal_dark", "Obsidian Silk", R.drawable.img_bg_minimal_dark);

    companion object {
        fun fromKey(key: String): BackgroundPreset =
            entries.firstOrNull { it.key == key } ?: CINEMATIC_RAIN
    }
}

data class LyricStylePreset(
    val name: String,
    val subtitle: String,
    val fontStyle: String,
    val fontSizeSp: Double,
    val textColorHex: String,
    val highlightColorHex: String,
    val textPosition: LyricTextPosition,
    val textAnimation: LyricAnimation,
    val textShadowIntensity: Double,
    val blurIntensity: Double,
    val glowIntensity: Double,
    val wordHighlightEnabled: Boolean,
    val slowMotionEnabled: Boolean,
    val beatFlashEnabled: Boolean,
    val backgroundKey: String
)

enum class VideoFilterStyle {
    NONE,
    NEON_GRADIENT,
    VINTAGE_SEPIA,
    MONOCHROME_NOIR,
    DREAMY_HAZE
}

data class StarterTemplate(
    val id: String,
    val name: String,
    val categoryTag: String,
    val description: String,
    val sampleLyric: String,
    val filterStyle: VideoFilterStyle,
    val preset: LyricStylePreset
)

val BUILTIN_STYLE_PRESETS = listOf(
    LyricStylePreset(
        name = "Cinematic Sad",
        subtitle = "Soft serif • rain blur • moody blue",
        fontStyle = "Cormorant Garamond",
        fontSizeSp = 30.0,
        textColorHex = "#E2E8F0",
        highlightColorHex = "#38BDF8",
        textPosition = LyricTextPosition.CENTER,
        textAnimation = LyricAnimation.BLUR_REVEAL,
        textShadowIntensity = 0.85,
        blurIntensity = 0.45,
        glowIntensity = 0.40,
        wordHighlightEnabled = true,
        slowMotionEnabled = true,
        beatFlashEnabled = false,
        backgroundKey = "cinematic_rain"
    ),
    LyricStylePreset(
        name = "Aesthetic",
        subtitle = "Editorial Syne • sunset rose • dream",
        fontStyle = "Syne",
        fontSizeSp = 28.0,
        textColorHex = "#FFF1F2",
        highlightColorHex = "#FB7185",
        textPosition = LyricTextPosition.CENTER,
        textAnimation = LyricAnimation.FADE_IN,
        textShadowIntensity = 0.60,
        blurIntensity = 0.30,
        glowIntensity = 0.55,
        wordHighlightEnabled = true,
        slowMotionEnabled = true,
        beatFlashEnabled = false,
        backgroundKey = "sunset_aesthetic"
    ),
    LyricStylePreset(
        name = "Concert Vibes",
        subtitle = "Bold Bebas • neon laser • beat pop",
        fontStyle = "Bebas Neue",
        fontSizeSp = 38.0,
        textColorHex = "#FFFFFF",
        highlightColorHex = "#00F5D4",
        textPosition = LyricTextPosition.CENTER,
        textAnimation = LyricAnimation.ZOOM_IN,
        textShadowIntensity = 0.90,
        blurIntensity = 0.20,
        glowIntensity = 0.85,
        wordHighlightEnabled = true,
        slowMotionEnabled = false,
        beatFlashEnabled = true,
        backgroundKey = "concert_neon"
    ),
    LyricStylePreset(
        name = "Dark Emotional",
        subtitle = "Heavy Space • violet glow • deep cut",
        fontStyle = "Space Grotesk",
        fontSizeSp = 31.0,
        textColorHex = "#CBD5E1",
        highlightColorHex = "#A78BFA",
        textPosition = LyricTextPosition.BOTTOM,
        textAnimation = LyricAnimation.BLUR_REVEAL,
        textShadowIntensity = 0.95,
        blurIntensity = 0.55,
        glowIntensity = 0.70,
        wordHighlightEnabled = true,
        slowMotionEnabled = true,
        beatFlashEnabled = true,
        backgroundKey = "cinematic_rain"
    ),
    LyricStylePreset(
        name = "Minimalist",
        subtitle = "Clean Sans • obsidian • crisp slide",
        fontStyle = "Plus Jakarta Sans",
        fontSizeSp = 24.0,
        textColorHex = "#F8FAFC",
        highlightColorHex = "#94A3B8",
        textPosition = LyricTextPosition.CENTER,
        textAnimation = LyricAnimation.SMOOTH_SLIDE,
        textShadowIntensity = 0.35,
        blurIntensity = 0.10,
        glowIntensity = 0.15,
        wordHighlightEnabled = false,
        slowMotionEnabled = false,
        beatFlashEnabled = false,
        backgroundKey = "minimal_dark"
    ),
    LyricStylePreset(
        name = "Spotify Lyrics Style",
        subtitle = "Punchy word pop • live line highlight",
        fontStyle = "Space Grotesk",
        fontSizeSp = 28.0,
        textColorHex = "#94A3B8",
        highlightColorHex = "#1DB954",
        textPosition = LyricTextPosition.CENTER,
        textAnimation = LyricAnimation.WORD_POP,
        textShadowIntensity = 0.45,
        blurIntensity = 0.15,
        glowIntensity = 0.30,
        wordHighlightEnabled = true,
        slowMotionEnabled = false,
        beatFlashEnabled = true,
        backgroundKey = "minimal_dark"
    )
)

val STARTER_TEMPLATES = listOf(
    StarterTemplate(
        id = "neon_night",
        name = "Neon Night",
        categoryTag = "CLUB • SYNTH",
        description = "Electric cyan & magenta rim glow, bold Bebas typography, and beat-flash cuts.",
        sampleLyric = "CITY LIGHTS NEVER SLEEP",
        filterStyle = VideoFilterStyle.NEON_GRADIENT,
        preset = LyricStylePreset(
            name = "Neon Night",
            subtitle = "Electric neon • Bebas • beat flash",
            fontStyle = "Bebas Neue",
            fontSizeSp = 39.0,
            textColorHex = "#FFFFFF",
            highlightColorHex = "#00F5D4",
            textPosition = LyricTextPosition.CENTER,
            textAnimation = LyricAnimation.WORD_POP,
            textShadowIntensity = 0.90,
            blurIntensity = 0.18,
            glowIntensity = 0.92,
            wordHighlightEnabled = true,
            slowMotionEnabled = false,
            beatFlashEnabled = true,
            backgroundKey = "concert_neon"
        )
    ),
    StarterTemplate(
        id = "vintage_film",
        name = "Vintage Film",
        categoryTag = "35MM • RETRO",
        description = "Warm sepia film grade, subtle 35mm grain, editorial serif, and slow-motion fade.",
        sampleLyric = "Memories on faded reel",
        filterStyle = VideoFilterStyle.VINTAGE_SEPIA,
        preset = LyricStylePreset(
            name = "Vintage Film",
            subtitle = "35mm sepia • grain • warm serif",
            fontStyle = "Cormorant Garamond",
            fontSizeSp = 31.0,
            textColorHex = "#FEF3C7",
            highlightColorHex = "#F59E0B",
            textPosition = LyricTextPosition.BOTTOM,
            textAnimation = LyricAnimation.FADE_IN,
            textShadowIntensity = 0.80,
            blurIntensity = 0.28,
            glowIntensity = 0.35,
            wordHighlightEnabled = true,
            slowMotionEnabled = true,
            beatFlashEnabled = false,
            backgroundKey = "sunset_aesthetic"
        )
    ),
    StarterTemplate(
        id = "cyberpunk_pulse",
        name = "Cyberpunk Pulse",
        categoryTag = "ENERGETIC • BPM",
        description = "High-voltage magenta accents, zoom-in impact lyrics, and rapid beat transitions.",
        sampleLyric = "OVERDRIVE IN THE DARK",
        filterStyle = VideoFilterStyle.NEON_GRADIENT,
        preset = LyricStylePreset(
            name = "Cyberpunk Pulse",
            subtitle = "Syne display • magenta • zoom pop",
            fontStyle = "Syne",
            fontSizeSp = 30.0,
            textColorHex = "#F8FAFC",
            highlightColorHex = "#EC4899",
            textPosition = LyricTextPosition.CENTER,
            textAnimation = LyricAnimation.ZOOM_IN,
            textShadowIntensity = 0.95,
            blurIntensity = 0.15,
            glowIntensity = 0.88,
            wordHighlightEnabled = true,
            slowMotionEnabled = false,
            beatFlashEnabled = true,
            backgroundKey = "concert_neon"
        )
    ),
    StarterTemplate(
        id = "golden_acoustic",
        name = "Golden Acoustic",
        categoryTag = "ROMANTIC • INDIE",
        description = "Warm twilight haze, smooth rising lyrics, and soft amber word highlighting.",
        sampleLyric = "Hold this moment softly",
        filterStyle = VideoFilterStyle.VINTAGE_SEPIA,
        preset = LyricStylePreset(
            name = "Golden Acoustic",
            subtitle = "Twilight amber • smooth slide",
            fontStyle = "Cormorant Garamond",
            fontSizeSp = 29.0,
            textColorHex = "#FFFBEB",
            highlightColorHex = "#FB7185",
            textPosition = LyricTextPosition.CENTER,
            textAnimation = LyricAnimation.SMOOTH_SLIDE,
            textShadowIntensity = 0.65,
            blurIntensity = 0.32,
            glowIntensity = 0.50,
            wordHighlightEnabled = true,
            slowMotionEnabled = true,
            beatFlashEnabled = false,
            backgroundKey = "sunset_aesthetic"
        )
    ),
    StarterTemplate(
        id = "monochrome_noir",
        name = "Monochrome Noir",
        categoryTag = "B&W • CINEMA",
        description = "High-contrast black & white film look with crisp geometric typography and blur reveal.",
        sampleLyric = "SILENCE AFTER MIDNIGHT",
        filterStyle = VideoFilterStyle.MONOCHROME_NOIR,
        preset = LyricStylePreset(
            name = "Monochrome Noir",
            subtitle = "B&W cinema • crisp blur reveal",
            fontStyle = "Space Grotesk",
            fontSizeSp = 28.0,
            textColorHex = "#94A3B8",
            highlightColorHex = "#FFFFFF",
            textPosition = LyricTextPosition.CENTER,
            textAnimation = LyricAnimation.BLUR_REVEAL,
            textShadowIntensity = 0.90,
            blurIntensity = 0.40,
            glowIntensity = 0.45,
            wordHighlightEnabled = true,
            slowMotionEnabled = true,
            beatFlashEnabled = false,
            backgroundKey = "minimal_dark"
        )
    ),
    StarterTemplate(
        id = "dreamy_lofi",
        name = "Dreamy Lo-Fi",
        categoryTag = "CHILL • LATE NIGHT",
        description = "Soft indigo rain atmosphere, dreamy violet haze, and relaxed lyric pacing.",
        sampleLyric = "drifting through the rain",
        filterStyle = VideoFilterStyle.DREAMY_HAZE,
        preset = LyricStylePreset(
            name = "Dreamy Lo-Fi",
            subtitle = "Indigo haze • soft violet glow",
            fontStyle = "Plus Jakarta Sans",
            fontSizeSp = 25.0,
            textColorHex = "#E2E8F0",
            highlightColorHex = "#A78BFA",
            textPosition = LyricTextPosition.BOTTOM,
            textAnimation = LyricAnimation.FADE_IN,
            textShadowIntensity = 0.70,
            blurIntensity = 0.52,
            glowIntensity = 0.60,
            wordHighlightEnabled = true,
            slowMotionEnabled = true,
            beatFlashEnabled = false,
            backgroundKey = "cinematic_rain"
        )
    )
)

val ALL_STYLE_PRESETS: List<LyricStylePreset> =
    STARTER_TEMPLATES.map { it.preset } + BUILTIN_STYLE_PRESETS

fun visualFilterForPresetName(presetName: String): VideoFilterStyle {
    return STARTER_TEMPLATES.firstOrNull {
        it.name.equals(presetName, ignoreCase = true)
    }?.filterStyle ?: when {
        presetName.contains("Concert", ignoreCase = true) -> VideoFilterStyle.NEON_GRADIENT
        presetName.contains("Sad", ignoreCase = true) -> VideoFilterStyle.DREAMY_HAZE
        else -> VideoFilterStyle.NONE
    }
}

@Keep
@IgnoreExtraProperties
data class LyricProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "Untitled Edit",
    val audioDisplayName: String = "",
    val localAudioPath: String = "",
    val localBackgroundPath: String = "",
    val backgroundPresetKey: String = "cinematic_rain",
    val durationMs: Long = 0L,
    val bpm: Long = 100L,
    val detectedMood: String = "chill",
    val presetName: String = "Cinematic Sad",
    val fontStyle: String = "Cormorant Garamond",
    val fontSizeSp: Double = 30.0,
    val textColorHex: String = "#FFFFFF",
    val highlightColorHex: String = "#00F5D4",
    val textPosition: String = "CENTER",
    val textAnimation: String = "BLUR_REVEAL",
    val textShadowIntensity: Double = 0.75,
    val blurIntensity: Double = 0.35,
    val glowIntensity: Double = 0.50,
    val wordHighlightEnabled: Boolean = true,
    val syncOffsetMs: Long = 0L,
    val slowMotionEnabled: Boolean = true,
    val beatFlashEnabled: Boolean = true,
    val lyricLines: List<LyricLine> = emptyList(),
    val sceneMarkers: List<SceneMarker> = emptyList(),
    val waveformBars: List<Double> = emptyList(),
    val lastExportedPath: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun applyPreset(preset: LyricStylePreset): LyricProject = copy(
        presetName = preset.name,
        fontStyle = preset.fontStyle,
        fontSizeSp = preset.fontSizeSp,
        textColorHex = preset.textColorHex,
        highlightColorHex = preset.highlightColorHex,
        textPosition = preset.textPosition.name,
        textAnimation = preset.textAnimation.name,
        textShadowIntensity = preset.textShadowIntensity,
        blurIntensity = preset.blurIntensity,
        glowIntensity = preset.glowIntensity,
        wordHighlightEnabled = preset.wordHighlightEnabled,
        slowMotionEnabled = preset.slowMotionEnabled,
        beatFlashEnabled = preset.beatFlashEnabled,
        backgroundPresetKey = preset.backgroundKey
    )

    fun toCreateMap(ownerUid: String): Map<String, Any> = mapOf(
        "userId" to ownerUid,
        "title" to title.trim().ifBlank { "Untitled Edit" }.take(120),
        "audioDisplayName" to audioDisplayName.take(200),
        "localAudioPath" to localAudioPath.take(600),
        "localBackgroundPath" to localBackgroundPath.take(600),
        "backgroundPresetKey" to BackgroundPreset.fromKey(backgroundPresetKey).key,
        "durationMs" to durationMs.coerceIn(0L, 3_600_000L),
        "bpm" to bpm.coerceIn(0L, 300L),
        "detectedMood" to detectedMood.ifBlank { "chill" }.take(40),
        "presetName" to presetName.ifBlank { "Cinematic Sad" }.take(50),
        "fontStyle" to fontStyle.ifBlank { "Space Grotesk" }.take(50),
        "fontSizeSp" to fontSizeSp.coerceIn(12.0, 64.0),
        "textColorHex" to textColorHex.ifBlank { "#FFFFFF" }.take(16),
        "highlightColorHex" to highlightColorHex.ifBlank { "#00F5D4" }.take(16),
        "textPosition" to LyricTextPosition.fromRaw(textPosition).name,
        "textAnimation" to LyricAnimation.fromRaw(textAnimation).name,
        "textShadowIntensity" to textShadowIntensity.coerceIn(0.0, 1.0),
        "blurIntensity" to blurIntensity.coerceIn(0.0, 1.0),
        "glowIntensity" to glowIntensity.coerceIn(0.0, 1.0),
        "wordHighlightEnabled" to wordHighlightEnabled,
        "syncOffsetMs" to syncOffsetMs.coerceIn(-5000L, 5000L),
        "slowMotionEnabled" to slowMotionEnabled,
        "beatFlashEnabled" to beatFlashEnabled,
        "lyricLines" to lyricLines.take(300).map { it.toMap() },
        "sceneMarkers" to sceneMarkers.take(120).map { it.toMap() },
        "waveformBars" to waveformBars.take(120),
        "lastExportedPath" to lastExportedPath.take(600),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "title" to title.trim().ifBlank { "Untitled Edit" }.take(120),
        "audioDisplayName" to audioDisplayName.take(200),
        "localAudioPath" to localAudioPath.take(600),
        "localBackgroundPath" to localBackgroundPath.take(600),
        "backgroundPresetKey" to BackgroundPreset.fromKey(backgroundPresetKey).key,
        "durationMs" to durationMs.coerceIn(0L, 3_600_000L),
        "bpm" to bpm.coerceIn(0L, 300L),
        "detectedMood" to detectedMood.ifBlank { "chill" }.take(40),
        "presetName" to presetName.ifBlank { "Cinematic Sad" }.take(50),
        "fontStyle" to fontStyle.ifBlank { "Space Grotesk" }.take(50),
        "fontSizeSp" to fontSizeSp.coerceIn(12.0, 64.0),
        "textColorHex" to textColorHex.ifBlank { "#FFFFFF" }.take(16),
        "highlightColorHex" to highlightColorHex.ifBlank { "#00F5D4" }.take(16),
        "textPosition" to LyricTextPosition.fromRaw(textPosition).name,
        "textAnimation" to LyricAnimation.fromRaw(textAnimation).name,
        "textShadowIntensity" to textShadowIntensity.coerceIn(0.0, 1.0),
        "blurIntensity" to blurIntensity.coerceIn(0.0, 1.0),
        "glowIntensity" to glowIntensity.coerceIn(0.0, 1.0),
        "wordHighlightEnabled" to wordHighlightEnabled,
        "syncOffsetMs" to syncOffsetMs.coerceIn(-5000L, 5000L),
        "slowMotionEnabled" to slowMotionEnabled,
        "beatFlashEnabled" to beatFlashEnabled,
        "lyricLines" to lyricLines.take(300).map { it.toMap() },
        "sceneMarkers" to sceneMarkers.take(120).map { it.toMap() },
        "waveformBars" to waveformBars.take(120),
        "lastExportedPath" to lastExportedPath.take(600),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

enum class ExportAspectRatio(val label: String, val ratioW: Int, val ratioH: Int) {
    PORTRAIT_9_16("9:16", 9, 16),
    LANDSCAPE_16_9("16:9", 16, 9),
    SQUARE_1_1("1:1", 1, 1);

    fun dimensionsFor(resolution: ExportResolution): Pair<Int, Int> {
        val shortEdge = when (resolution) {
            ExportResolution.HD_720P -> 720
            ExportResolution.FHD_1080P -> 1080
            ExportResolution.UHD_4K -> 2160
        }
        return when (this) {
            PORTRAIT_9_16 -> shortEdge to ((shortEdge * 16) / 9).let { it - (it % 2) }
            LANDSCAPE_16_9 -> ((shortEdge * 16) / 9).let { it - (it % 2) } to shortEdge
            SQUARE_1_1 -> shortEdge to shortEdge
        }
    }
}

enum class ExportResolution(val label: String, val baseBitrateBps: Int) {
    HD_720P("720p", 4_500_000),
    FHD_1080P("1080p", 9_000_000),
    UHD_4K("4K", 22_000_000)
}

enum class ExportFps(val fps: Int, val label: String) {
    FPS_30(30, "30 FPS"),
    FPS_60(60, "60 FPS")
}

fun parseHexColor(hex: String, fallback: Color = Color.White): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

fun parseHexInt(hex: String, fallback: Int = android.graphics.Color.WHITE): Int {
    return try {
        android.graphics.Color.parseColor(hex)
    } catch (_: Exception) {
        fallback
    }
}

fun formatMillis(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    val tenths = (ms.coerceAtLeast(0L) % 1000L) / 100L
    return "%d:%02d.%d".format(min, sec, tenths)
}

fun formatMillisShort(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0L) / 1000L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%d:%02d".format(min, sec)
}
