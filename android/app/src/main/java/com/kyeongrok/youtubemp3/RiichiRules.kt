package com.kyeongrok.youtubemp3

import kotlin.math.min

/**
 * 리치마작 패와 화료 판정, 역·부·점수 계산.
 *
 * 패 하나하나는 0..135 번호이고 종류(kind)는 번호 / 4다.
 * 종류 0..8 만수, 9..17 통수, 18..26 삭수, 27..30 동남서북, 31 중, 32 발, 33 백 (상하이와 같은 순서).
 * 각 5의 첫 장(16, 52, 88)은 적도라다.
 */
object Mahjong {
    const val KINDS = 34
    const val EAST = 27
    const val CHUN = 31
    const val HATSU = 32
    const val HAKU = 33

    fun kind(tile: Int) = tile / 4
    fun isRed(tile: Int) = tile == 16 || tile == 52 || tile == 88
    fun isHonor(kind: Int) = kind >= 27
    fun isTerminal(kind: Int) = kind < 27 && (kind % 9 == 0 || kind % 9 == 8)
    fun isYaochu(kind: Int) = isHonor(kind) || isTerminal(kind)
    fun isDragon(kind: Int) = kind >= CHUN
    fun suit(kind: Int) = if (kind < 27) kind / 9 else 3

    /** 도라 표시패 다음 패가 도라다. 삼원패는 백→발→중→백 순서. */
    fun doraOf(indicator: Int): Int = when {
        indicator < 27 -> indicator / 9 * 9 + (indicator % 9 + 1) % 9
        indicator <= 30 -> 27 + (indicator - 27 + 1) % 4
        indicator == HAKU -> HATSU
        indicator == HATSU -> CHUN
        else -> HAKU
    }

    fun counts(tiles: Iterable<Int>): IntArray {
        val counts = IntArray(KINDS)
        for (tile in tiles) counts[kind(tile)]++
        return counts
    }

    private val KOREAN_NUMBERS = listOf("일", "이", "삼", "사", "오", "육", "칠", "팔", "구")
    private val HONOR_NAMES = listOf("동", "남", "서", "북", "중", "발", "백")
    val WIND_NAMES = listOf("동", "남", "서", "북")

    fun kindName(kind: Int): String = when (kind / 9) {
        0 -> "${KOREAN_NUMBERS[kind % 9]}만"
        1 -> "${KOREAN_NUMBERS[kind % 9]}통"
        2 -> "${KOREAN_NUMBERS[kind % 9]}삭"
        else -> HONOR_NAMES[kind - 27]
    }

    fun tileName(tile: Int) = (if (isRed(tile)) "적" else "") + kindName(kind(tile))

    // ── 샹텐 수: 화료까지 몇 장 더 바꿔야 하는지. 0이면 텐파이, -1이면 화료형 ──

    fun shanten(counts: IntArray, melds: Int): Int {
        var best = standardShanten(counts, melds)
        if (melds == 0) best = min(best, min(chiitoiShanten(counts), kokushiShanten(counts)))
        return best
    }

    fun chiitoiShanten(counts: IntArray): Int {
        var pairs = 0
        var kinds = 0
        for (c in counts) {
            if (c >= 2) pairs++
            if (c >= 1) kinds++
        }
        return 6 - pairs + maxOf(0, 7 - kinds)
    }

    fun kokushiShanten(counts: IntArray): Int {
        var kinds = 0
        var pair = false
        for (k in 0 until KINDS) {
            if (!isYaochu(k)) continue
            if (counts[k] >= 1) kinds++
            if (counts[k] >= 2) pair = true
        }
        return 13 - kinds - if (pair) 1 else 0
    }

    fun standardShanten(counts: IntArray, melds: Int): Int {
        val c = counts.copyOf()
        var best = 8
        fun search(start: Int, sets: Int, partials: Int, pair: Int) {
            var i = start
            while (i < KINDS && c[i] == 0) i++
            if (i >= KINDS) {
                val allSets = sets + melds
                val usable = min(partials, 4 - allSets)
                val value = 8 - 2 * allSets - usable - pair
                if (value < best) best = value
                return
            }
            // 이 패로 만들 수 있는 것을 다 해 보고, 마지막에 이 패를 버린 셈 친다.
            if (c[i] >= 3) {
                c[i] -= 3; search(i, sets + 1, partials, pair); c[i] += 3
            }
            val sequenceStart = i < 27 && i % 9 <= 6
            if (sequenceStart && c[i + 1] > 0 && c[i + 2] > 0) {
                c[i]--; c[i + 1]--; c[i + 2]--
                search(i, sets + 1, partials, pair)
                c[i]++; c[i + 1]++; c[i + 2]++
            }
            if (c[i] >= 2) {
                c[i] -= 2
                if (pair == 0) search(i, sets, partials, 1)
                search(i, sets, partials + 1, pair)
                c[i] += 2
            }
            if (i < 27 && i % 9 <= 7 && c[i + 1] > 0) {
                c[i]--; c[i + 1]--; search(i, sets, partials + 1, pair); c[i]++; c[i + 1]++
            }
            if (sequenceStart && c[i + 2] > 0) {
                c[i]--; c[i + 2]--; search(i, sets, partials + 1, pair); c[i]++; c[i + 2]++
            }
            val saved = c[i]
            c[i] = 0
            search(i + 1, sets, partials, pair)
            c[i] = saved
        }
        search(0, 0, 0, 0)
        return best
    }

