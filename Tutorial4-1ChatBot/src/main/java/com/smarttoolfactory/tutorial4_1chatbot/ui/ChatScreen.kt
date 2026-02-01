@file:OptIn(ExperimentalMaterial3Api::class)

package com.smarttoolfactory.tutorial4_1chatbot.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.button.JumpToBottomButton
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.input.ChatTextField
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.message.MessageRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlin.math.max

val contentPaddingTop = 16.dp
val itemSpacing = 16.dp
val bottomPadding = 16.dp
val inputHeight = 48.dp
val topAppbarHeight = 38.dp

val backgroundColor = Color(0xFFFAFAFA)
val topAppbarBrush = Brush.verticalGradient(
    colors = listOf(
        backgroundColor.copy(alpha = 1f),
        backgroundColor.copy(alpha = 0f)
    )
)

val inputBrush = Brush.verticalGradient(
    colors = listOf(
        backgroundColor.copy(alpha = .7f),
        backgroundColor.copy(alpha = .8f),
        backgroundColor.copy(alpha = .9f)
    )
)

/**
 * Check if last item in visible items is the last item of Lazy list and scrolled to bottom
 * of last item by this threshold. When threshold is negative we check if we scrolled to top
 * by this offset. If it's 100px it returns true when user scrolls to last item's bottom less than
 * 100px
 */
private fun LazyListState.isAtBottomPx(thresholdPx: Int = 0): Boolean {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return false
    val lastIndex = info.totalItemsCount - 1
    if (lastVisible.index != lastIndex) return false

    val viewportBottom = info.viewportEndOffset
    val itemSize = lastVisible.size
    val itemBottom = lastVisible.offset + itemSize

    return itemBottom - viewportBottom <= thresholdPx
}

