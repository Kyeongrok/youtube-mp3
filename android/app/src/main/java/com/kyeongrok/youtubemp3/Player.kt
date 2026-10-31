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
)

object Player {
    private var mediaPlayer: MediaPlayer? = null
    private val mutableState = MutableStateFlow(PlayerState())
    val state = mutableState.asStateFlow()

    fun play(context: Context, track: Track) {
        release()
        val player = MediaPlayer.create(context.applicationContext, track.uri) ?: return
        player.setOnCompletionListener { mutableState.value = mutableState.value.copy(isPlaying = false, positionMs = 0) }
        player.start()
        mediaPlayer = player
        mutableState.value = PlayerState(track, isPlaying = true, durationMs = player.duration)
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
            mutableState.value = PlayerState()
        }
    }

    private fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
