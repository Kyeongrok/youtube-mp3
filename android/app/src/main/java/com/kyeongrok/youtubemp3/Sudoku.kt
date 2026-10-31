package com.kyeongrok.youtubemp3

enum class SudokuLevel(val label: String, val blanks: Int) {
    Easy("쉬움", 36),
    Medium("보통", 46),
    Hard("어려움", 54),
}

/** 칸은 왼쪽 위부터 0..80이고 빈칸은 0이다. */
data class SudokuGame(
    val level: SudokuLevel,
    val puzzle: List<Int>,
    val solution: List<Int>,
    val cells: List<Int>,
    val notes: List<Set<Int>> = List(puzzle.size) { emptySet() },
    val mistakes: Int = 0,
    val seconds: Int = 0,
) {
    val conflicts: Set<Int> = Sudoku.findConflicts(cells)
    val isComplete = 0 !in cells && conflicts.isEmpty()

    fun isGiven(index: Int) = puzzle[index] != 0

    /** 숫자를 넣으면 그 칸의 메모와 같은 줄·칸·박스에 적힌 같은 숫자 메모를 지운다. 0은 칸을 비운다. */
    fun enter(index: Int, digit: Int): SudokuGame {
        if (isGiven(index) || isComplete) return this
        val wrong = digit != 0 && digit != cells[index] && digit != solution[index]
        return copy(
            mistakes = if (wrong) mistakes + 1 else mistakes,
            cells = cells.toMutableList().also { it[index] = digit },
            notes = notes.mapIndexed { other, note ->
                when {
                    other == index -> emptySet()
                    digit != 0 && Sudoku.arePeers(index, other) -> note - digit
                    else -> note
                }
            },
        )
    }

    fun toggleNote(index: Int, digit: Int): SudokuGame {
        if (isGiven(index) || isComplete || cells[index] != 0) return this
        val note = notes[index]
        return copy(
            notes = notes.toMutableList().also { it[index] = if (digit in note) note - digit else note + digit },
        )
    }
}

object Sudoku {
    private const val SIZE = 81

    private val UNITS: List<List<Int>> =
        List(9) { row -> List(9) { row * 9 + it } } +
            List(9) { column -> List(9) { it * 9 + column } } +
            List(9) { box -> List(9) { (box / 3 * 3 + it / 3) * 9 + box % 3 * 3 + it % 3 } }

    private val PEERS: List<Set<Int>> = List(SIZE) { index ->
        UNITS.filter { index in it }.flatten().toSet() - index
    }

    fun arePeers(first: Int, second: Int) = second in PEERS[first]

    fun findConflicts(cells: List<Int>): Set<Int> =
        UNITS.flatMap { unit ->
            unit.filter { cells[it] != 0 }.groupBy { cells[it] }.values.filter { it.size > 1 }.flatten()
        }.toSet()

    /** 빈 판을 무작위로 채운 뒤, 풀이가 하나로 남는 칸만 골라 비운다. */
    fun generate(level: SudokuLevel): SudokuGame {
        val cells = IntArray(SIZE)
        countSolutions(cells, 1)
        val solution = cells.toList()

        var blanks = 0
        for (index in (0 until SIZE).shuffled()) {
            if (blanks == level.blanks) break
            val digit = cells[index]
            cells[index] = 0
            if (countSolutions(cells.copyOf(), 2) == 1) blanks++ else cells[index] = digit
        }
        return SudokuGame(level, cells.toList(), solution, cells.toList())
    }

    /** 후보가 가장 적은 빈칸부터 채워 풀이를 limit개까지 센다. limit에 닿으면 cells는 풀이로 채워진 채 끝난다. */
    private fun countSolutions(cells: IntArray, limit: Int): Int {
        var target = -1
        var candidates = emptyList<Int>()
        for (index in 0 until SIZE) {
            if (cells[index] != 0) continue
            val options = (1..9) - PEERS[index].map { cells[it] }.toSet()
            if (target == -1 || options.size < candidates.size) {
                target = index
                candidates = options
            }
        }
        if (target == -1) return 1

        var found = 0
        for (digit in candidates.shuffled()) {
            cells[target] = digit
            found += countSolutions(cells, limit - found)
            if (found >= limit) return found
        }
        cells[target] = 0
        return found
    }
}
