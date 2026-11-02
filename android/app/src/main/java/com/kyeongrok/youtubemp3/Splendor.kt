package com.kyeongrok.youtubemp3

import kotlin.random.Random

/** 보석 색 0~4는 흰·파랑·초록·빨강·검정, 5는 황금(만능)이다. */
data class SplendorCard(val id: Int, val tier: Int, val bonus: Int, val points: Int, val cost: List<Int>)

data class SplendorNoble(val id: Int, val need: List<Int>)

data class SplendorPlayer(
    val tokens: List<Int> = List(Splendor.TOKEN_KINDS) { 0 },
    val cards: List<Int> = emptyList(),
    val reserved: List<Int> = emptyList(),
    val nobles: List<Int> = emptyList(),
) {
    val bonuses get() = List(Splendor.COLORS) { color -> cards.count { Splendor.card(it).bonus == color } }
    val points get() = cards.sumOf { Splendor.card(it).points } + nobles.size * Splendor.NOBLE_POINTS
    val tokenCount get() = tokens.sum()

    /** 색마다 내야 할 토큰 수와 모자라서 황금으로 채울 수다. 살 수 없으면 null이다. */
    fun payment(card: SplendorCard): List<Int>? {
        val bonus = bonuses
        val pay = MutableList(Splendor.TOKEN_KINDS) { 0 }
        for (color in 0 until Splendor.COLORS) {
            val due = (card.cost[color] - bonus[color]).coerceAtLeast(0)
            pay[color] = minOf(due, tokens[color])
            pay[Splendor.GOLD] += due - pay[color]
        }
        return pay.takeIf { it[Splendor.GOLD] <= tokens[Splendor.GOLD] }
    }

    /** 카드를 사려면 더 모아야 하는 토큰 수다. 황금은 아무 색이나 채운다. */
    fun shortfall(card: SplendorCard): Int {
        val bonus = bonuses
        val missing = (0 until Splendor.COLORS).sumOf { color ->
            (card.cost[color] - bonus[color] - tokens[color]).coerceAtLeast(0)
        }
        return (missing - tokens[Splendor.GOLD]).coerceAtLeast(0)
    }
}

