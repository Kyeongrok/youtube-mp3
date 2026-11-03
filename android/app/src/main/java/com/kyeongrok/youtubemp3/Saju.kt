package com.kyeongrok.youtubemp3

import android.icu.util.ChineseCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import android.icu.util.TimeZone
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** 60갑자 중 하나다. 천간은 index % 10, 지지는 index % 12다. */
data class Pillar(val index: Int) {
    val stem get() = index % 10
    val branch get() = index % 12
    val hanja get() = "${Saju.STEM_HANJA[stem]}${Saju.BRANCH_HANJA[branch]}"
    val hangul get() = "${Saju.STEMS[stem]}${Saju.BRANCHES[branch]}"

    companion object {
        fun of(stem: Int, branch: Int) = Pillar((0 until 60).first { it % 10 == stem && it % 12 == branch })
    }
}

data class SajuProfile(
    val id: Long,
    val name: String,
    val male: Boolean,
    val lunar: Boolean,
    val leapMonth: Boolean,
    val year: Int,
    val month: Int,
    val day: Int,
    /** 태어난 시각을 모르면 null이다. 분 단위로 센다. */
    val minutes: Int?,
)

data class DaeUn(val age: Int, val pillar: Pillar)

data class SajuChart(
    val profile: SajuProfile,
    val solarDate: LocalDate,
    val year: Pillar,
    val month: Pillar,
    val day: Pillar,
    val hour: Pillar?,
    val forward: Boolean,
    val daeUn: List<DaeUn>,
) {
    val dayStem get() = day.stem

    /** 시·일·월·년 순서다. 시를 모르면 빠진다. */
    val pillars get() = listOfNotNull(hour, day, month, year)

    /** 오행마다 천간과 지지에 몇 번 나오는지 센다. */
    val elements: List<Int>
        get() {
            val counts = MutableList(5) { 0 }
            pillars.forEach {
                counts[Saju.stemElement(it.stem)]++
                counts[Saju.BRANCH_ELEMENTS[it.branch]]++
            }
            return counts
        }

    /**
     * 일간을 돕는 글자(같은 오행과 나를 낳는 오행)가 많으면 신강으로 본다.
     * 달의 지지는 계절을 정하므로 두 배로 친다. 간단한 판단이다.
     */
    val strong: Boolean
        get() {
            val me = Saju.stemElement(dayStem)
            val helps = { element: Int -> element == me || element == (me + 4) % 5 }
            var score = 0
            var total = 0
            fun add(element: Int, weight: Int) {
                total += weight
                if (helps(element)) score += weight
            }
            pillars.forEach { pillar ->
                if (pillar !== day) add(Saju.stemElement(pillar.stem), 1)
                add(Saju.BRANCH_ELEMENTS[pillar.branch], if (pillar === month) 2 else 1)
            }
            return score * 2 >= total
        }
}

object Saju {
    val STEMS = listOf("갑", "을", "병", "정", "무", "기", "경", "신", "임", "계")
    val STEM_HANJA = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    val BRANCHES = listOf("자", "축", "인", "묘", "진", "사", "오", "미", "신", "유", "술", "해")
    val BRANCH_HANJA = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    val ANIMALS = listOf("쥐", "소", "호랑이", "토끼", "용", "뱀", "말", "양", "원숭이", "닭", "개", "돼지")
    val ELEMENTS = listOf("목", "화", "토", "금", "수")
    val ELEMENT_HANJA = listOf("木", "火", "土", "金", "水")
    val BRANCH_ELEMENTS = listOf(4, 2, 0, 0, 2, 1, 1, 2, 3, 3, 2, 4)

    /** 지지 속 가장 힘센 천간(본기)이다. 십신을 셀 때 쓴다. */
    private val BRANCH_MAIN_STEMS = listOf(9, 5, 0, 1, 4, 2, 3, 5, 6, 7, 4, 8)

    val TEN_GODS = listOf("비견", "겁재", "식신", "상관", "편재", "정재", "편관", "정관", "편인", "정인")

    private val KOREA = ZoneId.of("Asia/Seoul")

    /** 서울(동경 127도)의 평균 태양시는 세계시보다 8시간 28분 빠르다. 시주와 날의 경계는 이 시각으로 정한다. */
    private const val SOLAR_OFFSET_MINUTES = 8 * 60 + 28
    private const val NOON = 12 * 60
    private const val YEAR_DAYS = 365.2422
    private const val DAY_MILLIS = 86_400_000.0
    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private val DAY_BASE = LocalDate.of(1900, 1, 1)

    /** 1900년 1월 1일은 갑술일(60갑자의 11번째)이다. */
    private const val DAY_BASE_INDEX = 10

    fun stemElement(stem: Int) = stem / 2

    fun isYang(stem: Int) = stem % 2 == 0

    /** 일간에서 본 다른 천간의 십신 번호다. */
    fun tenGod(dayStem: Int, other: Int): Int {
        val relation = (stemElement(other) - stemElement(dayStem) + 5) % 5
        val samePolarity = isYang(other) == isYang(dayStem)
        return relation * 2 + if (samePolarity) 0 else 1
    }

    fun branchTenGod(dayStem: Int, branch: Int) = tenGod(dayStem, BRANCH_MAIN_STEMS[branch])

    /** 음력 날짜를 양력으로 바꾼다. 없는 날짜면 null이다. 중국 기준 음력이라 아주 드물게 하루 다를 수 있다. */
    fun lunarToSolar(year: Int, month: Int, day: Int, leap: Boolean): LocalDate? {
        val calendar = ChineseCalendar(TimeZone.getTimeZone("UTC"), Locale.KOREA)
        calendar.clear()
        calendar.set(ChineseCalendar.EXTENDED_YEAR, year + 2637)
        calendar.set(ChineseCalendar.MONTH, month - 1)
        calendar.set(ChineseCalendar.IS_LEAP_MONTH, if (leap) 1 else 0)
        calendar.set(ChineseCalendar.DAY_OF_MONTH, day)
        val millis = calendar.timeInMillis
        val valid = calendar.get(ChineseCalendar.MONTH) == month - 1 &&
            calendar.get(ChineseCalendar.DAY_OF_MONTH) == day &&
            (calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1) == leap
        return if (valid) Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate() else null
    }

