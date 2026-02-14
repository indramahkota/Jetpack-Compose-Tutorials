package com.smarttoolfactory.tutorial1_1basics.chapter6_graphics

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.NativePaint
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttoolfactory.tutorial1_1basics.ui.components.StyleableTutorialText
import com.smarttoolfactory.tutorial1_1basics.ui.gradientColors
import kotlin.math.roundToInt

@Preview
@Composable
fun Tutorial6_41Screen2() {
    TutorialContent()
}

@Composable
private fun TutorialContent() {
    Column(
        modifier = Modifier
            .systemBarsPadding()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StyleableTutorialText(
            text = "Use custom shadow modifier to draw drop shadow and animated shadows.",
            bullets = false
        )
        CustomDropShadowSample()
    }
}

@Preview
@Composable
private fun CustomDropShadowSample() {
    Column(
        modifier = Modifier.systemBarsPadding()
    ) {
        var radius by remember {
            mutableFloatStateOf(5f)
        }

        var spread by remember {
            mutableFloatStateOf(5f)
        }

        var offsetX by remember {
            mutableFloatStateOf(5f)
        }

        var offsetY by remember {
            mutableFloatStateOf(5f)
        }

        var alpha by remember {
            mutableFloatStateOf(1f)
        }

        Text(text = "radius: ${radius.roundToInt()}")
        Slider(
            value = radius,
            onValueChange = { radius = it },
            valueRange = 0f..30f,
        )

        Text(text = "spread: ${spread.roundToInt()}")
        Slider(
            value = spread,
            onValueChange = { spread = it },
            valueRange = 0f..30f,
        )

        Text(text = "offsetX: ${offsetX.roundToInt()}")
        Slider(
            value = offsetX,
            onValueChange = { offsetX = it },
            valueRange = -20f..30f,
        )

        Text(text = "offsetY: ${offsetY.roundToInt()}")
        Slider(
            value = offsetY,
            onValueChange = { offsetY = it },
            valueRange = -20f..30f,
        )

        Text(text = "alpha: $alpha")
        Slider(
            value = alpha,
            onValueChange = { alpha = it },
        )

        Spacer(modifier = Modifier.height(32.dp))

        val shape = RoundedCornerShape(16.dp)
//    val shape = CircleShape

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Box(
                Modifier
                    .size(120.dp)
                    .dropShadow(
                        shape = shape,
                        shadow = Shadow(
                            brush = Brush.sweepGradient(colors),
                            radius = radius.dp,
                            spread = spread.dp,
                            alpha = alpha,
                            offset = DpOffset(x = offsetX.dp, offsetY.dp)
                        )
                    )
                    .background(
                        color = Color.White,
                        shape = shape
                    )
            ) {
                Text(
                    "Drop Shadow",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 18.sp
                )
            }

            Box(
                Modifier
                    .size(120.dp)
                    .dropShadow(
                        shape = shape,
                        shadow = Shadow(
                            brush = Brush.sweepGradient(colors),
                            radius = radius.dp,
                            spread = spread.dp,
                            alpha = alpha,
                            offset = DpOffset(x = offsetX.dp, offsetY.dp)
                        )
                    )
                    .background(
                        color = Color.White.copy(alpha = .7f),
                        shape = shape
                    )
            ) {
                Text(
                    "Drop Shadow",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Box(
                Modifier
                    .size(120.dp)
                    .drawShadow(
                        shape = shape,
                        Shadow(
                            brush = Brush.sweepGradient(colors),
                            alpha = alpha,
                            radius = radius.dp,
                            spread = spread.dp,
                            offset = DpOffset(x = offsetX.dp, offsetY.dp)
                        )
                    )
                    .background(
                        color = Color.White,
                        shape = shape
                    )
            ) {
                Text(
                    "Custom\nDrop Shadow",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 18.sp
                )
            }

            Box(
                Modifier
                    .size(120.dp)
                    .drawShadow(
                        shape = shape,
                        Shadow(
                            brush = Brush.sweepGradient(colors),
                            alpha = alpha,
                            radius = radius.dp,
                            spread = spread.dp,
                            offset = DpOffset(x = offsetX.dp, offsetY.dp)
                        )
                    )
                    .background(
                        color = Color.White.copy(alpha = .7f),
                        shape = shape
                    )
            ) {
                Text(
                    "Custom\nDrop Shadow",
                    modifier = Modifier.align(Alignment.Center),
                    fontSize = 18.sp
                )
            }
        }
    }
}

fun Modifier.drawShadow(
    shape: Shape,
    shadow: Shadow
) = composed {

    val paint = remember { Paint().apply { style = PaintingStyle.Fill } }

    drawWithCache {
        val radiusPx = shadow.radius.toPx()
        val spreadPx = shadow.spread.toPx()
        val offsetXPx = shadow.offset.x.toPx()
        val offsetYPx = shadow.offset.y.toPx()

        val outset = spreadPx * 2f
        val shadowWidth = size.width + outset
        val shadowHeight = size.height + outset

        val outline = shape.createOutline(
            size = Size(shadowWidth, shadowHeight),
            layoutDirection = layoutDirection,
            density = this
        )

        // Update paint fields without LaunchedEffect
        paint.color = shadow.color
        paint.alpha = shadow.alpha

        paint.shader = SweepGradientShader(
            center = size.center,
            colors = colors
        )

        val frameworkPaint = paint.asFrameworkPaint()
        frameworkPaint.maskFilter = if (radiusPx > 0f) {
            BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
        } else {
            null
        }

        val translateX = offsetXPx - spreadPx
        val translateY = offsetYPx - spreadPx

        onDrawBehind {
            translate(left = translateX, top = translateY) {
                drawIntoCanvas { canvas ->
                    canvas.drawOutline(outline, paint)
                }
            }
        }
    }
}

// TODO Finish this modifier
fun Modifier.drawAnimatedShadow(
    strokeWidth: Dp,
    shape: Shape,
    brush: (Size) -> Brush = {
        Brush.sweepGradient(gradientColors)
    },
    durationMillis: Int
) = composed {

    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    // Infinite phase animation for PathEffect
    val phase by infiniteTransition.animateFloat(
        initialValue = .8f,
        targetValue = .3f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        )
    )


    val paint = remember {
        Paint().apply {
            style = PaintingStyle.Fill
        }
    }

    val frameworkPaint: NativePaint = remember {
        paint.asFrameworkPaint()
    }

    Modifier
        .drawWithCache {

            val center = size.center

            paint.shader = SweepGradientShader(
                center = center,
                colors = colors
            )

            val strokeWidthPx = strokeWidth.toPx()

            val outline: Outline = shape.createOutline(size, layoutDirection, this)

            onDrawWithContent {
                // This is actual content of the Composable that this modifier is assigned to
                drawContent()

                with(drawContext.canvas.nativeCanvas) {
                    val checkPoint = saveLayer(null, null)

                    frameworkPaint.setMaskFilter(
                        BlurMaskFilter(
                            30f * phase,
                            BlurMaskFilter.Blur.NORMAL
                        )
                    )


                    // Using a maskPath with op(this, outline.path, PathOperation.Difference)
                    // And GenericShape can be used as Modifier.border does instead of clip
                    drawOutline(
                        outline = outline,
                        color = Color.Gray,
                        style = Stroke(strokeWidthPx * 2)
                    )

                    // Source
                    paint.alpha = (.1f + phase).coerceIn(0f, 1f)

//                    rotate(angle) {
//                        this.drawCircle(
//                            center = center,
//                            radius = size.width,
//                            paint = paint
//                        )
//                    }
                    restoreToCount(checkPoint)
                }
            }
        }
}

private val colors = listOf(
    Color(0xFF4cc9f0),
    Color(0xFFf72585),
    Color(0xFFb5179e),
    Color(0xFF7209b7),
    Color(0xFF560bad),
    Color(0xFF480ca8),
    Color(0xFF3a0ca3),
    Color(0xFF3f37c9),
    Color(0xFF4361ee),
    Color(0xFF4895ef),
    Color(0xFF4cc9f0)
)
