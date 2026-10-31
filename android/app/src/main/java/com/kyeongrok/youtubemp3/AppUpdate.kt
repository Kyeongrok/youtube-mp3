package com.kyeongrok.youtubemp3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** GitHub 최신 릴리즈가 지금 판보다 새로우면 그 태그를 알려 준다. */
object AppUpdate {
    const val RELEASES_URL = "https://github.com/Kyeongrok/youtube-mp3/releases/latest"
    private const val API_URL = "https://api.github.com/repos/Kyeongrok/youtube-mp3/releases/latest"

    suspend fun findNewerVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val latest = JSONObject(body).getString("tag_name").removePrefix("v")
            latest.takeIf { isNewer(it, BuildConfig.VERSION_NAME) }
        } catch (_: Exception) {
            null
        }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val left = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val right = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val difference = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }
}
