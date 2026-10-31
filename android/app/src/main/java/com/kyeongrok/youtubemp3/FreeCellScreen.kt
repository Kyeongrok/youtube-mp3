package com.kyeongrok.youtubemp3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val CardFace = Color.White
private val CardPicked = Color(0xFFFFF59D)
private val CardLine = Color(0xFF8A8A8A)
private val InkRed = Color(0xFFC62828)
private val InkBlack = Color(0xFF212121)

private val CardShape = RoundedCornerShape(4.dp)
private val CardGap = 2.dp
private const val CARD_RATIO = 1.4f
private const val STRIP_RATIO = 0.42f

// 변형 선택자로 이모지가 아닌 글자 모양을 쓰게 한다.
private val SUIT_MARKS = listOf("♠︎", "♥︎", "♦︎", "♣︎")

private fun rankLabel(rank: Int) = when (rank) {
    1 -> "A"
    11 -> "J"
    12 -> "Q"
    13 -> "K"
    else -> rank.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeCellTopBar(viewModel: MainViewModel, onBack: () -> Unit) {
    val current by viewModel.freeCell.collectAsStateWithLifecycle()

    TopAppBar(
        title = { Text("프리셀") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = {
            TextButton(onClick = viewModel::undoFreeCell, enabled = current?.previous != null) { Text("되돌리기") }
            TextButton(onClick = viewModel::newFreeCell) { Text("새 게임") }
        },
    )
}

@Composable
fun FreeCellScreen(viewModel: MainViewModel) {
    val current by viewModel.freeCell.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (current == null) viewModel.newFreeCell() }
    val game = current ?: return

    BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
        val cardWidth = min(
            (maxWidth - CardGap * (FreeCellGame.COLUMNS - 1)) / FreeCellGame.COLUMNS,
            maxHeight / 5,
        )
        val cardHeight = cardWidth * CARD_RATIO
        val tableHeight = maxHeight - cardHeight - 48.dp

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                game.cells.forEachIndexed { index, card ->
                    val onClick = { viewModel.tapFreeCellSpot(index) }
                    if (card == null) {
                        EmptySlot("", cardWidth, cardHeight, onClick)
                    } else {
                        CardView(card, game.picked == FreeCellPick.Cell(index), cardWidth, cardHeight, Modifier.clickable(onClick = onClick))
                    }
                }
                game.foundations.forEachIndexed { suit, rank ->
                    if (rank == 0) {
                        EmptySlot(SUIT_MARKS[suit], cardWidth, cardHeight, viewModel::tapFreeCellFoundation)
                    } else {
                        CardView(
                            PlayingCard(suit, rank),
                            picked = false,
                            cardWidth,
                            cardHeight,
                            Modifier.clickable(onClick = viewModel::tapFreeCellFoundation),
                        )
                    }
                }
            }
            Text(
                when {
                    game.isComplete -> "모두 맞췄습니다!"
                    !game.hasMoves -> "더 옮길 수 있는 카드가 없습니다. 되돌려 보세요."
                    else -> ""
                },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(40.dp).padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                game.cascades.forEachIndexed { column, cards ->
                    CascadeView(game, column, cards, cardWidth, cardHeight, tableHeight, viewModel)
                }
            }
        }
    }
}

@Composable
private fun CascadeView(
    game: FreeCellGame,
    column: Int,
    cards: List<PlayingCard>,
    cardWidth: Dp,
    cardHeight: Dp,
    tableHeight: Dp,
    viewModel: MainViewModel,
) {
    // 카드가 많이 쌓이면 화면 안에 들어오도록 겹치는 폭을 줄인다.
    val strip = if (cards.size > 1) {
        min(cardHeight * STRIP_RATIO, (tableHeight - cardHeight) / (cards.size - 1))
    } else {
        cardHeight * STRIP_RATIO
    }
    val picked = (game.picked as? FreeCellPick.Cascade)?.takeIf { it.column == column }

    Box(
        Modifier.width(cardWidth).height(tableHeight).clickable(enabled = cards.isEmpty()) {
            viewModel.tapFreeCellCascade(column, null)
        },
    ) {
        if (cards.isEmpty()) EmptySlot("", cardWidth, cardHeight) { viewModel.tapFreeCellCascade(column, null) }
        cards.forEachIndexed { row, card ->
            CardView(
                card,
                picked != null && row >= picked.row,
                cardWidth,
                cardHeight,
                Modifier.offset(y = strip * row).clickable { viewModel.tapFreeCellCascade(column, row) },
            )
        }
    }
}

@Composable
private fun EmptySlot(mark: String, width: Dp, height: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width, height)
            .border(1.dp, MaterialTheme.colorScheme.outline, CardShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(mark, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun CardView(card: PlayingCard, picked: Boolean, width: Dp, height: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    val ink = if (card.isRed) InkRed else InkBlack
    val mark = SUIT_MARKS[card.suit]

    Box(
        modifier
            .size(width, height)
            .background(if (picked) CardPicked else CardFace, CardShape)
            .border(1.dp, CardLine, CardShape),
    ) {
        Text(
            "${rankLabel(card.rank)}$mark",
            Modifier.align(Alignment.TopStart).padding(start = 3.dp),
            color = ink,
            fontSize = with(density) { (width * 0.3f).toSp() },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            mark,
            Modifier.align(Alignment.BottomCenter),
            color = ink,
            fontSize = with(density) { (width * 0.55f).toSp() },
        )
    }
}
