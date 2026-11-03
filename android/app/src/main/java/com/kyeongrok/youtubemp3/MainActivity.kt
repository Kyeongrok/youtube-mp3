package com.kyeongrok.youtubemp3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

// 목록 끝 항목이 화면 아래에 붙지 않도록 조금 띄운다.
internal val ListPadding = PaddingValues(bottom = 16.dp)

private enum class MiniGame { None, Sudoku, Shanghai, FreeCell, Rummikub, Splendor, Saju }

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        handleShare(intent)

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
            val dark = isDarkTheme(themeMode)
            // 앱 테마가 시스템과 달라도 상태바 아이콘이 배경과 대비되도록 맞춘다.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(dark, colorTheme) {
                MainScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let(MainViewModel::extractUrl)?.let { url ->
            viewModel.download(url, url)
        }
        // 화면을 돌려도 같은 공유를 다시 처리하지 않도록 비운다.
        intent.action = null
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun isDarkTheme(mode: ThemeMode) = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
private fun AppTheme(dark: Boolean, color: ColorTheme, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme() else lightScheme(color),
        content = { Surface(Modifier.fillMaxSize(), content = content) },
    )
}

private fun lightScheme(color: ColorTheme): ColorScheme {
    if (color == ColorTheme.Purple) return lightColorScheme()
    val primary = ComposeColor(color.primary)
    val container = ComposeColor(color.container)
    val onContainer = ComposeColor(color.onContainer)
    val secondaryContainer = ComposeColor(color.secondaryContainer)
    val background = ComposeColor(color.background)
    val variant = ComposeColor(color.surfaceVariant)
    return lightColorScheme(
        primary = primary,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = primary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onContainer,
        tertiary = primary,
        tertiaryContainer = container,
        onTertiaryContainer = onContainer,
        background = background,
        surface = background,
        surfaceVariant = variant,
        surfaceTint = primary,
        surfaceContainerLowest = ComposeColor.White,
        surfaceContainerLow = background,
        surfaceContainer = variant,
        surfaceContainerHigh = variant,
        surfaceContainerHighest = variant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val downloads by Downloads.items.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
    var updateAsked by rememberSaveable { mutableStateOf(false) }
    var game by rememberSaveable { mutableStateOf(MiniGame.None) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(game != MiniGame.None) { game = MiniGame.None }

    if (settingsOpen) SettingsDialog(viewModel, onDismiss = { settingsOpen = false })

    update?.takeIf { !updateAsked }?.let { release ->
        AlertDialog(
            onDismissRequest = { updateAsked = true },
            title = { Text("새 버전") },
            text = { Text("새 버전 ${release.version}이 나왔습니다. 받아서 설치할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    updateAsked = true
                    viewModel.installUpdate()
                }) { Text("설치") }
            },
            dismissButton = { TextButton(onClick = { updateAsked = true }) { Text("나중에") } },
        )
    }

    Scaffold(
        topBar = {
            when (game) {
                MiniGame.Sudoku -> SudokuTopBar(viewModel, onBack = { game = MiniGame.None })
                MiniGame.Shanghai -> ShanghaiTopBar(viewModel, onBack = { game = MiniGame.None })
                MiniGame.FreeCell -> FreeCellTopBar(viewModel, onBack = { game = MiniGame.None })
                MiniGame.Rummikub -> RummikubTopBar(viewModel, onBack = { game = MiniGame.None })
                MiniGame.Splendor -> SplendorTopBar(viewModel, onBack = { game = MiniGame.None })
                MiniGame.Saju -> SajuTopBar(onBack = { game = MiniGame.None })
                MiniGame.None -> TopAppBar(
                    navigationIcon = { MenuButton(onGame = { game = it }, onSettings = { settingsOpen = true }) },
                    title = {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("YoutubeMp3")
                            Text(
                                "v${BuildConfig.VERSION_NAME}",
                                Modifier.padding(start = 8.dp, bottom = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = { PlayerBar() },
    ) { padding ->
        if (game != MiniGame.None) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                when (game) {
                    MiniGame.Sudoku -> SudokuScreen(viewModel)
                    MiniGame.Shanghai -> ShanghaiScreen(viewModel)
                    MiniGame.Rummikub -> RummikubScreen(viewModel)
                    MiniGame.Splendor -> SplendorScreen(viewModel)
                    MiniGame.Saju -> SajuScreen(viewModel)
                    else -> FreeCellScreen(viewModel)
                }
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            update?.let { release ->
                val progress = updateProgress
                if (progress == null) {
                    TextButton(onClick = viewModel::installUpdate, modifier = Modifier.fillMaxWidth()) {
                        Text("새 버전 ${release.version} 설치")
                    }
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("검색") })
                Tab(tab == 1, { tab = 1 }, text = { Text("다운로드 (${downloads.size})") })
                Tab(tab == 2, { tab = 2 }, text = { Text("내 음악") })
                Tab(tab == 3, { tab = 3 }, text = { Text("재생목록") })
            }
            when (tab) {
                0 -> SearchTab(viewModel)
                1 -> DownloadsTab(downloads)
                2 -> LibraryTab(viewModel)
                else -> PlaylistTab(viewModel)
            }
        }
    }
}

@Composable
private fun MenuButton(onGame: (MiniGame) -> Unit, onSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Menu, contentDescription = "메뉴")
        }
        DropdownMenu(expanded, { expanded = false }) {
            listOf(
                Triple("스도쿠", AppIcons.Sudoku, MiniGame.Sudoku),
                Triple("상하이", AppIcons.Shanghai, MiniGame.Shanghai),
                Triple("프리셀", AppIcons.FreeCell, MiniGame.FreeCell),
                Triple("루미큐브", AppIcons.Rummikub, MiniGame.Rummikub),
                Triple("스플렌더", AppIcons.Splendor, MiniGame.Splendor),
                Triple("사주", AppIcons.Saju, MiniGame.Saju),
            ).forEach { (label, icon, target) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { Icon(icon, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onGame(target)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("설정") },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onSettings()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
    val tableSize by viewModel.rummikubTableSize.collectAsStateWithLifecycle()
    val blinkDuration by viewModel.rummikubBlinkDuration.collectAsStateWithLifecycle()
    val dark = isDarkTheme(themeMode)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("설정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("테마", style = MaterialTheme.typography.titleSmall)
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.setThemeMode(mode) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == themeMode, onClick = null)
                        Text(mode.label, Modifier.padding(start = 12.dp))
                    }
                }
                if (!dark) {
                    Text(
                        "색상",
                        Modifier.padding(top = 12.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ColorTheme.entries.forEach { color ->
                            ColorSwatch(color, color == colorTheme) { viewModel.setColorTheme(color) }
                        }
                    }
                }
                Text(
                    "루미큐브 판 타일 크기",
                    Modifier.padding(top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RummikubTableSize.entries.forEach { size ->
                        FilterChip(
                            selected = size == tableSize,
                            onClick = { viewModel.setRummikubTableSize(size) },
                            label = { Text(size.label, maxLines = 1) },
                        )
                    }
                }
                Text(
                    "루미큐브 뽑은 타일 깜빡임",
                    Modifier.padding(top = 12.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RummikubBlinkDuration.entries.forEach { duration ->
                        FilterChip(
                            selected = duration == blinkDuration,
                            onClick = { viewModel.setRummikubBlinkDuration(duration) },
                            label = { Text(duration.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun ColorSwatch(color: ColorTheme, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(ComposeColor(color.primary), CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = color.label },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = ComposeColor.White)
    }
}

@Composable
private fun SearchTab(viewModel: MainViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()

    Column(Modifier.padding(horizontal = 16.dp).fillMaxSize()) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("검색어 또는 주소") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.submit(input) }),
            trailingIcon = {
                IconButton(onClick = { viewModel.submit(input) }) {
                    Icon(Icons.Filled.Search, contentDescription = "검색")
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MainViewModel.QUALITIES.forEach { option ->
                FilterChip(
                    selected = option == quality,
                    onClick = { viewModel.quality.value = option },
                    label = { Text(option) },
                )
            }
        }
        if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, maxLines = 4) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
            items(results, key = { it.url }) { result ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(result.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(result.channel, result.duration).filter(String::isNotEmpty).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                        )
                    }
                    Button(onClick = { viewModel.download(result.title, result.url) }) { Text("받기") }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DownloadsTab(downloads: List<DownloadItem>) {
    Column(Modifier.padding(horizontal = 16.dp).fillMaxSize()) {
        TextButton(onClick = Downloads::clearFinished, modifier = Modifier.align(Alignment.End)) {
            Text("끝난 항목 지우기")
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
            items(downloads.reversed(), key = { it.id }) { item ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.status == DownloadStatus.Queued || item.status == DownloadStatus.Running) {
                            IconButton(onClick = { Downloads.cancel(item.id) }) {
                                Icon(Icons.Filled.Close, contentDescription = "취소")
                            }
                        }
                    }
                    if (item.status == DownloadStatus.Running) {
                        LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        item.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.status == DownloadStatus.Failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 4,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LibraryTab(viewModel: MainViewModel) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refreshTracks() }

    if (tracks.isEmpty()) {
        Text("받은 음악이 없습니다. 파일은 Music/YoutubeMp3에 저장됩니다.", Modifier.padding(16.dp))
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        items(tracks, key = { it.id }) { track ->
            Row(
                Modifier.fillMaxWidth().clickable { Player.play(context, track) }.padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    track.title,
                    Modifier.weight(1f).padding(horizontal = 8.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { viewModel.delete(track) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "삭제")
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun PlayerBar() {
    val state by Player.state.collectAsStateWithLifecycle()
    val track = state.track ?: return
    var dragPosition by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            Player.refresh()
            delay(500)
        }
    }

    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(track.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.queueSize > 1) {
                    IconButton(onClick = Player::previous, enabled = state.queueIndex > 0) {
                        Icon(AppIcons.SkipPrevious, contentDescription = "이전")
                    }
                }
                FilledIconButton(onClick = Player::togglePause) {
                    if (state.isPlaying) {
                        Icon(AppIcons.Pause, contentDescription = "일시정지")
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "재생")
                    }
                }
                if (state.queueSize > 1) {
                    IconButton(onClick = Player::next, enabled = state.queueIndex < state.queueSize - 1) {
                        Icon(AppIcons.SkipNext, contentDescription = "다음")
                    }
                }
            }
            Slider(
                value = dragPosition ?: state.positionMs.toFloat(),
                onValueChange = { dragPosition = it },
                onValueChangeFinished = {
                    dragPosition?.let { Player.seekTo(it.toInt()) }
                    dragPosition = null
                },
                valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
            )
        }
    }
}
