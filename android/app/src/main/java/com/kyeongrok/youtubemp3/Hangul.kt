package com.kyeongrok.youtubemp3

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 한 획이다. 점들을 차례로 잇는 꺾은선이고, 좌표는 글자 칸을 0~1로 본 값이다. */
class HangulStroke(val points: List<Offset>) {
    private val lengths: List<Float> = buildList {
        add(0f)
        for (i in 1 until points.size) add(last() + (points[i] - points[i - 1]).getDistance())
    }

    val length get() = lengths.last()

    /** 처음부터 [distance]만큼 따라간 곳이다. */
    fun pointAt(distance: Float): Offset {
        val d = distance.coerceIn(0f, length)
        for (i in 1 until points.size) {
            if (d <= lengths[i] || i == points.size - 1) {
                val span = lengths[i] - lengths[i - 1]
                val t = if (span == 0f) 0f else (d - lengths[i - 1]) / span
                return points[i - 1] + (points[i] - points[i - 1]) * t
            }
        }
        return points.last()
    }

    /** [distance]까지의 꼭짓점들이다. 그리기에 쓴다. */
    fun pointsUntil(distance: Float): List<Offset> {
        val d = distance.coerceIn(0f, length)
        val result = mutableListOf(points.first())
        for (i in 1 until points.size) {
            if (lengths[i] < d) result += points[i] else break
        }
        result += pointAt(d)
        return result
    }

    /**
     * 손가락이 [from]부터 [window]만큼 앞쪽 획 위에 있으면 그 자리까지의 거리를, 아니면 null을 돌려준다.
     * 앞쪽만 보므로 획을 건너뛰거나 거꾸로 쓸 수 없다.
     */
    fun advance(finger: Offset, from: Float, window: Float, tolerance: Float): Float? {
        val end = (from + window).coerceAtMost(length)
        var best: Float? = null
        var bestGap = tolerance
        var s = from
        while (s <= end + 1e-4f) {
            val gap = (pointAt(s) - finger).getDistance()
            if (gap < bestGap) {
                bestGap = gap
                best = s
            }
            s += 0.005f
        }
        return best
    }
}

class HangulLetter(val text: String, val strokes: List<HangulStroke>)

object Hangul {
    private const val LEFT = 0.08f
    private const val TOP = 0.15f
    private const val WIDTH = 0.46f
    private const val HEIGHT = 0.7f

    /** 자음 칸(0~1)의 좌표를 글자 칸 왼쪽 자리로 옮긴다. */
    private fun consonant(vararg strokes: List<Pair<Float, Float>>) = strokes.map { stroke ->
        HangulStroke(stroke.map { (x, y) -> Offset(LEFT + x * WIDTH, TOP + y * HEIGHT) })
    }

    private fun line(vararg points: Float) = points.toList().chunked(2).map { it[0] to it[1] }

    /** 위에서 시작해 왼쪽으로 도는 동그라미다. */
    private fun circle(cx: Float, cy: Float, rx: Float, ry: Float) = (0..40).map { step ->
        val t = 2 * PI * step / 40
        (cx - rx * sin(t)).toFloat() to (cy - ry * cos(t)).toFloat()
    }

    private val A = listOf(
        HangulStroke(listOf(Offset(0.72f, 0.08f), Offset(0.72f, 0.92f))),
        HangulStroke(listOf(Offset(0.72f, 0.5f), Offset(0.9f, 0.5f))),
    )

    private val CONSONANTS = listOf(
        "ㄱ" to consonant(line(0f, 0.05f, 1f, 0.05f, 1f, 1f)),
        "ㄴ" to consonant(line(0f, 0f, 0f, 0.95f, 1f, 0.95f)),
        "ㄷ" to consonant(line(0f, 0.05f, 1f, 0.05f), line(0f, 0.05f, 0f, 0.95f, 1f, 0.95f)),
        "ㄹ" to consonant(
            line(0f, 0.05f, 1f, 0.05f, 1f, 0.5f),
            line(0f, 0.5f, 1f, 0.5f),
            line(0f, 0.5f, 0f, 0.95f, 1f, 0.95f),
        ),
        "ㅁ" to consonant(
            line(0f, 0.05f, 0f, 0.95f),
            line(0f, 0.05f, 1f, 0.05f, 1f, 0.95f),
            line(0f, 0.95f, 1f, 0.95f),
        ),
        "ㅂ" to consonant(
            line(0f, 0f, 0f, 0.95f),
            line(1f, 0f, 1f, 0.95f),
            line(0f, 0.45f, 1f, 0.45f),
            line(0f, 0.95f, 1f, 0.95f),
        ),
        "ㅅ" to consonant(line(0.5f, 0f, 0f, 1f), line(0.32f, 0.38f, 1f, 1f)),
        "ㅇ" to consonant(circle(0.5f, 0.5f, 0.48f, 0.43f)),
        "ㅈ" to consonant(line(0f, 0.05f, 0.95f, 0.05f, 0.05f, 1f), line(0.545f, 0.48f, 1f, 1f)),
        "ㅊ" to consonant(
            line(0.5f, -0.12f, 0.5f, 0.03f),
            line(0f, 0.22f, 0.95f, 0.22f, 0.05f, 1f),
            line(0.545f, 0.57f, 1f, 1f),
        ),
        "ㅋ" to consonant(line(0f, 0.05f, 1f, 0.05f, 1f, 1f), line(0f, 0.5f, 1f, 0.5f)),
        "ㅌ" to consonant(
            line(0f, 0.05f, 1f, 0.05f),
            line(0f, 0.5f, 1f, 0.5f),
            line(0f, 0.05f, 0f, 0.95f, 1f, 0.95f),
        ),
        "ㅍ" to consonant(
            line(0f, 0.05f, 1f, 0.05f),
            line(0.25f, 0.05f, 0.25f, 0.95f),
            line(0.75f, 0.05f, 0.75f, 0.95f),
            line(0f, 0.95f, 1f, 0.95f),
        ),
        "ㅎ" to consonant(
            line(0.5f, -0.12f, 0.5f, 0.03f),
            line(0f, 0.22f, 1f, 0.22f),
            circle(0.5f, 0.72f, 0.42f, 0.28f),
        ),
    )

    val LETTERS: List<HangulLetter> = CONSONANTS.mapIndexed { index, (_, strokes) ->
        HangulLetter("가나다라마바사아자차카타파하"[index].toString(), strokes + A)
    }

    /** 각 글자의 자음이다. 획 순서 안내에 쓴다. */
    val JAMO: List<String> = CONSONANTS.map { it.first }
}