data class SplendorGame(
    val players: List<SplendorPlayer>,
    val bank: List<Int>,
    /** 단계별로 뒤집혀 쌓인 카드다. 0이 1단계다. */
    val decks: List<List<Int>>,
    /** 단계별로 펼쳐 놓은 카드 4칸이다. 더미가 떨어진 칸은 null이다. */
    val market: List<List<Int?>>,
    val nobles: List<Int>,
    val turn: Int = Splendor.HUMAN,
    val winner: Int? = null,
    val seconds: Int = 0,
    val notice: String = "",
    /** 이번 차례에 가져오려고 고른 보석 색이다. */
    val picked: List<Int> = emptyList(),
    /** 지난 내 차례 뒤로 컴퓨터가 한 일이다. */
    val log: List<String> = emptyList(),
    /** 방금 바뀐 카드 칸이다. 화면에서 강조한다. */
    val recent: Set<Int> = emptySet(),
) {
    val me get() = players[Splendor.HUMAN]
    val cpus get() = players.size - 1
    val isFinished get() = winner != null
    val isHumanTurn get() = turn == Splendor.HUMAN && !isFinished
    val mustDiscard get() = isHumanTurn && me.tokenCount > Splendor.TOKEN_LIMIT

    /** 고른 보석을 가져갈 수 있는지와, 못 가져가면 그 까닭이다. */
    val pickProblem: String?
        get() = when {
            picked.isEmpty() -> "가져올 보석을 고르세요."
            picked.size == 2 && picked[0] == picked[1] -> null
            picked.size == 3 -> null
            // 남은 색이 모자라면 다른 색 1~2개만 가져갈 수 있다.
            picked.distinct().size == picked.size &&
                (0 until Splendor.COLORS).count { bank[it] > 0 } == picked.size -> null
            else -> "서로 다른 색 3개, 또는 4개 이상 남은 색 2개를 고르세요."
        }

    fun tapGem(color: Int): SplendorGame {
        if (!isHumanTurn || mustDiscard) return this
        if (color == Splendor.GOLD) return copy(notice = "황금은 카드를 예약할 때 받습니다.")
        val same = picked.count { it == color }
        return when {
            same == 2 -> copy(picked = picked.filter { it != color }, notice = "")
            same == 1 && picked.size == 1 ->
                if (bank[color] >= Splendor.DOUBLE_MIN) {
                    copy(picked = listOf(color, color), notice = "")
                } else {
                    copy(picked = emptyList(), notice = "같은 색 2개는 4개 이상 남았을 때만 가져올 수 있습니다.")
                }
            same == 1 -> copy(picked = picked - color, notice = "")
            bank[color] == 0 -> copy(notice = "남은 보석이 없습니다.")
            picked.size >= 3 || (picked.size == 2 && picked[0] == picked[1]) -> copy(notice = "더 고를 수 없습니다.")
            else -> copy(picked = picked + color, notice = "")
        }
    }

    fun takePicked(): SplendorGame {
        if (!isHumanTurn || pickProblem != null) return this
        return take(picked).copy(picked = emptyList()).afterHumanAction()
    }

    fun buy(cardId: Int): SplendorGame {
        if (!isHumanTurn) return this
        return buyFor(turn, cardId)?.copy(picked = emptyList())?.afterHumanAction()
            ?: copy(notice = "보석이 모자랍니다.")
    }

    fun reserve(cardId: Int?, tier: Int): SplendorGame {
        if (!isHumanTurn) return this
        return reserveFor(turn, cardId, tier)?.copy(picked = emptyList())?.afterHumanAction()
            ?: copy(notice = if (me.reserved.size >= Splendor.RESERVE_LIMIT) "예약은 3장까지입니다." else "더미가 비었습니다.")
    }

    fun discard(color: Int): SplendorGame {
        if (!mustDiscard || me.tokens[color] == 0) return this
        val next = changeTokens(turn, color, -1)
        return if (next.mustDiscard) next else next.endTurn()
    }

    fun clearPicked() = copy(picked = emptyList(), notice = "")

    private fun afterHumanAction() = if (mustDiscard) copy(notice = "") else endTurn()

    /** 펼친 카드가 놓인 단계와 칸이다. 펼쳐져 있지 않으면 null이다. */
    fun slotOf(cardId: Int): Pair<Int, Int>? {
        market.forEachIndexed { tier, row ->
            val index = row.indexOf(cardId)
            if (index >= 0) return tier to index
        }
        return null
    }

    private fun changeTokens(player: Int, color: Int, amount: Int): SplendorGame {
        val owner = players[player]
        return copy(
            players = players.replace(player, owner.copy(tokens = owner.tokens.add(color, amount))),
            bank = bank.add(color, -amount),
        )
    }

    private fun take(colors: List<Int>) = colors.fold(this) { game, color -> game.changeTokens(turn, color, 1) }

    /** 펼친 카드를 사면 그 칸을 더미에서 채운다. */
    private fun removeFromMarket(cardId: Int): SplendorGame {
        val (tier, index) = slotOf(cardId) ?: return this
        val deck = decks[tier]
        val row = market[tier].toMutableList().apply { this[index] = deck.firstOrNull() }
        return copy(
            market = market.replace(tier, row),
            decks = decks.replace(tier, deck.drop(1)),
            recent = deck.firstOrNull()?.let { recent + it } ?: recent,
        )
    }

    internal fun buyFor(player: Int, cardId: Int): SplendorGame? {
        val owner = players[player]
        if (cardId !in owner.reserved && slotOf(cardId) == null) return null
        val pay = owner.payment(Splendor.card(cardId)) ?: return null
        val paid = copy(
            players = players.replace(
                player,
                owner.copy(
                    tokens = owner.tokens.zip(pay) { have, spend -> have - spend },
                    cards = owner.cards + cardId,
                    reserved = owner.reserved - cardId,
                ),
            ),
            bank = bank.zip(pay) { left, back -> left + back },
        )
        return if (cardId in owner.reserved) paid else paid.removeFromMarket(cardId)
    }

    /** cardId가 null이면 그 단계 더미 맨 위 카드를 예약한다. */
    internal fun reserveFor(player: Int, cardId: Int?, tier: Int): SplendorGame? {
        val owner = players[player]
        if (owner.reserved.size >= Splendor.RESERVE_LIMIT) return null
        val target = cardId ?: decks[tier].firstOrNull() ?: return null
        val taken = if (cardId == null) copy(decks = decks.replace(tier, decks[tier].drop(1))) else removeFromMarket(cardId)
        val reserved = taken.copy(
            players = taken.players.replace(player, taken.players[player].copy(reserved = owner.reserved + target)),
        )
        return if (bank[Splendor.GOLD] > 0) reserved.changeTokens(player, Splendor.GOLD, 1) else reserved
    }

    /**
     * 귀족을 맞이하고 다음 사람에게 넘긴다. 누가 15점에 닿으면 그 바퀴를 마저 돌고 끝낸다.
     * 귀족 여럿이 조건을 채워도 한 차례에 한 명만 온다.
     */
    internal fun endTurn(): SplendorGame {
        var game = this
        val owner = players[turn]
        val bonus = owner.bonuses
        nobles.firstOrNull { id -> Splendor.noble(id).need.indices.all { bonus[it] >= Splendor.noble(id).need[it] } }
            ?.let { id ->
                game = game.copy(
                    players = game.players.replace(turn, owner.copy(nobles = owner.nobles + id)),
                    nobles = nobles - id,
                )
            }
        val next = (turn + 1) % players.size
        if (next == Splendor.HUMAN && game.players.any { it.points >= Splendor.WIN_POINTS }) {
            val best = game.players.indices.maxWith(
                compareBy<Int> { game.players[it].points }.thenByDescending { game.players[it].cards.size },
            )
            return game.copy(winner = best, notice = "")
        }
        return game.copy(turn = next, notice = "", log = if (turn == Splendor.HUMAN) emptyList() else game.log)
            .let { if (turn == Splendor.HUMAN) it.copy(recent = emptySet()) else it }
    }

    /** 컴퓨터 한 사람의 차례를 둔다. */
    fun cpuTurn(): SplendorGame = SplendorCpu.play(this)
}

