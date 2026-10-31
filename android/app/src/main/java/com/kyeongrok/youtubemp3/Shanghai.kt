package com.kyeongrok.youtubemp3

data class ShanghaiSpot(val layer: Int, val row: Int, val column: Int)

/** 패 무늬는 0..8 만수, 9..17 통수, 18..26 삭수, 27..33 자패이고 없어진 자리는 EMPTY다. */
data class ShanghaiGame(
    val faces: List<Int>,
    val selected: Int? = null,
    val hint: Pair<Int, Int>? = null,
) {
    val remaining = faces.count { it != Shanghai.EMPTY }
    val isComplete = remaining == 0
    val freePair = Shanghai.findFreePair(faces)

    fun isFree(index: Int) = Shanghai.isFree(faces, index)

    /** 고른 패와 무늬가 같으면 함께 없애고, 다르면 새로 누른 패를 고른다. */
    fun pick(index: Int): ShanghaiGame {
        val first = selected
        return when {
            !isFree(index) -> this
            first == index -> copy(selected = null)
            first != null && faces[first] == faces[index] -> copy(
                faces = faces.toMutableList().also {
                    it[first] = Shanghai.EMPTY
                    it[index] = Shanghai.EMPTY
                },
                selected = null,
                hint = null,
            )
            else -> copy(selected = index)
        }
    }

    fun showHint() = copy(hint = freePair)

    /** 끝까지 풀 수 있게 다시 놓을 수 없으면 null이다. */
    fun shuffled(): ShanghaiGame? {
        val pairs = faces.filter { it != Shanghai.EMPTY }.groupBy { it }.values
            .flatMap { same -> List(same.size / 2) { same[0] } }
        return Shanghai.deal(faces.indices.filter { faces[it] != Shanghai.EMPTY }, pairs)?.let {
            ShanghaiGame(it)
        }
    }
}

object Shanghai {
    const val EMPTY = -1
    const val COLUMNS = 8
    const val ROWS = 8

    private const val DEAL_ATTEMPTS = 2000
    private const val FACE_COUNT = 34

    // 층마다 사방으로 한 칸씩 좁아지는 피라미드(8×8, 6×6, 4×4, 2×2).
    val SPOTS: List<ShanghaiSpot> = (0..3).flatMap { layer ->
        (layer until ROWS - layer).flatMap { row ->
            (layer until COLUMNS - layer).map { column -> ShanghaiSpot(layer, row, column) }
        }
    }

    private val INDEX_OF = SPOTS.withIndex().associate { (index, spot) -> spot to index }

    private fun neighbor(spot: ShanghaiSpot, layer: Int = 0, column: Int = 0) =
        INDEX_OF[spot.copy(layer = spot.layer + layer, column = spot.column + column)]

    private val ABOVE = SPOTS.map { neighbor(it, layer = 1) }
    private val LEFT = SPOTS.map { neighbor(it, column = -1) }
    private val RIGHT = SPOTS.map { neighbor(it, column = 1) }

    private fun present(faces: List<Int>, index: Int?) = index != null && faces[index] != EMPTY

    /** 위에 쌓인 패가 없고 왼쪽이나 오른쪽이 비어 있어야 집을 수 있다. */
    fun isFree(faces: List<Int>, index: Int) =
        present(faces, index) && !present(faces, ABOVE[index]) &&
            (!present(faces, LEFT[index]) || !present(faces, RIGHT[index]))

    fun findFreePair(faces: List<Int>): Pair<Int, Int>? {
        val free = faces.indices.filter { isFree(faces, it) }
        for ((position, first) in free.withIndex()) {
            val second = free.drop(position + 1).firstOrNull { faces[it] == faces[first] }
            if (second != null) return first to second
        }
        return null
    }

    fun generate(): ShanghaiGame {
        // 무늬마다 2쌍씩 68쌍 중에서 판에 들어갈 만큼만 고른다.
        val pairs = (0 until FACE_COUNT).flatMap { listOf(it, it) }.shuffled().take(SPOTS.size / 2)
        while (true) {
            deal(SPOTS.indices.toList(), pairs)?.let { return ShanghaiGame(it) }
        }
    }

    /** 다 맞춘 판에서 거꾸로 되짚듯, 집을 수 있는 두 자리에 한 쌍씩 놓아 풀이가 늘 있게 한다. */
    fun deal(spots: List<Int>, pairs: List<Int>): List<Int>? {
        repeat(DEAL_ATTEMPTS) {
            dealOnce(spots, pairs.shuffled())?.let { return it }
        }
        return null
    }

    private fun dealOnce(spots: List<Int>, pairs: List<Int>): List<Int>? {
        val taken = MutableList(SPOTS.size) { EMPTY }
        spots.forEach { taken[it] = 0 }
        val dealt = MutableList(SPOTS.size) { EMPTY }
        for (face in pairs) {
            val free = spots.filter { isFree(taken, it) }.shuffled()
            if (free.size < 2) return null
            for (index in free.take(2)) {
                dealt[index] = face
                taken[index] = EMPTY
            }
        }
        return dealt
    }
}
