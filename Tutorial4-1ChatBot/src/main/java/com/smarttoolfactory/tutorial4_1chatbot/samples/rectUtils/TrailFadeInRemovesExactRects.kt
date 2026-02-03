//package com.smarttoolfactory.tutorial4_1chatbot.markdown
//
//import androidx.compose.animation.core.LinearEasing
//import androidx.compose.animation.core.tween
//import androidx.compose.foundation.layout.Box
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.LaunchedEffect
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableIntStateOf
//import androidx.compose.runtime.mutableStateListOf
//import androidx.compose.runtime.mutableStateMapOf
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.drawWithContent
//import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.geometry.Rect
//import androidx.compose.ui.graphics.BlendMode
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.CompositingStrategy
//import androidx.compose.ui.graphics.drawscope.ContentDrawScope
//import androidx.compose.ui.graphics.drawscope.Stroke
//import androidx.compose.ui.graphics.graphicsLayer
//import androidx.compose.ui.graphics.lerp
//import androidx.compose.ui.text.TextLayoutResult
//import androidx.compose.ui.unit.dp
//import com.halilibo.richtext.markdown.node.AstNode
//import com.halilibo.richtext.ui.RichTextScope
//import com.halilibo.richtext.ui.string.RichTextString
//import com.halilibo.richtext.ui.string.Text
//import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.LineSegmentation
//import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.RectWithAnimation
//import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.calculateBoundingRectList
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.channels.Channel
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//
//private data class RectMeta(
//    val line: Int,
//    val startOffset: Int,
//    val endOffset: Int
//)
//
//@Composable
//internal fun RichTextScope.MarkdownFadeInRichText(
//    astNode: AstNode,
//    modifier: Modifier = Modifier,
//    debug: Boolean = true,
//    animate: Boolean = true,
//    delayInMillis: Long = 90L,
//    revealCoefficient: Float = 4f,
//    lingerInMillis: Long = 90L,
//    segmentation: LineSegmentation = LineSegmentation.None,
//    onCompleted: () -> Unit = {}
//) {
//
//    var startIndex by remember {
//        mutableIntStateOf(0)
//    }
//
//    MarkdownFadeInRichText(
//        astNode = astNode,
//        modifier = modifier,
//        delayInMillis = delayInMillis,
//        revealCoefficient = revealCoefficient,
//        lingerInMillis = lingerInMillis,
//        segmentation = segmentation,
//        debug = debug,
//        animate = animate,
//        startIndex = startIndex,
//        onStartIndexChange = {
//            startIndex = it
//        },
//        onCompleted = onCompleted
//    )
//}
//
//@Composable
//internal fun RichTextScope.MarkdownFadeInRichText(
//    astNode: AstNode,
//    modifier: Modifier = Modifier,
//    debug: Boolean = true,
//    animate: Boolean = true,
//    delayInMillis: Long = 90L,
//    revealCoefficient: Float = 4f,
//    lingerInMillis: Long = 90L,
//    segmentation: LineSegmentation = LineSegmentation.None,
//    startIndex: Int,
//    onStartIndexChange: (Int) -> Unit,
//    onCompleted: () -> Unit = {}
//) {
//    val richText: RichTextString = remember(astNode) {
//        computeRichTextString(astNode)
//    }
//
//    // If not animating, render normally and do not allocate rects/jobs.
//    // This is what prevents "re-trigger" when a completed message scrolls back into view.
//    if (!animate) {
//        Box(modifier) {
//            Text(
//                text = richText,
//                modifier = Modifier.matchParentSize()
//            )
//        }
//    } else {
//        MarkdownFadeInRichText(
//            modifier = modifier,
//            richText = richText,
//            delayInMillis = delayInMillis,
//            revealCoefficient = revealCoefficient,
//            lingerInMillis = lingerInMillis,
//            segmentation = segmentation,
//            debug = debug,
//            startIndex = startIndex,
//            onStartIndexChange = onStartIndexChange,
//            onCompleted = onCompleted
//        )
//    }
//}
//
//@Composable
//fun RichTextScope.MarkdownFadeInRichText(
//    modifier: Modifier = Modifier,
//    richText: RichTextString,
//    debug: Boolean,
//    delayInMillis: Long = 90L,
//    revealCoefficient: Float = 4f,
//    lingerInMillis: Long = 90L,
//    segmentation: LineSegmentation,
//    startIndex: Int,
//    onStartIndexChange: (Int) -> Unit,
//    onCompleted: () -> Unit,
//) {
//    val rectList = remember { mutableStateListOf<RectWithAnimation>() }
//
//    val rectBatchChannel = remember {
//        Channel<List<RectWithAnimation>>(capacity = Channel.UNLIMITED)
//    }
//
//    val jobsByRectId = remember { mutableStateMapOf<String, Job>() }
//
//    // ✅ side meta store (no changes to RectWithAnimation)
//    val rectMetaById = remember { mutableStateMapOf<String, RectMeta>() }
//
//    // ✅ per-victim per-textLen guard to avoid re-splitting every relayout
//    val splitDoneForTextLenByVictimId = remember { mutableStateMapOf<String, Int>() }
//
//    // ✅ pending must match scheduled jobs
//    var pendingRects by remember { mutableIntStateOf(0) }
//
//    // ✅ text fully revealed gate (index-based)
//    var fullyRevealed by remember { mutableStateOf(false) }
//
//    // ✅ per-laid-out-length completion latch
//    var lastLaidOutTextLen by remember { mutableIntStateOf(-1) }
//    var completedFiredForLen by remember { mutableIntStateOf(-1) }
//
//    // ✅ track previous laid out text length and the line of its last char
//    var prevTextLen by remember { mutableIntStateOf(0) }
//    var prevAnchorLine by remember { mutableIntStateOf(-1) }
//
//    // Dispatcher: schedule animations; pending increments ONLY for scheduled jobs
//    LaunchedEffect(Unit) {
//        for (batch in rectBatchChannel) {
//            var scheduledIndex = 0
//
//            batch.forEach { rectWithAnimation ->
//                val id = rectWithAnimation.id
//                val alreadyScheduled = jobsByRectId.containsKey(id)
//
//                if (!alreadyScheduled) {
//                    pendingRects += 1
//
//                    val myIndex = scheduledIndex
//                    scheduledIndex += 1
//
//                    val job = launch {
//                        delay(delayInMillis * myIndex)
//
//                        val duration = 1000
//                        // val duration = (revealCoefficient * rectWithAnimation.rect.width).toInt().coerceAtLeast(80)
//
//                        try {
//                            rectWithAnimation.animatable.animateTo(
//                                targetValue = 1f,
//                                animationSpec = tween(duration, easing = LinearEasing)
//                            )
//                            delay(lingerInMillis)
//                        } finally {
//                            pendingRects = (pendingRects - 1).coerceAtLeast(0)
//
//                            // ✅ DO NOT REMOVE ANY RECTANGLES
//                            // if (!debug) {
//                            //     rectList.remove(rectWithAnimation)
//                            // }
//
//                            jobsByRectId.remove(id)
//                        }
//                    }
//
//                    jobsByRectId[id] = job
//                }
//            }
//        }
//    }
//
//    // Complete once per laid-out length when fully revealed and animations drained
//    LaunchedEffect(fullyRevealed, pendingRects, lastLaidOutTextLen) {
//        val shouldComplete =
//            fullyRevealed &&
//                    pendingRects == 0 &&
//                    lastLaidOutTextLen >= 0 &&
//                    completedFiredForLen != lastLaidOutTextLen
//
//        if (shouldComplete) {
//            // Optional debounce against relayout churn
//            delay(80)
//
//            val stillShouldComplete =
//                fullyRevealed &&
//                        pendingRects == 0 &&
//                        lastLaidOutTextLen >= 0 &&
//                        completedFiredForLen != lastLaidOutTextLen
//
//            if (stillShouldComplete) {
//                completedFiredForLen = lastLaidOutTextLen
//                onCompleted()
//            }
//        }
//    }
//
//    Box(
//        modifier = modifier
//            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
//            .drawWithContent {
//                drawContent()
//                drawFadeInRects(rectList, debug)
//            }
//    ) {
//        Text(
//            text = richText,
//            onTextLayout = { textLayout ->
//
//                fun safeBoxOrCursor(layout: TextLayoutResult, offset: Int): Rect {
//                    val box = layout.getBoundingBox(offset)
//                    return if (box.width <= 0f) layout.getCursorRect(offset) else box
//                }
//
//                fun lastVisibleOffsetOnLine(layout: TextLayoutResult, line: Int): Int {
//                    val visibleEndExclusive = layout.getLineEnd(line, visibleEnd = true)
//                    val lineStart = layout.getLineStart(line)
//                    return (visibleEndExclusive - 1).coerceAtLeast(lineStart)
//                }
//
//                fun lineForRect(layout: TextLayoutResult, rect: Rect): Int {
//                    val y = rect.top + 0.5f
//                    val count = layout.lineCount
//                    for (line in 0 until count) {
//                        val top = layout.getLineTop(line)
//                        val bottom = layout.getLineBottom(line)
//                        if (y in top..bottom) return line
//                    }
//                    return (count - 1).coerceAtLeast(0)
//                }
//
//                fun offsetsForRect(layout: TextLayoutResult, rect: Rect, endIndex: Int): Pair<Int, Int> {
//                    val y = (rect.top + rect.bottom) * 0.5f
//                    val eps = 0.5f
//
//                    val left = layout.getOffsetForPosition(Offset(rect.left + eps, y))
//                    val right = layout.getOffsetForPosition(Offset(maxOf(rect.left + eps, rect.right - eps), y))
//
//                    val s = minOf(left, right).coerceIn(0, endIndex)
//                    val e = maxOf(left, right).coerceIn(0, endIndex)
//                    return s to e
//                }
//
//                val laidOutText = textLayout.layoutInput.text
//                val textLen = laidOutText.length
//                val endIndex = textLen - 1
//
//                // ✅ Always update length state (even when empty)
//                lastLaidOutTextLen = textLen
//
//                // Compute safeStartIndex even if empty; clamp to [0, textLen]
//                val safeStartIndex = startIndex.coerceIn(0, textLen)
//
//                // ✅ Always update fullyRevealed deterministically
//                // If empty text => endIndex = -1 => safeStartIndex (>=0) > -1 => fullyRevealed = true
//                fullyRevealed = safeStartIndex > endIndex
//
//                // Determine if there's a new range to process
//                val hasNewRange = safeStartIndex <= endIndex
//
//                if (!hasNewRange) {
//                    prevTextLen = textLen
//                    prevAnchorLine = if (endIndex >= 0) textLayout.getLineForOffset(endIndex) else -1
//                    return@Text
//                }
//
//                // If there is unrevealed text, advance progress *unconditionally*.
//                // (Never gate on rect production/dedupe.)
//                onStartIndexChange(endIndex + 1)
//
//                // Anchor = last char of PREVIOUS text (tail that can wrap)
//                val anchorOffset = (prevTextLen - 1).coerceIn(0, endIndex)
//                val newAnchorLine = if (endIndex >= 0) textLayout.getLineForOffset(anchorOffset) else -1
//                val didWrapDown = (prevAnchorLine >= 0 && newAnchorLine > prevAnchorLine)
//
//                if (didWrapDown) {
//
//                    // ✅ locate victim by stored offset-range meta (not geometry)
//                    val victimIndex = rectList.indexOfLast { r ->
//                        val meta = rectMetaById[r.id]
//                        meta != null &&
//                                meta.line == prevAnchorLine &&
//                                anchorOffset in meta.startOffset..meta.endOffset
//                    }
//
//                    if (victimIndex != -1) {
//                        val victim = rectList[victimIndex]
//                        val victimId = victim.id
//
//                        // ✅ guard: do split/patch only once per textLen for this victimId
//                        val doneLen = splitDoneForTextLenByVictimId[victimId]
//                        if (doneLen != textLen) {
//
//                            val victimMeta = rectMetaById[victimId]
//                            if (victimMeta != null) {
//
//                                val prevLineEnd = lastVisibleOffsetOnLine(textLayout, prevAnchorLine)
//
//                                // If victim range crosses the previous line end, we must SPLIT it
//                                val crosses =
//                                    victimMeta.startOffset <= prevLineEnd && victimMeta.endOffset > prevLineEnd
//
//                                if (crosses) {
//                                    val prevRangeStart = victimMeta.startOffset
//                                    val prevRangeEnd = prevLineEnd.coerceAtMost(endIndex)
//
//                                    val nextRangeStart = (prevLineEnd + 1).coerceAtMost(endIndex)
//                                    val nextRangeEnd = victimMeta.endOffset.coerceAtMost(endIndex)
//
//                                    // Recompute prev-line piece(s)
//                                    val prevRects = calculateBoundingRectList(
//                                        textLayoutResult = textLayout,
//                                        startIndex = prevRangeStart,
//                                        endIndex = prevRangeEnd,
//                                        segmentation = segmentation
//                                    ).filter { r -> lineForRect(textLayout, r) == prevAnchorLine }
//
//                                    // Recompute next-line piece(s)
//                                    val nextRects = if (nextRangeStart <= nextRangeEnd) {
//                                        calculateBoundingRectList(
//                                            textLayoutResult = textLayout,
//                                            startIndex = nextRangeStart,
//                                            endIndex = nextRangeEnd,
//                                            segmentation = segmentation
//                                        ).filter { r -> lineForRect(textLayout, r) == newAnchorLine }
//                                    } else {
//                                        emptyList()
//                                    }
//
//                                    // ✅ Patch victim IN PLACE to become the prev-line piece (keep animatable)
//                                    if (prevRects.isNotEmpty()) {
//                                        val patchedPrev = prevRects.first()
//                                        rectList[victimIndex] = victim.copy(
//                                            rect = patchedPrev,
//                                            animatable = victim.animatable
//                                        )
//                                        val (s, e) = offsetsForRect(textLayout, patchedPrev, endIndex)
//                                        rectMetaById[victimId] = RectMeta(
//                                            line = prevAnchorLine,
//                                            startOffset = s,
//                                            endOffset = e
//                                        )
//
//                                        // Add remaining prev rects as extra pieces (same animatable, no scheduling)
//                                        if (prevRects.size > 1) {
//                                            prevRects.drop(1).forEachIndexed { idx, r ->
//                                                val newId = "${victimId}_p$idx${r.top}_${r.left}_${r.right}_${r.bottom}"
//                                                if (!rectMetaById.containsKey(newId)) {
//                                                    val (ps, pe) = offsetsForRect(textLayout, r, endIndex)
//                                                    rectMetaById[newId] = RectMeta(
//                                                        line = prevAnchorLine,
//                                                        startOffset = ps,
//                                                        endOffset = pe
//                                                    )
//                                                }
//                                                rectList.add(
//                                                    RectWithAnimation(
//                                                        id = newId,
//                                                        rect = r,
//                                                        startIndex = victim.startIndex,
//                                                        endIndex = victim.endIndex,
//                                                        animatable = victim.animatable
//                                                    )
//                                                )
//                                            }
//                                        }
//
//                                        // Add next line rects as extra pieces (same animatable, no scheduling)
//                                        nextRects.forEachIndexed { idx, r ->
//                                            val newId = "${victimId}_n$idx${r.top}_${r.left}_${r.right}_${r.bottom}"
//                                            if (!rectMetaById.containsKey(newId)) {
//                                                val (ns, ne) = offsetsForRect(textLayout, r, endIndex)
//                                                rectMetaById[newId] = RectMeta(
//                                                    line = newAnchorLine,
//                                                    startOffset = ns,
//                                                    endOffset = ne
//                                                )
//                                            }
//                                            rectList.add(
//                                                RectWithAnimation(
//                                                    id = newId,
//                                                    rect = r,
//                                                    startIndex = victim.startIndex,
//                                                    endIndex = victim.endIndex,
//                                                    animatable = victim.animatable
//                                                )
//                                            )
//                                        }
//                                    }
//
//                                    // mark done for this victimId at this textLen
//                                    splitDoneForTextLenByVictimId[victimId] = textLen
//                                } else {
//                                    // If it didn't cross, do nothing (no patch). This avoids clobbering ", and ".
//                                    splitDoneForTextLenByVictimId[victimId] = textLen
//                                }
//                            }
//                        }
//                    }
//                }
//
//                // If wrapped, restart from start of the NEW line. Otherwise continue from safeStartIndex.
//                val computeStart: Int =
//                    if (didWrapDown) textLayout.getLineStart(newAnchorLine) else safeStartIndex
//
//                // Only build/enqueue rects if we have a valid range
//                val producedRects: List<RectWithAnimation> =
//                    calculateBoundingRectList(
//                        textLayoutResult = textLayout,
//                        startIndex = computeStart.coerceIn(0, endIndex),
//                        endIndex = endIndex,
//                        segmentation = segmentation
//                    ).map { rect ->
//                        val id = "${computeStart}_${endIndex}_${rect.top}_${rect.left}_${rect.right}_${rect.bottom}"
//
//                        // store meta for victim selection later (no change to RectWithAnimation)
//                        if (!rectMetaById.containsKey(id)) {
//                            val line = lineForRect(textLayout, rect)
//                            val (s, e) = offsetsForRect(textLayout, rect, endIndex)
//                            rectMetaById[id] = RectMeta(line = line, startOffset = s, endOffset = e)
//                        }
//
//                        RectWithAnimation(
//                            id = id,
//                            rect = rect,
//                            startIndex = computeStart.coerceIn(0, endIndex),
//                            endIndex = endIndex
//                        )
//                    }
//
//                // Dedupe before adding/sending
//                val existingRectIds = rectList.asSequence().map { it.id }.toHashSet()
//                val filtered =
//                    producedRects.filter { it.id !in jobsByRectId && it.id !in existingRectIds }
//
//                if (filtered.isNotEmpty()) {
//                    rectList.addAll(filtered)
//                    rectBatchChannel.trySend(filtered)
//                }
//
//                prevTextLen = textLen
//                prevAnchorLine = newAnchorLine
//            }
//        )
//    }
//}
//
//private fun ContentDrawScope.drawFadeInRects(
//    rectList: List<RectWithAnimation>,
//    debug: Boolean = false
//) {
//    rectList.forEachIndexed { _, rectWithAnimation ->
//
//        val progress = rectWithAnimation.animatable.value
//        val rect = rectWithAnimation.rect
//        val topLeft = rect.topLeft
//        val rectSize = rect.size
//
//        drawRect(
//            color = Color.Red.copy(1 - progress),
//            topLeft = topLeft,
//            size = rectSize,
//            blendMode = BlendMode.DstOut
//        )
//
//        // For Debugging
//        if (debug) {
//            drawRect(
//                color = lerp(Color.Red, Color.Green, progress),
//                topLeft = topLeft,
//                size = rectSize,
//                style = Stroke(width = 2.dp.toPx())
//            )
//        }
//    }
//}