    /** 이 손(13장 기준)으로 화료할 수 있는 패 종류. 텐파이가 아니면 비어 있다. */
    fun waits(counts: IntArray, melds: Int): List<Int> {
        if (shanten(counts, melds) != 0) return emptyList()
        return (0 until KINDS).filter { k ->
            if (counts[k] >= 4) return@filter false
            counts[k]++
            val complete = shanten(counts, melds) == -1
            counts[k]--
            complete
        }
    }

    // ── 화료형 분해 ──

    enum class BlockType { Sequence, Triplet, Pair }

    data class Block(val type: BlockType, val kind: Int)

    /** 손에 남은 패를 머리 하나와 몸통 sets개로 나누는 모든 방법. */
    fun decompositions(counts: IntArray, sets: Int): List<List<Block>> {
        val result = mutableListOf<List<Block>>()
        val c = counts.copyOf()
        val current = mutableListOf<Block>()
        fun extract(remaining: Int) {
            var i = 0
            while (i < KINDS && c[i] == 0) i++
            if (i >= KINDS) {
                if (remaining == 0) result += current.toList()
                return
            }
            if (remaining == 0) return
            if (c[i] >= 3) {
                c[i] -= 3; current += Block(BlockType.Triplet, i)
                extract(remaining - 1)
                current.removeAt(current.lastIndex); c[i] += 3
            }
            if (i < 27 && i % 9 <= 6 && c[i + 1] > 0 && c[i + 2] > 0) {
                c[i]--; c[i + 1]--; c[i + 2]--; current += Block(BlockType.Sequence, i)
                extract(remaining - 1)
                current.removeAt(current.lastIndex); c[i]++; c[i + 1]++; c[i + 2]++
            }
        }
        for (pair in 0 until KINDS) {
            if (c[pair] < 2) continue
            c[pair] -= 2
            current += Block(BlockType.Pair, pair)
            extract(sets)
            current.clear()
            c[pair] += 2
        }
        return result.distinct()
    }
}

enum class MeldType { Chi, Pon, OpenKan, ClosedKan, AddedKan }

/** 운 패. called는 남에게서 받은 패 번호(안깡은 -1), from은 그 패를 버린 자리다. */
data class Meld(val type: MeldType, val tiles: List<Int>, val called: Int = -1, val from: Int = -1) {
    val kind get() = Mahjong.kind(tiles.minOf { it })
    val isKan get() = type == MeldType.OpenKan || type == MeldType.ClosedKan || type == MeldType.AddedKan
    val isOpen get() = type != MeldType.ClosedKan
}

/** 화료할 때의 상황. hand는 화료패를 포함한 손패(운 패 제외)다. */
data class WinContext(
    val hand: List<Int>,
    val melds: List<Meld>,
    val winTile: Int,
    val tsumo: Boolean,
    val seatWind: Int,
    val roundWind: Int,
    val dealer: Boolean,
    val riichi: Boolean = false,
    val doubleRiichi: Boolean = false,
    val ippatsu: Boolean = false,
    val lastTile: Boolean = false,
    val rinshan: Boolean = false,
    val chankan: Boolean = false,
    val firstDraw: Boolean = false,
    val doraIndicators: List<Int> = emptyList(),
    val uraIndicators: List<Int> = emptyList(),
)

data class Yaku(val name: String, val han: Int)

