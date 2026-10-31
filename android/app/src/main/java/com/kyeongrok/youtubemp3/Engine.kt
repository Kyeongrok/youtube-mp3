package com.kyeongrok.youtubemp3

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class VideoSearchResult(
    val title: String,
    val channel: String,
    val duration: String,
    val url: String,
)

/**
 * yt-dlp 실행을 맡는다(데스크톱의 YoutubeService에 해당).
 * 유튜브가 바뀌면 앱에 들어 있는 yt-dlp는 금방 낡으므로, 켤 때 하루 한 번 최신판으로 올린다.
 */
object Engine {
    private const val TAG = "Engine"
    private const val PREFS = "engine"
    private const val KEY_LAST_UPDATE = "lastUpdate"

    private val ready = CompletableDeferred<Unit>()

    fun start(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
                updateIfStale(appContext)
                ready.complete(Unit)
            } catch (e: Exception) {
                ready.completeExceptionally(e)
            }
        }
    }

    private fun updateIfStale(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST_UPDATE, 0) < TimeUnit.DAYS.toMillis(1)) return

        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            Log.i(TAG, "yt-dlp update: $status")
            prefs.edit().putLong(KEY_LAST_UPDATE, now).apply()
        } catch (e: Exception) {
            // 오프라인 등으로 실패해도 들어 있는 버전으로 계속 쓴다.
            Log.w(TAG, "yt-dlp update failed", e)
        }
    }

    suspend fun search(query: String, maxResults: Int = 20): List<VideoSearchResult> {
        ready.await()
        return withContext(Dispatchers.IO) {
            val request = YoutubeDLRequest("ytsearch$maxResults:$query")
                .addOption("--flat-playlist")
                .addOption("--dump-single-json")
            parseSearchResults(YoutubeDL.getInstance().execute(request, null, false, null).out)
        }
    }

    private fun parseSearchResults(json: String): List<VideoSearchResult> {
        val entries = JSONObject(json).optJSONArray("entries") ?: return emptyList()
        return (0 until entries.length()).map(entries::getJSONObject)
            .filter { it.optString("id").isNotEmpty() }
            .map { entry ->
                VideoSearchResult(
                    title = entry.optString("title").ifEmpty { "(제목 없음)" },
                    channel = entry.optString("channel").takeUnless { it.isEmpty() || it == "null" }
                        ?: entry.optString("uploader").takeUnless { it == "null" }.orEmpty(),
                    duration = formatDuration(entry.optDouble("duration")),
                    url = "https://www.youtube.com/watch?v=${entry.getString("id")}",
                )
            }
    }

    /** [outputDirectory]에 MP3 한 개를 받아 그 파일을 돌려준다. */
    suspend fun downloadAudio(
        url: String,
        outputDirectory: File,
        audioQuality: String,
        processId: String,
        onProgress: (Float) -> Unit,
    ): File {
        ready.await()
        return withContext(Dispatchers.IO) {
            val request = YoutubeDLRequest(url)
                .addOption("--no-playlist")
                .addOption("--extract-audio")
                .addOption("--audio-format", "mp3")
                .addOption("--audio-quality", audioQuality)
                .addOption("--embed-metadata")
                .addOption("-o", File(outputDirectory, "%(title)s.%(ext)s").absolutePath)

            YoutubeDL.getInstance().execute(request, processId, false) { progress, _, _ ->
                onProgress(progress)
            }
            outputDirectory.listFiles { file -> file.extension == "mp3" }?.firstOrNull()
                ?: error("MP3 파일이 만들어지지 않았습니다.")
        }
    }

    fun cancel(processId: String) {
        YoutubeDL.getInstance().destroyProcessById(processId)
    }

    private fun formatDuration(seconds: Double): String {
        if (seconds.isNaN()) return ""
        val total = seconds.toLong()
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val rest = total % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
    }
}
