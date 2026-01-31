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
    delayInMillis: Long = 90L,
    revealCoefficient: Float = 4f,
    lingerInMillis: Long = 90L,
    segmentation: LineSegmentation = LineSegmentation.None,
    debug: Boolean = true,
    animate: Boolean = true,
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
        startIndex = startIndex,
        onStartIndexChange = {
            startIndex = it
        },
        onCompleted = {

        }
    )
}

@Composable
internal fun RichTextScope.MarkdownFadeInRichText(
    astNode: AstNode,
    modifier: Modifier = Modifier,
    delayInMillis: Long = 90L,
    revealCoefficient: Float = 4f,
    lingerInMillis: Long = 90L,
    segmentation: LineSegmentation = LineSegmentation.None,
    debug: Boolean = true,
    startIndex: Int,
    onStartIndexChange: (Int) -> Unit,
    onCompleted: () -> Unit
) {
    // Assume that only RichText nodes reside below this level.
    val richText: RichTextString = remember(astNode) {
        computeRichTextString(astNode)
    }

    val rectList = remember { mutableStateListOf<RectWithAnimation>() }

    // Queue of rect batches coming from onTextLayout
    val rectBatchChannel = remember {
        Channel<List<RectWithAnimation>>(
            capacity = Channel.UNLIMITED
        )
    }

    // Track jobs so each rect starts once, and can optionally clean up.
    val jobsByRectId = remember { mutableStateMapOf<String, Job>() }

    // ✅ Track how many rect animations are still running
    var pendingRects by remember { mutableIntStateOf(0) }

    // One long-lived "dispatcher" coroutine; does not restart on new layouts.
    LaunchedEffect(Unit) {
        for (batch in rectBatchChannel) {
            batch.forEachIndexed { index, rectWithAnimation ->
                // If you might enqueue the same instance again, guard it.
                val id = rectWithAnimation.id
                if (jobsByRectId.containsKey(id)) return@forEachIndexed

                val job = launch {
                    // Optional stagger, keeps parallel but slightly cascaded.
                    delay(delayInMillis * index)
                    val duration = 1000
//                    val duration = (revealCoefficient * rectWithAnimation.rect.width).toInt()

                    try {
                        rectWithAnimation.animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(duration, easing = LinearEasing)
                        )
                        delay(lingerInMillis)
                    } finally {
                        // ✅ Decrement pending when this rect's animation is finished
                        pendingRects = (pendingRects - 1).coerceAtLeast(0)

                        rectList.remove(rectWithAnimation)

                        jobsByRectId.remove(id)
                    }
                }

                jobsByRectId[id] = job
            }
        }
    }

    // ✅ When streaming is done and no animations are pending, mark completed once.
    // 🔥🔥 TODO This LaunchedEffect doesn't get called initially but it can break
    // if it gets called and calls complete without any guard
    LaunchedEffect(pendingRects) {
        println("😹 Markdown PENDING RECT: $pendingRects")

        if (pendingRects == 0) {
            onCompleted()
            println("😹 Markdown COMPLETED")
        }
    }

    Box(modifier = modifier
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawFadeInRects(rectList, debug)
        }
    ) {
        Text(
            text = richText,
            onTextLayout = { textLayout ->
                val text = textLayout.layoutInput.text
                val endIndex = text.lastIndex

                if (endIndex >= 0) {
                    /**
                     * When markdown re-parses, this Text may shrink/expand.
                     * Clamp startIndex so calculateBoundingRectList never receives an invalid range.
                     */
                    val safeStartIndex = startIndex.coerceIn(0, endIndex + 1)

                    // ✅ If there's no new range, do NOTHING.
                    // Do NOT call onCompleted here (that’s what was completing early).
                    if (safeStartIndex <= endIndex) {
                        val newRects = calculateBoundingRectList(
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

                        println("onTextLayout safeStartIndex: $safeStartIndex, endIndex: $endIndex, text: $text")

                        // ✅ advance progress (monotonic is enforced in caller)
                        onStartIndexChange(endIndex + 1)

                        // ✅ count these rects as pending BEFORE sending to channel
                        pendingRects += newRects.size

                        // Make them visible immediately
                        rectList.addAll(newRects)

                        // Kick off animations without causing cancellation of previous ones
                        rectBatchChannel.trySend(newRects)
                    }
                }
            }
        )
    }
}

