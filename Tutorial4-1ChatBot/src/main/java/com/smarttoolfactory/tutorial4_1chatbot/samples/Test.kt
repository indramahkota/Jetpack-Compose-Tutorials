@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun TrailFadeInTextParallelWithChannel2(
    modifier: Modifier = Modifier,
    text: String,
    style: TextStyle = TextStyle.Default,
    staggerMs: Long = 20L,
    revealMs: Int = 1000,
    lingerMs: Long = 80L,
) {
    // Append-only tracking
    var lastProcessedExclusive by remember { mutableIntStateOf(0) }

    val activeRects = remember { mutableStateListOf<RectWithAnimation>() }

    val rectBatchChannel = remember { Channel<List<RectWithAnimation>>(capacity = Channel.UNLIMITED) }
    val jobsById = remember { mutableStateMapOf<String, Job>() }

    // Dispatcher: launches per-rect animations in parallel (with stagger)
    LaunchedEffect(Unit) {
        for (batch in rectBatchChannel) {
            batch.forEachIndexed { index, rectWithAnimation ->
                if (jobsById.containsKey(rectWithAnimation.id)) return@forEachIndexed

                jobsById[rectWithAnimation.id] = launch {
                    // Stagger start.
                    // NOTE: rect is already in activeRects -> it will mask until its progress advances.
                    delay(staggerMs * index)

                    try {
                        rectWithAnimation.animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(revealMs, easing = LinearEasing)
                        )
                        delay(lingerMs)
                    } finally {
                        activeRects.remove(rectWithAnimation)
                        jobsById.remove(rectWithAnimation.id)
                    }
                }
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            rectBatchChannel.close()
            jobsById.values.forEach { it.cancel() }
            jobsById.clear()
            activeRects.clear()
        }
    }

    Text(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawFadeInRectsDstOut(activeRects)
            },
        text = text,
        style = style,
        onTextLayout = { layout ->
            val len = layout.layoutInput.text.length
            val startInclusive = lastProcessedExclusive.coerceIn(0, len)
            if (startInclusive >= len) return@Text

            val endInclusive = (len - 1).coerceAtLeast(startInclusive)

            val rects = calculateBoundingRectLists(
                textLayoutResult = layout,
                startIndex = startInclusive,
                endIndex = endInclusive
            )

            lastProcessedExclusive = len
            if (rects.isEmpty()) return@Text

            // IDs based on logical range + per-line index (stable)
            val startLine = layout.getLineForOffset(startInclusive)
            val endLine = layout.getLineForOffset(endInclusive)

            val batch = rects.mapIndexed { i, rect ->
                val line = (startLine + i).coerceAtMost(endLine)
                RectWithAnimation(
                    id = "range_${startInclusive}_${endInclusive}_line_$line",
                    startIndex = startInclusive,
                    endIndex = endInclusive,
                    rect = rect
                )
            }

            activeRects.addAll(batch)

            // Kick animations
            rectBatchChannel.trySend(batch)
        }
    )
}

private fun ContentDrawScope.drawFadeInRectsDstOut(
    rectList: List<RectWithAnimation>
) {
    rectList.forEach { rectWithAnimation ->
        val progress = rectWithAnimation.animatable.value.coerceIn(0f, 1f)
        val alpha = (1f - progress).coerceIn(0f, 1f)

        drawRect(
            color = Color.Black,
            topLeft = rectWithAnimation.rect.topLeft,
            size = rectWithAnimation.rect.size,
            alpha = alpha,
            blendMode = BlendMode.DstOut
        )
    }
}

