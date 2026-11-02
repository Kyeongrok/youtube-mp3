package com.kyeongrok.youtubemp3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SplendorRecord(val finishedAt: Long, val cpus: Int, val seconds: Int, val points: Int)

object SplendorRecords {
    private const val FILE_NAME = "splendor-records.json"

    fun load(context: Context): List<SplendorRecord> {
        val array = RecordFile.read(context, FILE_NAME) ?: return emptyList()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            SplendorRecord(item.getLong("finishedAt"), item.getInt("cpus"), item.getInt("seconds"), item.getInt("points"))
        }
    }

    fun save(context: Context, records: List<SplendorRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("finishedAt", record.finishedAt)
                    .put("cpus", record.cpus)
                    .put("seconds", record.seconds)
                    .put("points", record.points),
            )
        }
        RecordFile.write(context, FILE_NAME, array)
    }
}
