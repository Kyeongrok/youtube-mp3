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


/**
 * 따라 쓸 글자다. [weight]는 획 굵기 배율로, 획이 많아 자모가 작아지는 글자일수록 가늘게 그려야
 * 획끼리 뭉개지지 않는다.
 */
class HangulLetter(val text: String, val strokes: List<HangulStroke>, val weight: Float = 1f)

private typealias Shape = List<List<Pair<Float, Float>>>

/** 글자 칸 안의 네모 자리. 자모 하나를 이 안에 그린다. */
private class JamoBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    /** 가로세로 비율(가로/세로)을 [min]~[max] 안으로 맞추도록 가운데를 기준으로 줄인다. */
    fun fit(min: Float, max: Float): JamoBox = when {
        width > height * max -> (height * max).let { JamoBox(left + (width - it) / 2, top, it, height) }
        width < height * min -> (width / min).let { JamoBox(left, top + (height - it) / 2, width, it) }
        else -> this
    }
}

/**
 * 자모 획을 조합해 아무 한글 글자나 따라 쓸 수 있게 만든다.
 *
 * 옛 조합형 비트맵 글꼴(8×4×4벌)처럼 같은 자음이라도 놓이는 자리에 따라 모양을 바꾼다.
 * - 세로 모음(ㅏ·ㅓ·ㅣ…) 왼쪽의 자음: 좁고 길며, ㄱ·ㅋ·ㅅ·ㅈ·ㅊ은 아래가 왼쪽으로 휘어 내려간다(가, 사, 자).
 * - 가로 모음(ㅗ·ㅜ·ㅡ…) 위의 자음과 받침: 넓고 납작한 꺾인 모양이다(고, 각).
 * 모음은 받침이 있으면 짧아지고, 받침은 글자 아래쪽을 넓게 차지한다.
 */
object Hangul {
    private fun line(vararg points: Float) = points.toList().chunked(2).map { it[0] to it[1] }

    /** 위에서 시작해 왼쪽으로 도는 동그라미다. */
    private fun circle(cx: Float, cy: Float, rx: Float, ry: Float) = (0..40).map { step ->
        val t = 2 * PI * step / 40
        (cx - rx * sin(t)).toFloat() to (cy - ry * cos(t)).toFloat()
    }

    // 넓은 자리(가로 모음 위, 받침)에 쓰는 자음. 0~1 칸 기준이다.
    private val CONSONANT_SHAPES: Map<Char, Shape> = mapOf(
        'ㄱ' to listOf(line(0f, 0f, 1f, 0f, 1f, 1f)),
        'ㄴ' to listOf(line(0f, 0f, 0f, 1f, 1f, 1f)),
        'ㄷ' to listOf(line(0f, 0f, 1f, 0f), line(0f, 0f, 0f, 1f, 1f, 1f)),
        'ㄹ' to listOf(
            line(0f, 0f, 1f, 0f, 1f, 0.5f),
            line(0f, 0.5f, 1f, 0.5f),
            line(0f, 0.5f, 0f, 1f, 1f, 1f),
        ),
        'ㅁ' to listOf(
            line(0f, 0f, 0f, 1f),
            line(0f, 0f, 1f, 0f, 1f, 1f),
            line(0f, 1f, 1f, 1f),
        ),
        'ㅂ' to listOf(
            line(0f, 0f, 0f, 1f),
            line(1f, 0f, 1f, 1f),
            line(0f, 0.48f, 1f, 0.48f),
            line(0f, 1f, 1f, 1f),
        ),
        'ㅅ' to listOf(line(0.5f, 0f, 0.3f, 0.55f, 0f, 1f), line(0.42f, 0.4f, 1f, 1f)),
        'ㅇ' to listOf(circle(0.5f, 0.5f, 0.5f, 0.5f)),
        'ㅈ' to listOf(line(0.04f, 0f, 0.96f, 0f, 0.5f, 0.5f, 0f, 1f), line(0.5f, 0.5f, 1f, 1f)),
        'ㅊ' to listOf(
            line(0.5f, 0f, 0.5f, 0.18f),
            line(0.04f, 0.3f, 0.96f, 0.3f, 0.5f, 0.65f, 0f, 1f),
            line(0.5f, 0.65f, 1f, 1f),
        ),
        'ㅋ' to listOf(line(0f, 0f, 1f, 0f, 1f, 1f), line(0f, 0.5f, 1f, 0.5f)),
        'ㅌ' to listOf(
            line(0f, 0f, 1f, 0f),
            line(0f, 0.5f, 1f, 0.5f),
            line(0f, 0f, 0f, 1f, 1f, 1f),
        ),
        'ㅍ' to listOf(
            line(0f, 0f, 1f, 0f),
            line(0.28f, 0f, 0.28f, 1f),
            line(0.72f, 0f, 0.72f, 1f),
            line(0f, 1f, 1f, 1f),
        ),
        'ㅎ' to listOf(
            line(0.5f, 0f, 0.5f, 0.16f),
            line(0f, 0.3f, 1f, 0.3f),
            circle(0.5f, 0.69f, 0.38f, 0.31f),
        ),
    )

