package com.kyeongrok.youtubemp3

import kotlin.random.Random

/** 타일 번호 0~103은 색 4가지 × 숫자 13개 × 2벌, 104와 105는 조커다. */
data class RummikubTile(val id: Int) {
    val isJoker get() = id >= JOKER_START
    val color get() = id / 2 / Rummikub.MAX_NUMBER
    val number get() = id / 2 % Rummikub.MAX_NUMBER + 1
    val points get() = if (isJoker) Rummikub.JOKER_POINTS else number

    /** 같은 타일 두 벌과 조커 두 장을 같은 값으로 묶는다. */
    val face get() = if (isJoker) JOKER_FACE else id / 2

    companion object {
        const val JOKER_START = 104
        const val JOKER_FACE = -1
    }
}

data class RummikubGame(
    val racks: List<List<RummikubTile>>,
    val table: List<List<RummikubTile>>,
    val pool: List<RummikubTile>,
    val opened: List<Boolean>,
    val turn: Int = Rummikub.HUMAN,
    val passes: Int = 0,
    val winner: Int? = null,
    val seconds: Int = 0,
    val notice: String = "",
    val picked: Set<RummikubTile> = emptySet(),
    /** 내 차례가 끝난 뒤 컴퓨터가 내려놓은 타일이다. */
    val recent: Set<RummikubTile> = emptySet(),
    val turnRack: List<RummikubTile> = racks[Rummikub.HUMAN],
    val turnTable: List<List<RummikubTile>> = table,
) {
    val rack get() = racks[Rummikub.HUMAN]
    val cpus get() = racks.size - 1
    val isFinished get() = winner != null
    val isHumanTurn get() = turn == Rummikub.HUMAN && !isFinished
    val isUntouched get() = rack.size == turnRack.size && table == turnTable

    /** 내 차례를 끝낼 수 없는 까닭이다. 끝낼 수 있으면 null이다. */
    val problem: String?
        get() {
            if (!isHumanTurn) return "차례가 아닙니다."
            if (rack.size == turnRack.size) return "타일을 내려놓거나 뽑으세요."
            if (!table.all(Rummikub::isValid)) return "완성되지 않은 묶음이 있습니다."
            if (opened[Rummikub.HUMAN]) return null
            val value = table.filter { it !in turnTable }.sumOf(Rummikub::value)
            return if (value < Rummikub.OPENING_VALUE) "첫 등록은 ${Rummikub.OPENING_VALUE}점 이상이어야 합니다. (지금 ${value}점)" else null
        }

    /** 첫 등록 전에는 내 패에서 낸 타일만 만질 수 있다. */
    private fun canTouch(tile: RummikubTile) = opened[Rummikub.HUMAN] || tile in turnRack

    fun isOnTable(tile: RummikubTile) = table.any { tile in it }

    /** 고른 타일 중 이번 차례에 내 패에서 내려놓아 다시 가져올 수 있는 타일이다. */
    val returnable get() = picked.filter { it in turnRack && it !in rack }

    fun tapTile(tile: RummikubTile): RummikubGame {
        if (!isHumanTurn) return this
        val meld = table.indexOfFirst { tile in it }
        return when {
            meld >= 0 && picked.isNotEmpty() && tile !in picked -> moveTo(meld)
            !canTouch(tile) -> copy(notice = "첫 등록 전에는 판의 타일을 쓸 수 없습니다.")
            tile in picked -> copy(picked = picked - tile)
            else -> copy(picked = picked + tile)
        }
    }

    fun moveTo(meld: Int): RummikubGame {
        if (!isHumanTurn || picked.isEmpty()) return this
        if (!table[meld].all(::canTouch)) return copy(notice = "첫 등록 전에는 판의 묶음에 붙일 수 없습니다.")
        val moved = table.mapIndexed { index, tiles ->
            if (index == meld) Rummikub.arrange(tiles - picked + picked) else tiles - picked
        }
        return copy(
            racks = withHumanRack(rack - picked),
            table = moved.filter { it.isNotEmpty() },
            picked = emptySet(),
            notice = "",
        )
    }

    fun newMeld(): RummikubGame {
        if (!isHumanTurn || picked.isEmpty()) return this
        return copy(
            racks = withHumanRack(rack - picked),
            table = table.map { it - picked }.filter { it.isNotEmpty() } + listOf(Rummikub.arrange(picked.toList())),
            picked = emptySet(),
            notice = "",
        )
    }

    fun returnPicked(): RummikubGame {
        if (!isHumanTurn) return this
        val tiles = returnable
        return copy(
            racks = withHumanRack(rack + tiles),
            table = table.map { it - tiles.toSet() }.filter { it.isNotEmpty() },
            picked = picked - tiles.toSet(),
        )
    }

    fun resetTurn(): RummikubGame {
        if (!isHumanTurn) return this
        return copy(racks = withHumanRack(turnRack), table = turnTable, picked = emptySet(), notice = "")
    }

    fun finishTurn(): RummikubGame {
        problem?.let { return copy(notice = it) }
        return copy(
            opened = opened.mapIndexed { index, open -> open || index == Rummikub.HUMAN },
            passes = 0,
            picked = emptySet(),
            recent = emptySet(),
            notice = "",
        ).afterTurn()
    }

    /** 뽑을 타일이 없으면 차례만 넘긴다. */
    fun draw(): RummikubGame {
        if (!isHumanTurn || !isUntouched) return this
        return copy(picked = emptySet(), recent = emptySet(), notice = "").drawFor(turn).afterTurn()
    }

    fun cpuTurn(): RummikubGame {
        val player = turn
        val hand = racks[player]
        val isOpen = opened[player]
        val melds = RummikubAi.bestMelds(hand, if (isOpen) 0 else Rummikub.OPENING_VALUE)
        var left = hand - melds.flatten().toSet()
        var nextTable = table + melds
        // 첫 등록한 차례에는 판의 타일을 건드리지 않는다.
        if (isOpen) {
            val (extended, remaining) = RummikubAi.extend(nextTable, left)
            nextTable = extended
            left = remaining
        }
        val played = hand.size - left.size
        val name = "CPU $player"
        if (played == 0) {
            val drawn = drawFor(player)
            val text = if (pool.isEmpty()) "$name 넘김" else "$name 뽑음"
            return drawn.withNotice(text).afterTurn()
        }
        val placed = nextTable.flatten().toSet() - table.flatten().toSet()
        return copy(
            racks = racks.mapIndexed { index, tiles -> if (index == player) left else tiles },
            table = nextTable,
            opened = opened.mapIndexed { index, open -> open || index == player },
            passes = 0,
            recent = recent + placed,
        ).withNotice("$name ${played}장 냄").afterTurn()
    }

    private fun withNotice(text: String) = copy(notice = if (notice.isEmpty()) text else "$notice · $text")

    private fun withHumanRack(tiles: List<RummikubTile>) =
        racks.mapIndexed { index, rack -> if (index == Rummikub.HUMAN) tiles else rack }

    private fun drawFor(player: Int): RummikubGame {
        if (pool.isEmpty()) return copy(passes = passes + 1)
        return copy(
            racks = racks.mapIndexed { index, tiles -> if (index == player) tiles + pool.first() else tiles },
            pool = pool.drop(1),
            passes = 0,
        )
    }

    private fun afterTurn(): RummikubGame {
        val empty = racks.indices.firstOrNull { racks[it].isEmpty() }
        if (empty != null) return copy(winner = empty)
        if (pool.isEmpty() && passes >= racks.size) {
            return copy(winner = racks.indices.minBy { index -> racks[index].sumOf { it.points } })
        }
        val next = (turn + 1) % racks.size
        return if (next == Rummikub.HUMAN) {
            copy(turn = next, turnRack = rack, turnTable = table)
        } else {
            copy(turn = next)
        }
    }

    /** 이기면 상대 패에 남은 점수를 얻는다. */
    val humanScore get() = racks.drop(1).sumOf { tiles -> tiles.sumOf { it.points } }

    /** 저장할 때는 진행 중인 차례를 되돌려 차례가 시작될 때의 상태만 남긴다. */
    fun atTurnStart() = resetTurn()
}