data class WinScore(
    val yaku: List<Yaku>,
    val han: Int,
    val fu: Int,
    /** 역만 배수. 0이면 일반 역이다. */
    val yakuman: Int,
    val base: Int,
) {
    val label: String
        get() = when {
            yakuman >= 2 -> "${yakuman}배 역만"
            yakuman == 1 -> "역만"
            han >= 13 -> "헤아림 역만"
            han >= 11 -> "삼배만"
            han >= 8 -> "배만"
            han >= 6 -> "하네만"
            base >= 2000 -> "만관"
            else -> ""
        }

    /** 론으로 받는 점수(본장 제외). */
    fun ron(dealer: Boolean) = roundUp(base * if (dealer) 6 else 4)

    /** 쯔모일 때 (친이 내는 점수, 자가 내는 점수). 친이 화료하면 둘이 같다. */
    fun tsumo(dealer: Boolean): Pair<Int, Int> =
        if (dealer) roundUp(base * 2) to roundUp(base * 2) else roundUp(base * 2) to roundUp(base)

    companion object {
        fun roundUp(points: Int) = (points + 99) / 100 * 100
    }
}

object RiichiScoring {
    private val GREEN = setOf(19, 20, 21, 23, 25, Mahjong.HATSU)

    /** 역이 없거나 화료형이 아니면 null이다. 여러 해석 중 점수가 가장 높은 것을 고른다. */
    fun score(context: WinContext): WinScore? {
        val counts = Mahjong.counts(context.hand)
        val sets = 4 - context.melds.size
        val candidates = mutableListOf<WinScore>()

        if (context.melds.isEmpty() && Mahjong.kokushiShanten(counts) == -1) {
            return finish(listOf(Yaku("국사무쌍", 13)), 0, context)
        }
        if (context.melds.isEmpty() && Mahjong.chiitoiShanten(counts) == -1) {
            chiitoi(context, counts)?.let { candidates += it }
        }
        val winKind = Mahjong.kind(context.winTile)
        for (blocks in Mahjong.decompositions(counts, sets)) {
            // 화료패가 어느 덩어리를 완성했는지에 따라 대기 모양과 부가 달라진다.
            blocks.withIndex()
                .filter { (_, block) -> containsKind(block, winKind) }
                .distinctBy { it.value }
                .forEach { (index, _) -> standard(context, blocks, index)?.let { candidates += it } }
        }
        return candidates.maxWithOrNull(compareBy<WinScore> { it.base }.thenBy { it.han }.thenBy { it.fu })
    }

    private fun containsKind(block: Mahjong.Block, kind: Int) = when (block.type) {
        Mahjong.BlockType.Sequence -> kind in block.kind..block.kind + 2
        else -> block.kind == kind
    }

    private fun allKinds(context: WinContext): List<Int> =
        context.hand.map(Mahjong::kind) + context.melds.flatMap { meld -> meld.tiles.map(Mahjong::kind) }

    private fun commonYaku(context: WinContext, closed: Boolean): MutableList<Yaku> {
        val yaku = mutableListOf<Yaku>()
        when {
            context.doubleRiichi -> yaku += Yaku("더블 리치", 2)
            context.riichi -> yaku += Yaku("리치", 1)
        }
        if (context.riichi && context.ippatsu) yaku += Yaku("일발", 1)
        if (closed && context.tsumo) yaku += Yaku("멘젠 쯔모", 1)
        if (context.lastTile && !context.rinshan) yaku += Yaku(if (context.tsumo) "해저로월" else "하저로어", 1)
        if (context.rinshan) yaku += Yaku("영상개화", 1)
        if (context.chankan) yaku += Yaku("창깡", 1)
        return yaku
    }

    private fun flushYaku(kinds: List<Int>, closed: Boolean): Yaku? {
        val suits = kinds.map(Mahjong::suit).toSet()
        val numberSuits = suits - 3
        if (numberSuits.size != 1) return null
        return if (3 in suits) Yaku("혼일색", if (closed) 3 else 2) else Yaku("청일색", if (closed) 6 else 5)
    }

    private fun yakumanByTiles(context: WinContext, kinds: List<Int>): List<Yaku> {
        val yaku = mutableListOf<Yaku>()
        if (kinds.all(Mahjong::isHonor)) yaku += Yaku("자일색", 13)
        if (kinds.all { it in GREEN }) yaku += Yaku("녹일색", 13)
        if (kinds.all(Mahjong::isTerminal)) yaku += Yaku("청노두", 13)
        if (context.firstDraw && context.tsumo) yaku += Yaku(if (context.dealer) "천화" else "지화", 13)
        return yaku
    }

    private fun chiitoi(context: WinContext, counts: IntArray): WinScore? {
        val kinds = allKinds(context)
        val yakuman = yakumanByTiles(context, kinds)
        if (yakuman.isNotEmpty()) return finish(yakuman, 25, context)
        val yaku = commonYaku(context, closed = true)
        yaku += Yaku("치또이쯔", 2)
        if (kinds.none(Mahjong::isYaochu)) yaku += Yaku("탕야오", 1)
        if (kinds.all(Mahjong::isYaochu)) yaku += Yaku("혼노두", 2)
        flushYaku(kinds, closed = true)?.let { yaku += it }
        return finish(yaku, 25, context)
    }