@Composable
internal fun RichTextScope.MarkdownFadeInRichText2(
    astNode: AstNode,
    modifier: Modifier = Modifier,
    delayInMillis: Long = 90L,
    revealCoefficient: Float = 4f,
    lingerInMillis: Long = 90L,
    segmentation: LineSegmentation = LineSegmentation.None,
    debug: Boolean = true,
    startIndex: Int,
    onStartIndexChange: (Int) -> Unit,
    onCompleted: () -> Unit
) {
    // Assume that only RichText nodes reside below this level.
    val richText: RichTextString = remember(astNode) {
        computeRichTextString(astNode)
    }

    val rectList = remember { mutableStateListOf<RectWithAnimation>() }

    // Queue of rect batches coming from onTextLayout
    val rectBatchChannel = remember {
        Channel<List<RectWithAnimation>>(
            capacity = Channel.UNLIMITED
        )
    }

    // Track jobs so each rect starts once, and can optionally clean up.
    val jobsByRectId = remember { mutableStateMapOf<String, Job>() }

    // ✅ Track how many rect animations are still running (MUST match scheduled jobs)
    var pendingRects by remember { mutableIntStateOf(0) }

    // ✅ Track whether the currently laid out text is fully revealed (no new range)
    var fullyRevealed by remember { mutableStateOf(false) }

    // ✅ Track latest laid out text length (source of truth is TextLayoutResult input text)
    var lastLaidOutTextLen by remember { mutableIntStateOf(-1) }

    // ✅ Fire onCompleted once per laid out length (re-arm if the laid out text grows later)
    var completedFiredForLen by remember { mutableIntStateOf(-1) }

    // One long-lived "dispatcher" coroutine; does not restart on new layouts.
    LaunchedEffect(Unit) {
        for (batch in rectBatchChannel) {

            // ✅ Stagger only for rects that actually get scheduled
            var scheduledIndex = 0

            batch.forEach { rectWithAnimation ->
                val id = rectWithAnimation.id
                val alreadyScheduled = jobsByRectId.containsKey(id)

                if (!alreadyScheduled) {
                    // ✅ IMPORTANT: only count as pending if we actually schedule a job
                    pendingRects += 1

                    val myIndex = scheduledIndex
                    scheduledIndex += 1

                    val job = launch {
                        // Optional stagger, keeps parallel but slightly cascaded.
                        delay(delayInMillis * myIndex)
                        val duration = 1000
//                        val duration = (revealCoefficient * rectWithAnimation.rect.width).toInt()

                        try {
                            rectWithAnimation.animatable.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(duration, easing = LinearEasing)
                            )
                            delay(lingerInMillis)
                        } finally {
                            pendingRects = (pendingRects - 1).coerceAtLeast(0)

                            // Your v2 always removes; keep that behavior.
                            rectList.remove(rectWithAnimation)

                            jobsByRectId.remove(id)
                        }
                    }

                    jobsByRectId[id] = job
                }
            }
        }
    }

    // ✅ Mark completed when (fully revealed) AND (no pending animations),
    // fired once per laid-out text length (so it works for streaming growth too).
    LaunchedEffect(fullyRevealed, pendingRects, lastLaidOutTextLen) {
        val shouldComplete =
            fullyRevealed &&
                    pendingRects == 0 &&
                    lastLaidOutTextLen >= 0 &&
                    completedFiredForLen != lastLaidOutTextLen

        if (shouldComplete) {
            // Optional debounce to avoid flapping due to relayout churn
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
                val text = textLayout.layoutInput.text
                val endIndex = text.lastIndex

                if (endIndex >= 0) {
                    // ✅ track laid out text length (source of truth)
                    lastLaidOutTextLen = text.length

                    /**
                     * When markdown re-parses, this Text may shrink/expand.
                     * Clamp startIndex so calculateBoundingRectList never receives an invalid range.
                     */
                    val safeStartIndex = startIndex.coerceIn(0, endIndex + 1)

                    // ✅ update fully revealed state for completion logic (but DO NOT call onCompleted here)
                    fullyRevealed = safeStartIndex > endIndex

                    // ✅ If there's a new range, build rects; otherwise do nothing.
                    val hasNewRange = safeStartIndex <= endIndex

                    if (hasNewRange) {
                        val newRects = calculateBoundingRectList(
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

                        // ✅ Dedupe BEFORE adding/sending, otherwise you'll create rects that never get jobs
                        // because jobs are keyed by id.
                        val existingRectIds = rectList.asSequence().map { it.id }.toHashSet()
                        val filtered = newRects.filter { it.id !in jobsByRectId && it.id !in existingRectIds }

                        val hasRectsToAnimate = filtered.isNotEmpty()

                        if (hasRectsToAnimate) {
                            println(
                                "onTextLayout safeStartIndex: $safeStartIndex, " +
                                        "endIndex: $endIndex, added=${filtered.size}"
                            )

                            // ✅ advance progress (monotonic is enforced in caller)
                            onStartIndexChange(endIndex + 1)

                            // Make them visible immediately
                            rectList.addAll(filtered)

                            // Kick off animations without causing cancellation of previous ones
                            rectBatchChannel.trySend(filtered)
                        }
                    }
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
