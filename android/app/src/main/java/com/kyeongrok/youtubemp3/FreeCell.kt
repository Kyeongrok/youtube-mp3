package com.kyeongrok.youtubemp3

/** 무늬는 0 스페이드, 1 하트, 2 다이아, 3 클럽이고 숫자는 1(A)..13(K)이다. */
data class PlayingCard(val suit: Int, val rank: Int) {
    val isRed get() = suit == 1 || suit == 2

    fun canStackOn(other: PlayingCard) = isRed != other.isRed && rank == other.rank - 1
}

sealed interface FreeCellPick {
    data class Cascade(val column: Int, val row: Int) : FreeCellPick
    data class Cell(val index: Int) : FreeCellPick
}

data class FreeCellGame(
    val cascades: List<List<PlayingCard>>,
    val cells: List<PlayingCard?> = List(CELLS) { null },
    val foundations: List<Int> = List(SUITS) { 0 },
    val picked: FreeCellPick? = null,
    val previous: FreeCellGame? = null,
) {
    val isComplete = foundations.all { it == RANKS }
    val hasMoves = isComplete || canMoveSomething()

    fun undo() = previous ?: this

    fun tapCascade(column: Int, row: Int?): FreeCellGame {
        val pick = row?.let { FreeCellPick.Cascade(column, it) }
        if (picked != null) {
            if (picked == pick) return copy(picked = null)
            moveToCascade(picked, column)?.let { return it }
        }
        return copy(picked = pick?.takeIf { isRun(column, it.row) })
    }

    fun tapCell(index: Int): FreeCellGame {
        val pick = FreeCellPick.Cell(index)
        if (picked == pick) return copy(picked = null)
        if (cells[index] != null) return copy(picked = pick)
        val cards = picked?.let(::cardsOf) ?: return this
        if (cards.size > 1) return copy(picked = null)
        return moved(picked, cards) { _, cells -> cells[index] = cards[0] }
    }

    fun tapFoundation(): FreeCellGame {
        val pick = picked ?: return this
        val card = cardsOf(pick).singleOrNull()
        if (card == null || foundations[card.suit] != card.rank - 1) return copy(picked = null)
        return moved(pick, listOf(card), toFoundation = true) { _, _ -> }
    }

    private fun cardsOf(pick: FreeCellPick) = when (pick) {
        is FreeCellPick.Cascade -> cascades[pick.column].drop(pick.row)
        is FreeCellPick.Cell -> listOfNotNull(cells[pick.index])
    }

    /** 고른 자리부터 끝까지 색이 번갈아 내려가는 줄이어야 함께 옮길 수 있다. */
    private fun isRun(column: Int, row: Int) =
        cascades[column].drop(row).zipWithNext().all { (upper, lower) -> lower.canStackOn(upper) }

    private fun moveToCascade(pick: FreeCellPick, column: Int): FreeCellGame? {
        if (pick is FreeCellPick.Cascade && pick.column == column) return null
        val cards = cardsOf(pick)
        val top = cascades[column].lastOrNull()
        if (top != null && !cards[0].canStackOn(top)) return null
        // 여러 장은 빈 칸을 거쳐 옮기는 것과 같아서 빈 자리 수만큼만 움직일 수 있다.
        val emptyCascades = cascades.indices.count { it != column && cascades[it].isEmpty() }
        val limit = (cells.count { it == null } + 1) shl emptyCascades
        if (cards.size > limit) return null
        return moved(pick, cards) { cascades, _ -> cascades[column] = cascades[column] + cards }
    }

    private fun moved(
        pick: FreeCellPick,
        cards: List<PlayingCard>,
        toFoundation: Boolean = false,
        place: (MutableList<List<PlayingCard>>, MutableList<PlayingCard?>) -> Unit,
    ): FreeCellGame {
        val nextCascades = cascades.toMutableList()
        val nextCells = cells.toMutableList()
        when (pick) {
            is FreeCellPick.Cascade -> nextCascades[pick.column] = cascades[pick.column].dropLast(cards.size)
            is FreeCellPick.Cell -> nextCells[pick.index] = null
        }
        place(nextCascades, nextCells)
        val nextFoundations = foundations.toMutableList()
        if (toFoundation) nextFoundations[cards[0].suit] = cards[0].rank
        return copy(
            cascades = nextCascades,
            cells = nextCells,
            foundations = nextFoundations,
            picked = null,
            previous = copy(picked = null),
        ).settled()
    }

    /** 더 쓸 일이 없는 카드만 홈 칸으로 올린다. 필요할 수 있는 카드는 그대로 둔다. */
    private fun settled(): FreeCellGame {
        for (column in cascades.indices) {
            val card = cascades[column].lastOrNull() ?: continue
            if (isSafeToLift(card)) {
                val next = cascades.toMutableList().also { it[column] = cascades[column].dropLast(1) }
                return copy(cascades = next, foundations = lifted(card)).settled()
            }
        }
        for (index in cells.indices) {
            val card = cells[index] ?: continue
            if (isSafeToLift(card)) {
                val next = cells.toMutableList().also { it[index] = null }
                return copy(cells = next, foundations = lifted(card)).settled()
            }
        }
        return this
    }

    private fun isSafeToLift(card: PlayingCard): Boolean {
        if (foundations[card.suit] != card.rank - 1) return false
        val opposite = foundations.filterIndexed { suit, _ -> PlayingCard(suit, 1).isRed != card.isRed }
        return card.rank <= 2 || card.rank - 1 <= opposite.min()
    }

    private fun lifted(card: PlayingCard) = foundations.toMutableList().also { it[card.suit] = card.rank }

    private fun canMoveSomething(): Boolean {
        val tails = cascades.mapNotNull { it.lastOrNull() }
        val movable = tails + cells.filterNotNull()
        return movable.any { card ->
            foundations[card.suit] == card.rank - 1 ||
                null in cells ||
                cascades.any { it.isEmpty() } ||
                tails.any { card.canStackOn(it) }
        }
    }

    companion object {
        const val COLUMNS = 8
        const val CELLS = 4
        const val SUITS = 4
        const val RANKS = 13

        fun deal(): FreeCellGame {
            val deck = (0 until SUITS).flatMap { suit -> (1..RANKS).map { PlayingCard(suit, it) } }.shuffled()
            val cascades = List(COLUMNS) { column -> deck.filterIndexed { index, _ -> index % COLUMNS == column } }
            return FreeCellGame(cascades)
        }
    }
}
