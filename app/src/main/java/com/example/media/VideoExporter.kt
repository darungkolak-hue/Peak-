package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import com.example.model.BackgroundPreset
import com.example.model.ExportAspectRatio
import com.example.model.ExportFps
import com.example.model.ExportResolution
import com.example.model.LyricAnimation
import com.example.model.LyricLine
import com.example.model.LyricProject
import com.example.model.LyricTextPosition
import com.example.model.VideoFilterStyle
import com.example.model.parseHexInt
import com.example.model.visualFilterForPresetName
import com.example.ui.theme.fontResForName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "VideoExporter"

data class ExportResult(
    val internalFile: File,
    val galleryUri: Uri?,
    val actualWidth: Int,
    val actualHeight: Int,
    val fileSizeBytes: Long
)

object VideoExporter {

    fun estimateOutputBytes(
        durationMs: Long,
        resolution: ExportResolution,
        fps: ExportFps
    ): Long {
        val sec = (durationMs.coerceAtLeast(3000L) / 1000.0)
        val fpsMultiplier = if (fps == ExportFps.FPS_60) 1.38 else 1.0
        val videoBps = resolution.baseBitrateBps * fpsMultiplier
        val audioBps = 128_000.0
        return (((videoBps + audioBps) / 8.0) * sec).toLong()
    }

    suspend fun exportProjectToMp4(
        context: Context,
        project: LyricProject,
        resolution: ExportResolution,
        aspectRatio: ExportAspectRatio,
        fps: ExportFps,
        onProgress: (Float) -> Unit
    ): ExportResult = withContext(Dispatchers.Default) {
        val audioFile = File(project.localAudioPath)
        require(audioFile.exists()) { "Source audio file not found on device. Please re-attach audio." }

        val (reqW, reqH) = aspectRatio.dimensionsFor(resolution)
        val (width, height) = clampToSupportedAvcSize(reqW, reqH)
        val bitrate = when {
            width >= 2160 || height >= 2160 -> if (fps == ExportFps.FPS_60) 26_000_000 else 20_000_000
            width >= 1080 || height >= 1080 -> if (fps == ExportFps.FPS_60) 12_000_000 else 8_500_000
            else -> if (fps == ExportFps.FPS_60) 6_000_000 else 4_500_000
        }

        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeTitle = project.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "lyrix_edit" }
        val videoOnlyFile = File(context.cacheDir, "video_only_${System.currentTimeMillis()}.mp4")
        val finalMp4File = File(exportsDir, "${safeTitle}_${System.currentTimeMillis()}.mp4")

        val bgBitmap = loadBackgroundBitmap(context, project, width, height)
        val typeface = loadProjectTypeface(context, project.fontStyle)

