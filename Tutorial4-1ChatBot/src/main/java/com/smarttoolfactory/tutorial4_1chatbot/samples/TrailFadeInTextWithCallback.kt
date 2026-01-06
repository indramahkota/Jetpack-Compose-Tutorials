package com.smarttoolfactory.tutorial4_1chatbot.samples

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val deltas = listOf(
    "defined ", "by volatility, complexity", ", and accelerating\n",
    "change. Markets evolve ", "faster than planning\n",
    "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. ",
    " cycles ", "customer", " expectations shift", " continuously."
)

@Preview
@Composable
private fun TrailFadeInParallelWithCallbackPreview() {
    var text by remember {
        mutableStateOf("")
    }

    var chunkText by remember {
        mutableStateOf("")
    }

    var startAnimation by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(startAnimation) {
        delay(1000)
        deltas.toWordFlow(
            delayMillis = 60,
            wordsPerEmission = 3
        ).collect {
//            println("Collect: $it")
            chunkText += it
        }
    }

    Column(modifier = Modifier.systemBarsPadding()) {


        val context = LocalContext.current

        Text("TrailFadeInTextWithCallback", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        TrailFadeInTextWithCallback(
            text = chunkText,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            onTailRectComplete = {
                Toast.makeText(context, "onBatchComplete", Toast.LENGTH_SHORT).show()
            }
        )


        Spacer(modifier = Modifier.weight(1f))

        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            onClick = {
                startAnimation = startAnimation.not()
                chunkText = ""
            }
        ) {
            Text("Reset")
        }

        OutlinedTextField(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            value = text,
            onValueChange = {
                text = it
            }
        )
        Button(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            onClick = {
                chunkText += text
            }
        ) {
            Text("Append")
        }
    }
}

@Composable
private fun TrailFadeInTextWithCallback(
    modifier: Modifier = Modifier,
    text: String,
    start: Int = 0,
    end: Int = text.lastIndex,
    style: TextStyle = TextStyle.Default,
    onTailRectComplete: (() -> Unit)? = null
) {

    var startIndex by remember { mutableIntStateOf(start) }
    var endIndex by remember { mutableIntStateOf(end) }

    val rectList = remember { mutableStateListOf<RectWithAnimatable>() }

    val rectChannel = remember {
        Channel<List<RectWithAnimatable>>(capacity = Channel.UNLIMITED)
    }

    val jobsById = remember { mutableStateMapOf<String, Job>() }

    // Tail index as of most recent onTextLayout
    var latestTailIndex by remember { mutableIntStateOf(-1) }

    // last tail index we already notified for
    var lastNotifiedTailIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        for (batch in rectChannel) {
            batch.forEachIndexed { index, rectWithAnimatable: RectWithAnimatable ->
                if (jobsById.containsKey(rectWithAnimatable.id)) return@forEachIndexed

                val job = launch {
                    delay(20L * index)
                    try {
                        val rectWidth = (1.8f * rectWithAnimatable.rect.width).toInt()
                        rectWithAnimatable.animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(1000, easing = LinearEasing)
                        )
                        delay(60)
                    } finally {
                        jobsById.remove(rectWithAnimatable.id)

                        val tail = latestTailIndex
                        val rectWidth = rectWithAnimatable.rect.width.toInt()

                        println(
                            "Finally start: ${rectWithAnimatable.charStart}, " +
                                    "end:${rectWithAnimatable.charEnd}, " +
                                    "rectWidth:${rectWidth}, " +
                                    "tail: $tail, " +
                                    "lastNotifiedIndex: $lastNotifiedTailIndex"
                        )

                        if (tail >= 0 &&
                            rectWithAnimatable.covers(tail) &&
                            tail != lastNotifiedTailIndex
                        ) {
                            lastNotifiedTailIndex = tail
                            onTailRectComplete?.invoke()
                        }
                    }
                }

                jobsById[rectWithAnimatable.id] = job
            }
        }
    }

    Text(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawFadeInRects(rectList)
            },
        onTextLayout = { layout ->
            endIndex = text.lastIndex
            latestTailIndex = endIndex

            val spans = calculateBoundingRectSpans(
                textLayoutResult = layout,
                startIndex = startIndex,
                endIndex = endIndex
            )

            val newRects = spans.map { rectSpan ->
                RectWithAnimatable(
                    id = "${rectSpan.charStart}_${rectSpan.charEnd}_${rectSpan.line}_${rectSpan.rect.left}_${rectSpan.rect.top}_${rectSpan.rect.right}_${rectSpan.rect.bottom}",
                    rect = rectSpan.rect,
                    charStart = rectSpan.charStart,
                    charEnd = rectSpan.charEnd
                )
            }

            startIndex = endIndex + 1

            rectList.addAll(newRects)
            rectChannel.trySend(newRects)
        },
        text = text,
        style = style
    )
}