    /** 입력이 잘못되면 그 까닭을, 맞으면 null을 돌려준다. */
    fun problem(profile: SajuProfile): String? {
        if (profile.year !in 1900..2100) return "1900년부터 2100년까지만 볼 수 있습니다."
        if (profile.lunar) {
            if (lunarToSolar(profile.year, profile.month, profile.day, profile.leapMonth) == null) {
                return if (profile.leapMonth) "그해에는 그 윤달이 없습니다." else "음력에 없는 날짜입니다."
            }
        } else {
            runCatching { LocalDate.of(profile.year, profile.month, profile.day) }
                .onFailure { return "없는 날짜입니다." }
        }
        if (profile.minutes != null && profile.minutes !in 0 until 24 * 60) return "시각이 올바르지 않습니다."
        return null
    }

    fun chart(profile: SajuProfile): SajuChart {
        val date = if (profile.lunar) {
            lunarToSolar(profile.year, profile.month, profile.day, profile.leapMonth)!!
        } else {
            LocalDate.of(profile.year, profile.month, profile.day)
        }
        // 시각을 모르면 낮 12시로 보고 년·월·일만 쓴다.
        val clock = LocalDateTime.of(date, LocalTime.ofSecondOfDay((profile.minutes ?: NOON) * 60L))
        val instant = clock.atZone(KOREA).toInstant()
        val solar = LocalDateTime.ofInstant(instant, ZoneOffset.UTC).plusMinutes(SOLAR_OFFSET_MINUTES.toLong())

        val longitude = sunLongitude(julianDay(instant))
        val monthOrder = monthOrder(longitude)
        // 1~2월이면서 아직 입춘 전(자월·축월)이면 지난해로 친다.
        val sajuYear = if (date.monthValue <= 2 && monthOrder >= 10) date.year - 1 else date.year
        val year = Pillar(Math.floorMod(sajuYear - 4, 60))
        val monthStem = (year.stem % 5 * 2 + 2 + monthOrder) % 10
        val month = Pillar.of(monthStem, (monthOrder + 2) % 12)

        // 자시(23시~1시)가 시작되면 다음 날로 넘긴다.
        val solarMinutes = solar.hour * 60 + solar.minute
        val dayDate = if (solarMinutes >= 23 * 60) solar.toLocalDate().plusDays(1) else solar.toLocalDate()
        val day = dayPillar(dayDate)
        val hour = profile.minutes?.let {
            val branch = ((solarMinutes + 60) / 120) % 12
            Pillar.of((day.stem % 5 * 2 + branch) % 10, branch)
        }

        val forward = isYang(year.stem) == profile.male
        val startAge = daeUnStart(instant, longitude, forward)
        val daeUn = (1..8).map { step ->
            DaeUn(startAge + (step - 1) * 10, Pillar(Math.floorMod(month.index + if (forward) step else -step, 60)))
        }
        return SajuChart(profile, date, year, month, day, hour, forward, daeUn)
    }

    fun dayPillar(date: LocalDate) =
        Pillar(Math.floorMod(DAY_BASE_INDEX + ChronoUnit.DAYS.between(DAY_BASE, date), 60L).toInt())

    /** 오늘이 속한 해의 간지다. 입춘을 기준으로 바꾼다. */
    fun yearPillar(date: LocalDate): Pillar {
        val instant = date.atTime(NOON / 60, 0).atZone(KOREA).toInstant()
        val order = monthOrder(sunLongitude(julianDay(instant)))
        val year = if (date.monthValue <= 2 && order >= 10) date.year - 1 else date.year
        return Pillar(Math.floorMod(year - 4, 60))
    }

    /** 입춘(태양 황경 315도)부터 30도마다 달이 바뀐다. 0이 인월이고 11이 축월이다. */
    private fun monthOrder(longitude: Double) = floor(((longitude - 315 + 360) % 360) / 30).toInt()

    /**
     * 대운수다. 순행이면 다음 절기까지, 역행이면 지난 절기까지 날수를 3으로 나눠 반올림한다.
     */
    private fun daeUnStart(birth: Instant, longitude: Double, forward: Boolean): Int {
        val order = monthOrder(longitude)
        val target = 315.0 + 30 * (if (forward) order + 1 else order)
        val birthJd = julianDay(birth)
        var jd = birthJd
        repeat(8) {
            val gap = ((target - sunLongitude(jd)) % 360 + 540) % 360 - 180
            jd += gap * YEAR_DAYS / 360
        }
        val days = kotlin.math.abs(jd - birthJd)
        return (days / 3).roundToInt().coerceIn(1, 10)
    }

    private fun julianDay(instant: Instant) = instant.toEpochMilli() / DAY_MILLIS + UNIX_EPOCH_JD

    /** 태양의 겉보기 황경(도)이다. Meeus의 간단한 식으로 0.01도쯤 정확하다. */
    private fun sunLongitude(jd: Double): Double {
        val t = (jd - J2000) / 36525
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = Math.toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
            (0.019993 - 0.000101 * t) * sin(2 * m) + 0.000289 * sin(3 * m)
        val omega = Math.toRadians(125.04 - 1934.136 * t)
        val apparent = l0 + c - 0.00569 - 0.00478 * sin(omega)
        return (apparent % 360 + 360) % 360
    }
}
