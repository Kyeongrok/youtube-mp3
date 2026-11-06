package com.kyeongrok.youtubemp3

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ElevatedCard
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
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
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.CircularProgressIndicator

// 목록 끝 항목이 화면 아래에 붙지 않도록 조금 띄운다.
internal val ListPadding = PaddingValues(bottom = 16.dp)

/** 앱 안의 화면들이다. None은 음악(YoutubeMp3) 화면이고 Home은 앱을 고르는 첫 화면이다. */
private enum class MiniGame { Home, None, Sudoku, Shanghai, FreeCell, Rummikub, Splendor, Saju, Hangul }

private class AppEntry(val label: String, val icon: ImageVector, val target: MiniGame)

private val GAME_ENTRIES = listOf(
    AppEntry("스도쿠", AppIcons.Sudoku, MiniGame.Sudoku),
    AppEntry("상하이", AppIcons.Shanghai, MiniGame.Shanghai),
    AppEntry("프리셀", AppIcons.FreeCell, MiniGame.FreeCell),
    AppEntry("루미큐브", AppIcons.Rummikub, MiniGame.Rummikub),
    AppEntry("스플렌더", AppIcons.Splendor, MiniGame.Splendor),
    AppEntry("사주", AppIcons.Saju, MiniGame.Saju),
    AppEntry("한글 배우기", AppIcons.Hangul, MiniGame.Hangul),
)

private val APP_ENTRIES = listOf(AppEntry("음악", AppIcons.Music, MiniGame.None)) + GAME_ENTRIES