object Splendor {
    const val HUMAN = 0
    const val COLORS = 5
    const val GOLD = 5
    const val TOKEN_KINDS = 6
    const val TOKEN_LIMIT = 10
    const val RESERVE_LIMIT = 3
    const val DOUBLE_MIN = 4
    const val WIN_POINTS = 15
    const val NOBLE_POINTS = 3
    const val GOLD_TOKENS = 5
    const val MARKET_SIZE = 4
    const val TIERS = 3

    val COLOR_NAMES = listOf("흰색", "파랑", "초록", "빨강", "검정", "황금")

    /**
     * 원작과 같은 분포로 만든 카드다. 단계마다 색별로 같은 비용 꼴을 쓰고 색만 돌린다.
     * 꼴은 (점수, 자기 색으로부터 떨어진 색 → 개수)이다.
     */
    private val PATTERNS = listOf(
        listOf(
            0 to mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1),
            0 to mapOf(1 to 1, 2 to 2, 3 to 1, 4 to 1),
            0 to mapOf(1 to 2, 2 to 2, 4 to 1),
            0 to mapOf(0 to 1, 2 to 3, 3 to 1),
            0 to mapOf(1 to 2, 3 to 1),
            0 to mapOf(1 to 2, 3 to 2),
            0 to mapOf(2 to 3),
            1 to mapOf(3 to 4),
        ),
        listOf(
            1 to mapOf(1 to 2, 2 to 2, 3 to 3),
            1 to mapOf(0 to 2, 1 to 3, 3 to 3),
            2 to mapOf(1 to 1, 2 to 4, 3 to 2),
            2 to mapOf(2 to 5),
            2 to mapOf(3 to 5, 4 to 3),
            3 to mapOf(0 to 6),
        ),
        listOf(
            3 to mapOf(1 to 3, 2 to 3, 3 to 5, 4 to 3),
            4 to mapOf(4 to 7),
            4 to mapOf(0 to 3, 3 to 3, 4 to 6),
            5 to mapOf(0 to 3, 4 to 7),
        ),
    )

    val CARDS: List<SplendorCard> = buildList {
        PATTERNS.forEachIndexed { tier, patterns ->
            for (bonus in 0 until COLORS) {
                patterns.forEach { (points, shape) ->
                    val cost = MutableList(COLORS) { 0 }
                    shape.forEach { (offset, count) -> cost[(bonus + offset) % COLORS] = count }
                    add(SplendorCard(size, tier, bonus, points, cost))
                }
            }
        }
    }

    /** 이웃한 두 색 4개씩인 귀족 5명과 이웃한 세 색 3개씩인 귀족 5명이다. */
    val NOBLES: List<SplendorNoble> = buildList {
        for (start in 0 until COLORS) {
            add(SplendorNoble(size, List(COLORS) { if (it == start || it == (start + 1) % COLORS) 4 else 0 }))
        }
        for (start in 0 until COLORS) {
            add(SplendorNoble(size, List(COLORS) { if ((it - start + COLORS) % COLORS < 3) 3 else 0 }))
        }
    }

    fun card(id: Int) = CARDS[id]

    fun noble(id: Int) = NOBLES[id]

    /** 사람 수에 따라 색마다 4·5·7개를 놓는다. */
    fun tokensFor(players: Int) = when (players) {
        2 -> 4
        3 -> 5
        else -> 7
    }

    fun deal(cpus: Int, random: Random = Random.Default): SplendorGame {
        val players = cpus + 1
        val decks = List(TIERS) { tier -> CARDS.filter { it.tier == tier }.map { it.id }.shuffled(random) }
        return SplendorGame(
            players = List(players) { SplendorPlayer() },
            bank = List(COLORS) { tokensFor(players) } + GOLD_TOKENS,
            decks = decks.map { it.drop(MARKET_SIZE) },
            market = decks.map { it.take(MARKET_SIZE) },
            nobles = NOBLES.map { it.id }.shuffled(random).take(players + 1),
        )
    }
}

