package com.kyeongrok.youtubemp3

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 음악을 트는 동안 앱을 포그라운드로 붙잡아 둔다. 백그라운드 앱은 시스템이 얼려 버려서
 * 다른 앱이 소리를 가져가도(오디오 포커스를 잃어도) 멈추지 못하기 때문이다.
 * 알림과 잠금화면, 이어폰 버튼으로도 재생을 다룰 수 있게 MediaSession을 연다.
 */
class PlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var session: MediaSession
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession(this, "YoutubeMp3").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = Player.togglePauseIf(playing = false)
                override fun onPause() = Player.togglePauseIf(playing = true)
                override fun onSkipToNext() = Player.next()
                override fun onSkipToPrevious() = Player.previous()
                override fun onStop() = Player.stop()
            })
            setSessionActivity(openAppIntent())
            isActive = true
        }
        scope.launch {
            Player.state
                .map { Triple(it.track, it.isPlaying, it.queueIndex) }
                .distinctUntilChanged()
                .collect { update() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService로 불렸으면 몇 초 안에 반드시 startForeground를 불러야 한다.
        if (!foreground) showForeground(Player.state.value)
        when (intent?.action) {
            ACTION_TOGGLE -> Player.togglePause()
            ACTION_NEXT -> Player.next()
            ACTION_PREVIOUS -> Player.previous()
            ACTION_STOP -> Player.stop()
        }
        return START_NOT_STICKY
    }

    private fun update() {
        val state = Player.state.value
        if (state.track == null) {
            session.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0, 0f).build())
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
            return
        }
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, state.track.title)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, state.durationMs.toLong())
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP,
                )
                .setState(
                    if (state.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    state.positionMs.toLong(),
                    if (state.isPlaying) 1f else 0f,
                )
                .build(),
        )
        showForeground(state)
    }

    private fun showForeground(state: PlayerState) {
        val notification = notification(state)
        if (foreground) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        } else {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            foreground = true
        }
    }

    private fun notification(state: PlayerState): Notification {
        fun action(icon: Int, title: String, action: String) = Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(this, icon),
            title,
            PendingIntent.getService(
                this, action.hashCode(), Intent(this, PlaybackService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(state.track?.title ?: "음악")
            .setContentText(if (state.queueSize > 1) "${state.queueIndex + 1} / ${state.queueSize}" else null)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(
                PendingIntent.getService(
                    this, 0, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_previous, "이전", ACTION_PREVIOUS))
            .addAction(
                if (state.isPlaying) action(android.R.drawable.ic_media_pause, "일시정지", ACTION_TOGGLE)
                else action(android.R.drawable.ic_media_play, "재생", ACTION_TOGGLE),
            )
            .addAction(action(android.R.drawable.ic_media_next, "다음", ACTION_NEXT))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "닫기", ACTION_STOP))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    private fun openAppIntent() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onDestroy() {
        scope.cancel()
        session.release()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_TOGGLE = "toggle"
        private const val ACTION_NEXT = "next"
        private const val ACTION_PREVIOUS = "previous"
        private const val ACTION_STOP = "stop"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PlaybackService::class.java))
        }
    }
}
