package com.kyeongrok.youtubemp3

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HangulTest {
    private val samples = "가나다라마바사아자차카타파하똥응가쉬방구고양이까꿍뽀뽀닭값원왜뭐쥐의괜찮아요"

    /** 손가락이 획을 따라 조금씩(약간 흔들리며) 움직이면 모든 획을 끝까지 쓸 수 있어야 한다. */
    @Test
    fun everyStrokeCanBeTraced() {
        for (ch in samples) {
            val letter = Hangul.letter(ch)
            letter.strokes.forEachIndexed { i, stroke ->
                var progress = 0f
                var d = 0f
                var wobble = 1
                while (d <= stroke.length) {
                    val p = stroke.pointAt(d)
                    val finger = p + Offset(0.02f * wobble, 0.02f * wobble)
                    wobble = -wobble
                    val reached = stroke.advance(finger, progress, 0.18f, 0.1f)
                    if (reached != null && reached > progress) progress = reached
                    d += 0.01f
                }
                assertTrue("$ch 획${i + 1} progress=$progress length=${stroke.length}", progress >= stroke.length - 0.02f)
            }
        }
    }

    /** 모든 획은 글자 칸 가장자리에 잘리지 않게 안쪽에 있어야 한다. */
    @Test
    fun strokesStayInsideTheBoard() {
        for (ch in samples) {
            Hangul.letter(ch).strokes.flatMap { it.points }.forEach {
                assertTrue("$ch $it", it.x in 0.05f..0.95f && it.y in 0.03f..0.95f)
            }
        }
    }

    @Test
    fun jamoCounts() {
        assertEquals(7, Hangul.letter('똥').strokes.size) // ㄷㄷ(4) ㅗ(2) ㅇ(1)
    }
}
