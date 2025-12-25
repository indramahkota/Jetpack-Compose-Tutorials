package com.smarttoolfactory.tutorial4_1chatbot.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttoolfactory.tutorial4_1chatbot.data.ChatCompletionsRequest
import com.smarttoolfactory.tutorial4_1chatbot.domain.StreamChatCompletionUseCase
import com.smarttoolfactory.tutorial4_1chatbot.domain.StreamSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val chatStatus: ChatStatus = ChatStatus.Idle,
    val error: String? = null
)

enum class ChatStatus(val order: Int) {
    Idle(0),
    BeforePrompt(1),
    AfterPrompt(2),
    Thinking(3),
    Streaming(4),
    Completed(5),
    Failed(6)
}

fun ChatStatus.greaterThan(other: ChatStatus): Boolean = this.order > other.order
fun ChatStatus.lesserThan(other: ChatStatus): Boolean = this.order < other.order

enum class Role(val value: String) {
    User("user"), Assistant("assistant")
}

data class Chat(
    val title: String? = null,
    val messages: List<Message>? = null
)

data class Message(
    val id: String,
    val role: Role,
    val streaming: Boolean,
    val text: String,
    val feedback: Feedback? = null
)

data class Feedback(
    val text: String? = null,
    val reaction: Reaction? = null
) {
    enum class Reaction {
        ThumbsUp, ThumbsDown
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

    fun sendMessage(prompt: String) {
        _uiState.update {
            it.copy(chatStatus = ChatStatus.Thinking)
        }
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

            _uiState.update {
                it.copy(chatStatus = ChatStatus.BeforePrompt)
            }

            val userMessage = Message(
                id = request.id.orEmpty(),
                role = Role.User,
                streaming = false,
                text = prompt
            )

            messages.add(userMessage)

            _uiState.update {
                it.copy(chatStatus = ChatStatus.AfterPrompt)
            }

            val initialMessage = Message(
                id = userMessage.id,
                role = Role.Assistant,
                streaming = true,
                text = ""
            )
            messages.add(initialMessage)

            streamUseCase(chatCompletionsRequest = request)
                .onEach { stream: StreamSignal ->
                    when (stream) {
                        is StreamSignal.Start -> {
                            _uiState.update {
                                it.copy(chatStatus = ChatStatus.Thinking)
                            }
                        }

                        is StreamSignal.Delta -> {
                            _uiState.update {
                                it.copy(chatStatus = ChatStatus.Streaming)
                            }
                        }

                        is StreamSignal.Completed -> {
                            val lastIndex = messages.lastIndex
                            val currentMessage = messages.getOrNull(lastIndex)
                            currentMessage?.let {
                                messages[lastIndex] = it.copy(streaming = false)
                            }
                            println("ChatViewModel Completed")
                            _uiState.update {
                                it.copy(chatStatus = ChatStatus.Completed)
                            }
                        }

                        is StreamSignal.Failed -> {
                            println("ChatViewModel Failed: ${stream.throwable.message}")
                            _uiState.update {
                                it.copy(chatStatus = ChatStatus.Failed)
                            }
                        }

                    }
                }
//                .runningFold("") { acc: String, streamSignal: StreamSignal ->
//                    when (streamSignal) {
//                        is StreamSignal.Delta -> acc + streamSignal.text
//                        else -> acc
//                    }
//                }
//                .onEach { fullText ->
//                    val lastIndex = messages.lastIndex
//                    val currentMessage = messages.getOrNull(lastIndex)
//                    currentMessage?.let {
//                        messages[lastIndex] = it.copy(text = fullText)
//                    }
//                }

//                .flatMapConcat {
//                    flow {
//                        delay(30)
//                        emit(it)
//                    }
//                }
                .onEach { streamSignal: StreamSignal ->
                    if (streamSignal is StreamSignal.Delta) {
                        val lastIndex = messages.lastIndex
                        val currentMessage = messages.getOrNull(lastIndex)
                        val text = streamSignal.text
                        currentMessage?.let {
                            messages[lastIndex] = it.copy(text = it.text + text)
                        }
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
                    println("ChatViewModel onComplete")
                }
                .collect()
        }
    }
}