/**
 * 컴퓨터는 살 수 있는 카드 중 가장 값진 것을 사고, 못 사면 가장 빨리 살 수 있는 목표 카드에
 * 필요한 보석을 모은다. 보석을 가져올 수 없으면 목표 카드를 예약한다.
 */
object SplendorCpu {
    fun play(game: SplendorGame): SplendorGame {
        val player = game.turn
        val me = game.players[player]
        val choices = game.market.flatten().filterNotNull() + me.reserved

        val buys = choices.filter { me.payment(Splendor.card(it)) != null }
        buys.maxByOrNull { value(game, player, it) }?.let { id ->
            game.buyFor(player, id)?.let { return finish(it, player, describeBuy(player, id)) }
        }

        val target = choices.minByOrNull { id ->
            val card = Splendor.card(id)
            me.shortfall(card) * 3 - card.points * 2 - nobleHelp(game, me, card.bonus)
        }
        val colors = target?.let { pickColors(game, me, Splendor.card(it)) } ?: pickColors(game, me, null)
        if (colors.isNotEmpty()) {
            val taken = colors.fold(game) { next, color ->
                next.copy(
                    players = next.players.replace(player, next.players[player].let { it.copy(tokens = it.tokens.add(color, 1)) }),
                    bank = next.bank.add(color, -1),
                )
            }
            val names = if (colors.size == 2 && colors[0] == colors[1]) {
                "${Splendor.COLOR_NAMES[colors[0]]} 2개"
            } else {
                colors.joinToString("·") { Splendor.COLOR_NAMES[it] }
            }
            return finish(taken, player, "CPU $player: $names 보석을 가져갔습니다.")
        }

        target?.let { id ->
            val tier = Splendor.card(id).tier
            val source = if (id in me.reserved) null else id
            if (source != null) {
                game.reserveFor(player, source, tier)?.let { return finish(it, player, "CPU $player: ${tier + 1}단계 카드를 예약했습니다.") }
            }
        }
        return finish(game, player, "CPU $player: 할 수 있는 일이 없어 넘겼습니다.")
    }

