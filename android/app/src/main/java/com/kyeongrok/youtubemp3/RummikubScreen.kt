package com.kyeongrok.youtubemp3

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TileFace = Color.White
private val TilePicked = Color(0xFFFFF59D)
private val TileDrawn = Color(0xFF80DEEA)
private val TileLine = Color(0xFF8A8A8A)
private val TileInks = listOf(Color(0xFFC62828), Color(0xFF1565C0), Color(0xFF212121), Color(0xFFEF6C00))
private val JokerInk = Color(0xFF6A1B9A)

private val CpuTileBack = Color(0xFF37474F)
private val CpuTileEdge = Color(0xFFCFD8DC)
private val CpuTileDrawn = Color(0xFFFFB300)

private val TileShape = RoundedCornerShape(4.dp)
private val CpuTileWidth = 14.dp
private val CpuTileHeight = 19.dp
private val CpuTileStep = 7.dp
private val CpuDrawDrop = 12.dp
private val TileGap = 2.dp
private val MaxTableTile = 34.dp
private val MaxRackTile = 44.dp
private val RackHeight = 170.dp
private const val TILE_RATIO = 1.35f
private const val TABLE_TILES_WIDE = 13.8f
private const val RACK_TILES_WIDE = 9.5f
private const val DEFAULT_CPUS = 2
private const val BLINK_MILLIS = 500
private const val CPU_DRAW_MILLIS = 900
private const val TURN_GLOW_MILLIS = 250
private const val TURN_GLOW_COUNT = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RummikubTopBar(viewModel: MainViewModel, onBack: () -> Unit) {
    var cpusOpen by remember { mutableStateOf(false) }
    var recordsOpen by rememberSaveable { mutableStateOf(false) }
    var tutorialOpen by rememberSaveable { mutableStateOf(!viewModel.rummikubTutorialSeen) }
    val practice by viewModel.rummikubPractice.collectAsStateWithLifecycle()

    if (recordsOpen) RummikubRecordsDialog(viewModel, onDismiss = { recordsOpen = false })
    if (tutorialOpen) {
        val close = {
            tutorialOpen = false
            viewModel.markRummikubTutorialSeen()
        }
        RummikubTutorialDialog(
            onDismiss = close,
            onPractice = {
                close()
                viewModel.startRummikubPractice()
            },
        )
    }

    practice?.let {
        TopAppBar(
            title = { Text("연습 ${it.scene + 1} / ${RummikubPractice.SCENE_COUNT}") },
            navigationIcon = {
                IconButton(onClick = viewModel::stopRummikubPractice) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "연습 끝내기")
                }
            },
        )
        return
    }

    TopAppBar(
        title = { Text("루미큐브") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = {
            IconButton(onClick = { tutorialOpen = true }) {
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
                            viewModel.newRummikub(cpus)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun RummikubRecordsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val records by viewModel.rummikubRecords.collectAsStateWithLifecycle()

    GameRecordsDialog(
        records.map { "CPU ${it.cpus}명 · ${formatDuration(it.seconds)} · ${it.score}점" to it.finishedAt },
        onDismiss,
    )
}

@Composable
fun RummikubScreen(viewModel: MainViewModel) {
    val current by viewModel.rummikub.collectAsStateWithLifecycle()
    val practice by viewModel.rummikubPractice.collectAsStateWithLifecycle()
    var byColor by rememberSaveable { mutableStateOf(false) }
    BackHandler(practice != null) { viewModel.stopRummikubPractice() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        if (current == null) viewModel.newRummikub(DEFAULT_CPUS)
        viewModel.resumeRummikub()
    }

    // 이 화면을 보고 있는 동안만 시간을 잰다.
    LifecycleResumeEffect(Unit) {
        val timer = scope.launch {
            while (true) {
                delay(1000)
                viewModel.tickRummikub()
            }
        }
        onPauseOrDispose {
            timer.cancel()
            viewModel.saveRummikub()
        }
    }

    val game = practice?.game ?: current
    if (game == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
        if (maxWidth > maxHeight) {
            val tableWidth = maxWidth * 0.55f
            val rackWidth = maxWidth * 0.45f
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(0.55f).fillMaxHeight()) {
                    practice?.let { RummikubPracticePanel(it, viewModel) } ?: RummikubStatus(game)
                    RummikubTable(game, tableWidth, viewModel, Modifier.weight(1f))
                }
                Column(Modifier.weight(0.45f).fillMaxHeight()) {
                    RummikubRack(game, byColor, { byColor = it }, rackWidth, viewModel, Modifier.weight(1f))
                    RummikubActions(game, viewModel)
                }
            }
        } else {
            val width = maxWidth
            Column(Modifier.fillMaxSize()) {
                practice?.let { RummikubPracticePanel(it, viewModel) } ?: RummikubStatus(game)
                RummikubTable(game, width, viewModel, Modifier.weight(1f))
                RummikubRack(game, byColor, { byColor = it }, width, viewModel, Modifier.heightIn(max = RackHeight + 48.dp))
                RummikubActions(game, viewModel)
            }
        }
    }
}

@Composable
private fun RummikubPracticePanel(practice: RummikubPractice, viewModel: MainViewModel) {
    val colors = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.primaryContainer, TileShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(practice.title, color = colors.onPrimaryContainer, fontWeight = FontWeight.Bold)
        Text(practice.guide, color = colors.onPrimaryContainer)
        if (practice.game.notice.isNotEmpty()) {
            Text(practice.game.notice, color = colors.error, style = MaterialTheme.typography.bodySmall)
        }
        if (practice.isComplete) {
            Button(onClick = viewModel::nextRummikubPractice, modifier = Modifier.align(Alignment.End)) {
                Text(if (practice.isLast) "연습 끝내기" else "다음 연습")
            }
        }
    }
}

