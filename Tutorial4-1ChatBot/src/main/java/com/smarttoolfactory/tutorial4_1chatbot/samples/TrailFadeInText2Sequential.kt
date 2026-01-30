package com.smarttoolfactory.tutorial4_1chatbot.samples

import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.RectWithAnimation
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.calculateBoundingRectList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.coroutines.cancellation.CancellationException

private val deltas = listOf(
    "defined ", "by volatility, complexity", ", and accelerating\n",
    "change. Markets evolve ", "faster than planning\n",
    "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. ",
    " cycles ", "customer", " expectations shift", " continuously."
)

@Preview
@Composable
private fun TrailFadeInSequentialPreview() {
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
            wordsPerEmission = 5
        ).collect {
            chunkText += it
        }
    }

    Column(
        modifier = Modifier.systemBarsPadding()
    ) {

        val context = LocalContext.current

        Text("TrailFadeInSequentialChannelText", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))

        TrailFadeInSequentialChannelText(
            text = chunkText,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            onComplete = {
                Toast.makeText(
                    context,
                    "TrailFadeInSequentialChannelText completed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        Text("TrailFadeInSequentialSharedFlowText", fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        TrailFadeInSequentialSharedFlowText(
            text = chunkText,
            modifier = Modifier.fillMaxWidth().height(160.dp),
            onComplete = {
                Toast.makeText(
                    context,
                    "TrailFadeInSequentialSharedFlowText completed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        OutlinedTextField(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            value = text,
            onValueChange = {
                text = it
            }
        )

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

        Button(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            onClick = {
                chunkText += text
            }
        ) {
            Text("Append")
        }
    }
}

@Composable
private fun TrailFadeInSequentialChannelText(
    modifier: Modifier = Modifier,
    text: String,
    start: Int = 0,
    end: Int = text.lastIndex,
    style: TextStyle = TextStyle.Default,
    onComplete: () -> Unit = {}
) {
    var startIndex by remember { mutableIntStateOf(start) }
    var endIndex by remember { mutableIntStateOf(end) }

    val rectList = remember { mutableStateListOf<RectWithAnimation>() }

    // A queue of "new rect batches" coming from onTextLayout
    val rectBatchChannel = remember {
        Channel<List<RectWithAnimation>>(
            capacity = Channel.UNLIMITED
        )
    }

    // One long-lived consumer: never restarted -> never cancels previous work due to new layouts
    LaunchedEffect(Unit) {

        for (batch in rectBatchChannel) {

            println("LaunchedEffect Batch size: ${batch.size}")
            // If you want per-batch staggering:
            batch.forEachIndexed { index, rectWithAnimation: RectWithAnimation ->
                // Sequential processing (stable, predictable)
                try {
                    val duration = (rectWithAnimation.rect.width * 1.2f).toInt()
                    rectWithAnimation.animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(duration, easing = LinearEasing)
                    )
                    // rectList.remove(rwa) // optional cleanup
                } catch (e: CancellationException) {
                    // Only cancelled if the composable leaves composition
                    println(
                        "CANCELED for " +
                                "startIndex: $startIndex, " +
                                "endIndex: $endIndex, " +
                                "index: $index.\n" +
                                "message: ${e.message}"
                    )
                } finally {
                    if (rectWithAnimation.endIndex == text.lastIndex) {
                        onComplete()
                    }
                }
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

            val newRects = calculateBoundingRectList(
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

            startIndex = endIndex + 1

            // Mutate UI state here (main thread) so rects appear immediately
            rectList.addAll(newRects)

            // Enqueue animation work (never restarts/cancels consumer)
            rectBatchChannel.trySend(newRects)
        },
        text = text,
        style = style
    )
}

@Composable
private fun TrailFadeInSequentialSharedFlowText(
    modifier: Modifier = Modifier,
    text: String,
    start: Int = 0,
    end: Int = text.lastIndex,
    style: TextStyle = TextStyle.Default,
    onComplete: () -> Unit = {}
) {
    var startIndex by remember { mutableIntStateOf(start) }
    var endIndex by remember { mutableIntStateOf(end) }

    val rectList = remember { mutableStateListOf<RectWithAnimation>() }

    val rectSharedFlow = remember {
        MutableSharedFlow<List<RectWithAnimation>>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.SUSPEND
        )
    }

    LaunchedEffect(Unit) {
        rectSharedFlow.collect { batch ->
            batch.forEachIndexed { _, rectWithAnimation ->

                val duration = (rectWithAnimation.rect.width * 1.2f).toInt()
                rectWithAnimation.animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(duration, easing = LinearEasing)
                )

                if (rectWithAnimation.endIndex == text.lastIndex) {
                    onComplete()
                }
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

            val newRects = calculateBoundingRectList(
                textLayoutResult = layout,
                startIndex = startIndex,
                endIndex = endIndex
            ).map {
                RectWithAnimation(
                    rect = it,
                    startIndex = startIndex,
                    endIndex = end
                )
            }

            startIndex = endIndex + 1

            rectList.addAll(newRects)
            rectSharedFlow.tryEmit(newRects)
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

        // When alpha is 1 rect is visible so nothing is shown
        // If alpha of left top goes to 0 faster it will be revealed earlier than
        // bottom right of the rectangle
        // This is visible when rectangles are bigger than single word
        val alphaLeft = if (progress == 0f) 1f else (1 - progress * 4f).coerceIn(0f, 1f)
        val alphaRight = if (progress == 0f) 1f else (1 - progress).coerceIn(0f, 1f)

        val brush = Brush.linearGradient(
            colors = listOf(
                Color.Red.copy(alphaLeft),
                Color.Red.copy(alphaRight),
            ),
            start = rect.topLeft,
            end = rect.bottomRight
        )

        drawRect(
            brush = brush,
            topLeft = topLeft,
            size = rectSize,
            blendMode = BlendMode.DstOut
        )

//        drawRect(
//            color = Color.Red.copy(1 - progress),
//            topLeft = Offset(posX, topLeft.y),
//            size = Size(animatedWidth, rectHeight),
////            blendMode = BlendMode.DstOut
//        )

//        drawRect(
//            color = Color.Red.copy(1 - progress),
//            topLeft = topLeft,
//            size = rectSize,
////            blendMode = BlendMode.DstOut
//        )

        // For Debugging
        drawRect(
            color = lerp(Color.Red, Color.Green, progress),
            topLeft = topLeft,
            size = rectSize,
            style = Stroke(2.dp.toPx())
        )
    }
}