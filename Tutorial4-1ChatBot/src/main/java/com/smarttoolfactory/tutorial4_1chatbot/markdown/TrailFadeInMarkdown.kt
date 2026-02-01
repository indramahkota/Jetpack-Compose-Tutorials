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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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

    // ✅ pending must match scheduled jobs
    var pendingRects by remember { mutableIntStateOf(0) }

    // ✅ text fully revealed gate (index-based)
    var fullyRevealed by remember { mutableStateOf(false) }

    // ✅ per-laid-out-length completion latch
    var lastLaidOutTextLen by remember { mutableIntStateOf(-1) }
    var completedFiredForLen by remember { mutableIntStateOf(-1) }

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
                val laidOutText = textLayout.layoutInput.text
                val textLen = laidOutText.length
                val endIndex = textLen - 1

                // ✅ Always update length state (even when empty)
                lastLaidOutTextLen = textLen

                // Compute safeStartIndex even if empty; clamp to [0, textLen]
                val safeStartIndex = startIndex.coerceIn(0, textLen)

                // ✅ Always update fullyRevealed deterministically
                // If empty text => endIndex = -1 => safeStartIndex (>=0) > -1 => fullyRevealed = true
                fullyRevealed = safeStartIndex > endIndex

                // Determine if there's a new range to process
                val hasNewRange = safeStartIndex <= endIndex

                // If there is unrevealed text, advance progress *unconditionally*.
                // (Never gate on rect production/dedupe.)
                if (hasNewRange) {
                    onStartIndexChange(endIndex + 1)
                }

                // Only build/enqueue rects if we have a valid range
                val producedRects: List<RectWithAnimation> =
                    if (hasNewRange) {
                        calculateBoundingRectList(
                            textLayoutResult = textLayout,
                            startIndex = safeStartIndex,
                            endIndex = endIndex,
                            segmentation = segmentation
                        ).map { rect ->
                            RectWithAnimation(
                                id = "${safeStartIndex}_${endIndex}_${rect.top}_${rect.left}_${rect.right}_${rect.bottom}",
                                rect = rect,
                                startIndex = safeStartIndex,
                                endIndex = endIndex
                            )
                        }
                    } else {
                        emptyList()
                    }

                // Dedupe before adding/sending
                val existingRectIds = rectList.asSequence().map { it.id }.toHashSet()
                val filtered =
                    producedRects.filter { it.id !in jobsByRectId && it.id !in existingRectIds }

                if (filtered.isNotEmpty()) {
                    rectList.addAll(filtered)
                    rectBatchChannel.trySend(filtered)
                }
            }
        )
    }
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
