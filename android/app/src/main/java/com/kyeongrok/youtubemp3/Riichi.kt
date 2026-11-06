package com.kyeongrok.youtubemp3

import org.json.JSONArray
import org.json.JSONObject

/** 버린 패. called는 누가 울어 가져간 패, riichi는 리치 선언패다. */
data class RiichiDiscard(val tile: Int, val called: Boolean = false, val riichi: Boolean = false)

class RiichiSeat(var points: Int) {
    val hand = mutableListOf<Int>()
    val melds = mutableListOf<Meld>()
    val discards = mutableListOf<RiichiDiscard>()
    var riichi = false
    var doubleRiichi = false
    var ippatsu = false
    /** 화료할 수 있는 패를 놓친 뒤 자기 차례가 오기 전까지 론을 못 한다. */
    var tempFuriten = false
    /** 리치 뒤에 놓치면 그 판 내내 론을 못 한다. */
    var riichiFuriten = false

    val isClosed get() = melds.all { !it.isOpen }
    fun counts() = Mahjong.counts(hand)
    fun shanten() = Mahjong.shanten(counts(), melds.size)
    fun waits() = Mahjong.waits(counts(), melds.size)

    fun isFuriten(): Boolean {
        if (tempFuriten || riichiFuriten) return true
        val waits = waits()
        return discards.any { Mahjong.kind(it.tile) in waits }
    }

    fun sortHand() = hand.sortBy { it }
}

enum class RiichiPhase { Turn, Claim, HandOver, GameOver }

/** 남이 버린 패(또는 가깡한 패)를 두고 내가 할 수 있는 것. */
data class ClaimOptions(
    val ron: Boolean = false,
    val pon: List<Int>? = null,
    val kan: List<Int>? = null,
    val chi: List<List<Int>> = emptyList(),
) {
    val any get() = ron || pon != null || kan != null || chi.isNotEmpty()
    val anyCall get() = pon != null || kan != null || chi.isNotEmpty()
}

sealed interface ClaimChoice {
    data object Ron : ClaimChoice
    data object Pon : ClaimChoice
    data object Kan : ClaimChoice
    data class Chi(val tiles: List<Int>) : ClaimChoice
    data object Pass : ClaimChoice
}

/** 판이 끝났을 때 보여 줄 내용. 점수는 이미 옮겨 놓았다. */
data class RiichiHandResult(
    val title: String,
    /** 화료한 자리. 유국이면 -1. */
    val winner: Int,
    val loser: Int,
    val hand: List<Int>,
    val melds: List<Meld>,
    val winTile: Int,
    val yaku: List<Yaku>,
    val summary: String,
    val deltas: List<Int>,
    val tenpai: List<Boolean>,
    val dora: List<Int>,
    val ura: List<Int>,
    val dealerKeeps: Boolean,
    val draw: Boolean,
)

private class Call(val player: Int, val choice: ClaimChoice, val tiles: List<Int>) {
    val priority get() = if (choice is ClaimChoice.Chi) 1 else 2
}

/**
 * 4인 리치마작 한 대국. 자리 0이 사람이고 1(하가), 2(대면), 3(상가) 순서로 돈다.
 * 화면은 이 객체를 직접 읽고, 바뀔 때마다 ViewModel이 버전을 올려 다시 그린다.
 */