    private enum class Wait { Ryanmen, Kanchan, Penchan, Shanpon, Tanki }

    private class Set(val sequence: Boolean, val kind: Int, val concealed: Boolean, val kan: Boolean)

    private fun standard(context: WinContext, blocks: List<Mahjong.Block>, winIndex: Int): WinScore? {
        val winKind = Mahjong.kind(context.winTile)
        val winBlock = blocks[winIndex]
        val wait = when (winBlock.type) {
            Mahjong.BlockType.Pair -> Wait.Tanki
            Mahjong.BlockType.Triplet -> Wait.Shanpon
            Mahjong.BlockType.Sequence -> when {
                winKind == winBlock.kind + 1 -> Wait.Kanchan
                winKind == winBlock.kind && winBlock.kind % 9 == 6 -> Wait.Penchan
                winKind == winBlock.kind + 2 && winBlock.kind % 9 == 0 -> Wait.Penchan
                else -> Wait.Ryanmen
            }
        }
        val closed = context.melds.all { !it.isOpen }
        val pair = blocks.first { it.type == Mahjong.BlockType.Pair }.kind
        val sets = blocks.withIndex().filter { it.value.type != Mahjong.BlockType.Pair }.map { (index, block) ->
            // 론으로 완성한 커쯔는 밝힌 커쯔로 친다.
            val concealed = !(index == winIndex && block.type == Mahjong.BlockType.Triplet && !context.tsumo)
            Set(block.type == Mahjong.BlockType.Sequence, block.kind, concealed, kan = false)
        } + context.melds.map { meld ->
            Set(meld.type == MeldType.Chi, meld.kind, concealed = !meld.isOpen, kan = meld.isKan)
        }
        val triplets = sets.filter { !it.sequence }
        val sequences = sets.filter { it.sequence }
        val kinds = allKinds(context)
        val seatWind = Mahjong.EAST + context.seatWind
        val roundWind = Mahjong.EAST + context.roundWind
        val concealedTriplets = triplets.count { it.concealed }
        val dragonTriplets = triplets.count { Mahjong.isDragon(it.kind) }
        val windTriplets = triplets.count { it.kind in 27..30 }

        // 역만
        val yakuman = yakumanByTiles(context, kinds).toMutableList()
        if (concealedTriplets == 4) yakuman += Yaku("스안커", 13)
        if (dragonTriplets == 3) yakuman += Yaku("대삼원", 13)
        if (windTriplets == 4) yakuman += Yaku("대사희", 26)
        else if (windTriplets == 3 && pair in 27..30) yakuman += Yaku("소사희", 13)
        if (triplets.count { it.kan } == 4) yakuman += Yaku("스깡쯔", 13)
        if (closed && isNineGates(context)) yakuman += Yaku("구련보등", 13)
        if (yakuman.isNotEmpty()) return finish(yakuman, 0, context)

        val yaku = commonYaku(context, closed)
        val valuePair = Mahjong.isDragon(pair) || pair == seatWind || pair == roundWind
        val pinfu = closed && context.melds.isEmpty() && triplets.isEmpty() && !valuePair && wait == Wait.Ryanmen
        if (pinfu) yaku += Yaku("핑후", 1)
        if (kinds.none(Mahjong::isYaochu)) yaku += Yaku("탕야오", 1)

        if (closed) {
            val repeated = sequences.groupingBy { it.kind }.eachCount().values.sumOf { it / 2 }
            when (repeated) {
                2 -> yaku += Yaku("량페코", 3)
                1 -> yaku += Yaku("이페코", 1)
            }
        }
        for (triplet in triplets) {
            when {
                triplet.kind == Mahjong.HAKU -> yaku += Yaku("역패 백", 1)
                triplet.kind == Mahjong.HATSU -> yaku += Yaku("역패 발", 1)
                triplet.kind == Mahjong.CHUN -> yaku += Yaku("역패 중", 1)
            }
            if (triplet.kind == seatWind) yaku += Yaku("자풍패 ${Mahjong.WIND_NAMES[context.seatWind]}", 1)
            if (triplet.kind == roundWind) yaku += Yaku("장풍패 ${Mahjong.WIND_NAMES[context.roundWind]}", 1)
        }
        val sequenceNumbers = sequences.map { it.kind / 9 to it.kind % 9 }.toSet()
        if ((0..6).any { n -> (0..2).all { suit -> (suit to n) in sequenceNumbers } }) {
            yaku += Yaku("삼색동순", if (closed) 2 else 1)
        }
        if ((0..2).any { suit -> listOf(0, 3, 6).all { n -> (suit to n) in sequenceNumbers } }) {
            yaku += Yaku("일기통관", if (closed) 2 else 1)
        }
        val tripletNumbers = triplets.filter { it.kind < 27 }.map { it.kind / 9 to it.kind % 9 }.toSet()
        if ((0..8).any { n -> (0..2).all { suit -> (suit to n) in tripletNumbers } }) yaku += Yaku("삼색동각", 2)

        fun hasYaochu(set: Set) =
            if (set.sequence) set.kind % 9 == 0 || set.kind % 9 == 6 else Mahjong.isYaochu(set.kind)
        val allOutside = Mahjong.isYaochu(pair) && sets.all(::hasYaochu)
        if (allOutside && sequences.isNotEmpty()) {
            if (kinds.any(Mahjong::isHonor)) yaku += Yaku("찬타", if (closed) 2 else 1)
            else yaku += Yaku("준찬타", if (closed) 3 else 2)
        }
        if (triplets.size == 4) yaku += Yaku("또이또이", 2)
        if (concealedTriplets == 3) yaku += Yaku("산안커", 2)
        if (triplets.count { it.kan } == 3) yaku += Yaku("산깡쯔", 2)
        if (dragonTriplets == 2 && Mahjong.isDragon(pair)) yaku += Yaku("소삼원", 2)
        if (kinds.all(Mahjong::isYaochu)) yaku += Yaku("혼노두", 2)
        flushYaku(kinds, closed)?.let { yaku += it }

        // 부
        val fu = if (pinfu) {
            if (context.tsumo) 20 else 30
        } else {
            var fu = 20
            if (closed && !context.tsumo) fu += 10
            if (context.tsumo) fu += 2
            for (triplet in triplets) {
                var value = if (triplet.kan) 8 else 2
                if (triplet.concealed) value *= 2
                if (Mahjong.isYaochu(triplet.kind)) value *= 2
                fu += value
            }
            if (Mahjong.isDragon(pair)) fu += 2
            if (pair == seatWind) fu += 2
            if (pair == roundWind) fu += 2
            if (wait == Wait.Kanchan || wait == Wait.Penchan || wait == Wait.Tanki) fu += 2
            // 운 손으로 부가 붙지 않은 론은 30부로 친다.
            if (fu == 20) fu = 30
            (fu + 9) / 10 * 10
        }
        return finish(yaku, fu, context)
    }

