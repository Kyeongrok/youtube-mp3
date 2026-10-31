package com.kyeongrok.youtubemp3

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 곡은 MediaStore ID로 기억한다. 기기에서 지워진 곡은 목록을 읽을 때 걸러진다. */
data class Playlist(val id: Long, val name: String, val trackIds: List<Long>)

object Playlists {
    private const val FILE_NAME = "playlists.json"

    fun load(context: Context): List<Playlist> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        val array = JSONArray(String(AtomicFile(file).readFully()))
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val ids = item.getJSONArray("trackIds")
            Playlist(item.getLong("id"), item.getString("name"), List(ids.length()) { ids.getLong(it) })
        }
    }

    fun save(context: Context, playlists: List<Playlist>) {
        val array = JSONArray()
        playlists.forEach { playlist ->
            array.put(
                JSONObject()
                    .put("id", playlist.id)
                    .put("name", playlist.name)
                    .put("trackIds", JSONArray(playlist.trackIds)),
            )
        }
        val file = AtomicFile(File(context.filesDir, FILE_NAME))
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
