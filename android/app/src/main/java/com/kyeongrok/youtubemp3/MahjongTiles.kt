package com.kyeongrok.youtubemp3

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

// 상하이와 리치마작이 같이 쓰는 마작 패 그림.

internal val MjTileFace = Color(0xFFF7F1E1)
internal val MjTileEdge = Color(0xFFB9A67E)
internal val MjTileLine = Color(0xFF7A6A4A)
internal val MjInkRed = Color(0xFFB71C1C)
internal val MjInkBlue = Color(0xFF1565C0)
internal val MjInkGreen = Color(0xFF2E7D32)
internal val MjInkBlack = Color(0xFF212121)

private val NUMERALS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")
private val HONORS = listOf("東", "南", "西", "北", "中", "發", "白")
private val KOREAN_NUMBERS = listOf("일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
private val HONOR_NAMES = listOf("동풍", "남풍", "서풍", "북풍", "중", "발", "백")

/** 탭한 패를 아래에 보여 줄 이름. 예: 삼만, 칠통, 구삭, 동풍. */
internal fun tileName(face: Int): String = when (face / 9) {
    0 -> "${KOREAN_NUMBERS[face % 9]}만"
    1 -> "${KOREAN_NUMBERS[face % 9]}통"
    2 -> "${KOREAN_NUMBERS[face % 9]}삭"
    else -> HONOR_NAMES[face - 27]
}

// 숫자패의 무늬를 줄마다 몇 개씩 놓을지 정한다.
private val PIP_ROWS = listOf(
    listOf(1),
    listOf(1, 1),
    listOf(1, 1, 1),
    listOf(2, 2),
    listOf(2, 1, 2),
    listOf(2, 2, 2),
    listOf(2, 3, 2),
    listOf(3, 2, 3),
    listOf(3, 3, 3),
)

/** 패 무늬 하나. face는 0..33 종류이고 red면 적도라처럼 무늬를 빨갛게 그린다. */
@Composable
internal fun MahjongFace(face: Int, height: Dp, red: Boolean = false) {
    when (face / 9) {
        0 -> NumberFace(face % 9, height)
        1 -> PipFace(face % 9, if (red) MjInkRed else MjInkBlue, round = true)
        2 -> PipFace(face % 9, if (red) MjInkRed else MjInkGreen, round = false)
        else -> HonorFace(face - 27, height)
    }
}

@Composable
internal fun NumberFace(number: Int, height: Dp) {
    val fontSize = with(LocalDensity.current) { (height * 0.3f).toSp() }
    Text(
        "${NUMERALS[number]}\n萬",
        color = MjInkRed,
        fontSize = fontSize,
        lineHeight = fontSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun HonorFace(honor: Int, height: Dp) {
    Text(
        HONORS[honor],
        color = when (HONORS[honor]) {
            "中" -> MjInkRed
            "發" -> MjInkGreen
            else -> MjInkBlack
        },
        fontSize = with(LocalDensity.current) { (height * 0.5f).toSp() },
        fontWeight = FontWeight.Bold,
    )
}

/** 통수는 동그라미, 삭수는 세로 막대로 무늬를 그린다. */
@Composable
internal fun PipFace(number: Int, color: Color, round: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val rows = PIP_ROWS[number]
        val pip = minOf(size.width / 3, size.height / rows.size) * 0.8f
        rows.forEachIndexed { row, count ->
            val y = size.height * (row + 0.5f) / rows.size
            for (column in 0 until count) {
                val x = size.width * (column + 0.5f) / count
                if (round) {
                    drawCircle(color, pip / 2, Offset(x, y))
                } else {
                    drawLine(
                        color,
                        Offset(x, y - pip / 2),
                        Offset(x, y + pip / 2),
                        strokeWidth = pip / 3,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

