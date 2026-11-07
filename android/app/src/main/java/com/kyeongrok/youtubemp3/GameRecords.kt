package com.kyeongrok.youtubemp3

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import java.io.File

/** 기록을 앱 저장소의 JSON 배열 파일로 읽고 쓴다. */
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