    // 세로 모음 왼쪽에 서는 자음 가운데 모양이 달라지는 것. 아래가 왼쪽으로 비껴 내려간다.
    private val TALL_SHAPES: Map<Char, Shape> = mapOf(
        'ㄱ' to listOf(line(0f, 0.04f, 0.92f, 0.04f, 0.9f, 0.32f, 0.8f, 0.62f, 0.56f, 1f)),
        'ㅋ' to listOf(line(0f, 0.04f, 0.92f, 0.04f, 0.9f, 0.32f, 0.8f, 0.62f, 0.56f, 1f), line(0f, 0.46f, 0.84f, 0.46f)),
        'ㅅ' to listOf(line(0.55f, 0f, 0.42f, 0.5f, 0f, 1f), line(0.47f, 0.4f, 1f, 1f)),
        'ㅈ' to listOf(line(0f, 0.04f, 0.92f, 0.04f, 0.5f, 0.52f, 0f, 1f), line(0.5f, 0.52f, 1f, 1f)),
        'ㅊ' to listOf(
            line(0.48f, 0f, 0.48f, 0.16f),
            line(0f, 0.3f, 0.92f, 0.3f, 0.5f, 0.66f, 0f, 1f),
            line(0.5f, 0.66f, 1f, 1f),
        ),
    )

    /** 쌍자음과 겹받침은 두 자음을 나란히 놓는다. */
    private val PAIRS: Map<Char, String> = mapOf(
        'ㄲ' to "ㄱㄱ", 'ㄸ' to "ㄷㄷ", 'ㅃ' to "ㅂㅂ", 'ㅆ' to "ㅅㅅ", 'ㅉ' to "ㅈㅈ",
        'ㄳ' to "ㄱㅅ", 'ㄵ' to "ㄴㅈ", 'ㄶ' to "ㄴㅎ", 'ㄺ' to "ㄹㄱ", 'ㄻ' to "ㄹㅁ", 'ㄼ' to "ㄹㅂ",
        'ㄽ' to "ㄹㅅ", 'ㄾ' to "ㄹㅌ", 'ㄿ' to "ㄹㅍ", 'ㅀ' to "ㄹㅎ", 'ㅄ' to "ㅂㅅ",
    )

    private fun v(x: Float, top: Float = 0f, bottom: Float = 1f) = line(x, top, x, bottom)
    private fun h(x1: Float, x2: Float, y: Float) = line(x1, y, x2, y)

    // 세로로 긴 모음(칸 오른쪽에 선다). 기둥은 위에서 아래로, 곁줄기는 기둥에서 바깥으로 긋는다.
    private val TALL_VOWELS: Map<Char, Shape> = mapOf(
        'ㅏ' to listOf(v(0.3f), h(0.3f, 1f, 0.48f)),
        'ㅑ' to listOf(v(0.3f), h(0.3f, 1f, 0.34f), h(0.3f, 1f, 0.62f)),
        'ㅓ' to listOf(h(0f, 0.7f, 0.48f), v(0.7f)),
        'ㅕ' to listOf(h(0f, 0.7f, 0.34f), h(0f, 0.7f, 0.62f), v(0.7f)),
        'ㅣ' to listOf(v(0.5f)),
        'ㅐ' to listOf(v(0.15f, 0.06f, 0.94f), h(0.15f, 0.62f, 0.48f), v(0.9f)),
        'ㅒ' to listOf(v(0.15f, 0.06f, 0.94f), h(0.15f, 0.62f, 0.34f), h(0.15f, 0.62f, 0.62f), v(0.9f)),
        'ㅔ' to listOf(h(0f, 0.42f, 0.48f), v(0.42f, 0.06f, 0.94f), v(0.9f)),
        'ㅖ' to listOf(h(0f, 0.42f, 0.34f), h(0f, 0.42f, 0.62f), v(0.42f, 0.06f, 0.94f), v(0.9f)),
    )

