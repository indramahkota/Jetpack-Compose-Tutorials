package com.smarttoolfactory.tutorial4_1chatbot

import java.util.UUID

enum class Role { USER, ASSISTANT }

data class UiMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val isStreaming: Boolean = false
)