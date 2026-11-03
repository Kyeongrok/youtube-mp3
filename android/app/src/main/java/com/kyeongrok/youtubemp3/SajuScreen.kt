package com.kyeongrok.youtubemp3

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val ElementFills = listOf(
    Color(0xFF2E7D32), Color(0xFFD32F2F), Color(0xFFB8860B), Color(0xFFBDBDBD), Color(0xFF263238),
)
private val ElementInks = listOf(Color.White, Color.White, Color.White, Color.Black, Color.White)
private val BoxShape = RoundedCornerShape(8.dp)
private const val NEW_PROFILE = -1L

/** 사주를 본 사람들을 앱 저장소에 둔다. */
object SajuProfiles {
    private const val FILE_NAME = "saju-profiles.json"

    fun load(context: Context): List<SajuProfile> = try {
        val array = RecordFile.read(context, FILE_NAME) ?: JSONArray()
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            SajuProfile(
                id = item.getLong("id"),
                name = item.getString("name"),
                male = item.getBoolean("male"),
                lunar = item.getBoolean("lunar"),
                leapMonth = item.getBoolean("leapMonth"),
                year = item.getInt("year"),
                month = item.getInt("month"),
                day = item.getInt("day"),
                minutes = if (item.isNull("minutes")) null else item.getInt("minutes"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(context: Context, profiles: List<SajuProfile>) {
        val array = JSONArray()
        profiles.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("male", it.male)
                    .put("lunar", it.lunar)
                    .put("leapMonth", it.leapMonth)
                    .put("year", it.year)
                    .put("month", it.month)
                    .put("day", it.day)
                    .put("minutes", it.minutes ?: JSONObject.NULL),
            )
        }
        RecordFile.write(context, FILE_NAME, array)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SajuTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("사주") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SajuScreen(viewModel: MainViewModel) {
    val profiles by viewModel.sajuProfiles.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    val editing = editingId ?: if (profiles.isEmpty()) NEW_PROFILE else null

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (profiles.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                profiles.forEach { profile ->
                    FilterChip(
                        selected = editing == null && profile.id == selected?.id,
                        onClick = {
                            selectedId = profile.id
                            editingId = null
                        },
                        label = { Text(profile.name) },
                    )
                }
                FilterChip(editing == NEW_PROFILE, { editingId = NEW_PROFILE }, { Text("+ 추가") })
            }
        }

        if (editing != null) {
            SajuForm(
                initial = profiles.firstOrNull { it.id == editing },
                canCancel = profiles.isNotEmpty(),
                onCancel = { editingId = null },
                onSave = { profile ->
                    viewModel.saveSajuProfile(profile)
                    selectedId = profile.id
                    editingId = null
                },
            )
        } else if (selected != null) {
            SajuResult(
                Saju.chart(selected),
                onEdit = { editingId = selected.id },
                onDelete = {
                    viewModel.deleteSajuProfile(selected.id)
                    selectedId = null
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SajuForm(
    initial: SajuProfile?,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onSave: (SajuProfile) -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var male by rememberSaveable(initial?.id) { mutableStateOf(initial?.male ?: true) }
    var lunar by rememberSaveable(initial?.id) { mutableStateOf(initial?.lunar ?: false) }
    var leap by rememberSaveable(initial?.id) { mutableStateOf(initial?.leapMonth ?: false) }
    var year by rememberSaveable(initial?.id) { mutableStateOf(initial?.year?.toString() ?: "") }
    var month by rememberSaveable(initial?.id) { mutableStateOf(initial?.month?.toString() ?: "") }
    var day by rememberSaveable(initial?.id) { mutableStateOf(initial?.day?.toString() ?: "") }
    var hour by rememberSaveable(initial?.id) { mutableStateOf(initial?.minutes?.let { (it / 60).toString() } ?: "") }
    var minute by rememberSaveable(initial?.id) { mutableStateOf(initial?.minutes?.let { (it % 60).toString() } ?: "") }
    var timeUnknown by rememberSaveable(initial?.id) { mutableStateOf(initial != null && initial.minutes == null) }
    var error by remember { mutableStateOf<String?>(null) }
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (initial == null) "생년월일시 입력" else "정보 고치기", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("이름") }, singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.Center) {
            FilterChip(male, { male = true }, { Text("남자") })
            FilterChip(!male, { male = false }, { Text("여자") })
            Box(Modifier.width(12.dp))
            FilterChip(!lunar, { lunar = false }, { Text("양력") })
            FilterChip(lunar, { lunar = true }, { Text("음력") })
            if (lunar) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(leap, { leap = it })
                    Text("윤달")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, Modifier.weight(1.4f), label = { Text("년") }, singleLine = true, keyboardOptions = number)
            OutlinedTextField(month, { month = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("월") }, singleLine = true, keyboardOptions = number)
            OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("일") }, singleLine = true, keyboardOptions = number)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("시 (0~23)") }, singleLine = true, keyboardOptions = number, enabled = !timeUnknown)
            OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("분") }, singleLine = true, keyboardOptions = number, enabled = !timeUnknown)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(timeUnknown, { timeUnknown = it })
                Text("모름")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canCancel) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
            }
            Button(
                onClick = {
                    val y = year.toIntOrNull()
                    val m = month.toIntOrNull()
                    val d = day.toIntOrNull()
                    val h = hour.toIntOrNull()
                    val min = minute.toIntOrNull() ?: 0
                    error = when {
                        y == null || m == null || d == null -> "생년월일을 입력하세요."
                        !timeUnknown && (h == null || h !in 0..23 || min !in 0..59) -> "태어난 시각을 0~23시, 0~59분으로 입력하거나 「모름」을 고르세요."
                        else -> null
                    }
                    if (error != null) return@Button
                    val profile = SajuProfile(
                        id = initial?.id ?: System.currentTimeMillis(),
                        name = name.trim().ifEmpty { "이름 없음" },
                        male = male,
                        lunar = lunar,
                        leapMonth = lunar && leap,
                        year = y!!,
                        month = m!!,
                        day = d!!,
                        minutes = if (timeUnknown) null else h!! * 60 + min,
                    )
                    error = Saju.problem(profile)
                    if (error == null) onSave(profile)
                },
                modifier = Modifier.weight(1f),
            ) { Text("사주 보기") }
        }
        Text(
            "태어난 시각은 서울의 실제 태양 시각으로 바꿔 계산합니다(약 32분 늦춤). 음력은 중국 기준 음력표를 써서 아주 드물게 하루 차이가 날 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SajuResult(chart: SajuChart, onEdit: () -> Unit, onDelete: () -> Unit) {
    val profile = chart.profile
    val colors = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("삭제") },
            text = { Text("${profile.name}의 사주를 지울까요?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text(
                "${profile.name} · ${if (profile.male) "남" else "여"} · ${Saju.ANIMALS[chart.year.branch]}띠",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            val time = profile.minutes?.let { " %d:%02d".format(it / 60, it % 60) } ?: " 시각 모름"
            val lunarText = if (profile.lunar) {
                " (음력 ${profile.year}.${profile.month}.${profile.day}${if (profile.leapMonth) " 윤달" else ""})"
            } else {
                ""
            }
            Text(
                "양력 ${chart.solarDate.year}.${chart.solarDate.monthValue}.${chart.solarDate.dayOfMonth}$time$lunarText",
                color = colors.onSurfaceVariant,
            )
        }

        PillarTable(chart)

        Section("타고난 성향") {
            Text(SajuText.DAY_STEMS[chart.dayStem])
        }

        Section("오행") {
            ElementBars(chart.elements)
            chart.elements.forEachIndexed { element, count ->
                when {
                    count == 0 -> Text("· ${SajuText.ELEMENT_NONE[element]}")
                    count >= 3 -> Text("· ${SajuText.ELEMENT_MANY[element]}")
                }
            }
            Text(if (chart.strong) SajuText.STRONG else SajuText.WEAK, Modifier.padding(top = 4.dp))
        }

        Section("대운 (10년마다 바뀌는 큰 흐름)") {
            val age = today.year - chart.solarDate.year
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chart.daeUn.forEachIndexed { index, daeUn ->
                    val next = chart.daeUn.getOrNull(index + 1)?.age ?: Int.MAX_VALUE
                    val current = age >= daeUn.age && age < next
                    Column(
                        Modifier
                            .border(if (current) 2.dp else 1.dp, if (current) colors.primary else colors.outlineVariant, BoxShape)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${daeUn.age}세", style = MaterialTheme.typography.labelMedium, color = if (current) colors.primary else Color.Unspecified)
                        Text(daeUn.pillar.hanja, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(Saju.TEN_GODS[Saju.tenGod(chart.dayStem, daeUn.pillar.stem)], style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(
                if (chart.forward) "순행 대운입니다." else "역행 대운입니다.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        val thisYear = Saju.yearPillar(today)
        val yearGod = Saju.tenGod(chart.dayStem, thisYear.stem)
        Section("올해 운세 · ${thisYear.hangul}(${thisYear.hanja})년 · ${Saju.TEN_GODS[yearGod]}") {
            Text(SajuText.FORTUNES[yearGod])
        }

        val todayPillar = Saju.dayPillar(today)
        val todayGod = Saju.tenGod(chart.dayStem, todayPillar.stem)
        Section("오늘의 운세 · ${today.monthValue}월 ${today.dayOfMonth}일 ${todayPillar.hangul}(${todayPillar.hanja})일 · ${Saju.TEN_GODS[todayGod]}") {
            Text(SajuText.FORTUNES[todayGod])
            SajuText.branchRelation(todayPillar.branch, chart.day.branch)?.let { Text(it) }
        }

        Section("십신 풀이") {
            chart.pillars.flatMap { pillar ->
                listOfNotNull(
                    if (pillar === chart.day) null else Saju.tenGod(chart.dayStem, pillar.stem),
                    Saju.branchTenGod(chart.dayStem, pillar.branch),
                )
            }.distinct().sorted().forEach { god ->
                Text("· ${Saju.TEN_GODS[god]}: ${SajuText.TEN_GOD_MEANINGS[god]}")
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("고치기") }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("삭제") }
        }
        Text(
            "전통 명리학의 기본 규칙으로 계산한 풀이입니다. 재미로 보세요.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** 시·일·월·년 네 기둥을 표로 그린다. 시를 모르면 그 칸을 비워 둔다. */
@Composable
private fun PillarTable(chart: SajuChart) {
    val columns = listOf("시주" to chart.hour, "일주" to chart.day, "월주" to chart.month, "년주" to chart.year)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        columns.forEach { (label, pillar) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val stemGod = when {
                    pillar == null -> ""
                    pillar === chart.day -> "일간(나)"
                    else -> Saju.TEN_GODS[Saju.tenGod(chart.dayStem, pillar.stem)]
                }
                Text(stemGod, style = MaterialTheme.typography.labelSmall)
                CharBox(pillar?.let { Saju.stemElement(it.stem) }, pillar?.let { Saju.STEM_HANJA[it.stem] }, pillar?.let { Saju.STEMS[it.stem] })
                CharBox(pillar?.let { Saju.BRANCH_ELEMENTS[it.branch] }, pillar?.let { Saju.BRANCH_HANJA[it.branch] }, pillar?.let { Saju.BRANCHES[it.branch] })
                Text(pillar?.let { Saju.TEN_GODS[Saju.branchTenGod(chart.dayStem, it.branch)] } ?: "", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CharBox(element: Int?, hanja: String?, hangul: String?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(element?.let { ElementFills[it] } ?: MaterialTheme.colorScheme.surfaceVariant, BoxShape),
        contentAlignment = Alignment.Center,
    ) {
        if (hanja == null || element == null) {
            Text("?", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(hanja, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = ElementInks[element])
                Text("$hangul · ${Saju.ELEMENTS[element]}", fontSize = 11.sp, color = ElementInks[element])
            }
        }
    }
}

@Composable
private fun ElementBars(counts: List<Int>) {
    val total = counts.sum().coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        counts.forEachIndexed { element, count ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${Saju.ELEMENTS[element]}(${Saju.ELEMENT_HANJA[element]})", Modifier.width(56.dp))
                Box(Modifier.weight(1f).height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, BoxShape)) {
                    if (count > 0) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(count.toFloat() / total)
                                .background(ElementFills[element], BoxShape),
                        )
                    }
                }
                Text("$count", Modifier.width(24.dp).padding(start = 6.dp))
            }
        }
    }
}