internal data class RectSpan(
    val rect: Rect,
    val charStart: Int,
    val charEnd: Int,
    val line: Int
)

internal fun calculateBoundingRectSpans(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int
): List<RectSpan> {
    val text = textLayoutResult.layoutInput.text
    if (text.isEmpty()) return emptyList()
    if (startIndex > endIndex) return emptyList()

    val lastIndex = text.length - 1
    val safeStart = startIndex.coerceIn(0, lastIndex)
    val safeEnd = endIndex.coerceIn(0, lastIndex)

    val startLine = textLayoutResult.getLineForOffset(safeStart)
    val endLine = textLayoutResult.getLineForOffset(safeEnd)

    fun lastVisibleOffsetOnLine(line: Int): Int {
        val visibleEndExclusive = textLayoutResult.getLineEnd(line, visibleEnd = true)
        val lineStart = textLayoutResult.getLineStart(line)
        return (visibleEndExclusive - 1).coerceAtLeast(lineStart)
    }

    fun safeBoxOrCursor(offset: Int): Rect {
        val b = textLayoutResult.getBoundingBox(offset)
        return if (b.width <= 0f) textLayoutResult.getCursorRect(offset) else b
    }

    val spans = mutableListOf<RectSpan>()

    for (line in startLine..endLine) {
        val lineStartIndex = textLayoutResult.getLineStart(line)
        val lineLastVisible = lastVisibleOffsetOnLine(line)

        val spanStart = when {
            line == startLine -> safeStart
            else -> lineStartIndex
        }

        val spanEnd = when {
            line == endLine -> minOf(safeEnd, lineLastVisible)
            else -> lineLastVisible
        }

        if (spanStart > spanEnd) continue

        val lineTop = textLayoutResult.getLineTop(line)
        val lineBottom = textLayoutResult.getLineBottom(line)
        val lineLeft = textLayoutResult.getLineLeft(line)

        val rect = when {
            // single line
            startLine == endLine -> {
                val startRect = safeBoxOrCursor(spanStart)
                val endRect = safeBoxOrCursor(spanEnd)
                val unionRect = startRect.union(endRect)
                Rect(
                    topLeft = Offset(unionRect.left, lineTop),
                    bottomRight = Offset(unionRect.right, lineBottom)
                )
            }

            // first line
            line == startLine -> {
                val startRect = safeBoxOrCursor(spanStart)
                val endRect = safeBoxOrCursor(spanEnd)
                Rect(
                    topLeft = Offset(startRect.left, lineTop),
                    bottomRight = Offset(endRect.right, lineBottom)
                )
            }

            // last line
            line == endLine -> {
                val endRect = safeBoxOrCursor(spanEnd)
                Rect(
                    topLeft = Offset(lineLeft, lineTop),
                    bottomRight = Offset(endRect.right, lineBottom)
                )
            }

            // middle lines
            else -> {
                val endRect = safeBoxOrCursor(spanEnd)
                Rect(
                    topLeft = Offset(lineLeft, lineTop),
                    bottomRight = Offset(endRect.right, lineBottom)
                )
            }
        }

        if (rect.width > 0f && rect.height > 0f) {
            spans += RectSpan(rect = rect, charStart = spanStart, charEnd = spanEnd, line = line)
        }
    }

    return spans
}


private data class RectWithAnimatable(
    val id: String,
    val rect: Rect,
    val charStart: Int,
    val charEnd: Int,
    val animatable: Animatable<Float, AnimationVector1D> = Animatable(0f)
)

private fun RectWithAnimatable.covers(index: Int): Boolean = index in charStart..charEnd


private fun ContentDrawScope.drawFadeInRects(rectList: List<RectWithAnimatable>) {
    rectList.forEachIndexed { _, rectWithAnimation ->

        val progress = rectWithAnimation.animatable.value
        val rect = rectWithAnimation.rect
        val topLeft = rect.topLeft
        val rectSize = rect.size

        drawRect(
            color = Color.Red.copy(1 - progress),
            topLeft = topLeft,
            size = rectSize,
//            blendMode = BlendMode.DstOut
        )

        // For Debugging
        drawRect(
            color = lerp(Color.Red, Color.Green, progress),
            topLeft = topLeft,
            size = rectSize,
            style = Stroke(2.dp.toPx())
        )
    }
}
