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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TileFace = Color(0xFFF7F1E1)
private val TileEdge = Color(0xFFB9A67E)
private val TileLine = Color(0xFF7A6A4A)
private val TileBlocked = Color(0xFFDAD2BE)
private val TileSelected = Color(0xFFFFD54F)
private val TileHint = Color(0xFFAED581)
private val InkRed = Color(0xFFB71C1C)
private val InkBlue = Color(0xFF1565C0)
private val InkGreen = Color(0xFF2E7D32)
private val InkBlack = Color(0xFF212121)

private val TileShape = RoundedCornerShape(4.dp)
private val LayerShift = 3.dp
private const val TILE_RATIO = 1.3f

private val NUMERALS = listOf("一", "二", "三", "四", "五", "六", "七", "八", "九")
private val HONORS = listOf("東", "南", "西", "北", "中", "發", "白")

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShanghaiTopBar(viewModel: MainViewModel, onBack: () -> Unit) {
    var recordsOpen by rememberSaveable { mutableStateOf(false) }
    val records by viewModel.shanghaiRecords.collectAsStateWithLifecycle()

    if (recordsOpen) {
        GameRecordsDialog(
            records.map { "${formatDuration(it.seconds)} · 섞은 횟수 ${it.shuffles}" to it.finishedAt },
            onDismiss = { recordsOpen = false },
        )
    }

    TopAppBar(
        title = { Text("상하이") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = {
            TextButton(onClick = { recordsOpen = true }) { Text("기록") }
            TextButton(onClick = viewModel::newShanghai) { Text("새 게임") }
        },
    )
}

@Composable
fun ShanghaiScreen(viewModel: MainViewModel) {
    val current by viewModel.shanghai.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (current == null) viewModel.newShanghai() }

    // 이 화면을 보고 있는 동안만 시간을 잰다.
    LifecycleResumeEffect(Unit) {
        val timer = scope.launch {
            while (true) {
                delay(1000)
                viewModel.tickShanghai()
            }
        }
        onPauseOrDispose {
            timer.cancel()
            viewModel.saveShanghai()
        }
    }

    val game = current
    if (game == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val depth = LayerShift * 3
        if (maxWidth > maxHeight) {
            val tileWidth = min(
                (maxWidth - 160.dp - depth) / Shanghai.COLUMNS,
                (maxHeight - depth) / Shanghai.ROWS / TILE_RATIO,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ShanghaiBoard(game, tileWidth, viewModel)
                ShanghaiControls(game, viewModel, Modifier.weight(1f))
            }
        } else {
            val tileWidth = min(
                (maxWidth - depth) / Shanghai.COLUMNS,
                (maxHeight - 100.dp - depth) / Shanghai.ROWS / TILE_RATIO,
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ShanghaiBoard(game, tileWidth, viewModel)
                ShanghaiControls(game, viewModel, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ShanghaiBoard(game: ShanghaiGame, tileWidth: Dp, viewModel: MainViewModel) {
    val tileHeight = tileWidth * TILE_RATIO

    Box(
        Modifier.size(
            tileWidth * Shanghai.COLUMNS + LayerShift * 3,
            tileHeight * Shanghai.ROWS + LayerShift * 3,
        ),
    ) {
        Shanghai.SPOTS.forEachIndexed { index, spot ->
            val face = game.faces[index]
            if (face != Shanghai.EMPTY) {
                val free = game.isFree(index)
                ShanghaiTile(
                    face,
                    layer = spot.layer,
                    background = when {
                        index == game.selected -> TileSelected
                        game.hint?.let { index == it.first || index == it.second } == true -> TileHint
                        free -> TileFace
                        else -> TileBlocked
                    },
                    width = tileWidth,
                    height = tileHeight,
                    modifier = Modifier
                        .offset(
                            tileWidth * spot.column + LayerShift * (3 - spot.layer),
                            tileHeight * spot.row + LayerShift * (3 - spot.layer),
                        )
                        .clickable(enabled = free) { viewModel.pickShanghai(index) },
                )
            }
        }
    }
}

@Composable
private fun ShanghaiTile(face: Int, layer: Int, background: Color, width: Dp, height: Dp, modifier: Modifier) {
    // 높은 층일수록 옆면을 두껍게 그려 옆 패와 높이 차이가 보이게 한다.
    val side = 2.dp + 1.dp * layer

    Box(
        modifier
            .size(width, height)
            .background(TileEdge, TileShape)
            .border(1.dp, TileLine, TileShape)
            .padding(end = side, bottom = side)
            .background(background, TileShape)
            .border(1.dp, TileLine, TileShape)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (face / 9) {
            0 -> NumberFace(face % 9, height)
            1 -> PipFace(face % 9, InkBlue, round = true)
            2 -> PipFace(face % 9, InkGreen, round = false)
            else -> HonorFace(face - 27, height)
        }
    }
}

@Composable
private fun NumberFace(number: Int, height: Dp) {
    val fontSize = with(LocalDensity.current) { (height * 0.3f).toSp() }
    Text(
        "${NUMERALS[number]}\n萬",
        color = InkRed,
        fontSize = fontSize,
        lineHeight = fontSize,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun HonorFace(honor: Int, height: Dp) {
    Text(
        HONORS[honor],
        color = when (HONORS[honor]) {
            "中" -> InkRed
            "發" -> InkGreen
            else -> InkBlack
        },
        fontSize = with(LocalDensity.current) { (height * 0.5f).toSp() },
        fontWeight = FontWeight.Bold,
    )
}

/** 통수는 동그라미, 삭수는 세로 막대로 무늬를 그린다. */
@Composable
private fun PipFace(number: Int, color: Color, round: Boolean) {
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

@Composable
private fun ShanghaiControls(game: ShanghaiGame, viewModel: MainViewModel, modifier: Modifier) {
    Column(modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when {
                game.isComplete -> "모두 맞췄습니다! ${formatDuration(game.seconds)} · 섞은 횟수 ${game.shuffles}"
                game.freePair == null -> "더 맞출 수 있는 패가 없습니다. 섞어 보세요."
                else -> "남은 패 ${game.remaining}"
            },
            color = if (game.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::showShanghaiHint, enabled = game.freePair != null) { Text("힌트") }
            TextButton(onClick = viewModel::shuffleShanghai, enabled = !game.isComplete) { Text("섞기") }
        }
    }
}
