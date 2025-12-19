package com.smarttoolfactory.tutorial4_1chatbot.data.model

sealed interface SseMessage {
    data class Event(val type: String?, val data: String) : SseMessage
    data class Error(val throwable: Throwable) : SseMessage
    data object Closed : SseMessage
    data object Opened : SseMessage
}