package com.smarttoolfactory.tutorial4_1chatbot.samples

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.forEachIndexed
import kotlin.coroutines.cancellation.CancellationException

private val deltas = listOf(
    "defined ", "by volatility, complexity", ", and accelerating\n",
    "change. Markets evolve ", "faster than planning\n",
    "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. ",
    " cycles ", "customer", " expectations shift", " continuously."
)

@Preview
@Composable
private fun TrailFadeInParallelPreview() {
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

    Column(
        modifier = Modifier.systemBarsPadding().verticalScroll(rememberScrollState())
    ) {
        Text("TrailFadeInText", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        TrailFadeInText(
            text = chunkText,
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )

        Text("TrailFadeInTextParallelWithChannel", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        TrailFadeInTextParallelWithChannel(
            text = chunkText,
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )

        Text("TrailFadeInTextThatLags", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        TrailFadeInTextThatLags(
            text = chunkText,
            modifier = Modifier.fillMaxWidth().height(160.dp)
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
private fun TrailFadeInText(
    modifier: Modifier = Modifier,
    text: String,
    start: Int = 0,
    end: Int = text.lastIndex,
    style: TextStyle = TextStyle.Default
) {

    var startIndex by remember {
        mutableIntStateOf(start)
    }

    var endIndex by remember {
        mutableIntStateOf(end)
    }

    val rectList = remember {
        mutableStateListOf<RectWithAnimation>()
    }

    val scope = rememberCoroutineScope()

    Text(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawFadeInRects(rectList)
            },
        onTextLayout = { textLayout: TextLayoutResult ->
            endIndex = text.lastIndex

            if (text.isNotEmpty()) {

                // If rects are not added in onTextLayout, like LaunchedEffect etc, it lags
                // behind and some deltas are shown since rectangle lag behind them.

                val newList: List<RectWithAnimation> =
                    calculateBoundingRects(
                        textLayoutResult = textLayout,
                        startIndex = startIndex,
                        endIndex = endIndex
                    ).map {
                        RectWithAnimation(
                            rect = it,
                            startIndex = startIndex,
                            endIndex = end
                        )
                    }

//                val newList: List<RectWithAnimation> = computeDiffRects(
//                    layout = textLayout,
//                    start = startIndex,
//                    endExclusive = endIndex + 1
//                ).map {
//                    RectWithAnimation(
//                        rect = it
//                    )
//                }

                rectList.addAll(newList)
                startIndex = endIndex + 1

                newList.forEachIndexed { index, rectWithAnimation ->
                    scope.launch {
                        delay(20L * index)
                        try {
                            rectWithAnimation.animatable.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(1000, easing = LinearEasing)
                            )
                            delay(60)
                            rectList.remove(rectWithAnimation)
                        } catch (e: CancellationException) {
                            println(
                                "CANCELED for " +
                                        "startIndex: $startIndex, " +
                                        "endIndex: $endIndex, " +
                                        "index: $index.\n" +
                                        "message: ${e.message}"
                            )
                        }
                    }
                }
            }
        },
        text = text,
        style = style
    )
}


@Composable
private fun TrailFadeInTextParallelWithChannel(
    modifier: Modifier = Modifier,
    text: String,
    start: Int = 0,
    end: Int = text.lastIndex,
    style: TextStyle = TextStyle.Default
) {
    var startIndex by remember { mutableIntStateOf(start) }
    var endIndex by remember { mutableIntStateOf(end) }

    val rectList = remember { mutableStateListOf<RectWithAnimation>() }

    // Queue of rect batches coming from onTextLayout
    val rectBatchChannel = remember {
        Channel<List<RectWithAnimation>>(
            capacity = Channel.UNLIMITED
        )
    }

    // Track jobs so each rect starts once, and can optionally clean up.
    val jobsByRectId = remember { mutableStateMapOf<String, Job>() }

    // One long-lived "dispatcher" coroutine; does not restart on new layouts.
    LaunchedEffect(Unit) {
        for (batch in rectBatchChannel) {
            batch.forEachIndexed { index, rectWithAnimation ->
                // If you might enqueue the same instance again, guard it.
                val id = rectWithAnimation.id
                if (jobsByRectId.containsKey(id)) return@forEachIndexed

                val job = launch {
                    // Optional stagger, keeps parallel but slightly cascaded.
                    delay(20L * index)

                    try {
                        rectWithAnimation.animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(1000, easing = LinearEasing)
                        )
                        delay(60)

                         rectList.remove(rectWithAnimation)

                    } catch (e: CancellationException) {
                        println(
                            "CANCELED for " +
                                    "startIndex: $startIndex, " +
                                    "endIndex: $endIndex, " +
                                    "index: $index.\n" +
                                    "message: ${e.message}"
                        )
                    } finally {
                        jobsByRectId.remove(id)
                    }
                }

                jobsByRectId[id] = job
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
        onTextLayout = { textLayout ->
            endIndex = text.lastIndex

            val newRects = calculateBoundingRects(
                textLayoutResult = textLayout,
                startIndex = startIndex,
                endIndex = endIndex
            ).map { rect ->
                RectWithAnimation(
                    id = "${startIndex}_${endIndex}_${rect.top}_${rect.left}_${rect.right}_${rect.bottom}",
                    rect = rect,
                    startIndex = startIndex,
                    endIndex = end
                )
            }

            startIndex = endIndex + 1

            // Make them visible immediately
            rectList.addAll(newRects)

            // Kick off animations without causing cancellation of previous ones
            rectBatchChannel.trySend(newRects)
        },
        text = text,
        style = style
    )
}

/*
    This one lags one frame because of getting Rect list in LaunchedEffect instead of
    onTextLayout. LaunchedEffect is called in next frame after TextLayoutResult is assigned to
    MutableState
 */
@Composable
private fun TrailFadeInTextThatLags(
    modifier: Modifier = Modifier,
    text: String,
    start: Int = 0,
    end: Int = text.lastIndex,
    style: TextStyle = TextStyle.Default
) {

    var startIndex by remember {
        mutableIntStateOf(start)
    }

    var endIndex by remember {
        mutableIntStateOf(end)
    }

    val rectList = remember {
        mutableStateListOf<RectWithAnimation>()
    }

    val scope = rememberCoroutineScope()

    var result by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }

    LaunchedEffect(result) {
        result?.let { textLayout ->
            // If rects are not added in onTextLayout, like LaunchedEffect etc, it lags
            // behind and some deltas are shown since rectangle lag behind them.

            val newList: List<RectWithAnimation> =
                calculateBoundingRects(
                    textLayoutResult = textLayout,
                    startIndex = startIndex,
                    endIndex = endIndex
                ).map {
                    RectWithAnimation(
                        rect = it,
                        startIndex = startIndex,
                        endIndex = end
                    )
                }

            rectList.addAll(newList)
            startIndex = endIndex + 1

            newList.forEachIndexed { index, rectWithAnimation ->
                scope.launch {
                    delay(20L * index)
                    try {
                        rectWithAnimation.animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(1000, easing = LinearEasing)
                        )
                        delay(60)
                        rectList.remove(rectWithAnimation)
                    } catch (e: CancellationException) {
                        println(
                            "CANCELED for " +
                                    "startIndex: $startIndex, " +
                                    "endIndex: $endIndex, " +
                                    "index: $index.\n" +
                                    "message: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    Text(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawFadeInRects(rectList)
            },
        onTextLayout = { textLayout: TextLayoutResult ->
            endIndex = text.lastIndex
            result = textLayout
        },
        text = text,
        style = style
    )
}


private fun ContentDrawScope.drawFadeInRects(rectList: List<RectWithAnimation>) {
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
//        drawRect(
//            color = lerp(Color.Red, Color.Green, progress),
//            topLeft = topLeft,
//            size = rectSize,
//            style = Stroke(2.dp.toPx())
//        )
    }
}
