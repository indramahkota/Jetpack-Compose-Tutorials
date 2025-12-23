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
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

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

    val jumpToBottomButtonEnabled by remember {
        derivedStateOf {
            listState.canScrollForward && isKeyboardOpen.not()
        }
    }

    val coroutineScope = rememberCoroutineScope()


    var autScroll by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo
        }.collect {
            val layoutInfo = listState.layoutInfo

            if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                val totalItemsCount = layoutInfo.totalItemsCount
                val viewportEndOffset = layoutInfo.viewportEndOffset
                val viewPortHeight = layoutInfo.viewportSize.height

                val lastItem = layoutInfo.visibleItemsInfo.last()
                val lastVisibleIndex = lastItem.index
                val lastVisibleOffset = lastItem.offset
                val lastHeight = lastItem.size

                val scrollInProgress = listState.isScrollInProgress

                println(
                    "totalItemsCount: $totalItemsCount, " +
                            "viewport EndOffset: $viewportEndOffset, height: $viewPortHeight" +
                            "index: $lastVisibleIndex, " +
                            "visibleOffset: $lastVisibleOffset, " +
                            "height $lastHeight"
                )

                // last item is visible
                autScroll = if (uiState.chatStatus != ChatStatus.Streaming) {
                    false
                } else if (lastVisibleIndex == totalItemsCount - 1 && scrollInProgress.not()) {

                    val viewportBottom = layoutInfo.viewportEndOffset
                    val itemBottom = lastItem.offset + lastItem.size

                    val diff = (itemBottom - viewportBottom).absoluteValue
                    if (diff < 100) {
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
        }
    }



    LaunchedEffect(messages.lastOrNull()?.text) {
        if (autScroll && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
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
                "autScroll: $autScroll, state: ${uiState.chatStatus}"
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
                value = input.take(200).replace(
                    Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{26FF}]"),
                    ""
                ),
                onValueChange = {
                    input = it.take(200).replace(
                        Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{26FF}]"),
                        ""
                    )
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

