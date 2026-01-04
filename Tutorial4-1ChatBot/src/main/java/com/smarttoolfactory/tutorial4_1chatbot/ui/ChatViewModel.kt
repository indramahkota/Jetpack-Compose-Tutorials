package com.smarttoolfactory.tutorial4_1chatbot.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttoolfactory.tutorial4_1chatbot.data.ChatCompletionsRequest
import com.smarttoolfactory.tutorial4_1chatbot.domain.StreamChatCompletionUseCase
import com.smarttoolfactory.tutorial4_1chatbot.domain.StreamSignal
import com.smarttoolfactory.tutorial4_1chatbot.util.deltasToMarkdownTokensWithDelay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val scrollState: ScrollState = ScrollState.None,
    val inputState: InputState = InputState.Idle,
    val error: String? = null
) {
    sealed interface ScrollState {
        object None : ScrollState
        object AutoScroll : ScrollState
        object PinToTop : ScrollState
        object ScrollOnComplete : ScrollState
    }

    sealed interface InputState {
        object Idle : InputState
        object Typing : InputState
        object Streaming : InputState
    }
}

enum class MessageStatus {
    Queued,        // user message accepted, request not yet acted upon
    Streaming,    // assistant streaming
    Completed,     // final
    Failed,        // error
    Cancelled
}

data class Chat(
    val title: String? = null,
    val messages: List<Message>? = null
)

data class Message(
    val uiKey: String,
    val messageId: String,
    val role: Role,
    val text: String = "",
    val messageStatus: MessageStatus = MessageStatus.Queued,
    val feedback: Feedback? = null,
    val failure: Failure? = null
)

enum class Role(val value: String) {
    User("user"), Assistant("assistant")
}

data class Failure(
    val text: String,
    val promptToRetry: String,
    val retry: Boolean
)

data class Feedback(
    val text: String? = null,
    val reaction: Reaction = Reaction.None
) {
    enum class Reaction {
        None, ThumbsUp, ThumbsDown
    }
}

private const val Model = "gpt-4o-mini"

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val streamUseCase: StreamChatCompletionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val messages = mutableStateListOf<Message>()

    var streamJob: Job? = null

    fun sendMessage(prompt: String) {
        streamJob?.cancel()

        viewModelScope.launch {
            val request = ChatCompletionsRequest(
                model = Model,
                stream = true,
                messages = listOf(
                    ChatCompletionsRequest.Message(
                        role = Role.User.value,
                        content = prompt
                    )
                )
            )

            val userMessage = Message(
                messageId = request.id.orEmpty(),
                uiKey = UUID.randomUUID().toString(),
                role = Role.User,
                text = prompt,
            )

            messages.add(userMessage)

            val initialMessage = Message(
                messageId = userMessage.messageId,
                uiKey = UUID.randomUUID().toString(),
                role = Role.Assistant,
                messageStatus = MessageStatus.Queued
            )
            messages.add(initialMessage)

            if (messages.size > 2) {
                _uiState.update {
                    it.copy(
                        scrollState = ChatUiState.ScrollState.PinToTop
                    )
                }
            }

            repeat(5) {
                awaitFrame()
            }

            streamJob = streamUseCase(chatCompletionsRequest = request)
                .onEach { stream: StreamSignal ->
                    when (stream) {
                        is StreamSignal.Start -> {}

                        is StreamSignal.Delta -> {
                            _uiState.update {
                                it.copy(
                                    scrollState = ChatUiState.ScrollState.AutoScroll
                                )
                            }
                        }

                        is StreamSignal.Completed -> {
                            _uiState.update {
                                it.copy(
                                    scrollState = ChatUiState.ScrollState.ScrollOnComplete
                                )
                            }
                        }

                        is StreamSignal.Failed -> {
                            _uiState.update {
                                it.copy(
                                    scrollState = ChatUiState.ScrollState.ScrollOnComplete
                                )
                            }
                        }

                    }
                }
                .onEach { streamSignal: StreamSignal ->
                    when (streamSignal) {
                        is StreamSignal.Failed -> {
                            updateMessageById(initialMessage.uiKey) { message ->
                                message.copy(
                                    messageStatus = MessageStatus.Failed,
                                    failure = Failure(
                                        text = "Error Occurred",
                                        promptToRetry = prompt,
                                        retry = true
                                    )
                                )
                            }
                        }

                        else -> Unit
                    }
                }
                .filterIsInstance<StreamSignal.Delta>()
                .map { it.text }
                // 🔥Functions to split deltas to chunks after delay
                .deltasToMarkdownTokensWithDelay(
                    flushRemainderOnComplete = true,
                    delayMillis = 16
                )
//                .deltasToWordsWithDelay(
//                    delayMillis = 30
//                )
                .onEach { chunk ->
                    updateMessageById(initialMessage.uiKey) { message ->
                        message.copy(
                            messageStatus = MessageStatus.Streaming,
                            text = message.text + chunk
                        )
                    }
                }
                .flowOn(Dispatchers.Default)
                .catch { t ->
                    _uiState.update {
                        it.copy(
                            error = t.message ?: "Unknown error"
                        )
                    }
                }
                .onCompletion {
                    updateMessageById(initialMessage.uiKey) { message ->
                        message.copy(
                            messageStatus = MessageStatus.Completed,
                            feedback = Feedback()
                        )
                    }
                }
                .launchIn(this)
        }
    }

    private fun updateMessageById(id: String, transform: (Message) -> Message) {
        val index = messages.indexOfFirst { it.uiKey == id }
        if (index >= 0) {
            messages[index] = transform(messages[index])
        }
    }

    fun cancelStream() {
        streamJob?.cancel()
        streamJob = null

        // Optionally mark active assistant as Cancelled
//        val assistantId = _uiState.value.activeAssistantId
//        if (assistantId != null) {
//            updateMessageById(assistantId) { it.copy(messageStatus = MessageStatus.Cancelled) }
//        }
    }
}
