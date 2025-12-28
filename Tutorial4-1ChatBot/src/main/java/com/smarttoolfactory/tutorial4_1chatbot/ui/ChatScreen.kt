@file:OptIn(ExperimentalMaterial3Api::class)

package com.smarttoolfactory.tutorial4_1chatbot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.BasicRichText
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

val contentPaddingTop = 12.dp
val itemSpacing = 16.dp
val backgroundColor = Color(0xFFFAFAFA)

private fun LazyListState.isAtBottomPx(thresholdPx: Int = 6): Boolean {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return false
    val lastIndex = info.totalItemsCount - 1
    if (lastVisible.index != lastIndex) return false

    val viewportBottom = info.viewportEndOffset
    val itemBottom = lastVisible.offset + lastVisible.size
//    println(
//        "isAtBottomPx() viewportBottom: $viewportBottom, " +
//                "last index:${lastVisible.index}, " +
//                "visible index:${lastVisible.index}, " +
//                "itemBottom: $itemBottom"
//    )
    return itemBottom - viewportBottom <= thresholdPx
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
    val topAppbarHeight = 48.dp + with(density) {
        statusBarHeight.toDp()
    }

    val navBarHeight = WindowInsets.navigationBars.getBottom(density)

    // Height of Input area, its bottom padding and navigation bar heigh
    // This is the bottom of LazyColumn or last item minimum to keep user prompt on top
    // when it's entered
    val contentPaddingBottom = 56.dp + 16.dp + with(density) {
        navBarHeight.toDp()
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
    val messageStatus = messages.lastOrNull()?.messageStatus
    var bottomGapDp by remember { mutableStateOf(0.dp) }

    var autoScrollEnabled by remember { mutableStateOf(true) }

    val isAtBottom by remember {
        derivedStateOf { listState.isAtBottomPx(thresholdPx = 300) }
    }

    val jumpToBottomButtonEnabled by remember {
        derivedStateOf {
            isAtBottom.not() && isKeyboardOpen.not()
        }
    }

    // On start open keyboard on next frame
    LaunchedEffect(Unit) {
        awaitFrame()
        focusRequester.requestFocus()
    }

// If user scrolls while away from bottom, treat as reading history
    LaunchedEffect(Unit) {
        snapshotFlow { isAtBottom to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (atBottom, inProgress) ->
                autoScrollEnabled = if (inProgress) {
                    false
                } else if (atBottom) {
                    true
                } else {
                    false
                }
            }
    }

    LaunchedEffect(messageStatus) {
        if (messageStatus != MessageStatus.Streaming) return@LaunchedEffect
        snapshotFlow { autoScrollEnabled && isAtBottom && !listState.isScrollInProgress }
            .distinctUntilChanged()
            .flatMapLatest { shouldPin ->
                if (shouldPin) tickerFlow(120) else emptyFlow()
            }
            .collect {
//                println("COLLECTING...")
                if (messages.isNotEmpty()) {
                    try {
                        listState.scrollToItem(messages.lastIndex, Int.MAX_VALUE)
                    } catch (e: CancellationException) {
                        println("FLOW exception ${e.message}")
                    }
                }
            }
    }

    LaunchedEffect(messageStatus, isKeyboardOpen) {

        if (messageStatus != MessageStatus.Queued || isKeyboardOpen) return@LaunchedEffect

        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
        }.collect { visibleItemsInfo ->

            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val viewportEndOffset = info.viewportEndOffset

            println("Bottom Offset: $viewportEndOffset, bottomGapDp: $bottomGapDp")


            println(
                "LaunchedEffect " +
                        "status: $messageStatus, " +
                        "viewportEndOffset: $viewportEndOffset"
            )

            if (total > 2) {
                val lastIndex = total - 2

                val lastItem = visibleItemsInfo.firstOrNull {
                    it.index == lastIndex
                }

                if (lastItem == null) {
                    println("invoke pre-scroll")
                    listState.animateScrollToItem(lastIndex)
                }

                awaitFrame()
                if (lastItem != null) {
                    val lastBottom = lastItem.size
                    val gap = viewportEndOffset - lastBottom

                    val finalGap = max(0, gap)
                    bottomGapDp = with(density) {
                        finalGap.toDp() - contentPaddingBottom - itemSpacing
                    }
                    println(
                        "SCREEN Last index: $lastIndex," +
                                " viewportEndOffset: $viewportEndOffset," +
                                " lastBottom: $lastBottom," +
                                " count: ${messages.size}," +
                                " finalGap: $finalGap"
                    )

                    try {
                        println("FIRST Scroll $messageStatus")
                        awaitFrame()
                        listState.animateScrollToItem(lastIndex)
                    } catch (e: Exception) {
                        println("First Exception: ${e.message}")
                    }
                } else {
                    try {
                        println("SECOND scroll $messageStatus")
                        awaitFrame()
                        listState.animateScrollToItem(lastIndex)
                    } catch (e: Exception) {
                        println("Second Exception: ${e.message}")
                    }
                }
            }
        }
    }

//    LaunchedEffect(Unit) {
//        snapshotFlow {
//            listState.layoutInfo.visibleItemsInfo
//        }.collect { visibleItemsInfo ->
//            println("Viewport offset: ${listState.layoutInfo.viewportEndOffset}")
//        }
//    }

    Box(
        modifier = Modifier
            .background(backgroundColor)
            .imePadding()
    ) {
        val lastMessage = messages.lastOrNull()

        Text(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 40.dp),
            fontSize = 16.sp,
            color = Color.Red,
            text = "STATUS: ${lastMessage?.messageStatus} index: ${messages.lastIndex}\n" +
                    "isAtBottom: $isAtBottom, autoScroll: $autoScrollEnabled\n" +
                    "scrollState: $uiScrollState"
        )

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
                modifier = Modifier.fillMaxSize().border(4.dp, Color.Gray),
                state = listState,
                contentPadding = PaddingValues(
                    top = contentPaddingTop + topAppbarHeight,
                    bottom = contentPaddingBottom
                ),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                itemsIndexed(
                    items = messages,
                    contentType = { index: Int, message: Message ->
                        message.messageStatus
                    },
                    key = { _: Int, message: Message ->
                        message.uiKey
                    }
                ) { index: Int, msg: Message ->
                    val modifier = if (msg.role == Role.Assistant &&
                        messages.lastIndex == index
                    ) {
                        Modifier.heightIn(bottomGapDp).border(2.dp, Color.Magenta)
                    } else if (messages.size > 2 && index == messages.lastIndex - 1) {
                        Modifier
                            .border(2.dp, Color.Cyan)
                            .drawBehind {
//                                if (messages.size > 2 && index == messages.lastIndex - 1) {
//                                    val viewportEndOffset = listState.layoutInfo.viewportEndOffset
//                                    println(
//                                        "🔥 drawBehind index: $index, " +
//                                                "height: ${size.height}, " +
//                                                "viewportEndOffset: $viewportEndOffset"
//                                    )
//                                }
                            }
                    } else {
                        Modifier.border(2.dp, Color.Blue)
                    }

                    MessageRow(
                        modifier = modifier,
                        message = msg
                    )
                }

                // This creates the “empty space below” so you can scroll.

            }
        }

        TopAppBar(
            modifier = Modifier.height(topAppbarHeight)
                .border(2.dp, Color.Red)
//                .background(brush = topAppbarBrush)
            ,
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

        InputArea(
            modifier = Modifier
                .align(Alignment.BottomStart)
//                .border(2.dp, Color.Cyan)
                .navigationBarsPadding()
//                .background(
//                    brush = inputBrush
//                )
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
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

        JumpToBottomButton(
            modifier = Modifier
                .navigationBarsPadding()
                .offset(y = (-90).dp)
                .align(Alignment.BottomEnd),
            enabled = jumpToBottomButtonEnabled,
            onClick = {
                coroutineScope.launch {
                    listState.scrollToItem(
                        index = messages.lastIndex,
                        scrollOffset = Int.MAX_VALUE
                    )
                }
            }
        )
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


val topAppbarBrush = Brush.verticalGradient(
    colors = listOf(
        backgroundColor.copy(alpha = .9f),
        backgroundColor.copy(alpha = .8f),
        backgroundColor.copy(alpha = .7f),
        backgroundColor.copy(alpha = .5f)
    )
)

val inputBrush = Brush.verticalGradient(
    colors = listOf(
        backgroundColor.copy(alpha = .7f),
        backgroundColor.copy(alpha = .8f),
        backgroundColor.copy(alpha = .9f)
    )
)

