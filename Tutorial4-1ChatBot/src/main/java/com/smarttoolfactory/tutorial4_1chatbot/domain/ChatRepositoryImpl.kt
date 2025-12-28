package com.smarttoolfactory.tutorial4_1chatbot.domain

import com.smarttoolfactory.tutorial4_1chatbot.data.ChatCompletionsRequest
import com.smarttoolfactory.tutorial4_1chatbot.data.OpenAiRequestFactory
import com.smarttoolfactory.tutorial4_1chatbot.data.model.ChatCompletionsChunk
import com.smarttoolfactory.tutorial4_1chatbot.data.model.SseMessage
import com.smarttoolfactory.tutorial4_1chatbot.data.sseclient.ChatSseDataSource
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject

private const val Done = "[DONE]"

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
            .catch {
                emit(SseMessage.Error(it))
            }
            .mapNotNull { sseMessage: SseMessage ->
                when (sseMessage) {
                    is SseMessage.Opened -> {
                        StreamSignal.Start
                    }
                    is SseMessage.Error -> {
                        StreamSignal.Failed(sseMessage.throwable)
                    }
                    is SseMessage.Event -> {
                        parseEvent(sseMessage)
                    }
                    is SseMessage.Closed -> {
                        StreamSignal.Completed("closed")
                    }
                }
            }
    }

    private fun parseEvent(evt: SseMessage.Event): StreamSignal? {
        val data = evt.data.trim()

        if (data == Done) return StreamSignal.Completed()

        val chunk = runCatching { adapter.fromJson(data) }.getOrNull()
            ?: return null

        val choice0 = chunk.choices.firstOrNull() ?: return null

        choice0.finishReason?.let { fr ->
            return StreamSignal.Completed(finishReason = fr)
        }

        val deltaText = choice0.delta.content
        return if (deltaText.isNullOrEmpty()) null else StreamSignal.Delta(deltaText)
    }
}
