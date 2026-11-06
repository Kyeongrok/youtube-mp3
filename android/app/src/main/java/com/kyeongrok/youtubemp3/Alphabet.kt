package com.kyeongrok.youtubemp3

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 알파벳 대문자·소문자 따라 쓰기. 글자 모양은 0~1 칸 기준 획이다.
 * 소문자 칸은 위에서부터 0 = 키 큰 글자 꼭대기, 0.42 = 작은 글자 꼭대기, 0.8 = 밑줄, 1 = 꼬리 끝이다.
 */
object Alphabet {
    private fun line(vararg points: Float) = points.toList().chunked(2).map { it[0] to it[1] }

    /** 각도가 커지면 화면에서 반시계로 돈다(0도 = 오른쪽, 90도 = 위). */
    private fun arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Float, to: Float): List<Pair<Float, Float>> {
        val steps = (abs(to - from) / 9).toInt().coerceAtLeast(4)
        return (0..steps).map { step ->
            val angle = (from + (to - from) * step / steps) * PI / 180
            (cx + rx * cos(angle)).toFloat() to (cy - ry * sin(angle)).toFloat()
        }
    }

    private fun v(x: Float, top: Float = 0f, bottom: Float = 1f) = line(x, top, x, bottom)
    private fun h(x1: Float, x2: Float, y: Float) = line(x1, y, x2, y)

    private val UPPER: List<List<List<Pair<Float, Float>>>> = listOf(
        listOf(line(0.5f, 0f, 0.05f, 1f), line(0.5f, 0f, 0.95f, 1f), h(0.25f, 0.75f, 0.62f)), // A
        listOf(
            v(0.12f),
            line(0.12f, 0f, 0.55f, 0f) + arc(0.55f, 0.25f, 0.3f, 0.25f, 90f, -90f) +
                line(0.12f, 0.5f, 0.6f, 0.5f) + arc(0.6f, 0.75f, 0.32f, 0.25f, 90f, -90f) + line(0.12f, 1f),
        ), // B
        listOf(arc(0.55f, 0.5f, 0.45f, 0.5f, 45f, 315f)), // C
        listOf(v(0.12f), line(0.12f, 0f, 0.45f, 0f) + arc(0.45f, 0.5f, 0.45f, 0.5f, 90f, -90f) + line(0.12f, 1f)), // D
        listOf(v(0.15f), h(0.15f, 0.9f, 0f), h(0.15f, 0.8f, 0.5f), h(0.15f, 0.9f, 1f)), // E
        listOf(v(0.15f), h(0.15f, 0.9f, 0f), h(0.15f, 0.8f, 0.5f)), // F
        listOf(arc(0.52f, 0.5f, 0.45f, 0.5f, 45f, 345f), line(0.55f, 0.58f, 0.95f, 0.58f, 0.95f, 0.88f)), // G
        listOf(v(0.12f), v(0.88f), h(0.12f, 0.88f, 0.5f)), // H
        listOf(v(0.5f), h(0.2f, 0.8f, 0f), h(0.2f, 0.8f, 1f)), // I
        listOf(line(0.75f, 0f, 0.75f, 0.7f) + arc(0.45f, 0.7f, 0.3f, 0.3f, 0f, -180f)), // J
        listOf(v(0.15f), line(0.85f, 0f, 0.15f, 0.58f), line(0.38f, 0.4f, 0.88f, 1f)), // K
        listOf(line(0.15f, 0f, 0.15f, 1f, 0.85f, 1f)), // L
        listOf(v(0.08f), line(0.08f, 0f, 0.5f, 0.65f, 0.92f, 0f), v(0.92f)), // M
        listOf(v(0.12f), line(0.12f, 0f, 0.88f, 1f), line(0.88f, 1f, 0.88f, 0f)), // N
        listOf(arc(0.5f, 0.5f, 0.45f, 0.5f, 90f, 450f)), // O
        listOf(v(0.12f), line(0.12f, 0f, 0.5f, 0f) + arc(0.5f, 0.27f, 0.36f, 0.27f, 90f, -90f) + line(0.12f, 0.54f)), // P
        listOf(arc(0.5f, 0.5f, 0.45f, 0.5f, 90f, 450f), line(0.58f, 0.68f, 0.95f, 1.04f)), // Q
        listOf(
            v(0.12f),
            line(0.12f, 0f, 0.5f, 0f) + arc(0.5f, 0.27f, 0.36f, 0.27f, 90f, -90f) + line(0.12f, 0.54f),
            line(0.45f, 0.54f, 0.9f, 1f),
        ), // R
        listOf(arc(0.5f, 0.25f, 0.38f, 0.25f, 30f, 270f) + arc(0.5f, 0.75f, 0.4f, 0.25f, 90f, -150f)), // S
        listOf(h(0.05f, 0.95f, 0f), v(0.5f)), // T
        listOf(line(0.12f, 0f, 0.12f, 0.6f) + arc(0.5f, 0.6f, 0.38f, 0.4f, 180f, 360f) + line(0.88f, 0f)), // U
        listOf(line(0.05f, 0f, 0.5f, 1f, 0.95f, 0f)), // V
        listOf(line(0f, 0f, 0.25f, 1f, 0.5f, 0.3f, 0.75f, 1f, 1f, 0f)), // W
        listOf(line(0.1f, 0f, 0.9f, 1f), line(0.9f, 0f, 0.1f, 1f)), // X
        listOf(line(0.08f, 0f, 0.5f, 0.5f), line(0.92f, 0f, 0.5f, 0.5f, 0.5f, 1f)), // Y
        listOf(line(0.1f, 0f, 0.9f, 0f, 0.1f, 1f, 0.9f, 1f)), // Z
    )

    // 작은 글자의 몸통(동그라미) 자리.
    private const val MID = 0.61f
    private const val RY = 0.19f

    private val LOWER: List<List<List<Pair<Float, Float>>>> = listOf(
        listOf(arc(0.45f, MID, 0.3f, RY, 30f, 390f), v(0.75f, 0.42f, 0.8f)), // a
        listOf(v(0.2f, 0f, 0.8f), arc(0.48f, MID, 0.28f, RY, 180f, 540f)), // b
        listOf(arc(0.5f, MID, 0.3f, RY, 40f, 320f)), // c
        listOf(arc(0.45f, MID, 0.3f, RY, 30f, 390f), v(0.75f, 0f, 0.8f)), // d
        listOf(line(0.2f, MID, 0.8f, MID) + arc(0.5f, MID, 0.3f, RY, 0f, 320f)), // e
        listOf(arc(0.65f, 0.14f, 0.2f, 0.14f, 20f, 180f) + line(0.45f, 0.8f), h(0.25f, 0.7f, 0.42f)), // f
        listOf(arc(0.45f, MID, 0.3f, RY, 30f, 390f), line(0.75f, 0.42f, 0.75f, 0.9f) + arc(0.5f, 0.9f, 0.25f, 0.1f, 0f, -180f)), // g
        listOf(v(0.2f, 0f, 0.8f), arc(0.48f, MID, 0.28f, RY, 180f, 0f) + line(0.76f, 0.8f)), // h
        listOf(v(0.5f, 0.42f, 0.8f), v(0.5f, 0.2f, 0.25f)), // i
        listOf(line(0.55f, 0.42f, 0.55f, 0.9f) + arc(0.35f, 0.9f, 0.2f, 0.1f, 0f, -180f), v(0.55f, 0.2f, 0.25f)), // j
        listOf(v(0.2f, 0f, 0.8f), line(0.75f, 0.42f, 0.2f, 0.66f), line(0.4f, 0.58f, 0.8f, 0.8f)), // k
        listOf(v(0.5f, 0f, 0.8f)), // l
        listOf(
            v(0.1f, 0.42f, 0.8f),
            arc(0.3f, 0.6f, 0.2f, 0.18f, 180f, 0f) + line(0.5f, 0.8f),
            arc(0.7f, 0.6f, 0.2f, 0.18f, 180f, 0f) + line(0.9f, 0.8f),
        ), // m
        listOf(v(0.2f, 0.42f, 0.8f), arc(0.48f, MID, 0.28f, RY, 180f, 0f) + line(0.76f, 0.8f)), // n
        listOf(arc(0.5f, MID, 0.3f, RY, 90f, 450f)), // o
        listOf(v(0.2f, 0.42f, 1f), arc(0.48f, MID, 0.28f, RY, 180f, -180f)), // p
        listOf(arc(0.45f, MID, 0.3f, RY, 30f, 390f), v(0.75f, 0.42f, 1f)), // q
        listOf(v(0.25f, 0.42f, 0.8f), arc(0.5f, 0.62f, 0.25f, 0.18f, 180f, 30f)), // r
        listOf(arc(0.5f, 0.515f, 0.24f, 0.095f, 30f, 270f) + arc(0.5f, 0.705f, 0.26f, 0.095f, 90f, -150f)), // s
        listOf(v(0.45f, 0.12f, 0.8f), h(0.25f, 0.7f, 0.42f)), // t
        listOf(line(0.2f, 0.42f, 0.2f, 0.62f) + arc(0.48f, 0.62f, 0.28f, 0.18f, 180f, 360f), v(0.76f, 0.42f, 0.8f)), // u
        listOf(line(0.15f, 0.42f, 0.5f, 0.8f, 0.85f, 0.42f)), // v
        listOf(line(0.05f, 0.42f, 0.27f, 0.8f, 0.5f, 0.5f, 0.73f, 0.8f, 0.95f, 0.42f)), // w
        listOf(line(0.2f, 0.42f, 0.8f, 0.8f), line(0.8f, 0.42f, 0.2f, 0.8f)), // x
        listOf(line(0.15f, 0.42f, 0.5f, 0.8f), line(0.85f, 0.42f, 0.3f, 1f)), // y
        listOf(line(0.2f, 0.42f, 0.8f, 0.42f, 0.2f, 0.8f, 0.8f, 0.8f)), // z
    )

    private fun place(shape: List<List<Pair<Float, Float>>>, top: Float, height: Float) = shape.map { stroke ->
        HangulStroke(stroke.map { (x, y) -> Offset(0.2f + x * 0.6f, top + y * height) })
    }

    val UPPERCASE: List<HangulLetter> = UPPER.mapIndexed { i, shape -> HangulLetter(('A' + i).toString(), place(shape, 0.12f, 0.76f)) }
    val LOWERCASE: List<HangulLetter> = LOWER.mapIndexed { i, shape -> HangulLetter(('a' + i).toString(), place(shape, 0.06f, 0.88f)) }

    /** 한글로 적은 이름. */
    val NAMES = listOf(
        "에이", "비", "씨", "디", "이", "에프", "지", "에이치", "아이", "제이", "케이", "엘", "엠",
        "엔", "오", "피", "큐", "알", "에스", "티", "유", "브이", "더블유", "엑스", "와이", "지(제트)",
    )
}