private suspend fun LazyListState.scrollToBottomOfIndex(
    index: Int,
    offsetFromBottom: Int = 0,
    animate: Boolean = true
) {
    scrollToItem(index)
    awaitFrame()

    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull()
    val lastMessageIndex = info.totalItemsCount - 1

    val offset: Int = lastVisible?.let { lastItem ->
        if (lastItem.index == lastMessageIndex) {
            val viewportBottom = info.viewportEndOffset
            val itemBottom = lastItem.offset + lastItem.size
            (itemBottom - viewportBottom + offsetFromBottom).coerceAtLeast(0)
        } else 0
    } ?: 0

    if (lastVisible?.index == lastMessageIndex) {
        if (offset > 0) {
            if (animate) {
                animateScrollToItem(index = index, scrollOffset = offset)
            } else {
                scrollToItem(index = index, scrollOffset = offset)
            }
        } else {
            // Fallback: last item is shorter than viewport (or already above threshold),
            // so offset computes to 0 and we'd stay TOP-aligned. Force BOTTOM-align.
            if (animate) {
                animateScrollToItem(index = index, scrollOffset = Int.MAX_VALUE)
            } else {
                scrollToItem(index = index, scrollOffset = Int.MAX_VALUE)
            }
        }
    }
}

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel
) {
    val density = LocalDensity.current

    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val topAppbarHeight = remember(statusBarHeight) {
        topAppbarHeight + with(density) {
            statusBarHeight.toDp()
        }
    }

    val navBarHeight = WindowInsets.navigationBars.getBottom(density)

    val contentPaddingBottom = remember(navBarHeight) {
        inputHeight + bottomPadding + with(density) {
            navBarHeight.toDp()
        }
    }

    // Bottom of the input area to start auto scroll when bottom of assistant message is above this space
    val autoScrollBottomPadding = remember(navBarHeight) {
        -(bottomPadding + with(density) {
            navBarHeight.toDp()
        })
    }

    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val uiScrollState: ChatUiState.ScrollState = uiState.scrollState

    val messages: SnapshotStateList<Message> = chatViewModel.messages
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val isKeyboardOpen by rememberKeyboardState()
    val focusRequester = remember { FocusRequester() }

    val coroutineScope = rememberCoroutineScope()
    val messageStatus: MessageStatus? = messages.lastOrNull()?.messageStatus
    var lastItemHeight by remember { mutableStateOf(0.dp) }

    /**
     * Button for scrolling to bottom of the list when last message is not totally visible
     * Setting threshold for isAtBottomPx changes when it should be visible
     * based on current scroll. 0 is when last item's bottom is equal to viewport's bottom
     * which is bottom of navbar if edge to edge enabled
     */
    val jumpToBottomButtonEnabled by remember {
        derivedStateOf {
            messages.isNotEmpty() &&
                    listState.isAtBottomPx().not() &&
                    isKeyboardOpen.not() &&
                    listState.isScrollInProgress.not()
        }
    }

    val initialItemPadding by animateDpAsState(
        targetValue = if (messages.size <= 1) 1000.dp else 0.dp,
        animationSpec = tween(500)
    )

    /**
     * Check if user manually scrolls to bottom to initiate auto-scroll when streaming
     * Do not start auto-scrolling before user scrolls to bottom with gesture or by pressing
     * jump to bottom button
     */
    var autoScrollToBottom by remember { mutableStateOf(false) }

    var userDragging by remember { mutableStateOf(false) }
    var programmaticScroll by remember { mutableStateOf(false) }

    // On start open keyboard on next frame
    LaunchedEffect(Unit) {
        awaitFrame()
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .background(backgroundColor)
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (isKeyboardOpen) {
                                focusManager.clearFocus()
                                val text = input.trim()
                                if (text.isNotEmpty()) {
                                    chatViewModel.sendMessage(text)
                                    input = ""
                                }
                            }
                        }
                    )
                }
        ) {

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    /**
                     * User gesture detector for the LIST only.
                     * - disables pin immediately on touch
                     * - marks this as a user-initiated session (drag + fling)
                     */
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            userDragging = true
                            // user interacts => stop pin immediately
                            autoScrollToBottom = false

                            // wait until finger up; fling continues; settle logic is handled inside UpdateScrollState
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { it.pressed })
                        }
                    },
                state = listState,
                contentPadding = PaddingValues(
                    top = contentPaddingTop + topAppbarHeight,
                    bottom = contentPaddingBottom,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                itemsIndexed(
                    items = messages,
                    contentType = { _, message -> message.role },
                    key = { _, message -> message.uiKey }
                ) { index, msg ->
                    val modifier =
                        if (msg.role == Role.Assistant && messages.lastIndex == index) {
                            Modifier.heightIn(
                                min = (lastItemHeight - contentPaddingBottom - itemSpacing)
                                    .coerceAtLeast(0.dp)
                            )
                        } else if (index == 0 && messages.size <= 2) {
                            Modifier.padding(top = initialItemPadding)
                        } else {
                            Modifier
                        }

                    MessageRow(
                        modifier = modifier,
                        message = msg
                    )
                }
            }
        }

        TopAppBar(
            modifier = Modifier.height(topAppbarHeight).background(brush = topAppbarBrush),
            title = {},
            actions = {
                IconButton(onClick = {}) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White.copy(alpha = .6f)
            )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .imePadding()
                .navigationBarsPadding()
        ) {

            JumpToBottomButton(
                modifier = Modifier.align(Alignment.End),
                enabled = jumpToBottomButtonEnabled,
                onClick = {
                    val bottomGapToInputArea = with(density) {
                        (contentPaddingBottom + itemSpacing).roundToPx()
                    }

                    coroutineScope.launch {
                        programmaticScroll = true
                        println("JUMP scroll true")
                        try {
                            listState.scrollToBottomOfIndex(
                                index = messages.lastIndex,
                                offsetFromBottom = bottomGapToInputArea
                            )
                            // keep your behavior: jump enables pin
                            autoScrollToBottom = true
                        } catch (e: CancellationException) {
                            println("🚀JUMP to BOTTOM failed: ${e.message}")
                        } finally {
                            awaitFrame()
                            programmaticScroll = false
                            println("JUMP scroll false")
                        }
                    }
                }
            )

            InputArea(
                modifier = Modifier
                    .background(brush = inputBrush)
                    .padding(bottom = bottomPadding, start = 16.dp, end = 16.dp)
                    .navigationBarsPadding()
                    .heightIn(min = inputHeight)
                    .fillMaxWidth(),
                focusRequester = focusRequester,
                value = input,
                onValueChange = {
                    input = it
                },
                onClick = {
                    focusManager.clearFocus()
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        chatViewModel.sendMessage(text)
                        input = ""
                    }
                }
            )
        }

        UpdateScrollState(
            listState = listState,
            messageStatus = messageStatus,
            autoScrollBottomThreshold = autoScrollBottomPadding,
            autoScrollToBottom = autoScrollToBottom,
            userDragging = userDragging,
            programmaticScroll = programmaticScroll,
            onAutoScrollToBottomChange = { autoScrollToBottom = it },
            onUserDraggingChange = { userDragging = it },
            onProgrammaticScrollChange = {
                programmaticScroll = it
            },
            onLastItemHeightCalculated = { lastItemHeight = it }
        )
    }
}

