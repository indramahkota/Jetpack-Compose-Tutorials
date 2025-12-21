package com.smarttoolfactory.tutorial4_1chatbot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.util.detectTapGesturesIf

@Composable
fun ChatScreen(
    chatViewModel: ChatViewModel
) {

    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()

    val messages = chatViewModel.messages
    val listState = rememberLazyListState()

    val focusManager = LocalFocusManager.current


    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(uiState.chatStatus) {
        if (uiState.chatStatus == ChatStatus.Streaming) {
            println("SCROLL to BOTTOM in STREAM")
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    var input by remember { mutableStateOf("") }

    val isKeyboardOpen by rememberKeyboardState()

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().pointerInput(Unit){
        detectTapGesturesIf (
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg: Message ->
                MessageRow(msg)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message") },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        focusManager.clearFocus()
                        val text = input.trim()
                        if (text.isNotEmpty()) {
                            chatViewModel.sendMessage(text)
                            input = ""
                        }
                    }),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))

            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        focusManager.clearFocus()
                        chatViewModel.sendMessage(text)
                        input = ""
                    }
                }
            ) { Text("Send") }
        }
    }
}

@Composable
private fun MessageRow(message: Message) {

    val isUser = message.role == Role.User

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(Modifier.padding(12.dp).widthIn(max = 320.dp)) {
                BasicRichText {
                    Markdown(message.text)
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