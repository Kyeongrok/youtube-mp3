package com.kyeongrok.youtubemp3

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
)

object Player {
    private var mediaPlayer: MediaPlayer? = null
    private var appContext: Context? = null
    private var queue = emptyList<Track>()
    private val mutableState = MutableStateFlow(PlayerState())
    val state = mutableState.asStateFlow()

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /** 전화나 짧은 알림으로 잠깐 멈췄으면, 포커스가 돌아올 때 다시 튼다. */
    private var resumeOnGain = false

    /**
     * 다른 앱(유튜브 등)이 소리를 내기 시작하면 시스템이 알려 준다.
     * 아주 가져가면 멈추고, 잠깐이면 멈췄다가 돌아올 때 다시 튼다.
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val player = mediaPlayer ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnGain = false
                if (player.isPlaying) player.pause()
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnGain = player.isPlaying
                if (player.isPlaying) player.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player.setVolume(0.2f, 0.2f)
            AudioManager.AUDIOFOCUS_GAIN -> {
                player.setVolume(1f, 1f)
                if (resumeOnGain) player.start()
                resumeOnGain = false
            }
        }
        refresh()
    }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    /** 이어폰을 뽑거나 블루투스가 끊기면 스피커로 소리가 새지 않게 멈춘다. */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val player = mediaPlayer ?: return
            if (player.isPlaying) player.pause()
            resumeOnGain = false
            refresh()
        }
    }
    private var noisyRegistered = false

    fun play(context: Context, track: Track) = play(context, listOf(track), 0)

    fun play(context: Context, tracks: List<Track>, index: Int) {
        appContext = context.applicationContext
        queue = tracks
        playFrom(index)
        if (mediaPlayer != null) PlaybackService.start(context)
    }

    private fun audioManager() = appContext?.getSystemService(AudioManager::class.java)

    private fun requestFocus(): Boolean {
        val context = appContext ?: return false
        if (!noisyRegistered) {
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyRegistered = true
        }
        return audioManager()?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        audioManager()?.abandonAudioFocusRequest(focusRequest)
    }

    /** 마지막 곡에서 다음을 누르면 처음 곡으로, 첫 곡에서 이전을 누르면 마지막 곡으로 간다. */
    fun next() {
        if (queue.isEmpty()) return
        playFrom((state.value.queueIndex + 1) % queue.size)
    }

    fun previous() {
        if (queue.isEmpty()) return
        playFrom((state.value.queueIndex - 1 + queue.size) % queue.size)
    }

    /** 파일이 지워졌거나 재생할 수 없으면 건너뛰고 뒤의 곡을 튼다. */
    private fun playFrom(index: Int) {
        release()
        val context = appContext ?: return
        for (position in index until queue.size) {
            val track = queue[position]
            val player = MediaPlayer.create(context, track.uri, null, attributes, 0) ?: continue
            player.setOnCompletionListener { onCompleted(position) }
            mediaPlayer = player
            resumeOnGain = false
            val granted = requestFocus()
            if (granted) player.start()
            mutableState.value = PlayerState(
                track,
                isPlaying = granted,
                durationMs = player.duration,
                queueIndex = position,
                queueSize = queue.size,
            )
            return
        }
        mutableState.value = PlayerState()
    }

    private fun onCompleted(position: Int) {
        if (position + 1 < queue.size) {
            playFrom(position + 1)
        } else {
            mutableState.value = mutableState.value.copy(isPlaying = false, positionMs = 0)
            abandonFocus()
        }
    }

    fun togglePause() {
        val player = mediaPlayer ?: return
        resumeOnGain = false
        if (player.isPlaying) {
            player.pause()
        } else if (requestFocus()) {
            player.setVolume(1f, 1f)
            player.start()
        }
        refresh()
    }

    /** 잠금화면·이어폰의 재생/일시정지 버튼처럼 원하는 상태가 정해진 경우다. */
    fun togglePauseIf(playing: Boolean) {
        if (mediaPlayer?.isPlaying == playing) togglePause()
    }

    /** 알림의 닫기 버튼: 재생을 끝내고 알림도 치운다. */
    fun stop() {
        release()
        abandonFocus()
        resumeOnGain = false
        queue = emptyList()
        mutableState.value = PlayerState()
    }

    /** 지금 위치에서 deltaMs만큼 앞뒤로 옮긴다. 곡의 처음과 끝을 넘지 않는다. */
    fun seekBy(deltaMs: Int) {
        val player = mediaPlayer ?: return
        seekTo((player.currentPosition + deltaMs).coerceIn(0, player.duration))
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        refresh()
    }

    fun refresh() {
        val player = mediaPlayer ?: return
        mutableState.value = mutableState.value.copy(isPlaying = player.isPlaying, positionMs = player.currentPosition)
    }

    fun stopIfPlaying(track: Track) {
        if (state.value.track?.id == track.id) stop()
    }

    private fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
