package com.kyeongrok.youtubemp3

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * 따라 쓰기 화면의 배경음악. 음원 파일 없이 오르골 소리로 저작권이 끝난 동요 여러 곡을 만들어 차례로 되풀이한다.
 * 말소리(TTS)가 잘 들리도록 작게 튼다.
 */
class KidsMusic {
    private var track: AudioTrack? = null
    private var feeder: Thread? = null
    @Volatile private var running = true
    @Volatile private var playing = false

    /** 곡을 만들고 재생 준비를 한다. 조금 걸리므로 백그라운드에서 부른다. */
    fun prepare() {
        if (track != null || !running) return
        // 곡은 틀 차례가 되었을 때 하나씩 만든다. 자바 힙 밖의 버퍼에 담아 넘긴다.
        val songs = arrayOfNulls<ByteBuffer>(SONGS.size)
        fun song(index: Int): ByteBuffer {
            val i = index % SONGS.size
            val buffer = songs[i] ?: run {
                val samples = render(SONGS[i])
                ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
                    asShortBuffer().put(samples)
                }.also { songs[i] = it }
            }
            return buffer.duplicate().apply { position(0) }
        }
        val minBuffer = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
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
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer, RATE))
            .build()
        built.setVolume(VOLUME)
        synchronized(this) {
            if (!running) {
                built.release()
                return
            }
            track = built
            if (playing) built.play()
        }
        // 곡을 하나씩 흘려 넣는다. 화면(메인 스레드)이 막히지 않도록 기다리지 않는 쓰기만 하고,
        // 멈춘 동안이나 버퍼가 찼을 때는 이 스레드가 잠깐 쉰다. 다 쓰면 이 스레드가 스스로 정리한다.
        feeder = Thread {
            var index = 0
            var song = song(0)
            try {
                while (running) {
                    if (!playing) {
                        Thread.sleep(100)
                        continue
                    }
                    val written = built.write(song, song.remaining(), AudioTrack.WRITE_NON_BLOCKING)
                    if (written < 0) break
                    if (!song.hasRemaining()) {
                        index++
                        song = song(index)
                    }
                    // 버퍼가 차 있으면 조금 쉬었다가 다시 채운다.
                    Thread.sleep(if (written == 0) 50 else 10)
                }
            } catch (_: InterruptedException) {
            } finally {
                synchronized(this) { track = null }
                built.release()
            }
        }.apply {
            isDaemon = true
            start()
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

    /** 정리는 소리를 흘려 넣는 스레드가 한다. 여기서는 멈추고 끝내라고만 알린다. */
    @Synchronized
    fun release() {
        running = false
        playing = false
        track?.pause()
        feeder?.interrupt()
        feeder = null
    }

    /** 가락은 (도=0 기준 반음, 박 수) 쌍이고, 낮은 음은 bassBeats 박마다 하나씩 친다. */
    private class Song(val beat: Double, val melody: List<Pair<Int, Double>>, val bassBeats: Double, val bass: List<Int>)

    companion object {
        private const val RATE = 16000
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

        private const val C = 0
        private const val D = 2
        private const val E = 4
        private const val F = 5
        private const val G = 7
        private const val A = 9
        private const val HIGH_C = 12

        private fun notes(vararg pairs: Pair<Int, Double>) = pairs.toList()
        private infix fun Int.x(beats: Double) = this to beats
        private val Int.q get() = this to 1.0

        private val SONGS = listOf(
            // 작은 별
            Song(
                0.42,
                notes(
                    C.q, C.q, G.q, G.q, A.q, A.q, G x 2.0, F.q, F.q, E.q, E.q, D.q, D.q, C x 2.0,
                    G.q, G.q, F.q, F.q, E.q, E.q, D x 2.0, G.q, G.q, F.q, F.q, E.q, E.q, D x 2.0,
                    C.q, C.q, G.q, G.q, A.q, A.q, G x 2.0, F.q, F.q, E.q, E.q, D.q, D.q, C x 2.0,
                ),
                2.0,
                listOf(C, C, F, C, F, C, G, C, C, F, C, G, C, F, C, G, C, C, F, C, F, C, G, C),
            ),
            // 메리의 어린 양 (떴다 떴다 비행기)
            Song(
                0.36,
                notes(
                    E.q, D.q, C.q, D.q, E.q, E.q, E x 2.0, D.q, D.q, D x 2.0, E.q, G.q, G x 2.0,
                    E.q, D.q, C.q, D.q, E.q, E.q, E.q, E.q, D.q, D.q, E.q, D.q, C x 4.0,
                ),
                2.0,
                listOf(C, C, C, C, G, G, C, C, C, C, C, C, G, G, C, C),
            ),
            // 환희의 송가
            Song(
                0.4,
                notes(
                    E.q, E.q, F.q, G.q, G.q, F.q, E.q, D.q, C.q, C.q, D.q, E.q, E x 1.5, D x 0.5, D x 2.0,
                    E.q, E.q, F.q, G.q, G.q, F.q, E.q, D.q, C.q, C.q, D.q, E.q, D x 1.5, C x 0.5, C x 2.0,
                ),
                2.0,
                listOf(C, C, G, G, C, C, G, G, C, C, G, G, C, C, G, C),
            ),
            // 런던 다리
            Song(
                0.38,
                notes(
                    G x 1.5, A x 0.5, G.q, F.q, E.q, F.q, G x 2.0, D.q, E.q, F x 2.0, E.q, F.q, G x 2.0,
                    G x 1.5, A x 0.5, G.q, F.q, E.q, F.q, G x 2.0, D x 2.0, G x 2.0, E.q, C x 3.0,
                ),
                2.0,
                listOf(C, C, C, C, G, G, C, C, C, C, C, C, G, G, C, C),
            ),
            // 노를 저어라
            Song(
                0.36,
                notes(
                    C x 1.5, C x 1.5, C.q, D x 0.5, E x 1.5, E.q, D x 0.5, E.q, F x 0.5, G x 3.0,
                    HIGH_C x 0.5, HIGH_C x 0.5, HIGH_C x 0.5, G x 0.5, G x 0.5, G x 0.5,
                    E x 0.5, E x 0.5, E x 0.5, C x 0.5, C x 0.5, C x 0.5,
                    G.q, F x 0.5, E.q, D x 0.5, C x 3.0,
                ),
                3.0,
                listOf(C, C, C, C, C, C, G, C),
            ),
        )

        private fun frequency(semitone: Int, octave: Int) = 261.63 * 2.0.pow(octave + semitone / 12.0)

        /** 곡 하나를 소리로 만든다. 곡 끝에 두 박을 쉬어 다음 곡과 띄운다. */
        private fun render(song: Song): ShortArray {
            val beatSamples = song.beat * RATE
            val beats = song.melody.sumOf { it.second } + 2
            val total = (beats * beatSamples).toInt()
            val mix = DoubleArray(total)
            // 오르골처럼 바로 울리고 서서히 사라진다. 배음을 조금 섞는다.
            // 표본마다 sin·exp를 부르면 느려서, 회전과 곱셈만으로 파형과 줄어드는 크기를 이어 간다.
            fun note(start: Int, hz: Double, gain: Double, decay: Double, seconds: Double) {
                val length = minOf((RATE * seconds).toInt(), total - start)
                val step = 2 * PI * hz / RATE
                val c1 = cos(step); val s1 = sin(step)
                val c2 = cos(2 * step); val s2 = sin(2 * step)
                val c3 = cos(3 * step); val s3 = sin(3 * step)
                var x1 = 1.0; var y1 = 0.0
                var x2 = 1.0; var y2 = 0.0
                var x3 = 1.0; var y3 = 0.0
                val fall = exp(-decay / RATE)
                var level = gain
                val attack = RATE / 400
                for (i in 0 until length) {
                    val rise = if (i < attack) i.toDouble() / attack else 1.0
                    mix[start + i] += (y1 + 0.3 * y2 + 0.1 * y3) * level * rise
                    level *= fall
                    val nx1 = x1 * c1 - y1 * s1; y1 = x1 * s1 + y1 * c1; x1 = nx1
                    val nx2 = x2 * c2 - y2 * s2; y2 = x2 * s2 + y2 * c2; x2 = nx2
                    val nx3 = x3 * c3 - y3 * s3; y3 = x3 * s3 + y3 * c3; x3 = nx3
                }
            }
            var at = 0.0
            for ((semitone, length) in song.melody) {
                note((at * beatSamples).toInt(), frequency(semitone, 1), 0.5, 4.0, 1.0)
                at += length
            }
            song.bass.forEachIndexed { index, semitone ->
                note((index * song.bassBeats * beatSamples).toInt(), frequency(semitone, -1), 0.35, 2.5, 1.5)
            }
            val peak = mix.maxOf { abs(it) }.coerceAtLeast(1e-6)
            // 끝은 쉬는 박이라 소리가 거의 사라져 있지만, 이어 붙일 때 튀지 않게 살짝 줄인다.
            val fade = RATE / 10
            return ShortArray(total) { i ->
                val edge = minOf(1.0, (total - i).toDouble() / fade)
                (mix[i] / peak * edge * 0.9 * Short.MAX_VALUE).toInt().toShort()
            }
        }
    }
}