    // 가로로 긴 모음(자음 아래에 눕는다). 칸 위쪽이 자음 쪽이다.
    private val FLAT_VOWELS: Map<Char, Shape> = mapOf(
        'ㅗ' to listOf(v(0.5f, 0.05f, 0.62f), h(0f, 1f, 0.62f)),
        'ㅛ' to listOf(v(0.33f, 0.05f, 0.62f), v(0.67f, 0.05f, 0.62f), h(0f, 1f, 0.62f)),
        'ㅜ' to listOf(h(0f, 1f, 0.3f), v(0.5f, 0.3f, 0.95f)),
        'ㅠ' to listOf(h(0f, 1f, 0.3f), v(0.33f, 0.3f, 0.95f), v(0.67f, 0.3f, 0.95f)),
        'ㅡ' to listOf(h(0f, 1f, 0.48f)),
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

    private fun place(shape: Shape, box: JamoBox) = shape.map { stroke ->
        HangulStroke(stroke.map { (x, y) -> Offset(box.left + x * box.width, box.top + y * box.height) })
    }

    /** 자음 하나를 자리에 맞춰 놓는다. ㅇ·ㅎ은 동그라미가 찌그러지지 않게 자리를 줄인다. */
    private fun single(jamo: Char, box: JamoBox, tall: Boolean): List<HangulStroke> {
        val shape = (if (tall) TALL_SHAPES[jamo] else null) ?: CONSONANT_SHAPES.getValue(jamo)
        val fitted = when (jamo) {
            'ㅇ' -> box.fit(0.85f, 1.2f)
            'ㅎ' -> box.fit(0.75f, 1.3f)
            else -> box.fit(0.45f, 2.6f)
        }
        return place(shape, fitted)
    }

    /** 자음을 놓는다. 쌍자음·겹받침은 두 자음을 나란히, 사이를 띄워 놓는다. */
    private fun consonant(jamo: Char, box: JamoBox, tall: Boolean): List<HangulStroke> {
        val pair = PAIRS[jamo] ?: return single(jamo, box, tall)
        val part = box.width * 0.42f
        return single(pair[0], JamoBox(box.left, box.top, part, box.height), tall) +
            single(pair[1], JamoBox(box.left + box.width - part, box.top, part, box.height), tall)
    }

    fun letter(ch: Char): HangulLetter {
        val (initial, medial, final) = split(ch)
        val hasFinal = final != null
        val doubled = initial in PAIRS || final in PAIRS
        val strokes = mutableListOf<HangulStroke>()
        val weight: Float
        when (medial) {
            in TALL_VOWELS -> {
                // 가, 각
                weight = if (hasFinal) 0.72f else 0.92f
                val narrow = medial in "ㅓㅕㅔㅖ" // 왼쪽으로 곁줄기가 나오는 모음은 자음을 좁힌다.
                strokes += if (hasFinal) {
                    consonant(initial, JamoBox(0.12f, 0.1f, if (narrow) 0.36f else 0.4f, 0.34f), tall = true)
                } else {
                    consonant(initial, JamoBox(0.1f, 0.2f, if (narrow) 0.38f else 0.42f, 0.6f), tall = true)
                }
                strokes += place(
                    TALL_VOWELS.getValue(medial),
                    if (hasFinal) JamoBox(0.6f, 0.06f, 0.3f, 0.48f) else JamoBox(0.6f, 0.08f, 0.3f, 0.84f),
                )
                if (final != null) strokes += consonant(final, JamoBox(0.2f, 0.65f, 0.6f, 0.25f), tall = false)
            }
            in FLAT_VOWELS -> {
                // 고, 공
                weight = if (hasFinal) 0.7f else 0.9f
                val wide = initial in PAIRS
                strokes += if (hasFinal) {
                    consonant(initial, if (wide) JamoBox(0.18f, 0.07f, 0.64f, 0.25f) else JamoBox(0.27f, 0.07f, 0.46f, 0.25f), tall = false)
                } else {
                    consonant(initial, if (wide) JamoBox(0.19f, 0.12f, 0.62f, 0.3f) else JamoBox(0.24f, 0.1f, 0.52f, 0.34f), tall = false)
                }
                strokes += place(
                    FLAT_VOWELS.getValue(medial),
                    if (hasFinal) JamoBox(0.1f, 0.33f, 0.8f, 0.24f) else JamoBox(0.1f, 0.5f, 0.8f, 0.38f),
                )
                if (final != null) strokes += consonant(final, JamoBox(0.2f, 0.66f, 0.6f, 0.25f), tall = false)
            }
            else -> {
                // 와, 왕
                weight = if (hasFinal) 0.62f else 0.8f
                val (flat, tall) = MIXED_VOWELS.getValue(medial)
                strokes += if (hasFinal) {
                    consonant(initial, JamoBox(0.14f, 0.07f, 0.38f, 0.22f), tall = false)
                } else {
                    consonant(initial, JamoBox(0.12f, 0.1f, 0.42f, 0.32f), tall = false)
                }
                strokes += place(
                    FLAT_VOWELS.getValue(flat),
                    if (hasFinal) JamoBox(0.07f, 0.31f, 0.58f, 0.26f) else JamoBox(0.07f, 0.47f, 0.58f, 0.4f),
                )
                strokes += place(
                    TALL_VOWELS.getValue(tall),
                    if (hasFinal) JamoBox(0.7f, 0.05f, 0.22f, 0.54f) else JamoBox(0.7f, 0.07f, 0.22f, 0.86f),
                )
                if (final != null) strokes += consonant(final, JamoBox(0.2f, 0.68f, 0.6f, 0.23f), tall = false)
            }
        }
        return HangulLetter(ch.toString(), strokes, if (doubled) weight * 0.85f else weight)
    }

    /** 획 순서 안내. 예: ㅍ+ㅣ+ㅇ */
    fun jamoHint(ch: Char): String {
        val (initial, medial, final) = split(ch)
        return listOfNotNull(initial, medial, final).joinToString("+")
    }

    val LETTERS: List<HangulLetter> = "가나다라마바사아자차카타파하".map(::letter)
}
