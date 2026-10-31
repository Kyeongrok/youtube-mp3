package com.kyeongrok.youtubemp3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 진행 중인 게임을 앱 저장소에 둔다. 끝난 판이나 읽을 수 없는 파일은 없는 셈 치고 새 게임을 시작한다. */
object SavedGames {
    private const val SUDOKU_FILE = "sudoku-save.json"
    private const val SHANGHAI_FILE = "shanghai-save.json"
    private const val FREECELL_FILE = "freecell-save.json"
    private const val SUIT_UNIT = 100

    private fun write(context: Context, name: String, game: JSONObject) {
        RecordFile.write(context, name, JSONArray().put(game))
    }

    private fun <T> read(context: Context, name: String, parse: (JSONObject) -> T): T? =
        try {
            RecordFile.read(context, name)?.let { parse(it.getJSONObject(0)) }
        } catch (_: Exception) {
            null
        }

    private fun JSONArray.ints() = List(length()) { getInt(it) }

    fun saveSudoku(context: Context, game: SudokuGame) {
        write(
            context,
            SUDOKU_FILE,
            JSONObject()
                .put("level", game.level.name)
                .put("puzzle", JSONArray(game.puzzle))
                .put("solution", JSONArray(game.solution))
                .put("cells", JSONArray(game.cells))
                .put("notes", JSONArray(game.notes.map { JSONArray(it.toList()) }))
                .put("mistakes", game.mistakes)
                .put("seconds", game.seconds),
        )
    }

    fun loadSudoku(context: Context) = read(context, SUDOKU_FILE) { item ->
        val notes = item.getJSONArray("notes")
        SudokuGame(
            SudokuLevel.valueOf(item.getString("level")),
            item.getJSONArray("puzzle").ints(),
            item.getJSONArray("solution").ints(),
            item.getJSONArray("cells").ints(),
            List(notes.length()) { notes.getJSONArray(it).ints().toSet() },
            item.getInt("mistakes"),
            item.getInt("seconds"),
        )
    }?.takeUnless { it.isComplete }

    fun saveShanghai(context: Context, game: ShanghaiGame) {
        write(
            context,
            SHANGHAI_FILE,
            JSONObject()
                .put("faces", JSONArray(game.faces))
                .put("shuffles", game.shuffles)
                .put("seconds", game.seconds),
        )
    }

    fun loadShanghai(context: Context) = read(context, SHANGHAI_FILE) { item ->
        ShanghaiGame(
            item.getJSONArray("faces").ints(),
            shuffles = item.getInt("shuffles"),
            seconds = item.getInt("seconds"),
        )
    }?.takeUnless { it.isComplete }

    private fun encode(card: PlayingCard?) = card?.let { it.suit * SUIT_UNIT + it.rank } ?: 0

    private fun decode(value: Int) = PlayingCard(value / SUIT_UNIT, value % SUIT_UNIT)

    /** 실행취소 내역은 판마다 길어져 저장하지 않는다. */
    fun saveFreeCell(context: Context, game: FreeCellGame) {
        write(
            context,
            FREECELL_FILE,
            JSONObject()
                .put("cascades", JSONArray(game.cascades.map { cards -> JSONArray(cards.map(::encode)) }))
                .put("cells", JSONArray(game.cells.map(::encode)))
                .put("foundations", JSONArray(game.foundations)),
        )
    }

    fun loadFreeCell(context: Context) = read(context, FREECELL_FILE) { item ->
        val cascades = item.getJSONArray("cascades")
        FreeCellGame(
            List(cascades.length()) { cascades.getJSONArray(it).ints().map(::decode) },
            item.getJSONArray("cells").ints().map { value -> if (value == 0) null else decode(value) },
            item.getJSONArray("foundations").ints(),
        )
    }?.takeUnless { it.isComplete }
}
