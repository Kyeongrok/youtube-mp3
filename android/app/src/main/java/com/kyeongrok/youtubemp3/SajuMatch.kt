package com.kyeongrok.youtubemp3

/** 궁합 풀이의 한 항목이다. [points]는 점수에 더하거나 빼는 값이다. */
data class MatchItem(val title: String, val text: String, val points: Int)

data class MatchResult(val score: Int, val grade: String, val summary: String, val items: List<MatchItem>)

/**
 * 두 사람의 사주로 궁합을 본다. 일간(나 자신), 일지(배우자 자리), 띠, 오행 보완을 차례로 살펴
 * 기본 점수에 더하고 뺀다. 전통 궁합의 대표 규칙만 쓴 간단한 풀이다.
 */
object SajuMatch {
    private const val BASE = 55

    /** 받침이 있으면 "과", 없으면 "와"를 붙인다. */
    private fun withAnd(word: String): String {
        val c = word.last()
        val batchim = c in '가'..'힣' && (c - '가') % 28 != 0
        return word + if (batchim) "과" else "와"
    }


    fun compute(a: SajuChart, b: SajuChart): MatchResult {
        val nameA = a.profile.name
        val nameB = b.profile.name
        val items = buildList {
            add(stemItem(a.dayStem, b.dayStem, nameA, nameB))
            branchItem(a.day.branch, b.day.branch)?.let(::add)
            yearItem(a.year.branch, b.year.branch)?.let(::add)
            addAll(elementItems(a, b))
        }
        val score = (BASE + items.sumOf { it.points }).coerceIn(20, 99)
        val (grade, summary) = when {
            score >= 85 -> "천생연분" to "타고난 기운이 서로 잘 맞물리는 아주 좋은 궁합입니다. 함께 있으면 서로를 키워 줍니다."
            score >= 70 -> "잘 맞는 궁합" to "서로 끌리는 점이 많고 함께하면 편안한 사이입니다. 작은 차이만 존중하면 됩니다."
            score >= 55 -> "무난한 궁합" to "좋은 점과 부딪치는 점이 고루 있습니다. 서로 맞춰 가는 만큼 좋아지는 사이입니다."
            score >= 40 -> "노력이 필요한 궁합" to "성향 차이가 큰 편이라 오해가 생기기 쉽습니다. 대화와 배려가 관계를 지켜 줍니다."
            else -> "많이 다른 두 사람" to "기운이 자주 엇갈리는 사이입니다. 다른 점을 장점으로 여기면 서로에게 없는 것을 배울 수 있습니다."
        }
        return MatchResult(score, grade, summary, items)
    }

    private fun stemItem(sa: Int, sb: Int, nameA: String, nameB: String): MatchItem {
        val ea = Saju.stemElement(sa)
        val eb = Saju.stemElement(sb)
        val title = "일간 · ${withAnd(Saju.STEMS[sa] + Saju.ELEMENTS[ea])} ${Saju.STEMS[sb]}${Saju.ELEMENTS[eb]}"
        return when {
            kotlin.math.abs(sa - sb) == 5 -> MatchItem(
                title, "두 사람의 일간이 천간합(合)을 이룹니다. 처음부터 서로 끌리고 마음이 잘 통하는 대표적인 좋은 인연입니다.", 25,
            )
            ea == eb -> MatchItem(
                title, "같은 오행이라 친구처럼 편하고 공감이 잘 됩니다. 다만 고집도 닮아서 한발씩 양보하는 것이 좋습니다.", 8,
            )
            (eb - ea + 5) % 5 == 1 -> MatchItem(
                title, "${nameA}님이 ${nameB}님을 돕고 키워 주는 상생 관계입니다. ${nameA}님이 베풀고 ${nameB}님이 기대며 정이 쌓입니다.", 12,
            )
            (ea - eb + 5) % 5 == 1 -> MatchItem(
                title, "${nameB}님이 ${nameA}님을 돕고 키워 주는 상생 관계입니다. ${nameB}님이 베풀고 ${nameA}님이 기대며 정이 쌓입니다.", 12,
            )
            (eb - ea + 5) % 5 == 2 -> MatchItem(
                title, "${nameA}님의 기운이 ${nameB}님을 누르는 상극 관계입니다. ${nameA}님의 말이 간섭처럼 들리지 않게 존중이 필요합니다.", -6,
            )
            else -> MatchItem(
                title, "${nameB}님의 기운이 ${nameA}님을 누르는 상극 관계입니다. ${nameB}님의 말이 간섭처럼 들리지 않게 존중이 필요합니다.", -6,
            )
        }
    }


