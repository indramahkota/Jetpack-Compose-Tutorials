package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

fun Flow<String>.chunkWordsWithDelay(
    maxWords: Int = 10,
    delayMillis: Long = 300L
): Flow<String> = flow {
    val buffer = mutableListOf<String>()

    collect { delta ->
        val words = delta
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        for (word in words) {
            buffer.add(word)

            if (buffer.size == maxWords) {
                emit(buffer.joinToString(" ") + " ")
                delay(delayMillis)
                buffer.clear()
            }
        }
    }

    // Flush remaining words on completion
    if (buffer.isNotEmpty()) {
        emit(buffer.joinToString(" "))
        delay(delayMillis)
    }
}


@Preview
@Composable
fun TrailFadeInTextPreview() {
    var text by remember {
        mutableStateOf("")
    }

    var chunkText by remember {
        mutableStateOf("")
    }

    val deltas = remember {
        listOf(
            "defined ", "by volatility, complexity", ", and accelerating\n",
            "change. Markets evolve ", "faster than planning\n",
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. ",
            " cycles ", "customer", " expectations shift", " continuously."
        )
    }

    LaunchedEffect(Unit) {
        delay(1000)
        deltas.asFlow().chunkWordsWithDelay(
            maxWords = 1,
            delayMillis = 60
        ).collect {
            println("COLLECT: $it")
            chunkText += it
        }
    }

    Column {
        TrailFadeInText(chunkText)
        Spacer(modifier = Modifier.weight(1f))

        OutlinedTextField(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth(),
            value = text,
            onValueChange = {
                text = it
            }
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                chunkText += text
            }
        ) {
            Text("Append")
        }
    }
}

@Composable
private fun TrailFadeInText(text: String) {
    var startIndex by remember {
        mutableIntStateOf(text.lastIndex.coerceAtLeast(0))
    }

    val rectList = remember {
        mutableStateListOf<RectWithAnimation>()
    }

    val scope = rememberCoroutineScope()
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                rectList.forEachIndexed { index, rectWithAnimation ->

                    val progress = rectWithAnimation.animatable.value
                    val topLeft = rectWithAnimation.rect.topLeft
                    val rectSize = rectWithAnimation.rect.size

                    val width = rectSize.width
                    val height = rectSize.height
                    val posX = topLeft.x + progress * width
                    val animatedWidth = (1 - progress) * width

                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Red.copy((-0.25f + 1f - progress).coerceIn(0f, 1f)),
                            Color.Red.copy(1f - progress),
                        ),
                        start = rectWithAnimation.rect.topLeft,
                        end = rectWithAnimation.rect.bottomRight,
                    )

//                    drawRect(
//                        brush = brush,
//                        topLeft = topLeft,
//                        size = rectSize,
//                        blendMode = BlendMode.DstOut
//                    )

                    drawRect(
                        color = Color.Red.copy(1 - progress),
                        topLeft = topLeft,
                        size = rectSize,
//                        topLeft = Offset(posX, topLeft.y),
//                        size = Size(animatedWidth, height),
                        blendMode = BlendMode.DstOut
                    )
//                    drawRect(
//                        color = lerp(
//                            start = Color.Green,
//                            stop = Color.Red,
//                            fraction = progress
//                        ).copy(alpha = progress),
//                        topLeft = topLeft,
//                        size = rectSize,
//                        style = Stroke(4.dp.toPx())
//                    )

                }
            },
        onTextLayout = { textLayout: TextLayoutResult ->
            val endIndex = text.lastIndex

//            val newCharCount = (endIndex - startIndex).coerceAtLeast(0)

            if (text.isNotEmpty()) {
                val newList: List<RectWithAnimation> =
                    calculateBoundingRects(
                        textLayoutResult = textLayout,
                        startIndex = startIndex,
                        endIndex = endIndex
                    ).map {
                        RectWithAnimation(
                            rect = it
                        )
                    }

                rectList.addAll(newList)
                newList.forEachIndexed { index, rectWithAnimation ->
                    scope.launch {
                        delay(20L * index)
                        try {
                            rectWithAnimation.animatable.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(1000, easing = LinearEasing)
                            )
                            delay(200)
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
                    startIndex = endIndex + 1
                }
            }
        },
        text = text,
        fontSize = 18.sp
    )
}

data class RectWithAnimation(
    val rect: Rect,
    val animatable: Animatable<Float, AnimationVector1D> = Animatable(0f),
    var alpha: Float = 0f
)
