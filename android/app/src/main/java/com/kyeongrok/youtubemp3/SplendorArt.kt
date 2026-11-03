package com.kyeongrok.youtubemp3

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp

/**
 * 스플렌더 카드와 보석 그림이다. 이미지 파일 없이 도형으로 그려 앱 크기를 늘리지 않는다.
 * 좌표는 모두 그릴 칸의 너비·높이에 대한 비율이다.
 */
object SplendorArt {
    /** 보석을 그릴 때 쓰는 바탕색이다. 흰 보석과 검은 보석은 면이 보이도록 회색 쪽으로 옮긴다. */
    private val GemBases = listOf(
        Color(0xFFDDE5EA), Color(0xFF1E88E5), Color(0xFF2E9D46), Color(0xFFD32F2F), Color(0xFF4A4A4A), Color(0xFFFFB300),
    )
    private val Outline = Color(0x66000000)
    private val Scenery = Color(0x1F000000)
    private val SceneryDark = Color(0x2E000000)

    private fun DrawScope.path(points: List<Pair<Float, Float>>, origin: Offset, area: Size) = Path().apply {
        points.forEachIndexed { index, (x, y) ->
            val point = Offset(origin.x + x * area.width, origin.y + y * area.height)
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }

    /** 옆에서 본 브릴리언트 컷 보석이다. 위쪽 면은 밝게, 아래쪽 면은 어둡게 칠한다. */
    fun DrawScope.gem(color: Int, topLeft: Offset, size: Size) {
        val base = GemBases[color]
        val light = { amount: Float -> lerp(base, Color.White, amount) }
        val dark = { amount: Float -> lerp(base, Color.Black, amount) }
        val t1 = 0.28f to 0.1f
        val t2 = 0.72f to 0.1f
        val g0 = 0.02f to 0.36f
        val g1 = 0.34f to 0.36f
        val g2 = 0.66f to 0.36f
        val g3 = 0.98f to 0.36f
        val culet = 0.5f to 0.95f

        listOf(
            listOf(t1, g0, g1) to light(0.35f),
            listOf(t1, t2, g2, g1) to light(0.55f),
            listOf(t2, g3, g2) to light(0.15f),
            listOf(g0, g1, culet) to base,
            listOf(g1, g2, culet) to dark(0.12f),
            listOf(g2, g3, culet) to dark(0.3f),
        ).forEach { (points, fill) -> drawPath(path(points, topLeft, size), fill) }

        val outline = path(listOf(t1, t2, g3, culet, g0), topLeft, size)
        drawPath(outline, Outline, style = Stroke(width = size.width * 0.04f))
        // 윗면에 비친 빛.
        drawPath(path(listOf(0.36f to 0.14f, 0.5f to 0.14f, 0.42f to 0.3f), topLeft, size), Color.White.copy(alpha = 0.7f))
    }

    /** 보석 토큰이다. 가운데가 밝은 원판에 안쪽 테를 두른다. */
    fun DrawScope.coin(color: Int, alpha: Float = 1f) {
        val base = GemBases[color]
        val radius = size.minDimension / 2
        drawCircle(
            Brush.radialGradient(
                listOf(lerp(base, Color.White, 0.45f), base, lerp(base, Color.Black, 0.25f)),
                center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
                radius = radius * 1.6f,
            ),
            alpha = alpha,
        )
        drawCircle(Color.White.copy(alpha = 0.45f * alpha), radius * 0.72f, style = Stroke(width = radius * 0.1f))
    }

    /** 카드 바탕이다. 위는 밝고 아래로 갈수록 짙어지며, 단계마다 다른 풍경을 옅게 깐다. */
    fun DrawScope.cardBackground(tint: Color, tier: Int) {
        drawRect(Brush.verticalGradient(listOf(lerp(tint, Color.White, 0.5f), tint, lerp(tint, Color.Black, 0.08f))))
        when (tier) {
            0 -> mine()
            1 -> ship()
            else -> palace()
        }
    }

    private fun DrawScope.whole() = Offset.Zero to size

    /** 1단계: 산과 갱도 입구. */
    private fun DrawScope.mine() {
        val (origin, area) = whole()
        drawPath(
            path(
                listOf(0.25f to 1f, 0.5f to 0.55f, 0.62f to 0.7f, 0.8f to 0.48f, 1f to 0.72f, 1f to 1f),
                origin,
                area,
            ),
            Scenery,
        )
        val cave = Rect(Offset(area.width * 0.62f, area.height * 0.84f), Size(area.width * 0.18f, area.height * 0.26f))
        drawArc(SceneryDark, 180f, 180f, true, cave.topLeft, cave.size)
        // 갱도를 받치는 기둥.
        drawRect(SceneryDark, Offset(area.width * 0.6f, area.height * 0.9f), Size(area.width * 0.03f, area.height * 0.1f))
        drawRect(SceneryDark, Offset(area.width * 0.79f, area.height * 0.9f), Size(area.width * 0.03f, area.height * 0.1f))
    }

    /** 2단계: 돛을 단 상선과 물결. */
    private fun DrawScope.ship() {
        val (origin, area) = whole()
        drawPath(path(listOf(0.36f to 0.8f, 1f to 0.8f, 0.9f to 0.93f, 0.46f to 0.93f), origin, area), SceneryDark)
        drawRect(SceneryDark, Offset(area.width * 0.66f, area.height * 0.36f), Size(area.width * 0.025f, area.height * 0.44f))
        drawPath(path(listOf(0.7f to 0.4f, 0.96f to 0.76f, 0.7f to 0.76f), origin, area), Scenery)
        drawPath(path(listOf(0.64f to 0.44f, 0.64f to 0.76f, 0.42f to 0.76f), origin, area), Scenery)
        drawRect(Scenery, Offset(area.width * 0.3f, area.height * 0.95f), Size(area.width * 0.7f, area.height * 0.05f))
    }

    /** 3단계: 둥근 지붕의 궁전과 양쪽 탑. */
    private fun DrawScope.palace() {
        val (origin, area) = whole()
        drawRect(Scenery, Offset(area.width * 0.36f, area.height * 0.72f), Size(area.width * 0.64f, area.height * 0.28f))
        drawRect(Scenery, Offset(area.width * 0.38f, area.height * 0.56f), Size(area.width * 0.12f, area.height * 0.16f))
        drawPath(path(listOf(0.36f to 0.56f, 0.44f to 0.44f, 0.52f to 0.56f), origin, area), SceneryDark)
        drawRect(Scenery, Offset(area.width * 0.86f, area.height * 0.56f), Size(area.width * 0.12f, area.height * 0.16f))
        drawPath(path(listOf(0.84f to 0.56f, 0.92f to 0.44f, 1f to 0.56f), origin, area), SceneryDark)
        drawRect(Scenery, Offset(area.width * 0.55f, area.height * 0.6f), Size(area.width * 0.26f, area.height * 0.12f))
        drawArc(SceneryDark, 180f, 180f, true, Offset(area.width * 0.55f, area.height * 0.48f), Size(area.width * 0.26f, area.height * 0.24f))
        drawArc(SceneryDark, 180f, 180f, true, Offset(area.width * 0.62f, area.height * 0.86f), Size(area.width * 0.12f, area.height * 0.2f))
    }

    /** 귀족 타일 바탕의 왕관. */
    fun DrawScope.crown() {
        val (origin, area) = whole()
        drawPath(
            path(
                listOf(
                    0.3f to 0.7f, 0.26f to 0.32f, 0.42f to 0.5f, 0.56f to 0.24f,
                    0.7f to 0.5f, 0.86f to 0.32f, 0.82f to 0.7f,
                ),
                origin,
                area,
            ),
            Color(0x26795548),
        )
        drawRect(Color(0x26795548), Offset(area.width * 0.3f, area.height * 0.74f), Size(area.width * 0.52f, area.height * 0.08f))
    }

    /** 더미 뒷면. 가운데에 큰 보석 윤곽을 옅게 그린다. */
    fun DrawScope.deckBack(color: Color) {
        drawRect(Brush.linearGradient(listOf(lerp(color, Color.White, 0.15f), lerp(color, Color.Black, 0.2f))))
        val gemSize = Size(size.width * 0.7f, size.width * 0.56f)
        val topLeft = Offset((size.width - gemSize.width) / 2, (size.height - gemSize.height) / 2)
        drawPath(
            path(listOf(0.28f to 0.1f, 0.72f to 0.1f, 0.98f to 0.36f, 0.5f to 0.95f, 0.02f to 0.36f), topLeft, gemSize),
            Color.White.copy(alpha = 0.18f),
        )
        drawRect(
            Color.White.copy(alpha = 0.35f),
            Offset(size.width * 0.06f, size.height * 0.05f),
            Size(size.width * 0.88f, size.height * 0.9f),
            style = Stroke(width = size.width * 0.02f),
        )
    }
}