    private fun branchItem(x: Int, y: Int): MatchItem? {
        val title = "일지(배우자 자리) · ${withAnd(Saju.BRANCHES[x])} ${Saju.BRANCHES[y]}"
        return when (Saju.branchRelation(x, y)) {
            BranchRelation.Hap -> MatchItem(title, "일지가 육합을 이룹니다. 함께 살면 편안하고 시간이 갈수록 정이 깊어지는 궁합입니다.", 20)
            BranchRelation.Samhap -> MatchItem(title, "일지가 삼합으로 묶입니다. 같은 목표를 향해 힘을 모으기 좋은 동반자입니다.", 12)
            BranchRelation.Same -> MatchItem(title, "일지가 같아 생활 방식과 취향이 비슷합니다. 익숙한 만큼 새로움을 함께 찾아보세요.", 5)
            BranchRelation.Chung -> MatchItem(title, "일지가 서로 부딪칩니다(충). 생활 습관이나 생각 차이로 다투기 쉬우니 그때그때 대화로 푸세요.", -18)
            BranchRelation.Wonjin -> MatchItem(title, "일지가 원진 관계입니다. 이유 없이 서운한 마음이 들기 쉬우니 말을 부드럽게 하고 고마움을 자주 표현하세요.", -10)
            else -> null
        }
    }

    private fun yearItem(x: Int, y: Int): MatchItem? {
        val title = "띠 · ${Saju.ANIMALS[x]}띠와 ${Saju.ANIMALS[y]}띠"
        return when (Saju.branchRelation(x, y)) {
            BranchRelation.Hap -> MatchItem(title, "육합을 이루는 띠라 서로 잘 어울립니다.", 8)
            BranchRelation.Samhap -> MatchItem(title, "삼합을 이루는 띠라 뜻이 잘 맞습니다.", 8)
            BranchRelation.Same -> MatchItem(title, "같은 띠라 생각하는 방식이 닮았습니다.", 3)
            BranchRelation.Chung -> MatchItem(title, "서로 부딪치는 띠(충)라 의견이 엇갈리기 쉽습니다.", -8)
            BranchRelation.Wonjin -> MatchItem(title, "원진 관계의 띠라 사소한 일로 마음이 상하기 쉽습니다.", -5)
            else -> null
        }
    }

    /** 한 사람에게 없는 오행을 다른 사람이 넉넉히 가졌으면 보완해 주는 것으로 본다. */
    private fun elementItems(a: SajuChart, b: SajuChart): List<MatchItem> {
        val nameA = a.profile.name
        val nameB = b.profile.name
        val fills = mutableListOf<String>()
        val bothLack = mutableListOf<String>()
        for (element in 0 until 5) {
            val ca = a.elements[element]
            val cb = b.elements[element]
            val label = "${Saju.ELEMENTS[element]}(${Saju.ELEMENT_HANJA[element]})"
            when {
                ca == 0 && cb == 0 -> bothLack += label
                ca == 0 && cb >= 2 -> fills += "${nameA}님에게 없는 $label 기운을 ${nameB}님이 채워 줍니다."
                cb == 0 && ca >= 2 -> fills += "${nameB}님에게 없는 $label 기운을 ${nameA}님이 채워 줍니다."
            }
        }
        return buildList {
            if (fills.isNotEmpty()) {
                add(MatchItem("오행 보완", fills.joinToString(" ") + " 서로의 빈자리를 메워 주는 사이입니다.", (fills.size * 6).coerceAtMost(15)))
            }
            if (bothLack.isNotEmpty()) {
                add(MatchItem("함께 부족한 오행", "두 사람 모두 ${bothLack.joinToString(", ")} 기운이 없습니다. 함께 있을 때 이 부분이 더 약해질 수 있으니 의식해서 채워 보세요.", -4 * bothLack.size))
            }
        }
    }
}
