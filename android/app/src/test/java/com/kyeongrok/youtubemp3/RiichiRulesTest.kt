package com.kyeongrok.youtubemp3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RiichiRulesTest {
    /** "123m456p789s11z" 같은 표기를 패 번호로 바꾼다. 같은 종류는 0번째 장부터 차례로 쓴다(5는 적도라를 피한다). */
    private fun tiles(text: String): List<Int> {
        val used = IntArray(34)
        val result = mutableListOf<Int>()
        val digits = mutableListOf<Int>()
        for (ch in text) {
            if (ch.isDigit()) {
                digits += ch - '0'
                continue
            }
            val base = when (ch) { 'm' -> 0; 'p' -> 9; 's' -> 18; else -> 27 }
            for (d in digits) {
                val kind = base + d - 1
                var copy = used[kind]++
                if (kind == 4 || kind == 13 || kind == 22) copy = (copy + 1) % 4
                result += kind * 4 + copy
            }
            digits.clear()
        }
        return result
    }

    private fun ctx(hand: String, win: String, tsumo: Boolean, riichi: Boolean = false, dealer: Boolean = false, seat: Int = 1) : WinContext {
        val h = tiles(hand)
        val w = h.last { Mahjong.kind(it) == Mahjong.kind(tiles(win)[0]) }
        return WinContext(hand = h, melds = emptyList(), winTile = w, tsumo = tsumo, seatWind = seat, roundWind = 0, dealer = dealer, riichi = riichi)
    }

    @Test fun shanten() {
        assertEquals(-1, Mahjong.shanten(Mahjong.counts(tiles("123m456p789s11122z")), 0))
        assertEquals(0, Mahjong.shanten(Mahjong.counts(tiles("123m456p789s1112z")), 0))
        assertEquals(-1, Mahjong.shanten(Mahjong.counts(tiles("1199m1199p1199s11z").let { tiles("1199m1199p1199s1z") + tiles("1z") }), 0).coerceAtMost(0))
        assertEquals(-1, Mahjong.shanten(Mahjong.counts(tiles("19m19p19s12345677z")), 0))
    }

    @Test fun waits() {
        assertEquals(listOf(0, 3, 6), Mahjong.waits(Mahjong.counts(tiles("23456m456p789s11z")), 0))
    }

    @Test fun pinfuRiichiTsumo() {
        // 리치 멘젠쯔모 핑후 = 3판 20부 → 자 쯔모 700·1300
        val score = RiichiScoring.score(ctx("234567m456p789s33p", "7m", tsumo = true, riichi = true))!!
        assertEquals(20, score.fu)
        assertEquals(3, score.han)
        assertEquals(700 to 1300, score.tsumo(false).let { it.second to it.first })
    }

    @Test fun noYakuIsNull() {
        // 운 것 없이 역이 없는 론: 1만이 끼어 탕야오도 아니고 샤보 대기라 핑후도 아니다.
        assertNull(RiichiScoring.score(ctx("111m234p678s99s567p", "7p", tsumo = false).copy(winTile = tiles("111m")[2])))
    }

    @Test fun tanyaoRon30Fu() {
        // 탕야오 1판 멘젠론 40부? 234m 567p 345s 66s 678m 간짱 아님 → 핑후+탕야오 30부 2판 = 2000
        val score = RiichiScoring.score(ctx("234678m567p345s66s", "8m", tsumo = false))!!
        assertEquals(30, score.fu)
        assertEquals(2, score.han)
        assertEquals(2000, score.ron(false))
    }

    @Test fun chiitoi() {
        val score = RiichiScoring.score(ctx("1199m1199p1199s11z", "1z", tsumo = false))!!
        assertEquals(25, score.fu)
        assertTrue(score.yaku.any { it.name == "치또이쯔" })
        assertTrue(score.yaku.any { it.name == "혼노두" })
    }

    @Test fun kokushi() {
        val score = RiichiScoring.score(ctx("19m19p19s12345677z", "7z", tsumo = false))!!
        assertEquals(1, score.yakuman)
        assertEquals(32000, score.ron(false))
    }

    @Test fun dealerMangan() {
        // 친 혼일색(3) + 역패 중(1) + 일기통관(2) 론 → 하네만 18000
        val score = RiichiScoring.score(ctx("123456789m55z555z".replace("55z555z", "11z555z"), "9m", tsumo = false, dealer = true, seat = 0))
        assertTrue(score != null)
        assertEquals(18000, score!!.ron(true))
    }

    @Test fun table_runs() {
        var hands = 0; var wins = 0; var draws = 0; var calls = 0; var riichis = 0
        val yakuSeen = sortedMapOf<String, Int>()
        repeat(30) {
            val table = RiichiTable.newGame(4)
            var steps = 0
            while (table.phase != RiichiPhase.GameOver && steps < 20000) {
                steps++
                when (table.phase) {
                    RiichiPhase.Turn -> if (table.turn == RiichiTable.HUMAN) {
                        // 사람 자리도 컴퓨터처럼 둔다.
                        if (table.tsumoScore(0) != null) table.tsumo(0)
                        else table.discard(0, table.seats[0].hand.first { table.canDiscard(0, it) })
                    } else table.aiTurn()
                    RiichiPhase.Claim -> table.humanClaim(if (table.claimOptions().ron) ClaimChoice.Ron else ClaimChoice.Pass)
                    RiichiPhase.HandOver -> {
                        val r = table.result!!
                        hands++
                        if (r.draw) draws++ else wins++
                        r.yaku.forEach { y -> yakuSeen[y.name] = (yakuSeen[y.name] ?: 0) + 1 }
                        calls += table.seats.sumOf { s -> s.melds.count { it.isOpen } }
                        riichis += table.seats.count { it.riichi }
                        // 중간 저장 왕복
                        val copy = RiichiTable.fromJson(table.toJson())
                        assertEquals(table.toJson().toString(), copy.toJson().toString())
                        table.nextHand()
                    }
                    RiichiPhase.GameOver -> {}
                }
                val total = table.seats.sumOf { it.points } + table.sticks * 1000 + (if (table.pendingRiichi >= 0) 0 else 0)
                assertEquals(100000, total)
                val tileCount = table.seats.sumOf { s -> s.hand.size + s.melds.sumOf { it.tiles.size } + s.discards.count { !it.called } }
                assertTrue(tileCount <= 136)
            }
            assertEquals(RiichiPhase.GameOver, table.phase)
            // JSON 왕복
            RiichiTable.fromJson(table.toJson())
        }
        println("hands=$hands wins=$wins draws=$draws calls=$calls riichi=$riichis")
        println(yakuSeen)
    }
}
