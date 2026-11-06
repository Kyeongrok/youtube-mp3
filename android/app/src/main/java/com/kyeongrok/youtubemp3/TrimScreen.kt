package com.kyeongrok.youtubemp3

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

private const val MIN_VIEW_SECONDS = 0.5
private const val NUDGE_SECONDS = 0.1

/**
 * 곡 하나를 열어 필요 없는 구간을 잘라 내는 전체 화면 편집기(데스크톱 편집기의 모바일판).
 * 파형을 한 손가락으로 끌면 구간을 고르고, 두 손가락으로 벌리거나 밀면 확대·이동한다.
 */
@Composable
fun TrimScreen(track: Track, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf<AudioTrimmer.Source?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var removed by remember { mutableStateOf(emptyList<TimeRange>()) }
    var undo by remember { mutableStateOf(emptyList<List<TimeRange>>()) }
    // 마지막으로 저장한 편집 상태. 지금 목록과 다르면 저장할 것이 남은 것이다.
    var savedRemoved by remember { mutableStateOf(emptyList<TimeRange>()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmOverwrite by remember { mutableStateOf(false) }

    // 모든 시간은 편집된(지운 구간을 접은) 시간이다.
    var selStart by remember { mutableDoubleStateOf(0.0) }
    var selEnd by remember { mutableDoubleStateOf(0.0) }
    var playhead by remember { mutableDoubleStateOf(0.0) }
    var viewStart by remember { mutableDoubleStateOf(0.0) }
    var viewSpan by remember { mutableDoubleStateOf(1.0) }
    var playing by remember { mutableStateOf(false) }
    val player = remember { mutableStateOf<MediaPlayer?>(null) }

    val src = source
    val total = src?.let { AudioTrimmer.editedDuration(it.durationSec, removed) } ?: 0.0
    val peaks = remember(src, removed) { src?.let { AudioTrimmer.editedPeaks(it.peaks, removed) } ?: FloatArray(0) }

    LaunchedEffect(track.id) {
        // 편집기에서 들어야 하므로 원래 재생은 멈춘다.
        Player.togglePauseIf(true)
        try {
            val opened = AudioTrimmer.open(context, track)
            source = opened
            viewSpan = opened.durationSec
            player.value = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build(),
                )
                setDataSource(opened.file.absolutePath)
                prepare()
                setOnCompletionListener { playing = false }
            }
        } catch (e: Exception) {
            loadError = e.message ?: e.toString()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            player.value?.release()
            player.value = null
            AudioTrimmer.clear(context)
        }
    }

    fun seekPlayer(edited: Double) {
        val mp = player.value ?: return
        val original = AudioTrimmer.toOriginal(edited.coerceIn(0.0, total), removed)
        mp.seekTo((original * 1000).toLong(), MediaPlayer.SEEK_CLOSEST)
        playhead = edited.coerceIn(0.0, total)
    }

    fun stopPlaying() {
        player.value?.takeIf { it.isPlaying }?.pause()
        playing = false
    }

    fun togglePlay() {
        val mp = player.value ?: return
        if (playing) {
            stopPlaying()
            return
        }
        if (playhead >= total - 0.05) seekPlayer(0.0) else seekPlayer(playhead)
        mp.start()
        playing = true
    }

    // 재생 중에는 지운 구간을 건너뛰고, 커서가 화면 밖으로 나가면 따라간다.
    LaunchedEffect(playing) {
        while (playing) {
            val mp = player.value ?: break
            val original = mp.currentPosition / 1000.0
            val skip = AudioTrimmer.removedAt(original, removed)
            if (skip != null) {
                mp.seekTo((skip.end * 1000).toLong(), MediaPlayer.SEEK_CLOSEST)
            }
            playhead = AudioTrimmer.toEdited(skip?.end ?: original, removed).coerceIn(0.0, total)
            if (playhead >= total - 0.01) {
                stopPlaying()
                break
            }
            if (playhead < viewStart || playhead > viewStart + viewSpan) {
                viewStart = (playhead - viewSpan * 0.1).coerceIn(0.0, max(0.0, total - viewSpan))
            }
            delay(30)
        }
    }

    fun applyRemoval(next: List<TimeRange>, cursor: Double) {
        stopPlaying()
        undo = undo + listOf(removed)
        removed = next
        val newTotal = src?.let { AudioTrimmer.editedDuration(it.durationSec, next) } ?: 0.0
        viewSpan = min(viewSpan, newTotal).coerceAtLeast(min(MIN_VIEW_SECONDS, newTotal))
        viewStart = viewStart.coerceIn(0.0, max(0.0, newTotal - viewSpan))
        selStart = cursor.coerceIn(0.0, newTotal)
        selEnd = selStart
        playhead = selStart
    }

    fun cutSelection() {
        if (selEnd <= selStart) return
        applyRemoval(AudioTrimmer.remove(removed, selStart, selEnd), selStart)
    }

    fun keepSelection() {
        if (selEnd <= selStart) return
        // 뒤쪽부터 지워야 앞쪽 시간이 밀리지 않는다.
        val afterTail = AudioTrimmer.remove(removed, selEnd, total)
        applyRemoval(AudioTrimmer.remove(afterTail, 0.0, selStart), 0.0)
    }

    fun undoLast() {
        val previous = undo.lastOrNull() ?: return
        stopPlaying()
        undo = undo.dropLast(1)
        removed = previous
        val newTotal = src?.let { AudioTrimmer.editedDuration(it.durationSec, previous) } ?: 0.0
        selStart = selStart.coerceIn(0.0, newTotal)
        selEnd = selStart
        playhead = selStart
        viewStart = viewStart.coerceIn(0.0, max(0.0, newTotal - viewSpan))
    }

    fun refreshLists() {
        viewModel.refreshTracks()
        viewModel.refreshDeviceTracks()
    }

    var pendingOverwrite by remember { mutableStateOf<Pair<File, List<TimeRange>>?>(null) }
    val writeRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val (file, snapshot) = pendingOverwrite ?: (null to emptyList())
        pendingOverwrite = null
        if (result.resultCode != Activity.RESULT_OK || file == null) {
            busy = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                AudioTrimmer.overwrite(context, file, track.uri)
                savedRemoved = snapshot
                refreshLists()
                Toast.makeText(context, "원본에 저장했습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = null
            }
        }
    }

    fun saveNew() {
        val s = src ?: return
        stopPlaying()
        busy = "저장하는 중..."
        val snapshot = removed
        scope.launch {
            try {
                val file = AudioTrimmer.render(context, s, snapshot, "mp3")
                AudioTrimmer.saveAsNew(context, file, track.title)
                savedRemoved = snapshot
                refreshLists()
                Toast.makeText(context, "'${track.title} (편집)'으로 저장했습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = null
            }
        }
    }

    fun saveOverwrite() {
        val s = src ?: return
        stopPlaying()
        Player.stopIfPlaying(track)
        busy = "저장하는 중..."
        val snapshot = removed
        scope.launch {
            val file = try {
                AudioTrimmer.render(context, s, snapshot)
            } catch (e: Exception) {
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                busy = null
                return@launch
            }
            try {
                AudioTrimmer.overwrite(context, file, track.uri)
                savedRemoved = snapshot
                refreshLists()
                Toast.makeText(context, "원본에 저장했습니다.", Toast.LENGTH_SHORT).show()
                busy = null
            } catch (e: SecurityException) {
                // 다른 앱이 만든 파일은 시스템에 고쳐도 되는지 물어본 뒤 다시 쓴다.
                val sender: IntentSender? = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        MediaStore.createWriteRequest(context.contentResolver, listOf(track.uri)).intentSender
                    e is RecoverableSecurityException -> e.userAction.actionIntent.intentSender
                    else -> null
                }
                if (sender == null) {
                    Toast.makeText(context, "이 파일은 고칠 수 없습니다. 새 파일로 저장해 주세요.", Toast.LENGTH_LONG).show()
                    busy = null
                } else {
                    pendingOverwrite = file to snapshot
                    writeRequest.launch(IntentSenderRequest.Builder(sender).build())
                }
            } catch (e: Exception) {
                Toast.makeText(context, "저장 실패: ${e.message}", Toast.LENGTH_LONG).show()
                busy = null
            }
        }
    }

    val close = {
        if (removed != savedRemoved) confirmClose = true else onDismiss()
    }

    BackHandler { if (busy == null) close() }

    Box(Modifier.fillMaxSize()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (busy == null) close() }) {
                        Icon(Icons.Filled.Close, contentDescription = "닫기")
                    }
                    Column(Modifier.weight(1f)) {
                        Text("잘라내기", style = MaterialTheme.typography.titleMedium)
                        Text(
                            track.title,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = ::undoLast, enabled = undo.isNotEmpty() && busy == null) { Text("되돌리기") }
                }

                when {
                    loadError != null -> Text("파일을 열지 못했습니다: $loadError", Modifier.padding(16.dp))
                    src == null -> Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("파형을 읽는 중... 긴 녹음은 시간이 걸립니다.")
                    }
                    else -> Column(Modifier.fillMaxSize()) {
                        Waveform(
                            peaks = peaks,
                            total = total,
                            selStart = selStart,
                            selEnd = selEnd,
                            playhead = playhead,
                            viewStart = viewStart,
                            viewSpan = viewSpan,
                            onSelect = { a, b ->
                                selStart = a
                                selEnd = b
                            },
                            onTap = { t ->
                                selStart = t
                                selEnd = t
                                seekPlayer(t)
                            },
                            onView = { start, span ->
                                viewSpan = span.coerceIn(min(MIN_VIEW_SECONDS, total), total)
                                viewStart = start.coerceIn(0.0, max(0.0, total - viewSpan))
                            },
                            modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 8.dp),
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${formatTrimTime(viewStart)} ~ ${formatTrimTime(viewStart + viewSpan)}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                val center = viewStart + viewSpan / 2
                                val span = (viewSpan / 2).coerceAtLeast(min(MIN_VIEW_SECONDS, total))
                                viewSpan = span
                                viewStart = (center - span / 2).coerceIn(0.0, max(0.0, total - span))
                            }) { Text("확대") }
                            TextButton(onClick = {
                                val center = viewStart + viewSpan / 2
                                val span = min(viewSpan * 2, total)
                                viewSpan = span
                                viewStart = (center - span / 2).coerceIn(0.0, max(0.0, total - span))
                            }) { Text("축소") }
                            TextButton(onClick = {
                                viewStart = 0.0
                                viewSpan = total
                            }) { Text("전체") }
                        }

                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = { seekPlayer(playhead - 5) }) { Text("-5초") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = ::togglePlay) {
                                    if (playing) Text("❚❚") else Icon(Icons.Filled.PlayArrow, contentDescription = "재생")
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { seekPlayer(playhead + 5) }) { Text("+5초") }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${formatTrimTime(playhead)} / ${formatTrimTime(total)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            Text(
                                "파형을 끌어 구간을 고르거나, 들으면서 '지금 위치'로 시작·끝을 찍으세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            BoundRow(
                                label = "시작",
                                value = selStart,
                                onNudge = { d ->
                                    selStart = (selStart + d).coerceIn(0.0, total)
                                    if (selEnd < selStart) selEnd = selStart
                                },
                                onSetHere = {
                                    selStart = playhead
                                    if (selEnd < selStart) selEnd = selStart
                                },
                            )
                            BoundRow(
                                label = "끝",
                                value = selEnd,
                                onNudge = { d ->
                                    selEnd = (selEnd + d).coerceIn(0.0, total)
                                    if (selStart > selEnd) selStart = selEnd
                                },
                                onSetHere = {
                                    selEnd = playhead
                                    if (selStart > selEnd) selStart = selEnd
                                },
                            )
                            Text(
                                "선택 길이 ${formatTrimTime(selEnd - selStart)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = ::cutSelection,
                                    enabled = selEnd > selStart && busy == null,
                                    modifier = Modifier.weight(1f),
                                ) { Text("선택 구간 삭제") }
                                OutlinedButton(
                                    onClick = ::keepSelection,
                                    enabled = selEnd > selStart && busy == null,
                                    modifier = Modifier.weight(1f),
                                ) { Text("선택만 남기기") }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = ::saveNew,
                                enabled = removed.isNotEmpty() && busy == null,
                                modifier = Modifier.weight(1f),
                            ) { Text("새 파일로 저장") }
                            Button(
                                onClick = { confirmOverwrite = true },
                                enabled = removed.isNotEmpty() && busy == null && src.canOverwrite,
                                modifier = Modifier.weight(1f),
                            ) { Text("원본에 저장") }
                        }
                    }
                }
            }

            busy?.let { message ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp, shadowElevation = 6.dp) {
                        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator()
                            Spacer(Modifier.width(16.dp))
                            Text(message)
                        }
                    }
                }
            }
        }

        if (confirmClose) {
            AlertDialog(
                onDismissRequest = { confirmClose = false },
                title = { Text("저장하지 않고 닫기") },
                text = { Text("잘라낸 내용이 저장되지 않았습니다. 그냥 닫을까요?") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClose = false
                        onDismiss()
                    }) { Text("닫기") }
                },
                dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("계속 편집") } },
            )
        }
        if (confirmOverwrite) {
            AlertDialog(
                onDismissRequest = { confirmOverwrite = false },
                title = { Text("원본에 저장") },
                text = { Text("'${track.title}' 파일을 잘라낸 내용으로 바꿉니다. 저장한 뒤에는 되돌릴 수 없습니다.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmOverwrite = false
                        saveOverwrite()
                    }) { Text("저장") }
                },
                dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("취소") } },
            )
        }
    }
}

