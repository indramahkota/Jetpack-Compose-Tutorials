package com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal fun Rect.union(other: Rect): Rect {
    return Rect(
        left = min(this.left, other.left),
        top = min(this.top, other.top),
        right = max(this.right, other.right),
        bottom = max(this.bottom, other.bottom)
    )
}

data class RectWithAnimation(
    val id: String = "",
    val startIndex: Int,
    val endIndex: Int,
    val rect: Rect,
    val animatable: Animatable<Float, AnimationVector1D> = Animatable(0f),
)

internal data class RectWithAnimatable(
    val id: String,
    val rect: Rect,
    val charStart: Int,
    val charEnd: Int,
    val batchId: Long = 0L,
    val animatable: Animatable<Float, AnimationVector1D> = Animatable(0f),
)

internal fun RectWithAnimatable.covers(index: Int): Boolean = index in charStart..charEnd

data class RectWithColor(
    val rect: Rect,
    val color: Color
)

fun randomColor() = Color(
    Random.nextInt(256),
    Random.nextInt(256),
    Random.nextInt(256)
)