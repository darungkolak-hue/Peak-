package com.example.ai

import android.content.Context
import android.util.Log
import com.example.media.AudioAnalyzer
import com.example.model.BackgroundPreset
import com.example.model.BUILTIN_STYLE_PRESETS
import com.example.model.LyricLine
import com.example.model.LyricProject
import com.example.model.SceneMarker
import com.example.model.WordTiming
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

private const val TAG = "AiLyricEngine"

data class AiTranscriptionOutput(
    val title: String,
    val detectedMood: String,
    val estimatedBpm: Int,
    val recommendedPreset: String,
    val recommendedBackground: String,
    val lines: List<LyricLine>,
    val sceneMarkers: List<SceneMarker>
)

data class AiAutoEditOutput(
    val detectedMood: String,
    val recommendedPreset: String,
    val recommendedBackground: String,
    val textPosition: String,
    val textAnimation: String,
    val textColorHex: String,
    val highlightColorHex: String,
    val blurIntensity: Double,
    val glowIntensity: Double,
    val slowMotionEnabled: Boolean,
    val beatFlashEnabled: Boolean,
    val sceneMarkers: List<SceneMarker>
)

class AiLyricEngine {

    private val transcriptionSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Song title inferred from vocals or filename"),
            "detectedMood" to Schema.enumeration(
                values = listOf("sad", "happy", "energetic", "romantic", "dark", "chill"),
                description = "Primary emotional mood of the song"
            ),
            "estimatedBpm" to Schema.integer(description = "Estimated tempo in BPM (60 to 180)"),
            "recommendedPreset" to Schema.enumeration(
                values = listOf(
                    "Cinematic Sad",
                    "Aesthetic",
                    "Concert Vibes",
                    "Dark Emotional",
                    "Minimalist",
                    "Spotify Lyrics Style"
                ),
                description = "Best matching visual lyric preset"
            ),
            "recommendedBackground" to Schema.enumeration(
                values = listOf("cinematic_rain", "concert_neon", "sunset_aesthetic", "minimal_dark"),
                description = "Best matching cinematic background key"
            ),
            "lines" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "startMs" to Schema.integer(description = "Line start timestamp in milliseconds"),
                        "endMs" to Schema.integer(description = "Line end timestamp in milliseconds"),
                        "sectionLabel" to Schema.string(description = "Song section e.g. Intro, Verse 1, Chorus, Bridge, Pause, Outro"),
                        "text" to Schema.string(description = "Full sung lyric line"),
                        "words" to Schema.array(
                            items = Schema.obj(
                                properties = mapOf(
                                    "word" to Schema.string(description = "Single sung word"),
                                    "startMs" to Schema.integer(description = "Exact word start time in ms"),
                                    "endMs" to Schema.integer(description = "Exact word end time in ms")
                                )
                            ),
                            description = "Word-level timestamps synchronized to the vocals"
                        )
                    )
                ),
                description = "Chronological list of transcribed lyric lines with word-level timestamps"
            ),
            "sceneCuts" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "timeMs" to Schema.integer(description = "Transition timestamp in ms"),
                        "transitionType" to Schema.enumeration(
                            values = listOf("FADE", "ZOOM", "BEAT_FLASH", "BLUR"),
                            description = "Visual transition effect"
                        ),
                        "isSlowMotion" to Schema.boolean(description = "Whether this scene uses cinematic slow motion"),
                        "backgroundKey" to Schema.enumeration(
                            values = listOf("cinematic_rain", "concert_neon", "sunset_aesthetic", "minimal_dark")
                        )
                    )
                ),
                description = "Beat-synced background transitions and slow-motion moments"
            )
        )
    )

    private val autoEditSchema = Schema.obj(
        properties = mapOf(
            "detectedMood" to Schema.enumeration(
                values = listOf("sad", "happy", "energetic", "romantic", "dark", "chill")
            ),
            "recommendedPreset" to Schema.enumeration(
                values = listOf(
                    "Cinematic Sad",
                    "Aesthetic",
                    "Concert Vibes",
                    "Dark Emotional",
                    "Minimalist",
                    "Spotify Lyrics Style"
                )
            ),
            "recommendedBackground" to Schema.enumeration(
                values = listOf("cinematic_rain", "concert_neon", "sunset_aesthetic", "minimal_dark")
            ),
            "textPosition" to Schema.enumeration(values = listOf("TOP", "CENTER", "BOTTOM")),
            "textAnimation" to Schema.enumeration(
                values = listOf("FADE_IN", "ZOOM_IN", "BLUR_REVEAL", "SMOOTH_SLIDE", "WORD_POP")
            ),
            "textColorHex" to Schema.string(description = "Hex color like #FFFFFF"),
            "highlightColorHex" to Schema.string(description = "Hex highlight color like #00F5D4"),
            "blurIntensity" to Schema.double(description = "0.0 to 1.0"),
            "glowIntensity" to Schema.double(description = "0.0 to 1.0"),
            "slowMotionEnabled" to Schema.boolean(),
            "beatFlashEnabled" to Schema.boolean(),
            "sceneCuts" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "timeMs" to Schema.integer(),
                        "transitionType" to Schema.enumeration(values = listOf("FADE", "ZOOM", "BEAT_FLASH", "BLUR")),
                        "isSlowMotion" to Schema.boolean(),
                        "backgroundKey" to Schema.enumeration(
                            values = listOf("cinematic_rain", "concert_neon", "sunset_aesthetic", "minimal_dark")
                        )
                    )
                )
            )
        )
    )

    private val transcriptionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = transcriptionSchema
                temperature = 0.2f
            }
        )
    }

    private val autoEditModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = autoEditSchema
                temperature = 0.35f
            }
        )
    }

    suspend fun transcribeAndAnalyzeAudio(
        context: Context,
        audioFile: File,
        mimeType: String,
        displayName: String,
        durationMs: Long,
        estimatedBpm: Int,
        beatPeaksMs: List<Long>
    ): AiTranscriptionOutput {
        try {
            val (audioBytes, aiMime) = AudioAnalyzer.extractCompactAudioPayload(context, audioFile, mimeType)
            val peaksText = beatPeaksMs.take(24).joinToString(", ")
            val prompt = """
                Listen carefully to this audio track (filename: "$displayName", total duration: ${durationMs}ms, acoustic beat estimate: ${estimatedBpm} BPM, energy peaks at ms: [$peaksText]).
                1. Detect the singing vocals and transcribe every sung line accurately in the original language of the vocals.
                2. Provide precise millisecond startMs and endMs for each lyric line AND word-level timestamps (startMs, endMs) for EVERY word in each line so the lyrics highlight in real time as each word is sung.
                3. Identify pauses, verse/chorus/bridge transitions, and section labels.
                4. If there is an instrumental intro/outro or no vocals at a timestamp, leave silent gaps accurately rather than inventing words. If the audio is purely instrumental with zero vocals, return an empty lines array and still provide mood, BPM, preset, and beat-synced sceneCuts.
                5. Recommend the best visual preset, mood, and 4 to 10 beat-synced sceneCuts (including slow-motion on emotional/chorus peaks).
            """.trimIndent()

            val response = withTimeout(60_000L) {
                transcriptionModel.generateContent(
                    content {
                        inlineData(audioBytes, aiMime)
                        text(prompt)
                    }
                )
            }

            val rawJson = response.text ?: throw IllegalStateException("AI returned empty response.")
            return parseTranscriptionJson(rawJson, displayName, durationMs, estimatedBpm)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Audio transcription timed out", e)
            throw IllegalStateException("AI lyric analysis timed out after 60 seconds. Try a shorter clip or retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Audio transcription failed", e)
            throw e
        }
    }

    suspend fun autoEditStyle(project: LyricProject): AiAutoEditOutput {
        try {
            val lyricsSample = project.lyricLines.take(25).joinToString("\n") {
                "[${it.sectionLabel} ${it.startMs}ms] ${it.text}"
            }
            val prompt = """
                You are LYRIX AI, a cinematic music video director for TikTok/Reels lyric edits.
                Analyze this project and generate the ideal automatic visual direction:
                - Title: "${project.title}"
                - Duration: ${project.durationMs}ms
                - Tempo: ${project.bpm} BPM
                - Current mood: ${project.detectedMood}
                - Lyrics excerpt:
                $lyricsSample

                Choose the best mood, preset, background key, text position, animation, textColorHex, highlightColorHex, blurIntensity (0..1), glowIntensity (0..1), slowMotionEnabled, beatFlashEnabled, and 4-8 sceneCuts across 0..${project.durationMs}ms.
            """.trimIndent()

            val response = withTimeout(45_000L) {
                autoEditModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("AI returned empty auto-edit response.")
            val json = JSONObject(rawJson)

            val cutsArray = json.optJSONArray("sceneCuts")
            val cuts = mutableListOf<SceneMarker>()
            if (cutsArray != null) {
                for (i in 0 until cutsArray.length()) {
                    val obj = cutsArray.getJSONObject(i)
                    cuts.add(
                        SceneMarker(
                            timeMs = obj.optLong("timeMs", 0L).coerceIn(0L, project.durationMs.coerceAtLeast(1000L)),
                            transitionType = obj.optString("transitionType", "FADE"),
                            isSlowMotion = obj.optBoolean("isSlowMotion", false),
                            backgroundKey = BackgroundPreset.fromKey(obj.optString("backgroundKey", "cinematic_rain")).key
                        )
                    )
                }
            }

            return AiAutoEditOutput(
                detectedMood = json.optString("detectedMood", project.detectedMood),
                recommendedPreset = json.optString("recommendedPreset", project.presetName),
                recommendedBackground = BackgroundPreset.fromKey(
                    json.optString("recommendedBackground", project.backgroundPresetKey)
                ).key,
                textPosition = json.optString("textPosition", project.textPosition),
                textAnimation = json.optString("textAnimation", project.textAnimation),
                textColorHex = json.optString("textColorHex", "#FFFFFF"),
                highlightColorHex = json.optString("highlightColorHex", "#00F5D4"),
                blurIntensity = json.optDouble("blurIntensity", 0.35).coerceIn(0.0, 1.0),
                glowIntensity = json.optDouble("glowIntensity", 0.55).coerceIn(0.0, 1.0),
                slowMotionEnabled = json.optBoolean("slowMotionEnabled", true),
                beatFlashEnabled = json.optBoolean("beatFlashEnabled", true),
                sceneMarkers = cuts.sortedBy { it.timeMs }.ifEmpty { project.sceneMarkers }
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Auto edit timed out", e)
            throw IllegalStateException("AI Auto-Edit timed out. Please try again.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Auto edit failed", e)
            throw e
        }
    }

    private fun parseTranscriptionJson(
        rawJson: String,
        fallbackTitle: String,
        durationMs: Long,
        fallbackBpm: Int
    ): AiTranscriptionOutput {
        val json = JSONObject(rawJson)
        val cleanFallbackTitle = fallbackTitle.substringBeforeLast('.').ifBlank { "Untitled Track" }
        val title = json.optString("title", cleanFallbackTitle).ifBlank { cleanFallbackTitle }
        val mood = json.optString("detectedMood", "chill")
        val bpm = json.optInt("estimatedBpm", fallbackBpm).coerceIn(50, 220)
        val presetName = json.optString("recommendedPreset", BUILTIN_STYLE_PRESETS.first().name)
        val bgKey = BackgroundPreset.fromKey(json.optString("recommendedBackground", "cinematic_rain")).key

        val linesJson = json.optJSONArray("lines")
        val parsedLines = mutableListOf<LyricLine>()
        if (linesJson != null) {
            for (i in 0 until linesJson.length()) {
                val lineObj = linesJson.getJSONObject(i)
                val lineText = lineObj.optString("text", "").trim()
                if (lineText.isEmpty()) continue
                val startMs = lineObj.optLong("startMs", 0L).coerceAtLeast(0L)
                val endMs = lineObj.optLong("endMs", startMs + 2500L).coerceAtLeast(startMs + 250L)
                val section = lineObj.optString("sectionLabel", "Verse").ifBlank { "Verse" }

                val wordsJson = lineObj.optJSONArray("words")
                val words = mutableListOf<WordTiming>()
                if (wordsJson != null) {
                    for (w in 0 until wordsJson.length()) {
                        val wObj = wordsJson.getJSONObject(w)
                        val wordText = wObj.optString("word", "").trim()
                        if (wordText.isNotEmpty()) {
                            val wStart = wObj.optLong("startMs", startMs).coerceIn(startMs, endMs)
                            val wEnd = wObj.optLong("endMs", wStart + 350L).coerceIn(wStart + 50L, endMs + 200L)
                            words.add(WordTiming(wordText, wStart, wEnd))
                        }
                    }
                }
                if (words.isEmpty()) {
                    words.addAll(distributeWordTimings(lineText, startMs, endMs))
                }
                parsedLines.add(
                    LyricLine(
                        startMs = startMs,
                        endMs = endMs,
                        sectionLabel = section,
                        text = lineText,
                        words = words
                    )
                )
            }
        }

        val cutsJson = json.optJSONArray("sceneCuts")
        val parsedCuts = mutableListOf<SceneMarker>()
        if (cutsJson != null) {
            for (i in 0 until cutsJson.length()) {
                val cObj = cutsJson.getJSONObject(i)
                parsedCuts.add(
                    SceneMarker(
                        timeMs = cObj.optLong("timeMs", 0L).coerceIn(0L, durationMs.coerceAtLeast(1000L)),
                        transitionType = cObj.optString("transitionType", "FADE"),
                        isSlowMotion = cObj.optBoolean("isSlowMotion", false),
                        backgroundKey = BackgroundPreset.fromKey(cObj.optString("backgroundKey", bgKey)).key
                    )
                )
            }
        }
        if (parsedCuts.isEmpty()) {
            parsedCuts.add(SceneMarker(0L, "FADE", false, bgKey))
        }

        return AiTranscriptionOutput(
            title = title,
            detectedMood = mood,
            estimatedBpm = bpm,
            recommendedPreset = presetName,
            recommendedBackground = bgKey,
            lines = parsedLines.sortedBy { it.startMs },
            sceneMarkers = parsedCuts.sortedBy { it.timeMs }
        )
    }
}

fun distributeWordTimings(text: String, startMs: Long, endMs: Long): List<WordTiming> {
    val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    val totalDur = (endMs - startMs).coerceAtLeast(tokens.size * 120L)
    val totalChars = tokens.sumOf { it.length.coerceAtLeast(1) }.toDouble()
    var cursor = startMs
    return tokens.mapIndexed { index, token ->
        val weight = token.length.coerceAtLeast(1) / totalChars
        val dur = if (index == tokens.lastIndex) (endMs - cursor).coerceAtLeast(120L) else (totalDur * weight).toLong().coerceAtLeast(100L)
        val wordStart = cursor
        val wordEnd = (cursor + dur).coerceAtMost(endMs.coerceAtLeast(wordStart + 100L))
        cursor = wordEnd
        WordTiming(token, wordStart, wordEnd)
    }
}
