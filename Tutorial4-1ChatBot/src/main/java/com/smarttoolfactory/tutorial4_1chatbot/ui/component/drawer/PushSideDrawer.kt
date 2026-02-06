package com.smarttoolfactory.tutorial4_1chatbot.ui.component.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class PushDrawerValue { Closed, Open }

@Stable
class PushDrawerState internal constructor(
    initialValue: PushDrawerValue,
    private val confirmValueChange: (PushDrawerValue) -> Boolean
) {
    var currentValue by mutableStateOf(initialValue)
        private set

    // Canonical open width in px (computed as screenWidth - endPadding)
    internal var openWidthPx by mutableFloatStateOf(0f)

    // Screen width in px (used for drag overshoot)
    internal var screenWidthPx by mutableFloatStateOf(0f)

    // Content translation in px: 0..screenWidthPx during drag, settles to 0 or openWidthPx
    internal val offsetX = Animatable(0f)

    val isOpen: Boolean get() = currentValue == PushDrawerValue.Open
    val isClosed: Boolean get() = currentValue == PushDrawerValue.Closed

    /**
     * Progress is relative to canonical open width (openWidthPx).
     * Overshoot still clamps to 1f.
     */
    val progress: Float
        get() {
            val ow = openWidthPx
            if (ow <= 0f) return 0f
            return (offsetX.value / ow).coerceIn(0f, 1f)
        }

    internal fun setGeometry(openWidthPx: Float, screenWidthPx: Float) {
        this.openWidthPx = openWidthPx
        this.screenWidthPx = screenWidthPx
    }

    suspend fun open() {
        if (!confirmValueChange(PushDrawerValue.Open)) return
        currentValue = PushDrawerValue.Open
        offsetX.animateTo(
            targetValue = openWidthPx,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f)
        )
    }

    suspend fun close() {
        if (!confirmValueChange(PushDrawerValue.Closed)) return
        currentValue = PushDrawerValue.Closed
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f)
        )
    }

    suspend fun toggle() {
        if (isOpen) close() else open()
    }

    internal suspend fun snapTo(valuePx: Float) {
        offsetX.snapTo(valuePx.coerceIn(0f, screenWidthPx))
        currentValue =
            if (offsetX.value >= openWidthPx * 0.5f) PushDrawerValue.Open else PushDrawerValue.Closed
    }

    internal suspend fun settle(velocityPxPerSec: Float) {
        val flingThreshold = 1400f
        val shouldOpen =
            when {
                velocityPxPerSec > flingThreshold -> true
                velocityPxPerSec < -flingThreshold -> false
                else -> progress >= 0.5f
            }
        if (shouldOpen) open() else close()
    }
}

@Composable
fun rememberPushDrawerState(
    initialValue: PushDrawerValue = PushDrawerValue.Closed,
    confirmValueChange: (PushDrawerValue) -> Boolean = { true }
): PushDrawerState = remember {
    PushDrawerState(initialValue, confirmValueChange)
}

/**
 * PushDrawer behaves like ChatGPT:
 * - drawer width is computed like ModalDrawer: screenWidth - endDrawerPadding (default 56.dp)
 * - content is pushed right
 * - drawer slides in from the left
 * - scrim covers ONLY pushed content region (not drawer)
 * - you can drag anywhere start->end to open; drag can overshoot to full screen width
 * - on release it settles to Closed(0) or Open(openWidthPx)
 */
@Composable
fun PushSideDrawer(
    drawerContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    drawerState: PushDrawerState = rememberPushDrawerState(PushDrawerValue.Closed),
    gesturesEnabled: Boolean = true,
    endDrawerPadding: Dp = 56.dp, // ✅ ModalDrawer style
    drawerShape: RoundedCornerShape = RoundedCornerShape(0.dp),
    drawerBackgroundColor: Color = MaterialTheme.colorScheme.surface,
    drawerContentColor: Color = contentColorFor(drawerBackgroundColor),
    scrimColor: Color = Color.Black.copy(alpha = 0.40f),
    content: @Composable (onHamburgerClick: () -> Unit) -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val endPaddingPx = with(density) { endDrawerPadding.toPx() }

        // ModalDrawer width rule: maxWidth - EndDrawerPadding
        val openWidthPx = (screenWidthPx - endPaddingPx).coerceAtLeast(0f)

        SideEffect {
            drawerState.setGeometry(openWidthPx = openWidthPx, screenWidthPx = screenWidthPx)
        }

        val draggableState = rememberDraggableState { delta ->
            scope.launch {
                // allow overshoot to full screen while dragging
                drawerState.snapTo(drawerState.offsetX.value + delta)
            }
        }

        Box(
            modifier = Modifier.then(
                if (gesturesEnabled) {
                    Modifier.draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { v -> scope.launch { drawerState.settle(v) } }
                    )
                } else Modifier
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = drawerState.offsetX.value

                        val progress = drawerState.progress
                        val scale = 1f - 0.02f * progress
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = 0f
                        shape = RoundedCornerShape((16.dp * progress).toPx())
                        clip = progress > 0f
                    }
            ) {
                content({ scope.launch { drawerState.toggle() } })
            }

            // Starts at x = offsetX and covers to screen end.
            if (drawerState.offsetX.value > 0f) {
                val scrimStartPx = drawerState.offsetX.value.coerceIn(0f, screenWidthPx)
                val scrimWidthPx = (screenWidthPx - scrimStartPx).coerceAtLeast(0f)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(scrimStartPx.roundToInt(), 0) }
                        .width(with(density) { scrimWidthPx.toDp() })
                        .fillMaxHeight()
                        .background(scrimColor.copy(alpha = scrimColor.alpha * drawerState.progress))
                        .pointerInput(gesturesEnabled) {
                            if (!gesturesEnabled) return@pointerInput
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.any { it.pressed }) {
                                        scope.launch { drawerState.close() }
                                        event.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        }
                        .semantics {
                            if (drawerState.isOpen) {
                                dismiss {
                                    scope.launch { drawerState.close() }
                                    true
                                }
                            }
                        }
                )
            }

            // Slide driven by canonical progress (0..1). Overshoot doesn't move it further.
            val drawerTranslationX = -openWidthPx * (1f - drawerState.progress)

            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { openWidthPx.toDp() })
                    .graphicsLayer { translationX = drawerTranslationX }
                    .semantics { paneTitle = "Navigation drawer" },
                shape = drawerShape,
                color = drawerBackgroundColor,
                contentColor = drawerContentColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Column(Modifier.fillMaxSize(), content = drawerContent)
            }
        }
    }
}

@Composable
private fun DemoChatScreen(onHamburgerClick: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // Top bar is part of content; no elevation.
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
    val drawerState = rememberPushDrawerState(PushDrawerValue.Closed)

    PushSideDrawer(
        drawerState = drawerState,
        endDrawerPadding = 56.dp, // like ModalDrawer
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
    MaterialTheme { DemoScreen() }
}
