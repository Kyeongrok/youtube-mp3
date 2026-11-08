package com.kyeongrok.youtubemp3

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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

// 음악 권한과 함께 동영상 권한도 묻는다. 브라우저로 받은 m4a가 동영상으로 분류된 경우가 있어서다.
// 안드로이드 12 이하는 READ_EXTERNAL_STORAGE 하나로 둘 다 읽힌다.
private val MEDIA_PERMISSIONS =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

// 사용자 재생목록 ID는 시각 값이라 겹치지 않는다.
private const val RECENT_ID = -1L
private const val RECENT_LIMIT = 50

private fun hasAudioPermission(context: Context) =
    context.checkSelfPermission(AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED

private fun hasAllMediaPermissions(context: Context) =
    MEDIA_PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

/** 기기 음악을 읽을 권한이 없으면 허용 버튼을 보여 주고, 화면에 돌아올 때마다(설정에서 켜고 온 경우 포함) 목록을 다시 읽는다. */
@Composable
fun AudioPermissionBar(viewModel: MainViewModel) {
    val context = LocalContext.current
    var canRead by remember { mutableStateOf(hasAudioPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    // 동영상 권한만 아직 없으면(이전 버전에서 음악 권한만 준 경우) 한 번 더 물어볼 수 있게 버튼을 보여 준다.
    var needsVideo by remember { mutableStateOf(!hasAllMediaPermissions(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val granted = hasAudioPermission(context)
        canRead = granted
        denied = !granted
        needsVideo = !hasAllMediaPermissions(context)
        if (granted) viewModel.refreshDeviceTracks()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canRead = hasAudioPermission(context)
        needsVideo = !hasAllMediaPermissions(context)
        viewModel.refreshDeviceTracks()
    }

    if (canRead && needsVideo && !denied) {
        TextButton(onClick = { permission.launch(MEDIA_PERMISSIONS) }, modifier = Modifier.fillMaxWidth()) {
            Text("브라우저로 받은 m4a도 보이게 하려면 동영상 접근 허용하기")
        }
    }

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
                    permission.launch(MEDIA_PERMISSIONS)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (denied) "설정에서 음악 접근 허용하기" else "기기의 모든 음악·녹음을 쓰려면 접근 허용하기") }
    }
}

@Composable
fun PlaylistTab(viewModel: MainViewModel) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val deviceTracks by viewModel.deviceTracks.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = playlists.find { it.id == openId }
    BackHandler(openId != null) { openId = null }

    Column(Modifier.fillMaxSize()) {
        AudioPermissionBar(viewModel)
        when {
            openId == RECENT_ID -> RecentTracks(deviceTracks, playlists, viewModel, onBack = { openId = null })
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
        PlaylistNameDialog(
            title = "새 재생목록",
            confirmLabel = "만들기",
            onDismiss = { creating = false },
            onConfirm = {
                creating = false
                onOpen(viewModel.createPlaylist(it))
            },
        )
    }
}

/**
 * 기기에 추가된 순서대로 자동으로 채워지는 목록이라 순서를 고치는 버튼은 없다.
 * 곡을 누르면 재생하거나 재생목록에 넣는 메뉴가 나온다.
 */
@Composable
private fun RecentTracks(deviceTracks: List<Track>, playlists: List<Playlist>, viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    // 녹음은 내 음악 탭의 녹음에서 따로 보므로 여기엔 노래만 둔다.
    val tracks = deviceTracks.filter { !it.isRecording }.sortedByDescending { it.dateAdded }.take(RECENT_LIMIT)
    var menuFor by remember { mutableStateOf<Long?>(null) }
    val renameTrack = rememberTrackRenamer(viewModel)
    var adding by remember { mutableStateOf<Track?>(null) }

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
                Box {
                    Text(
                        track.title,
                        Modifier.fillMaxWidth().clickable { menuFor = track.id }.padding(16.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DropdownMenu(menuFor == track.id, { menuFor = null }) {
                        DropdownMenuItem(
                            text = { Text("재생") },
                            leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                            onClick = {
                                menuFor = null
                                Player.play(context, tracks, index)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("재생목록에 추가") },
                            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            onClick = {
                                menuFor = null
                                adding = track
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("이름 바꾸기") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuFor = null
                                renameTrack(track)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("편집") },
                            leadingIcon = { Icon(AppIcons.Cut, contentDescription = null) },
                            onClick = {
                                menuFor = null
                                viewModel.openTrim(track)
                            },
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    adding?.let { track ->
        AddToPlaylistDialog(
            track,
            playlists,
            viewModel,
            onDone = { adding = null },
        )
    }
}

/** 곡을 넣을 재생목록을 고른다. 목록이 없거나 새로 만들고 싶으면 이름을 받아 만든 뒤 넣는다. */
@Composable
fun AddToPlaylistDialog(track: Track, playlists: List<Playlist>, viewModel: MainViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    val add = { id: Long, name: String ->
        val added = viewModel.addPlaylistTrack(id, track.id)
        val message = if (added) "'$name'에 추가했습니다." else "'$name'에 이미 있는 곡입니다."
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onDone()
    }

    if (creating) {
        PlaylistNameDialog(
            title = "새 재생목록",
            confirmLabel = "만들고 추가",
            onDismiss = onDone,
            onConfirm = { name -> add(viewModel.createPlaylist(name), name) },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("재생목록에 추가") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Text(
                        playlist.name,
                        Modifier.fillMaxWidth().clickable { add(playlist.id, playlist.name) }.padding(vertical = 14.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    HorizontalDivider()
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("새 재생목록", Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("취소") } },
    )
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initial: String = "",
) {
    // 이름을 고칠 때 바로 이어서 쓸 수 있도록 커서를 끝에 둔다.
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.text.trim()) }, enabled = name.text.isNotBlank()) { Text(confirmLabel) }
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
    var renaming by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val tracksById = deviceTracks.associateBy { it.id }
    val tracks = playlist.trackIds.mapNotNull(tracksById::get)
    val deleteFromDevice = rememberTrackDeleter(viewModel)

    // 끄는 동안에는 화면의 순서만 바꾸고, 손을 떼면 저장한다.
    // 끄는 제스처가 붙잡은 상태가 바뀌지 않도록 같은 상태 객체를 계속 쓰고, 목록이 바뀌면 값만 맞춘다.
    var order by remember { mutableStateOf(tracks) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    val latestTracks by rememberUpdatedState(tracks)
    LaunchedEffect(tracks) { if (draggedId == null) order = tracks }
    var dragOffset by remember { mutableStateOf(0f) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    val renameTrack = rememberTrackRenamer(viewModel)
    val listState = rememberLazyListState()

    fun dragBy(delta: Float) {
        val id = draggedId ?: return
        val info = listState.layoutInfo
        val current = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return
        dragOffset += delta
        // 목록 끝에 닿으면 그쪽으로 굴린다. 곡이 따라 움직이므로 그만큼 되돌려 손가락 밑에 둔다.
        val top = current.offset + dragOffset
        val bottom = top + current.size
        val edge = current.size / 2f
        val scroll = when {
            delta < 0 && top < info.viewportStartOffset + edge -> delta
            delta > 0 && bottom > info.viewportEndOffset - edge -> delta
            else -> 0f
        }
        if (scroll != 0f) dragOffset += listState.dispatchRawDelta(scroll)
        // 끄는 곡의 가운데가 다른 곡 위로 넘어가면 자리를 바꾼다.
        val center = current.offset + dragOffset + current.size / 2f
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != id && center >= it.offset && center < it.offset + it.size
        } ?: return
        val from = order.indexOfFirst { it.id == id }
        val to = order.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        dragOffset += current.offset - target.offset
    }

    fun endDrag() {
        if (draggedId != null && order.map { it.id } != latestTracks.map { it.id }) {
            viewModel.reorderPlaylistTracks(playlist.id, order.map { it.id })
        }
        draggedId = null
        dragOffset = 0f
    }

    // 끄는 제스처는 곡마다 한 번 만들어져 계속 살아 있으므로, 늘 최신 함수를 부르게 한다.
    val latestDragBy by rememberUpdatedState<(Float) -> Unit> { dragBy(it) }
    val latestEndDrag by rememberUpdatedState<() -> Unit> { endDrag() }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
            Text(playlist.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { renaming = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "이름 바꾸기")
            }
            IconButton(onClick = { deleting = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "재생목록 삭제")
            }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { Player.play(context, tracks, 0) }, enabled = tracks.isNotEmpty()) { Text("전체 재생") }
            TextButton(onClick = { picking = true }) { Text("곡 선택") }
        }
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = ListPadding) {
            itemsIndexed(order, key = { _, track -> track.id }) { index, track ->
                val dragging = track.id == draggedId
                Column(
                    if (dragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragOffset }
                            .shadow(6.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    } else {
                        Modifier.animateItem()
                    },
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { Player.play(context, order, index) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 손잡이를 잡고 위아래로 끌면 순서가 바뀐다.
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "끌어서 순서 바꾸기",
                            modifier = Modifier
                                .pointerInput(track.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedId = track.id
                                            dragOffset = 0f
                                        },
                                        onDragEnd = { latestEndDrag() },
                                        onDragCancel = { latestEndDrag() },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            latestDragBy(amount.y)
                                        },
                                    )
                                }
                                .padding(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(track.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Box {
                            IconButton(onClick = { menuFor = track.id }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "더 보기")
                            }
                            DropdownMenu(menuFor == track.id, { menuFor = null }) {
                                DropdownMenuItem(
                                    text = { Text("재생목록에서 삭제") },
                                    leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                                    onClick = {
                                        menuFor = null
                                        viewModel.removePlaylistTrack(playlist.id, track.id)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("이름 바꾸기") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                    onClick = {
                                        menuFor = null
                                        renameTrack(track)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("편집") },
                                    leadingIcon = { Icon(AppIcons.Cut, contentDescription = null) },
                                    onClick = {
                                        menuFor = null
                                        viewModel.openTrim(track)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("기기에서 삭제") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = {
                                        menuFor = null
                                        deleteFromDevice(track)
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
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
    if (renaming) {
        PlaylistNameDialog(
            title = "이름 바꾸기",
            confirmLabel = "바꾸기",
            initial = playlist.name,
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                viewModel.renamePlaylist(playlist.id, it)
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
    val visible = deviceTracks.filter { it.title.contains(filter.trim(), ignoreCase = true) }
    val deleteTrack = rememberTrackDeleter(viewModel)

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
                            IconButton(onClick = { deleteTrack(track) }) {
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
}

/**
 * 곡 파일을 기기에서 지우는 함수를 돌려준다. Android 11부터는 시스템이 직접 확인 창을 띄우고,
 * 그 전에는 우리가 먼저 묻는다.
 */
@Composable
fun rememberTrackDeleter(viewModel: MainViewModel): (Track) -> Unit {
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<Track?>(null) }
    var confirming by remember { mutableStateOf<Track?>(null) }
    val deleteRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Android 11부터는 시스템이 이미 지웠고, 그 전에는 허락만 받았으므로 다시 지운다.
        if (result.resultCode == Activity.RESULT_OK) {
            deleting?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) viewModel.forgetTrack(it) else viewModel.delete(it)
            }
        }
        deleting = null
    }
    val delete = { track: Track ->
        val sender = Library.requestDelete(context, track)
        if (sender == null) {
            viewModel.forgetTrack(track)
        } else {
            deleting = track
            deleteRequest.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    confirming?.let { track ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("기기에서 삭제") },
            text = { Text("'${track.title}' 파일을 기기에서 완전히 지웁니다. 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    delete(track)
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("취소") } },
        )
    }

    return { track -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) delete(track) else confirming = track }
}

/**
 * 곡 이름(파일 이름)을 바꾸는 창을 띄우는 함수를 돌려준다.
 * 다른 앱이 만든 파일이면 시스템에 고쳐도 되는지 물은 뒤 다시 바꾼다.
 */
@Composable
fun rememberTrackRenamer(viewModel: MainViewModel): (Track) -> Unit {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Track?>(null) }
    var pending by remember { mutableStateOf<Pair<Track, String>?>(null) }

    fun done() {
        viewModel.refreshTracks()
        viewModel.refreshDeviceTracks()
        Toast.makeText(context, "이름을 바꿨습니다.", Toast.LENGTH_SHORT).show()
    }

    val writeRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (track, name) = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        try {
            Library.rename(context, track, name)
            done()
        } catch (e: Exception) {
            Toast.makeText(context, "이름을 바꾸지 못했습니다: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun rename(track: Track, name: String) {
        try {
            Library.rename(context, track, name)
            done()
        } catch (e: SecurityException) {
            val sender = Library.writeRequest(context, track)
                ?: (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
            if (sender == null) {
                Toast.makeText(context, "이 파일은 이름을 바꿀 수 없습니다.", Toast.LENGTH_LONG).show()
            } else {
                pending = track to name
                writeRequest.launch(IntentSenderRequest.Builder(sender).build())
            }
        } catch (e: Exception) {
            Toast.makeText(context, "이름을 바꾸지 못했습니다: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    editing?.let { track ->
        PlaylistNameDialog(
            title = "이름 바꾸기",
            confirmLabel = "바꾸기",
            initial = track.title,
            onDismiss = { editing = null },
            onConfirm = { name ->
                editing = null
                if (name != track.title) rename(track, name)
            },
        )
    }

    return { track -> editing = track }
}
