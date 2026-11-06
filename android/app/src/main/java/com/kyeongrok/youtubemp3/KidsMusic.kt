package com.kyeongrok.youtubemp3

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * 따라 쓰기 화면의 배경음악. 음원 파일 없이 오르골 소리로 「작은 별」(저작권이 끝난 동요)을 만들어 되풀이한다.
 * 말소리(TTS)가 잘 들리도록 작게 튼다.
 */
class KidsMusic {
    private var track: AudioTrack? = null
    private var playing = false

    /** 소리를 만든다. 조금 걸리므로 백그라운드에서 부른다. */
    fun prepare() {
        if (track != null) return
        val samples = render()
        val built = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
        built.write(samples, 0, samples.size)
        built.setLoopPoints(0, samples.size, -1)
        built.setVolume(VOLUME)
        synchronized(this) {
            track = built
            if (playing) built.play()
        }
    }

    @Synchronized
    fun play() {
        playing = true
        track?.play()
    }

    @Synchronized
    fun pause() {
        playing = false
        track?.pause()
    }

    @Synchronized
    fun release() {
        playing = false
        track?.release()
        track = null
    }

    companion object {
        private const val RATE = 22050
        private const val BEAT = 0.42
        private const val VOLUME = 0.35f
        private const val PREFS = "kidsMusic"
        private const val KEY_ON = "on"

        /** 켜고 끄기. 한글·숫자 화면이 같이 쓴다. */
        var enabled by mutableStateOf(true)
            private set

        fun load(context: Context) {
            enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ON, true)
        }

        fun toggle(context: Context) {
            enabled = !enabled
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ON, enabled).apply()
        }

        // 도=0 기준 반음 수. 한 마디에 4박, 마지막 음은 2박.
        private val MELODY = listOf(
            0, 0, 7, 7, 9, 9, 7, -1, 5, 5, 4, 4, 2, 2, 0, -1,
            7, 7, 5, 5, 4, 4, 2, -1, 7, 7, 5, 5, 4, 4, 2, -1,
            0, 0, 7, 7, 9, 9, 7, -1, 5, 5, 4, 4, 2, 2, 0, -1,
        )
        // 두 박마다 하나씩 치는 낮은 음.
        private val BASS = listOf(
            0, 0, 5, 0, 5, 0, 7, 0,
            0, 5, 0, 7, 0, 5, 0, 7,
            0, 0, 5, 0, 5, 0, 7, 0,
        )

        private fun frequency(semitone: Int, octave: Int) = 261.63 * 2.0.pow(octave + semitone / 12.0)

        private fun render(): ShortArray {
            val beatSamples = (BEAT * RATE).toInt()
            val total = beatSamples * MELODY.size
            val mix = DoubleArray(total)
            fun note(start: Int, length: Int, hz: Double, gain: Double, decay: Double) {
                for (i in 0 until minOf(length + RATE / 2, total - start)) {
                    val t = i.toDouble() / RATE
                    // 오르골처럼 바로 울리고 서서히 사라진다. 배음을 조금 섞는다.
                    val envelope = exp(-t * decay) * minOf(1.0, t * 400)
                    val wave = sin(2 * PI * hz * t) + 0.3 * sin(4 * PI * hz * t) + 0.1 * sin(6 * PI * hz * t)
                    mix[start + i] += wave * envelope * gain
                }
            }
            MELODY.forEachIndexed { index, semitone ->
                // -1은 앞 음을 늘인 자리다.
                if (semitone >= 0) note(index * beatSamples, beatSamples, frequency(semitone, 1), 0.5, 4.0)
            }
            BASS.forEachIndexed { index, semitone ->
                note(index * 2 * beatSamples, 2 * beatSamples, frequency(semitone, -1), 0.35, 2.5)
            }
            // 되풀이할 때 끝에 남은 울림이 튀지 않게 마지막을 살짝 줄인다.
            val fade = RATE / 20
            val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1e-6)
            return ShortArray(total) { i ->
                val edge = minOf(1.0, (total - i).toDouble() / fade)
                (mix[i] / peak * edge * 0.9 * Short.MAX_VALUE).toInt().toShort()
            }
        }
    }
}
