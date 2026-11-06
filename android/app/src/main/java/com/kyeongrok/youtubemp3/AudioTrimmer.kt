package com.kyeongrok.youtubemp3

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** 원본 곡 기준 시간(초)으로 잡은 구간. */
data class TimeRange(val start: Double, val end: Double) {
    val length get() = end - start
}

/**
 * 곡에서 필요 없는 구간을 잘라 낸다(데스크톱 편집기의 모바일판).
 * 편집하는 동안은 원본을 그대로 두고 지운 구간 목록만 들고 있다가, 저장할 때 앱에 들어 있는 FFmpeg로 한 번에 만든다.
 */
object AudioTrimmer {
    /** 파형은 1초에 이만큼의 막대로 나눠 둔다. 확대해도 0.01초 단위까지 보인다. */
    const val PEAKS_PER_SECOND = 100
    private const val DECODE_RATE = 4000
    private const val SAMPLES_PER_PEAK = DECODE_RATE / PEAKS_PER_SECOND

    /** 덮어쓸 때 원래 형식 그대로 다시 만들 수 있는 확장자와 그 인코더 설정. */
    private val ENCODERS = mapOf(
        "mp3" to listOf("-c:a", "libmp3lame", "-q:a", "2"),
        "m4a" to listOf("-c:a", "aac", "-b:a", "192k"),
        "mp4" to listOf("-c:a", "aac", "-b:a", "192k"),
        "aac" to listOf("-c:a", "aac", "-b:a", "192k"),
        "wav" to listOf("-c:a", "pcm_s16le"),
        "flac" to listOf("-c:a", "flac"),
    )

    class Source(
        /** 앱 캐시에 복사해 둔 원본. 미리 듣기와 저장에 쓴다. */
        val file: File,
        val extension: String,
        /** 0~1로 맞춘 파형 막대, [PEAKS_PER_SECOND]개가 1초다. */
        val peaks: FloatArray,
        val durationSec: Double,
    ) {
        val canOverwrite get() = extension in ENCODERS
    }

    /** 곡을 캐시에 복사하고 파형을 뽑는다. 긴 녹음은 몇십 초 걸릴 수 있다. */
    suspend fun open(context: Context, track: Track): Source = withContext(Dispatchers.IO) {
        Engine.awaitFFmpeg()
        val dir = File(context.cacheDir, "trim").apply {
            deleteRecursively()
            mkdirs()
        }
        val extension = displayName(context, track.uri)
            ?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() } ?: "mp3"
        val file = File(dir, "source.$extension")
        val input = context.contentResolver.openInputStream(track.uri) ?: error("파일을 열 수 없습니다.")
        input.use { stream -> file.outputStream().use { stream.copyTo(it) } }

        val (peaks, samples) = decodePeaks(context, file)
        if (samples == 0L) error("소리를 읽지 못했습니다.")
        Source(file, extension, peaks, samples.toDouble() / DECODE_RATE)
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    /** 모노·저음질로 풀어서 0.01초마다 가장 큰 소리를 모은다. */
    private suspend fun decodePeaks(context: Context, file: File): Pair<FloatArray, Long> {
        val log = File(file.parentFile, "decode.log")
        val process = ffmpeg(
            context,
            "-v", "error", "-i", file.absolutePath, "-vn",
            "-ac", "1", "-ar", DECODE_RATE.toString(), "-f", "s16le", "-",
        ).redirectError(log).start()

        var peaks = FloatArray(PEAKS_PER_SECOND * 600)
        var count = 0
        var samples = 0L
        var current = 0
        var inBucket = 0
        try {
            process.inputStream.buffered(1 shl 16).use { input ->
                val buffer = ByteArray(1 shl 16)
                var leftover = -1
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    var i = 0
                    while (i < read) {
                        val low: Int
                        val high: Int
                        if (leftover >= 0) {
                            low = leftover
                            high = buffer[i].toInt()
                            leftover = -1
                            i += 1
                        } else if (i + 1 < read) {
                            low = buffer[i].toInt() and 0xFF
                            high = buffer[i + 1].toInt()
                            i += 2
                        } else {
                            leftover = buffer[i].toInt() and 0xFF
                            i += 1
                            continue
                        }
                        val value = abs((high shl 8) or low)
                        if (value > current) current = value
                        samples++
                        if (++inBucket == SAMPLES_PER_PEAK) {
                            if (count == peaks.size) peaks = peaks.copyOf(peaks.size * 2)
                            peaks[count++] = current.toFloat()
                            current = 0
                            inBucket = 0
                        }
                    }
                }
            }
            if (process.waitFor() != 0) error(log.readText().lines().lastOrNull { it.isNotBlank() } ?: "디코딩 실패")
        } finally {
            process.destroy()
        }
        if (inBucket > 0) {
            if (count == peaks.size) peaks = peaks.copyOf(count + 1)
            peaks[count++] = current.toFloat()
        }

