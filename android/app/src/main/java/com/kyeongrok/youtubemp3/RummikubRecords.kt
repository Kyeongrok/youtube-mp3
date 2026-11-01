package com.kyeongrok.youtubemp3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RummikubRecord(val finishedAt: Long, val cpus: Int, val seconds: Int, val score: Int)

object RummikubRecords {
    private const val FILE_NAME = "rummikub-records.json"

    fun load(context: Context): List<RummikubRecord> {
        val array = RecordFile.read(context, FILE_NAME) ?: return emptyList()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            RummikubRecord(item.getLong("finishedAt"), item.getInt("cpus"), item.getInt("seconds"), item.getInt("score"))
        }
    }

    fun save(context: Context, records: List<RummikubRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("finishedAt", record.finishedAt)
                    .put("cpus", record.cpus)
                    .put("seconds", record.seconds)
                    .put("score", record.score),
            )
        }
        RecordFile.write(context, FILE_NAME, array)
    }
}
