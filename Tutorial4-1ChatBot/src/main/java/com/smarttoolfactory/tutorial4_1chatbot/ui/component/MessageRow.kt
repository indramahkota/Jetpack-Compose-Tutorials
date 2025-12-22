package com.smarttoolfactory.tutorial4_1chatbot.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun LoadingRowPreview() {

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {

        LoadingRow(
            icon ={
                Icon(
                    modifier = Modifier.size(28.dp).background(Color.White, CircleShape),
                    imageVector = Icons.Default.Lock,
                    contentDescription = null
                )

                Spacer(modifier = Modifier.width(8.dp))
            },
            animatedContent = {
                Text("Word by word reveal")
            }
        )
    }
}

@Composable
internal fun LoadingRow(
    modifier: Modifier = Modifier,
    icon: @Composable RowScope.() -> Unit = {},
    animatedContent: @Composable () -> Unit = {},
) {

    val transition = rememberInfiniteTransition()

    val progress by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()

        Box(
            modifier = Modifier.graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
                .drawWithCache {
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Red.copy(
                                alpha = progress
                            ),
                            Color.Red.copy(
                                alpha = (progress * 1.6f - .3f).coerceIn(0f, 1f)
                            )
                        )
                    )

                    onDrawWithContent {
                        // Destination
                        drawContent()

                        // Source
                        drawRect(
                            brush = brush,
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            animatedContent()
        }
    }
}