/**
 * Handle scrolling after a message is sent, while streaming, and after the stream is terminated
 * via completed, failed, or cancelled.
 *
 * - **Streaming**
 *   - If the user has enabled auto-scroll ([autoScrollToBottom] == true), keep the last item
 *     bottom-aligned as the assistant delta grows (layout-driven; safe when deltas arrive faster
 *     than any ticker/interval).
 *   - If the user manually scrolls (drag/fling) and settles at the *true bottom*, enable
 *     auto-scroll (pin) for the remainder of the stream.
 *
 * - **Queued**
 *   - Scroll the last user prompt to the top once (so the prompt stays visible while waiting).
 *   - Compute and publish extra spacer height via [onLastItemHeightCalculated] to allow the prompt
 *     to move to the top without compressing the layout.
 *
 * - **Terminal** (Completed/Failed/Cancelled)
 *   - Reset spacer height if the last item is no longer visible.
 *   - Optionally bottom-align once if the user is already “near bottom” by [autoScrollBottomThreshold].
 *
 * @param listState State of LazyColumn to read layout/visibility information and issue scroll requests.
 * @param messageStatus Current message/stream status driving the state machine (Queued/Streaming/Terminal).
 * @param autoScrollBottomThreshold "Near bottom" threshold used for terminal behavior (Dp converted to px).
 * @param autoScrollToBottom Public flag: true when UI should keep pinning to the bottom during Streaming.
 * @param userDragging True when the user has initiated a scroll gesture (drag/fling). Cleared on settle.
 * @param programmaticScroll True while scroll is initiated by code (Jump-to-bottom or internal alignment).
 * @param onAutoScrollToBottomChange Callback to update [autoScrollToBottom].
 * @param onUserDraggingChange Callback to update/clear [userDragging].
 * @param onProgrammaticScrollChange Callback to update [programmaticScroll] (kept for compatibility).
 * @param onLastItemHeightCalculated Spacer height used to allow prompt-to-top behavior while queued.
 */
