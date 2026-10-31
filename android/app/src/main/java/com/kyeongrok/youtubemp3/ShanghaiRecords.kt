package com.kyeongrok.youtubemp3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ShanghaiRecord(val finishedAt: Long, val seconds: Int, val shuffles: Int)

object ShanghaiRecords {
    private const val FILE_NAME = "shanghai-records.json"

    fun load(context: Context): List<ShanghaiRecord> {
        val array = RecordFile.read(context, FILE_NAME) ?: return emptyList()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            ShanghaiRecord(item.getLong("finishedAt"), item.getInt("seconds"), item.getInt("shuffles"))
        }
    }

    fun save(context: Context, records: List<ShanghaiRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("finishedAt", record.finishedAt)
                    .put("seconds", record.seconds)
                    .put("shuffles", record.shuffles),
            )
        }
        RecordFile.write(context, FILE_NAME, array)
    }
}
