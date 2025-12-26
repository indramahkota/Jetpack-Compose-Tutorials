package com.smarttoolfactory.tutorial4_1chatbot.ui.component.indicator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp


@Preview
@Composable
fun BouncingDotProgressIndicatorPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .padding(10.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DotProgressIndicator(
                modifier = Modifier.size(48.dp, 24.dp),
            )

            Spacer(modifier = Modifier.width(10.dp))
            DotProgressIndicator()
            Spacer(modifier = Modifier.width(10.dp))
            DotProgressIndicator(
                initialColor = Color(0xffF44336),
                animatedColor = Color(0xff29B6F6)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BouncingDotProgressIndicator(
                modifier = Modifier.size(48.dp, 24.dp),
                animatedColor = IndicatorDefaults.DotColor.copy(alpha = .5f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            BouncingDotProgressIndicator()
            Spacer(modifier = Modifier.width(10.dp))
            BouncingDotProgressIndicator(
                initialColor = Color(0xffF44336),
                animatedColor = Color(0xff29B6F6)
            )
        }
    }
}

/**
 * Bouncing dot progress indicator that draws 3 dots that constantly bouncing after the dot on its
 * left.
 * If [initialColor] and [animatedColor] are not same colors color is animated
 * from [initialColor] to [animatedColor]
 *
 * @param initialColor color that is initially set with animation
 * @param animatedColor color that is set after animation
 */
@Composable
fun BouncingDotProgressIndicator(
    modifier: Modifier = Modifier,
    initialColor: Color = IndicatorDefaults.DotColor,
    animatedColor: Color = IndicatorDefaults.DotColor
) {
    val dotAnimatables = remember {
        listOf(
            Animatable(0f),
            Animatable(0f),
            Animatable(0f)
        )
    }
    dotAnimatables.forEachIndexed { index, animatable ->

        LaunchedEffect(key1 = animatable) {
            animatable.animateTo(
                targetValue = 1f, animationSpec = infiniteRepeatable(
                    initialStartOffset = StartOffset(index * 150),
                    animation = keyframes {
                        durationMillis = 1000
                        0.0f at 0 using LinearOutSlowInEasing
                        1.0f at 300 using LinearOutSlowInEasing
                        0f at 700 using LinearOutSlowInEasing
                        0f at 1000
                    },
                    repeatMode = RepeatMode.Restart,
                )
            )
        }
    }

    val sameColor = initialColor == animatedColor

    Canvas(
        modifier
            .progressSemantics()
            .size(IndicatorDefaults.Size * 2, IndicatorDefaults.Size)
            .padding(8.dp)
    ) {
        val canvasWidth = size.width

        val space = canvasWidth * 0.1f
        val diameter = (canvasWidth - 2 * space) / 3
        val radius = diameter / 2

        dotAnimatables.forEachIndexed { index, animatable ->
            val x = radius + index * (diameter + space)
            val value = animatable.value

            drawCircle(
                color = if (sameColor) initialColor else lerp(
                    initialColor,
                    animatedColor,
                    value
                ),
                center = Offset(
                    x = x,
                    y = center.y - radius * value * 1.6f
                ),
                radius = radius
            )
        }
    }
}

/**
 * Dot progress indicator that draws 3 dots that constantly changing radius after the one
 * next to it. If [initialColor] and [animatedColor] are not same colors color is animated
 * from [initialColor] to [animatedColor]
 *
 * @param initialColor color that is initially set with animation
 * @param animatedColor color that is set after animation
 */
@Composable
fun DotProgressIndicator(
    modifier: Modifier = Modifier,
    initialColor: Color = IndicatorDefaults.DotColor,
    animatedColor: Color = IndicatorDefaults.DotColor
) {

    val initialValue = 0.25f

    val dotAnimatables = remember {
        listOf(
            Animatable(initialValue),
            Animatable(initialValue),
            Animatable(initialValue)
        )
    }
    dotAnimatables.forEachIndexed { index, animatable ->

        LaunchedEffect(key1 = animatable) {
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    initialStartOffset = StartOffset(index * 300),
                    animation = tween(600, easing = FastOutLinearInEasing),
                    repeatMode = RepeatMode.Reverse,
                )
            )
        }
    }

    val sameColor = initialColor == animatedColor

    Canvas(
        modifier
            .progressSemantics()
            .size(IndicatorDefaults.Size * 2, IndicatorDefaults.Size)
            .padding(8.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val diameter = (canvasHeight / 2).coerceAtLeast(canvasWidth / 3)
        val radius = diameter / 2

        dotAnimatables.forEachIndexed { index, animatable ->
            val x = radius + index * (diameter)
            val value = animatable.value
            val colorFraction =
                scale(start1 = initialValue, end1 = 1f, pos = value, start2 = 0f, end2 = 1f)

            drawCircle(
                color = if (sameColor) initialColor.copy(alpha = value) else
                    lerp(
                        initialColor,
                        animatedColor,
                        colorFraction
                    ),
                center = Offset(
                    x = x,
                    y = center.y
                ),
                radius = radius * value
            )
        }
    }
}

object IndicatorDefaults {
    val Size = 48.0.dp
    val DotColor = Color(0xffBDBDBD)
}

/**
 * Contains the [semantics] required for an indeterminate progress indicator, that represents the
 * fact of the in-progress operation.
 *
 * If you need determinate progress 0.0 to 1.0, consider using overload with the progress
 * parameter.
 *
 */
@Stable
fun Modifier.progressSemantics(): Modifier {
    // Older versions of Talkback will ignore nodes with range info which aren't focusable or
    // screen reader focusable. Setting this semantics as merging descendants will mark it as
    // screen reader focusable.
    return semantics(mergeDescendants = true) {
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }
}