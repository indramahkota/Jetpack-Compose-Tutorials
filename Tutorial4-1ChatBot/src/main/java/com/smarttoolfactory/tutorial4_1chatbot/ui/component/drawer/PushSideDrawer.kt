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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
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

    // Canonical open width in px (screenWidth - endPadding)
    internal var openWidthPx by mutableFloatStateOf(0f)

    // Screen width in px
    internal var screenWidthPx by mutableFloatStateOf(0f)

    // Content translation in px: 0..screenWidthPx during drag
    internal val offsetX = Animatable(0f)

    /**
     * Full-open is explicit-only. Gestures must never settle into full-open.
     * - openFully() sets this flag
     * - any gesture snap/settle clears it
     * - open()/close() also clear it to keep semantics deterministic
     */
    internal var fullOpenRequested by mutableStateOf(false)

    val isOpen: Boolean get() = currentValue == PushDrawerValue.Open
    val isClosed: Boolean get() = currentValue == PushDrawerValue.Closed

    /**
     * Drawer can be "open" in two widths:
     * - normal open width = openWidthPx (default)
     * - full open width   = screenWidthPx (only if openFully() was called and not invalidated)
     */
    internal val openTargetPx: Float
        get() = if (fullOpenRequested) screenWidthPx else openWidthPx

    /**
     * Progress is always relative to canonical open width (openWidthPx).
     * Full-open still clamps to 1f.
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

    private fun springSpec() =
        spring<Float>(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f)

    suspend fun open() {
        fullOpenRequested = false
        if (!confirmValueChange(PushDrawerValue.Open)) return
        currentValue = PushDrawerValue.Open
        offsetX.animateTo(
            targetValue = openWidthPx,
            animationSpec = springSpec()
        )
    }

    /**
     * Opens drawer to full screen width (no end padding) ONLY when explicitly called.
     * Any user drag will invalidate full-open and settle back to normal open width.
     */
    suspend fun openFully() {
        fullOpenRequested = true
        if (!confirmValueChange(PushDrawerValue.Open)) return
        currentValue = PushDrawerValue.Open
        offsetX.animateTo(
            targetValue = screenWidthPx,
            animationSpec = springSpec()
        )
    }

    suspend fun close() {
        fullOpenRequested = false
        if (!confirmValueChange(PushDrawerValue.Closed)) return
        currentValue = PushDrawerValue.Closed
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = springSpec()
        )
    }

    suspend fun toggle() {
        // toggle is always the canonical behavior (no full-open)
        if (isOpen) close() else open()
    }

    internal suspend fun snapTo(valuePx: Float) {
        // Any gesture interaction invalidates explicit full-open.
        fullOpenRequested = false

        offsetX.snapTo(valuePx.coerceIn(0f, screenWidthPx))
        currentValue =
            if (offsetX.value >= openWidthPx * 0.5f) PushDrawerValue.Open else PushDrawerValue.Closed
    }

    internal suspend fun settle(velocityPxPerSec: Float) {
        // Any gesture interaction invalidates explicit full-open.
        fullOpenRequested = false

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
 * - drawer width is computed like ModalDrawer: screenWidth - endDrawerPadding (default 56.dp)
 * - content is pushed right
 * - drawer slides in from the left
 * - scrim covers ONLY pushed content region (not drawer)
 * - you can drag anywhere start->end to open; drag can overshoot to full screen width
 * - on release it settles to Closed(0) or Open(openWidthPx) (never full-open)
 *
 * Full-open (screenWidth) exists ONLY via drawerState.openFully()
 */
@Composable
fun PushSideDrawer(
    drawerContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    drawerState: PushDrawerState = rememberPushDrawerState(PushDrawerValue.Closed),
    gesturesEnabled: Boolean = true,
    endDrawerPadding: Dp = 56.dp,
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
        val openWidthPx = (screenWidthPx - endPaddingPx).coerceAtLeast(0f)

        SideEffect {
            drawerState.setGeometry(openWidthPx = openWidthPx, screenWidthPx = screenWidthPx)
        }

        val draggableState = rememberDraggableState { delta ->
            if (gesturesEnabled) {
                scope.launch {
                    // allow overshoot to full screen while dragging
                    drawerState.snapTo(drawerState.offsetX.value + delta)
                }
            }
        }

        Box(
            modifier = modifier.then(
                if (gesturesEnabled) {
                    Modifier.draggable(
                        state = draggableState,
                        orientation = Orientation.Horizontal,
                        onDragStopped = { v ->
                            scope.launch { drawerState.settle(v) }
                        }
                    )
                } else {
                    Modifier
                }
            )
        ) {
            // CONTENT (pushed)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = drawerState.offsetX.value

                        val p = drawerState.progress
                        val scale = 1f - 0.02f * p
                        scaleX = scale
                        scaleY = scale
                        shape = RoundedCornerShape((16.dp * p).toPx())
                        clip = p > 0f
                    }
            ) {
                content { scope.launch { drawerState.toggle() } }
            }

            // SCRIM covers only pushed-content region
            if (drawerState.offsetX.value > 0f) {
                val scrimStartPx = drawerState.offsetX.value.coerceIn(0f, screenWidthPx)
                val scrimWidthPx = (screenWidthPx - scrimStartPx).coerceAtLeast(0f)

                // When full-open, scrimWidth becomes 0 -> scrim disappears naturally.
                if (scrimWidthPx > 0.5f) {
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
                                if (drawerState.isOpen) {
                                    dismiss {
                                        scope.launch { drawerState.close() }
                                        true
                                    }
                                }
                            }
                    )
                }
            }

            // - width uses openTargetPx (openWidthPx normally, screenWidthPx only after openFully())
            val drawerWidthPx = drawerState.openTargetPx

            // Slide distance uses the actual drawer width so full-open slides correctly.
            val drawerTranslationX = -drawerWidthPx * (1f - drawerState.progress)

            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(with(density) { drawerWidthPx.toDp() })
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
private fun DemoChatScreen(
    onHamburgerClick: () -> Unit,
    onOpenFullyClick: () -> Unit
) {
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
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

            // Demo button to trigger full-open explicitly
            Text(
                text = "Search",
                modifier = Modifier
                    .padding(start = 16.dp)
                    .background(Color.Transparent)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.pressed }) {
                                    onOpenFullyClick()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
            )
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
    val scope = rememberCoroutineScope()

    PushSideDrawer(
        drawerState = drawerState,
        endDrawerPadding = 56.dp,
        drawerContent = {

            Text(
                "Chats",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(16.dp)
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
        DemoChatScreen(
            onHamburgerClick = onHamburgerClick,
            onOpenFullyClick = { scope.launch { drawerState.openFully() } }
        )
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun PushDrawerPreview() {
    MaterialTheme {
        DemoScreen()
    }
}
