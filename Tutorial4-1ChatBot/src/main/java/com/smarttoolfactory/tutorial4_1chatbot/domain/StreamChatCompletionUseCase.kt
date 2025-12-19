package com.smarttoolfactory.tutorial4_1chatbot.domain

import android.R.attr.prompt
import com.smarttoolfactory.tutorial4_1chatbot.data.ChatCompletionsRequest
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StreamChatCompletionUseCase @Inject constructor(
    private val repo: ChatStreamRepository
) {
    operator fun invoke(chatCompletionsRequest: ChatCompletionsRequest): Flow<StreamSignal> =
        repo.streamChatCompletion(chatCompletionsRequest)
}