        try {
            val totalDurationMs = project.durationMs.coerceAtLeast(5_000L)
            val totalFrames = ((totalDurationMs / 1000.0) * fps.fps).toInt().coerceAtLeast(1)

            // Step 1: Encode animated video frames via H.264 MediaCodec Surface
            encodeVideoFrames(
                outputFile = videoOnlyFile,
                width = width,
                height = height,
                fps = fps.fps,
                bitrate = bitrate,
                totalFrames = totalFrames,
                totalDurationMs = totalDurationMs,
                project = project,
                bgBitmap = bgBitmap,
                typeface = typeface,
                onFrameProgress = { frac -> onProgress(frac * 0.82f) }
            )

            // Step 2: Mux video + audio track (transcoding audio to AAC if needed)
            onProgress(0.85f)
            muxVideoAndAudio(
                context = context,
                videoOnlyFile = videoOnlyFile,
                audioSourceFile = audioFile,
                outputMp4 = finalMp4File,
                maxDurationUs = totalDurationMs * 1000L
            )
            onProgress(0.96f)

            // Step 3: Publish to MediaStore Movies/LYRIX_AI on Android 10+
            val galleryUri = saveToMediaStore(context, finalMp4File, "${safeTitle}_LYRIX.mp4")
            onProgress(1.0f)

            ExportResult(
                internalFile = finalMp4File,
                galleryUri = galleryUri,
                actualWidth = width,
                actualHeight = height,
                fileSizeBytes = finalMp4File.length()
            )
        } finally {
            videoOnlyFile.delete()
            bgBitmap?.recycle()
        }
    }

    private suspend fun encodeVideoFrames(
        outputFile: File,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        totalFrames: Int,
        totalDurationMs: Long,
        project: LyricProject,
        bgBitmap: Bitmap?,
        typeface: Typeface,
        onFrameProgress: (Float) -> Unit
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        val frameDurationUs = 1_000_000L / fps
        val primaryColor = parseHexInt(project.textColorHex, Color.WHITE)
        val highlightColor = parseHexInt(project.highlightColorHex, Color.CYAN)
        val animation = LyricAnimation.fromRaw(project.textAnimation)
        val position = LyricTextPosition.fromRaw(project.textPosition)
        val beatPeriodMs = (60_000.0 / project.bpm.coerceIn(50L, 200L)).coerceAtLeast(300.0)

        try {
            for (frameIdx in 0 until totalFrames) {
                coroutineContext.ensureActive()
                val timeMs = ((frameIdx.toDouble() / fps) * 1000.0).toLong()
                val ptsUs = frameIdx * frameDurationUs

                val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    inputSurface.lockHardwareCanvas()
                } else {
                    inputSurface.lockCanvas(null)
                }

                try {
                    drawVideoFrame(
                        canvas = canvas,
                        width = width,
                        height = height,
                        timeMs = timeMs,
                        totalDurationMs = totalDurationMs,
                        project = project,
                        bgBitmap = bgBitmap,
                        typeface = typeface,
                        primaryColor = primaryColor,
                        highlightColor = highlightColor,
                        animation = animation,
                        position = position,
                        beatPeriodMs = beatPeriodMs
                    )
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                drainEncoder(encoder, muxer, bufferInfo, endOfStream = false) { outFormat ->
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(outFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    trackIndex
                }

                if (frameIdx % 6 == 0 || frameIdx == totalFrames - 1) {
                    onFrameProgress((frameIdx + 1).toFloat() / totalFrames.toFloat())
                }
            }

            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, bufferInfo, endOfStream = true) { outFormat ->
                if (!muxerStarted) {
                    trackIndex = muxer.addTrack(outFormat)
                    muxer.start()
                    muxerStarted = true
                }
                trackIndex
            }
        } finally {
            try {
                encoder.stop()
            } catch (_: Exception) {
            }
            encoder.release()
            inputSurface.release()
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }
            muxer.release()
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onFormatChanged: (MediaFormat) -> Int
    ) {
        var trackIndex = -1
        val timeoutUs = if (endOfStream) 10_000L else 0L
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                status == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) break
                }
                status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = onFormatChanged(encoder.outputFormat)
                }
                status >= 0 -> {
                    val encodedData = encoder.getOutputBuffer(status)
                    if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        val resolvedTrack = if (trackIndex >= 0) trackIndex else 0
                        muxer.writeSampleData(resolvedTrack, encodedData, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(status, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                }
            }
        }
    }

    private fun drawVideoFrame(
        canvas: Canvas,
        width: Int,
        height: Int,
        timeMs: Long,
        totalDurationMs: Long,
        project: LyricProject,
        bgBitmap: Bitmap?,
        typeface: Typeface,
        primaryColor: Int,
        highlightColor: Int,
        animation: LyricAnimation,
        position: LyricTextPosition,
        beatPeriodMs: Double
    ) {
        canvas.drawColor(Color.parseColor("#07080F"))

        val progress = (timeMs.toFloat() / totalDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        val inSlowMo = project.slowMotionEnabled && project.sceneMarkers.any {
            it.isSlowMotion && timeMs >= it.timeMs && timeMs <= it.timeMs + 6000L
        }
        val beatPhase = ((timeMs % beatPeriodMs) / beatPeriodMs).toFloat()
        val beatPulse = if (project.beatFlashEnabled) {
            ((1f - beatPhase) * (1f - beatPhase)).coerceIn(0f, 1f)
        } else 0f

        val filterStyle = visualFilterForPresetName(project.presetName)

        // 1. Draw Background with Ken Burns slow zoom + beat pulse + template color matrix
        if (bgBitmap != null) {
            val kenBurns = 1.0f + (if (inSlowMo) 0.05f else 0.14f) * progress + beatPulse * 0.018f
            val scale = maxOf(width.toFloat() / bgBitmap.width, height.toFloat() / bgBitmap.height) * kenBurns
            val matrix = Matrix().apply {
                postTranslate(-bgBitmap.width / 2f, -bgBitmap.height / 2f)
                postScale(scale, scale)
                postTranslate(width / 2f, height / 2f)
            }
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                when (filterStyle) {
                    VideoFilterStyle.MONOCHROME_NOIR -> {
                        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                    }
                    VideoFilterStyle.VINTAGE_SEPIA -> {
                        colorFilter = ColorMatrixColorFilter(
                            ColorMatrix(
                                floatArrayOf(
                                    0.393f, 0.769f, 0.189f, 0f, 12f,
                                    0.349f, 0.686f, 0.168f, 0f, 6f,
                                    0.272f, 0.534f, 0.131f, 0f, -8f,
                                    0f, 0f, 0f, 1f, 0f
                                )
                            )
                        )
                    }
                    else -> Unit
                }
            }
            canvas.drawBitmap(bgBitmap, matrix, bgPaint)
        }

        // 2. Dark cinema scrim + template tint + beat flash aura
        val scrimAlpha = (110 + (project.blurIntensity * 85).toInt()).coerceIn(80, 220)
        val topTint = when (filterStyle) {
            VideoFilterStyle.NEON_GRADIENT -> Color.argb(95, 0, 245, 212)
            VideoFilterStyle.VINTAGE_SEPIA -> Color.argb(105, 69, 26, 3)
            VideoFilterStyle.MONOCHROME_NOIR -> Color.argb(160, 0, 0, 0)
            VideoFilterStyle.DREAMY_HAZE -> Color.argb(105, 49, 46, 129)
            VideoFilterStyle.NONE -> Color.argb(scrimAlpha, 7, 8, 15)
        }
        val bottomTint = when (filterStyle) {
            VideoFilterStyle.NEON_GRADIENT -> Color.argb(135, 236, 72, 153)
            VideoFilterStyle.VINTAGE_SEPIA -> Color.argb(150, 39, 19, 6)
            VideoFilterStyle.MONOCHROME_NOIR -> Color.argb(200, 0, 0, 0)
            VideoFilterStyle.DREAMY_HAZE -> Color.argb(150, 30, 27, 75)
            VideoFilterStyle.NONE -> Color.argb((scrimAlpha + 30).coerceAtMost(240), 7, 8, 15)
        }
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(
                topTint,
                Color.argb((scrimAlpha * 0.72f).toInt(), 7, 8, 15),
                bottomTint
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        val overlayPaint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)

        if (beatPulse > 0.1f) {
            val pulsePaint = Paint().apply {
                color = highlightColor
                alpha = (beatPulse * 38).toInt().coerceIn(0, 60)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pulsePaint)
        }

        // 3. Draw active synchronized lyrics with word-level highlighting
        val syncedTimeMs = timeMs + project.syncOffsetMs
        val activeLine = project.lyricLines.firstOrNull { syncedTimeMs in it.startMs..it.endMs }
            ?: return

        drawAnimatedLyricLine(
            canvas = canvas,
            width = width,
            height = height,
            line = activeLine,
            syncedTimeMs = syncedTimeMs,
            project = project,
            typeface = typeface,
            primaryColor = primaryColor,
            highlightColor = highlightColor,
            animation = animation,
            position = position
        )
    }

    private fun drawAnimatedLyricLine(
        canvas: Canvas,
        width: Int,
        height: Int,
        line: LyricLine,
        syncedTimeMs: Long,
        project: LyricProject,
        typeface: Typeface,
        primaryColor: Int,
        highlightColor: Int,
        animation: LyricAnimation,
        position: LyricTextPosition
    ) {
        val elapsedInLine = (syncedTimeMs - line.startMs).coerceAtLeast(0L)
        val remainingInLine = (line.endMs - syncedTimeMs).coerceAtLeast(0L)
        val fadeInFrac = (elapsedInLine / 280f).coerceIn(0f, 1f)
        val fadeOutFrac = (remainingInLine / 220f).coerceIn(0f, 1f)
        val lineAlpha = minOf(fadeInFrac, fadeOutFrac)

        val scaleFactor = when (animation) {
            LyricAnimation.ZOOM_IN -> 0.86f + 0.14f * fadeInFrac
            LyricAnimation.WORD_POP -> 0.94f + 0.06f * fadeInFrac
            else -> 1.0f
        }
        val translateY = when (animation) {
            LyricAnimation.SMOOTH_SLIDE -> (1f - fadeInFrac) * (height * 0.03f)
            else -> 0f
        }

        val scaleRatio = width.toFloat() / 390f
        val fontSizePx = (project.fontSizeSp.toFloat() * scaleRatio).coerceIn(28f, width * 0.13f)

        val basePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = fontSizePx
            color = primaryColor
            alpha = (255 * lineAlpha).toInt().coerceIn(0, 255)
            if (project.textShadowIntensity > 0.05) {
                setShadowLayer(
                    (18f * project.textShadowIntensity.toFloat() * scaleRatio).coerceAtLeast(1f),
                    0f,
                    4f * scaleRatio,
                    Color.argb((210 * lineAlpha).toInt(), 0, 0, 0)
                )
            }
        }

        val maxLineWidth = (width * 0.84f).toInt().coerceAtLeast(100)
        val centerY = when (position) {
            LyricTextPosition.TOP -> height * 0.22f
            LyricTextPosition.CENTER -> height * 0.50f
            LyricTextPosition.BOTTOM -> height * 0.78f
        } + translateY

        canvas.save()
        canvas.translate(width / 2f, centerY)
        canvas.scale(scaleFactor, scaleFactor)

        // Draw words wrapped manually for exact per-word glow & highlight
        val words = if (line.words.isNotEmpty()) line.words else {
            com.example.ai.distributeWordTimings(line.text, line.startMs, line.endMs)
        }
        val spaceW = basePaint.measureText(" ")
        val wrappedRows = mutableListOf<List<Pair<String, Boolean>>>()
        var currentRow = mutableListOf<Pair<String, Boolean>>()
        var currentRowW = 0f

        for (wt in words) {
            val isActiveWord = project.wordHighlightEnabled && syncedTimeMs in wt.startMs..wt.endMs
            val wWidth = basePaint.measureText(wt.word)
            if (currentRow.isNotEmpty() && currentRowW + spaceW + wWidth > maxLineWidth) {
                wrappedRows.add(currentRow)
                currentRow = mutableListOf()
                currentRowW = 0f
            }
            currentRow.add(wt.word to isActiveWord)
            currentRowW += (if (currentRow.size > 1) spaceW else 0f) + wWidth
        }
        if (currentRow.isNotEmpty()) wrappedRows.add(currentRow)

        val lineHeightPx = fontSizePx * 1.28f
        val totalBlockH = wrappedRows.size * lineHeightPx
        var yCursor = -totalBlockH / 2f + fontSizePx * 0.85f

        for (row in wrappedRows) {
            val rowWidth = row.sumOf { basePaint.measureText(it.first).toDouble() }.toFloat() +
                spaceW * (row.size - 1).coerceAtLeast(0)
            var xCursor = -rowWidth / 2f
            for ((idx, pair) in row.withIndex()) {
                val (wordStr, isHighlighted) = pair
                val paintToUse = if (isHighlighted) {
                    TextPaint(basePaint).apply {
                        color = highlightColor
                        alpha = (255 * lineAlpha).toInt().coerceIn(0, 255)
                        if (project.glowIntensity > 0.05) {
                            maskFilter = BlurMaskFilter(
                                (12f * project.glowIntensity.toFloat() * scaleRatio).coerceAtLeast(1f),
                                BlurMaskFilter.Blur.SOLID
                            )
                        }
                    }
                } else {
                    basePaint
                }
                if (isHighlighted && project.glowIntensity > 0.1) {
                    val glowPaint = TextPaint(paintToUse).apply {
                        alpha = (140 * project.glowIntensity * lineAlpha).toInt().coerceIn(0, 180)
                        maskFilter = BlurMaskFilter(
                            (22f * project.glowIntensity.toFloat() * scaleRatio).coerceAtLeast(2f),
                            BlurMaskFilter.Blur.NORMAL
                        )
                    }
                    canvas.drawText(wordStr, xCursor, yCursor, glowPaint)
                }
                canvas.drawText(wordStr, xCursor, yCursor, paintToUse)
                xCursor += basePaint.measureText(wordStr) + (if (idx < row.lastIndex) spaceW else 0f)
            }
            yCursor += lineHeightPx
        }

        canvas.restore()
    }

    private fun muxVideoAndAudio(
        context: Context,
        videoOnlyFile: File,
        audioSourceFile: File,
        outputMp4: File,
        maxDurationUs: Long
    ) {
        val videoExtractor = MediaExtractor().apply { setDataSource(videoOnlyFile.absolutePath) }
        val audioExtractor = MediaExtractor().apply { setDataSource(audioSourceFile.absolutePath) }

        var videoTrackIdx = -1
        var videoFormat: MediaFormat? = null
        for (i in 0 until videoExtractor.trackCount) {
            val fmt = videoExtractor.getTrackFormat(i)
            if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                videoTrackIdx = i
                videoFormat = fmt
                break
            }
        }

        var audioTrackIdx = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until audioExtractor.trackCount) {
            val fmt = audioExtractor.getTrackFormat(i)
            if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                audioTrackIdx = i
                audioFormat = fmt
                break
            }
        }

        val audioMime = audioFormat?.getString(MediaFormat.KEY_MIME) ?: ""
        val needsTranscode = audioFormat != null && !audioMime.contains("mp4a") && !audioMime.contains("aac")
        var transcodedAacFile: File? = null
        var finalAudioExtractor = audioExtractor
        var finalAudioTrackIdx = audioTrackIdx
        var finalAudioFormat = audioFormat

        if (needsTranscode && audioFormat != null) {
            transcodedAacFile = File(context.cacheDir, "transcoded_audio_${System.currentTimeMillis()}.m4a")
            val ok = transcodeAudioToAacM4a(audioSourceFile, transcodedAacFile, maxDurationUs)
            if (ok && transcodedAacFile.exists()) {
                audioExtractor.release()
                finalAudioExtractor = MediaExtractor().apply { setDataSource(transcodedAacFile.absolutePath) }
                for (i in 0 until finalAudioExtractor.trackCount) {
                    val fmt = finalAudioExtractor.getTrackFormat(i)
                    if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                        finalAudioTrackIdx = i
                        finalAudioFormat = fmt
                        break
                    }
                }
            }
        }

        val muxer = MediaMuxer(outputMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxVideoTrack = if (videoFormat != null) muxer.addTrack(videoFormat) else -1
        val muxAudioTrack = if (finalAudioFormat != null && finalAudioTrackIdx >= 0) muxer.addTrack(finalAudioFormat!!) else -1
        muxer.start()

        val buf = ByteBuffer.allocate(512 * 1024)
        val info = MediaCodec.BufferInfo()

        if (muxVideoTrack >= 0 && videoTrackIdx >= 0) {
            videoExtractor.selectTrack(videoTrackIdx)
            while (true) {
                val size = videoExtractor.readSampleData(buf, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = videoExtractor.sampleTime
                info.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(muxVideoTrack, buf, info)
                videoExtractor.advance()
            }
        }

        if (muxAudioTrack >= 0 && finalAudioTrackIdx >= 0) {
            finalAudioExtractor.selectTrack(finalAudioTrackIdx)
            while (true) {
                val size = finalAudioExtractor.readSampleData(buf, 0)
                if (size < 0) break
                val pts = finalAudioExtractor.sampleTime
                if (pts > maxDurationUs) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = pts
                info.flags = finalAudioExtractor.sampleFlags
                muxer.writeSampleData(muxAudioTrack, buf, info)
                finalAudioExtractor.advance()
            }
        }

        muxer.stop()
        muxer.release()
        videoExtractor.release()
        finalAudioExtractor.release()
        transcodedAacFile?.delete()
    }

    private fun transcodeAudioToAacM4a(
        sourceFile: File,
        outM4a: File,
        maxDurationUs: Long
    ): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(sourceFile.absolutePath)
            var audioIdx = -1
            var inFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                if ((fmt.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    audioIdx = i
                    inFormat = fmt
                    break
                }
            }
            if (audioIdx == -1 || inFormat == null) return false
            extractor.selectTrack(audioIdx)

            val inMime = inFormat.getString(MediaFormat.KEY_MIME)!!
            val sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)

            val decoder = MediaCodec.createDecoderByType(inMime)
            decoder.configure(inFormat, null, null, 0)
            decoder.start()

            val outFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val muxer = MediaMuxer(outM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxTrack = -1
            var muxStarted = false

            val decInfo = MediaCodec.BufferInfo()
            val encInfo = MediaCodec.BufferInfo()
            var decInDone = false
            var decOutDone = false
            var encOutDone = false

            while (!encOutDone) {
                if (!decInDone) {
                    val idx = decoder.dequeueInputBuffer(5_000)
                    if (idx >= 0) {
                        val buf = decoder.getInputBuffer(idx)!!
                        val size = extractor.readSampleData(buf, 0)
                        val pts = extractor.sampleTime
                        if (size < 0 || pts > maxDurationUs) {
                            decoder.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            decInDone = true
                        } else {
                            decoder.queueInputBuffer(idx, 0, size, pts, 0)
                            extractor.advance()
                        }
                    }
                }

                if (!decOutDone) {
                    val outIdx = decoder.dequeueOutputBuffer(decInfo, 5_000)
                    if (outIdx >= 0) {
                        val pcmBuf = decoder.getOutputBuffer(outIdx)
                        val isEos = (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        if (pcmBuf != null && decInfo.size > 0) {
                            pcmBuf.position(decInfo.offset)
                            pcmBuf.limit(decInfo.offset + decInfo.size)
                            val encInIdx = encoder.dequeueInputBuffer(10_000)
                            if (encInIdx >= 0) {
                                val encInBuf = encoder.getInputBuffer(encInIdx)!!
                                val toCopy = minOf(pcmBuf.remaining(), encInBuf.remaining())
                                val slice = pcmBuf.slice()
                                slice.limit(toCopy)
                                encInBuf.put(slice)
                                encoder.queueInputBuffer(encInIdx, 0, toCopy, decInfo.presentationTimeUs, if (isEos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                            }
                        } else if (isEos) {
                            val encInIdx = encoder.dequeueInputBuffer(10_000)
                            if (encInIdx >= 0) {
                                encoder.queueInputBuffer(encInIdx, 0, 0, decInfo.presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (isEos) decOutDone = true
                    }
                }

                while (true) {
                    val encOutIdx = encoder.dequeueOutputBuffer(encInfo, 2_000)
                    if (encOutIdx == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxStarted) {
                            muxTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxStarted = true
                        }
                    } else if (encOutIdx >= 0) {
                        val outBuf = encoder.getOutputBuffer(encOutIdx)
                        if (outBuf != null && (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && encInfo.size > 0 && muxStarted) {
                            outBuf.position(encInfo.offset)
                            outBuf.limit(encInfo.offset + encInfo.size)
                            muxer.writeSampleData(muxTrack, outBuf, encInfo)
                        }
                        encoder.releaseOutputBuffer(encOutIdx, false)
                        if ((encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            encOutDone = true
                            break
                        }
                    }
                }
            }

            decoder.stop(); decoder.release()
            encoder.stop(); encoder.release()
            if (muxStarted) muxer.stop()
            muxer.release()
            true
        } catch (e: Exception) {
            Log.w(TAG, "AAC transcode failed", e)
            false
        } finally {
            extractor.release()
        }
    }

    private fun loadBackgroundBitmap(
        context: Context,
        project: LyricProject,
        targetW: Int,
        targetH: Int
    ): Bitmap? {
        if (project.localBackgroundPath.isNotBlank()) {
            val file = File(project.localBackgroundPath)
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) return bmp
            }
            try {
                val uri = Uri.parse(project.localBackgroundPath)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { return it }
                }
            } catch (_: Exception) {
            }
        }
        // Check if source audio is a video file — extract a cinematic frame
        if (project.localAudioPath.endsWith(".mp4", ignoreCase = true) ||
            project.localAudioPath.endsWith(".mov", ignoreCase = true)
        ) {
            val mmr = MediaMetadataRetriever()
            try {
                mmr.setDataSource(project.localAudioPath)
                mmr.getFrameAtTime(1_000_000L)?.let { return it }
            } catch (_: Exception) {
            } finally {
                try { mmr.release() } catch (_: Exception) {}
            }
        }
        val preset = BackgroundPreset.fromKey(project.backgroundPresetKey)
        return BitmapFactory.decodeResource(context.resources, preset.drawableRes)
    }

    private fun loadProjectTypeface(context: Context, fontStyle: String): Typeface {
        return try {
            ResourcesCompat.getFont(context, fontResForName(fontStyle)) ?: Typeface.DEFAULT_BOLD
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    private fun clampToSupportedAvcSize(reqW: Int, reqH: Int): Pair<Int, Int> {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val info = codecList.codecInfos.firstOrNull {
                it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) }
            }
            val caps = info?.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)?.videoCapabilities
            if (caps != null && !caps.isSizeSupported(reqW, reqH)) {
                val maxW = caps.supportedWidths.upper
                val maxH = caps.supportedHeights.upper
                val scale = minOf(maxW.toFloat() / reqW, maxH.toFloat() / reqH, 1f)
                val w = ((reqW * scale).toInt() / 16) * 16
                val h = ((reqH * scale).toInt() / 16) * 16
                w.coerceAtLeast(320) to h.coerceAtLeast(320)
            } else {
                (reqW - (reqW % 2)) to (reqH - (reqH % 2))
            }
        } catch (_: Exception) {
            (reqW - (reqW % 2)) to (reqH - (reqH % 2))
        }
    }

    private fun saveToMediaStore(context: Context, sourceFile: File, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/LYRIX_AI")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            Log.w(TAG, "Failed to insert into MediaStore", e)
            null
        }
    }
}
