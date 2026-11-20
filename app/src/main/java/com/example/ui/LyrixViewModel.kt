package com.example.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiLyricEngine
import com.example.ai.distributeWordTimings
import com.example.data.AppSettings
import com.example.data.ProjectRepository
import com.example.data.SettingsRepository
import com.example.media.AudioAnalyzer
import com.example.media.ExportResult
import com.example.media.VideoExporter
import com.example.model.ALL_STYLE_PRESETS
import com.example.model.BUILTIN_STYLE_PRESETS
import com.example.model.BackgroundPreset
import com.example.model.ExportAspectRatio
import com.example.model.ExportFps
import com.example.model.ExportResolution
import com.example.model.LyricLine
import com.example.model.LyricProject
import com.example.model.SceneMarker
import com.example.model.StarterTemplate
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "LyrixVM"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiPipelineState {
    data object Idle : AiPipelineState
    data class Processing(val stepLabel: String) : AiPipelineState
    data class Error(val message: String) : AiPipelineState
}

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val progress: Float) : ExportUiState
    data class Success(val result: ExportResult) : ExportUiState
    data class Error(val message: String) : ExportUiState
}

class LyrixViewModel(
    private val projectRepository: ProjectRepository,
    private val settingsRepository: SettingsRepository,
    private val aiEngine: AiLyricEngine,
    private val currentUserId: String
) : ViewModel() {

    val projectsState: StateFlow<UiState<List<LyricProject>>> =
        projectRepository.observeProjects(currentUserId)
            .map<List<LyricProject>, UiState<List<LyricProject>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing projects", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load projects."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val appSettings: StateFlow<AppSettings> = settingsRepository.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppSettings()
        )

    private val _activeProject = MutableStateFlow<LyricProject?>(null)
    val activeProject: StateFlow<LyricProject?> = _activeProject.asStateFlow()

    private val _undoStack = MutableStateFlow<List<LyricProject>>(emptyList())
    private val _redoStack = MutableStateFlow<List<LyricProject>>(emptyList())
    val canUndo: StateFlow<Boolean> = _undoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)
    val canRedo: StateFlow<Boolean> = _redoStack.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    private val _aiState = MutableStateFlow<AiPipelineState>(AiPipelineState.Idle)
    val aiState: StateFlow<AiPipelineState> = _aiState.asStateFlow()

    private val _exportState = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackMs = MutableStateFlow(0L)
    val playbackMs: StateFlow<Long> = _playbackMs.asStateFlow()

    private val _previewAspectRatio = MutableStateFlow(ExportAspectRatio.PORTRAIT_9_16)
    val previewAspectRatio: StateFlow<ExportAspectRatio> = _previewAspectRatio.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var tickerJob: Job? = null
    private var exportJob: Job? = null

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun clearAiError() {
        if (_aiState.value is AiPipelineState.Error) {
            _aiState.value = AiPipelineState.Idle
        }
    }

    fun resetExportState() {
        _exportState.value = ExportUiState.Idle
    }

    fun setPreviewAspectRatio(ratio: ExportAspectRatio) {
        _previewAspectRatio.value = ratio
    }

    fun openProject(project: LyricProject) {
        stopPlayback()
        _undoStack.value = emptyList()
        _redoStack.value = emptyList()
        _activeProject.value = project
        _playbackMs.value = 0L
        _exportState.value = ExportUiState.Idle
        prepareMediaPlayer(project.localAudioPath)
    }

    fun importMediaAndCreateProject(
        context: Context,
        uri: Uri,
        starterTemplate: StarterTemplate? = null,
        onProjectReady: (LyricProject) -> Unit
    ) {
        viewModelScope.launch {
            _aiState.value = AiPipelineState.Processing("Extracting audio waveform & detecting BPM...")
            try {
                val analysis = AudioAnalyzer.importAndAnalyzeMedia(context, uri)
                _aiState.value = AiPipelineState.Processing("AI detecting vocals & syncing word timestamps...")
                val aiResult = aiEngine.transcribeAndAnalyzeAudio(
                    context = context,
                    audioFile = analysis.localFile,
                    mimeType = analysis.mimeType,
                    displayName = analysis.displayName,
                    durationMs = analysis.durationMs,
                    estimatedBpm = analysis.estimatedBpm,
                    beatPeaksMs = analysis.beatPeaksMs
                )

                val chosenPreset = starterTemplate?.preset ?: (
                    ALL_STYLE_PRESETS.firstOrNull {
                        it.name.equals(aiResult.recommendedPreset, ignoreCase = true)
                    } ?: BUILTIN_STYLE_PRESETS.first()
                )

                val baseProject = LyricProject(
                    userId = currentUserId,
                    title = aiResult.title,
                    audioDisplayName = analysis.displayName,
                    localAudioPath = analysis.localFile.absolutePath,
                    localBackgroundPath = if (analysis.isVideoSource) {
                        extractVideoPoster(context, analysis.localFile) ?: ""
                    } else "",
                    backgroundPresetKey = chosenPreset.backgroundKey,
                    durationMs = analysis.durationMs,
                    bpm = aiResult.estimatedBpm.toLong(),
                    detectedMood = aiResult.detectedMood,
                    lyricLines = aiResult.lines,
                    sceneMarkers = aiResult.sceneMarkers,
                    waveformBars = analysis.waveformBars
                ).applyPreset(chosenPreset)

                _aiState.value = AiPipelineState.Processing("Saving project to LYRIX Cloud...")
                val createResult = projectRepository.createProject(currentUserId, baseProject)
                val newId = createResult.getOrElse { err ->
                    throw IllegalStateException(err.localizedMessage ?: "Failed to save project.")
                }

                val savedProject = baseProject.copy(id = newId)
                _aiState.value = AiPipelineState.Idle
                openProject(savedProject)
                onProjectReady(savedProject)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Import & AI transcription failed", e)
                _aiState.value = AiPipelineState.Error(
                    e.localizedMessage ?: "AI lyric analysis failed. Please check connection and retry."
                )
            }
        }
    }

    fun applyStarterTemplateAndOpen(
        project: LyricProject,
        template: StarterTemplate,
        onProjectReady: (LyricProject) -> Unit
    ) {
        viewModelScope.launch {
            val updated = project.applyPreset(template.preset).copy(localBackgroundPath = "")
            if (updated.id.isNotBlank()) {
                projectRepository.updateProject(currentUserId, updated)
            }
            openProject(updated)
            _statusBanner.value = "Applied starter template: ${template.name}"
            onProjectReady(updated)
        }
    }

    fun relinkAudioFile(context: Context, uri: Uri) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            _aiState.value = AiPipelineState.Processing("Relinking local audio track...")
            try {
                val analysis = AudioAnalyzer.importAndAnalyzeMedia(context, uri)
                val updated = current.copy(
                    audioDisplayName = analysis.displayName,
                    localAudioPath = analysis.localFile.absolutePath,
                    durationMs = analysis.durationMs,
                    waveformBars = analysis.waveformBars
                )
                commitProjectChange(updated)
                prepareMediaPlayer(updated.localAudioPath)
                _aiState.value = AiPipelineState.Idle
                _statusBanner.value = "Audio re-linked: ${analysis.displayName}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiPipelineState.Error(e.localizedMessage ?: "Failed to relink audio.")
            }
        }
    }

    fun setCustomBackgroundImage(context: Context, uri: Uri) {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            try {
                val destPath = withContext(Dispatchers.IO) {
                    val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val isVideo = mime.startsWith("video/")
                    if (isVideo) {
                        val tempVideo = File(context.cacheDir, "bg_vid_${System.currentTimeMillis()}.mp4")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempVideo).use { out -> input.copyTo(out) }
                        }
                        val poster = extractVideoPoster(context, tempVideo)
                        tempVideo.delete()
                        poster ?: throw IllegalStateException("Could not extract frame from video.")
                    } else {
                        val destFile = File(mediaDir, "bg_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(destFile).use { out -> input.copyTo(out) }
                        }
                        destFile.absolutePath
                    }
                }
                commitProjectChange(current.copy(localBackgroundPath = destPath))
                _statusBanner.value = "Custom visual background applied"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusBanner.value = e.localizedMessage ?: "Failed to load background media"
            }
        }
    }

    fun selectPresetBackground(preset: BackgroundPreset) {
        val current = _activeProject.value ?: return
        commitProjectChange(
            current.copy(
                backgroundPresetKey = preset.key,
                localBackgroundPath = ""
            )
        )
    }

    fun applyStylePreset(presetName: String) {
        val current = _activeProject.value ?: return
        val preset = ALL_STYLE_PRESETS.firstOrNull { it.name.equals(presetName, ignoreCase = true) } ?: return
        commitProjectChange(current.applyPreset(preset))
    }

    fun updateProjectStyle(transform: (LyricProject) -> LyricProject) {
        val current = _activeProject.value ?: return
        commitProjectChange(transform(current))
    }

    fun updateLyricLine(index: Int, newText: String, newStartMs: Long, newEndMs: Long, sectionLabel: String) {
        val current = _activeProject.value ?: return
        if (index !in current.lyricLines.indices) return
        val safeStart = newStartMs.coerceAtLeast(0L)
        val safeEnd = newEndMs.coerceAtLeast(safeStart + 250L)
        val cleanText = newText.trim()
        val updatedWords = distributeWordTimings(cleanText, safeStart, safeEnd)
        val updatedLines = current.lyricLines.toMutableList()
        updatedLines[index] = LyricLine(
            startMs = safeStart,
            endMs = safeEnd,
            sectionLabel = sectionLabel.ifBlank { "Verse" },
            text = cleanText,
            words = updatedWords
        )
        commitProjectChange(current.copy(lyricLines = updatedLines.sortedBy { it.startMs }))
    }

    fun addLyricLine(atTimeMs: Long) {
        val current = _activeProject.value ?: return
        val start = atTimeMs.coerceIn(0L, (current.durationMs - 1000L).coerceAtLeast(0L))
        val end = (start + 2600L).coerceAtMost(current.durationMs.coerceAtLeast(start + 2600L))
        val text = "New lyric line"
        val line = LyricLine(
            startMs = start,
            endMs = end,
            sectionLabel = "Verse",
            text = text,
            words = distributeWordTimings(text, start, end)
        )
        val updated = (current.lyricLines + line).sortedBy { it.startMs }
        commitProjectChange(current.copy(lyricLines = updated))
    }

    fun deleteLyricLine(index: Int) {
        val current = _activeProject.value ?: return
        if (index !in current.lyricLines.indices) return
        val updated = current.lyricLines.toMutableList().apply { removeAt(index) }
        commitProjectChange(current.copy(lyricLines = updated))
    }

    fun runAiAutoEdit() {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            _aiState.value = AiPipelineState.Processing("LYRIX AI directing mood, typography & beat cuts...")
            try {
                val style = aiEngine.autoEditStyle(current)
                val matchedPreset = BUILTIN_STYLE_PRESETS.firstOrNull {
                    it.name.equals(style.recommendedPreset, ignoreCase = true)
                }
                val next = current.copy(
                    detectedMood = style.detectedMood,
                    presetName = matchedPreset?.name ?: style.recommendedPreset,
                    fontStyle = matchedPreset?.fontStyle ?: current.fontStyle,
                    fontSizeSp = matchedPreset?.fontSizeSp ?: current.fontSizeSp,
                    backgroundPresetKey = style.recommendedBackground,
                    textPosition = style.textPosition,
                    textAnimation = style.textAnimation,
                    textColorHex = style.textColorHex,
                    highlightColorHex = style.highlightColorHex,
                    blurIntensity = style.blurIntensity,
                    glowIntensity = style.glowIntensity,
                    slowMotionEnabled = style.slowMotionEnabled,
                    beatFlashEnabled = style.beatFlashEnabled,
                    sceneMarkers = style.sceneMarkers
                )
                commitProjectChange(next)
                _aiState.value = AiPipelineState.Idle
                _statusBanner.value = "AI Auto-Edit applied (${next.presetName} • ${next.detectedMood})"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiState.value = AiPipelineState.Error(e.localizedMessage ?: "Auto-Edit failed.")
            }
        }
    }

    fun undo() {
        val current = _activeProject.value ?: return
        val stack = _undoStack.value
        if (stack.isEmpty()) return
        val prev = stack.last()
        _undoStack.value = stack.dropLast(1)
        _redoStack.value = (_redoStack.value + current).takeLast(25)
        _activeProject.value = prev
        persistActiveProject(prev)
    }

    fun redo() {
        val current = _activeProject.value ?: return
        val stack = _redoStack.value
        if (stack.isEmpty()) return
        val next = stack.last()
        _redoStack.value = stack.dropLast(1)
        _undoStack.value = (_undoStack.value + current).takeLast(25)
        _activeProject.value = next
        persistActiveProject(next)
    }

    private fun commitProjectChange(updated: LyricProject) {
        val prev = _activeProject.value
        if (prev != null && prev != updated) {
            _undoStack.value = (_undoStack.value + prev).takeLast(25)
            _redoStack.value = emptyList()
        }
        _activeProject.value = updated
        persistActiveProject(updated)
    }

    private fun persistActiveProject(project: LyricProject) {
        if (project.id.isBlank()) return
        viewModelScope.launch {
            val res = projectRepository.updateProject(currentUserId, project)
            if (res.isFailure) {
                _statusBanner.value = "Sync warning: ${res.exceptionOrNull()?.localizedMessage ?: "save error"}"
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            val res = projectRepository.deleteProject(currentUserId, projectId)
            if (res.isFailure) {
                _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Delete failed"
            } else if (_activeProject.value?.id == projectId) {
                stopPlayback()
                _activeProject.value = null
            }
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer
        if (player == null) {
            val path = _activeProject.value?.localAudioPath
            if (!path.isNullOrBlank() && File(path).exists()) {
                prepareMediaPlayer(path, autoPlay = true)
            } else {
                // Fallback virtual timer playback if audio file isn't present locally (e.g. synced from cloud)
                if (_isPlaying.value) {
                    pausePlayback()
                } else {
                    startVirtualPlayback()
                }
            }
            return
        }
        if (player.isPlaying) {
            pausePlayback()
        } else {
            player.seekTo(_playbackMs.value.toInt().coerceIn(0, player.duration.coerceAtLeast(1)))
            player.start()
            _isPlaying.value = true
            startTicker()
        }
    }

    fun seekTo(ms: Long) {
        val duration = _activeProject.value?.durationMs?.coerceAtLeast(1000L) ?: 60_000L
        val clamped = ms.coerceIn(0L, duration)
        _playbackMs.value = clamped
        try {
            mediaPlayer?.seekTo(clamped.toInt())
        } catch (_: Exception) {
        }
    }

    fun pausePlayback() {
        tickerJob?.cancel()
        _isPlaying.value = false
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {
        }
    }

    private fun prepareMediaPlayer(audioPath: String, autoPlay: Boolean = false) {
        releasePlayer()
        val file = File(audioPath)
        if (!file.exists()) return
        try {
            val mp = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _playbackMs.value = 0L
                    tickerJob?.cancel()
                }
            }
            mediaPlayer = mp
            if (autoPlay) {
                mp.start()
                _isPlaying.value = true
                startTicker()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaPlayer prepare failed", e)
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive && _isPlaying.value) {
                val pos = try {
                    mediaPlayer?.currentPosition?.toLong() ?: _playbackMs.value
                } catch (_: Exception) {
                    _playbackMs.value
                }
                _playbackMs.value = pos
                delay(16L)
            }
        }
    }

    private fun startVirtualPlayback() {
        _isPlaying.value = true
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            val dur = _activeProject.value?.durationMs?.coerceAtLeast(5000L) ?: 30_000L
            while (isActive && _isPlaying.value) {
                delay(16L)
                val next = _playbackMs.value + 16L
                if (next >= dur) {
                    _playbackMs.value = 0L
                    _isPlaying.value = false
                } else {
                    _playbackMs.value = next
                }
            }
        }
    }

    fun stopPlayback() {
        pausePlayback()
        releasePlayer()
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    fun startExportMp4(
        context: Context,
        resolution: ExportResolution,
        aspectRatio: ExportAspectRatio,
        fps: ExportFps
    ) {
        val project = _activeProject.value ?: return
        pausePlayback()
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _exportState.value = ExportUiState.Exporting(0.02f)
            try {
                val res = VideoExporter.exportProjectToMp4(
                    context = context,
                    project = project,
                    resolution = resolution,
                    aspectRatio = aspectRatio,
                    fps = fps,
                    onProgress = { p -> _exportState.value = ExportUiState.Exporting(p) }
                )
                val updated = project.copy(lastExportedPath = res.internalFile.absolutePath)
                _activeProject.value = updated
                persistActiveProject(updated)
                _exportState.value = ExportUiState.Success(res)
            } catch (e: CancellationException) {
                _exportState.value = ExportUiState.Idle
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Video export failed", e)
                _exportState.value = ExportUiState.Error(
                    e.localizedMessage ?: "Export failed. Check available storage and audio source."
                )
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        _exportState.value = ExportUiState.Idle
    }

    // Settings updates
    fun updateDefaultResolution(res: ExportResolution) {
        viewModelScope.launch { settingsRepository.setDefaultResolution(res) }
    }

    fun updateDefaultAspectRatio(ratio: ExportAspectRatio) {
        viewModelScope.launch { settingsRepository.setDefaultAspectRatio(ratio) }
    }

    fun updateDefaultFps(fps: ExportFps) {
        viewModelScope.launch { settingsRepository.setDefaultFps(fps) }
    }

    fun updateAutoEditOnUpload(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoEditOnUpload(enabled) }
    }

    fun updateHighQualityBlur(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHighQualityBlur(enabled) }
    }

    private fun extractVideoPoster(context: Context, videoFile: File): String? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(videoFile.absolutePath)
            val bmp = mmr.getFrameAtTime(1_000_000L) ?: return null
            val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
            val outFile = File(mediaDir, "poster_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
            }
            bmp.recycle()
            outFile.absolutePath
        } catch (_: Exception) {
            null
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        tickerJob?.cancel()
        exportJob?.cancel()
        releasePlayer()
    }
}
