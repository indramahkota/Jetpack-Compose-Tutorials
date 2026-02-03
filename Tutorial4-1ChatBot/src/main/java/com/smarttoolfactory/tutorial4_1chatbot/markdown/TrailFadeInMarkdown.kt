package com.smarttoolfactory.tutorial4_1chatbot.markdown

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.string.RichTextString
import com.halilibo.richtext.ui.string.Text
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.LineSegmentation
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.RectWithAnimation
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.calculateBoundingRectList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun RichTextScope.MarkdownFadeInRichText(
    astNode: AstNode,
    modifier: Modifier = Modifier,
    debug: Boolean = true,
    animate: Boolean = true,
    delayInMillis: Long = 90L,
    revealCoefficient: Float = 4f,
    lingerInMillis: Long = 90L,
    segmentation: LineSegmentation = LineSegmentation.None,
    onCompleted: () -> Unit = {}
) {

    var startIndex by remember {
        mutableIntStateOf(0)
    }

    MarkdownFadeInRichText(
        astNode = astNode,
        modifier = modifier,
        delayInMillis = delayInMillis,
        revealCoefficient = revealCoefficient,
        lingerInMillis = lingerInMillis,
        segmentation = segmentation,
        debug = debug,
        animate = animate,
        startIndex = startIndex,
        onStartIndexChange = {
            startIndex = it
        },
        onCompleted = onCompleted
    )
}

@Composable
internal fun RichTextScope.MarkdownFadeInRichText(
    astNode: AstNode,
    modifier: Modifier = Modifier,
    debug: Boolean = true,
    animate: Boolean = true,
    delayInMillis: Long = 90L,
    revealCoefficient: Float = 4f,
    lingerInMillis: Long = 90L,
    segmentation: LineSegmentation = LineSegmentation.None,
    startIndex: Int,
    onStartIndexChange: (Int) -> Unit,
    onCompleted: () -> Unit = {}
) {
    val richText: RichTextString = remember(astNode) {
        computeRichTextString(astNode)
    }

    // If not animating, render normally and do not allocate rects/jobs.
    // This is what prevents "re-trigger" when a completed message scrolls back into view.
    if (!animate) {
        Box(modifier) {
            Text(
                text = richText,
                modifier = Modifier.matchParentSize()
            )
        }
    } else {
        MarkdownFadeInRichText(
            modifier = modifier,
            richText = richText,
            delayInMillis = delayInMillis,
            revealCoefficient = revealCoefficient,
            lingerInMillis = lingerInMillis,
            segmentation = segmentation,
            debug = debug,
            startIndex = startIndex,
            onStartIndexChange = onStartIndexChange,
            onCompleted = onCompleted
        )
    }
}

