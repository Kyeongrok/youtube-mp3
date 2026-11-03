package com.kyeongrok.youtubemp3

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val GemFills = listOf(
    Color(0xFFF5F5F5), Color(0xFF1E88E5), Color(0xFF2E7D32), Color(0xFFD32F2F), Color(0xFF212121), Color(0xFFFFB300),
)
private val GemInks = listOf(Color.Black, Color.White, Color.White, Color.White, Color.White, Color.Black)
private val CardTints = listOf(
    Color(0xFFFFFFFF), Color(0xFFD6E9FB), Color(0xFFD5ECD6), Color(0xFFFBD9DB), Color(0xFFCFCFCF),
)
private val CardInk = Color(0xFF212121)
private val CardLine = Color(0xFF9E9E9E)
private val DeckBacks = listOf(Color(0xFF558B2F), Color(0xFFF9A825), Color(0xFF1565C0))
private val NobleFace = Color(0xFFEFE6DC)

private val CardShape = RoundedCornerShape(6.dp)
private val MaxCard = 84.dp
private val CardGap = 4.dp
private const val CARD_RATIO = 1.3f
private const val MARKET_COLUMNS = 5
private const val DEFAULT_CPUS = 2

/** 카드를 누르면 여는 창이다. cardId가 null이면 그 단계 더미 맨 위 카드다. */
private data class SplendorChoice(val cardId: Int?, val tier: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplendorTopBar(viewModel: MainViewModel, onBack: () -> Unit) {
    var cpusOpen by remember { mutableStateOf(false) }
    var recordsOpen by rememberSaveable { mutableStateOf(false) }
    var rulesOpen by rememberSaveable { mutableStateOf(false) }

    if (recordsOpen) {
        val records by viewModel.splendorRecords.collectAsStateWithLifecycle()
        GameRecordsDialog(
            records.map { "CPU ${it.cpus}명 · ${formatDuration(it.seconds)} · ${it.points}점" to it.finishedAt },
            onDismiss = { recordsOpen = false },
        )
    }
    if (rulesOpen) SplendorRulesDialog(onDismiss = { rulesOpen = false })

    TopAppBar(
        title = { Text("스플렌더") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = {
            IconButton(onClick = { rulesOpen = true }) {
                Icon(Icons.Filled.Info, contentDescription = "게임 방법")
            }
            TextButton(onClick = { recordsOpen = true }) { Text("기록") }
            TextButton(onClick = { cpusOpen = true }) { Text("새 게임") }
            DropdownMenu(cpusOpen, { cpusOpen = false }) {
                for (cpus in 1..3) {
                    DropdownMenuItem(
                        text = { Text("CPU ${cpus}명") },
                        onClick = {
                            cpusOpen = false
                            viewModel.newSplendor(cpus)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun SplendorRulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("게임 방법") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                listOf(
                    "보석을 모아 카드를 사고, 먼저 15점을 넘기면 됩니다. 누가 15점에 닿으면 그 바퀴를 끝까지 돌고, 점수가 가장 높은 사람이 이깁니다. 같으면 카드가 적은 사람이 이깁니다.",
                    "차례마다 다음 중 하나만 합니다.",
                    "· 서로 다른 색 보석 3개를 가져옵니다.\n· 4개 이상 남은 색이면 같은 색 2개를 가져옵니다.\n· 카드 1장을 예약하고 황금 1개를 받습니다. 예약은 3장까지이고, 더미 맨 위 카드도 예약할 수 있습니다.\n· 펼친 카드나 예약한 카드를 삽니다.",
                    "산 카드는 오른쪽 위 색의 보석 하나 몫을 영구히 깎아 줍니다. 황금은 아무 색 대신 낼 수 있습니다.",
                    "보석은 10개까지만 가질 수 있어, 넘으면 차례 끝에 돌려줍니다.",
                    "귀족 타일에 적힌 만큼 카드 색을 모으면 차례 끝에 귀족이 찾아와 3점을 줍니다.",
                    "보석을 가져오려면 아래 보석을 눌러 고른 뒤 「가져오기」를 누르세요. 같은 색을 두 번 누르면 2개를 고릅니다. 카드를 누르면 사거나 예약할 수 있습니다.",
                ).forEach { Text(it, Modifier.padding(bottom = 8.dp)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
fun SplendorScreen(viewModel: MainViewModel) {
    val current by viewModel.splendor.collectAsStateWithLifecycle()
    var choice by remember { mutableStateOf<SplendorChoice?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (current == null) viewModel.newSplendor(DEFAULT_CPUS)
        viewModel.resumeSplendor()
    }

    // 이 화면을 보고 있는 동안만 시간을 잰다.
    LifecycleResumeEffect(Unit) {
        val timer = scope.launch {
            while (true) {
                delay(1000)
                viewModel.tickSplendor()
            }
        }
        onPauseOrDispose {
            timer.cancel()
            viewModel.saveSplendor()
        }
    }

    val game = current
    if (game == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    choice?.let { SplendorCardDialog(game, it, viewModel, onDismiss = { choice = null }) }
    if (game.mustDiscard) SplendorDiscardDialog(game, viewModel)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cardWidth = min(MaxCard, (maxWidth - 8.dp - CardGap * (MARKET_COLUMNS - 1)) / MARKET_COLUMNS)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (cpu in 1..game.cpus) {
                    CpuPanel(game, cpu, Modifier.weight(1f))
                }
            }
            SplendorStatus(game)
            Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                game.nobles.forEach { NobleView(it, cardWidth * 0.8f) }
            }
            for (tier in Splendor.TIERS - 1 downTo 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                    DeckView(tier, game.decks[tier].size, cardWidth) {
                        if (game.isHumanTurn && game.decks[tier].isNotEmpty()) choice = SplendorChoice(null, tier)
                    }
                    game.market[tier].forEach { id ->
                        if (id == null) {
                            Spacer(Modifier.size(cardWidth, cardWidth * CARD_RATIO))
                        } else {
                            CardView(Splendor.card(id), cardWidth, highlighted = id in game.recent) {
                                choice = SplendorChoice(id, tier)
                            }
                        }
                    }
                }
            }
            Bank(game, viewModel)
            MyPanel(game, cardWidth) { id -> choice = SplendorChoice(id, Splendor.card(id).tier) }
        }
    }
}

@Composable
private fun SplendorStatus(game: SplendorGame) {
    val colors = MaterialTheme.colorScheme
    val message = when {
        game.winner == Splendor.HUMAN -> "이겼습니다! ${formatDuration(game.seconds)} · ${game.me.points}점"
        game.winner != null -> "CPU ${game.winner}이(가) 이겼습니다. (${game.players[game.winner].points}점)"
        game.notice.isNotEmpty() -> game.notice
        game.isHumanTurn -> "내 차례입니다."
        else -> "CPU ${game.turn} 차례입니다."
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        game.log.forEach {
            Text(it, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            message,
            color = if (game.isFinished || game.isHumanTurn) colors.primary else colors.onSurfaceVariant,
            fontWeight = if (game.isFinished || game.isHumanTurn) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CpuPanel(game: SplendorGame, cpu: Int, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val player = game.players[cpu]
    val active = game.turn == cpu && !game.isFinished

    Column(
        modifier
            .border(if (active) 2.dp else 1.dp, if (active) colors.primary else colors.outlineVariant, CardShape)
            .padding(4.dp),
    ) {
        Text(
            "CPU $cpu · ${player.points}점",
            color = if (active) colors.primary else Color.Unspecified,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Holdings(player, 16.dp)
        Text(
            "예약 ${player.reserved.size} · 귀족 ${player.nobles.size}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 색마다 카드 보너스(네모)와 보석(동그라미) 수를 보여 준다. */
@Composable
private fun Holdings(player: SplendorPlayer, size: Dp) {
    val bonus = player.bonuses
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (color in 0 until Splendor.TOKEN_KINDS) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (color < Splendor.COLORS) {
                    Chip(color, bonus[color], size, RoundedCornerShape(3.dp))
                } else {
                    Spacer(Modifier.size(size))
                }
                Chip(color, player.tokens[color], size, CircleShape, coin = true)
            }
        }
    }
}

@Composable
private fun Chip(color: Int, count: Int, size: Dp, shape: Shape, coin: Boolean = false) {
    val alpha = if (count == 0) 0.25f else 1f
    Box(
        Modifier
            .size(size)
            .then(
                if (coin) {
                    Modifier.drawBehind { with(SplendorArt) { coin(color, alpha) } }
                } else {
                    Modifier.background(GemFills[color].copy(alpha = alpha), shape)
                },
            )
            .border(1.dp, CardLine.copy(alpha = alpha), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (count > 0) {
            // 작은 칸에서도 숫자가 잘리지 않도록 글꼴 위아래 여백을 없앤다.
            val fontSize = with(LocalDensity.current) { (size * 0.62f).toSp() }
            Text(
                count.toString(),
                color = GemInks[color],
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = TextStyle(
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
            )
        }
    }
}

@Composable
private fun NobleView(id: Int, size: Dp) {
    val noble = Splendor.noble(id)
    Box(
        Modifier
            .size(size)
            .clip(CardShape)
            .background(NobleFace)
            .drawBehind { with(SplendorArt) { crown() } }
            .border(1.dp, CardLine, CardShape)
            .padding(3.dp),
    ) {
        Text(
            Splendor.NOBLE_POINTS.toString(),
            color = CardInk,
            fontWeight = FontWeight.Bold,
            fontSize = with(LocalDensity.current) { (size * 0.28f).toSp() },
            modifier = Modifier.align(Alignment.TopStart),
        )
        Column(Modifier.align(Alignment.BottomEnd), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            noble.need.forEachIndexed { color, count ->
                if (count > 0) Chip(color, count, size * 0.28f, RoundedCornerShape(2.dp))
            }
        }
    }
}

@Composable
private fun DeckView(tier: Int, count: Int, width: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width, width * CARD_RATIO)
            .clip(CardShape)
            .alpha(if (count == 0) 0.3f else 1f)
            .drawBehind { with(SplendorArt) { deckBack(DeckBacks[tier]) } }
            .border(1.dp, CardLine, CardShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${tier + 1}단계", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text("${count}장", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CardView(card: SplendorCard, width: Dp, highlighted: Boolean = false, onClick: () -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .size(width, width * CARD_RATIO)
            .clip(CardShape)
            .drawBehind { with(SplendorArt) { cardBackground(CardTints[card.bonus], card.tier) } }
            .border(
                if (highlighted) 2.dp else 1.dp,
                if (highlighted) MaterialTheme.colorScheme.primary else CardLine,
                CardShape,
            )
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        if (card.points > 0) {
            Text(
                card.points.toString(),
                color = CardInk,
                fontWeight = FontWeight.Bold,
                fontSize = with(density) { (width * 0.3f).toSp() },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        Canvas(Modifier.align(Alignment.TopEnd).size(width * 0.32f, width * 0.27f)) {
            with(SplendorArt) { gem(card.bonus, Offset.Zero, size) }
        }
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            card.cost.forEachIndexed { color, count ->
                if (count > 0) Chip(color, count, width * 0.22f, CircleShape, coin = true)
            }
        }
    }
}

@Composable
private fun Bank(game: SplendorGame, viewModel: MainViewModel) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant, CardShape)
            .padding(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (color in 0 until Splendor.TOKEN_KINDS) {
                val picked = game.picked.count { it == color }
                Box(
                    Modifier
                        .size(40.dp)
                        .drawBehind { with(SplendorArt) { coin(color, if (game.bank[color] == 0) 0.25f else 1f) } }
                        .border(if (picked > 0) 3.dp else 1.dp, if (picked > 0) colors.primary else CardLine, CircleShape)
                        .clickable(enabled = game.isHumanTurn) { viewModel.tapSplendorGem(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (picked > 0) "+$picked" else game.bank[color].toString(),
                        color = GemInks[color],
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(
                onClick = viewModel::clearSplendorGems,
                enabled = game.isHumanTurn && game.picked.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) { Text("고르기 취소", maxLines = 1) }
            Button(
                onClick = viewModel::takeSplendorGems,
                enabled = game.isHumanTurn && game.pickProblem == null,
                modifier = Modifier.weight(1f),
            ) { Text("가져오기", maxLines = 1) }
        }
    }
}

@Composable
private fun MyPanel(game: SplendorGame, cardWidth: Dp, onReserved: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val me = game.me
    val myTurn = game.isHumanTurn

    Column(
        Modifier
            .fillMaxWidth()
            .border(2.dp, if (myTurn) colors.primary else colors.outlineVariant, CardShape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "${if (myTurn) "내 차례 · " else ""}${me.points}점 · 보석 ${me.tokenCount}/${Splendor.TOKEN_LIMIT} · 귀족 ${me.nobles.size}",
            color = if (myTurn) colors.primary else Color.Unspecified,
            fontWeight = FontWeight.Bold,
        )
        Holdings(me, 28.dp)
        if (me.reserved.isNotEmpty()) {
            Text("예약한 카드", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
                me.reserved.forEach { id ->
                    CardView(Splendor.card(id), cardWidth) { onReserved(id) }
                }
            }
        }
    }
}

@Composable
private fun SplendorCardDialog(game: SplendorGame, choice: SplendorChoice, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val me = game.me
    val card = choice.cardId?.let(Splendor::card)
    val reserved = choice.cardId != null && choice.cardId in me.reserved
    val payment = card?.let(me::payment)
    val canReserve = !reserved && me.reserved.size < Splendor.RESERVE_LIMIT

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (card == null) "${choice.tier + 1}단계 더미" else "${choice.tier + 1}단계 카드") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (card != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardView(card, 96.dp) { }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${card.points}점 · ${Splendor.COLOR_NAMES[card.bonus]} 보너스")
                            Text(
                                payment?.let { pay ->
                                    val spent = pay.withIndex().filter { it.value > 0 }
                                    if (spent.isEmpty()) "보석 없이 살 수 있습니다." else "낼 보석: " +
                                        spent.joinToString(", ") { "${Splendor.COLOR_NAMES[it.index]} ${it.value}" }
                                } ?: "보석이 ${me.shortfall(card)}개 모자랍니다.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                } else {
                    Text("맨 위 카드를 보지 않고 예약합니다.")
                }
                when {
                    !game.isHumanTurn -> Text("내 차례가 아닙니다.", color = MaterialTheme.colorScheme.error)
                    !reserved && !canReserve -> Text("예약은 3장까지입니다.", style = MaterialTheme.typography.bodySmall)
                    !reserved && game.bank[Splendor.GOLD] == 0 -> Text("황금이 남지 않아 예약만 합니다.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Row {
                if (!reserved) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            viewModel.reserveSplendor(choice.cardId, choice.tier)
                        },
                        enabled = game.isHumanTurn && canReserve,
                    ) { Text("예약") }
                }
                if (card != null) {
                    TextButton(
                        onClick = {
                            onDismiss()
                            viewModel.buySplendor(card.id)
                        },
                        enabled = game.isHumanTurn && payment != null,
                    ) { Text("구매") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun SplendorDiscardDialog(game: SplendorGame, viewModel: MainViewModel) {
    val me = game.me
    AlertDialog(
        onDismissRequest = { },
        title = { Text("보석 돌려주기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("보석은 ${Splendor.TOKEN_LIMIT}개까지 가질 수 있습니다. ${me.tokenCount - Splendor.TOKEN_LIMIT}개를 눌러 돌려주세요.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (color in 0 until Splendor.TOKEN_KINDS) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(GemFills[color].copy(alpha = if (me.tokens[color] == 0) 0.25f else 1f), CircleShape)
                                .border(1.dp, CardLine, CircleShape)
                                .clickable(enabled = me.tokens[color] > 0) { viewModel.discardSplendor(color) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(me.tokens[color].toString(), color = GemInks[color], fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = { },
    )
}
