package com.smarttoolfactory.tutorial4_1chatbot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smarttoolfactory.tutorial4_1chatbot.data.ChatCompletionsRequest
import com.smarttoolfactory.tutorial4_1chatbot.domain.StreamChatCompletionUseCase
import com.smarttoolfactory.tutorial4_1chatbot.domain.StreamSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val text: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ChatSseViewModel @Inject constructor(
    private val streamUseCase: StreamChatCompletionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun start(prompt: String) {
        viewModelScope.launch {
            _uiState.value = ChatUiState(isStreaming = true)

            streamUseCase(
                ChatCompletionsRequest(
                    model = "",
                    stream = true,
                    messages = listOf(
                        ChatCompletionsRequest.Message(
                            role = "user",
                            content = prompt
                        )
                    )
                )
            )
                .runningFold("") { acc, sig ->
                    when (sig) {
                        is StreamSignal.Delta -> acc + sig.text
                        else -> acc
                    }
                }
                .onEach { fullText ->
                    _uiState.update { it.copy(text = fullText, isStreaming = true, error = null) }
                }
                .catch { t ->
                    _uiState.update {
                        it.copy(
                            isStreaming = false,
                            error = t.message ?: "Unknown error"
                        )
                    }
                }
                .onCompletion {
                    _uiState.update { it.copy(isStreaming = false) }
                }
                .collect()
        }
    }
}