object Rummikub {
    const val HUMAN = 0
    const val COLORS = 4
    const val MAX_NUMBER = 13
    const val MIN_MELD = 3
    const val OPENING_VALUE = 30
    const val JOKER_POINTS = 30
    private const val RACK_SIZE = 14
    private const val TILE_COUNT = 106

    fun deal(cpus: Int, random: Random = Random.Default): RummikubGame {
        val tiles = List(TILE_COUNT, ::RummikubTile).shuffled(random)
        val players = cpus + 1
        return RummikubGame(
            racks = List(players) { tiles.subList(it * RACK_SIZE, (it + 1) * RACK_SIZE) },
            table = emptyList(),
            pool = tiles.drop(players * RACK_SIZE),
            opened = List(players) { false },
        )
    }

    fun isValid(meld: List<RummikubTile>) = run(meld) != null || setValue(meld) != null

    fun value(meld: List<RummikubTile>) = maxOf(run(meld)?.second ?: 0, setValue(meld) ?: 0)

    /** 연속이 되면 숫자 자리대로, 아니면 숫자·색 순서로 늘어놓는다. 조커는 맨 뒤다. */
    fun arrange(meld: List<RummikubTile>) = run(meld)?.first
        ?: meld.sortedWith(compareBy({ it.isJoker }, { it.number }, { it.color }))

