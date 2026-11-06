package com.kyeongrok.youtubemp3

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.media.MediaScannerConnection
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class Track(
    val id: Long,
    val title: String,
    val uri: Uri,
    val dateAdded: Long = 0,
    /** 음성 녹음·통화 녹음처럼 노래가 아닌 소리. 기기 음악 목록에서 따로 모아 보여 준다. */
    val isRecording: Boolean = false,
    val durationMs: Long = 0,
)

/** 받은 MP3를 공용 음악 폴더(Music/YoutubeMp3)에 두고 목록을 읽는다. */
object Library {
    private val RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/YoutubeMp3/"
    private val COLLECTION = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    fun save(context: Context, file: File): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(COLLECTION, values) ?: error("음악 폴더에 저장할 수 없습니다.")
        try {
            resolver.openOutputStream(uri)!!.use { output ->
                file.inputStream().use { it.copyTo(output) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    fun list(context: Context): List<Track> {
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            COLLECTION,
            projection,
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(RELATIVE_PATH),
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                tracks += Track(
                    id = id,
                    title = cursor.getString(1).removeSuffix(".mp3"),
                    uri = ContentUris.withAppendedId(COLLECTION, id),
                )
            }
        }
        return tracks
    }

    /** 녹음 앱들이 흔히 쓰는 폴더 이름. 시스템이 녹음 표시를 안 해 준 파일도 이걸로 가려낸다. */
    private val RECORDING_FOLDERS = Regex(
        "(^|/)(Recordings|Recording|Recorder|Voice Recorder|VoiceRecorder|Sounds|Call|Call recordings|CallRecordings|녹음)(/|$)",
        RegexOption.IGNORE_CASE,
    )
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "3gp", "3ga", "wma", "mp4")

    /**
     * 기기의 소리 파일 전체를 읽고 음악과 녹음으로 나눈다. 벨소리·알림음·알람은 뺀다.
     * 권한이 없으면 이 앱이 저장한 곡만 나온다.
     */
    fun listDevice(context: Context): List<Track> {
        val hasRecordingFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.Audio.Media.IS_MUSIC)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.MediaColumns.DURATION)
            if (hasRecordingFlag) add(MediaStore.Audio.Media.IS_RECORDING)
        }.toTypedArray()
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            COLLECTION,
            projection,
            // 아직 다 읽지 않은 파일은 값이 비어(NULL) 있으므로 그것도 통과시킨다.
            listOf(MediaStore.Audio.Media.IS_RINGTONE, MediaStore.Audio.Media.IS_NOTIFICATION, MediaStore.Audio.Media.IS_ALARM)
                .joinToString(" AND ") { "($it IS NULL OR $it = 0)" },
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val path = cursor.getString(5).orEmpty()
                // 시스템이 아직 파일을 다 읽지 않았으면 비어 있다. 그때는 폴더 이름으로만 가른다.
                val notMusic = !cursor.isNull(4) && cursor.getInt(4) == 0
                val flagged = hasRecordingFlag && cursor.getInt(7) != 0
                tracks += Track(
                    id,
                    cursor.getString(1)?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(2).orEmpty().substringBeforeLast('.'),
                    ContentUris.withAppendedId(COLLECTION, id),
                    cursor.getLong(3),
                    isRecording = flagged || notMusic || RECORDING_FOLDERS.containsMatchIn(path),
                    durationMs = cursor.getLong(6),
                )
            }
        }
        return tracks
    }

    /**
     * 저장소를 훑어 시스템 목록(MediaStore)에 아직 안 올라온 소리 파일을 등록한다.
     * 컴퓨터에서 복사해 넣었거나 녹음 앱이 알리지 않은 파일이 이걸로 보이게 된다. 새로 찾은 개수를 돌려준다.
     */
    @Suppress("DEPRECATION")
    suspend fun scanStorage(context: Context): Int = withContext(Dispatchers.IO) {
        val known = HashSet<String>()
        context.contentResolver.query(COLLECTION, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
            ?.use { cursor -> while (cursor.moveToNext()) cursor.getString(0)?.let(known::add) }

        val found = mutableListOf<String>()
        fun walk(dir: File, depth: Int) {
            if (depth > 8 || found.size > 5000) return
            val children = dir.listFiles() ?: return
            for (file in children) {
                if (file.name.startsWith(".")) continue
                if (file.isDirectory) {
                    // 앱 전용 폴더는 볼 수 없고, 봐도 앱 내부 파일뿐이다.
                    if (depth == 0 && file.name == "Android") continue
                    walk(file, depth + 1)
                } else if (file.extension.lowercase() in AUDIO_EXTENSIONS && file.absolutePath !in known) {
                    found += file.absolutePath
                }
            }
        }
        walk(Environment.getExternalStorageDirectory(), 0)
        if (found.isEmpty()) return@withContext 0

        withTimeoutOrNull(60_000) {
            suspendCancellableCoroutine { continuation ->
                val remaining = AtomicInteger(found.size)
                MediaScannerConnection.scanFile(context, found.toTypedArray(), null) { _, _ ->
                    if (remaining.decrementAndGet() == 0 && continuation.isActive) continuation.resume(Unit)
                }
            }
        }
        found.size
    }

    /** 사용자 동의가 필요한 파일이면 시스템 확인 창을 돌려주고, 바로 지웠으면 null이다. */
    fun requestDelete(context: Context, track: Track): IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return MediaStore.createDeleteRequest(context.contentResolver, listOf(track.uri)).intentSender
        }
        return try {
            delete(context, track)
            null
        } catch (e: RecoverableSecurityException) {
            e.userAction.actionIntent.intentSender
        }
    }

    fun delete(context: Context, track: Track) {
        context.contentResolver.delete(track.uri, null, null)
    }
}
