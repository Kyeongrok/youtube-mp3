package com.kyeongrok.youtubemp3

import android.speech.tts.TextToSpeech
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SeaTop = Color(0xFF4FC3F7)
private val SeaBottom = Color(0xFF81D4FA)
private val Wave = Color(0x33FFFFFF)
private val Ink = Color(0xFFFF7043)
private val Guide = Color(0xFFE53935)
private val StarColor = Color(0xFFFFA000)

/** 글자 칸 기준 굵기와 허용 거리다. */
private const val TRACK_WIDTH = 0.13f
private const val INK_WIDTH = 0.085f
private const val TOLERANCE = 0.1f
private const val WINDOW = 0.18f
private const val ARROW_GAP = 0.09f

@Composable
fun HangulTopBar(onBack: () -> Unit) = TracingTopBar("한글 배우기", onBack)

@Composable
fun NumberTopBar(onBack: () -> Unit) = TracingTopBar("숫자 공부", onBack)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TracingTopBar(title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
        actions = {
            TextButton(onClick = { KidsMusic.toggle(context) }) {
                Text(if (KidsMusic.enabled) "음악 끄기" else "음악 켜기")
            }
        },
    )
}

/** 아이가 쓰고 싶은 낱말. 기기에 저장하고, 처음에는 예시 낱말을 넣어 둔다. */
private object HangulWords {
    private const val PREFS = "hangulWords"
    private const val KEY = "words"
    private const val DEFAULT = "똥\n응가\n쉬\n방구\n고양이"
    const val MAX_LENGTH = 8

    fun load(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DEFAULT).orEmpty()
            .split("\n").filter { it.isNotBlank() }

    fun save(context: Context, words: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, words.joinToString("\n")).apply()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HangulScreen() {
    BackgroundMusic()
    val context = LocalContext.current
    var words by remember { mutableStateOf(HangulWords.load(context)) }
    var word by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val current = word?.takeIf { it in words }
    val letters = remember(current) { current?.map(Hangul::letter) ?: Hangul.LETTERS }

    if (adding) {
        AddWordDialog(
            onAdd = { added ->
                adding = false
                if (added !in words) {
                    words = words + added
                    HangulWords.save(context, words)
                }
                word = added
            },
            onDismiss = { adding = false },
        )
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("낱말 지우기") },
            text = { Text("「$target」을(를) 목록에서 지울까요?") },
            confirmButton = {
                TextButton(onClick = {
                    words = words - target
                    HangulWords.save(context, words)
                    if (word == target) word = null
                    deleting = null
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("취소") } },
        )
    }

    @Composable
    fun Pill(text: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) Color(0xFF7E57C2) else Color.White)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color(0xFF37474F),
            )
        }
    }

    TracingScreen(
        id = current ?: "",
        letters = letters,
        hint = { Hangul.jamoHint(letters[it].text[0]) },
        reading = { if (current != null && it == letters.lastIndex) "\n「$current」 완성!" else "" },
        spoken = { if (current != null && it == letters.lastIndex) current else letters[it].text },
        top = {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Pill("가나다", current == null, onClick = { word = null })
                words.forEach { w ->
                    Pill(w, w == current, onClick = { word = w }, onLongClick = { deleting = w })
                }
                Pill("+ 낱말", false, onClick = { adding = true })
            }
        },
    )
}

