package com.smarttoolfactory.tutorial4_1chatbot.markdown

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.RichTextThemeProvider
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.LineSegmentation
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.RectWithAnimation
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.calculateBoundingRectList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Preview-only version that uses Compose Text (not RichTextScope) to test:
 * - wrap detection (delta causes previous tail to move to next line)
 * - rect removal/recalc debug overlays (magenta removed, cyan produced, yellow anchor)
 */
@Preview(showBackground = true, widthDp = 420, heightDp = 800)
@Composable
fun MarkdownFadeInRectsWrapDebugPreview() {

    var typed by remember { mutableStateOf("") }
    var chunkText by remember { mutableStateOf("") }

    // These deltas are intentionally chosen to force wrap behavior.
    // Adjust the strings to reproduce your specific "wrap down" cases.
    val deltas = remember {
        listOf(
            "Back exercises are an essential component of a balanced fitness routine, ",
//            "as they help strengthen the muscles in the back, improve posture, ",
//            "enhance stability, and reduce the risk of injuries. ",
//            "Here's a breakdown of various types of back ",
//            "exercises:\n",
//            "1. Strengthening Exercises:\n",
//            "• Purpose: Build muscle strength in the back.\n",
//            "• Examples: Deadlifts, Bent-over Rows, Pull-ups.\n",
//            "2. Mobility Exercises:\n",
//            "• Purpose: Improve range of motion and posture, ",
//            "especially after long periods of sitting.\n",
        )
    }

    LaunchedEffect(Unit) {
        delay(200)
        deltas.forEachIndexed { index, s ->
            chunkText += s
            // bigger delays make wrap events obvious
            delay(if (index < 3) 600 else 450)
        }
    }

    Column(
        modifier = Modifier
            .systemBarsPadding()
            .padding(16.dp)
            .fillMaxSize()
    ) {

        TrailMarkdownTextDebug(
            modifier = Modifier.fillMaxWidth(),
            text = chunkText,
            debug = true,
            segmentation = LineSegmentation.None,
            delayInMillis = 90L,
            lingerInMillis = 90L,
        )

        RichTextThemeProvider(
            textStyleProvider = {
                TextStyle.Default.copy(fontSize = 16.sp)
            }
        ) {
            MarkdownComposer(
                markdown = chunkText,
                debug = true
            )
        }


        OutlinedTextField(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth(),
            value = typed,
            onValueChange = { typed = it },
            label = { Text("Append text to force wrap") }
        )

        Spacer(Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (typed.isNotBlank()) {
                    chunkText += typed
                    typed = ""
                }
            }
        ) { Text("Append") }
    }
}

