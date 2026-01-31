package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextStyle
import com.smarttoolfactory.tutorial4_1chatbot.markdown.MarkdownComposer
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.LineSegmentation
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.RectWithAnimation
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.calculateBoundingRectList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    var completed1 by remember {
        mutableStateOf(false)
    }

    var completed2 by remember {
        mutableStateOf(false)
    }

    var completed3 by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(startAnimation) {
        delay(1000)
        singleLongText.toWordFlow(
            delayMillis = 60,
            wordsPerEmission = 3
        ).collect {
            println("Collect: $it")
            chunkText += it
        }
    }

    Column(modifier = Modifier.systemBarsPadding()) {

        Text(
            "TrailFadeInTextWithCallback",
            fontSize = 18.sp,
            color = if (completed1) Color.Green else Color.Red
        )
        TrailFadeInTextWithCallback(
            text = chunkText,
//            text = singleLongText,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            onCompleted = {
                println("🔥 COMPLETED 1")
                completed1 = true
            }
        )
        Text(
            "MarkdownComposer",
            fontSize = 18.sp,
            color = if (completed3) Color.Green else Color.Red
        )

        BasicRichText(
            modifier = Modifier,
            style = RichTextStyle.Default
        ) {
            MarkdownComposer(
                markdown = chunkText,
//            markdown = singleLongText,
                useFirst = false,
                debug = true,
                onCompleted = {
                    println("🔥🔥🔥 COMPLETED 3")
                    completed3 = true
                }
            )
        }
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
    style: TextStyle = TextStyle.Default,
    delayInMillis: Long = 90L,
    revealCoefficient: Float = 4f,
    lingerInMillis: Long = 90L,
    segmentation: LineSegmentation = LineSegmentation.None,
    debug: Boolean = true,
    onCompleted: () -> Unit
) {

    var startIndex by remember {
        mutableIntStateOf(0)
    }

    TrailFadeInTextWithCallback(
        modifier = modifier,
        text = text,
        style = style,
        delayInMillis = delayInMillis,
        revealCoefficient = revealCoefficient,
        lingerInMillis = lingerInMillis,
        segmentation = segmentation,
        debug = debug,
        startIndex = startIndex,
        onStartIndexChange = {
            startIndex = it
        },
        onCompleted = onCompleted

    )
}

@Composable
private fun TrailFadeInTextWithCallback(
    modifier: Modifier = Modifier,
    text: String,
    style: TextStyle = TextStyle.Default,
    delayInMillis: Long = 90L,
    revealCoefficient: Float = 4f,
    lingerInMillis: Long = 90L,
    segmentation: LineSegmentation = LineSegmentation.None,
    debug: Boolean = true,
    startIndex: Int,
    onStartIndexChange: (Int) -> Unit,
    onCompleted: () -> Unit
) {
    val rectList = remember { mutableStateListOf<RectWithAnimation>() }

    val rectBatchChannel = remember {
        Channel<List<RectWithAnimation>>(capacity = Channel.UNLIMITED)
    }

    val jobsByRectId = remember { mutableStateMapOf<String, Job>() }

    var pendingRects by remember { mutableIntStateOf(0) }
    var fullyRevealed by remember { mutableStateOf(false) }
    var lastLaidOutTextLen by remember { mutableIntStateOf(-1) }
    var completedFiredForLen by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        for (batch in rectBatchChannel) {
            var scheduledIndex = 0

            batch.forEach { rectWithAnimation ->
                val id = rectWithAnimation.id
                if (jobsByRectId.containsKey(id)) return@forEach

                pendingRects += 1

                val myIndex = scheduledIndex
                scheduledIndex += 1

                val job = launch {
                    delay(delayInMillis * myIndex)

                    val duration = 1000
                    // val duration = (revealCoefficient * rectWithAnimation.rect.width).toInt()

                    try {
                        rectWithAnimation.animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(duration, easing = LinearEasing)
                        )
                        delay(lingerInMillis)
                    } finally {
                        pendingRects = (pendingRects - 1).coerceAtLeast(0)
                        rectList.remove(rectWithAnimation)
                        jobsByRectId.remove(id)
                    }
                }

                jobsByRectId[id] = job
            }
        }
    }

    LaunchedEffect(fullyRevealed, pendingRects, lastLaidOutTextLen) {
        val shouldComplete =
            fullyRevealed &&
                    pendingRects == 0 &&
                    lastLaidOutTextLen >= 0 &&
                    completedFiredForLen != lastLaidOutTextLen

        if (shouldComplete) {
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
            text = text,
            style = style,
            onTextLayout = { textLayout ->
                val laidOut = textLayout.layoutInput.text
                val endIndex = laidOut.lastIndex
                if (endIndex < 0) return@Text

                lastLaidOutTextLen = laidOut.length

                val safeStartIndex = startIndex.coerceIn(0, endIndex + 1)

                // This becomes true only when startIndex has advanced past endIndex.
                fullyRevealed = safeStartIndex > endIndex

                val hasNewRange = safeStartIndex <= endIndex
                if (!hasNewRange) return@Text

                // ✅ FIX #1: Advance progress whenever there is a new range,
                // independent of whether we ended up animating any rects.
                onStartIndexChange(endIndex + 1)

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

                val existingRectIds = rectList.asSequence().map { it.id }.toHashSet()
                val filtered =
                    newRects.filter { it.id !in jobsByRectId && it.id !in existingRectIds }

                if (filtered.isNotEmpty()) {
                    println(
                        "onTextLayout safeStartIndex: $safeStartIndex, " +
                                "endIndex: $endIndex, added=${filtered.size}"
                    )

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

        val rectWidth = rectSize.width
        val rectHeight = rectSize.height
        val posX = topLeft.x + progress * rectWidth
        val animatedWidth = (1 - progress) * rectWidth

        /*
            These draw rect only work if Animatables are animated sequentially
            Keep rects in queue at 0 progress are not drawn with rects
            draw only one with changing alpha or dimensions with color or brush
         */

//        val brush = Brush.linearGradient(
//            colors = listOf(
//                Color.Red.copy((-0.25f + 1f - progress).coerceIn(0f, 1f)),
//                Color.Red.copy(1f - progress),
//            ),
//            start = rect.topLeft,
//            end = rect.bottomRight,
//        )
//
//        drawRect(
//            brush = brush,
//            topLeft = topLeft,
//            size = rectSize,
//            blendMode = BlendMode.DstOut
//        )

//        drawRect(
//            color = Color.Red.copy(1 - progress),
//            topLeft = Offset(posX, topLeft.y),
//            size = Size(animatedWidth, rectHeight),
//            blendMode = BlendMode.DstOut
//        )

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
                style = Stroke(2.dp.toPx())
            )
        }

    }
}

