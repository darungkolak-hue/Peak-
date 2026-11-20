package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "AudioAnalyzer"
private const val MAX_AI_INLINE_BYTES = 16 * 1024 * 1024 // Keep safely under 20MB inline limit

data class AudioAnalysisResult(
    val localFile: File,
    val displayName: String,
    val mimeType: String,
    val durationMs: Long,
    val estimatedBpm: Int,
    val waveformBars: List<Double>,
    val beatPeaksMs: List<Long>,
    val isVideoSource: Boolean
)

object AudioAnalyzer {

    suspend fun importAndAnalyzeMedia(
        context: Context,
        uri: Uri
    ): AudioAnalysisResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawMime = resolver.getType(uri) ?: "audio/mpeg"
        val displayName = queryDisplayName(context, uri) ?: "track_${System.currentTimeMillis()}.mp3"
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        val safeName = "${System.currentTimeMillis()}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
        val destFile = File(mediaDir, safeName)

        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read selected file.")

        val isVideo = rawMime.startsWith("video/") ||
            displayName.endsWith(".mp4", ignoreCase = true) ||
            displayName.endsWith(".mov", ignoreCase = true) ||
            displayName.endsWith(".mkv", ignoreCase = true)

        var durationMs = extractDurationMs(destFile)
        val (waveform, beats, bpm) = analyzePcmWaveformAndBeats(destFile, durationMs)
        if (durationMs <= 0L) {
            durationMs = 30_000L
        }

