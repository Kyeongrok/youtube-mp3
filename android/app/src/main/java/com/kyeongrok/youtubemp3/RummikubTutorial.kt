package com.kyeongrok.youtubemp3

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val RED = 0
private const val BLUE = 1
private const val BLACK = 2
private const val ORANGE = 3

private val ExampleTileWidth = 30.dp
private val PageHeight = 252.dp
private const val EXAMPLE_TILE_RATIO = 1.35f

private val Joker = RummikubTile(RummikubTile.JOKER_START)

private fun tile(color: Int, number: Int) = RummikubTile((color * Rummikub.MAX_NUMBER + number - 1) * 2)

private fun run(color: Int, numbers: IntRange) = numbers.map { tile(color, it) }

private class TutorialPage(val title: String, val text: String, val example: List<RummikubTile> = emptyList())

private val Pages = listOf(
    TutorialPage(
        "목표",
        "패의 타일을 판에 모두 내려놓으면 이깁니다.\n\n" +
            "모두 14장씩 받고 나부터 시작합니다. 판에는 완성된 묶음만 남길 수 있습니다.",
    ),
    TutorialPage("연속 묶음", "같은 색의 이어진 숫자 3장 이상입니다. 13 다음에 1로 넘어갈 수는 없습니다.", run(RED, 4..7)),
    TutorialPage(
        "같은 숫자 묶음",
        "같은 숫자의 서로 다른 색 3~4장입니다. 같은 색이 두 장 들어가면 안 됩니다.",
        listOf(tile(RED, 9), tile(BLUE, 9), tile(BLACK, 9)),
    ),
    TutorialPage(
        "조커",
        "★은 어떤 타일 대신으로든 쓸 수 있습니다. 아래 묶음에서는 5 자리를 채웁니다.",
        listOf(tile(ORANGE, 4), Joker, tile(ORANGE, 6)),
    ),
    TutorialPage(
        "첫 등록",
        "처음 내려놓을 때는 내 패만으로 만든 묶음의 숫자 합이 30점 이상이어야 합니다. " +
            "아래 묶음은 10+10+10으로 30점입니다.\n\n등록하기 전에는 판의 타일을 쓸 수 없습니다.",
        listOf(tile(RED, 10), tile(BLUE, 10), tile(ORANGE, 10)),
    ),
    TutorialPage(
        "내 차례",
        "1. 패에서 타일을 눌러 고릅니다.\n" +
            "2. 「+ 새 묶음」을 누르거나 판의 묶음을 눌러 붙입니다.\n" +
            "3. 「완료」를 누릅니다.\n\n" +
            "낼 타일이 없으면 「뽑기」로 1장을 가져오고 차례를 넘깁니다.",
    ),
    TutorialPage(
        "판 다시 짜기",
        "첫 등록 뒤에는 판의 타일도 골라 다른 묶음으로 옮길 수 있습니다. " +
            "아래 묶음에서 끝의 7을 떼어 다른 묶음에 써도 4장이 남아 그대로 완성입니다.\n\n" +
            "미완성 묶음은 빨간 테두리로 표시되며, 모두 고쳐야 끝낼 수 있습니다. " +
            "막히면 「되돌리기」로 차례 시작 상태로 돌아갑니다.",
        run(BLUE, 3..7),
    ),
    TutorialPage(
        "끝내기",
        "더미가 떨어지고 아무도 낼 수 없으면 남은 패의 합이 가장 적은 사람이 이깁니다. 조커는 30점입니다.\n\n" +
            "이기면 상대 패에 남은 점수가 내 점수로 기록됩니다.",
    ),
)

@Composable
fun RummikubTutorialDialog(onDismiss: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = Pages[index]
    val isLast = index == Pages.lastIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(page.title) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(min = PageHeight)) {
                Text(page.text)
                if (page.example.isNotEmpty()) {
                    Row(
                        Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        page.example.forEach {
                            RummikubTileFace(it, ExampleTileWidth, ExampleTileWidth * EXAMPLE_TILE_RATIO)
                        }
                    }
                }
                Text(
                    "${index + 1} / ${Pages.size}",
                    Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (isLast) onDismiss() else index++ }) { Text(if (isLast) "시작" else "다음") }
        },
        dismissButton = {
            if (index > 0) {
                TextButton(onClick = { index-- }) { Text("이전") }
            } else {
                TextButton(onClick = onDismiss) { Text("건너뛰기") }
            }
        },
    )
}
