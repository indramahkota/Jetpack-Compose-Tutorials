package com.smarttoolfactory.tutorial4_1chatbot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.BasicRichText
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.ChatTextField
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.JumpToBottomButton
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun LazyListState.isAtBottomPx(thresholdPx: Int = 6): Boolean {
    val info = layoutInfo
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return false
    val lastIndex = info.totalItemsCount - 1
    if (lastVisible.index != lastIndex) return false

    val viewportBottom = info.viewportEndOffset
    val itemBottom = lastVisible.offset + lastVisible.size
    return abs(itemBottom - viewportBottom) <= thresholdPx
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
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()

    val messages: SnapshotStateList<Message> = chatViewModel.messages
    val chatStatus = uiState.chatStatus

    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val isKeyboardOpen by rememberKeyboardState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        awaitFrame()
        focusRequester.requestFocus()
    }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) {
        100.dp.roundToPx()
    }

    val jumpToBottomButtonEnabled by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()

            if (lastVisible == null) {
                false
            } else {
                val viewportBottom = info.viewportEndOffset
                val itemBottom = lastVisible.offset + lastVisible.size
                val isAwayFromBottom = abs(itemBottom - viewportBottom) > thresholdPx

                isAwayFromBottom && isKeyboardOpen.not()
            }
        }
    }

    var autoScrollEnabled by remember { mutableStateOf(true) }

    val isAtBottom by remember {
        derivedStateOf { listState.isAtBottomPx(thresholdPx = 300) }
    }

    var position by remember {
        mutableIntStateOf(0)
    }

    var reservedSpace by remember {
        mutableStateOf(0.dp)
    }

// If user scrolls while away from bottom, treat as reading history
    LaunchedEffect(Unit) {
        snapshotFlow { isAtBottom to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (atBottom, inProgress) ->
                autoScrollEnabled = if (inProgress) {
                    false
                }else if (atBottom) {
                    true
                } else {
                    false
                }
            }
    }

    LaunchedEffect(chatStatus) {
        if (chatStatus != ChatStatus.Streaming) return@LaunchedEffect
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
                    } catch (e: Exception) {
                        println("FLOW exception")
                    }
                }
            }
    }

    // After user prompt message is added, scroll it to top after calculating difference between
    // prompt's bottom and viewports end offset(position before bottom padding)
    LaunchedEffect(messages.size, chatStatus, reservedSpace) {
        if (chatStatus == ChatStatus.AfterPrompt && messages.size > 2 && reservedSpace > 0.dp) {
            val lastIndexToScroll = (messages.lastIndex - 1).coerceIn(0, messages.lastIndex)
            println("SCROLL to $lastIndexToScroll, message size: ${messages.size}, reservedSpace: $reservedSpace")
            try {
                listState.animateScrollToItem(lastIndexToScroll)
                println("SCROLL COMPLETE")
            } catch (e: Exception) {
                println("Exception ${e.message}")
            }
        }
    }

    Box(
        modifier = Modifier
            .background(Color.LightGray.copy(alpha = .05f))
            .systemBarsPadding()
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

            Text(
                modifier = Modifier.height(120.dp),
                text = "autoScroll: $autoScrollEnabled, scroll in progress: ${listState.isScrollInProgress}\n" +
                        "isAtBottom: $isAtBottom\n" +
                        "jumpToBottomButtonEnabled: $jumpToBottomButtonEnabled\n" +
                        "position: $position\n" +
                        " state: $chatStatus"
            )

            val contentPadding = 12.dp
            val itemSpacing = 16.dp
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color.Red),
                state = listState,
                contentPadding = PaddingValues(contentPadding),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                itemsIndexed(
                    items = messages,
                    // TODO Add unique keys to user and assistant messages
//                    key = { it.id }
                ) { index: Int, msg: Message ->
                    val modifier =
                        when (index) {
                            messages.lastIndex - 1 if chatStatus == ChatStatus.AfterPrompt -> {
                                Modifier
                                    .onGloballyPositioned { layoutCoordinates ->

                                        // Get bottom of prompt message after it's added
                                        // before stream started
                                        val viewportEndOffset =
                                            listState.layoutInfo.viewportEndOffset

                                        val bounds = layoutCoordinates.boundsInParent()
                                        val height = bounds.height
                                        val bottom = bounds.bottom
                                        position = bottom.toInt()

                                        reservedSpace = with(density) {
                                            (viewportEndOffset - height).toDp() - contentPadding - itemSpacing
                                        }

                                        println(
                                            "index:$index, messages size: ${messages.size}" +
                                                    " bounds: ${bounds.height}, " +
                                                    "reservedSpace: $reservedSpace, " +
                                                    "status: $chatStatus"
                                        )
                                    }
                            }

                            messages.lastIndex if msg.role != Role.User && messages.size > 2 -> {
                                println("Add Assistant last modifier")
                                Modifier.heightIn(min = reservedSpace).border(2.dp, Color.Blue)
                            }

                            else -> {
                                Modifier
                            }
                        }
                    Box(
                        modifier = modifier
                    ) {
                        MessageRow(
                            modifier = Modifier,
                            message = msg
                        )
                    }
                }

//                if (
//                    chatStatus == ChatStatus.AfterPrompt ||
//                    chatStatus == ChatStatus.Thinking ||
//                    chatStatus == ChatStatus.Streaming
//                ) {
//                item {
//                    Box(modifier = Modifier.fillParentMaxSize().border(2.dp, Color.Red)) {
//
//                    }
//                }
//                }
            }

            InputArea(
                modifier = Modifier
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
        }

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
private fun MessageRow(
    modifier: Modifier = Modifier,
    message: Message
) {
    val isUser = message.role == Role.User
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        val text = message.text
        if (text.isNotEmpty()) {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                color = if (isUser) MaterialTheme.colorScheme.surface
                else Color.Transparent
            ) {
                Column(Modifier.padding(16.dp)) {
                    if (isUser) {
                        BasicRichText(
                            modifier = Modifier
                        ) {
                            Markdown(message.text)
                        }
                    } else {
                        BasicRichText(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Markdown(text)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun rememberKeyboardState(): State<Boolean> {
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    return rememberUpdatedState(isImeVisible)
}


@Preview
@Composable
fun LazyColumnTest() {

    var text by remember {
        mutableStateOf("")
    }

    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()

    val density = LocalDensity.current

    val padding = with(density) {
        100.toDp()
    }

    val iteHeight = with(density) {
        100.toDp()
    }

    val height = with(density) {
        1000.toDp()
    }

    var index by remember { mutableIntStateOf(3) }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
        }.collect {
            val viewportEndOffset = listState.layoutInfo.viewportEndOffset
            val height = listState.layoutInfo.viewportSize.height

            text = "viewportEndOffset: $viewportEndOffset, height:$height"
        }
    }

    Column {

        Text(text)
        Button(
            onClick = {
                scope.launch {
                    listState.scrollToItem(index)
                }
            }
        ) {
            Text("Scroll to $index")
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .border(2.dp, Color.Blue)
                .fillMaxWidth().height(height),
            contentPadding = PaddingValues(padding)
        ) {
            items(30) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(iteHeight)
                        .border(1.dp, Color.Red)
                ) {
                    Text("Index: $it")
                }
            }
        }
    }
}
