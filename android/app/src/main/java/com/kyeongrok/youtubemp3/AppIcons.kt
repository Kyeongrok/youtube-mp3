package com.kyeongrok.youtubemp3

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * material-icons-core에 없는 아이콘이다. 확장 아이콘 묶음은 앱을 크게 키우므로 필요한 것만 그린다.
 * 모두 24×24 격자 기준이다.
 */
object AppIcons {
    /** Material의 ContentCut(가위)과 같은 모양이다. 편집(잘라내기)에 쓴다. */
    val Cut: ImageVector = materialIcon("App.Cut") {
        addPath(
            pathData = addPathNodes(
                "M9.64,7.64c0.23,-0.5 0.36,-1.05 0.36,-1.64 0,-2.21 -1.79,-4 -4,-4S2,3.79 2,6s1.79,4 4,4c0.59,0 " +
                    "1.14,-0.13 1.64,-0.36L10,12l-2.36,2.36C7.14,14.13 6.59,14 6,14c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 " +
                    "4,-1.79 4,-4c0,-0.59 -0.13,-1.14 -0.36,-1.64L12,14l7,7h3v-1L9.64,7.64zM6,8c-1.1,0 -2,-0.89 " +
                    "-2,-2s0.9,-2 2,-2 2,0.89 2,2 -0.9,2 -2,2zM6,20c-1.1,0 -2,-0.89 -2,-2s0.9,-2 2,-2 2,0.89 2,2 " +
                    "-0.9,2 -2,2zM12,12.5c-0.28,0 -0.5,-0.22 -0.5,-0.5s0.22,-0.5 0.5,-0.5 0.5,0.22 0.5,0.5 -0.22,0.5 " +
                    "-0.5,0.5zM19,3l-6,6 2,2 7,-7L22,3z",
            ),
            fill = SolidColor(Color.Black),
        )
    }

    /** Material의 Pause와 같은 모양이다. */
    val Pause: ImageVector = materialIcon("App.Pause") {
        materialPath {
            moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); close()
            moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); close()
        }
    }

    /** Material의 SkipNext와 같은 모양이다. */
    val SkipNext: ImageVector = materialIcon("App.SkipNext") {
        materialPath {
            moveTo(6f, 18f); lineToRelative(8.5f, -6f); lineTo(6f, 6f); close()
            moveTo(16f, 6f); verticalLineToRelative(12f); horizontalLineToRelative(2f); verticalLineTo(6f); close()
        }
    }

    /** Material의 SkipPrevious와 같은 모양이다. */
    val SkipPrevious: ImageVector = materialIcon("App.SkipPrevious") {
        materialPath {
            moveTo(6f, 6f); horizontalLineToRelative(2f); verticalLineToRelative(12f); horizontalLineTo(6f); close()
            moveTo(9.5f, 12f); lineToRelative(8.5f, 6f); verticalLineTo(6f); close()
        }
    }

    /** 8분음표. 동그란 머리와 기둥, 위쪽 깃발. */
    val Music: ImageVector = materialIcon("App.Music") {
        materialPath {
            moveTo(12f, 3f); verticalLineToRelative(10.55f)
            curveToRelative(-0.59f, -0.34f, -1.27f, -0.55f, -2f, -0.55f)
            curveToRelative(-2.21f, 0f, -4f, 1.79f, -4f, 4f)
            reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
            reflectiveCurveToRelative(4f, -1.79f, 4f, -4f)
            verticalLineTo(7f); horizontalLineToRelative(4f); verticalLineTo(3f); horizontalLineToRelative(-6f); close()
        }
    }

    /** 대문자 A와 작은 a. */
    val Alphabet: ImageVector = materialIcon("App.Alphabet") {
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(2f, 20f); lineTo(7.5f, 4f); horizontalLineToRelative(2.2f); lineTo(15.2f, 20f)
            horizontalLineToRelative(-2.3f); lineToRelative(-1.3f, -4f); horizontalLineToRelative(-5.2f)
            lineToRelative(-1.3f, 4f); close()
            moveTo(7f, 14f); horizontalLineToRelative(4f); lineTo(9f, 7.8f); close()
        }
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(18.5f, 13f)
            arcToRelative(3f, 3f, 0f, true, true, 0f, 6f)
            arcToRelative(3f, 3f, 0f, true, true, 0f, -6f)
            close()
            moveTo(18.5f, 14.6f)
            arcToRelative(1.4f, 1.4f, 0f, true, false, 0f, 2.8f)
            arcToRelative(1.4f, 1.4f, 0f, true, false, 0f, -2.8f)
            close()
        }
        materialPath {
            moveTo(20.8f, 13f); horizontalLineToRelative(1.5f); verticalLineToRelative(7f); horizontalLineToRelative(-1.5f); close()
        }
    }

    /** 네모 칸 안의 숫자 12. */
    val Numbers: ImageVector = materialIcon("App.Numbers") {
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(3f, 3f); horizontalLineToRelative(18f); verticalLineToRelative(18f); horizontalLineTo(3f); close()
            moveTo(5f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(14f); verticalLineTo(5f); close()
        }
        materialPath {
            // 1
            moveTo(7f, 8.5f); lineTo(9.5f, 7f); horizontalLineToRelative(1.5f); verticalLineToRelative(10f)
            horizontalLineToRelative(-2f); verticalLineTo(9.4f); lineTo(7f, 10.5f); close()
            // 2
            moveTo(12.5f, 7f); horizontalLineToRelative(5f); verticalLineToRelative(5.5f); horizontalLineToRelative(-3f)
            verticalLineToRelative(2.5f); horizontalLineToRelative(3f); verticalLineToRelative(2f); horizontalLineToRelative(-5f)
            verticalLineToRelative(-5.5f); horizontalLineToRelative(3f); verticalLineTo(9f); horizontalLineToRelative(-3f); close()
        }
    }

    /** 태극 모양. 바깥 테 안에 어두운 반쪽과 점 하나를 그린다. */
    val Saju: ImageVector = materialIcon("App.Saju") {
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(12f, 1.5f)
            arcToRelative(10.5f, 10.5f, 0f, true, true, 0f, 21f)
            arcToRelative(10.5f, 10.5f, 0f, true, true, 0f, -21f)
            close()
            moveTo(12f, 3f)
            arcToRelative(9f, 9f, 0f, true, true, 0f, 18f)
            arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
            close()
        }
        materialPath {
            moveTo(12f, 3f)
            arcTo(9f, 9f, 0f, false, true, 12f, 21f)
            arcTo(4.5f, 4.5f, 0f, false, true, 12f, 12f)
            arcTo(4.5f, 4.5f, 0f, false, false, 12f, 3f)
            close()
            moveTo(12f, 6.3f)
            arcToRelative(1.2f, 1.2f, 0f, true, true, 0f, 2.4f)
            arcToRelative(1.2f, 1.2f, 0f, true, true, 0f, -2.4f)
            close()
        }
    }

    /** 네모난 획으로 쓴 「가」. */
    val Hangul: ImageVector = materialIcon("App.Hangul") {
        materialPath {
            moveTo(2.5f, 5f); horizontalLineTo(12f); verticalLineTo(19f); horizontalLineTo(9.5f)
            verticalLineTo(7.5f); horizontalLineTo(2.5f); close()
            moveTo(15f, 3f); horizontalLineToRelative(2.5f); verticalLineToRelative(18f); horizontalLineTo(15f); close()
            moveTo(17.5f, 10.8f); horizontalLineTo(21.5f); verticalLineToRelative(2.4f); horizontalLineTo(17.5f); close()
        }
    }
}
