package com.smarttoolfactory.tutorial4_1chatbot.domain

import com.smarttoolfactory.tutorial4_1chatbot.data.ChatCompletionsRequest
import com.smarttoolfactory.tutorial4_1chatbot.data.model.ChatCompletionsChunk
import com.smarttoolfactory.tutorial4_1chatbot.data.OpenAiRequestFactory
import com.smarttoolfactory.tutorial4_1chatbot.data.sseclient.ChatSseDataSource
import com.smarttoolfactory.tutorial4_1chatbot.data.model.SseMessage
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

class ChatStreamRepositoryImpl @Inject constructor(
    private val chatSseDataSource: ChatSseDataSource,
    private val requestFactory: OpenAiRequestFactory,
    moshi: Moshi
) : ChatStreamRepository {

    private val adapter: JsonAdapter<ChatCompletionsChunk> =
        moshi.adapter(ChatCompletionsChunk::class.java)

    override fun streamChatCompletion(request: ChatCompletionsRequest): Flow<StreamSignal> {
        val request = requestFactory.chatCompletionsStreamRequest(request)

        return chatSseDataSource.connectAsFlow(request)
            .filterIsInstance<SseMessage.Event>()
            .mapNotNull { evt ->
                val data = evt.data.trim()

                if (data == "[DONE]") return@mapNotNull StreamSignal.Completed()

                val chunk = runCatching { adapter.fromJson(data) }.getOrNull()
                    ?: return@mapNotNull null

                val choice0 = chunk.choices.firstOrNull() ?: return@mapNotNull null

                choice0.finishReason?.let { fr ->
                    return@mapNotNull StreamSignal.Completed(finishReason = fr)
                }

                val deltaText = choice0.delta.content
                if (deltaText.isNullOrEmpty()) null else StreamSignal.Delta(deltaText)
            }
    }
}
