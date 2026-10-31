package com.kyeongrok.youtubemp3

import android.content.Context
import android.media.MediaPlayer
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

    fun play(context: Context, track: Track) = play(context, listOf(track), 0)

    fun play(context: Context, tracks: List<Track>, index: Int) {
        appContext = context.applicationContext
        queue = tracks
        playFrom(index)
    }

    fun next() {
        val index = state.value.queueIndex + 1
        if (index < queue.size) playFrom(index)
    }

    fun previous() {
        val index = state.value.queueIndex - 1
        if (index >= 0) playFrom(index)
    }

    /** 파일이 지워졌거나 재생할 수 없으면 건너뛰고 뒤의 곡을 튼다. */
    private fun playFrom(index: Int) {
        release()
        val context = appContext ?: return
        for (position in index until queue.size) {
            val track = queue[position]
            val player = MediaPlayer.create(context, track.uri) ?: continue
            player.setOnCompletionListener { onCompleted(position) }
            player.start()
            mediaPlayer = player
            mutableState.value = PlayerState(
                track,
                isPlaying = true,
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
        }
    }

    fun togglePause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) player.pause() else player.start()
        refresh()
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
        if (state.value.track?.id == track.id) {
            release()
            queue = emptyList()
            mutableState.value = PlayerState()
        }
    }

    private fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