class RiichiTable(
    /** 4면 동풍전, 8이면 반장전. */
    val length: Int,
) {
    var round = 0
    var honba = 0
    var sticks = 0
    val seats = List(4) { RiichiSeat(START_POINTS) }
    var wall: List<Int> = emptyList()
    var next = 0
    var kans = 0
    var turn = 0
    var phase = RiichiPhase.Turn
    /** 이번 차례에 뽑은 패. 운 직후라 뽑지 않았으면 -1. */
    var drawn = -1
    var rinshanDraw = false
    /** 운 직후 바로 버릴 수 없는 패 종류(쿠이카에). */
    var forbidden: List<Int> = emptyList()
    /** 아직 아무도 울지 않았고 첫 바퀴다. 더블 리치·천화·지화에 쓴다. */
    var untouched = true
    /** 리치를 선언하며 버린 자리. 그 패로 론을 당하지 않으면 공탁금을 낸다. */
    var pendingRiichi = -1
    var claimTile = -1
    var claimFrom = -1
    var chankan = false
    var result: RiichiHandResult? = null
    var message = ""

    val dealer get() = round % 4
    val roundWind get() = round / 4
    fun seatWind(player: Int) = (player - dealer + 4) % 4
    val liveEnd get() = DEAD_WALL_START - kans
    val remaining get() = liveEnd - next
    val doraIndicators get() = (0..kans).map { wall[DEAD_WALL_START + 8 - 2 * it] }
    val uraIndicators get() = (0..kans).map { wall[DEAD_WALL_START + 9 - 2 * it] }
    val roundName get() = "${Mahjong.WIND_NAMES[roundWind]} ${dealer + 1}국"

    fun startHand() {
        wall = (0 until 136).shuffled()
        next = 0
        kans = 0
        seats.forEach { seat ->
            seat.hand.clear()
            seat.melds.clear()
            seat.discards.clear()
            seat.riichi = false
            seat.doubleRiichi = false
            seat.ippatsu = false
            seat.tempFuriten = false
            seat.riichiFuriten = false
        }
        for (i in 0 until 4) {
            val seat = seats[(dealer + i) % 4]
            repeat(13) { seat.hand += wall[next++] }
            seat.sortHand()
        }
        untouched = true
        pendingRiichi = -1
        result = null
        forbidden = emptyList()
        message = ""
        draw(dealer)
    }

    private fun draw(player: Int, rinshan: Boolean = false) {
        val tile = if (rinshan) wall[wall.size - kans] else wall[next++]
        seats[player].hand += tile
        turn = player
        drawn = tile
        rinshanDraw = rinshan
        forbidden = emptyList()
        phase = RiichiPhase.Turn
    }

    // ── 자기 차례에 할 수 있는 것 ──

    fun winContext(player: Int, winTile: Int, tsumo: Boolean, hand: List<Int>, chankan: Boolean = false): WinContext {
        val seat = seats[player]
        return WinContext(
            hand = hand,
            melds = seat.melds.toList(),
            winTile = winTile,
            tsumo = tsumo,
            seatWind = seatWind(player),
            roundWind = roundWind,
            dealer = player == dealer,
            riichi = seat.riichi,
            doubleRiichi = seat.doubleRiichi,
            ippatsu = seat.ippatsu,
            lastTile = remaining == 0,
            rinshan = tsumo && rinshanDraw,
            chankan = chankan,
            firstDraw = tsumo && untouched && seat.discards.isEmpty(),
            doraIndicators = doraIndicators,
            uraIndicators = uraIndicators,
        )
    }

    fun tsumoScore(player: Int): WinScore? {
        if (phase != RiichiPhase.Turn || turn != player || drawn < 0) return null
        val seat = seats[player]
        if (seat.shanten() != -1) return null
        return RiichiScoring.score(winContext(player, drawn, tsumo = true, hand = seat.hand.toList()))
    }

    /** 리치를 걸며 버릴 수 있는 패. 리치할 수 없으면 비어 있다. */
    fun riichiDiscards(player: Int): Set<Int> {
        val seat = seats[player]
        if (phase != RiichiPhase.Turn || turn != player || seat.riichi || !seat.isClosed) return emptySet()
        if (seat.points < 1000 || remaining < 4) return emptySet()
        val counts = seat.counts()
        val okKinds = (0 until Mahjong.KINDS).filter { k ->
            if (counts[k] == 0) return@filter false
            counts[k]--
            val tenpai = Mahjong.shanten(counts, seat.melds.size) == 0
            counts[k]++
            tenpai
        }.toSet()
        return seat.hand.filter { Mahjong.kind(it) in okKinds }.toSet()
    }

    /** 깡할 수 있는 패 종류(안깡·가깡). 리치 중에는 하지 않는다. */
    fun kanOptions(player: Int): List<Int> {
        val seat = seats[player]
        if (phase != RiichiPhase.Turn || turn != player || seat.riichi) return emptyList()
        if (remaining <= 0 || kans >= 4) return emptyList()
        val counts = seat.counts()
        val closed = (0 until Mahjong.KINDS).filter { counts[it] == 4 }
        val added = seat.melds.filter { it.type == MeldType.Pon && counts[it.kind] > 0 }.map { it.kind }
        return closed + added
    }

    fun canDiscard(player: Int, tile: Int): Boolean {
        val seat = seats[player]
        if (phase != RiichiPhase.Turn || turn != player || tile !in seat.hand) return false
        if (seat.riichi) return tile == drawn
        if (Mahjong.kind(tile) in forbidden && seat.hand.any { Mahjong.kind(it) !in forbidden }) return false
        return true
    }

    fun discard(player: Int, tile: Int, declareRiichi: Boolean = false) {
        if (!canDiscard(player, tile)) return
        val seat = seats[player]
        if (declareRiichi && tile !in riichiDiscards(player)) return
        val wasRiichi = seat.riichi
        seat.hand.remove(tile)
        seat.sortHand()
        seat.discards += RiichiDiscard(tile, riichi = declareRiichi)
        seat.tempFuriten = false
        if (wasRiichi) seat.ippatsu = false
        if (declareRiichi) {
            seat.riichi = true
            seat.ippatsu = true
            seat.doubleRiichi = untouched && seat.discards.size == 1
            pendingRiichi = player
            message = "${seatName(player)} 리치"
        }
        drawn = -1
        rinshanDraw = false
        // 한 바퀴 돌아 친이 다시 버리면 첫 바퀴가 끝난다.
        if (seats.all { it.discards.isNotEmpty() }) untouched = false
        claimTile = tile
        claimFrom = player
        chankan = false
        resolveClaims(humanDecided = false)
    }

    fun tsumo(player: Int) {
        val score = tsumoScore(player) ?: return
        val seat = seats[player]
        val dealerWins = player == dealer
        val (fromDealer, fromOther) = score.tsumo(dealerWins)
        val deltas = MutableList(4) { 0 }
        for (other in 0 until 4) {
            if (other == player) continue
            val pay = (if (other == dealer) fromDealer else fromOther) + 100 * honba
            deltas[other] -= pay
            deltas[player] += pay
        }
        deltas[player] += sticks * 1000
        val payText = if (dealerWins) "${fromDealer}점 올" else "${fromOther}·${fromDealer}점"
        finishWin(player, -1, seat.hand.toList(), drawn, score, deltas, "쯔모", payText)
    }

    fun kan(player: Int, kind: Int) {
        if (kind !in kanOptions(player)) return
        val seat = seats[player]
        val ponIndex = seat.melds.indexOfFirst { it.type == MeldType.Pon && it.kind == kind }
        untouched = false
        seats.forEach { it.ippatsu = false }
        if (ponIndex < 0) {
            val tiles = seat.hand.filter { Mahjong.kind(it) == kind }
            seat.hand.removeAll(tiles)
            seat.melds += Meld(MeldType.ClosedKan, tiles)
            kans++
            message = "${seatName(player)} 안깡"
            draw(player, rinshan = true)
        } else {
            // 가깡은 다른 사람이 그 패로 론(창깡)할 수 있다.
            val tile = seat.hand.first { Mahjong.kind(it) == kind }
            seat.hand.remove(tile)
            val pon = seat.melds[ponIndex]
            seat.melds[ponIndex] = Meld(MeldType.AddedKan, pon.tiles + tile, pon.called, pon.from)
            drawn = -1
            claimTile = tile
            claimFrom = player
            chankan = true
            message = "${seatName(player)} 가깡"
            resolveClaims(humanDecided = false)
        }
    }

    // ── 남이 버린 패 ──

    fun ronScore(player: Int, tile: Int, isChankan: Boolean): WinScore? {
        val seat = seats[player]
        if (seat.isFuriten()) return null
        val hand = seat.hand + tile
        if (Mahjong.shanten(Mahjong.counts(hand), seat.melds.size) != -1) return null
        return RiichiScoring.score(winContext(player, tile, tsumo = false, hand = hand, chankan = isChankan))
    }

    /** 사람에게 묻고 있는 동안의 선택지. */
    fun claimOptions(): ClaimOptions =
        if (phase == RiichiPhase.Claim) optionsFor(HUMAN, claimTile, claimFrom, chankan) else ClaimOptions()

    private fun optionsFor(player: Int, tile: Int, from: Int, isChankan: Boolean): ClaimOptions {
        if (player == from || tile < 0) return ClaimOptions()
        val seat = seats[player]
        val ron = ronScore(player, tile, isChankan) != null
        if (isChankan || seat.riichi || remaining <= 0) return ClaimOptions(ron = ron)
        val kind = Mahjong.kind(tile)
        val same = seat.hand.filter { Mahjong.kind(it) == kind }
        val pon = if (same.size >= 2) same.take(2) else null
        val kan = if (same.size >= 3 && kans < 4) same.take(3) else null
        val chi = if (player == (from + 1) % 4 && kind < 27) {
            val n = kind % 9
            listOf(-2 to -1, -1 to 1, 1 to 2)
                .filter { (a, b) -> n + a >= 0 && n + b <= 8 }
                .mapNotNull { (a, b) ->
                    val first = seat.hand.firstOrNull { Mahjong.kind(it) == kind + a }
                    val second = seat.hand.firstOrNull { Mahjong.kind(it) == kind + b }
                    if (first != null && second != null) listOf(first, second) else null
                }
        } else {
            emptyList()
        }
        return ClaimOptions(ron, pon, kan, chi)
    }

    /** 사람이 고르지 않았으면 고를 것이 있을 때 묻고 멈춘다. 골랐으면 컴퓨터 선택대로 진행한다. */
    private fun resolveClaims(humanDecided: Boolean) {
        val order = (1..3).map { (claimFrom + it) % 4 }
        val human = if (humanDecided) ClaimOptions() else optionsFor(HUMAN, claimTile, claimFrom, chankan)
        val humanPosition = order.indexOf(HUMAN)
        val aiRonner = order.withIndex().firstOrNull { (_, player) ->
            player != HUMAN && ronScore(player, claimTile, chankan) != null
        }
        if (aiRonner != null && (!human.ron || aiRonner.index < humanPosition)) {
            ron(aiRonner.value)
            return
        }
        val aiCall = bestAiCall()
        val humanPriority = when {
            human.pon != null || human.kan != null -> 2
            human.chi.isNotEmpty() -> 1
            else -> 0
        }
        if (human.ron || (humanPriority > 0 && humanPriority >= (aiCall?.priority ?: 0))) {
            phase = RiichiPhase.Claim
            turn = claimFrom
            return
        }
        proceed(aiCall)
    }

    private fun bestAiCall(): Call? {
        if (chankan || remaining <= 0) return null
        return (1..3).map { (claimFrom + it) % 4 }.filter { it != HUMAN }
            .mapNotNull { aiCallFor(it) }
            .maxByOrNull { it.priority }
    }

    fun humanClaim(choice: ClaimChoice) {
        if (phase != RiichiPhase.Claim) return
        val options = optionsFor(HUMAN, claimTile, claimFrom, chankan)
        val call = when (choice) {
            ClaimChoice.Ron -> {
                if (options.ron) ron(HUMAN)
                return
            }
            ClaimChoice.Pon -> options.pon?.let { Call(HUMAN, choice, it) }
            ClaimChoice.Kan -> options.kan?.let { Call(HUMAN, choice, it) }
            is ClaimChoice.Chi -> choice.tiles.takeIf { it in options.chi }?.let { Call(HUMAN, choice, it) }
            ClaimChoice.Pass -> null
        }
        if (choice != ClaimChoice.Pass && call == null) return
        phase = RiichiPhase.Turn
        // 내가 울어도 뒤 사람의 론, 치보다 앞서는 퐁이 먼저다.
        val order = (1..3).map { (claimFrom + it) % 4 }
        val aiRonner = order.firstOrNull { it != HUMAN && ronScore(it, claimTile, chankan) != null }
        if (aiRonner != null) {
            ron(aiRonner)
            return
        }
        val aiCall = bestAiCall()
        proceed(if (call == null || (aiCall != null && aiCall.priority > call.priority)) aiCall else call)
    }

    /** 아무도 론하지 않았다. 놓친 사람은 후리텐이 되고, 울거나 다음 사람이 뽑는다. */
    private fun proceed(call: Call?) {
        val kind = Mahjong.kind(claimTile)
        for (player in 0 until 4) {
            if (player == claimFrom) continue
            val seat = seats[player]
            if (kind in seat.waits()) {
                seat.tempFuriten = true
                if (seat.riichi) seat.riichiFuriten = true
            }
        }
        if (pendingRiichi >= 0) {
            seats[pendingRiichi].points -= 1000
            sticks++
            pendingRiichi = -1
        }
        if (chankan) {
            chankan = false
            kans++
            draw(claimFrom, rinshan = true)
            return
        }
        if (call != null) {
            applyCall(call)
            return
        }
        if (remaining <= 0) {
            exhaustiveDraw()
            return
        }
        draw((claimFrom + 1) % 4)
    }

    private fun applyCall(call: Call) {
        val seat = seats[call.player]
        val from = claimFrom
        val tile = claimTile
        val discards = seats[from].discards
        discards[discards.lastIndex] = discards.last().copy(called = true)
        seat.hand.removeAll(call.tiles)
        untouched = false
        seats.forEach { it.ippatsu = false }
        val kind = Mahjong.kind(tile)
        turn = call.player
        drawn = -1
        rinshanDraw = false
        phase = RiichiPhase.Turn
        when (call.choice) {
            ClaimChoice.Pon -> {
                seat.melds += Meld(MeldType.Pon, call.tiles + tile, tile, from)
                forbidden = listOf(kind)
                message = "${seatName(call.player)} 퐁"
            }
            ClaimChoice.Kan -> {
                seat.melds += Meld(MeldType.OpenKan, call.tiles + tile, tile, from)
                kans++
                message = "${seatName(call.player)} 깡"
                draw(call.player, rinshan = true)
            }
            else -> {
                seat.melds += Meld(MeldType.Chi, (call.tiles + tile).sortedBy { it }, tile, from)
                val others = call.tiles.map(Mahjong::kind).sorted()
                // 양 끝을 기다리던 모양으로 울었으면 반대쪽 끝도 바로 버릴 수 없다.
                forbidden = when {
                    others[0] == kind + 1 && kind % 9 <= 5 -> listOf(kind, kind + 3)
                    others[1] == kind - 1 && kind % 9 >= 3 -> listOf(kind, kind - 3)
                    else -> listOf(kind)
                }
                message = "${seatName(call.player)} 치"
            }
        }
    }

    private fun ron(player: Int) {
        val score = ronScore(player, claimTile, chankan) ?: return
        val seat = seats[player]
        // 리치 선언패로 론을 당하면 리치는 없던 일이 된다.
        if (pendingRiichi >= 0) {
            seats[pendingRiichi].riichi = false
            pendingRiichi = -1
        }
        val points = score.ron(player == dealer) + 300 * honba
        val deltas = MutableList(4) { 0 }
        deltas[claimFrom] -= points
        deltas[player] += points + sticks * 1000
        if (chankan) {
            // 창깡당한 가깡은 퐁으로 되돌린다.
            val from = seats[claimFrom]
            val index = from.melds.indexOfFirst { it.type == MeldType.AddedKan && claimTile in it.tiles }
            if (index >= 0) {
                val meld = from.melds[index]
                from.melds[index] = Meld(MeldType.Pon, meld.tiles - claimTile, meld.called, meld.from)
            }
        }
        finishWin(player, claimFrom, seat.hand + claimTile, claimTile, score, deltas, "론", "${score.ron(player == dealer)}점")
    }

    private fun finishWin(
        winner: Int,
        loser: Int,
        hand: List<Int>,
        winTile: Int,
        score: WinScore,
        deltas: List<Int>,
        how: String,
        payText: String,
    ) {
        for (i in 0 until 4) seats[i].points += deltas[i]
        sticks = 0
        val fuText = if (score.yakuman > 0) "" else "${score.fu}부 ${score.han}판 "
        val label = score.label.takeIf { it.isNotEmpty() }?.let { "$it " } ?: ""
        result = RiichiHandResult(
            title = "${seatName(winner)} $how" + if (loser >= 0) " (${seatName(loser)} 방총)" else "",
            winner = winner,
            loser = loser,
            hand = hand.sortedBy { it },
            melds = seats[winner].melds.toList(),
            winTile = winTile,
            yaku = score.yaku,
            summary = "$fuText$label$payText",
            deltas = deltas,
            tenpai = List(4) { false },
            dora = doraIndicators,
            ura = if (seats[winner].riichi) uraIndicators else emptyList(),
            dealerKeeps = winner == dealer,
            draw = false,
        )
        phase = RiichiPhase.HandOver
    }

    private fun exhaustiveDraw() {
        val tenpai = seats.map { it.shanten() == 0 }
        val count = tenpai.count { it }
        val deltas = MutableList(4) { 0 }
        if (count in 1..3) {
            for (i in 0 until 4) deltas[i] = if (tenpai[i]) 3000 / count else -3000 / (4 - count)
        }
        for (i in 0 until 4) seats[i].points += deltas[i]
        result = RiichiHandResult(
            title = "유국",
            winner = -1,
            loser = -1,
            hand = emptyList(),
            melds = emptyList(),
            winTile = -1,
            yaku = emptyList(),
            summary = if (count == 0) "모두 노텐" else "텐파이: " + (0 until 4).filter { tenpai[it] }.joinToString { seatName(it) },
            deltas = deltas,
            tenpai = tenpai,
            dora = doraIndicators,
            ura = emptyList(),
            dealerKeeps = tenpai[dealer],
            draw = true,
        )
        phase = RiichiPhase.HandOver
    }

    /** 판 결과를 보고 다음 판으로 넘어가거나 대국을 끝낸다. */
    fun nextHand() {
        val last = result ?: return
        if (last.dealerKeeps) {
            honba++
        } else {
            round++
            honba = if (last.draw) honba + 1 else 0
        }
        if (seats.any { it.points < 0 } || round >= length) {
            phase = RiichiPhase.GameOver
            return
        }
        startHand()
    }

    /** 1등부터. 점수가 같으면 처음 친에 가까운 자리가 앞선다. */
    fun ranking(): List<Int> = (0 until 4).sortedWith(compareByDescending<Int> { seats[it].points }.thenBy { it })

    // ── 컴퓨터 ──

    /** 컴퓨터 차례 한 번. 쯔모·깡·버리기 중 하나를 한다. */
    fun aiTurn() {
        val player = turn
        if (phase != RiichiPhase.Turn || player == HUMAN) return
        val seat = seats[player]
        if (tsumoScore(player) != null) {
            tsumo(player)
            return
        }
        if (seat.riichi) {
            discard(player, drawn)
            return
        }
        val shanten = seat.shanten()
        val danger = seats.indices.any { it != player && seats[it].riichi }
        for (kind in kanOptions(player)) {
            val counts = seat.counts()
            val closedKan = counts[kind] == 4
            counts[kind] -= if (closedKan) 4 else 1
            val after = Mahjong.shanten(counts, seat.melds.size + if (closedKan) 1 else 0)
            if (after <= shanten && !danger) {
                kan(player, kind)
                return
            }
        }
        val tile = chooseDiscard(player)
        val riichi = tile in riichiDiscards(player)
        discard(player, tile, declareRiichi = riichi)
    }

    private fun visibleCounts(player: Int): IntArray {
        val counts = IntArray(Mahjong.KINDS)
        seats[player].hand.forEach { counts[Mahjong.kind(it)]++ }
        seats.forEach { seat ->
            seat.discards.forEach { counts[Mahjong.kind(it.tile)]++ }
            seat.melds.forEach { meld -> meld.tiles.forEach { counts[Mahjong.kind(it)]++ } }
        }
        doraIndicators.forEach { counts[Mahjong.kind(it)]++ }
        return counts
    }

    private fun chooseDiscard(player: Int): Int {
        val seat = seats[player]
        val counts = seat.counts()
        val melds = seat.melds.size
        val candidates = seat.hand.filter { canDiscard(player, it) }.distinctBy(Mahjong::kind)
        val shantenAfter = candidates.associateWith { tile ->
            val kind = Mahjong.kind(tile)
            counts[kind]--
            val value = Mahjong.shanten(counts, melds)
            counts[kind]++
            value
        }
        val best = shantenAfter.values.min()
        val riichiPlayers = seats.indices.filter { it != player && seats[it].riichi }
        if (riichiPlayers.isNotEmpty() && best >= 1) {
            // 이길 가망이 적으면 안전한 패를 버린다.
            val visible = visibleCounts(player)
            return candidates.maxWith(
                compareBy<Int> { tile -> safety(tile, riichiPlayers, visible) }
                    .thenBy { -(shantenAfter[it] ?: 9) },
            )
        }
        val visible = visibleCounts(player)
        val dora = doraIndicators.map { Mahjong.doraOf(Mahjong.kind(it)) }.toSet()
        return candidates.filter { shantenAfter[it] == best }.maxWith(
            compareBy<Int> { tile ->
                val kind = Mahjong.kind(tile)
                counts[kind]--
                val accept = (0 until Mahjong.KINDS).sumOf { k ->
                    if (visible[k] >= 4 || counts[k] >= 4) return@sumOf 0
                    counts[k]++
                    val better = Mahjong.shanten(counts, melds) < best
                    counts[k]--
                    if (better) 4 - visible[k] else 0
                }
                counts[kind]++
                accept
            }.thenBy { tile -> -keepValue(player, tile, dora) },
        )
    }

    /** 높을수록 남기고 싶은 패. 도라·역패·가운데 숫자를 아낀다. */
    private fun keepValue(player: Int, tile: Int, dora: Set<Int>): Int {
        val kind = Mahjong.kind(tile)
        var value = 0
        if (kind in dora) value += 3
        if (Mahjong.isRed(tile)) value += 3
        if (Mahjong.isDragon(kind) || kind == Mahjong.EAST + seatWind(player) || kind == Mahjong.EAST + roundWind) value += 1
        if (kind < 27) value += minOf(kind % 9, 8 - kind % 9)
        return value
    }

    private fun safety(tile: Int, riichiPlayers: List<Int>, visible: IntArray): Int {
        val kind = Mahjong.kind(tile)
        val safeAgainstAll = riichiPlayers.all { r -> seats[r].discards.any { Mahjong.kind(it.tile) == kind } }
        return when {
            safeAgainstAll -> 100
            Mahjong.isHonor(kind) && visible[kind] >= 3 -> 90
            Mahjong.isHonor(kind) && visible[kind] == 2 -> 70
            Mahjong.isTerminal(kind) -> 40
            Mahjong.isHonor(kind) -> 30
            else -> 10 - minOf(kind % 9, 8 - kind % 9) * 2
        }
    }

    private fun aiCallFor(player: Int): Call? {
        val options = optionsFor(player, claimTile, claimFrom, false)
        if (!options.anyCall) return null
        val seat = seats[player]
        val kind = Mahjong.kind(claimTile)
        val valueKind = Mahjong.isDragon(kind) || kind == Mahjong.EAST + seatWind(player) || kind == Mahjong.EAST + roundWind
        if (valueKind && options.pon != null) return Call(player, ClaimChoice.Pon, options.pon)

        val hasValueMeld = seat.melds.any { meld ->
            meld.type != MeldType.Chi && (Mahjong.isDragon(meld.kind) ||
                meld.kind == Mahjong.EAST + seatWind(player) || meld.kind == Mahjong.EAST + roundWind)
        }
        val current = seat.shanten()
        fun improves(used: List<Int>): Boolean {
            val counts = seat.counts()
            used.forEach { counts[Mahjong.kind(it)]-- }
            val after = (0 until Mahjong.KINDS).filter { counts[it] > 0 }.minOfOrNull { k ->
                counts[k]--
                val value = Mahjong.shanten(counts, seat.melds.size + 1)
                counts[k]++
                value
            } ?: return false
            return after < current
        }
        fun tanyaoPossible(used: List<Int>): Boolean {
            if (Mahjong.isYaochu(kind)) return false
            val rest = seat.hand - used.toSet()
            return seat.melds.all { m -> m.tiles.none { Mahjong.isYaochu(Mahjong.kind(it)) } } &&
                rest.count { Mahjong.isYaochu(Mahjong.kind(it)) } <= 1
        }
        options.pon?.let { if ((hasValueMeld || tanyaoPossible(it)) && improves(it)) return Call(player, ClaimChoice.Pon, it) }
        for (chi in options.chi) {
            if ((hasValueMeld || tanyaoPossible(chi)) && improves(chi)) return Call(player, ClaimChoice.Chi(chi), chi)
        }
        return null
    }

    // ── 저장 ──

    fun toJson(): JSONObject = JSONObject()
        .put("length", length)
        .put("round", round)
        .put("honba", honba)
        .put("sticks", sticks)
        .put("wall", JSONArray(wall))
        .put("next", next)
        .put("kans", kans)
        .put("turn", turn)
        .put("phase", phase.name)
        .put("drawn", drawn)
        .put("rinshanDraw", rinshanDraw)
        .put("forbidden", JSONArray(forbidden))
        .put("untouched", untouched)
        .put("pendingRiichi", pendingRiichi)
        .put("claimTile", claimTile)
        .put("claimFrom", claimFrom)
        .put("chankan", chankan)
        .put("message", message)
        .put("seats", JSONArray(seats.map { seat ->
            JSONObject()
                .put("points", seat.points)
                .put("hand", JSONArray(seat.hand))
                .put("melds", JSONArray(seat.melds.map(::meldJson)))
                .put("discards", JSONArray(seat.discards.map {
                    JSONObject().put("tile", it.tile).put("called", it.called).put("riichi", it.riichi)
                }))
                .put("riichi", seat.riichi)
                .put("doubleRiichi", seat.doubleRiichi)
                .put("ippatsu", seat.ippatsu)
                .put("tempFuriten", seat.tempFuriten)
                .put("riichiFuriten", seat.riichiFuriten)
        }))
        .put("result", result?.let { r ->
            JSONObject()
                .put("title", r.title)
                .put("winner", r.winner)
                .put("loser", r.loser)
                .put("hand", JSONArray(r.hand))
                .put("melds", JSONArray(r.melds.map(::meldJson)))
                .put("winTile", r.winTile)
                .put("yaku", JSONArray(r.yaku.map { JSONObject().put("name", it.name).put("han", it.han) }))
                .put("summary", r.summary)
                .put("deltas", JSONArray(r.deltas))
                .put("tenpai", JSONArray(r.tenpai))
                .put("dora", JSONArray(r.dora))
                .put("ura", JSONArray(r.ura))
                .put("dealerKeeps", r.dealerKeeps)
                .put("draw", r.draw)
        })

    companion object {
        const val HUMAN = 0
        const val START_POINTS = 25000
        private const val DEAD_WALL_START = 122
        private val SEAT_NAMES = listOf("나", "하가", "대면", "상가")

        fun seatName(player: Int) = SEAT_NAMES[player]

        fun newGame(length: Int) = RiichiTable(length).also { it.startHand() }

        private fun meldJson(meld: Meld) = JSONObject()
            .put("type", meld.type.name)
            .put("tiles", JSONArray(meld.tiles))
            .put("called", meld.called)
            .put("from", meld.from)

        private fun JSONArray.ints() = List(length()) { getInt(it) }

        private fun parseMeld(item: JSONObject) = Meld(
            MeldType.valueOf(item.getString("type")),
            item.getJSONArray("tiles").ints(),
            item.getInt("called"),
            item.getInt("from"),
        )

        fun fromJson(item: JSONObject): RiichiTable = RiichiTable(item.getInt("length")).apply {
            round = item.getInt("round")
            honba = item.getInt("honba")
            sticks = item.getInt("sticks")
            wall = item.getJSONArray("wall").ints()
            next = item.getInt("next")
            kans = item.getInt("kans")
            turn = item.getInt("turn")
            phase = RiichiPhase.valueOf(item.getString("phase"))
            drawn = item.getInt("drawn")
            rinshanDraw = item.getBoolean("rinshanDraw")
            forbidden = item.getJSONArray("forbidden").ints()
            untouched = item.getBoolean("untouched")
            pendingRiichi = item.getInt("pendingRiichi")
            claimTile = item.getInt("claimTile")
            claimFrom = item.getInt("claimFrom")
            chankan = item.getBoolean("chankan")
            message = item.optString("message")
            val seatItems = item.getJSONArray("seats")
            seats.forEachIndexed { index, seat ->
                val s = seatItems.getJSONObject(index)
                seat.points = s.getInt("points")
                seat.hand += s.getJSONArray("hand").ints()
                val melds = s.getJSONArray("melds")
                for (i in 0 until melds.length()) seat.melds += parseMeld(melds.getJSONObject(i))
                val discards = s.getJSONArray("discards")
                for (i in 0 until discards.length()) {
                    val d = discards.getJSONObject(i)
                    seat.discards += RiichiDiscard(d.getInt("tile"), d.getBoolean("called"), d.getBoolean("riichi"))
                }
                seat.riichi = s.getBoolean("riichi")
                seat.doubleRiichi = s.getBoolean("doubleRiichi")
                seat.ippatsu = s.getBoolean("ippatsu")
                seat.tempFuriten = s.getBoolean("tempFuriten")
                seat.riichiFuriten = s.getBoolean("riichiFuriten")
            }
            result = item.optJSONObject("result")?.let { r ->
                val yaku = r.getJSONArray("yaku")
                val melds = r.getJSONArray("melds")
                val tenpai = r.getJSONArray("tenpai")
                RiichiHandResult(
                    title = r.getString("title"),
                    winner = r.getInt("winner"),
                    loser = r.getInt("loser"),
                    hand = r.getJSONArray("hand").ints(),
                    melds = List(melds.length()) { parseMeld(melds.getJSONObject(it)) },
                    winTile = r.getInt("winTile"),
                    yaku = List(yaku.length()) { Yaku(yaku.getJSONObject(it).getString("name"), yaku.getJSONObject(it).getInt("han")) },
                    summary = r.getString("summary"),
                    deltas = r.getJSONArray("deltas").ints(),
                    tenpai = List(tenpai.length()) { tenpai.getBoolean(it) },
                    dora = r.getJSONArray("dora").ints(),
                    ura = r.getJSONArray("ura").ints(),
                    dealerKeeps = r.getBoolean("dealerKeeps"),
                    draw = r.getBoolean("draw"),
                )
            }
        }
    }
}
