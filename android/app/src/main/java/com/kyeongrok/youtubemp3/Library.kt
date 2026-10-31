package com.kyeongrok.youtubemp3

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

data class Track(val id: Long, val title: String, val uri: Uri, val dateAdded: Long = 0)

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

    /** 기기의 음악 전체를 읽는다. 권한이 없으면 이 앱이 저장한 곡만 나온다. */
    fun listDevice(context: Context): List<Track> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val tracks = mutableListOf<Track>()
        context.contentResolver.query(
            COLLECTION,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                tracks += Track(
                    id,
                    cursor.getString(1).orEmpty(),
                    ContentUris.withAppendedId(COLLECTION, id),
                    cursor.getLong(2),
                )
            }
        }
        return tracks
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
