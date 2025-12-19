package com.smarttoolfactory.tutorial4_1chatbot.domain

sealed interface StreamSignal {
    data class Delta(val text: String) : StreamSignal
    data class Completed(val finishReason: String? = null) : StreamSignal
    data class Failed(val throwable: Throwable) : StreamSignal
}