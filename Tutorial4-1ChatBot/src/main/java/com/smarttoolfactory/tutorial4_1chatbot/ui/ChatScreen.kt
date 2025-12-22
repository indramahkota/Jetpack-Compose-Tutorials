package com.smarttoolfactory.tutorial4_1chatbot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.util.detectTapGesturesIf
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.ChatTextField
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.JumpToBottomButton
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel
) {
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()

    val messages = chatViewModel.messages
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

    // TODO Fix scrolling when new deltas are appended, user touched button or scrolls up
    // while new deltas are appended
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(
                index = messages.lastIndex,
                scrollOffset = Int.MAX_VALUE
            )
        }
    }

    LaunchedEffect(uiState.chatStatus) {
        if (uiState.chatStatus == ChatStatus.Streaming) {
            listState.animateScrollToItem(
                index = messages.lastIndex,
                scrollOffset = Int.MAX_VALUE
            )
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
                    detectTapGesturesIf(
                        predicate = {
                            isKeyboardOpen
                        },
                        onTap = {
                            focusManager.clearFocus()
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                chatViewModel.sendMessage(text)
                                input = ""
                            }
                        }
                    )
                }) {
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
                    listState.scrollToItem(messages.lastIndex)
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