        val result = peaks.copyOf(count)
        val max = result.maxOrNull() ?: 0f
        if (max > 0f) for (i in result.indices) result[i] /= max
        return result to samples
    }

    // ── 편집 목록 계산 ─────────────────────────────────────────

    fun editedDuration(total: Double, removed: List<TimeRange>) = total - removed.sumOf { it.length }

    /** 편집된 시간(지운 구간을 접은 시간)을 원본 시간으로 바꾼다. */
    fun toOriginal(edited: Double, removed: List<TimeRange>): Double {
        var original = edited
        for (range in removed) {
            if (range.start <= original) original += range.length else break
        }
        return original
    }

    /** 원본 시간을 편집된 시간으로 바꾼다. 지운 구간 안이면 그 구간이 접힌 자리다. */
    fun toEdited(original: Double, removed: List<TimeRange>): Double {
        var edited = original
        for (range in removed) {
            when {
                range.end <= original -> edited -= range.length
                range.start < original -> edited -= original - range.start
                else -> break
            }
        }
        return edited
    }

    /** 편집된 시간의 [start, end) 구간을 더 지운 새 목록. 겹치거나 붙은 구간은 하나로 합친다. */
    fun remove(removed: List<TimeRange>, start: Double, end: Double): List<TimeRange> {
        if (end <= start) return removed
        val added = TimeRange(toOriginal(start, removed), toOriginal(end, removed))
        val merged = mutableListOf<TimeRange>()
        for (range in (removed + added).sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if (last != null && range.start <= last.end + 1e-6) {
                merged[merged.lastIndex] = TimeRange(last.start, maxOf(last.end, range.end))
            } else {
                merged += range
            }
        }
        return merged
    }

    /** 원본 시간이 지운 구간 안이면 그 구간을 돌려준다. 미리 듣기에서 건너뛸 때 쓴다. */
    fun removedAt(original: Double, removed: List<TimeRange>) =
        removed.firstOrNull { original >= it.start && original < it.end }

    /** 지운 구간을 빼고 이어 붙인 파형. */
    fun editedPeaks(peaks: FloatArray, removed: List<TimeRange>): FloatArray {
        if (removed.isEmpty()) return peaks
        val result = FloatArray(peaks.size)
        var count = 0
        var next = 0
        for (i in peaks.indices) {
            val time = (i + 0.5) / PEAKS_PER_SECOND
            while (next < removed.size && removed[next].end <= time) next++
            if (next < removed.size && time >= removed[next].start) continue
            result[count++] = peaks[i]
        }
        return result.copyOf(count)
    }

    private fun keptRanges(total: Double, removed: List<TimeRange>): List<TimeRange> {
        val kept = mutableListOf<TimeRange>()
        var cursor = 0.0
        for (range in removed) {
            if (range.start > cursor) kept += TimeRange(cursor, range.start)
            cursor = maxOf(cursor, range.end)
        }
        if (cursor < total) kept += TimeRange(cursor, total)
        return kept.filter { it.length > 0.001 }
    }

    // ── 저장 ────────────────────────────────────────────────

    /**
     * 지운 구간을 뺀 파일을 캐시에 만들어 돌려준다.
     * [extension]이 null이면 원본과 같은 형식으로, 아니면 그 형식으로 만든다.
     * [gainDb]만큼 볼륨을 키우거나 줄인다(데스크톱 볼륨 조절과 같은 FFmpeg volume 필터).
     */
    suspend fun render(
        context: Context,
        source: Source,
        removed: List<TimeRange>,
        extension: String? = null,
        gainDb: Double = 0.0,
    ): File =
        withContext(Dispatchers.IO) {
            val ext = extension ?: source.extension
            val encoder = ENCODERS[ext] ?: error("$ext 형식으로는 저장할 수 없습니다.")
            val kept = keptRanges(source.durationSec, removed)
            if (kept.isEmpty()) error("남는 구간이 없습니다.")

            // 구간마다 샘플 단위로 정확히 잘라(atrim) 이어 붙인다.
            val graph = buildString {
                append("[0:a:0]asplit=${kept.size}")
                kept.indices.forEach { append("[s$it]") }
                append(';')
                kept.forEachIndexed { i, range ->
                    append("[s$i]atrim=start=%.4f:end=%.4f,asetpts=PTS-STARTPTS[k$i];".format(java.util.Locale.ROOT, range.start, range.end))
                }
                kept.indices.forEach { append("[k$it]") }
                append("concat=n=${kept.size}:v=0:a=1[cat];")
                append("[cat]volume=%.1fdB[out]".format(java.util.Locale.ROOT, gainDb))
            }

            val output = File(source.file.parentFile, "edited.$ext")
            output.delete()
            val args = listOf(
                "-y", "-v", "error", "-i", source.file.absolutePath,
                "-filter_complex", graph, "-map", "[out]", "-map_metadata", "0",
            ) + encoder + output.absolutePath
            val process = ffmpeg(context, *args.toTypedArray()).redirectErrorStream(true).start()
            try {
                val log = process.inputStream.bufferedReader().readText()
                if (process.waitFor() != 0 || !output.exists()) {
                    error(log.lines().lastOrNull { it.isNotBlank() } ?: "저장 실패")
                }
            } finally {
                process.destroy()
            }
            output
        }

    /** 만든 파일로 원본을 덮어쓴다. 권한이 없으면 SecurityException이 난다. */
    suspend fun overwrite(context: Context, file: File, uri: Uri) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("원본을 열 수 없습니다.")
        output.use { stream -> file.inputStream().use { it.copyTo(stream) } }
    }

    /** 새 곡으로 Music/YoutubeMp3에 넣는다. */
    suspend fun saveAsNew(context: Context, file: File, title: String): Uri = withContext(Dispatchers.IO) {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "편집" }
        val named = File(file.parentFile, "$safe (편집).mp3")
        named.delete()
        file.copyTo(named)
        try {
            Library.save(context, named)
        } finally {
            named.delete()
        }
    }

    fun clear(context: Context) {
        File(context.cacheDir, "trim").deleteRecursively()
    }

    /** yt-dlp와 같은 방식으로, 앱에 함께 깔린 FFmpeg를 직접 실행한다. */
    private fun ffmpeg(context: Context, vararg args: String): ProcessBuilder {
        val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val binary = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        // 입력을 기다리거나 키 입력을 읽지 않게 표준입력은 쓰지 않는다.
        return ProcessBuilder(listOf(binary.absolutePath, "-nostdin") + args).apply {
            environment()["LD_LIBRARY_PATH"] = listOf(
                File(packages, "python/usr/lib"),
                File(packages, "ffmpeg/usr/lib"),
            ).joinToString(":") { it.absolutePath }
        }
    }
}
