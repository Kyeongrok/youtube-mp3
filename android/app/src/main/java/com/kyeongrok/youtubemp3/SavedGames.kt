package com.kyeongrok.youtubemp3

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 진행 중인 게임을 앱 저장소에 둔다. 끝난 판이나 읽을 수 없는 파일은 없는 셈 치고 새 게임을 시작한다. */
object SavedGames {
    private const val SUDOKU_FILE = "sudoku-save.json"
    private const val SHANGHAI_FILE = "shanghai-save.json"
    private const val FREECELL_FILE = "freecell-save.json"
    private const val RUMMIKUB_FILE = "rummikub-save.json"
    private const val SPLENDOR_FILE = "splendor-save.json"
    private const val SUIT_UNIT = 100
    private const val NO_WINNER = -1

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

    private fun JSONArray.tiles() = ints().map(::RummikubTile)

    private fun tileIds(tiles: List<RummikubTile>) = JSONArray(tiles.map { it.id })

    /** 차례 도중에 옮긴 타일은 저장하지 않고 차례가 시작될 때로 돌아간다. */
    fun saveRummikub(context: Context, game: RummikubGame) {
        write(
            context,
            RUMMIKUB_FILE,
            JSONObject()
                .put("racks", JSONArray(game.racks.map(::tileIds)))
                .put("table", JSONArray(game.table.map(::tileIds)))
                .put("pool", tileIds(game.pool))
                .put("opened", JSONArray(game.opened))
                .put("turn", game.turn)
                .put("passes", game.passes)
                .put("winner", game.winner ?: NO_WINNER)
                .put("seconds", game.seconds),
        )
    }

    fun loadRummikub(context: Context) = read(context, RUMMIKUB_FILE) { item ->
        val racks = item.getJSONArray("racks")
        val table = item.getJSONArray("table")
        val opened = item.getJSONArray("opened")
        val winner = item.getInt("winner")
        RummikubGame(
            racks = List(racks.length()) { racks.getJSONArray(it).tiles() },
            table = List(table.length()) { table.getJSONArray(it).tiles() },
            pool = item.getJSONArray("pool").tiles(),
            opened = List(opened.length()) { opened.getBoolean(it) },
            turn = item.getInt("turn"),
            passes = item.getInt("passes"),
            winner = winner.takeUnless { it == NO_WINNER },
            seconds = item.getInt("seconds"),
        )
    }?.takeUnless { it.isFinished }

    private fun JSONArray.nullableInts() = List(length()) { if (isNull(it)) null else getInt(it) }

    private fun nullable(values: List<Int?>) = JSONArray().apply { values.forEach { put(it ?: JSONObject.NULL) } }

    /** 고르던 보석은 저장하지 않는다. */
    fun saveSplendor(context: Context, game: SplendorGame) {
        write(
            context,
            SPLENDOR_FILE,
            JSONObject()
                .put(
                    "players",
                    JSONArray(
                        game.players.map { player ->
                            JSONObject()
                                .put("tokens", JSONArray(player.tokens))
                                .put("cards", JSONArray(player.cards))
                                .put("reserved", JSONArray(player.reserved))
                                .put("nobles", JSONArray(player.nobles))
                        },
                    ),
                )
                .put("bank", JSONArray(game.bank))
                .put("decks", JSONArray(game.decks.map { JSONArray(it) }))
                .put("market", JSONArray(game.market.map(::nullable)))
                .put("nobles", JSONArray(game.nobles))
                .put("turn", game.turn)
                .put("winner", game.winner ?: NO_WINNER)
                .put("seconds", game.seconds)
                .put("log", JSONArray(game.log)),
        )
    }

    fun loadSplendor(context: Context) = read(context, SPLENDOR_FILE) { item ->
        val players = item.getJSONArray("players")
        val decks = item.getJSONArray("decks")
        val market = item.getJSONArray("market")
        val log = item.getJSONArray("log")
        val winner = item.getInt("winner")
        SplendorGame(
            players = List(players.length()) { index ->
                val player = players.getJSONObject(index)
                SplendorPlayer(
                    player.getJSONArray("tokens").ints(),
                    player.getJSONArray("cards").ints(),
                    player.getJSONArray("reserved").ints(),
                    player.getJSONArray("nobles").ints(),
                )
            },
            bank = item.getJSONArray("bank").ints(),
            decks = List(decks.length()) { decks.getJSONArray(it).ints() },
            market = List(market.length()) { market.getJSONArray(it).nullableInts() },
            nobles = item.getJSONArray("nobles").ints(),
            turn = item.getInt("turn"),
            winner = winner.takeUnless { it == NO_WINNER },
            seconds = item.getInt("seconds"),
            log = List(log.length()) { log.getString(it) },
        )
    }?.takeUnless { it.isFinished }
}
