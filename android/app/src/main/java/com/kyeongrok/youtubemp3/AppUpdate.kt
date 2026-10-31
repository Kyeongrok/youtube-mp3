package com.kyeongrok.youtubemp3

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(val version: String, val apkUrl: String)

/** GitHub 최신 릴리즈가 지금 판보다 새로우면 이 폰에 맞는 APK를 받아 PackageInstaller로 깐다. */
object AppUpdate {
    private const val API_URL = "https://api.github.com/repos/Kyeongrok/youtube-mp3/releases/latest"
    private const val INSTALL_ACTION = "com.kyeongrok.youtubemp3.INSTALLED"

    private val APK_SUFFIXES = mapOf(
        "arm64-v8a" to "-android-arm64.apk",
        "armeabi-v7a" to "-android-armv7.apk",
    )

    /** 이 폰에 맞는 APK가 아직 릴리즈에 붙지 않았으면 null이다. */
    suspend fun findNewerRelease(): AppRelease? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(API_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(body)
            val latest = release.getString("tag_name").removePrefix("v")
            if (!isNewer(latest, BuildConfig.VERSION_NAME)) return@withContext null

            val suffix = Build.SUPPORTED_ABIS.firstNotNullOfOrNull(APK_SUFFIXES::get) ?: return@withContext null
            val assets = release.getJSONArray("assets")
            List(assets.length()) { assets.getJSONObject(it).getString("browser_download_url") }
                .firstOrNull { it.endsWith(suffix) }
                ?.let { AppRelease(latest, it) }
        } catch (_: Exception) {
            null
        }
    }

    /** APK가 커서 메모리에 올리지 않고 받는 대로 설치 세션에 쓴다. */
    suspend fun install(context: Context, release: AppRelease, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            val sessionId = installer.createSession(params)
            try {
                installer.openSession(sessionId).use { session ->
                    val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 30_000
                    val total = connection.contentLengthLong
                    connection.inputStream.use { input ->
                        session.openWrite("update.apk", 0, total).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var written = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) onProgress(written.toFloat() / total)
                            }
                            session.fsync(output)
                        }
                    }
                    session.commit(resultSender(context, sessionId))
                }
            } catch (e: Exception) {
                installer.abandonSession(sessionId)
                throw e
            }
        }

    private fun resultSender(context: Context, sessionId: Int) =
        PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, InstallResultReceiver::class.java).setAction(INSTALL_ACTION),
            // 설치기가 결과를 Intent에 채워 넣으므로 변경 가능해야 한다.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).intentSender

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

/** 사용자 확인이 필요하면 안드로이드의 업데이트 확인 화면을 띄운다. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            Toast.makeText(context, "설치하지 못했습니다: $message", Toast.LENGTH_LONG).show()
        }
    }
}
