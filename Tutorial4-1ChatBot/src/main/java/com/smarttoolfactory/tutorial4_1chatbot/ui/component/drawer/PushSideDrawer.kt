package com.smarttoolfactory.tutorial4_1chatbot.ui.component.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 *
 * Side drawer that pushes content to side while
 *
 */
@Composable
fun PushSideDrawer(
    modifier: Modifier = Modifier,
    drawerWidth: Dp = 320.dp,
    scrimMaxAlpha: Float = 0.40f,
    drawerContent: @Composable ColumnScope.() -> Unit,
    content: @Composable (onHamburgerClick: () -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { drawerWidth.toPx() }
    val scope = rememberCoroutineScope()

    // 0..screenWidthPx (during drag we allow overshoot; canonical open is drawerWidthPx)
    val offsetX = remember { Animatable(0f) }

    fun openCanonical() = scope.launch {
        offsetX.animateTo(
            targetValue = drawerWidthPx,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f)
        )
    }

    fun close() = scope.launch {
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f)
        )
    }

    fun toggle() {
        if (offsetX.value > drawerWidthPx * 0.5f) close() else openCanonical()
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }

        // Progress is relative to canonical open width (drawerWidth), not overshoot width.
        val progress by remember {
            derivedStateOf { (offsetX.value / drawerWidthPx).coerceIn(0f, 1f) }
        }

        fun settle(velocityPxPerSec: Float) {
            val flingThreshold = 1400f
            val shouldOpen =
                when {
                    velocityPxPerSec > flingThreshold -> true
                    velocityPxPerSec < -flingThreshold -> false
                    else -> progress >= 0.5f
                }
            if (shouldOpen) openCanonical() else close()
        }

        val draggableState = rememberDraggableState { delta ->
            // Allow dragging ALL THE WAY to screen width (overshoot), not just drawerWidth.
            val newValue = (offsetX.value + delta).coerceIn(0f, screenWidthPx)
            scope.launch { offsetX.snapTo(newValue) }
        }

        // Slide-in is based on canonical progress (0..1). Overshoot doesn't push it further.
        val drawerTranslationX = (-drawerWidthPx) * (1f - progress)
        Surface(
            modifier = Modifier
                .width(drawerWidth)
                .fillMaxHeight()
                .graphicsLayer { translationX = drawerTranslationX },
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp),
                content = drawerContent
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity -> settle(velocity) }
                )
                .graphicsLayer {
                    translationX = offsetX.value

                    // Optional subtle ChatGPT-like card effect (based on canonical progress)
                    val scale = 1f - 0.02f * progress
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = 0f
                    shape = RoundedCornerShape((16.dp * progress).toPx())
                    clip = progress > 0f
                }
        ) {
            content(::toggle)
        }

        // Scrim starts at x = offsetX (actual) and spans to screen end.
        // This guarantees drawer is never under scrim, even during overshoot.
        if (offsetX.value > 0f) {
            val scrimStartPx = offsetX.value.coerceIn(0f, screenWidthPx)
            val scrimWidthPx = (screenWidthPx - scrimStartPx).coerceAtLeast(0f)

            Box(
                modifier = Modifier
                    .offset { IntOffset(scrimStartPx.roundToInt(), 0) }
                    .width(with(density) { scrimWidthPx.toDp() })
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = scrimMaxAlpha * progress))
                    .pointerInput(Unit) {
                        // Tap anywhere on the scrim area closes.
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.pressed }) {
                                    close()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun DemoChatScreen(onHamburgerClick: () -> Unit) {
    Column(Modifier.fillMaxSize()) {

        // Top bar is part of content; no elevation/shadow.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onHamburgerClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }
            Text("Chat", style = MaterialTheme.typography.titleMedium)
        }

        Divider()

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Main content")
        }
    }
}

@Composable
private fun DemoScreen() {
    PushSideDrawer(
        drawerContent = {
            Text(
                "Chats",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            Divider(Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
            repeat(10) { i ->
                Text(
                    text = "Conversation ${i + 1}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    ) { onHamburgerClick ->
        DemoChatScreen(onHamburgerClick)
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun PushDrawerPreview() {
    DemoScreen()
}
