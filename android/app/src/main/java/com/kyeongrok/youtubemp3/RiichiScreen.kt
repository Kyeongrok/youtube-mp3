package com.kyeongrok.youtubemp3

import android.app.Application
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class RiichiRecord(val finishedAt: Long, val rank: Int, val points: Int, val length: Int)

private const val SAVE_FILE = "riichi-save.json"
private const val RECORD_FILE = "riichi-records.json"
private const val AI_DELAY_MS = 450L

class RiichiViewModel(application: Application) : AndroidViewModel(application) {
    var table: RiichiTable? = load()
        private set

    private val mutableVersion = MutableStateFlow(0)
    /** 판이 바뀔 때마다 오른다. 화면은 이걸 보고 다시 그린다. */
    val version = mutableVersion.asStateFlow()

    private val mutableRecords = MutableStateFlow(loadRecords())
    val records = mutableRecords.asStateFlow()

    private var driver: Job? = null

    private fun load(): RiichiTable? = try {
        RecordFile.read(getApplication(), SAVE_FILE)
            ?.let { RiichiTable.fromJson(it.getJSONObject(0)) }
            ?.takeUnless { it.phase == RiichiPhase.GameOver }
    } catch (_: Exception) {
        null
    }

    private fun loadRecords(): List<RiichiRecord> = try {
        RecordFile.read(getApplication(), RECORD_FILE)?.let { array ->
            List(array.length()) {
                val item = array.getJSONObject(it)
                RiichiRecord(item.getLong("finishedAt"), item.getInt("rank"), item.getInt("points"), item.getInt("length"))
            }
        }.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun changed() {
        mutableVersion.value++
        table?.let { RecordFile.write(getApplication(), SAVE_FILE, JSONArray().put(it.toJson())) }
    }

    fun newGame(length: Int) {
        driver?.cancel()
        table = RiichiTable.newGame(length)
        changed()
        drive()
    }

    /** 컴퓨터 차례와 리치 뒤 자동 버리기를 사람 차례가 올 때까지 이어서 한다. */
    fun drive() {
        if (driver?.isActive == true) return
        driver = viewModelScope.launch {
            while (true) {
                val table = table ?: break
                if (table.phase != RiichiPhase.Turn) break
                val me = table.seats[RiichiTable.HUMAN]
                when {
                    table.turn != RiichiTable.HUMAN -> {
                        delay(AI_DELAY_MS)
                        table.aiTurn()
                    }
                    me.riichi && table.drawn >= 0 && table.tsumoScore(RiichiTable.HUMAN) == null -> {
                        delay(AI_DELAY_MS + 300)
                        table.discard(RiichiTable.HUMAN, table.drawn)
                    }
                    else -> break
                }
                changed()
            }
        }
    }

    private fun act(action: (RiichiTable) -> Unit) {
        val table = table ?: return
        action(table)
        changed()
        drive()
    }

    fun discard(tile: Int, riichi: Boolean) = act { it.discard(RiichiTable.HUMAN, tile, riichi) }
    fun tsumo() = act { it.tsumo(RiichiTable.HUMAN) }
    fun kan(kind: Int) = act { it.kan(RiichiTable.HUMAN, kind) }
    fun claim(choice: ClaimChoice) = act { it.humanClaim(choice) }

    fun nextHand() = act { table ->
        table.nextHand()
        if (table.phase == RiichiPhase.GameOver) {
            val rank = table.ranking().indexOf(RiichiTable.HUMAN) + 1
            val record = RiichiRecord(System.currentTimeMillis(), rank, table.seats[RiichiTable.HUMAN].points, table.length)
            mutableRecords.value = listOf(record) + mutableRecords.value
            RecordFile.write(getApplication(), RECORD_FILE, JSONArray(mutableRecords.value.map {
                JSONObject().put("finishedAt", it.finishedAt).put("rank", it.rank).put("points", it.points).put("length", it.length)
            }))
        }
    }
}

private fun lengthName(length: Int) = if (length == 4) "동풍전" else "반장전"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiichiTopBar(onBack: () -> Unit) {
    val viewModel: RiichiViewModel = viewModel()
    var recordsOpen by rememberSaveable { mutableStateOf(false) }
    var newGameOpen by rememberSaveable { mutableStateOf(false) }
    val records by viewModel.records.collectAsStateWithLifecycle()

    if (recordsOpen) {
        GameRecordsDialog(
            records.map { "${it.rank}위 · ${it.points}점 · ${lengthName(it.length)}" to it.finishedAt },
            onDismiss = { recordsOpen = false },
        )
    }
    if (newGameOpen) {
        NewRiichiGameDialog(
            onStart = {
                newGameOpen = false
                viewModel.newGame(it)
            },
            onDismiss = { newGameOpen = false },
        )
    }

    TopAppBar(
        title = { Text("리치마작") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = {
            TextButton(onClick = { recordsOpen = true }) { Text("기록") }
            TextButton(onClick = { newGameOpen = true }) { Text("새 게임") }
        },
    )
}

@Composable
private fun NewRiichiGameDialog(onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 게임") },
        text = { Text("컴퓨터 3명과 25000점으로 시작합니다. 동풍전은 4판, 반장전은 8판 이상입니다.") },
        confirmButton = {
            Row {
                TextButton(onClick = { onStart(4) }) { Text("동풍전") }
                TextButton(onClick = { onStart(8) }) { Text("반장전") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private val TableGreen = Color(0xFF2E5E46)
private val TileBack = Color(0xFF3D8B63)
private val RedFive = Color(0xFFFFE1E1)
private val SelectedTile = Color(0xFFFFD54F)
private val CalledTile = Color(0xFFD6D0C0)
private val RiichiMark = Color(0xFFFF7043)
private val RiichiTileShape = RoundedCornerShape(3.dp)
private const val TILE_RATIO = 1.35f

/** 패 한 장. back이면 뒷면을 그린다. */
@Composable
private fun RiichiTile(
    tile: Int,
    width: Dp,
    modifier: Modifier = Modifier,
    background: Color? = null,
    dim: Boolean = false,
    back: Boolean = false,
) {
    val height = width * TILE_RATIO
    val face = when {
        back -> TileBack
        background != null -> background
        Mahjong.isRed(tile) -> RedFive
        else -> MjTileFace
    }
    Box(
        modifier
            .size(width, height)
            .alpha(if (dim) 0.4f else 1f)
            .background(MjTileEdge, RiichiTileShape)
            .padding(end = 1.dp, bottom = 2.dp)
            .background(face, RiichiTileShape)
            .border(0.5.dp, MjTileLine, RiichiTileShape)
            .padding(1.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (!back) MahjongFace(Mahjong.kind(tile), height, red = Mahjong.isRed(tile))
    }
}

@NonSkippableComposable
@Composable
private fun MeldRow(melds: List<Meld>, width: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        melds.forEach { meld ->
            Row {
                meld.tiles.forEachIndexed { index, tile ->
                    RiichiTile(
                        tile,
                        width,
                        background = if (tile == meld.called) CalledTile else null,
                        // 안깡은 양 끝을 뒷면으로 보여 준다.
                        back = meld.type == MeldType.ClosedKan && (index == 0 || index == 3),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@NonSkippableComposable
@Composable
private fun River(discards: List<RiichiDiscard>, width: Dp) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        discards.forEach { discard ->
            RiichiTile(
                discard.tile,
                width,
                background = if (discard.riichi) RiichiMark.copy(alpha = 0.55f).compositeOverTile() else null,
                dim = discard.called,
            )
        }
    }
}

private fun Color.compositeOverTile(): Color {
    val a = alpha
    return Color(
        red = red * a + MjTileFace.red * (1 - a),
        green = green * a + MjTileFace.green * (1 - a),
        blue = blue * a + MjTileFace.blue * (1 - a),
    )
}

@NonSkippableComposable
@Composable
private fun SeatHeader(table: RiichiTable, player: Int) {
    val seat = table.seats[player]
    val active = table.phase == RiichiPhase.Turn && table.turn == player
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            Mahjong.WIND_NAMES[table.seatWind(player)],
            color = if (player == table.dealer) RiichiMark else Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(RiichiTable.seatName(player), color = Color.White, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
        Text("${seat.points}", color = Color.White)
        if (seat.riichi) Text("리치", color = RiichiMark, fontWeight = FontWeight.Bold)
        if (player != RiichiTable.HUMAN) Text("손패 ${seat.hand.size}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        if (active) Text("◀ 차례", color = SelectedTile, style = MaterialTheme.typography.bodySmall)
    }
}

@NonSkippableComposable
@Composable
private fun SeatPanel(table: RiichiTable, player: Int, small: Dp) {
    val seat = table.seats[player]
    val active = table.phase == RiichiPhase.Turn && table.turn == player
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (active) Color.White.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SeatHeader(table, player)
        // 내 운 패는 손패 바로 위에 따로 보여 준다.
        if (player != RiichiTable.HUMAN && seat.melds.isNotEmpty()) MeldRow(seat.melds, small)
        River(seat.discards, small)
    }
}

@Composable
fun RiichiScreen() {
    val viewModel: RiichiViewModel = viewModel()
    // version을 먼저 읽어 두어야 판이 바뀔 때 이 화면이 다시 그려진다.
    val version = viewModel.version.collectAsStateWithLifecycle().value
    LaunchedEffect(Unit) {
        if (viewModel.table == null) viewModel.newGame(4) else viewModel.drive()
    }
    val table = viewModel.table
    if (table == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (version < 0) return

    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var riichiMode by rememberSaveable { mutableStateOf(false) }
    val myTurn = table.phase == RiichiPhase.Turn && table.turn == RiichiTable.HUMAN
    if (!myTurn && riichiMode) riichiMode = false
    val me = table.seats[RiichiTable.HUMAN]
    if (selected != null && selected !in me.hand) selected = null

    table.result?.let { result ->
        if (table.phase == RiichiPhase.HandOver) HandResultDialog(table, result, onNext = viewModel::nextHand)
    }
    if (table.phase == RiichiPhase.GameOver) {
        GameOverDialog(table, onNewGame = viewModel::newGame)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(TableGreen).padding(6.dp)) {
        // 내 손패는 두 줄로 크게 보여 준다. 한 줄에 7장과 뽑은 패 자리까지 들어가게 한다.
        val handWidth = min((maxWidth - 24.dp) / 8, 60.dp)
        // 버림패는 한 줄에 10장쯤 들어가게 키운다. 많아지면 위쪽 칸이 스크롤된다.
        val small = min((maxWidth - 24.dp) / 10, 40.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InfoBar(table, small)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 차례가 도는 순서(하가 → 대면 → 상가)로 보여 준다.
                for (player in 1..3) SeatPanel(table, player, small)
                SeatPanel(table, RiichiTable.HUMAN, small)
            }
            if (me.melds.isNotEmpty()) MeldRow(me.melds, small)
            Hand(
                table,
                handWidth,
                selected,
                allowed = when {
                    !myTurn -> emptySet()
                    riichiMode -> table.riichiDiscards(RiichiTable.HUMAN)
                    else -> me.hand.filter { table.canDiscard(RiichiTable.HUMAN, it) }.toSet()
                },
                onTap = { tile, allowed ->
                    if (selected == tile && allowed) {
                        viewModel.discard(tile, riichi = riichiMode)
                        selected = null
                        riichiMode = false
                    } else {
                        selected = tile
                    }
                },
            )
            Actions(
                table,
                viewModel,
                selected,
                riichiMode,
                onRiichiMode = {
                    riichiMode = it
                    selected = null
                },
                small = small,
            )
        }
    }
}

@NonSkippableComposable
@Composable
private fun InfoBar(table: RiichiTable, small: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${table.roundName} · ${table.honba}본장 · 공탁 ${table.sticks * 1000} · 남은 패 ${table.remaining}",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text("도라", color = Color.White, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            table.doraIndicators.forEach { RiichiTile(it, small) }
        }
    }
}

@NonSkippableComposable
@Composable
private fun Hand(
    table: RiichiTable,
    width: Dp,
    selected: Int?,
    allowed: Set<Int>,
    onTap: (tile: Int, allowed: Boolean) -> Unit,
) {
    val me = table.seats[RiichiTable.HUMAN]
    val drawn = table.drawn.takeIf { table.turn == RiichiTable.HUMAN && it in me.hand }
    val sorted = me.hand.filter { it != drawn }.sorted()
    val myTurn = table.phase == RiichiPhase.Turn && table.turn == RiichiTable.HUMAN

    @Composable
    fun tile(tile: Int) {
        val canDiscard = tile in allowed
        RiichiTile(
            tile,
            width,
            modifier = Modifier
                .offset(y = if (tile == selected) (-8).dp else 0.dp)
                .clickable { onTap(tile, canDiscard) },
            background = if (tile == selected) SelectedTile else null,
            dim = myTurn && !canDiscard,
        )
    }
    val firstRow = (sorted.size + 1) / 2
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            sorted.take(firstRow).forEach { tile(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            sorted.drop(firstRow).forEach { tile(it) }
            if (drawn != null) {
                Spacer(Modifier.width(8.dp))
                tile(drawn)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@NonSkippableComposable
@Composable
private fun Actions(
    table: RiichiTable,
    viewModel: RiichiViewModel,
    selected: Int?,
    riichiMode: Boolean,
    onRiichiMode: (Boolean) -> Unit,
    small: Dp,
) {
    val me = table.seats[RiichiTable.HUMAN]
    val myTurn = table.phase == RiichiPhase.Turn && table.turn == RiichiTable.HUMAN
    val waits = if (me.hand.size % 3 == 1) me.waits() else emptyList()

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val status = buildString {
            selected?.let { append(Mahjong.tileName(it)) }
            if (waits.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("대기 ")
                append(waits.joinToString(", ") { Mahjong.kindName(it) })
                if (me.isFuriten()) append(" (후리텐)")
            }
        }
        Text(
            status.ifEmpty {
                when {
                    riichiMode -> "리치하며 버릴 패를 두 번 누르세요"
                    myTurn -> "버릴 패를 두 번 누르세요"
                    table.phase == RiichiPhase.Claim -> ""
                    else -> table.message
                }
            },
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when {
                table.phase == RiichiPhase.Claim -> {
                    val options = table.claimOptions()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "${RiichiTable.seatName(table.claimFrom)}${if (table.chankan) " 가깡" else ""}:",
                            color = Color.White,
                        )
                        RiichiTile(table.claimTile, small)
                    }
                    if (options.ron) Button(onClick = { viewModel.claim(ClaimChoice.Ron) }) { Text("론") }
                    if (options.pon != null) OutlinedButton(onClick = { viewModel.claim(ClaimChoice.Pon) }) { Text("퐁", color = Color.White) }
                    if (options.kan != null) OutlinedButton(onClick = { viewModel.claim(ClaimChoice.Kan) }) { Text("깡", color = Color.White) }
                    options.chi.forEach { tiles ->
                        OutlinedButton(onClick = { viewModel.claim(ClaimChoice.Chi(tiles)) }) {
                            Text("치 " + tiles.joinToString("·") { Mahjong.kindName(Mahjong.kind(it)) }, color = Color.White)
                        }
                    }
                    TextButton(onClick = { viewModel.claim(ClaimChoice.Pass) }) { Text("패스", color = Color.White) }
                }
                myTurn -> {
                    if (table.tsumoScore(RiichiTable.HUMAN) != null) {
                        Button(onClick = viewModel::tsumo) { Text("쯔모") }
                    }
                    if (table.riichiDiscards(RiichiTable.HUMAN).isNotEmpty()) {
                        if (riichiMode) {
                            TextButton(onClick = { onRiichiMode(false) }) { Text("리치 취소", color = Color.White) }
                        } else {
                            OutlinedButton(onClick = { onRiichiMode(true) }) { Text("리치", color = Color.White) }
                        }
                    }
                    table.kanOptions(RiichiTable.HUMAN).forEach { kind ->
                        OutlinedButton(onClick = { viewModel.kan(kind) }) { Text("깡 ${Mahjong.kindName(kind)}", color = Color.White) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@NonSkippableComposable
@Composable
private fun HandResultDialog(table: RiichiTable, result: RiichiHandResult, onNext: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(result.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!result.draw) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        result.hand.forEach { tile ->
                            RiichiTile(tile, 20.dp, background = if (tile == result.winTile) SelectedTile else null)
                        }
                    }
                    if (result.melds.isNotEmpty()) MeldRow(result.melds, 18.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("도라 표시", style = MaterialTheme.typography.bodySmall)
                        result.dora.forEach { RiichiTile(it, 16.dp) }
                        if (result.ura.isNotEmpty()) {
                            Text(" 뒷도라", style = MaterialTheme.typography.bodySmall)
                            result.ura.forEach { RiichiTile(it, 16.dp) }
                        }
                    }
                    result.yaku.forEach { yaku ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(yaku.name, Modifier.weight(1f))
                            Text(if (yaku.han >= 13) "역만" else "${yaku.han}판")
                        }
                    }
                }
                Text(result.summary, fontWeight = FontWeight.Bold)
                for (player in 0 until 4) {
                    val delta = result.deltas[player]
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            RiichiTable.seatName(player) + if (result.draw && result.tenpai[player]) " (텐파이)" else "",
                            Modifier.weight(1f),
                        )
                        Text(
                            (if (delta > 0) "+$delta" else if (delta < 0) "$delta" else "±0") + "  → ${table.seats[player].points}",
                            color = when {
                                delta > 0 -> MaterialTheme.colorScheme.primary
                                delta < 0 -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNext) { Text("다음") } },
    )
}

@NonSkippableComposable
@Composable
private fun GameOverDialog(table: RiichiTable, onNewGame: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("대국 종료") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                table.ranking().forEachIndexed { index, player ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "${index + 1}위  ${RiichiTable.seatName(player)}",
                            Modifier.weight(1f),
                            fontWeight = if (player == RiichiTable.HUMAN) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text("${table.seats[player].points}")
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onNewGame(4) }) { Text("새 동풍전") }
                TextButton(onClick = { onNewGame(8) }) { Text("새 반장전") }
            }
        },
    )
}
