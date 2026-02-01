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
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.button.JumpToBottomButton
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.input.ChatTextField
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.message.MessageRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
 * "At bottom" when the last visible item is the last item AND its bottom is within [thresholdPx]
 * of the viewport end.
 *
 * thresholdPx:
 * - 0: exact bottom
 * - positive: allow slack (recommended)
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
                    .alpha(.3f)
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

    val isAboveBottom by remember(autoScrollBottomThreshold) {
        val thresholdPx = with(density) { autoScrollBottomThreshold.roundToPx() }
        derivedStateOf { listState.isAtBottomPx(thresholdPx) }
    }

    // Use 0 or small positive slack if you want tolerance (e.g. 2..16px).
    val isPinnedAtBottom by remember {
        derivedStateOf { listState.isAtBottomPx(thresholdPx = 0) }
    }

    Text(
        modifier = Modifier
            .padding(start = 170.dp)
            .padding(top = 120.dp),
        fontSize = 16.sp,
        color = Color.Red,
        text = "STATUS: $messageStatus\n" +
                "autoScrollToBottom: $autoScrollToBottom\n" +
                "isAboveBottom(threshold): $isAboveBottom\n" +
                "isPinnedAtBottom: $isPinnedAtBottom\n" +
                "userDragging: $userDragging\n" +
                "programmaticScroll: $programmaticScroll\n" +
                "isScrollInProgress: ${listState.isScrollInProgress}"
    )

    LaunchedEffect(messageStatus, isKeyboardOpen, autoScrollToBottom, userDragging, programmaticScroll) {
        var queuedScrollDone = false
        var queuedLastItemHeightComputed = false
        var completionHandled = false

        var wasScrollInProgress = false

        // Coalesce "pin to bottom" requests to max 1 per frame
        var pinRequestScheduled = false

        onProgrammaticScrollChange(false)

        snapshotFlow { listState.layoutInfo }
            .collect { info ->
                val total = info.totalItemsCount
                val visible = info.visibleItemsInfo

                // While pinned + Streaming: keep bottom-aligning on every layout change.
                // This fixes "deltas append faster than ticker / settle", because the last item can grow
                // after you reached bottom and you’d otherwise fall behind.
                if (messageStatus == MessageStatus.Streaming &&
                    autoScrollToBottom &&
                    !programmaticScroll
                ) {
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
                                    println("😱 requestScrollToItem failed: ${e.message}")
                                }
                            }
                            pinRequestScheduled = false
                        }
                    }
                }

                // Enable pin on SETTLE during Streaming (drag OR fling)
                val nowInProgress = listState.isScrollInProgress
                if (messageStatus == MessageStatus.Streaming) {
                    if (wasScrollInProgress && !nowInProgress) {
                        awaitFrame()

                        // Use the real bottom check here (not the negative threshold one)
                        val shouldEnablePin =
                            userDragging &&
                                    !programmaticScroll &&
                                    isPinnedAtBottom

                        if (shouldEnablePin) {
                            onAutoScrollToBottomChange(true)
                        }

                        onUserDraggingChange(false)
                    }
                }
                wasScrollInProgress = nowInProgress

                if (messageStatus == MessageStatus.Queued && !isKeyboardOpen) {
                    if (total >= 2) {
                        val lastUserMessageIndex = total - 2
                        val lastUserMessageItem =
                            visible.firstOrNull { it.index == lastUserMessageIndex }

                        if (lastUserMessageItem != null) {
                            // Ensure layout is settled for this frame before reading viewport offsets.
                            awaitFrame()

                            if (!queuedLastItemHeightComputed && total > 2) {
                                val viewportEndOffset = info.viewportEndOffset
                                val lastUserMessageBottom = lastUserMessageItem.size
                                val gap = viewportEndOffset - lastUserMessageBottom
                                val finalGap = max(0, gap)

                                val lastItemHeight = with(density) { finalGap.toDp() }
                                onLastItemHeightCalculated(lastItemHeight)
                                queuedLastItemHeightComputed = true
                            }

                            if (!queuedScrollDone) {
                                try {
                                    listState.animateScrollToItem(lastUserMessageIndex)
                                } catch (_: CancellationException) {
                                    listState.requestScrollToItem(lastUserMessageIndex)
                                }
                                queuedScrollDone = true
                            }
                        } else {
                            try {
                                listState.animateScrollToItem(lastUserMessageIndex)
                            } catch (_: CancellationException) {
                                listState.requestScrollToItem(lastUserMessageIndex)
                            }
                        }
                    }
                }

                val isTerminal =
                    messageStatus == MessageStatus.Completed ||
                            messageStatus == MessageStatus.Failed ||
                            messageStatus == MessageStatus.Cancelled

                if (isTerminal && !completionHandled) {
                    val lastIndex = total - 1
                    val lastVisibleIndex = visible.lastOrNull()?.index
                    val shouldResetGap = lastIndex != lastVisibleIndex
                    if (shouldResetGap) onLastItemHeightCalculated(0.dp)

                    awaitFrame()
                    if (total > 0 && isAboveBottom) {
                        listState.scrollToItem(lastIndex, Int.MAX_VALUE)
                    }
                    completionHandled = true
                }
            }
    }

    // ⛔️ Removed ticker-based pinning.
    // Pinning is now layout-driven above, which is delta-speed safe and fixes the "sometimes jump doesn't pin"
    // because it no longer depends on a bottom check racing against fast incoming deltas.
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
