package com.kyeongrok.youtubemp3

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 1부터 20까지 따라 쓰기. 숫자 하나는 0~1 칸에 그린 획이고, 두 자리면 나란히 놓는다. */
object Numbers {
    private fun line(vararg points: Float) = points.toList().chunked(2).map { it[0] to it[1] }

    /** 각도는 수학 방향(반시계)이고 화면은 y가 아래로 커진다. 각도가 커지면 화면에서 반시계로 돈다. */
    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Float, to: Float): List<Pair<Float, Float>> {
        val steps = (kotlin.math.abs(to - from) / 9).toInt().coerceAtLeast(4)
        return (0..steps).map { step ->
            val angle = (from + (to - from) * step / steps) * PI / 180
            (cx + rx * cos(angle)).toFloat() to (cy - ry * sin(angle)).toFloat()
        }
    }

    // 숫자 칸(0~1) 안의 획. 학교에서 흔히 가르치는 순서와 방향이다.
    private val DIGITS: List<List<List<Pair<Float, Float>>>> = listOf(
        // 0: 위에서 시작해 왼쪽으로 돈다.
        listOf(arc(0.5f, 0.5f, 0.45f, 0.48f, 90f, 450f)),
        // 1
        listOf(line(0.2f, 0.22f, 0.55f, 0f, 0.55f, 1f)),
        // 2
        listOf(arc(0.5f, 0.3f, 0.4f, 0.28f, 160f, -30f) + line(0.05f, 1f, 0.95f, 1f)),
        // 3: 위 고리와 아래 고리를 한 번에.
        listOf(arc(0.48f, 0.26f, 0.36f, 0.24f, 150f, -90f) + arc(0.48f, 0.75f, 0.42f, 0.25f, 90f, -150f)),
        // 4
        listOf(line(0.62f, 0f, 0.05f, 0.7f, 0.95f, 0.7f), line(0.68f, 0.35f, 0.68f, 1f)),
        // 5: 세로를 내리고 배를 그린 뒤 위 가로를 긋는다.
        listOf(line(0.22f, 0f, 0.18f, 0.45f) + arc(0.5f, 0.69f, 0.4f, 0.31f, 135f, -150f), line(0.22f, 0f, 0.9f, 0f)),
        // 6: 위에서 내려와 아래에서 동그라미.
        listOf(arc(0.78f, 0.72f, 0.66f, 0.7f, 87f, 180f) + arc(0.5f, 0.72f, 0.38f, 0.27f, 180f, 540f)),
        // 7
        listOf(line(0.05f, 0f, 0.95f, 0f, 0.4f, 1f)),
        // 8: 위 고리 반쯤 → 아래 고리 한 바퀴 → 위 고리 나머지.
        listOf(
            arc(0.5f, 0.25f, 0.32f, 0.23f, 30f, 270f) +
                arc(0.5f, 0.73f, 0.38f, 0.25f, 90f, -270f) +
                arc(0.5f, 0.25f, 0.32f, 0.23f, 270f, 390f),
        ),
        // 9: 동그라미를 돌고 그대로 내려 긋는다.
        listOf(arc(0.5f, 0.3f, 0.38f, 0.28f, 0f, 360f) + line(0.88f, 1f)),
    )

    private fun place(digit: Int, left: Float, width: Float) = DIGITS[digit].map { stroke ->
        HangulStroke(stroke.map { (x, y) -> Offset(left + x * width, 0.14f + y * 0.72f) })
    }

    val LETTERS: List<HangulLetter> = (1..20).map { n ->
        val strokes = if (n < 10) {
            place(n, 0.3f, 0.4f)
        } else {
            place(n / 10, 0.1f, 0.34f) + place(n % 10, 0.56f, 0.34f)
        }
        HangulLetter(n.toString(), strokes)
    }

    private val SINO = listOf("일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
    private val NATIVE = listOf("하나", "둘", "셋", "넷", "다섯", "여섯", "일곱", "여덟", "아홉")

    /** "십이 · 열둘"처럼 두 가지 읽는 법. */
    val READINGS: List<String> = (1..20).map { n ->
        val sino = when {
            n < 10 -> SINO[n - 1]
            n == 10 -> "십"
            n < 20 -> "십" + SINO[n - 11]
            else -> "이십"
        }
        val native = when {
            n < 10 -> NATIVE[n - 1]
            n == 10 -> "열"
            n < 20 -> "열" + NATIVE[n - 11]
            else -> "스물"
        }
        "$sino · $native"
    }
}
