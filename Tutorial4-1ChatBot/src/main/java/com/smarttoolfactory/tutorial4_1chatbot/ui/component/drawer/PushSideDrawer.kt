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

/**
 * State object similar to Material's DrawerState, but for the "push" drawer:
 * - Content pushes right
 * - Drawer slides in
 * - Scrim only covers pushed content region (not the drawer)
 * - Drag overshoots up to screenWidth during gesture, then settles to canonical openWidth
 */
@Stable
class PushDrawerState internal constructor(
    initialValue: PushDrawerValue,
    private val confirmValueChange: (PushDrawerValue) -> Boolean
) {
    var currentValue by mutableStateOf(initialValue)
        private set

    // Canonical open width in px (drawerWidthPx). Used for "fully open" resting position.
    internal var openWidthPx by mutableFloatStateOf(0f)

    // Screen width in px. Used for "overshoot to full screen" during drag.
    internal var screenWidthPx by mutableFloatStateOf(0f)

    // 0..screenWidthPx while dragging; settles to 0 or openWidthPx.
    internal val offsetX = Animatable(if (initialValue == PushDrawerValue.Open) 1f else 0f)

    val isOpen: Boolean get() = currentValue == PushDrawerValue.Open
    val isClosed: Boolean get() = currentValue == PushDrawerValue.Closed

    /**
     * Drawer progress relative to canonical open width.
     * Overshoot (> openWidthPx) still reports progress as 1f.
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
): PushDrawerState {
    // Saver optional; keep simple for now like your previous snippets.
    return remember {
        PushDrawerState(
            initialValue = initialValue,
            confirmValueChange = confirmValueChange
        )
    }
}

/**
 * ModalDrawer-like API for the "push" drawer.
 *
 * Differences from Material ModalDrawer:
 * - Drawer does affect layout visually because content is translated (pushes).
 * - Scrim only covers the pushed content area; drawer stays above/visible.
 * - Gestures can overshoot to screen width during drag, then settle to canonical open width.
 */
@Composable
fun PushSideDrawer(
    drawerContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    drawerState: PushDrawerState = rememberPushDrawerState(PushDrawerValue.Closed),
    gesturesEnabled: Boolean = true,
    drawerWidth: Dp = 320.dp,
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
        val openWidthPx = with(density) { drawerWidth.toPx() }

        // Like ModalDrawer's SideEffect anchor update: update geometry on size changes.
        SideEffect {
            drawerState.setGeometry(openWidthPx = openWidthPx, screenWidthPx = screenWidthPx)
        }

        val draggableState = rememberDraggableState { delta ->
            scope.launch {
                // Allow overshoot up to full screen width while dragging.
                drawerState.snapTo(drawerState.offsetX.value + delta)
            }
        }

        // The whole composition is draggable, similar to ModalDrawer's Box(Modifier.anchoredDraggable(...))
        Box(
            modifier = Modifier
                .then(
                    if (gesturesEnabled) {
                        Modifier.draggable(
                            state = draggableState,
                            orientation = Orientation.Horizontal,
                            onDragStopped = { v -> scope.launch { drawerState.settle(v) } }
                        )
                    } else Modifier
                )
        ) {
            // Keep top bar INSIDE this content. No separate elevated bar here.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = drawerState.offsetX.value

                        // Optional subtle ChatGPT card effect driven by canonical progress:
                        val p = drawerState.progress
                        val s = 1f - 0.02f * p
                        scaleX = s
                        scaleY = s
                        shadowElevation = 0f
                        shape = RoundedCornerShape((16.dp * p).toPx())
                        clip = p > 0f
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
                            if (gesturesEnabled) {
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
                        }
                        .semantics {
                            // Mirrors ModalDrawer semantics: dismiss when open
                            if (drawerState.isOpen) {
                                dismiss {
                                    scope.launch { drawerState.close() }
                                    true
                                }
                            }
                        }
                )
            }

            // --- Drawer sheet (slides in) ---
            // Slide-in driven by canonical progress (not overshoot).
            val drawerTranslationX = (-openWidthPx) * (1f - drawerState.progress)
            Surface(
                modifier = Modifier
                    .width(drawerWidth)
                    .fillMaxHeight()
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

/* -----------------------------------------------------------------------------------------
 *  DEMO
 * ----------------------------------------------------------------------------------------- */

@Composable
private fun DemoChatScreen(onHamburgerClick: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // Top bar is PART of content; no elevation.
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
        drawerWidth = 320.dp,
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
