package com.kyeongrok.youtubemp3

import android.speech.tts.TextToSpeech
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HangulTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("한글 따라 쓰기") },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HangulScreen() {
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

    var index by rememberSaveable { mutableIntStateOf(0) }
    val finished = remember { mutableStateListOf<Int>() }
    val letter = Hangul.LETTERS[index]
    var stroke by rememberSaveable { mutableIntStateOf(0) }
    var progress by rememberSaveable { mutableFloatStateOf(0f) }
    val done = stroke >= letter.strokes.size

    fun restart() {
        stroke = 0
        progress = 0f
    }

    fun move(to: Int) {
        index = to.coerceIn(0, Hangul.LETTERS.lastIndex)
        restart()
    }

    fun trace(finger: Offset) {
        val letter = Hangul.LETTERS[index]
        if (stroke >= letter.strokes.size) return
        val current = letter.strokes[stroke]
        val reached = current.advance(finger, progress, WINDOW, TOLERANCE) ?: return
        if (reached > progress) progress = reached
        if (progress >= current.length - 0.02f) {
            stroke++
            progress = 0f
            if (stroke == letter.strokes.size) {
                if (index !in finished) finished += index
                say(letter.text)
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
                Hangul.LETTERS[i].text,
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
                    enabled = index < Hangul.LETTERS.lastIndex,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 글자")
                }
            }
        }
    }

    @Composable
    fun Status(modifier: Modifier, gap: String) {
        Text(
            if (done) "참 잘했어요!$gap「${letter.text}」" else
                "${Hangul.JAMO[index]}+ㅏ$gap${letter.strokes.size}획 중 ${stroke + 1}번째 획",
            modifier,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF263238),
            textAlign = if (gap == "\n") TextAlign.Center else TextAlign.Start,
        )
    }

    @Composable
    fun Buttons() {
        OutlinedButton(onClick = { say(letter.text) }) { Text("듣기") }
        OutlinedButton(onClick = ::restart) { Text("다시") }
        if (done && index < Hangul.LETTERS.lastIndex) {
            Button(onClick = { move(index + 1) }) { Text("다음") }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(SeaBottom)) {
        if (maxWidth > maxHeight) {
            // 가로 화면: 글자 고르기는 왼쪽, 버튼은 오른쪽에 두어 글자 칸을 크게 쓴다.
            Row(Modifier.fillMaxSize()) {
                FlowRow(
                    Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxItemsInEachRow = 2,
                ) {
                    Hangul.LETTERS.indices.forEach { Chip(it) }
                }
                Board(Modifier.weight(1f).fillMaxHeight())
                Column(
                    Modifier.width(150.dp).fillMaxHeight().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Status(Modifier, "\n")
                    Buttons()
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Hangul.LETTERS.indices.forEach { Chip(it) }
                }
                Board(Modifier.weight(1f).fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Status(Modifier.weight(1f), "  ")
                    Buttons()
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
