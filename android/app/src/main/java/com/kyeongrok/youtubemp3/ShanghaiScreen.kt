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

private val TileBlocked = Color(0xFFDAD2BE)
private val TileSelected = Color(0xFFFFD54F)
private val TileHint = Color(0xFFAED581)

private val TileShape = RoundedCornerShape(4.dp)
private val LayerShift = 3.dp
private const val TILE_RATIO = 1.3f

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

    // 마지막으로 탭한 패 무늬. 막힌 패도 이름은 볼 수 있다.
    var tappedFace by rememberSaveable { mutableStateOf<Int?>(null) }
    val onTap = { index: Int ->
        current?.let { tappedFace = it.faces[index] }
        viewModel.pickShanghai(index)
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
                ShanghaiBoard(game, tileWidth, onTap)
                ShanghaiControls(game, tappedFace, viewModel, Modifier.weight(1f))
            }
        } else {
            val tileWidth = min(
                (maxWidth - depth) / Shanghai.COLUMNS,
                (maxHeight - 130.dp - depth) / Shanghai.ROWS / TILE_RATIO,
            )
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ShanghaiBoard(game, tileWidth, onTap)
                ShanghaiControls(game, tappedFace, viewModel, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ShanghaiBoard(game: ShanghaiGame, tileWidth: Dp, onTap: (Int) -> Unit) {
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
                        free -> MjTileFace
                        else -> TileBlocked
                    },
                    width = tileWidth,
                    height = tileHeight,
                    modifier = Modifier
                        .offset(
                            tileWidth * spot.column + LayerShift * (3 - spot.layer),
                            tileHeight * spot.row + LayerShift * (3 - spot.layer),
                        )
                        .clickable { onTap(index) },
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
            .background(MjTileEdge, TileShape)
            .border(1.dp, MjTileLine, TileShape)
            .padding(end = side, bottom = side)
            .background(background, TileShape)
            .border(1.dp, MjTileLine, TileShape)
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        MahjongFace(face, height)
    }
}

@Composable
private fun ShanghaiControls(game: ShanghaiGame, tappedFace: Int?, viewModel: MainViewModel, modifier: Modifier) {
    Column(modifier.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            when {
                game.isComplete -> "모두 맞췄습니다! ${formatDuration(game.seconds)} · 섞은 횟수 ${game.shuffles}"
                game.freePair == null -> "더 맞출 수 있는 패가 없습니다. 섞어 보세요."
                else -> "남은 패 ${game.remaining}"
            },
            color = if (game.isComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        if (tappedFace != null && !game.isComplete) {
            Text(
                "패: ${tileName(tappedFace)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = viewModel::showShanghaiHint, enabled = game.freePair != null) { Text("힌트") }
            TextButton(onClick = viewModel::shuffleShanghai, enabled = !game.isComplete) { Text("섞기") }
        }
    }
}
