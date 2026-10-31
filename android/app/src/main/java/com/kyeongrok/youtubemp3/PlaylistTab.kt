package com.kyeongrok.youtubemp3

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val AUDIO_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

// 사용자 재생목록 ID는 시각 값이라 겹치지 않는다.
private const val RECENT_ID = -1L
private const val RECENT_LIMIT = 50

private fun hasAudioPermission(context: Context) =
    context.checkSelfPermission(AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED

@Composable
fun PlaylistTab(viewModel: MainViewModel) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val deviceTracks by viewModel.deviceTracks.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    var canRead by remember { mutableStateOf(hasAudioPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canRead = granted
        denied = !granted
        if (granted) viewModel.refreshDeviceTracks()
    }

    // 탭에 들어올 때와, 설정 화면에서 권한을 켜고 돌아올 때 다시 읽는다.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canRead = hasAudioPermission(context)
        viewModel.refreshDeviceTracks()
    }

    val open = playlists.find { it.id == openId }
    BackHandler(openId != null) { openId = null }

    Column(Modifier.fillMaxSize()) {
        if (!canRead) {
            TextButton(
                onClick = {
                    if (denied) {
                        // 거부가 반복되면 시스템이 요청 창을 띄우지 않으므로 설정으로 보낸다.
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    } else {
                        permission.launch(AUDIO_PERMISSION)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (denied) "설정에서 음악 접근 허용하기" else "기기의 모든 음악을 쓰려면 접근 허용하기") }
        }
        when {
            openId == RECENT_ID -> RecentTracks(deviceTracks, onBack = { openId = null })
            open == null -> PlaylistList(playlists, viewModel, onOpen = { openId = it })
            else -> PlaylistDetail(open, deviceTracks, viewModel, onBack = { openId = null })
        }
    }
}

@Composable
private fun PlaylistList(playlists: List<Playlist>, viewModel: MainViewModel, onOpen: (Long) -> Unit) {
    var creating by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = { creating = true }, modifier = Modifier.align(Alignment.End).padding(end = 16.dp)) {
            Text("새 재생목록")
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
            item {
                Text("최근 추가된 노래", Modifier.fillMaxWidth().clickable { onOpen(RECENT_ID) }.padding(16.dp))
                HorizontalDivider()
            }
            items(playlists, key = { it.id }) { playlist ->
                Text(
                    playlist.name,
                    Modifier.fillMaxWidth().clickable { onOpen(playlist.id) }.padding(16.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                HorizontalDivider()
            }
        }
    }

    if (creating) {
        NewPlaylistDialog(
            onDismiss = { creating = false },
            onCreate = {
                creating = false
                onOpen(viewModel.createPlaylist(it))
            },
        )
    }
}

/** 기기에 추가된 순서대로 자동으로 채워지는 목록이라 곡을 고치는 버튼은 없다. */
@Composable
private fun RecentTracks(deviceTracks: List<Track>, onBack: () -> Unit) {
    val context = LocalContext.current
    val tracks = deviceTracks.sortedByDescending { it.dateAdded }.take(RECENT_LIMIT)

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text("최근 추가된 노래")
        }
        Button(
            onClick = { Player.play(context, tracks, 0) },
            enabled = tracks.isNotEmpty(),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text("전체 재생") }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                Text(
                    track.title,
                    Modifier.fillMaxWidth().clickable { Player.play(context, tracks, index) }.padding(16.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 재생목록") },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) { Text("만들기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    deviceTracks: List<Track>,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val tracksById = deviceTracks.associateBy { it.id }
    val tracks = playlist.trackIds.mapNotNull(tracksById::get)

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(playlist.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { deleting = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "재생목록 삭제")
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { Player.play(context, tracks, 0) }, enabled = tracks.isNotEmpty()) { Text("전체 재생") }
            TextButton(onClick = { picking = true }) { Text("곡 선택") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                Row(
                    Modifier.fillMaxWidth().clickable { Player.play(context, tracks, index) }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(track.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(
                        onClick = { viewModel.swapPlaylistTracks(playlist.id, track.id, tracks[index - 1].id) },
                        enabled = index > 0,
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "위로") }
                    IconButton(
                        onClick = { viewModel.swapPlaylistTracks(playlist.id, track.id, tracks[index + 1].id) },
                        enabled = index < tracks.lastIndex,
                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "아래로") }
                    IconButton(onClick = { viewModel.removePlaylistTrack(playlist.id, track.id) }) {
                        Icon(Icons.Filled.Close, contentDescription = "목록에서 빼기")
                    }
                }
                HorizontalDivider()
            }
        }
    }

    if (picking) {
        TrackPickerDialog(
            deviceTracks,
            initial = playlist.trackIds.toSet(),
            viewModel,
            onDismiss = { picking = false },
            onConfirm = {
                picking = false
                viewModel.choosePlaylistTracks(playlist.id, it)
            },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("재생목록 삭제") },
            text = { Text("'${playlist.name}' 목록을 지웁니다. 음악 파일은 지워지지 않습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    onBack()
                    viewModel.deletePlaylist(playlist.id)
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("취소") } },
        )
    }
}

@Composable
private fun TrackPickerDialog(
    deviceTracks: List<Track>,
    initial: Set<Long>,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    var filter by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initial) }
    var deleting by remember { mutableStateOf<Track?>(null) }
    var confirming by remember { mutableStateOf<Track?>(null) }
    val visible = deviceTracks.filter { it.title.contains(filter.trim(), ignoreCase = true) }
    val context = LocalContext.current
    val deleteRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Android 11부터는 시스템이 이미 지웠고, 그 전에는 허락만 받았으므로 다시 지운다.
        if (result.resultCode == Activity.RESULT_OK) {
            deleting?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) viewModel.forgetTrack(it) else viewModel.delete(it)
            }
        }
        deleting = null
    }
    val deleteTrack = { track: Track ->
        val sender = Library.requestDelete(context, track)
        if (sender == null) {
            viewModel.forgetTrack(track)
        } else {
            deleting = track
            deleteRequest.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("곡 선택") },
        text = {
            Column {
                OutlinedTextField(filter, { filter = it }, label = { Text("곡 찾기") }, singleLine = true)
                if (deviceTracks.isEmpty()) {
                    Text("기기에서 찾은 음악이 없습니다.", Modifier.padding(top = 12.dp))
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(visible, key = { it.id }) { track ->
                        val checked = track.id in selected
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (checked) selected - track.id else selected + track.id
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked, onCheckedChange = null, Modifier.padding(12.dp))
                            Text(
                                track.title,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Android 11부터는 시스템이 직접 삭제를 확인한다.
                            IconButton(onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) deleteTrack(track) else confirming = track
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "영구 삭제")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )

    confirming?.let { track ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("영구 삭제") },
            text = { Text("'${track.title}' 파일을 기기에서 완전히 지웁니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    deleteTrack(track)
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("취소") } },
        )
    }
}
