package com.smarttoolfactory.tutorial4_1chatbot.data.sseclient


import com.smarttoolfactory.tutorial4_1chatbot.data.model.SseMessage
import kotlinx.coroutines.flow.Flow
import okhttp3.Request

interface ChatSseDataSource {
    fun connectAsFlow(request: Request): Flow<SseMessage>
}