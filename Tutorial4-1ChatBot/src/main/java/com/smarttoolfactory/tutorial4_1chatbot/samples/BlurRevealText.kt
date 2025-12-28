package com.smarttoolfactory.tutorial4_1chatbot.samples

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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay


@Preview
@Composable
fun BlurRevealTextPreview() {

    Column(
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize()
            .padding(32.dp)
    ) {

        BlurRevealText(
            text = "Hello World and Rest\nAnd something"
        )
    }
}

@Composable
fun BlurRevealText(
    modifier: Modifier = Modifier,
    text: String
) {
    val rectList = remember {
        mutableStateListOf<RectWithAnimation>()
    }

    LaunchedEffect(rectList) {
        delay(1000)
        // Snapshot copy so mutation during iteration won’t crash
        val items = rectList.toList()

        items.forEach { item ->
            item.animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(delayMillis = 200, durationMillis = 1500)
            )
        }
    }

    val graphicsLayer = rememberGraphicsLayer()

    Column {
        Box(modifier) {
            Text(
                modifier = Modifier
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                .blur(5.dp, BlurredEdgeTreatment.Unbounded)
                    .drawWithContent {
                        drawContent()

                        rectList.forEach { rectWithAnimation ->
                            val alpha = rectWithAnimation.animatable.value
                            val rect = rectWithAnimation.rect
                            val rectSize = rect.size

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
                fontSize = 20.sp
            )
        }
        Box(
            modifier = Modifier.drawBehind {
                drawLayer(graphicsLayer)
            }
        )
    }
}