@Composable
fun RichTextScope.MarkdownFadeInRichText(
    modifier: Modifier = Modifier,
    richText: RichTextString,
    debug: Boolean,
    delayInMillis: Long = 90L,
    revealCoefficient: Float = 4f,
    lingerInMillis: Long = 90L,
    segmentation: LineSegmentation,
    startIndex: Int,
    onStartIndexChange: (Int) -> Unit,
    onCompleted: () -> Unit,
) {
    val rectList = remember { mutableStateListOf<RectWithAnimation>() }

    val rectBatchChannel = remember {
        Channel<List<RectWithAnimation>>(capacity = Channel.UNLIMITED)
    }

    val jobsByRectId = remember { mutableStateMapOf<String, Job>() }

    // pending must match scheduled jobs
    var pendingRects by remember { mutableIntStateOf(0) }

    // text fully revealed gate (index-based)
    var fullyRevealed by remember { mutableStateOf(false) }

    // per-laid-out-length completion latch
    var lastLaidOutTextLen by remember { mutableIntStateOf(-1) }
    var completedFiredForLen by remember { mutableIntStateOf(-1) }

    // track previous laid out text length and the line of its last char
    var prevTextLen by remember { mutableIntStateOf(0) }
    var prevAnchorLine by remember { mutableIntStateOf(-1) }

    // track previous anchor glyph box (for patching without removing any rect)
    var prevAnchorBox by remember { mutableStateOf<Rect?>(null) }

    // O(1) membership for wrap rect ids (avoids rectList.any { ... })
    val wrapRectIds = remember { HashSet<String>(128) }

    // O(1) membership for existing rect ids (avoid creating RectWithAnimation objects that are guaranteed duplicates)
    val knownRectIds = remember { HashSet<String>(2048) }

    // Dispatcher: schedule animations; pending increments ONLY for scheduled jobs
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
                        // val duration = (revealCoefficient * rectWithAnimation.rect.width).toInt().coerceAtLeast(80)

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
                                // Keep wrap set in sync when the removed rect is a wrap rect
                                wrapRectIds.remove(rectWithAnimation.id)
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

    // Complete once per laid-out length when fully revealed and animations drained
    LaunchedEffect(fullyRevealed, pendingRects, lastLaidOutTextLen) {
        val shouldComplete =
            fullyRevealed &&
                    pendingRects == 0 &&
                    lastLaidOutTextLen >= 0 &&
                    completedFiredForLen != lastLaidOutTextLen

        if (shouldComplete) {
            // Optional debounce against relayout churn
            delay(80)

            val stillShouldComplete =
                fullyRevealed &&
                        pendingRects == 0 &&
                        lastLaidOutTextLen >= 0 &&
                        completedFiredForLen != lastLaidOutTextLen

            if (stillShouldComplete) {
                completedFiredForLen = lastLaidOutTextLen
                onCompleted()
            }
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawFadeInRects(rectList, debug)
            }
    ) {
        Text(
            text = richText,
            onTextLayout = { textLayout ->

                fun safeBoxOrCursor(layout: TextLayoutResult, offset: Int): Rect {
                    val box = layout.getBoundingBox(offset)
                    return if (box.width <= 0f) layout.getCursorRect(offset) else box
                }

                fun cancelAndRemoveRectsFromLine(line: Int) {
                    val lineTop = textLayout.getLineTop(line)

                    // Remove only rects that are on/under this line AND still animating (masking).
                    // This fixes out-of-order reveals when a delta wraps and changes line breaks.
                    val victimIds = ArrayList<String>(32)

                    rectList.forEach { item ->
                        val isAnimating = item.animatable.value < 1f
                        if (isAnimating && item.rect.top >= lineTop) {
                            victimIds.add(item.id)
                        }
                    }

                    if (victimIds.isEmpty()) return

                    victimIds.forEach { id ->
                        jobsByRectId[id]?.cancel()
                    }

                    rectList.removeAll { it.id in victimIds }
                    victimIds.forEach { id ->
                        knownRectIds.remove(id)
                        wrapRectIds.remove(id)
                    }
                }

                val laidOutText = textLayout.layoutInput.text
                val textLen = laidOutText.length
                val endIndex = textLen - 1

                // Always update length state (even when empty)
                lastLaidOutTextLen = textLen

                // Compute safeStartIndex even if empty; clamp to [0, textLen]
                val safeStartIndex = startIndex.coerceIn(0, textLen)

                // Always update fullyRevealed deterministically
                // If empty text => endIndex = -1 => safeStartIndex (>=0) > -1 => fullyRevealed = true
                fullyRevealed = safeStartIndex > endIndex

                // Determine if there's a new range to process
                val hasNewRange = safeStartIndex <= endIndex

                if (!hasNewRange) {
                    prevTextLen = textLen
                    prevAnchorLine = if (endIndex >= 0) textLayout.getLineForOffset(endIndex) else -1
                    prevAnchorBox = if (endIndex >= 0) safeBoxOrCursor(textLayout, endIndex) else null
                    return@Text
                }

                // If there is unrevealed text, advance progress *unconditionally*.
                // (Never gate on rect production/dedupe.)
                onStartIndexChange(endIndex + 1)

                // Anchor = last char of PREVIOUS text (tail that can wrap)
                val anchorOffset = (prevTextLen - 1).coerceIn(0, endIndex)
                val newAnchorLine = if (endIndex >= 0) textLayout.getLineForOffset(anchorOffset) else -1
                val didWrapDown = (prevAnchorLine >= 0 && newAnchorLine > prevAnchorLine)

                if (didWrapDown) {
                    // When a delta moves to a new line, previously created rects on that line become stale.
                    // Cancel and remove ONLY rects from that line and below that are still animating,
                    // then recalculate from start of that line and animate them in correct order.
                    cancelAndRemoveRectsFromLine(newAnchorLine)
                }

                // If wrapped, restart from start of the NEW line. Otherwise continue from safeStartIndex.
                val computeStart: Int =
                    if (didWrapDown) textLayout.getLineStart(newAnchorLine) else safeStartIndex

                val safeComputeStart = computeStart.coerceIn(0, endIndex)

                // Avoid creating producedRects objects for ones that are guaranteed duplicates by checking the id set during mapping
                val rawRects = calculateBoundingRectList(
                    textLayoutResult = textLayout,
                    startIndex = safeComputeStart,
                    endIndex = endIndex,
                    segmentation = segmentation
                )

                val filtered = ArrayList<RectWithAnimation>(rawRects.size)
                for (rect in rawRects) {
                    val id = "${safeComputeStart}_${endIndex}_${rect.top}_${rect.left}_${rect.right}_${rect.bottom}"

                    // Guaranteed duplicates
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
                }

                if (filtered.isNotEmpty()) {
                    rectList.addAll(filtered)
                    rectBatchChannel.trySend(filtered)
                }

                prevTextLen = textLen
                prevAnchorLine = newAnchorLine
                prevAnchorBox = if (anchorOffset >= 0) safeBoxOrCursor(textLayout, anchorOffset) else null
            }
        )
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

private fun ContentDrawScope.drawFadeInRects(
    rectList: List<RectWithAnimation>,
    debug: Boolean = false
) {
    rectList.forEachIndexed { _, rectWithAnimation ->

        val progress = rectWithAnimation.animatable.value
        val rect = rectWithAnimation.rect
        val topLeft = rect.topLeft
        val rectSize = rect.size

        drawRect(
            color = Color.Red.copy(1 - progress),
            topLeft = topLeft,
            size = rectSize,
            blendMode = BlendMode.DstOut
        )

        // For Debugging
        if (debug) {
            drawRect(
                color = lerp(Color.Red, Color.Green, progress),
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