    /** 같은 색 연속 숫자의 놓는 순서와 점수다. 남는 조커는 큰 숫자 쪽부터 붙인다. */
    private fun run(meld: List<RummikubTile>): Pair<List<RummikubTile>, Int>? {
        if (meld.size !in MIN_MELD..MAX_NUMBER) return null
        val (jokers, naturals) = meld.partition { it.isJoker }
        if (naturals.isEmpty() || naturals.map { it.color }.distinct().size != 1) return null
        val byNumber = naturals.associateBy { it.number }
        if (byNumber.size != naturals.size) return null
        val lowest = byNumber.keys.min()
        if (byNumber.keys.max() - lowest >= meld.size) return null
        val top = minOf(MAX_NUMBER, lowest + meld.size - 1)
        val bottom = top - meld.size + 1
        val spare = jokers.iterator()
        return (bottom..top).map { byNumber[it] ?: spare.next() } to (bottom..top).sum()
    }

    /** 같은 숫자 다른 색 묶음의 점수다. */
    private fun setValue(meld: List<RummikubTile>): Int? {
        if (meld.size !in MIN_MELD..COLORS) return null
        val naturals = meld.filterNot { it.isJoker }
        if (naturals.isEmpty() || naturals.map { it.number }.distinct().size != 1) return null
        if (naturals.map { it.color }.distinct().size != naturals.size) return null
        return naturals.first().number * meld.size
    }
}

/** 컴퓨터는 자기 패로 묶음을 만들고 남은 타일을 판의 묶음에 붙인다. 판 전체를 다시 짜지는 않는다. */
object RummikubAi {
    private const val SEARCH_LIMIT = 30_000
    private const val TILE_WEIGHT = 100
    private const val JOKER_COST = 20

