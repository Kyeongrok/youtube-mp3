package com.kyeongrok.youtubemp3

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * material-icons-core에 없는 아이콘이다. 확장 아이콘 묶음은 앱을 크게 키우므로 필요한 것만 그린다.
 * 모두 24×24 격자 기준이다.
 */
object AppIcons {
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

    /** 3×3 칸. */
    val Sudoku: ImageVector = materialIcon("App.Sudoku") {
        materialPath {
            for (row in 0..2) {
                for (col in 0..2) {
                    moveTo(3f + col * 6.5f, 3f + row * 6.5f)
                    horizontalLineToRelative(5f); verticalLineToRelative(5f); horizontalLineToRelative(-5f); close()
                }
            }
        }
    }

    /** 뒤에 한 장이 비치는 마작 패. 앞 패에는 동그라미 무늬를 뚫는다. */
    val Shanghai: ImageVector = materialIcon("App.Shanghai") {
        materialPath {
            moveTo(8f, 2f); horizontalLineToRelative(12f); verticalLineToRelative(15f); horizontalLineToRelative(-3f)
            verticalLineTo(5f); horizontalLineTo(8f); close()
        }
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(4f, 7f); horizontalLineToRelative(11f); verticalLineToRelative(15f); horizontalLineTo(4f); close()
            moveTo(9.5f, 11f)
            arcToRelative(3.5f, 3.5f, 0f, true, true, 0f, 7f)
            arcToRelative(3.5f, 3.5f, 0f, true, true, 0f, -7f)
            close()
        }
    }

    /** 다이아몬드가 그려진 카드. */
    val FreeCell: ImageVector = materialIcon("App.FreeCell") {
        materialPath(pathFillType = PathFillType.EvenOdd) {
            moveTo(5f, 2f); horizontalLineToRelative(14f); verticalLineToRelative(20f); horizontalLineTo(5f); close()
            moveTo(7f, 4f); verticalLineToRelative(16f); horizontalLineToRelative(10f); verticalLineTo(4f); close()
        }
        materialPath {
            moveTo(12f, 7f); lineToRelative(3.5f, 5f); lineToRelative(-3.5f, 5f); lineToRelative(-3.5f, -5f); close()
        }
    }

    /** 나란히 놓인 타일 세 장. */
    val Rummikub: ImageVector = materialIcon("App.Rummikub") {
        materialPath {
            for (index in 0..2) {
                moveTo(1.5f + index * 7.5f, 6f)
                horizontalLineToRelative(6f); verticalLineToRelative(12f); horizontalLineToRelative(-6f); close()
            }
        }
    }
}
