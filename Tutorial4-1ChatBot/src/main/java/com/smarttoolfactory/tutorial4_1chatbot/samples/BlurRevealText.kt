package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.indicator.scale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Preview
@Composable
fun BlurRevealTextPreview() {
    Column(
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize()
            .padding(32.dp)
    ) {

        val text =
            "Lorem Ipsum\n is simply **dummy** çöüğı<>₺ş- text of the printing and typesetting industry.\n" +
                    "Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, " +
                    "when an unknown printer took a galley of type and scrambled " +
                    "it to make a type specimen book. It has survived not only five centuries, but " +
                    "also the leap into electronic typesetting, remaining essentially unchanged. "

        var completed1 by remember {
            mutableStateOf(false)
        }

        var completed2 by remember {
            mutableStateOf(false)
        }

        Text("BlurRevealText", fontSize = 18.sp, color = if (completed1) Color.Green else Color.Red)

        BlurRevealText(
            text = text,
            style = TextStyle.Default.copy(fontSize = 18.sp),
            onComplete = {
                completed1 = true
            }
        )

        Text(
            "BlurRevealTextWithCallback",
            fontSize = 18.sp,
            color = if (completed2) Color.Green else Color.Red
        )

        BlurRevealTextWithCallback(
            text = text,
            style = TextStyle.Default.copy(fontSize = 18.sp),
            onComplete = {
                completed2 = true
            }
        )
    }
}

@Composable
fun BlurRevealText(
    modifier: Modifier = Modifier,
    text: String,
    style: TextStyle = TextStyle.Default,
    onComplete: () -> Unit = {}
) {
    val rectList = remember {
        mutableStateListOf<RectWithAnimation>()
    }

    LaunchedEffect(rectList) {
        delay(1000)
        // Snapshot copy so mutation during iteration won’t crash
        val items: List<RectWithAnimation> = rectList.toList()

        var duration: Int
        items.forEach { rectWithAnimation ->
            duration = (1 * rectWithAnimation.rect.width).toInt()
            launch {
                rectWithAnimation.animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = LinearEasing
                    )
                )

                if (rectWithAnimation.endIndex == text.lastIndex) {
                    onComplete()
                }

            }

            delay((duration * .9f).toLong())
        }
    }
    Box(modifier) {
        Text(
            modifier = Modifier.alpha(0f),
            onTextLayout = {
                rectList.clear()
                rectList.addAll(
                    computeDiffRects(
                        layout = it,
                        start = 0,
                        endExclusive = text.length
                    ).map { rect ->
                        RectWithAnimation(
                            rect = rect,
                            startIndex = 0,
                            endIndex = text.length - 1
                        )
                    }
                )
            },
            text = text,
            style = style
        )

        rectList.forEach { rectWithAnimation ->

            val alpha = rectWithAnimation.animatable.value
            val rect = rectWithAnimation.rect
            val rectSize = rect.size
            Text(
                modifier = modifier
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .blur((scale(.3f, 1f, alpha, 6f, 0f)).dp)
                    .drawWithContent {
                        clipRect(
                            top = rect.top,
                            left = rect.left,
                            bottom = rect.bottom,
                            right = rect.right
                        ) {
                            this@drawWithContent.drawContent()

                            drawRect(
                                color = Color.White,
                                topLeft = Offset(
                                    alpha * rectSize.width,
                                    rect.top
                                ),
                                size = Size(
                                    width = (1 - alpha) * rectSize.width,
                                    height = rectSize.height
                                ),
                                blendMode = BlendMode.DstOut
                            )
                        }
                    },
                text = text,
                style = style
            )
        }
    }
}

@Composable
fun BlurRevealTextWithCallback(
    modifier: Modifier = Modifier,
    text: String,
    style: TextStyle = TextStyle.Default,
    initialDelay: Long = 1000L,
    onComplete: () -> Unit = {}
) {
    val rectList = remember { mutableStateListOf<RectWithAnimation>() }

    // generation + completion gating (prevents multiple/early callbacks)
    var generation by remember { mutableIntStateOf(0) }
    var lastCompletedGeneration by remember { mutableIntStateOf(-1) }

    LaunchedEffect(generation) {
        // Only run when we have rects for this generation
        if (rectList.isEmpty()) return@LaunchedEffect

        delay(initialDelay)

        val currentGen = generation
        val items: List<RectWithAnimation> = rectList.toList()
        val total = items.size
        if (total == 0) return@LaunchedEffect

        var completedCount = 0

        items.forEach { rectWithAnimation ->
            val duration = (1 * rectWithAnimation.rect.width).toInt().coerceAtLeast(1)

            launch {
                rectWithAnimation.animatable.snapTo(0f)
                try {
                    rectWithAnimation.animatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = duration,
                            easing = LinearEasing
                        )
                    )
                } finally {
                    rectWithAnimation.animatable.snapTo(1f)
                }

                // completion counter for this generation
                completedCount++

                // Fire exactly once, only for the latest generation
                if (completedCount == total &&
                    currentGen == generation &&
                    lastCompletedGeneration != currentGen
                ) {
                    lastCompletedGeneration = currentGen
                    onComplete()
                }
            }

            delay((duration * .9f).toLong())
        }
    }

    Box(modifier) {
        Text(
            modifier = Modifier.alpha(0f),
            onTextLayout = {
                // Reset for new text/layout
                rectList.clear()
                rectList.addAll(
                    computeDiffRects(
                        layout = it,
                        start = 0,
                        endExclusive = text.length
                    ).map { rect ->
                        RectWithAnimation(
                            rect = rect,
                            startIndex = 0,
                            endIndex = text.length - 1
                        )
                    }
                )

                // bump generation so LaunchedEffect restarts for the new rect set
                generation++
            },
            text = text,
            style = style
        )

        rectList.forEach { rectWithAnimation ->
            val alpha = rectWithAnimation.animatable.value
            val rect = rectWithAnimation.rect
            val rectSize = rect.size

            Text(
                modifier = modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .blur((scale(.3f, 1f, alpha, 6f, 0f)).dp)
                    .drawWithContent {
                        clipRect(
                            top = rect.top,
                            left = rect.left,
                            bottom = rect.bottom,
                            right = rect.right
                        ) {
                            this@drawWithContent.drawContent()

                            drawRect(
                                color = Color.White,
                                topLeft = Offset(
                                    alpha * rectSize.width,
                                    rect.top
                                ),
                                size = Size(
                                    width = (1 - alpha) * rectSize.width,
                                    height = rectSize.height
                                ),
                                blendMode = BlendMode.DstOut
                            )
                        }
                    },
                text = text,
                style = style
            )
        }
    }
}