/** 저장된 순서대로 늘어놓고, 저장된 뒤에 새로 생긴 앱은 맨 뒤에 붙인다. */
private fun orderedEntries(order: List<String>): List<AppEntry> {
    val saved = order.mapNotNull { name -> APP_ENTRIES.find { it.target.name == name } }
    return saved + APP_ENTRIES.filter { it !in saved }
}

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
            val trimming by viewModel.trimming.collectAsStateWithLifecycle()
            AppTheme(dark, colorTheme) {
                Box(Modifier.fillMaxSize()) {
                    MainScreen(viewModel)
                    // 잘라내기 편집은 어느 탭에서 열든 화면 전체를 덮는다.
                    trimming?.let { track -> TrimScreen(track, viewModel, onDismiss = viewModel::closeTrim) }
                }
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
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(AppSettings.loadMusicTab(context)) }
    val downloads by Downloads.items.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val updateProgress by viewModel.updateProgress.collectAsStateWithLifecycle()
    val homeOrder by viewModel.homeOrder.collectAsStateWithLifecycle()
    var updateAsked by rememberSaveable { mutableStateOf(false) }
    // 처음 켜면 마지막으로 쓰던 화면을, 기록이 없으면 앱 고르는 화면을 연다.
    var game by rememberSaveable {
        val last = AppSettings.loadLastScreen(context)
        mutableStateOf(MiniGame.entries.find { it.name == last && it != MiniGame.Home } ?: MiniGame.Home)
    }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    var nowPlayingOpen by rememberSaveable { mutableStateOf(false) }
    // 화면을 돌려 다시 그릴 때는 들어온 것으로 치지 않도록 직전 화면을 함께 기억한다.
    var enteredGame by rememberSaveable { mutableStateOf<MiniGame?>(null) }
    LaunchedEffect(game) {
        if (game != MiniGame.Home) AppSettings.saveLastScreen(context, game.name)
        // 음악 화면은 마지막에 보던 탭으로 열고, 노래가 나오고 있으면 지금 재생 중 목록을 위에 띄운다.
        if (game != enteredGame && game == MiniGame.None && Player.state.value.isPlaying) nowPlayingOpen = true
        enteredGame = game
    }
    if (nowPlayingOpen) NowPlayingSheet(onDismiss = { nowPlayingOpen = false })
    LaunchedEffect(tab) { AppSettings.saveMusicTab(context, tab) }
    BackHandler(game != MiniGame.Home) { game = MiniGame.Home }

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
                MiniGame.Sudoku -> SudokuTopBar(viewModel, onBack = { game = MiniGame.Home })
                MiniGame.Shanghai -> ShanghaiTopBar(viewModel, onBack = { game = MiniGame.Home })
                MiniGame.FreeCell -> FreeCellTopBar(viewModel, onBack = { game = MiniGame.Home })
                MiniGame.Rummikub -> RummikubTopBar(viewModel, onBack = { game = MiniGame.Home })
                MiniGame.Splendor -> SplendorTopBar(viewModel, onBack = { game = MiniGame.Home })
                MiniGame.Saju -> SajuTopBar(onBack = { game = MiniGame.Home })
                MiniGame.Hangul -> HangulTopBar(onBack = { game = MiniGame.Home })
                MiniGame.Home -> TopAppBar(
                    title = { AppTitle() },
                    actions = {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "설정")
                        }
                    },
                )
                MiniGame.None -> TopAppBar(title = { AppTitle() })
            }
        },
        bottomBar = { PlayerBar(onOpen = { nowPlayingOpen = true }) },
        // 한 손으로 누르기 쉽게 메뉴는 오른쪽 아래에 둔다.
        floatingActionButton = {
            if (game == MiniGame.None) MenuButton(onGame = { game = it }, onSettings = { settingsOpen = true })
        },
    ) { padding ->
        if (game == MiniGame.Home) {
            HomeScreen(
                modifier = Modifier.padding(padding),
                update = update?.version,
                updateProgress = updateProgress,
                onInstall = viewModel::installUpdate,
                onOpen = { game = it },
                entries = remember(homeOrder) { orderedEntries(homeOrder) },
                onReorder = { entries -> viewModel.setHomeOrder(entries.map { it.target.name }) },
            )
            return@Scaffold
        }
        if (game != MiniGame.None) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                when (game) {
                    MiniGame.Sudoku -> SudokuScreen(viewModel)
                    MiniGame.Shanghai -> ShanghaiScreen(viewModel)
                    MiniGame.Rummikub -> RummikubScreen(viewModel)
                    MiniGame.Splendor -> SplendorScreen(viewModel)
                    MiniGame.Saju -> SajuScreen(viewModel)
                    MiniGame.Hangul -> HangulScreen()
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
        FloatingActionButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.Menu, contentDescription = "메뉴")
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(
                text = { Text("처음 화면") },
                leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                onClick = {
                    expanded = false
                    onGame(MiniGame.Home)
                },
            )
            HorizontalDivider()
            GAME_ENTRIES.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    leadingIcon = { Icon(entry.icon, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onGame(entry.target)
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

private enum class LibrarySource(val label: String) {
    Downloaded("받은 음악"),
    Device("기기 음악"),
    Recordings("녹음"),
}

/** 이 앱이 받은 곡, 기기의 다른 음악, 녹음을 나눠 보여 준다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTab(viewModel: MainViewModel) {
    val downloaded by viewModel.tracks.collectAsStateWithLifecycle()
    val device by viewModel.deviceTracks.collectAsStateWithLifecycle()
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var source by rememberSaveable { mutableStateOf(LibrarySource.Downloaded) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf<Track?>(null) }
    val deleteFromDevice = rememberTrackDeleter(viewModel)
    LaunchedEffect(Unit) {
        viewModel.refreshTracks()
        viewModel.refreshDeviceTracks()
    }

    val music = remember(device) { device.filter { !it.isRecording } }
    // 녹음은 제목보다 언제 녹음했는지로 찾게 되므로 최신순으로 둔다.
    val recordings = remember(device) { device.filter { it.isRecording }.sortedByDescending { it.dateAdded } }
    val tracks = when (source) {
        LibrarySource.Downloaded -> downloaded
        LibrarySource.Device -> music
        LibrarySource.Recordings -> recordings
    }

    Column(Modifier.fillMaxSize()) {
        if (source != LibrarySource.Downloaded) AudioPermissionBar(viewModel)
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibrarySource.entries.forEach { item ->
                    val count = when (item) {
                        LibrarySource.Downloaded -> downloaded.size
                        LibrarySource.Device -> music.size
                        LibrarySource.Recordings -> recordings.size
                    }
                    FilterChip(
                        selected = source == item,
                        onClick = { source = item },
                        label = { Text("${item.label} $count") },
                    )
                }
            }
            if (scanning) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = {
                    viewModel.scanDevice { found ->
                        val message = if (found > 0) "새 파일 ${found}개를 찾았습니다." else "새로 찾은 파일이 없습니다."
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "기기 스캔")
                }
            }
        }

        if (tracks.isEmpty()) {
            Text(
                when (source) {
                    LibrarySource.Downloaded -> "받은 음악이 없습니다. 파일은 Music/YoutubeMp3에 저장됩니다."
                    LibrarySource.Device -> "기기에서 음악을 찾지 못했습니다. 오른쪽 위 새로고침으로 저장소를 다시 훑어보세요."
                    LibrarySource.Recordings -> "녹음 파일이 없습니다. 오른쪽 위 새로고침으로 저장소를 다시 훑어보세요."
                },
                Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                // 누른 곡부터 목록 끝까지 이어서 튼다.
                Row(
                    Modifier.fillMaxWidth().clickable { Player.play(context, tracks, index) }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (source == LibrarySource.Recordings) {
                            Text(
                                recordingInfo(track),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuFor = track.id }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "더 보기")
                        }
                        DropdownMenu(menuFor == track.id, { menuFor = null }) {
                            DropdownMenuItem(
                                text = { Text("재생목록에 추가") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = {
                                    menuFor = null
                                    adding = track
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("잘라내기") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuFor = null
                                    viewModel.openTrim(track)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("삭제") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuFor = null
                                    // 다른 앱이 만든 파일은 시스템에 허락을 받아야 지울 수 있다.
                                    if (source == LibrarySource.Downloaded) viewModel.delete(track) else deleteFromDevice(track)
                                },
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }

    adding?.let { track ->
        AddToPlaylistDialog(track, playlists, viewModel, onDone = { adding = null })
    }
}

private val RecordingDateFormat = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA)

/** 녹음 목록 아래 줄: 녹음한 때와 길이(예: 2026.11.06 14:20 · 3:12). */
private fun recordingInfo(track: Track): String {
    val date = RecordingDateFormat.format(java.util.Date(track.dateAdded * 1000))
    if (track.durationMs <= 0) return date
    val total = track.durationMs / 1000
    val length = if (total >= 3600) {
        "%d:%02d:%02d".format(total / 3600, total % 3600 / 60, total % 60)
    } else {
        "%d:%02d".format(total / 60, total % 60)
    }
    return "$date · $length"
}

/** 재생 중인 곡과 그 뒤로 나올 곡들. 하단 재생바의 제목을 누르면 열린다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlayingSheet(onDismiss: () -> Unit) {
    val state by Player.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.queueIndex)
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("지금 재생 중", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (state.queueSize > 0) {
                Text("${state.queueIndex + 1} / ${state.queueSize}", color = colors.onSurfaceVariant)
            }
        }
        if (state.queue.isEmpty()) {
            Text("재생 중인 곡이 없습니다.", Modifier.padding(16.dp))
        }
        LazyColumn(state = listState, contentPadding = ListPadding) {
            itemsIndexed(state.queue) { index, track ->
                val current = index == state.queueIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) colors.primaryContainer else colors.surfaceContainerLow)
                        .clickable { if (!current) Player.playAt(index) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(32.dp)) {
                        if (current) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.PlayArrow else AppIcons.Pause,
                                contentDescription = null,
                                tint = colors.primary,
                            )
                        } else {
                            Text("${index + 1}", color = colors.onSurfaceVariant)
                        }
                    }
                    Text(
                        track.title,
                        Modifier.weight(1f),
                        color = if (current) colors.onPrimaryContainer else colors.onSurface,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBar(onOpen: () -> Unit) {
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
                // 제목을 누르면 지금 재생 중 목록이 열린다.
                Row(
                    Modifier.weight(1f).clickable(onClick = onOpen).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "지금 재생 중 목록")
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (state.queueSize > 1) {
                    IconButton(onClick = Player::previous) {
                        Icon(AppIcons.SkipPrevious, contentDescription = "이전")
                    }
                }
                SeekButton(forward = false)
                FilledIconButton(onClick = Player::togglePause) {
                    if (state.isPlaying) {
                        Icon(AppIcons.Pause, contentDescription = "일시정지")
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "재생")
                    }
                }
                SeekButton(forward = true)
                if (state.queueSize > 1) {
                    IconButton(onClick = Player::next) {
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

/** 둥근 화살표 안에 10을 적은 10초 앞/뒤 버튼. 뒤로 갈 때는 화살표를 좌우로 뒤집는다. */
@Composable
private fun SeekButton(forward: Boolean) {
    IconButton(onClick = { Player.seekBy(if (forward) 10_000 else -10_000) }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = if (forward) "10초 앞으로" else "10초 뒤로",
                modifier = Modifier.size(28.dp).graphicsLayer { scaleX = if (forward) 1f else -1f },
            )
            Text("10", fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AppTitle() {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("YoutubeMp3")
        Text(
            "v${BuildConfig.VERSION_NAME}",
            Modifier.padding(start = 8.dp, bottom = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** 타일을 이만큼 누르고 있으면 들어 올려 옮길 수 있다. */
private const val HOME_HOLD_MILLIS = 3000L

/**
 * 앱을 고르는 첫 화면이다. 음악과 게임들을 바둑판처럼 늘어놓는다.
 * 안드로이드 홈 화면처럼 타일을 누르고 있으면 들어 올려 다른 자리로 끌어 옮길 수 있다.
 */
@Composable
private fun HomeScreen(
    modifier: Modifier,
    update: String?,
    updateProgress: Float?,
    onInstall: () -> Unit,
    onOpen: (MiniGame) -> Unit,
    entries: List<AppEntry>,
    onReorder: (List<AppEntry>) -> Unit,
) {
    var order by remember { mutableStateOf(entries) }
    var pressed by remember { mutableStateOf<MiniGame?>(null) }
    var dragged by remember { mutableStateOf<MiniGame?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val latestEntries by rememberUpdatedState(entries)
    val gridState = rememberLazyGridState()
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(entries) { if (dragged == null) order = entries }

    // 끄는 타일의 가운데가 다른 타일 위에 오면 그 자리로 옮긴다.
    // 옮긴 만큼 타일의 원래 자리가 바뀌므로 끄는 거리에서 그만큼 빼서 손가락 아래에 그대로 둔다.
    fun dragBy(delta: Offset) {
        val key = dragged ?: return
        dragOffset += delta
        val items = gridState.layoutInfo.visibleItemsInfo
        val current = items.find { it.key == key.name } ?: return
        val center = Offset(
            current.offset.x + dragOffset.x + current.size.width / 2f,
            current.offset.y + dragOffset.y + current.size.height / 2f,
        )
        val target = items.find {
            it.key != key.name &&
                center.x >= it.offset.x && center.x <= it.offset.x + it.size.width &&
                center.y >= it.offset.y && center.y <= it.offset.y + it.size.height
        } ?: return
        order = order.toMutableList().apply { add(target.index, removeAt(current.index)) }
        dragOffset += Offset(
            (current.offset.x - target.offset.x).toFloat(),
            (current.offset.y - target.offset.y).toFloat(),
        )
    }

    fun endDrag() {
        if (order != latestEntries) onReorder(order)
        dragged = null
        dragOffset = Offset.Zero
    }

    val latestDragBy by rememberUpdatedState<(Offset) -> Unit> { dragBy(it) }
    val latestEndDrag by rememberUpdatedState<() -> Unit> { endDrag() }
    val latestOnOpen by rememberUpdatedState(onOpen)

    Column(modifier.fillMaxSize()) {
        update?.let { version ->
            if (updateProgress == null) {
                TextButton(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Text("새 버전 $version 설치")
                }
            } else {
                LinearProgressIndicator(progress = { updateProgress }, modifier = Modifier.fillMaxWidth())
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            state = gridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(order, key = { it.target.name }) { entry ->
                val isDragged = entry.target == dragged
                // 누르고 있는 동안 천천히 눌려 들어가다가, 들어 올리면 커진다.
                val scale by animateFloatAsState(
                    targetValue = when {
                        isDragged -> 1.1f
                        entry.target == pressed -> 0.92f
                        else -> 1f
                    },
                    animationSpec = if (entry.target == pressed && !isDragged) {
                        tween(HOME_HOLD_MILLIS.toInt())
                    } else {
                        spring()
                    },
                    label = "tileScale",
                )
                val itemModifier = if (isDragged) {
                    Modifier.zIndex(1f).graphicsLayer {
                        translationX = dragOffset.x
                        translationY = dragOffset.y
                    }
                } else {
                    Modifier.animateItem()
                }
                ElevatedCard(
                    modifier = itemModifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .pointerInput(entry.target) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                pressed = entry.target
                                // true는 손을 뗌(누름), false는 움직이거나 목록이 스크롤됨, null은 오래 누름이다.
                                val released = withTimeoutOrNull(HOME_HOLD_MILLIS) {
                                    var result = false
                                    while (true) {
                                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) {
                                            result = change != null && !change.isConsumed
                                            break
                                        }
                                        if (change.isConsumed ||
                                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                                        ) {
                                            break
                                        }
                                    }
                                    result
                                }
                                pressed = null
                                when (released) {
                                    true -> latestOnOpen(entry.target)
                                    false -> Unit
                                    null -> {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragged = entry.target
                                        dragOffset = Offset.Zero
                                        drag(down.id) { change ->
                                            latestDragBy(change.positionChange())
                                            change.consume()
                                        }
                                        latestEndDrag()
                                    }
                                }
                            }
                        },
                    elevation = if (isDragged) CardDefaults.elevatedCardElevation(defaultElevation = 8.dp) else CardDefaults.elevatedCardElevation(),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            entry.icon,
                            contentDescription = null,
                            Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(entry.label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
