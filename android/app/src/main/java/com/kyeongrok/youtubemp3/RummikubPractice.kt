package com.kyeongrok.youtubemp3

private const val RED = RummikubTile.RED
private const val BLUE = RummikubTile.BLUE
private const val BLACK = RummikubTile.BLACK
private const val ORANGE = RummikubTile.ORANGE

private fun tile(color: Int, number: Int) = RummikubTile.of(color, number)

private fun run(color: Int, numbers: IntRange) = numbers.map { tile(color, it) }

private val Joker = RummikubTile(RummikubTile.JOKER_START)

/** 정해진 패로 한 동작씩 익히는 장면이다. targets를 한 묶음에 모으고 완료하거나, drawLesson이면 뽑으면 끝난다. */
private class PracticeScene(
    val title: String,
    val rack: List<RummikubTile>,
    val table: List<List<RummikubTile>> = emptyList(),
    val opened: Boolean = true,
    val targets: List<RummikubTile> = emptyList(),
    val pickText: String = "",
    val placeText: String = "",
    val finishText: String,
    val praise: String,
    val drawLesson: Boolean = false,
) {
    fun isPlaced(game: RummikubGame) = targets.isNotEmpty() && game.table.any { it.containsAll(targets) }

    fun guide(game: RummikubGame) = when {
        drawLesson || isPlaced(game) -> finishText
        game.picked.containsAll(targets) -> placeText
        else -> pickText
    }

    fun start() = RummikubGame(
        racks = listOf(rack, CpuRack),
        table = table,
        pool = run(ORANGE, 1..6),
        opened = listOf(opened, true),
        recent = targets.toSet(),
    )
}

private val CpuRack = run(BLACK, 1..5)

private val Scenes = listOf(
    PracticeScene(
        title = "첫 등록",
        rack = listOf(tile(RED, 10), tile(BLUE, 10), tile(ORANGE, 10), tile(BLACK, 2), tile(BLUE, 7)),
        opened = false,
        targets = listOf(tile(RED, 10), tile(BLUE, 10), tile(ORANGE, 10)),
        pickText = "테두리가 굵은 10 세 장을 차례로 눌러 고르세요.",
        placeText = "판에 나타난 「+ 새 묶음」을 누르세요.",
        finishText = "10+10+10=30점이라 첫 등록 조건을 채웠습니다. 「완료」를 누르세요.",
        praise = "첫 등록 성공! 이제부터 판의 타일도 쓸 수 있습니다.",
    ),
    PracticeScene(
        title = "묶음에 붙이기",
        rack = listOf(tile(RED, 7), tile(BLACK, 2), tile(ORANGE, 12)),
        table = listOf(run(RED, 4..6)),
        targets = listOf(tile(RED, 7)),
        pickText = "패의 빨간 7을 눌러 고르세요.",
        placeText = "판의 빨간 4·5·6 묶음을 누르세요.",
        finishText = "4·5·6·7로 이어졌습니다. 「완료」를 누르세요.",
        praise = "판에 있는 묶음에 타일을 붙였습니다.",
    ),
    PracticeScene(
        title = "낼 타일이 없을 때",
        rack = listOf(tile(RED, 1), tile(BLUE, 5), tile(ORANGE, 11)),
        table = listOf(run(BLACK, 7..9)),
        finishText = "지금 패로는 낼 수 있는 타일이 없습니다. 「뽑기」를 누르세요.",
        praise = "1장을 가져오고 차례가 넘어갔습니다.",
        drawLesson = true,
    ),
    PracticeScene(
        title = "조커 쓰기",
        rack = listOf(Joker, tile(BLUE, 8), tile(BLUE, 9), tile(ORANGE, 1)),
        targets = listOf(Joker, tile(BLUE, 8), tile(BLUE, 9)),
        pickText = "★과 파란 8, 9를 모두 눌러 고르세요.",
        placeText = "「+ 새 묶음」을 누르세요.",
        finishText = "★이 빠진 숫자 자리를 채웠습니다. 「완료」를 누르세요.",
        praise = "조커로 묶음을 완성했습니다.",
    ),
    PracticeScene(
        title = "판 다시 짜기",
        rack = listOf(tile(RED, 7), tile(ORANGE, 7), tile(BLACK, 12)),
        table = listOf(run(BLUE, 3..7)),
        targets = listOf(tile(BLUE, 7), tile(RED, 7), tile(ORANGE, 7)),
        pickText = "판의 파란 7과 패의 빨강·주황 7을 고르세요.",
        placeText = "「+ 새 묶음」을 누르세요.",
        finishText = "파란 묶음은 4장이 남아도 완성입니다. 「완료」를 누르세요.",
        praise = "판의 타일을 빌려 새 묶음을 만들었습니다.",
    ),
    PracticeScene(
        title = "마지막 타일",
        rack = listOf(tile(BLACK, 9)),
        table = listOf(run(BLACK, 6..8)),
        targets = listOf(tile(BLACK, 9)),
        pickText = "마지막 남은 9를 눌러 고르세요.",
        placeText = "판의 6·7·8 묶음을 누르세요.",
        finishText = "패가 비었습니다. 「완료」를 눌러 게임을 끝내세요.",
        praise = "패를 모두 내려놓아 이겼습니다! 실제 게임에서는 컴퓨터 패에 남은 타일의 합이 점수가 됩니다.",
    ),
)

/** 연습 진행 상황이다. 저장하지 않고 진행 중인 게임이나 기록에도 영향을 주지 않는다. */
data class RummikubPractice(val scene: Int, val game: RummikubGame, val isComplete: Boolean = false) {
    private val current get() = Scenes[scene]

    val title get() = current.title
    val guide get() = if (isComplete) current.praise else current.guide(game)
    val isLast get() = scene == Scenes.lastIndex

    fun change(update: (RummikubGame) -> RummikubGame) = if (isComplete) this else copy(game = update(game))

    fun finishTurn(): RummikubPractice = when {
        isComplete -> this
        game.problem != null -> copy(game = game.finishTurn())
        !current.isPlaced(game) -> copy(game = game.copy(notice = "안내대로 해 보세요. 막히면 「되돌리기」를 누르세요."))
        else -> copy(game = game.finishTurn(), isComplete = true)
    }

    fun draw(): RummikubPractice = when {
        isComplete -> this
        !current.drawLesson -> copy(game = game.copy(notice = "이번에는 낼 수 있는 타일이 있습니다."))
        !game.isUntouched -> this
        else -> copy(game = game.draw(), isComplete = true)
    }

    /** 마지막 장면을 마치면 null이다. */
    fun next() = if (isLast) null else start(scene + 1)

    companion object {
        val SCENE_COUNT = Scenes.size

        fun start(scene: Int = 0) = RummikubPractice(scene, Scenes[scene].start())
    }
}