@Composable
private fun UpdateScrollState(
    listState: LazyListState,
    messageStatus: MessageStatus?,
    autoScrollBottomThreshold: Dp,
    autoScrollToBottom: Boolean,
    userDragging: Boolean,
    programmaticScroll: Boolean,
    onAutoScrollToBottomChange: (Boolean) -> Unit,
    onUserDraggingChange: (Boolean) -> Unit,
    onProgrammaticScrollChange: (Boolean) -> Unit,
    onLastItemHeightCalculated: (Dp) -> Unit
) {
    val density = LocalDensity.current
    val isKeyboardOpen by rememberKeyboardState()

    // This is for starting auto scroll when user scrolls end of viewPort above bottom of input area.
    // Touch Pointer should be going up while bottom of viewport should be above bottom of input area
    val isAboveBottom by remember(autoScrollBottomThreshold) {
        val thresholdPx = with(density) { autoScrollBottomThreshold.roundToPx() }
        derivedStateOf { listState.isAtBottomPx(thresholdPx) }
    }

    // This is one is for auto scroll from jump button, when scrolled to bottom of the screen
    // requisite for enabling scroll is met
    val isPinnedAtBottom by remember {
        derivedStateOf { listState.isAtBottomPx(thresholdPx = 0) }
    }

//    Text(
//        modifier = Modifier
//            .padding(start = 160.dp)
//            .padding(top = 120.dp),
//        fontSize = 16.sp,
//        color = Color.Red,
//        text = "STATUS: $messageStatus\n" +
//                "autoScrollToBottom: $autoScrollToBottom\n" +
//                "isAboveBottom: $isAboveBottom\n" +
//                "isPinnedAtBottom: $isPinnedAtBottom\n" +
//                "userDragging: $userDragging\n" +
//                "programmaticScroll: $programmaticScroll\n" +
//                "isScrollInProgress: ${listState.isScrollInProgress}"
//    )

    LaunchedEffect(
        messageStatus,
        isKeyboardOpen,
        autoScrollToBottom,
        userDragging,
        programmaticScroll
    ) {
        // Guards for one-shot behavior per effect lifetime.
        var queuedScrollDone = false
        var queuedLastItemHeightComputed = false
        var terminalHandled = false

        // Detect "settle": isScrollInProgress transitions true -> false.
        var wasScrollInProgress = false

        // Coalesce bottom-align requests to max 1 per frame.
        var pinRequestScheduled = false

        // Clear programmatic flag at the start of this effect lifecycle.
        // (Call sites may still manage it externally; this prevents a stale true from leaking in.)
        onProgrammaticScrollChange(false)

        snapshotFlow { listState.layoutInfo }
            .collect { info ->
                val total = info.totalItemsCount
                val visible = info.visibleItemsInfo

                // Streaming
                if (messageStatus == MessageStatus.Streaming) {

                    // Enable pin on SETTLE:
                    // If the user was dragging/flinging and the list settles at the true bottom,
                    // enable auto-scroll for subsequent deltas.
                    val nowInProgress = listState.isScrollInProgress
                    if (wasScrollInProgress && !nowInProgress) {
                        awaitFrame() // allow settle layout to apply

                        val shouldEnablePin =
                            userDragging && !programmaticScroll && isPinnedAtBottom

                        if (shouldEnablePin) {
                            onAutoScrollToBottomChange(true)
                        }

                        // Always clear user-drag latch after settle.
                        onUserDraggingChange(false)
                    }
                    wasScrollInProgress = nowInProgress

                    // Keep pinned to bottom while streaming:
                    // Layout-driven pin fixes "deltas arrive faster than interval" cases.
                    if (autoScrollToBottom && !programmaticScroll) {
                        if (!pinRequestScheduled) {
                            pinRequestScheduled = true
                            launch {
                                awaitFrame()

                                val totalNow = listState.layoutInfo.totalItemsCount
                                val lastIndex = totalNow - 1
                                if (lastIndex >= 0) {
                                    try {
                                        listState.requestScrollToItem(lastIndex, Int.MAX_VALUE)
                                    } catch (e: CancellationException) {
                                        println("🔥Auto scroll failed: ${e.message}")
                                    }
                                }

                                pinRequestScheduled = false
                            }
                        }
                    }
                }

                // Queued
                if (messageStatus == MessageStatus.Queued && !isKeyboardOpen) {
                    if (total >= 2) {
                        val lastUserMessageIndex = total - 2
                        val lastUserMessageItem =
                            visible.firstOrNull { it.index == lastUserMessageIndex }

                        if (lastUserMessageItem != null) {
                            awaitFrame() // ensure stable viewport offsets for this frame

                            // Compute spacer height once (skip for 1st prompt case).
                            if (!queuedLastItemHeightComputed && total > 2) {
                                val viewportEndOffset = info.viewportEndOffset
                                val lastUserMessageBottom = lastUserMessageItem.size
                                val gap = viewportEndOffset - lastUserMessageBottom
                                val finalGap = max(0, gap)

                                onLastItemHeightCalculated(with(density) { finalGap.toDp() })
                                queuedLastItemHeightComputed = true
                            }

                            // Scroll prompt to top once.
                            if (!queuedScrollDone) {
                                try {
                                    listState.animateScrollToItem(lastUserMessageIndex)
                                } catch (_: CancellationException) {
                                    listState.requestScrollToItem(lastUserMessageIndex)
                                }
                                queuedScrollDone = true
                            }
                        } else {
                            // Pre-scroll to bring prompt into viewport so it can be measured.
                            try {
                                listState.animateScrollToItem(lastUserMessageIndex)
                            } catch (_: CancellationException) {
                                listState.requestScrollToItem(lastUserMessageIndex)
                            }
                        }
                    }
                }

                // Terminal (Completed / Failed / Canceled)
                val isTerminal =
                    messageStatus == MessageStatus.Completed ||
                            messageStatus == MessageStatus.Failed ||
                            messageStatus == MessageStatus.Cancelled

                if (isTerminal && !terminalHandled) {
                    val lastIndex = total - 1
                    val lastVisibleIndex = visible.lastOrNull()?.index

                    // Reset spacer if last item isn't visible anymore.
                    if (lastIndex != lastVisibleIndex) {
                        onLastItemHeightCalculated(0.dp)
                    }

                    awaitFrame()

                    // If already near bottom, bottom-align once (keep existing behavior).
                    if (total > 0 && isAboveBottom) {
                        listState.scrollToItem(lastIndex, Int.MAX_VALUE)
                    }

                    terminalHandled = true
                }
            }
    }
}


@Composable
private fun InputArea(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onClick: () -> Unit,
) {
    ChatTextField(
        modifier = modifier,
        enabled = enabled,
        value = value,
        onValueChange = onValueChange,
        focusRequester = focusRequester,
        onClick = onClick
    )
}

@Composable
fun rememberKeyboardState(): State<Boolean> {
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    return rememberUpdatedState(isImeVisible)
}
