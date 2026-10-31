package com.kyeongrok.youtubemp3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SudokuRecord(val finishedAt: Long, val level: SudokuLevel, val seconds: Int, val mistakes: Int)

object SudokuRecords {
    private const val FILE_NAME = "sudoku-records.json"

    fun load(context: Context): List<SudokuRecord> {
        val array = RecordFile.read(context, FILE_NAME) ?: return emptyList()
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            SudokuRecord(
                item.getLong("finishedAt"),
                SudokuLevel.valueOf(item.getString("level")),
                item.getInt("seconds"),
                item.getInt("mistakes"),
            )
        }
    }

    fun save(context: Context, records: List<SudokuRecord>) {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject()
                    .put("finishedAt", record.finishedAt)
                    .put("level", record.level.name)
                    .put("seconds", record.seconds)
                    .put("mistakes", record.mistakes),
            )
        }
        RecordFile.write(context, FILE_NAME, array)
    }
}
