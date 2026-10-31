package com.kyeongrok.youtubemp3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

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
            AppTheme {
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
private fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = { Surface(Modifier.fillMaxSize(), content = content) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val downloads by Downloads.items.collectAsStateWithLifecycle()
    val newerVersion by viewModel.newerVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("YoutubeMp3") }) },
        bottomBar = { PlayerBar() },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            newerVersion?.let { version ->
                TextButton(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdate.RELEASES_URL)))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("새 버전 $version 받기") }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("검색") })
                Tab(tab == 1, { tab = 1 }, text = { Text("다운로드 (${downloads.size})") })
                Tab(tab == 2, { tab = 2 }, text = { Text("내 음악") })
            }
            when (tab) {
                0 -> SearchTab(viewModel)
                1 -> DownloadsTab(downloads)
                else -> LibraryTab(viewModel)
            }
        }
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
        LazyColumn(Modifier.fillMaxSize()) {
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
        LazyColumn(Modifier.fillMaxSize()) {
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
    LazyColumn(Modifier.fillMaxSize()) {
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
                TextButton(onClick = Player::togglePause) { Text(if (state.isPlaying) "일시정지" else "재생") }
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