    /** 패에서 가장 많은 타일을 내는 묶음 조합을 찾는다. 점수가 minValue에 못 미치면 내지 않는다. */
    fun bestMelds(hand: List<RummikubTile>, minValue: Int): List<List<RummikubTile>> {
        val stacks = hand.groupBy { it.face }.mapValues { it.value.toMutableList() }
        val candidates = candidates(hand)
        var best = emptyList<List<RummikubTile>>()
        var bestScore = 0
        var nodes = 0
        val chosen = mutableListOf<List<RummikubTile>>()

        fun search(start: Int, value: Int) {
            if (nodes++ >= SEARCH_LIMIT) return
            if (value >= minValue) {
                val tiles = chosen.flatten()
                val score = tiles.size * TILE_WEIGHT + value - tiles.count { it.isJoker } * JOKER_COST
                if (score > bestScore) {
                    bestScore = score
                    best = chosen.toList()
                }
            }
            for (index in start until candidates.size) {
                val candidate = candidates[index]
                val needed = candidate.groupingBy { it.face }.eachCount()
                if (needed.any { (face, count) -> stacks[face].orEmpty().size < count }) continue
                // 같은 타일 두 벌이 있으니 후보를 꺼낼 때 남은 실제 타일로 바꾼다.
                val taken = candidate.map { stacks.getValue(it.face).let { stack -> stack.removeAt(stack.lastIndex) } }
                chosen += Rummikub.arrange(taken)
                search(index, value + Rummikub.value(taken))
                chosen.removeAt(chosen.lastIndex)
                taken.forEach { stacks.getValue(it.face) += it }
            }
        }

        search(0, 0)
        return best
    }

    private fun candidates(hand: List<RummikubTile>): List<List<RummikubTile>> {
        val byFace = hand.filterNot { it.isJoker }.associateBy { it.color to it.number }
        val jokers = hand.filter { it.isJoker }
        val found = mutableListOf<List<RummikubTile>>()

        for (color in 0 until Rummikub.COLORS) {
            for (start in 1..Rummikub.MAX_NUMBER - Rummikub.MIN_MELD + 1) {
                val run = mutableListOf<RummikubTile>()
                var used = 0
                for (number in start..Rummikub.MAX_NUMBER) {
                    val tile = byFace[color to number]
                        ?: jokers.getOrNull(used++)
                        ?: break
                    run += tile
                    if (Rummikub.isValid(run)) found += run.toList()
                }
            }
        }

        for (number in 1..Rummikub.MAX_NUMBER) {
            val colors = (0 until Rummikub.COLORS).mapNotNull { byFace[it to number] }
            for (mask in 1 until (1 shl colors.size)) {
                val naturals = colors.filterIndexed { index, _ -> mask shr index and 1 == 1 }
                for (count in 0..jokers.size) {
                    val set = naturals + jokers.take(count)
                    if (Rummikub.isValid(set)) found += set
                }
            }
        }
        return found.distinctBy { meld -> meld.map { it.face }.sorted() }.sortedByDescending { it.size }
    }

    /** 남은 타일을 판에 붙이고, 연속 묶음 한가운데의 같은 타일이면 묶음을 둘로 나눠 낸다. */
    fun extend(
        table: List<List<RummikubTile>>,
        hand: List<RummikubTile>,
    ): Pair<List<List<RummikubTile>>, List<RummikubTile>> {
        val melds = table.toMutableList()
        val left = hand.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            // 조커는 낼 타일이 조커밖에 없을 때만 판에 붙인다.
            val playable = left.filterNot { it.isJoker }.ifEmpty { left.toList() }
            for (tile in playable) {
                if (attach(melds, tile) || split(melds, tile)) {
                    left -= tile
                    changed = true
                }
            }
        }
        return melds to left
    }

    private fun attach(melds: MutableList<List<RummikubTile>>, tile: RummikubTile): Boolean {
        val index = melds.indexOfFirst { Rummikub.isValid(it + tile) }
        if (index < 0) return false
        melds[index] = Rummikub.arrange(melds[index] + tile)
        return true
    }

    private fun split(melds: MutableList<List<RummikubTile>>, tile: RummikubTile): Boolean {
        for ((index, meld) in melds.withIndex()) {
            val at = meld.indexOfFirst { it.face == tile.face }
            if (at < 0) continue
            val head = meld.subList(0, at + 1)
            val tail = listOf(tile) + meld.subList(at + 1, meld.size)
            if (Rummikub.isValid(head) && Rummikub.isValid(tail)) {
                melds[index] = head
                melds += tail
                return true
            }
        }
        return false
    }
}