@Composable
private fun BoundRow(label: String, value: Double, onNudge: (Double) -> Unit, onSetHere: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(36.dp))
        Text(formatTrimTime(value), Modifier.width(88.dp), fontWeight = FontWeight.SemiBold)
        TextButton(onClick = { onNudge(-NUDGE_SECONDS) }) { Text("-0.1") }
        TextButton(onClick = { onNudge(NUDGE_SECONDS) }) { Text("+0.1") }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onSetHere) { Text("지금 위치") }
    }
}

@Composable
private fun Waveform(
    peaks: FloatArray,
    total: Double,
    selStart: Double,
    selEnd: Double,
    playhead: Double,
    viewStart: Double,
    viewSpan: Double,
    onSelect: (Double, Double) -> Unit,
    onTap: (Double) -> Unit,
    onView: (start: Double, span: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val wave = MaterialTheme.colorScheme.primary
    val selection = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
    val cursor = MaterialTheme.colorScheme.error
    val background = MaterialTheme.colorScheme.surfaceVariant

    // 제스처는 한 번 걸어 두고 계속 쓰므로 최신 값을 따로 들고 있는다.
    val view = remember { doubleArrayOf(0.0, 1.0, 0.0) }
    view[0] = viewStart
    view[1] = viewSpan
    view[2] = total
    val callbacks = remember { arrayOfNulls<Any>(3) }
    callbacks[0] = onSelect
    callbacks[1] = onTap
    callbacks[2] = onView

    Canvas(
        modifier.pointerInput(Unit) {
            @Suppress("UNCHECKED_CAST")
            awaitEachGesture {
                val select = callbacks[0] as (Double, Double) -> Unit
                val tap = callbacks[1] as (Double) -> Unit
                val changeView = callbacks[2] as (Double, Double) -> Unit
                fun timeAt(x: Float) = (view[0] + x / size.width * view[1]).coerceIn(0.0, view[2])

                val down = awaitFirstDown()
                val anchor = timeAt(down.position.x)
                var multi = false
                var dragging = false
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        multi = true
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        val centroid = event.calculateCentroid()
                        if (zoom > 0f) {
                            val focus = view[0] + centroid.x / size.width * view[1]
                            val span = (view[1] / zoom).coerceIn(min(MIN_VIEW_SECONDS, view[2]), view[2])
                            val start = focus - centroid.x / size.width * span - pan.x / size.width * span
                            changeView(start, span)
                        }
                        event.changes.forEach { it.consume() }
                    } else if (!multi && pressed.size == 1) {
                        val change = pressed[0]
                        if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            dragging = true
                        }
                        if (dragging) {
                            val t = timeAt(change.position.x)
                            select(min(anchor, t), max(anchor, t))
                            change.consume()
                        }
                    }
                } while (event.changes.any { it.pressed })
                if (!multi && !dragging) tap(anchor)
            }
        },
    ) {
        drawRect(background)
        if (total <= 0 || peaks.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val mid = h / 2
        fun x(t: Double) = ((t - viewStart) / viewSpan * w).toFloat()

        if (selEnd > selStart) {
            val left = x(selStart).coerceIn(0f, w)
            val right = x(selEnd).coerceIn(0f, w)
            drawRect(selection, Offset(left, 0f), Size(max(right - left, 1f), h))
        }

        // 화면 한 칸(픽셀)마다 그 사이 막대 중 가장 큰 것을 그린다.
        val perPixel = viewSpan / w * AudioTrimmer.PEAKS_PER_SECOND
        val step = 2
        var px = 0
        while (px < w) {
            val from = ((viewStart * AudioTrimmer.PEAKS_PER_SECOND) + px * perPixel).toInt()
            val to = max(from + 1, ((viewStart * AudioTrimmer.PEAKS_PER_SECOND) + (px + step) * perPixel).toInt())
            var peak = 0f
            for (i in from until min(to, peaks.size)) if (peaks[i] > peak) peak = peaks[i]
            val half = max(peak * mid * 0.95f, 0.5f)
            drawLine(wave, Offset(px.toFloat(), mid - half), Offset(px.toFloat(), mid + half), strokeWidth = step.toFloat())
            px += step
        }

        val cx = x(playhead)
        if (cx in 0f..w) drawLine(cursor, Offset(cx, 0f), Offset(cx, h), strokeWidth = 3f)
    }
}

/** 분:초.소수로 보인다(예: 1:24.47). 한 시간이 넘으면 시:분:초.소수. */
fun formatTrimTime(seconds: Double): String {
    val s = if (seconds.isNaN() || seconds < 0) 0.0 else seconds
    val hours = (s / 3600).toInt()
    val minutes = ((s - hours * 3600) / 60).toInt()
    val rest = s - hours * 3600 - minutes * 60
    return if (hours > 0) {
        "%d:%02d:%05.2f".format(java.util.Locale.ROOT, hours, minutes, rest)
    } else {
        "%d:%05.2f".format(java.util.Locale.ROOT, minutes, rest)
    }
}
