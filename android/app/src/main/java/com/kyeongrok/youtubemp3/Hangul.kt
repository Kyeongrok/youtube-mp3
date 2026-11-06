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

private typealias Shape = List<List<Pair<Float, Float>>>

/** 글자 칸 안의 네모 자리. 자모 하나를 이 안에 그린다. */
private class JamoBox(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * 자모 획을 조합해 아무 한글 글자나 따라 쓸 수 있게 만든다.
 * 자모 모양은 0~1 칸 기준이고, 모음 모양(세로·가로·섞임)과 받침 유무에 따라 놓을 자리를 정한다.
 */
object Hangul {
    private fun line(vararg points: Float) = points.toList().chunked(2).map { it[0] to it[1] }

    /** 위에서 시작해 왼쪽으로 도는 동그라미다. */
    private fun circle(cx: Float, cy: Float, rx: Float, ry: Float) = (0..40).map { step ->
        val t = 2 * PI * step / 40
        (cx - rx * sin(t)).toFloat() to (cy - ry * cos(t)).toFloat()
    }

    private val CONSONANT_SHAPES: Map<Char, Shape> = mapOf(
        'ㄱ' to listOf(line(0f, 0.05f, 1f, 0.05f, 1f, 1f)),
        'ㄴ' to listOf(line(0f, 0f, 0f, 0.95f, 1f, 0.95f)),
        'ㄷ' to listOf(line(0f, 0.05f, 1f, 0.05f), line(0f, 0.05f, 0f, 0.95f, 1f, 0.95f)),
        'ㄹ' to listOf(
            line(0f, 0.05f, 1f, 0.05f, 1f, 0.5f),
            line(0f, 0.5f, 1f, 0.5f),
            line(0f, 0.5f, 0f, 0.95f, 1f, 0.95f),
        ),
        'ㅁ' to listOf(
            line(0f, 0.05f, 0f, 0.95f),
            line(0f, 0.05f, 1f, 0.05f, 1f, 0.95f),
            line(0f, 0.95f, 1f, 0.95f),
        ),
        'ㅂ' to listOf(
            line(0f, 0f, 0f, 0.95f),
            line(1f, 0f, 1f, 0.95f),
            line(0f, 0.45f, 1f, 0.45f),
            line(0f, 0.95f, 1f, 0.95f),
        ),
        'ㅅ' to listOf(line(0.5f, 0f, 0f, 1f), line(0.32f, 0.38f, 1f, 1f)),
        'ㅇ' to listOf(circle(0.5f, 0.5f, 0.48f, 0.43f)),
        'ㅈ' to listOf(line(0f, 0.05f, 0.95f, 0.05f, 0.05f, 1f), line(0.545f, 0.48f, 1f, 1f)),
        'ㅊ' to listOf(
            line(0.5f, -0.12f, 0.5f, 0.03f),
            line(0f, 0.22f, 0.95f, 0.22f, 0.05f, 1f),
            line(0.545f, 0.57f, 1f, 1f),
        ),
        'ㅋ' to listOf(line(0f, 0.05f, 1f, 0.05f, 1f, 1f), line(0f, 0.5f, 1f, 0.5f)),
        'ㅌ' to listOf(
            line(0f, 0.05f, 1f, 0.05f),
            line(0f, 0.5f, 1f, 0.5f),
            line(0f, 0.05f, 0f, 0.95f, 1f, 0.95f),
        ),
        'ㅍ' to listOf(
            line(0f, 0.05f, 1f, 0.05f),
            line(0.25f, 0.05f, 0.25f, 0.95f),
            line(0.75f, 0.05f, 0.75f, 0.95f),
            line(0f, 0.95f, 1f, 0.95f),
        ),
        'ㅎ' to listOf(
            line(0.5f, -0.12f, 0.5f, 0.03f),
            line(0f, 0.22f, 1f, 0.22f),
            circle(0.5f, 0.72f, 0.42f, 0.28f),
        ),
    )

    /** 쌍자음과 겹받침은 두 자음을 나란히 놓는다. */
    private val PAIRS: Map<Char, String> = mapOf(
        'ㄲ' to "ㄱㄱ", 'ㄸ' to "ㄷㄷ", 'ㅃ' to "ㅂㅂ", 'ㅆ' to "ㅅㅅ", 'ㅉ' to "ㅈㅈ",
        'ㄳ' to "ㄱㅅ", 'ㄵ' to "ㄴㅈ", 'ㄶ' to "ㄴㅎ", 'ㄺ' to "ㄹㄱ", 'ㄻ' to "ㄹㅁ", 'ㄼ' to "ㄹㅂ",
        'ㄽ' to "ㄹㅅ", 'ㄾ' to "ㄹㅌ", 'ㄿ' to "ㄹㅍ", 'ㅀ' to "ㄹㅎ", 'ㅄ' to "ㅂㅅ",
    )

    private fun v(x: Float) = line(x, 0f, x, 1f)
    private fun h(x1: Float, x2: Float, y: Float) = line(x1, y, x2, y)

    // 세로로 긴 모음(칸 오른쪽에 선다).
    private val TALL_VOWELS: Map<Char, Shape> = mapOf(
        'ㅏ' to listOf(v(0.3f), h(0.3f, 0.9f, 0.5f)),
        'ㅑ' to listOf(v(0.3f), h(0.3f, 0.9f, 0.36f), h(0.3f, 0.9f, 0.64f)),
        'ㅓ' to listOf(h(0.1f, 0.7f, 0.5f), v(0.7f)),
        'ㅕ' to listOf(h(0.1f, 0.7f, 0.36f), h(0.1f, 0.7f, 0.64f), v(0.7f)),
        'ㅣ' to listOf(v(0.5f)),
        'ㅐ' to listOf(v(0.25f), h(0.25f, 0.62f, 0.5f), v(0.85f)),
        'ㅒ' to listOf(v(0.25f), h(0.25f, 0.62f, 0.36f), h(0.25f, 0.62f, 0.64f), v(0.85f)),
        'ㅔ' to listOf(h(0.05f, 0.45f, 0.5f), v(0.45f), v(0.85f)),
        'ㅖ' to listOf(h(0.05f, 0.45f, 0.36f), h(0.05f, 0.45f, 0.64f), v(0.45f), v(0.85f)),
    )

    // 가로로 긴 모음(자음 아래에 눕는다).
    private val FLAT_VOWELS: Map<Char, Shape> = mapOf(
        'ㅗ' to listOf(line(0.5f, 0.1f, 0.5f, 0.6f), h(0f, 1f, 0.6f)),
        'ㅛ' to listOf(line(0.35f, 0.1f, 0.35f, 0.6f), line(0.65f, 0.1f, 0.65f, 0.6f), h(0f, 1f, 0.6f)),
        'ㅜ' to listOf(h(0f, 1f, 0.35f), line(0.5f, 0.35f, 0.5f, 0.95f)),
        'ㅠ' to listOf(h(0f, 1f, 0.35f), line(0.35f, 0.35f, 0.35f, 0.95f), line(0.65f, 0.35f, 0.65f, 0.95f)),
        'ㅡ' to listOf(h(0f, 1f, 0.5f)),
    )

    // 섞인 모음 = 가로 모음 + 세로 모음.
    private val MIXED_VOWELS: Map<Char, Pair<Char, Char>> = mapOf(
        'ㅘ' to ('ㅗ' to 'ㅏ'), 'ㅙ' to ('ㅗ' to 'ㅐ'), 'ㅚ' to ('ㅗ' to 'ㅣ'),
        'ㅝ' to ('ㅜ' to 'ㅓ'), 'ㅞ' to ('ㅜ' to 'ㅔ'), 'ㅟ' to ('ㅜ' to 'ㅣ'), 'ㅢ' to ('ㅡ' to 'ㅣ'),
    )

    private const val INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val MEDIALS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private const val FINALS = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"
    private const val FIRST_SYLLABLE = '가'
    private const val LAST_SYLLABLE = '힣'

    fun isSyllable(ch: Char) = ch in FIRST_SYLLABLE..LAST_SYLLABLE

    /** 글자를 (초성, 중성, 받침) 자모로 나눈다. 받침이 없으면 null. */
    fun split(ch: Char): Triple<Char, Char, Char?> {
        val code = ch - FIRST_SYLLABLE
        val final = FINALS[code % 28]
        return Triple(INITIALS[code / 588], MEDIALS[code % 588 / 28], final.takeIf { it != ' ' })
    }

    private fun consonantShape(jamo: Char): Shape {
        val pair = PAIRS[jamo] ?: return CONSONANT_SHAPES.getValue(jamo)
        fun squeeze(shape: Shape, left: Float) = shape.map { stroke -> stroke.map { (x, y) -> left + x * 0.44f to y } }
        return squeeze(CONSONANT_SHAPES.getValue(pair[0]), 0f) + squeeze(CONSONANT_SHAPES.getValue(pair[1]), 0.56f)
    }

    private fun place(shape: Shape, box: JamoBox) = shape.map { stroke ->
        HangulStroke(stroke.map { (x, y) -> Offset(box.left + x * box.width, box.top + y * box.height) })
    }

    fun letter(ch: Char): HangulLetter {
        val (initial, medial, final) = split(ch)
        val hasFinal = final != null
        val strokes = mutableListOf<HangulStroke>()
        val finalBox: JamoBox
        when (medial) {
            in TALL_VOWELS -> {
                strokes += place(
                    consonantShape(initial),
                    if (hasFinal) JamoBox(0.1f, 0.08f, 0.44f, 0.4f) else JamoBox(0.08f, 0.14f, 0.46f, 0.72f),
                )
                strokes += place(
                    TALL_VOWELS.getValue(medial),
                    if (hasFinal) JamoBox(0.6f, 0.04f, 0.3f, 0.56f) else JamoBox(0.6f, 0.06f, 0.32f, 0.88f),
                )
                finalBox = JamoBox(0.2f, 0.68f, 0.6f, 0.26f)
            }
            in FLAT_VOWELS -> {
                strokes += place(
                    consonantShape(initial),
                    if (hasFinal) JamoBox(0.28f, 0.06f, 0.44f, 0.26f) else JamoBox(0.25f, 0.08f, 0.5f, 0.4f),
                )
                strokes += place(
                    FLAT_VOWELS.getValue(medial),
                    if (hasFinal) JamoBox(0.1f, 0.36f, 0.8f, 0.26f) else JamoBox(0.08f, 0.56f, 0.84f, 0.36f),
                )
                finalBox = JamoBox(0.22f, 0.68f, 0.56f, 0.26f)
            }
            else -> {
                val (flat, tall) = MIXED_VOWELS.getValue(medial)
                strokes += place(
                    consonantShape(initial),
                    if (hasFinal) JamoBox(0.1f, 0.06f, 0.38f, 0.24f) else JamoBox(0.08f, 0.08f, 0.42f, 0.36f),
                )
                strokes += place(
                    FLAT_VOWELS.getValue(flat),
                    if (hasFinal) JamoBox(0.04f, 0.34f, 0.58f, 0.26f) else JamoBox(0.04f, 0.5f, 0.6f, 0.36f),
                )
                strokes += place(
                    TALL_VOWELS.getValue(tall),
                    if (hasFinal) JamoBox(0.66f, 0.02f, 0.3f, 0.6f) else JamoBox(0.66f, 0.04f, 0.3f, 0.92f),
                )
                finalBox = JamoBox(0.2f, 0.7f, 0.6f, 0.24f)
            }
        }
        if (final != null) strokes += place(consonantShape(final), finalBox)
        return HangulLetter(ch.toString(), strokes)
    }

    /** 획 순서 안내. 예: ㅍ+ㅣ+ㅇ */
    fun jamoHint(ch: Char): String {
        val (initial, medial, final) = split(ch)
        return listOfNotNull(initial, medial, final).joinToString("+")
    }

    val LETTERS: List<HangulLetter> = "가나다라마바사아자차카타파하".map(::letter)
}
