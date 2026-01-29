@file:OptIn(ExperimentalMaterial3Api::class)

package com.smarttoolfactory.tutorial4_1chatbot.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
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
import kotlinx.coroutines.flow.map
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

    println(
        "LazyListState last item index: ${lastVisible.index}" +
                "viewportBottom: $viewportBottom, " +
                "item size: ${lastVisible.size}, " +
                "itemBottom: $itemBottom"
    )

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
    val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index
    val lastMessageIndex = info.totalItemsCount - 1

    println("last lastMessageIndex: $lastMessageIndex, lastVisible: $lastVisibleIndex")

    val offset: Int = lastVisible?.let { lastItem ->
        if (lastItem.index == lastMessageIndex) {
            val viewportBottom = info.viewportEndOffset
            val itemSize = lastVisible.size
            val itemBottom = lastVisible.offset + itemSize

            val offset =
                (itemBottom - viewportBottom + offsetFromBottom).coerceAtLeast(
                    0
                )

            println(
                "itemBottom: $itemBottom," +
                        " itemSize: $itemSize" +
                        "viewportBottom: $viewportBottom, " +
                        "Offset: $offset"
            )

            offset
        } else {
            0
        }

    } ?: 0
    if (offset > 0) {
        if (animate) {
            animateScrollToItem(index = index, scrollOffset = offset)
        } else {
            scrollToItem(index = index, scrollOffset = offset)
        }
    }
}

/**
 * Emits Unit periodically; used to "pin" during streaming with throttling.
 */
private fun tickerFlow(periodMs: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMs)
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

    // Height of Input area, its bottom padding and navigation bar heigh
    // This is the bottom of LazyColumn or last item minimum to keep user prompt on top
    // when it's entered
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

    /**
     * padding for first user message to animate it from bottom
     */
    val initialItemPadding by animateDpAsState(
        targetValue = if (messages.size <= 1) 1000.dp else 0.dp,
        animationSpec = tween(500)
    )

    /**
     * Check if user manually scrolls to bottom to initiate auto-scroll when streaming
     * Do not start auto-scrolling before user scrolls to bottom with gesture or by pressing
     * jump to bottom button
     */
    var autoScrollToBottom by remember {
        mutableStateOf(false)
    }

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
                        onPress = {
                            autoScrollToBottom = false
                        },
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
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    top = contentPaddingTop + topAppbarHeight,
                    bottom = contentPaddingBottom
                ),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                itemsIndexed(
                    items = messages,
                    contentType = { _: Int, message: Message ->
                        message.role
                    },
                    key = { _: Int, message: Message ->
                        message.uiKey
                    }
                ) { index: Int, msg: Message ->
                    val modifier = if (msg.role == Role.Assistant &&
                        messages.lastIndex == index
                    ) {
                        Modifier.heightIn(
                            min = (lastItemHeight - contentPaddingBottom - itemSpacing)
                                .coerceAtLeast(0.dp)
                        )
//                            .border(2.dp, Color.Magenta)
                    } else if (index == 0 && messages.size <= 2) {
                        Modifier.padding(top = initialItemPadding)
//                            .border(2.dp, Color.Black)
                    } else {
                        Modifier
//                            .border(2.dp, Color.Blue)
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
                IconButton(
                    onClick = {}
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null
                    )
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
                    autoScrollToBottom = true
                    val bottomGapToInputArea = with(density) {
                        (contentPaddingBottom + itemSpacing).roundToPx()
                    }

                    coroutineScope.launch {
                        listState.scrollToBottomOfIndex(
                            index = messages.lastIndex,
                            offsetFromBottom = bottomGapToInputArea
                        )
                    }
                }
            )

            InputArea(
                modifier = Modifier
//                    .border(2.dp, Color.Cyan)
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
            autoScrollToBottom = autoScrollToBottom,
            onAutoScrollToBottomChange = {
                autoScrollToBottom = it
            },
            autoScrollStartThreshold = autoScrollBottomPadding,
            onLastItemHeightCalculated = {
                lastItemHeight = it
            }
        )
    }
}