@Composable
private fun RummikubStatus(game: RummikubGame) {
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (cpu in 1..game.cpus) {
                CpuRack("CPU $cpu", game.racks[cpu].size, game.turn == cpu && !game.isFinished, Modifier.weight(1f))
            }
        }
        Text("더미 ${game.pool.size}장", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall)
        val message = when {
            game.winner == Rummikub.HUMAN -> "이겼습니다! ${formatDuration(game.seconds)} · ${game.humanScore}점"
            game.winner != null -> "CPU ${game.winner}이(가) 이겼습니다."
            game.notice.isNotEmpty() -> game.notice
            game.isHumanTurn -> "내 차례입니다."
            else -> "CPU ${game.turn} 차례입니다."
        }
        Text(
            message,
            color = if (game.isFinished) colors.primary else colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (game.isFinished) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** 컴퓨터의 패를 뒤집힌 타일로 겹쳐 보여 준다. 타일이 많으면 칸에 맞게 더 겹친다. */
@Composable
private fun CpuRack(name: String, count: Int, active: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val drawn = remember { Animatable(0f) }
    var shown by remember { mutableIntStateOf(count) }

    // 1장이 늘었을 때만 뽑은 것이다. 새 게임으로 장수가 바뀌어도 움직이지 않는다.
    LaunchedEffect(count) {
        val isDraw = count == shown + 1
        shown = count
        if (isDraw) {
            drawn.snapTo(1f)
            drawn.animateTo(0f, tween(CPU_DRAW_MILLIS))
        }
    }

    Column(modifier) {
        Text(
            "$name ${count}장",
            color = if (active || drawn.value > 0f) colors.primary else colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (active || drawn.value > 0f) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
        BoxWithConstraints(Modifier.fillMaxWidth().height(CpuTileHeight)) {
            val step = if (count > 1) min(CpuTileStep, (maxWidth - CpuTileWidth) / (count - 1)) else 0.dp
            repeat(count) { index ->
                // 방금 뽑은 맨 끝 타일은 위에서 내려오며 밝은색에서 원래 색으로 돌아온다.
                val blend = if (index == count - 1) drawn.value else 0f
                Box(
                    Modifier
                        .offset(x = step * index, y = -CpuDrawDrop * blend)
                        .size(CpuTileWidth, CpuTileHeight)
                        .background(lerp(CpuTileBack, CpuTileDrawn, blend), TileShape)
                        .border(1.dp, CpuTileEdge, TileShape),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RummikubTable(game: RummikubGame, width: Dp, viewModel: MainViewModel, modifier: Modifier) {
    val tableSize by viewModel.rummikubTableSize.collectAsStateWithLifecycle()
    val tileWidth = min(MaxTableTile, width / TABLE_TILES_WIDE) * tableSize.scale
    val tileHeight = tileWidth * TILE_RATIO
    val colors = MaterialTheme.colorScheme

    FlowRow(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        game.table.forEachIndexed { index, meld ->
            val valid = Rummikub.isValid(meld)
            // 타일을 크게 하면 긴 묶음이 한 줄을 넘으므로 다음 줄로 내린다.
            FlowRow(
                Modifier
                    .border(
                        BorderStroke(1.dp, if (valid) colors.outlineVariant else colors.error),
                        TileShape,
                    )
                    .clickable(enabled = game.picked.isNotEmpty()) { viewModel.moveRummikubTo(index) }
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(TileGap),
                verticalArrangement = Arrangement.spacedBy(TileGap),
            ) {
                meld.forEach { tile ->
                    RummikubTileView(tile, game, tileWidth, tileHeight) { viewModel.tapRummikubTile(tile) }
                }
            }
        }
        if (game.isHumanTurn && game.picked.isNotEmpty()) {
            Box(
                Modifier
                    .size(tileHeight * 2, tileHeight + 6.dp)
                    .border(1.dp, colors.primary, TileShape)
                    .clickable(onClick = viewModel::newRummikubMeld),
                contentAlignment = Alignment.Center,
            ) {
                Text("+ 새 묶음", color = colors.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RummikubRack(
    game: RummikubGame,
    byColor: Boolean,
    onByColorChange: (Boolean) -> Unit,
    width: Dp,
    viewModel: MainViewModel,
    modifier: Modifier,
) {
    val tileWidth = min(MaxRackTile, (width - 8.dp) / RACK_TILES_WIDE)
    val tiles = game.rack.sortedWith(
        if (byColor) {
            compareBy({ it.isJoker }, { it.color }, { it.number })
        } else {
            compareBy({ it.isJoker }, { it.number }, { it.color })
        },
    )

    val colors = MaterialTheme.colorScheme
    val myTurn = game.isHumanTurn
    val glow = remember { Animatable(0f) }

    // 차례가 돌아온 순간 두 번 번쩍여 눈길을 끈다.
    LaunchedEffect(myTurn) {
        glow.snapTo(0f)
        if (myTurn) {
            repeat(TURN_GLOW_COUNT) {
                glow.animateTo(1f, tween(TURN_GLOW_MILLIS))
                glow.animateTo(0f, tween(TURN_GLOW_MILLIS))
            }
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.primaryContainer.copy(alpha = glow.value), TileShape)
            .border(2.dp, if (myTurn) colors.primary else Color.Transparent, TileShape)
            .padding(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (myTurn) "내 차례 · 내 패 ${game.rack.size}장" else "내 패 ${game.rack.size}장",
                color = if (myTurn) colors.primary else Color.Unspecified,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (myTurn) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 4.dp).weight(1f),
            )
            if (game.returnable.isNotEmpty()) {
                TextButton(onClick = viewModel::returnRummikubTiles) { Text("패로 회수") }
            }
            FilterChip(!byColor, { onByColorChange(false) }, { Text("숫자") })
            FilterChip(byColor, { onByColorChange(true) }, { Text("색") }, Modifier.padding(horizontal = 4.dp))
        }
        FlowRow(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            tiles.forEach { tile ->
                RummikubTileView(tile, game, tileWidth, tileWidth * TILE_RATIO) { viewModel.tapRummikubTile(tile) }
            }
        }
    }
}

@Composable
private fun RummikubActions(game: RummikubGame, viewModel: MainViewModel) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedButton(
            onClick = viewModel::resetRummikubTurn,
            enabled = game.isHumanTurn && !game.isUntouched,
            modifier = Modifier.weight(1f),
        ) { Text("되돌리기", maxLines = 1) }
        OutlinedButton(
            onClick = viewModel::drawRummikub,
            enabled = game.isHumanTurn && game.isUntouched,
            modifier = Modifier.weight(1f),
        ) { Text(if (game.pool.isEmpty()) "넘기기" else "뽑기", maxLines = 1) }
        Button(
            onClick = viewModel::finishRummikubTurn,
            enabled = game.isHumanTurn && game.rack.size < game.turnRack.size,
            modifier = Modifier.weight(1f),
        ) { Text("완료", maxLines = 1) }
    }
}

@Composable
private fun RummikubTileView(tile: RummikubTile, game: RummikubGame, width: Dp, height: Dp, onClick: () -> Unit) {
    RummikubTileFace(
        tile,
        width,
        height,
        picked = tile in game.picked,
        recent = tile in game.recent,
        // 판으로 옮긴 타일은 더 깜빡이지 않고, 패로 회수하면 다시 깜빡인다.
        blinking = tile == game.drawn && tile in game.rack,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun rememberBlink(): Float {
    val transition = rememberInfiniteTransition(label = "drawnTile")
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BLINK_MILLIS), RepeatMode.Reverse),
        label = "drawnTileBlink",
    )
    return blink
}

@Composable
fun RummikubTileFace(
    tile: RummikubTile,
    width: Dp,
    height: Dp,
    picked: Boolean = false,
    recent: Boolean = false,
    blinking: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ink = if (tile.isJoker) JokerInk else TileInks[tile.color]
    val blink = if (blinking) rememberBlink() else 0f
    val highlighted = recent || blinking

    Box(
        modifier
            .size(width, height)
            .background(if (picked) TilePicked else lerp(TileFace, TileDrawn, blink), TileShape)
            .border(
                if (highlighted) 2.dp else 1.dp,
                if (highlighted) MaterialTheme.colorScheme.primary else TileLine,
                TileShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (tile.isJoker) "★" else tile.number.toString(),
            color = ink,
            fontSize = with(LocalDensity.current) { (width * 0.55f).toSp() },
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
        )
    }
}
