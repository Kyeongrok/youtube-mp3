package com.kyeongrok.youtubemp3

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val RED = RummikubTile.RED
private const val BLUE = RummikubTile.BLUE
private const val BLACK = RummikubTile.BLACK
private const val ORANGE = RummikubTile.ORANGE

private val ExampleTileWidth = 30.dp
private val PageHeight = 300.dp
private const val EXAMPLE_TILE_RATIO = 1.35f

private val Joker = RummikubTile(RummikubTile.JOKER_START)

private fun tile(color: Int, number: Int) = RummikubTile.of(color, number)

private fun run(color: Int, numbers: IntRange) = numbers.map { tile(color, it) }

private enum class TutorialSection(val label: String) {
    Basics("기본"),
    Scoring("점수 계산"),
    Winning("승리"),
}

private class TutorialPage(
    val section: TutorialSection,
    val title: String,
    val text: String,
    val example: List<RummikubTile> = emptyList(),
    val headline: String? = null,
)

private val Pages = listOf(
    TutorialPage(
        TutorialSection.Basics,
        "게임의 목적",
        "모두 14장씩 받고 나부터 시작합니다. 타일을 묶음으로 만들어 판에 내려놓으며, 판에는 완성된 묶음만 남길 수 있습니다.",
        headline = "내 패의 타일을 가장 먼저 모두 내려놓으면 이깁니다.",
    ),
    TutorialPage(
        TutorialSection.Basics,
        "연속 묶음",
        "같은 색의 이어진 숫자 3장 이상입니다. 13 다음에 1로 넘어갈 수는 없습니다.",
        run(RED, 4..7),
    ),
    TutorialPage(
        TutorialSection.Basics,
        "같은 숫자 묶음",
        "같은 숫자의 서로 다른 색 3~4장입니다. 같은 색이 두 장 들어가면 안 됩니다.",
        listOf(tile(RED, 9), tile(BLUE, 9), tile(BLACK, 9)),
    ),
    TutorialPage(
        TutorialSection.Basics,
        "조커",
        "★은 어떤 타일 대신으로든 쓸 수 있습니다. 아래 묶음에서는 5 자리를 채웁니다.",
        listOf(tile(ORANGE, 4), Joker, tile(ORANGE, 6)),
    ),
    TutorialPage(
        TutorialSection.Basics,
        "내 차례",
        "1. 패에서 타일을 눌러 고릅니다.\n" +
            "2. 「+ 새 묶음」을 누르거나 판의 묶음을 눌러 붙입니다.\n" +
            "3. 「완료」를 누릅니다.\n\n" +
            "낼 타일이 없으면 「뽑기」로 1장을 가져오고 차례를 넘깁니다.",
    ),
    TutorialPage(
        TutorialSection.Basics,
        "판 다시 짜기",
        "첫 등록 뒤에는 판의 타일도 골라 다른 묶음으로 옮길 수 있습니다. " +
            "아래 묶음에서 끝의 7을 떼어 다른 묶음에 써도 4장이 남아 그대로 완성입니다.\n\n" +
            "미완성 묶음은 빨간 테두리로 표시되며, 모두 고쳐야 끝낼 수 있습니다. " +
            "막히면 「되돌리기」로 차례 시작 상태로 돌아갑니다.",
        run(BLUE, 3..7),
    ),
    TutorialPage(
        TutorialSection.Scoring,
        "언제 점수를 쓰나요",
        "① 첫 등록이 30점을 넘는지 볼 때\n② 게임이 끝나 최종 점수를 낼 때\n\n" +
            "이 두 번만 계산합니다. 게임 중에 판에 내는 타일은 점수로 쌓이지 않습니다.",
        headline = "타일을 내도 점수가 바로 쌓이지는 않습니다.",
    ),
    TutorialPage(
        TutorialSection.Scoring,
        "묶음의 점수",
        "묶음의 점수는 숫자의 합입니다. 판에 놓인 조커는 자기가 채운 자리의 숫자로 셉니다. " +
            "아래 묶음은 4+5+6으로 15점입니다.",
        listOf(tile(ORANGE, 4), Joker, tile(ORANGE, 6)),
    ),
    TutorialPage(
        TutorialSection.Scoring,
        "첫 등록 30점",
        "처음 내려놓을 때는 내 패만으로 만든 묶음의 합이 30점 이상이어야 합니다. " +
            "아래 묶음은 10+10+10으로 30점입니다. 묶음 여러 개를 합쳐도 됩니다.\n\n" +
            "등록하기 전에는 판의 타일을 쓸 수 없습니다.",
        listOf(tile(RED, 10), tile(BLUE, 10), tile(ORANGE, 10)),
    ),
    TutorialPage(
        TutorialSection.Scoring,
        "컴퓨터가 패를 낼 때",
        "컴퓨터도 같은 규칙으로 30점 이상을 첫 등록해야 합니다. " +
            "「CPU 2 10장 냄」은 낸 장수일 뿐이고, 컴퓨터가 타일을 낸다고 내 점수나 컴퓨터 점수가 바뀌지는 않습니다.\n\n" +
            "다만 컴퓨터의 패가 줄어드니, 내가 이겼을 때 받을 점수는 그만큼 줄어듭니다.",
        headline = "컴퓨터가 내도 점수는 오르내리지 않습니다.",
    ),
    TutorialPage(
        TutorialSection.Scoring,
        "최종 점수",
        "내가 이기면 컴퓨터들의 패에 남은 타일의 합이 내 점수입니다. 남은 패의 조커는 30점입니다. " +
            "아래처럼 남았다면 5+8+30으로 43점입니다.",
        listOf(tile(RED, 5), tile(BLUE, 8), Joker),
    ),
    TutorialPage(
        TutorialSection.Winning,
        "패를 비우면 승리",
        "누구든 마지막 타일을 내려놓고 차례를 마치는 순간 게임이 끝납니다.",
        headline = "내 패를 가장 먼저 비운 사람이 이깁니다.",
    ),
    TutorialPage(
        TutorialSection.Winning,
        "더미가 떨어지면",
        "더미가 비면 뽑는 대신 차례를 넘깁니다. 모두가 연달아 넘기면 게임이 끝나고, " +
            "남은 패의 합이 가장 적은 사람이 이깁니다. 나와 동점이면 내가 이깁니다.\n\n" +
            "내 패가 남은 채로 이겨도 최종 점수는 컴퓨터들의 남은 패 합이며, 내 패는 빼지 않습니다.",
    ),
    TutorialPage(
        TutorialSection.Winning,
        "기록",
        "내가 이긴 판만 「기록」에 남습니다. 컴퓨터 인원, 걸린 시간, 최종 점수를 볼 수 있습니다.",
    ),
)

@Composable
fun RummikubTutorialDialog(onDismiss: () -> Unit, onPractice: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = Pages[index]
    val isLast = index == Pages.lastIndex
    val sectionPages = Pages.filter { it.section == page.section }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("게임 방법", Modifier.weight(1f))
                TextButton(onClick = onPractice) { Text("연습하기") }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = page.section.ordinal, containerColor = Color.Transparent) {
                    TutorialSection.entries.forEach { section ->
                        Tab(
                            selected = section == page.section,
                            onClick = { index = Pages.indexOfFirst { it.section == section } },
                            text = { Text(section.label, maxLines = 1) },
                        )
                    }
                }
                // 큰 글씨나 가로 화면에서도 끝까지 읽도록 스크롤하고, 쪽을 넘기면 맨 위부터 보여 준다.
                key(index) {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .heightIn(min = PageHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp),
                    ) {
                        Text(
                            page.title,
                            Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        page.headline?.let {
                            Text(
                                it,
                                Modifier.padding(bottom = 8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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
                            "${page.section.label} ${sectionPages.indexOf(page) + 1} / ${sectionPages.size}",
                            Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
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