        AudioAnalysisResult(
            localFile = destFile,
            displayName = displayName,
            mimeType = rawMime,
            durationMs = durationMs,
            estimatedBpm = bpm,
            waveformBars = waveform,
            beatPeaksMs = beats,
            isVideoSource = isVideo
        )
    }

    suspend fun extractCompactAudioPayload(
        context: Context,
        sourceFile: File,
        mimeType: String
    ): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val isVideo = mimeType.startsWith("video/") ||
            sourceFile.extension.lowercase() in setOf("mp4", "mov", "mkv", "webm")

        if (!isVideo && sourceFile.length() <= MAX_AI_INLINE_BYTES) {
            val normalizedMime = when (sourceFile.extension.lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a", "aac" -> "audio/mp4"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                "flac" -> "audio/flac"
                else -> if (mimeType.startsWith("audio/")) mimeType else "audio/mpeg"
            }
            return@withContext sourceFile.readBytes() to normalizedMime
        }

        // For video files or large audio files, try muxing just the audio track to a compact .m4a
        val tempM4a = File(context.cacheDir, "ai_audio_${System.currentTimeMillis()}.m4a")
        try {
            val muxed = extractAudioTrackOnly(sourceFile, tempM4a)
            if (muxed && tempM4a.exists() && tempM4a.length() in 1..MAX_AI_INLINE_BYTES) {
                return@withContext tempM4a.readBytes() to "audio/mp4"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct audio track mux fallback to PCM WAV downsample", e)
        } finally {
            tempM4a.delete()
        }

        // Fallback: Decode to compact mono 16kHz 16-bit WAV (capped under MAX_AI_INLINE_BYTES)
        val wavBytes = decodeToCompactMonoWav(sourceFile, targetSampleRate = 16000, maxBytes = MAX_AI_INLINE_BYTES)
        wavBytes to "audio/wav"
    }

    private fun extractAudioTrackOnly(sourceFile: File, outputFile: File): Boolean {
        val extractor = MediaExtractor()
        extractor.setDataSource(sourceFile.absolutePath)
        var audioTrackIndex = -1
        var audioFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                audioFormat = format
                break
            }
        }
        if (audioTrackIndex == -1 || audioFormat == null) {
            extractor.release()
            return false
        }
        val audioMime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
        if (!audioMime.contains("mp4a") && !audioMime.contains("aac")) {
            extractor.release()
            return false
        }

        extractor.selectTrack(audioTrackIndex)
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val outTrack = muxer.addTrack(audioFormat)
        muxer.start()

        val buffer = ByteBuffer.allocate(256 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break
            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(outTrack, buffer, bufferInfo)
            extractor.advance()
        }

        muxer.stop()
        muxer.release()
        extractor.release()
        return true
    }

    private fun decodeToCompactMonoWav(
        sourceFile: File,
        targetSampleRate: Int,
        maxBytes: Int
    ): ByteArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(sourceFile.absolutePath)
        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = fmt
                break
            }
        }
        if (audioTrackIndex == -1 || format == null) {
            extractor.release()
            throw IllegalStateException("No audio track found in selected file.")
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val srcSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmOut = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        val step = (srcSampleRate / targetSampleRate).coerceAtLeast(1)
        var sampleCounter = 0

        while (!sawOutputEos && pcmOut.size() < maxBytes - 64) {
            if (!sawInputEos) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx)!!
                    val size = extractor.readSampleData(inBuf, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIdx = codec.dequeueOutputBuffer(info, 10_000)
            if (outIdx >= 0) {
                val outBuf = codec.getOutputBuffer(outIdx)
                if (outBuf != null && info.size > 0) {
                    outBuf.position(info.offset)
                    outBuf.limit(info.offset + info.size)
                    val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val frameCount = shortBuf.remaining() / channels
                    for (f in 0 until frameCount) {
                        var sum = 0
                        for (c in 0 until channels) {
                            sum += shortBuf.get()
                        }
                        if (sampleCounter % step == 0) {
                            val mono = (sum / channels).toShort()
                            pcmOut.write(mono.toInt() and 0xFF)
                            pcmOut.write((mono.toInt() shr 8) and 0xFF)
                        }
                        sampleCounter++
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEos = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        val pcmBytes = pcmOut.toByteArray()
        val actualRate = (srcSampleRate / step).coerceAtLeast(8000)
        return wrapWavHeader(pcmBytes, actualRate, 1, 16)
    }

    private fun wrapWavHeader(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val totalDataLen = pcmData.size + 36
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalDataLen)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1.toShort())
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcmData.size)
        return header.array() + pcmData
    }

    private fun extractDurationMs(file: File): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract duration", e)
            0L
        } finally {
            try {
                mmr.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun analyzePcmWaveformAndBeats(
        file: File,
        knownDurationMs: Long
    ): Triple<List<Double>, List<Long>, Int> {
        val barCount = 72
        val buckets = DoubleArray(barCount)
        val bucketCounts = IntArray(barCount)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var audioIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioIdx = i
                    format = fmt
                    break
                }
            }
            if (audioIdx == -1 || format == null) {
                return defaultWaveformAndBeats(knownDurationMs)
            }
            val durationUs = when {
                knownDurationMs > 0L -> knownDurationMs * 1000L
                format.containsKey(MediaFormat.KEY_DURATION) -> format.getLong(MediaFormat.KEY_DURATION)
                else -> 60_000_000L
            }.coerceAtLeast(1_000_000L)

            extractor.selectTrack(audioIdx)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var sawIn = false
            var sawOut = false
            var framesProcessed = 0L

            while (!sawOut) {
                if (!sawIn) {
                    val idx = codec.dequeueInputBuffer(5_000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(idx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawIn = true
                        } else {
                            codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 5_000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && info.size > 0) {
                        val bucketIdx = ((info.presentationTimeUs.toDouble() / durationUs.toDouble()) * barCount)
                            .toInt().coerceIn(0, barCount - 1)
                        outBuf.position(info.offset)
                        outBuf.limit(info.offset + info.size)
                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        var sumSq = 0.0
                        var count = 0
                        val stride = (8 * channels).coerceAtLeast(1)
                        while (shortBuf.remaining() >= stride) {
                            val sample = shortBuf.get().toDouble() / 32768.0
                            sumSq += sample * sample
                            count++
                            for (s in 1 until stride) shortBuf.get()
                        }
                        if (count > 0) {
                            val rms = sqrt(sumSq / count)
                            buckets[bucketIdx] += rms
                            bucketCounts[bucketIdx]++
                        }
                        framesProcessed++
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0 || framesProcessed > 6000L) {
                        sawOut = true
                    }
                }
            }
            codec.stop()
            codec.release()

            val averaged = DoubleArray(barCount) { i ->
                if (bucketCounts[i] > 0) buckets[i] / bucketCounts[i] else 0.08
            }
            val maxPeak = averaged.maxOrNull()?.coerceAtLeast(0.05) ?: 1.0
            val normalized = averaged.map { (it / maxPeak).coerceIn(0.08, 1.0) }

            val durMs = (durationUs / 1000L).coerceAtLeast(1000L)
            val peaks = mutableListOf<Long>()
            for (i in 1 until barCount - 1) {
                if (normalized[i] > 0.62 && normalized[i] >= normalized[i - 1] && normalized[i] >= normalized[i + 1]) {
                    val tMs = ((i.toDouble() / barCount) * durMs).toLong()
                    if (peaks.isEmpty() || tMs - peaks.last() >= 1800L) {
                        peaks.add(tMs)
                    }
                }
            }
            val estBpm = estimateBpmFromPeaks(peaks, durMs)
            return Triple(normalized, peaks, estBpm)
        } catch (e: Exception) {
            Log.w(TAG, "Waveform analysis fallback", e)
            return defaultWaveformAndBeats(knownDurationMs)
        } finally {
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun estimateBpmFromPeaks(peaks: List<Long>, durationMs: Long): Int {
        if (peaks.size < 3) return 104
        val intervals = peaks.zipWithNext { a, b -> b - a }.filter { it in 350L..2400L }
        if (intervals.isEmpty()) return 108
        val avgMs = intervals.average()
        var bpm = (60_000.0 / avgMs).roundToInt()
        while (bpm < 72) bpm *= 2
        while (bpm > 165) bpm /= 2
        return bpm.coerceIn(70, 170)
    }

    private fun defaultWaveformAndBeats(durationMs: Long): Triple<List<Double>, List<Long>, Int> {
        val dur = durationMs.coerceAtLeast(15_000L)
        val bars = List(64) { i ->
            0.25 + 0.6 * abs(kotlin.math.sin(i * 0.37))
        }
        val beats = (0L until dur step 4500L).toList()
        return Triple(bars, beats, 105)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