/**
 * Handle scrolling after message is sent, while streaming and after
 * stream is terminated via completed, failed or canceled
 * @param listState state of LazyColumn to get visible item information
 * @param messageStatus status of ui based on stream state
 * @param autoScrollStartThreshold bottom spacing to start auto-scrolling if other conditions are met. If bottom of streaming assistant message is
 * above this value threshold for auto-scroll is passed. This can be set as bottom or top of input area. Default value is bottom of input area.
 * If last message's bottom is above bottom of input it's set to true
 * @param autoScrollToBottom public param to disable auto scroll when user touches screen while streaming or enable it by touching jump to bottom button
 * @param onAutoScrollToBottomChange callback for setting auto scroll conditions. In this function it's set to false when stream started and
 * set to true if user scrolls up to move last message bottom above **autoScrollStartThreshold**
 * @param onLastItemHeightCalculated height of last item to move user message to top. Without adding extra space there won't be space to move prompt to top
 */
@Composable
private fun HandleScrollState(
    listState: LazyListState,
    messageStatus: MessageStatus?,
    autoScrollStartThreshold: Dp,
    autoScrollToBottom: Boolean,
    onAutoScrollToBottomChange: (Boolean) -> Unit,
    onLastItemHeightCalculated: (Dp) -> Unit
) {
    val density = LocalDensity.current

    val isKeyboardOpen by rememberKeyboardState()

    // Check if bottom of streaming message is above autoScrollStartThreshold
    val isAboveBottom by remember(autoScrollStartThreshold) {
        val threshold = with(density) {
            autoScrollStartThreshold.roundToPx()
        }

        derivedStateOf {
            listState.isAtBottomPx(threshold)
        }
    }

    var isScrollCompleted by remember {
        mutableStateOf(false)
    }

    Text(
        modifier = Modifier
            .padding(start = 190.dp)
            .padding(top = 120.dp),
        fontSize = 16.sp,
        color = Color.Red,
        text = "STATUS: $messageStatus\n" +
                "autoScrollToBottom: $autoScrollToBottom\n" +
                "isAboveBottom: $isAboveBottom\n" +
                "isScrollInProgress: ${listState.isScrollInProgress}"
    )

    // Check if user scrolled to bottom while stream is going to enable auto-scrolling

    LaunchedEffect(messageStatus) {
        onAutoScrollToBottomChange(false)

        if (messageStatus == MessageStatus.Streaming) {

            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
            }
                .collect {
                    if (listState.isScrollInProgress) {
                        println("Change pinned to bottom SCROLLING $isAboveBottom")
                        onAutoScrollToBottomChange(isAboveBottom)
                    }
                }
        } else if (
            messageStatus == MessageStatus.Completed ||
            messageStatus == MessageStatus.Failed ||
            messageStatus == MessageStatus.Cancelled
        ) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo
            }
                .map { visibleItemsInfo ->
                    val lastItemIndex = listState.layoutInfo.totalItemsCount - 1
                    val lastVisibleItemIndex = visibleItemsInfo.lastOrNull()?.index

                    lastItemIndex != lastVisibleItemIndex
                }
                .collect { resetLastItemHeight ->
                    if (resetLastItemHeight) {
                        onLastItemHeightCalculated(0.dp)
                    }
                }
        }
    }

    // If streaming and at the bottom while keyboard is not open and user is not scrolling
    // scroll to bottom and recollect after tick to check again
    LaunchedEffect(messageStatus, autoScrollToBottom) {
        if (messageStatus == MessageStatus.Streaming) {
            snapshotFlow { isAboveBottom && autoScrollToBottom }
                .distinctUntilChanged()
                .flatMapLatest { shouldPin ->
                    println("Should pin: $shouldPin")
                    if (shouldPin) tickerFlow(120) else emptyFlow()
                }
                .collect {
                    val total = listState.layoutInfo.totalItemsCount
                    val lastIndex = total - 1
                    if (total > 0) {
                        println("ChatScreen Auto scrolling...")
                        try {
                            listState.requestScrollToItem(lastIndex, Int.MAX_VALUE)
                        } catch (e: CancellationException) {
                            println("ChatScreen FLOW exception ${e.message}")
                        }
                    }
                }
        }
    }

    // If user prompt is posted but still waiting for reply scroll prompt to top
    // after measuring lastItemHeight, this ist added to have enough space to scroll, the distance
    // between prompt's bottom and end of viewport
    LaunchedEffect(messageStatus, isKeyboardOpen) {
        if (messageStatus != MessageStatus.Queued || isKeyboardOpen) return@LaunchedEffect

        isScrollCompleted = false

        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
        }.collect { visibleItemsInfo ->

            awaitFrame()

            val info: LazyListLayoutInfo = listState.layoutInfo
            val total = info.totalItemsCount
            val viewportEndOffset = info.viewportEndOffset

            if (total >= 2) {
                val lastIndexOfUserMessage = total - 2

                val lastUserMessageItem: LazyListItemInfo? = visibleItemsInfo.firstOrNull {
                    it.index == lastIndexOfUserMessage
                }

                if (lastUserMessageItem != null) {
                    val lastUserMessageBottom = lastUserMessageItem.size
                    val gap = viewportEndOffset - lastUserMessageBottom

                    val finalGap = max(0, gap)

                    val lastItemHeight = with(density) {
                        finalGap.toDp()
                    }

                    onLastItemHeightCalculated(lastItemHeight)

                    if (!isScrollCompleted) {
                        println(
                            "FIRST Scroll $messageStatus, " +
                                    "lastIndex: $lastIndexOfUserMessage, " +
                                    "lastBottom: $lastUserMessageBottom"
                        )
                        try {
                            listState.animateScrollToItem(lastIndexOfUserMessage)
                            isScrollCompleted = true
                            println(
                                "FIRST Scroll Completed $messageStatus, " +
                                        "isScrollCompleted: $isScrollCompleted"
                            )
                        } catch (e: CancellationException) {
                            listState.requestScrollToItem(lastIndexOfUserMessage)
                            println("FIRST Scroll failed ${e.message}")
                        }
                    }

                } else {
                    // If new prompt is outside of lazy column, first push it to bottom of LazyColumn
                    // for it to be visible to start measuring space needed to push it to top
                    println("invoke pre-scroll")
                    listState.animateScrollToItem(lastIndexOfUserMessage)
                }
            }
        }
    }

    // After message is completed of failed wait for one frame and scroll
    // to bottom of last message to be fully be visible if user is already at the bottom
    LaunchedEffect(messageStatus) {
        if (
            messageStatus == MessageStatus.Completed ||
            messageStatus == MessageStatus.Failed ||
            messageStatus == MessageStatus.Cancelled
        ) {
            awaitFrame()
            val lastIndex = listState.layoutInfo.totalItemsCount - 1

            if (isAboveBottom && lastIndex >= 0) {
                listState.scrollToItem(lastIndex, Int.MAX_VALUE)
            }
        }
    }
}

