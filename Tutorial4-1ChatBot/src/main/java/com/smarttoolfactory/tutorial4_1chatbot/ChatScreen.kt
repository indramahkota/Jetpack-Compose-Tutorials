package com.smarttoolfactory.tutorial4_1chatbot

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val messages by vm.messages.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on updates
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    var input by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg)
            }
        }

        Divider()

        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message") },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        vm.sendUserMessage(text)
                        input = ""
                    }
                }
            ) { Text("Send") }
        }
    }
}

@Composable
private fun MessageBubble(msg: UiMessage) {
    val isUser = msg.role == Role.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            tonalElevation = 2.dp,
            shape = MaterialTheme.shapes.medium
        ) {
            Column(Modifier.padding(12.dp).widthIn(max = 320.dp)) {
                Text(
                    text = msg.text.ifEmpty { if (msg.isStreaming) "…" else "" },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (msg.isStreaming) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}