    private fun isNineGates(context: WinContext): Boolean {
        val counts = Mahjong.counts(context.hand)
        val suit = Mahjong.suit(Mahjong.kind(context.hand[0]))
        if (suit == 3 || context.hand.any { Mahjong.suit(Mahjong.kind(it)) != suit }) return false
        val need = intArrayOf(3, 1, 1, 1, 1, 1, 1, 1, 3)
        return (0..8).all { counts[suit * 9 + it] >= need[it] }
    }

    private fun finish(yaku: List<Yaku>, fu: Int, context: WinContext): WinScore? {
        if (yaku.isEmpty()) return null
        val yakumanHan = yaku.filter { it.han >= 13 }
        if (yakumanHan.isNotEmpty()) {
            val multiple = yakumanHan.sumOf { it.han / 13 }
            return WinScore(yakumanHan, 13 * multiple, fu, multiple, 8000 * multiple)
        }
        val all = yaku.toMutableList()
        val kinds = context.hand + context.melds.flatMap { it.tiles }
        val dora = context.doraIndicators.sumOf { indicator ->
            val dora = Mahjong.doraOf(Mahjong.kind(indicator))
            kinds.count { Mahjong.kind(it) == dora }
        }
        val red = kinds.count(Mahjong::isRed)
        val ura = if (context.riichi) {
            context.uraIndicators.sumOf { indicator ->
                val dora = Mahjong.doraOf(Mahjong.kind(indicator))
                kinds.count { Mahjong.kind(it) == dora }
            }
        } else {
            0
        }
        if (dora > 0) all += Yaku("도라", dora)
        if (red > 0) all += Yaku("적도라", red)
        if (ura > 0) all += Yaku("뒷도라", ura)
        val han = all.sumOf { it.han }
        val base = when {
            han >= 13 -> 8000
            han >= 11 -> 6000
            han >= 8 -> 4000
            han >= 6 -> 3000
            han >= 5 -> 2000
            else -> min(2000, fu * (1 shl (han + 2)))
        }
        return WinScore(all, han, fu, 0, base)
    }
}
