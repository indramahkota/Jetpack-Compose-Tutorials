package com.smarttoolfactory.tutorial4_1chatbot.data.model

data class ChatCompletionsChunk(
    val id: String? = null,
    val choices: List<Choice> = emptyList()
) {
    data class Choice(
        val index: Int = 0,
        val delta: Delta = Delta(),
        val finishReason: String? = null
    )

    data class Delta(
        val role: String? = null,
        val content: String? = null
    )
}
