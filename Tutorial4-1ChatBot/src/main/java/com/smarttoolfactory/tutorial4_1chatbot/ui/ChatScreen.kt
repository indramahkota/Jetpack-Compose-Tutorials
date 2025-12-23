package com.smarttoolfactory.tutorial4_1chatbot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.flow.filter
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

private fun LazyListState.isNearBottom(itemsThreshold: Int = 1): Boolean {
    val info = layoutInfo
    val lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: return false
    val lastIndex = info.totalItemsCount - 1
    return (lastIndex - lastVisibleIndex) <= itemsThreshold
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

sealed interface ScrollRequest {
    data object ForceToBottom : ScrollRequest   // user sent, jump button
    data object PinToBottom : ScrollRequest     // streaming pin tick
    data object MaybeToBottom : ScrollRequest   // new message arrived if allowed
}


@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel
) {
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()

    val messages: SnapshotStateList<Message> = chatViewModel.messages
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

                listState.canScrollForward && isAwayFromBottom && isKeyboardOpen.not()
            }
        }
    }

    var autoScrollEnabled by remember { mutableStateOf(true) }

    val isNearBottom by remember {
        derivedStateOf { listState.isNearBottom(itemsThreshold = 1) }
    }
    val isAtBottom by remember {
        derivedStateOf { listState.isAtBottomPx(thresholdPx = 300) }
    }

// If user scrolls while away from bottom, treat as "reading history"
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { it } // when scrolling starts
            .collect {
                if (!isNearBottom) autoScrollEnabled = false
            }
    }

    // If user comes back to bottom, re-enable
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) autoScrollEnabled = true
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (autoScrollEnabled && isNearBottom) {
            listState.scrollToItem(messages.lastIndex, Int.MAX_VALUE)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { isAtBottom to listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (atBottom, inProgress) ->
                if (atBottom && !inProgress) {
                    autoScrollEnabled = true
                }
            }
    }

    LaunchedEffect(uiState.chatStatus) {
        if (uiState.chatStatus != ChatStatus.Streaming) return@LaunchedEffect

        snapshotFlow { autoScrollEnabled && isAtBottom && !listState.isScrollInProgress }
            .distinctUntilChanged()
            .flatMapLatest { shouldPin ->
                if (shouldPin) tickerFlow(100) else emptyFlow()
            }
            .collect {
                if (messages.isNotEmpty()) {
                    try {
                        listState.scrollToItem(messages.lastIndex, Int.MAX_VALUE)
                    } catch (e: Exception) {
                    }
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
                "autoScroll: $autoScrollEnabled\n" +
                        "isNearBottom: $isNearBottom\n" +
                        "isAtBottom: $isAtBottom\n" +
                        "jumpToBottomButtonEnabled: $jumpToBottomButtonEnabled\n" +
                        " state: ${uiState.chatStatus}"
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    items = messages,
                    // TODO Add unique keys to user and assistant messages
//                    key = { it.id }
                ) { msg: Message ->
                    MessageRow(msg)
                }
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
private fun MessageRow(message: Message) {
    val isUser = message.role == Role.User
    Row(
        modifier = Modifier.fillMaxWidth(),
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