    private fun finish(game: SplendorGame, player: Int, message: String): SplendorGame {
        var next = game
        // 10개를 넘으면 덜 쓰일 색부터 돌려준다.
        while (next.players[player].tokenCount > Splendor.TOKEN_LIMIT) {
            val tokens = next.players[player].tokens
            val need = demand(next, next.players[player])
            val color = (0 until Splendor.TOKEN_KINDS).filter { tokens[it] > 0 }.minBy { if (it == Splendor.GOLD) Int.MAX_VALUE else need[it] }
            next = next.copy(
                players = next.players.replace(player, next.players[player].copy(tokens = tokens.add(color, -1))),
                bank = next.bank.add(color, 1),
            )
        }
        return next.copy(log = next.log + message).endTurn()
    }

    private fun value(game: SplendorGame, player: Int, id: Int): Int {
        val card = Splendor.card(id)
        val spent = game.players[player].payment(card)!!.sum()
        return card.points * 10 + nobleHelp(game, game.players[player], card.bonus) * 2 + demand(game, game.players[player])[card.bonus] - spent
    }

    /** 이 색 보너스가 남은 귀족에게 얼마나 도움이 되는지다. */
    private fun nobleHelp(game: SplendorGame, me: SplendorPlayer, color: Int): Int {
        val bonus = me.bonuses
        return game.nobles.count { Splendor.noble(it).need[color] > bonus[color] }
    }

    /** 펼친 카드들을 사는 데 색마다 얼마나 모자란지 더한 값이다. */
    private fun demand(game: SplendorGame, me: SplendorPlayer): List<Int> {
        val bonus = me.bonuses
        val cards = game.market.flatten().filterNotNull() + me.reserved
        return List(Splendor.COLORS) { color ->
            cards.sumOf { (Splendor.card(it).cost[color] - bonus[color]).coerceAtLeast(0) }
        }
    }

    private fun pickColors(game: SplendorGame, me: SplendorPlayer, target: SplendorCard?): List<Int> {
        val bonus = me.bonuses
        val need = List(Splendor.COLORS) { color ->
            target?.let { (it.cost[color] - bonus[color] - me.tokens[color]).coerceAtLeast(0) } ?: 0
        }
        val demand = demand(game, me)
        val available = (0 until Splendor.COLORS).filter { game.bank[it] > 0 }

        // 한 색만 2개 이상 모자라면 같은 색 2개를 가져온다.
        val wanted = available.filter { need[it] > 0 }
        if (wanted.size == 1 && need[wanted[0]] >= 2 && game.bank[wanted[0]] >= Splendor.DOUBLE_MIN) {
            return listOf(wanted[0], wanted[0])
        }
        // 10개를 다 채웠으면 목표에 필요한 색만 가져온다. 넘친 보석은 차례 끝에 덜 쓰일 색부터 돌려준다.
        if (me.tokenCount >= Splendor.TOKEN_LIMIT) return wanted.take(3)
        return available
            .sortedWith(compareByDescending<Int> { need[it] }.thenByDescending { demand[it] })
            .take(3)
    }

    private fun describeBuy(player: Int, id: Int): String {
        val card = Splendor.card(id)
        val points = if (card.points > 0) " (+${card.points}점)" else ""
        return "CPU $player: ${card.tier + 1}단계 ${Splendor.COLOR_NAMES[card.bonus]} 카드를 샀습니다$points."
    }
}

private fun <T> List<T>.replace(index: Int, value: T) = toMutableList().apply { this[index] = value }

private fun List<Int>.add(index: Int, amount: Int) = toMutableList().apply { this[index] += amount }
