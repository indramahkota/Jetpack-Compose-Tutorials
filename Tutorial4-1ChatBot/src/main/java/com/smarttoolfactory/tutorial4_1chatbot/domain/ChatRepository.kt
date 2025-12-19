package com.smarttoolfactory.tutorial4_1chatbot.domain

import com.smarttoolfactory.tutorial4_1chatbot.data.ChatCompletionsRequest
import kotlinx.coroutines.flow.Flow

interface ChatStreamRepository {
    fun streamChatCompletion(request: ChatCompletionsRequest): Flow<StreamSignal>
}