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
    /** 기기 안의 폴더(예: Recordings/Voice Recorder/). 녹음·음악 구분을 폴더 단위로 바꿀 때 쓴다. */
    val folder: String = "",
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
                    title = cursor.getString(1).substringBeforeLast('.'),
                    uri = ContentUris.withAppendedId(COLLECTION, id),
                )
            }
        }
        return tracks
    }

    private const val PREFS = "library"
    private const val KEY_RECORDING_FOLDERS = "recordingFolders"
    private const val KEY_MUSIC_FOLDERS = "musicFolders"

    /** 이 폴더의 파일을 앞으로 녹음(또는 음악)으로 보여 준다. 자동 구분이 틀렸을 때 쓴다. */
    fun setFolderKind(context: Context, folder: String, recording: Boolean) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recordings = prefs.getStringSet(KEY_RECORDING_FOLDERS, emptySet()).orEmpty().toMutableSet()
        val music = prefs.getStringSet(KEY_MUSIC_FOLDERS, emptySet()).orEmpty().toMutableSet()
        if (recording) {
            recordings += folder
            music -= folder
        } else {
            music += folder
            recordings -= folder
        }
        prefs.edit().putStringSet(KEY_RECORDING_FOLDERS, recordings).putStringSet(KEY_MUSIC_FOLDERS, music).apply()
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
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val recordingFolders = prefs.getStringSet(KEY_RECORDING_FOLDERS, emptySet()).orEmpty()
        val musicFolders = prefs.getStringSet(KEY_MUSIC_FOLDERS, emptySet()).orEmpty()
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
            "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val path = cursor.getString(5).orEmpty()
                // 시스템이 아직 파일을 다 읽지 않았으면 비어 있다. 그때는 폴더 이름으로만 가른다.
                val notMusic = !cursor.isNull(4) && cursor.getInt(4) == 0
                val flagged = hasRecordingFlag && cursor.getInt(7) != 0
                tracks += Track(
                    id,
                    // 이름 바꾸기가 고치는 건 파일 이름이므로 파일 이름을 보여 준다. 없으면 태그 제목.
                    cursor.getString(2)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(1).orEmpty(),
                    ContentUris.withAppendedId(COLLECTION, id),
                    cursor.getLong(3),
                    // 사용자가 폴더를 직접 정했으면 그걸 따른다.
                    isRecording = when (path) {
                        in recordingFolders -> true
                        in musicFolders -> false
                        else -> flagged || notMusic || RECORDING_FOLDERS.containsMatchIn(path)
                    },
                    durationMs = cursor.getLong(6),
                    folder = path,
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

    /**
     * 파일 이름을 바꾼다. 확장자는 그대로 둔다.
     * 다른 앱이 만든 파일이면 SecurityException이 나므로, 시스템 허락을 받은 뒤 다시 부른다.
     */
    fun rename(context: Context, track: Track, newTitle: String) {
        val resolver = context.contentResolver
        val current = resolver.query(track.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: error("파일을 찾을 수 없습니다.")
        val extension = current.substringAfterLast('.', "")
        // 확장자까지 적어도 두 번 붙지 않게 뗀다.
        val safe = newTitle.trim().removeSuffix(".$extension").replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        if (safe.isEmpty()) error("이름이 비어 있습니다.")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, if (extension.isEmpty()) safe else "$safe.$extension")
            put(MediaStore.MediaColumns.TITLE, safe)
        }
        if (resolver.update(track.uri, values, null, null) == 0) error("이름을 바꾸지 못했습니다.")
    }

    /** 다른 앱이 만든 파일을 고쳐도 되는지 묻는 시스템 창. Android 10은 예외에 담겨 오므로 null이다. */
    fun writeRequest(context: Context, track: Track): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(context.contentResolver, listOf(track.uri)).intentSender
        } else {
            null
        }

    fun delete(context: Context, track: Track) {
        context.contentResolver.delete(track.uri, null, null)
    }
}
