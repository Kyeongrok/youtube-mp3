package com.kyeongrok.youtubemp3

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class DownloadStatus { Queued, Running, Done, Failed, Canceled }

data class DownloadItem(
    val id: String,
    val title: String,
    val url: String,
    val quality: String,
    val status: DownloadStatus = DownloadStatus.Queued,
    val progress: Float = 0f,
    val message: String = "대기 중",
)

object Downloads {
    private val mutableItems = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items = mutableItems.asStateFlow()

    fun enqueue(context: Context, title: String, url: String, quality: String) {
        mutableItems.update { it + DownloadItem(UUID.randomUUID().toString(), title, url, quality) }
        context.startForegroundService(Intent(context, DownloadService::class.java))
    }

    fun cancel(id: String) {
        val item = mutableItems.value.firstOrNull { it.id == id } ?: return
        when (item.status) {
            DownloadStatus.Queued -> change(id) { it.copy(status = DownloadStatus.Canceled, message = "취소됨") }
            DownloadStatus.Running -> Engine.cancel(id)
            else -> Unit
        }
    }

    fun clearFinished() {
        mutableItems.update { items ->
            items.filter { it.status == DownloadStatus.Queued || it.status == DownloadStatus.Running }
        }
    }

    internal fun next() = mutableItems.value.firstOrNull { it.status == DownloadStatus.Queued }

    internal fun change(id: String, transform: (DownloadItem) -> DownloadItem) {
        mutableItems.update { items -> items.map { if (it.id == id) transform(it) else it } }
    }
}

/** 화면을 꺼도 끝까지 받도록 큐를 포그라운드 서비스에서 하나씩 처리한다. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("다운로드 준비 중", null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (!running) {
            running = true
            scope.launch {
                while (true) {
                    download(Downloads.next() ?: break)
                }
                running = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun download(item: DownloadItem) {
        val workDirectory = File(cacheDir, "downloads/${item.id}").apply { mkdirs() }
        Downloads.change(item.id) { it.copy(status = DownloadStatus.Running, message = "준비 중...") }
        try {
            val file = Engine.downloadAudio(item.url, workDirectory, item.quality, item.id) { progress ->
                val percent = progress.coerceIn(0f, 100f)
                val message = if (percent >= 100f) "MP3로 변환 중..." else "다운로드 중... ${percent.toInt()}%"
                Downloads.change(item.id) { it.copy(progress = percent / 100f, message = message) }
                updateNotification(notification(item.title, percent.toInt()))
            }
            Library.save(this, file)
            Downloads.change(item.id) {
                it.copy(status = DownloadStatus.Done, progress = 1f, title = file.nameWithoutExtension, message = "완료")
            }
        } catch (e: YoutubeDL.CanceledException) {
            Downloads.change(item.id) { it.copy(status = DownloadStatus.Canceled, message = "취소됨") }
        } catch (e: Exception) {
            val reason = e.message?.lineSequence()?.lastOrNull { it.startsWith("ERROR") } ?: e.message
            Downloads.change(item.id) { it.copy(status = DownloadStatus.Failed, message = reason ?: "오류 발생") }
        } finally {
            workDirectory.deleteRecursively()
        }
    }

    private fun updateNotification(notification: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // 알림 권한이 없어도 다운로드는 계속한다.
        }
    }

    private fun notification(title: String, percent: Int?): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(title)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent ?: 0, percent == null)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID = 1
    }
}
