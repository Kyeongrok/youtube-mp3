package com.kyeongrok.youtubemp3

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SudokuTopBar(viewModel: MainViewModel, onBack: () -> Unit) {
    var levelsOpen by remember { mutableStateOf(false) }
    var recordsOpen by rememberSaveable { mutableStateOf(false) }
    val game by viewModel.sudoku.collectAsStateWithLifecycle()

    if (recordsOpen) SudokuRecordsDialog(viewModel, onDismiss = { recordsOpen = false })

    TopAppBar(
        title = { Text(game?.let { "스도쿠 · ${it.level.label}" } ?: "스도쿠") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = {
            TextButton(onClick = { recordsOpen = true }) { Text("기록") }
            TextButton(onClick = { levelsOpen = true }) { Text("새 게임") }
            DropdownMenu(levelsOpen, { levelsOpen = false }) {
                SudokuLevel.entries.forEach { level ->
                    DropdownMenuItem(
                        text = { Text(level.label) },
                        onClick = {
                            levelsOpen = false
                            viewModel.newSudoku(level)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun SudokuRecordsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val records by viewModel.sudokuRecords.collectAsStateWithLifecycle()

    GameRecordsDialog(
        records.map {
            "${it.level.label} · ${formatDuration(it.seconds)} · 틀린 횟수 ${it.mistakes}" to it.finishedAt
        },
        onDismiss,
    )
}

@Composable
fun SudokuScreen(viewModel: MainViewModel) {
    val current by viewModel.sudoku.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    var noteMode by rememberSaveable { mutableStateOf(false) }
    var focused by rememberSaveable { mutableStateOf<Int?>(null) }
    val onSelect = { index: Int ->
        selected = if (index == selected) null else index
        focused = null
    }
    val onFocus = { digit: Int ->
        selected = null
        focused = if (digit == focused) null else digit
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { if (current == null) viewModel.newSudoku(SudokuLevel.Medium) }

    // 이 화면을 보고 있는 동안만 시간을 잰다.
    LifecycleResumeEffect(Unit) {
        val timer = scope.launch {
            while (true) {
                delay(1000)
                viewModel.tickSudoku()
            }
        }
        onPauseOrDispose {
            timer.cancel()
            viewModel.saveSudoku()
        }
    }

    val game = current
    if (game == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val boardSize = min(maxWidth, maxHeight)
        if (maxWidth > maxHeight) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SudokuBoard(game, selected, focused, boardSize, onSelect)
                SudokuPad(game, selected, focused, noteMode, { noteMode = it }, onFocus, viewModel, Modifier.weight(1f))
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SudokuBoard(game, selected, focused, boardSize, onSelect)
                SudokuPad(game, selected, focused, noteMode, { noteMode = it }, onFocus, viewModel, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SudokuBoard(game: SudokuGame, selected: Int?, focused: Int?, size: Dp, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fontSize = with(LocalDensity.current) { (size / 18).toSp() }
    val noteFontSize = with(LocalDensity.current) { (size / 38).toSp() }
    val highlighted = focused ?: selected?.let { game.cells[it] } ?: 0

    Column(
        Modifier.size(size).drawWithContent {
            drawContent()
            val step = this.size.width / 9
            for (line in 0..9) {
                val stroke = if (line % 3 == 0) 2.dp.toPx() else 0.5.dp.toPx()
                val position = (step * line).coerceIn(stroke / 2, this.size.width - stroke / 2)
                drawLine(colors.onSurface, Offset(position, 0f), Offset(position, this.size.height), stroke)
                drawLine(colors.onSurface, Offset(0f, position), Offset(this.size.width, position), stroke)
            }
        },
    ) {
        for (row in 0 until 9) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (column in 0 until 9) {
                    val index = row * 9 + column
                    val digit = game.cells[index]
                    val background = when {
                        index == selected -> colors.primaryContainer
                        digit != 0 && digit == highlighted -> colors.secondaryContainer
                        selected != null && Sudoku.arePeers(selected, index) -> colors.surfaceVariant
                        else -> colors.surface
                    }
                    Box(
                        Modifier.weight(1f).fillMaxHeight().background(background).clickable { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (digit != 0) {
                            Text(
                                digit.toString(),
                                color = when {
                                    index in game.wrong -> colors.error
                                    game.isGiven(index) -> colors.onSurface
                                    else -> colors.primary
                                },
                                fontSize = fontSize,
                                fontWeight = when {
                                    digit == focused -> FontWeight.Black
                                    game.isGiven(index) -> FontWeight.Bold
                                    else -> FontWeight.Normal
                                },
                            )
                        } else if (game.notes[index].isNotEmpty()) {
                            SudokuNotes(game.notes[index], focused, noteFontSize)
                        }
                    }
                }
            }
        }
    }
}

/** 칸 안을 3×3으로 나눠 메모 숫자를 제자리에 적는다. */
@Composable
private fun SudokuNotes(note: Set<Int>, focused: Int?, fontSize: TextUnit) {
    Column(Modifier.fillMaxSize()) {
        for (row in 0 until 3) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                for (column in 0 until 3) {
                    val digit = row * 3 + column + 1
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (digit in note) {
                            Text(
                                digit.toString(),
                                color = if (digit == focused) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = fontSize,
                                lineHeight = fontSize,
                                fontWeight = if (digit == focused) FontWeight.Black else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SudokuPad(
    game: SudokuGame,
    selected: Int?,
    focused: Int?,
    noteMode: Boolean,
    onNoteModeChange: (Boolean) -> Unit,
    onFocus: (Int) -> Unit,
    viewModel: MainViewModel,
    modifier: Modifier,
) {
    val editable = selected != null && !game.isLocked(selected) && !game.isComplete
    val filled = selected != null && game.cells[selected] != 0

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            for (digit in 1..9) {
                // 틀린 칸은 세지 않아야 남은 개수가 0이 되어 정답을 못 넣는 일이 없다.
                val left = 9 - game.cells.indices.count { game.cells[it] == digit && game.solution[it] == digit }
                TextButton(
                    onClick = {
                        // 숫자를 넣을 칸이 없으면 판에서 그 숫자를 찾아 보여 준다.
                        when {
                            !editable -> onFocus(digit)
                            noteMode -> viewModel.toggleSudokuNote(selected!!, digit)
                            else -> viewModel.enterSudoku(selected!!, digit)
                        }
                    },
                    enabled = !game.isComplete && (!editable || (!(noteMode && filled) && left > 0)),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            digit.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (digit == focused) FontWeight.Black else FontWeight.Normal,
                        )
                        Text(left.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        if (game.isComplete) {
            Text(
                "완성했습니다! ${formatDuration(game.seconds)} · 틀린 횟수 ${game.mistakes}",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { selected?.let { viewModel.enterSudoku(it, 0) } },
                    enabled = editable && (filled || game.notes[selected!!].isNotEmpty()),
                ) { Text("지우기") }
                FilterChip(
                    selected = noteMode,
                    onClick = { onNoteModeChange(!noteMode) },
                    label = { Text("메모") },
                )
            }
        }
    }
}
