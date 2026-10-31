package com.kyeongrok.youtubemp3

import android.content.Context
import android.util.AtomicFile
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 게임 기록을 앱 저장소의 JSON 배열 파일로 읽고 쓴다. */
object RecordFile {
    fun read(context: Context, name: String): JSONArray? {
        val file = File(context.filesDir, name)
        if (!file.exists()) return null
        return JSONArray(String(AtomicFile(file).readFully()))
    }

    fun write(context: Context, name: String, array: JSONArray) {
        val file = AtomicFile(File(context.filesDir, name))
        val output = file.startWrite()
        try {
            output.write(array.toString().toByteArray())
            file.finishWrite(output)
        } catch (e: Exception) {
            file.failWrite(output)
            throw e
        }
    }
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    return if (minutes >= 60) {
        "%d:%02d:%02d".format(minutes / 60, minutes % 60, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}

/** rows는 최신순의 (요약 문구, 끝낸 시각)이다. */
@Composable
fun GameRecordsDialog(rows: List<Pair<String, Long>>, onDismiss: () -> Unit) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("기록") },
        text = {
            if (rows.isEmpty()) {
                Text("아직 완성한 게임이 없습니다.")
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(rows) { (summary, finishedAt) ->
                        val finished = Instant.ofEpochMilli(finishedAt).atZone(ZoneId.systemDefault())
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(summary)
                            Text(dateFormat.format(finished), style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