@Composable
private fun AddWordDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val word = text.trim().replace(" ", "")
    val valid = word.isNotEmpty() && word.length <= HangulWords.MAX_LENGTH && word.all(Hangul::isSyllable)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("낱말 넣기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("예: 하츄핑") },
                )
                Text(
                    when {
                        word.isEmpty() -> "따라 쓸 낱말을 적어 주세요. 지울 때는 낱말을 길게 누르면 됩니다."
                        !word.all(Hangul::isSyllable) -> "완성된 한글 글자만 쓸 수 있습니다."
                        word.length > HangulWords.MAX_LENGTH -> "${HangulWords.MAX_LENGTH}글자까지 넣을 수 있습니다."
                        else -> "${word.length}글자를 차례로 따라 씁니다."
                    },
                    fontSize = 14.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(word) }, enabled = valid) { Text("넣기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
fun NumberScreen() {
    BackgroundMusic()
    TracingScreen(
        letters = Numbers.LETTERS,
        hint = { Numbers.READINGS[it] },
        reading = { " " + Numbers.READINGS[it] },
        spoken = { Numbers.READINGS[it].replace(" · ", ", ") },
    )
}

/** 배경음악. 화면이 보이는 동안 켜져 있을 때만 튼다. 이미 노래를 듣고 있으면 틀지 않는다. */
@Composable
private fun BackgroundMusic() {
    val context = LocalContext.current
    val music = remember { KidsMusic() }
    LaunchedEffect(Unit) {
        KidsMusic.load(context)
        withContext(Dispatchers.Default) { music.prepare() }
    }
    DisposableEffect(Unit) { onDispose { music.release() } }
    val enabled = KidsMusic.enabled
    LifecycleResumeEffect(enabled) {
        if (enabled && !Player.state.value.isPlaying) music.play() else music.pause()
        onPauseOrDispose { music.pause() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TracingScreen(
    letters: List<HangulLetter>,
    hint: (Int) -> String,
    reading: (Int) -> String,
    spoken: (Int) -> String,
    /** 글자 목록이 바뀌면 처음 글자부터 다시 시작한다. */
    id: String = "",
    top: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val speech = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) engine?.language = Locale.KOREAN
        }
        speech[0] = engine
        onDispose {
            engine.shutdown()
            speech[0] = null
        }
    }
    fun say(text: String) {
        speech[0]?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "hangul")
    }

    var index by rememberSaveable(id) { mutableIntStateOf(0) }
    val finished = remember(id) { mutableStateListOf<Int>() }
    val letter = letters[index]
    var stroke by rememberSaveable(id) { mutableIntStateOf(0) }
    var progress by rememberSaveable(id) { mutableFloatStateOf(0f) }
    val done = stroke >= letter.strokes.size

    fun restart() {
        stroke = 0
        progress = 0f
    }

    fun move(to: Int) {
        index = to.coerceIn(0, letters.lastIndex)
        restart()
    }

    fun trace(finger: Offset) {
        val letter = letters[index]
        if (stroke >= letter.strokes.size) return
        val current = letter.strokes[stroke]
        val reached = current.advance(finger, progress, WINDOW, TOLERANCE) ?: return
        if (reached > progress) progress = reached
        if (progress >= current.length - 0.02f) {
            stroke++
            progress = 0f
            if (stroke == letter.strokes.size) {
                if (index !in finished) finished += index
                say(spoken(index))
            }
        }
    }

    @Composable
    fun Chip(i: Int) {
        val selected = i == index
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected -> Color(0xFF7E57C2)
                        i in finished -> Color(0xFFFFCC80)
                        else -> Color.White
                    },
                )
                .clickable { move(i) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                letters[i].text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else Color(0xFF37474F),
            )
        }
    }

    @Composable
    fun Board(modifier: Modifier) {
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val side = min(maxWidth - 96.dp, maxHeight - 16.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { move(index - 1) }, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "앞 글자")
                }
                TracingBoard(
                    letter = letter,
                    stroke = stroke,
                    progress = progress,
                    onTrace = ::trace,
                    modifier = Modifier.padding(horizontal = 4.dp).size(side)
                        .clip(RoundedCornerShape(24.dp)),
                )
                FilledTonalIconButton(
                    onClick = { move(index + 1) },
                    enabled = index < letters.lastIndex,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 글자")
                }
            }
        }
    }

    @Composable
    fun Status(modifier: Modifier, gap: String) {
        Text(
            if (done) "참 잘했어요!$gap「${letter.text}」${reading(index)}" else
                "${hint(index)}$gap${letter.strokes.size}획 중 ${stroke + 1}번째 획",
            modifier,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF263238),
            textAlign = if (gap == "\n") TextAlign.Center else TextAlign.Start,
        )
    }

    @Composable
    fun SmallButtons(modifier: Modifier) {
        OutlinedButton(onClick = { say(spoken(index)) }, modifier.height(56.dp)) { Text("듣기", fontSize = 18.sp) }
        OutlinedButton(onClick = ::restart, modifier.height(56.dp)) { Text("다시", fontSize = 18.sp) }
    }

    @Composable
    fun NextButton(modifier: Modifier) {
        Button(
            onClick = { move(index + 1) },
            modifier = modifier.height(64.dp),
            enabled = done && index < letters.lastIndex,
        ) {
            Text("다음 ▶", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(SeaBottom)) {
        if (maxWidth > maxHeight) {
            // 가로 화면: 글자 고르기는 왼쪽, 버튼은 오른쪽에 두어 글자 칸을 크게 쓴다.
            Column(Modifier.fillMaxSize()) {
            top()
            Row(Modifier.fillMaxSize()) {
                FlowRow(
                    Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxItemsInEachRow = 2,
                ) {
                    letters.indices.forEach { Chip(it) }
                }
                Board(Modifier.weight(1f).fillMaxHeight())
                Column(
                    Modifier.width(190.dp).fillMaxHeight().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Status(Modifier, "\n")
                    SmallButtons(Modifier.fillMaxWidth())
                    NextButton(Modifier.fillMaxWidth())
                }
            }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                top()
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    letters.indices.forEach { Chip(it) }
                }
                Board(Modifier.weight(1f).fillMaxWidth())
                Status(Modifier.fillMaxWidth().padding(horizontal = 12.dp), "  ")
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallButtons(Modifier)
                    NextButton(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TracingBoard(
    letter: HangulLetter,
    stroke: Int,
    progress: Float,
    onTrace: (Offset) -> Unit,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val trace by rememberUpdatedState(onTrace)
    Canvas(
        modifier
            .pointerInput(letter) {
                detectDragGestures(
                    onDragStart = { trace(it / size.width.toFloat()) },
                    onDrag = { change, _ -> trace(change.position / size.width.toFloat()) },
                )
            }
            .pointerInput(letter) {
                detectTapGestures { trace(it / size.width.toFloat()) }
            },
    ) {
        val unit = size.width
        drawSea()
        fun path(points: List<Offset>) = Path().apply {
            points.forEachIndexed { i, p ->
                if (i == 0) moveTo(p.x * unit, p.y * unit) else lineTo(p.x * unit, p.y * unit)
            }
        }
        val track = Stroke(TRACK_WIDTH * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val ink = Stroke(INK_WIDTH * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)

        letter.strokes.forEach { drawPath(path(it.points), Color.White, style = track) }
        letter.strokes.take(stroke).forEach { drawPath(path(it.points), Ink, style = ink) }
        val current = letter.strokes.getOrNull(stroke) ?: return@Canvas

        if (progress > 0f) drawPath(path(current.pointsUntil(progress)), Ink, style = ink)

        // 남은 길에 진행 방향 화살표를 놓는다.
        var s = progress + ARROW_GAP
        while (s < current.length - ARROW_GAP * 0.6f) {
            val a = current.pointAt(s - 0.01f)
            val b = current.pointAt(s + 0.01f)
            arrow(current.pointAt(s) * unit, b - a, unit * 0.022f)
            s += ARROW_GAP
        }
        star(current.pointAt(current.length) * unit, unit * 0.045f)

        // 펜 끝: 지금 쓰는 자리와 획 번호.
        val pen = current.pointAt(progress) * unit
        drawCircle(Color(0xFF7E57C2), unit * 0.04f, pen)
        drawCircle(Color.White, unit * 0.04f, pen, style = Stroke(unit * 0.008f))
        val label = measurer.measure(
            "${stroke + 1}",
            TextStyle(color = Color.White, fontSize = (unit * 0.04f / density).sp, fontWeight = FontWeight.Bold),
        )
        drawText(label, topLeft = pen - Offset(label.size.width / 2f, label.size.height / 2f))
    }
}

private fun DrawScope.drawSea() {
    drawRect(Brush.verticalGradient(listOf(SeaTop, SeaBottom)))
    val step = size.width / 8
    for (row in 0..4) {
        val y = size.height * (0.15f + row * 0.2f)
        val wave = Path().apply {
            moveTo(0f, y)
            var x = 0f
            var up = true
            while (x < size.width) {
                x += step / 2
                lineTo(x, if (up) y - step / 3 else y)
                up = !up
            }
            lineTo(size.width, y + step / 2)
            lineTo(0f, y + step / 2)
            close()
        }
        drawPath(wave, Wave)
    }
}

/** 방향 [direction]을 가리키는 작은 꺾쇠다. */
private fun DrawScope.arrow(center: Offset, direction: Offset, half: Float) {
    val length = direction.getDistance()
    if (length == 0f) return
    val d = direction / length
    val n = Offset(-d.y, d.x)
    val tip = center + d * half
    val path = Path().apply {
        moveTo((center - d * half + n * half).x, (center - d * half + n * half).y)
        lineTo(tip.x, tip.y)
        lineTo((center - d * half - n * half).x, (center - d * half - n * half).y)
    }
    drawPath(path, Guide, style = Stroke(half * 0.55f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.star(center: Offset, radius: Float) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val angle = -PI / 2 + i * PI / 5
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, StarColor)
    drawPath(path, Color(0xFFE65100), style = Stroke(radius * 0.12f, join = StrokeJoin.Round))
}
