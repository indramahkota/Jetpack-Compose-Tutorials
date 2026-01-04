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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
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

        BlurRevealText(
            text = text,
            style = TextStyle.Default.copy(fontSize = 20.sp)
        )
    }
}


@Composable
fun BlurRevealText(
    modifier: Modifier = Modifier,
    text: String,
    style: TextStyle = TextStyle.Default
) {
    val rectList = remember {
        mutableStateListOf<RectWithAnimation>()
    }

    LaunchedEffect(rectList) {
        delay(1000)
        // Snapshot copy so mutation during iteration won’t crash
        val items = rectList.toList()

        var duration: Int
        items.forEach { item ->
            duration = (1 * item.rect.width).toInt()
            launch {
                item.animatable.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = LinearEasing
                    )
                )
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
                    ).map {
                        RectWithAnimation(rect = it)
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

// Scale x1 from a1..b1 range to a2..b2 range
internal fun scale(a1: Float, b1: Float, x1: Float, a2: Float, b2: Float) =
    lerp(a2, b2, calcFraction(a1, b1, x1))


// Calculate the 0..1 fraction that `pos` value represents between `a` and `b`
private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)