/**
 * Handle scrolling after message is sent, while streaming and after
 * stream is terminated via completed, failed or canceled
 * @param listState state of LazyColumn to get visible item information
 * @param messageStatus status of ui based on stream state
 * @param autoScrollStartThreshold bottom spacing to start auto-scrolling if other conditions are met. If bottom of streaming assistant message is
 * above this value threshold for auto-scroll is passed. This can be set as bottom or top of input area. Default value is bottom of input area.
 * If last message's bottom is above bottom of input it's set to true
 * @param autoScrollToBottom public param to disable auto scroll when user touches screen while streaming or enable it by touching jump to bottom button
 * @param onAutoScrollToBottomChange callback for setting auto scroll conditions. In this function it's set to false when stream started and
 * set to true if user scrolls up to move last message bottom above **autoScrollStartThreshold**
 * @param onLastItemHeightCalculated height of last item to move user message to top. Without adding extra space there won't be space to move prompt to top
 */
@Composable
private fun UpdateScrollState(
    listState: LazyListState,
    messageStatus: MessageStatus?,
    autoScrollStartThreshold: Dp,
    autoScrollToBottom: Boolean,
    onAutoScrollToBottomChange: (Boolean) -> Unit,
    onLastItemHeightCalculated: (Dp) -> Unit
) {
    val density = LocalDensity.current
    val isKeyboardOpen by rememberKeyboardState()

    val isAboveBottom by remember(autoScrollStartThreshold) {
        val threshold = with(density) {
            autoScrollStartThreshold.roundToPx()
        }

        derivedStateOf {
            listState.isAtBottomPx(threshold)
        }
    }

//    Text(
//        modifier = Modifier
//            .padding(start = 190.dp)
//            .padding(top = 120.dp),
//        fontSize = 16.sp,
//        color = Color.Red,
//        text = "STATUS: $messageStatus\n" +
//                "autoScrollToBottom: $autoScrollToBottom\n" +
//                "isAboveBottom: $isAboveBottom\n" +
//                "isScrollInProgress: ${listState.isScrollInProgress}"
//    )

    // Reset pin whenever status changes
    LaunchedEffect(messageStatus) {
        onAutoScrollToBottomChange(false)
    }

    /**
     * Single collector for LazyList layout changes.
     * Drives:
     * - update pinnedToBottom while user is actively scrolling during Streaming
     * - queued prompt pin-to-top (measure gap + scroll once)
     * - completion cleanup (reset gap / final bottom scroll)
     */
    LaunchedEffect(messageStatus, isKeyboardOpen) {
        // Local flags for this effect lifecycle
        var queuedScrollDone = false
        var queuedLastItemHeightComputed = false
        var completionHandled = false

        snapshotFlow { listState.layoutInfo }
            .collect { info ->
                val total = info.totalItemsCount
                val visible = info.visibleItemsInfo

                // 1) While streaming: if user is scrolling, update auto scroll enabling based on isAboveBottom
                if (messageStatus == MessageStatus.Streaming) {
                    if (listState.isScrollInProgress) {
                        onAutoScrollToBottomChange(isAboveBottom)
                    }
                }

                // 2) While queued (and keyboard closed): measure bottom gap & scroll user prompt to top once
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

                                val lastItemHeight = with(density) {
                                    finalGap.toDp()
                                }

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
                            // Pre-scroll to bring the user message into viewport so it can be measured
                            try {
                                listState.animateScrollToItem(lastUserMessageIndex)
                            } catch (_: CancellationException) {
                                listState.requestScrollToItem(lastUserMessageIndex)
                            }
                        }
                    }
                }

                // 3) On completion/failure/cancel: cleanup gap + optional final bottom-align scroll (once)
                val isTerminal =
                    messageStatus == MessageStatus.Completed ||
                            messageStatus == MessageStatus.Failed ||
                            messageStatus == MessageStatus.Cancelled

                if (isTerminal && !completionHandled) {
                    // reset gap if last item isn't visible (same logic as before)
                    val lastIndex = total - 1
                    val lastVisibleIndex = visible.lastOrNull()?.index
                    val shouldResetGap = lastIndex != lastVisibleIndex
                    if (shouldResetGap) onLastItemHeightCalculated(0.dp)

                    // final bottom align if user is already at bottom
                    awaitFrame()
                    if (total > 0 && isAboveBottom) {
                        listState.scrollToItem(lastIndex, Int.MAX_VALUE)
                    }
                    completionHandled = true
                }
            }
    }

    /**
     * Auto-scroll ticker while Streaming AND (isAboveBottom && autoScrollToBottom).
     * This remains a separate lightweight flow, but it no longer snapshots layoutInfo repeatedly.
     */
    LaunchedEffect(messageStatus, autoScrollToBottom) {
        if (messageStatus != MessageStatus.Streaming) return@LaunchedEffect

        snapshotFlow { isAboveBottom && autoScrollToBottom }
            .distinctUntilChanged()
            .flatMapLatest { shouldPin ->
                if (shouldPin) tickerFlow(120) else emptyFlow()
            }
            .collect {

                val total = listState.layoutInfo.totalItemsCount
                val lastIndex = total - 1
                if (total > 0) {
                    try {
                        listState.requestScrollToItem(lastIndex, Int.MAX_VALUE)
                    } catch (e: CancellationException) {
                        println("😱 requestScrollToItem failed: ${e.message}")
                    }
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
    focusRequester: FocusRequester = remember {
        FocusRequester()
    },
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