internal fun calculateBoundingRectLists(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int
): List<Rect> {

    val text = textLayoutResult.layoutInput.text
    if (text.isEmpty()) return emptyList()
    if (startIndex > endIndex) return emptyList()

    val lastIndex = (text.length - 1).coerceAtLeast(0)

    val safeStart = startIndex.coerceIn(0, lastIndex)
    val safeEnd = endIndex.coerceIn(0, lastIndex)

    val startLine = textLayoutResult.getLineForOffset(safeStart)
    val endLine = textLayoutResult.getLineForOffset(safeEnd)

    val rectList = mutableListOf<Rect>()

    for (currentLine in startLine..endLine) {
        val rect = getBoundingRectForLine(
            textLayoutResult = textLayoutResult,
            startIndex = safeStart,
            endIndex = safeEnd,
            startLine = startLine,
            endLine = endLine,
            currentLine = currentLine
        )

        if (rect.width > 0f && rect.height > 0f) {
            rectList.add(rect)
        }
    }
    return rectList
}

private fun lastVisibleOffsetOnLine(layout: TextLayoutResult, line: Int): Int {
    val visibleEndExclusive = layout.getLineEnd(line, visibleEnd = true)
    val lineStart = layout.getLineStart(line)
    return (visibleEndExclusive - 1).coerceAtLeast(lineStart)
}

private fun safeBoxOrCursor(layout: TextLayoutResult, offset: Int): Rect {
    val boundingRect = layout.getBoundingBox(offset)
    return if (boundingRect.width <= 0f) layout.getCursorRect(offset) else boundingRect
}

private fun getBoundingRectForLine(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int,
    currentLine: Int,
    startLine: Int,
    endLine: Int
): Rect {

    val lineTop: Float = textLayoutResult.getLineTop(currentLine)
    val lineBottom: Float = textLayoutResult.getLineBottom(currentLine)
    val lineLeft: Float = textLayoutResult.getLineLeft(currentLine)

    val endOnThisLine = minOf(endIndex, lastVisibleOffsetOnLine(textLayoutResult, currentLine))

    return when {
        currentLine == startLine && startLine == endLine -> {
            val startRect: Rect = safeBoxOrCursor(textLayoutResult, startIndex)
            val endRect: Rect = safeBoxOrCursor(textLayoutResult, endOnThisLine)
            val unionRect: Rect = startRect.union(endRect)

            Rect(
                topLeft = Offset(unionRect.left, lineTop),
                bottomRight = Offset(unionRect.right, lineBottom)
            )
        }

        currentLine == startLine -> {
            val startRect: Rect = safeBoxOrCursor(textLayoutResult, startIndex)
            val endRect: Rect = safeBoxOrCursor(
                textLayoutResult,
                lastVisibleOffsetOnLine(textLayoutResult, currentLine)
            )
            Rect(
                topLeft = Offset(startRect.left, lineTop),
                bottomRight = Offset(endRect.right, lineBottom)
            )
        }

        currentLine == endLine -> {
            val endRect: Rect = safeBoxOrCursor(textLayoutResult, endOnThisLine)
            Rect(
                topLeft = Offset(lineLeft, lineTop),
                bottomRight = Offset(endRect.right, lineBottom)
            )
        }

        else -> {
            val endRect = safeBoxOrCursor(
                textLayoutResult,
                lastVisibleOffsetOnLine(textLayoutResult, currentLine)
            )
            Rect(
                topLeft = Offset(lineLeft, lineTop),
                bottomRight = Offset(endRect.right, lineBottom)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun TrailFadeInTextParallelWithChannelPreview() {
    MaterialTheme {
        Surface {
            Column(Modifier.padding(16.dp)) {
                var streamed by remember { mutableStateOf("") }

                LaunchedEffect(Unit) {
                    val chunks = listOf(
                        "Hello. This is masked immediately and revealed using DstOut.\n\n",
                        "New text is appended in chunks. ",
                        "Each batch generates per-line rects, which start fully hidden.\n\n",
                        "• One\n• Two\n• Three\n\n",
                        "Done."
                    )
                    for (c in chunks) {
                        streamed += c
                        delay(240L)
                    }
                }

                TrailFadeInTextParallelWithChannel2(
                    text = streamed,
                    style = MaterialTheme.typography.bodyMedium,
                    staggerMs = 18L,
                    revealMs = 200,
                    lingerMs = 70L
                )
            }
        }
    }
}