@Composable
private fun TrailMarkdownTextDebug(
    modifier: Modifier = Modifier,
    text: String,
    debug: Boolean,
    segmentation: LineSegmentation,
    delayInMillis: Long,
    lingerInMillis: Long,
) {
    var startIndex by remember { mutableIntStateOf(0) }

    val rectList = remember { mutableStateListOf<RectWithAnimation>() }
    val rectBatchChannel =
        remember { Channel<List<RectWithAnimation>>(capacity = Channel.UNLIMITED) }
    val jobsByRectId = remember { mutableStateMapOf<String, Job>() }

    var pendingRects by remember { mutableIntStateOf(0) }

    var prevTextLen by remember { mutableIntStateOf(0) }
    var prevAnchorLine by remember { mutableIntStateOf(-1) }
    var prevAnchorBox by remember { mutableStateOf<Rect?>(null) }

    // O(1) membership for existing rect ids (avoid creating RectWithAnimation objects that are guaranteed duplicates)
    val knownRectIds = remember { HashSet<String>(2048) }

    // --- DEBUG STATE ---
    val removedRectsDebug = remember { mutableStateListOf<Rect>() }
    val producedAfterWrapDebug = remember { mutableStateListOf<Rect>() }
    var lastWrapAnchorBoxDebug by remember { mutableStateOf<Rect?>(null) }
    // -------------------

    LaunchedEffect(Unit) {
        for (batch in rectBatchChannel) {
            var scheduledIndex = 0
            batch.forEach { rectWithAnimation ->
                val id = rectWithAnimation.id
                val alreadyScheduled = jobsByRectId.containsKey(id)
                if (!alreadyScheduled) {
                    pendingRects += 1

                    val myIndex = scheduledIndex
                    scheduledIndex += 1

                    val job = launch {
                        delay(delayInMillis * myIndex)

                        val duration = 1000

                        try {
                            rectWithAnimation.animatable.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(duration, easing = LinearEasing)
                            )
                            delay(lingerInMillis)
                        } finally {
                            pendingRects = (pendingRects - 1).coerceAtLeast(0)
                            if (!debug) {
                                rectList.remove(rectWithAnimation)
                                knownRectIds.remove(rectWithAnimation.id)
                            }
                            jobsByRectId.remove(id)
                        }
                    }

                    jobsByRectId[id] = job
                }
            }
        }
    }

    androidx.compose.material3.Text(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {

                if (debug) {
                    // Removed rects (magenta)
                    removedRectsDebug.forEach { r ->
                        drawRect(
                            color = Color.Magenta,
                            topLeft = r.topLeft,
                            size = r.size,
                        )
                    }

                    // Produced after wrap (cyan)
                    producedAfterWrapDebug.forEach { r ->
                        drawRect(
                            color = Color.Cyan,
                            topLeft = r.topLeft,
                            size = r.size,
                        )
                    }

                    // Anchor box (yellow)
                    lastWrapAnchorBoxDebug?.let { r ->
                        drawRect(
                            color = Color.Yellow,
                            topLeft = r.topLeft,
                            size = r.size,
                        )
                    }
                }

                drawContent()

                drawFadeInRects(rectList, debug)
            },
        text = text,
        fontSize = 18.sp,
        onTextLayout = { textLayout ->

            fun safeBoxOrCursor(layout: TextLayoutResult, offset: Int): Rect {
                val box = layout.getBoundingBox(offset)
                return if (box.width <= 0f) layout.getCursorRect(offset) else box
            }

            fun cancelAndRemoveRectsFromLine(line: Int) {
                val lineTop = textLayout.getLineTop(line)

                if (debug) {
                    removedRectsDebug.clear()
                    producedAfterWrapDebug.clear()
                    lastWrapAnchorBoxDebug = null
                }

                val victimIds = ArrayList<String>(32)
                val victimRects = ArrayList<Rect>(32)

                rectList.forEach { item ->
                    val isAnimating = item.animatable.value < 1f
                    if (isAnimating && item.rect.top >= lineTop) {
                        victimIds.add(item.id)
                        if (debug) victimRects.add(item.rect)
                    }
                }

                if (victimIds.isEmpty()) return

                if (debug) removedRectsDebug.addAll(victimRects)

                victimIds.forEach { id -> jobsByRectId[id]?.cancel() }
                rectList.removeAll { it.id in victimIds }
                victimIds.forEach { id -> knownRectIds.remove(id) }
            }

            val laidOutText = textLayout.layoutInput.text
            val textLen = laidOutText.length
            val endIndex = textLen - 1

            val safeStartIndex = startIndex.coerceIn(0, textLen)
            val hasNewRange = safeStartIndex <= endIndex

            if (!hasNewRange) {
                prevTextLen = textLen
                prevAnchorLine = if (endIndex >= 0) textLayout.getLineForOffset(endIndex) else -1
                prevAnchorBox = if (endIndex >= 0) safeBoxOrCursor(textLayout, endIndex) else null
                return@Text
            }

            startIndex = endIndex + 1

            val anchorOffset = (prevTextLen - 1).coerceIn(0, endIndex)
            val newAnchorLine = if (endIndex >= 0) textLayout.getLineForOffset(anchorOffset) else -1
            val didWrapDown = (prevAnchorLine >= 0 && newAnchorLine > prevAnchorLine)

            if (didWrapDown) {
                if (debug) {
                    lastWrapAnchorBoxDebug = safeBoxOrCursor(textLayout, anchorOffset)
                }
                cancelAndRemoveRectsFromLine(newAnchorLine)
            } else if (debug) {
                removedRectsDebug.clear()
                producedAfterWrapDebug.clear()
                lastWrapAnchorBoxDebug = null
            }

            val computeStart: Int =
                if (didWrapDown) textLayout.getLineStart(newAnchorLine) else safeStartIndex

            val safeComputeStart = computeStart.coerceIn(0, endIndex)

            val rawRects = calculateBoundingRectList(
                textLayoutResult = textLayout,
                startIndex = safeComputeStart,
                endIndex = endIndex,
                segmentation = segmentation
            )

            val filtered = ArrayList<RectWithAnimation>(rawRects.size)
            val producedDebugRects =
                if (debug && didWrapDown) ArrayList<Rect>(rawRects.size) else null

            for (rect in rawRects) {
                val id =
                    "${safeComputeStart}_${endIndex}_${rect.top}_${rect.left}_${rect.right}_${rect.bottom}"

                if (knownRectIds.contains(id)) continue
                if (jobsByRectId.containsKey(id)) continue

                knownRectIds.add(id)

                filtered.add(
                    RectWithAnimation(
                        id = id,
                        rect = rect,
                        startIndex = safeComputeStart,
                        endIndex = endIndex
                    )
                )

                producedDebugRects?.add(rect)
            }

            if (debug && didWrapDown) {
                producedAfterWrapDebug.clear()
                if (producedDebugRects != null) producedAfterWrapDebug.addAll(producedDebugRects)
            }

            if (filtered.isNotEmpty()) {
                rectList.addAll(filtered)
                rectBatchChannel.trySend(filtered)
            }

            prevTextLen = textLen
            prevAnchorLine = newAnchorLine
            prevAnchorBox =
                if (anchorOffset >= 0) safeBoxOrCursor(textLayout, anchorOffset) else null
        }
    )
}

private fun ContentDrawScope.drawFadeInRects(
    rectList: List<RectWithAnimation>,
    debug: Boolean = false
) {
    rectList.forEach { rectWithAnimation ->
        val progress = rectWithAnimation.animatable.value
        val rect = rectWithAnimation.rect

        drawRect(
            color = Color.Red.copy(1 - progress),
            topLeft = rect.topLeft,
            size = rect.size,
            blendMode = BlendMode.DstOut
        )

        if (debug) {
            drawRect(
                color = lerp(Color.Red, Color.Green, progress),
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

private fun intersectionArea(a: Rect, b: Rect): Float {
    val left = maxOf(a.left, b.left)
    val top = maxOf(a.top, b.top)
    val right = minOf(a.right, b.right)
    val bottom = minOf(a.bottom, b.bottom)
    val w = (right - left).coerceAtLeast(0f)
    val h = (bottom - top).coerceAtLeast(0f)
    return w * h
}
