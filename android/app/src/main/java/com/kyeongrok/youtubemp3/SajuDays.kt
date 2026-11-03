package com.kyeongrok.youtubemp3

import java.time.LocalDate

enum class DayMood { Good, Normal, Bad }

/** 그날이 한 사람에게 어떤 날인지다. [reasons]는 점수를 매긴 까닭이다. */
data class DayRating(val date: LocalDate, val pillar: Pillar, val score: Int, val reasons: List<String>) {
    val mood get() = when {
        score >= 2 -> DayMood.Good
        score <= -2 -> DayMood.Bad
        else -> DayMood.Normal
    }
}

/** 한 달의 기운이다. */
data class MonthFortune(val month: Int, val pillar: Pillar, val god: Int, val text: String, val note: String?)

/**
 * 날마다, 달마다 그 사람의 사주와 맞춰 본다. 그날 지지가 태어난 날의 지지(일지)와 합인지 충인지,
 * 그날 천간이 일간에게 어떤 십신인지로 간단히 점수를 매긴다.
 */
object SajuDays {
    /** 무난하게 좋은 일로 보는 십신: 식신, 정재, 정관, 정인. */
    private val GOOD_GODS = setOf(2, 5, 7, 9)

    /** 조심할 일로 보는 십신: 겁재, 편관. */
    private val HARD_GODS = setOf(1, 6)

    fun rate(chart: SajuChart, date: LocalDate): DayRating {
        val pillar = Saju.dayPillar(date)
        val reasons = mutableListOf<String>()
        var score = 0
        val god = Saju.tenGod(chart.dayStem, pillar.stem)
        when (god) {
            in GOOD_GODS -> {
                score += 1
                reasons += "${Saju.TEN_GODS[god]}일 · ${GOD_DAY[god]}"
            }
            in HARD_GODS -> {
                score -= 1
                reasons += "${Saju.TEN_GODS[god]}일 · ${GOD_DAY[god]}"
            }
            else -> reasons += "${Saju.TEN_GODS[god]}일 · ${GOD_DAY[god]}"
        }
        if (kotlin.math.abs(pillar.stem - chart.dayStem) == 5) {
            score += 1
            reasons += "그날 천간이 내 일간과 합을 이뤄 마음이 잘 통합니다."
        }
        when (Saju.branchRelation(pillar.branch, chart.day.branch)) {
            BranchRelation.Hap -> {
                score += 2
                reasons += "그날 지지가 내 일지와 육합이라 사람을 만나고 약속하기 좋습니다."
            }
            BranchRelation.Samhap -> {
                score += 1
                reasons += "그날 지지가 내 일지와 삼합이라 일이 순조롭게 모입니다."
            }
            BranchRelation.Chung -> {
                score -= 2
                reasons += "그날 지지가 내 일지와 충이라 이동·이사·다툼을 조심하세요."
            }
            BranchRelation.Wonjin -> {
                score -= 1
                reasons += "그날 지지가 내 일지와 원진이라 말 한마디에 마음이 상하기 쉽습니다."
            }
            else -> {}
        }
        if (Saju.branchRelation(pillar.branch, chart.year.branch) == BranchRelation.Chung) {
            score -= 1
            reasons += "그날이 내 띠와 부딪쳐(충) 집안일과 윗사람 일에 마찰이 생기기 쉽습니다."
        }
        return DayRating(date, pillar, score, reasons)
    }

    /** 그해 1~12월의 운세다. 달마다 15일이 속한 절기 달로 본다. */
    fun months(chart: SajuChart, year: Int): List<MonthFortune> = (1..12).map { month ->
        val pillar = Saju.monthPillar(LocalDate.of(year, month, 15))
        val god = Saju.tenGod(chart.dayStem, pillar.stem)
        val note = when (Saju.branchRelation(pillar.branch, chart.day.branch)) {
            BranchRelation.Hap -> "내 일지와 합을 이루는 달이라 인연과 협력이 잘 풀립니다."
            BranchRelation.Samhap -> "내 일지와 삼합인 달이라 주변의 힘을 모으기 좋습니다."
            BranchRelation.Chung -> "내 일지와 충인 달이라 변동이 많으니 큰 결정은 신중히 하세요."
            BranchRelation.Wonjin -> "내 일지와 원진인 달이라 가까운 사람과 서운한 일이 없게 살피세요."
            else -> null
        }
        MonthFortune(month, pillar, god, MONTH_TEXT[god], note)
    }

    /** 십신마다 그날의 짧은 풀이다. */
    private val GOD_DAY = listOf(
        "동료와 힘을 합치기 좋은 날입니다.",
        "경쟁과 지출이 생기기 쉬운 날입니다. 돈거래는 미루세요.",
        "마음이 여유롭고 먹을 복이 있는 날입니다.",
        "말과 표현이 날카로워지기 쉬운 날입니다.",
        "활동이 넓어지고 돈이 오가는 날입니다.",
        "성실함이 결과로 돌아오는 날입니다. 계약에도 무난합니다.",
        "부담과 압박이 커지는 날입니다. 무리하지 마세요.",
        "인정받기 좋은 날입니다. 공식적인 일에 좋습니다.",
        "직감이 날카로운 날입니다. 공부와 계획에 좋습니다.",
        "도움을 받기 좋은 날입니다. 문서와 계약에 좋습니다.",
    )

    /** 십신마다 그달의 풀이다. */
    private val MONTH_TEXT = listOf(
        "주변 사람과 어울릴 일이 많은 달입니다. 함께하면 힘이 되지만 씀씀이를 살피세요.",
        "경쟁이 붙는 달입니다. 욕심을 줄이고 돈거래와 보증은 피하세요.",
        "재능이 드러나고 마음이 여유로운 달입니다. 배우고 즐기기에 좋습니다.",
        "말에 힘이 실리는 달입니다. 아이디어를 살리되 윗사람과 부딪치지 않게 하세요.",
        "활동 범위가 넓어지는 달입니다. 기회가 오지만 무리한 투자는 조심하세요.",
        "노력한 만큼 거두는 달입니다. 차근차근 계획대로 하면 알찬 결과가 있습니다.",
        "부담이 커지는 달입니다. 건강과 안전을 먼저 챙기고 서두르지 마세요.",
        "인정받고 자리가 굳어지는 달입니다. 원칙을 지키면 좋은 평가가 따릅니다.",
        "생각이 깊어지는 달입니다. 공부와 연구에 좋지만 걱정은 덜어 내세요.",
        "도움과 배움이 있는 달입니다. 문서·계약·시험에 좋은 기운이 있습니다.",
    )
}
