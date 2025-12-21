package com.smarttoolfactory.tutorial4_1chatbot.data.sseclient

import com.smarttoolfactory.tutorial4_1chatbot.data.model.SseMessage
import kotlinx.coroutines.channels.awaitClose

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import javax.inject.Inject

class ChatSseDataSourceImpl @Inject constructor(
    private val okHttpClient: OkHttpClient
) : ChatSseDataSource {

    override fun connectAsFlow(
        request: Request
    ): Flow<SseMessage> = callbackFlow {
        val factory = EventSources.createFactory(okHttpClient)

        val eventSource: EventSource = factory.newEventSource(
            request,
            object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    super.onOpen(eventSource, response)
                    println("1️⃣ ChatSseDataSourceImpl onOpen() response: $response, thread: ${Thread.currentThread().name}")
                    trySend(SseMessage.Opened)
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String
                ) {
//                    println("🔥ChatSseDataSourceImpl onEvent() id: $id, type: $type\n$data")
                    trySend(SseMessage.Event(type = type, data = data))
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?
                ) {
                    println("🚀 ChatSseDataSourceImpl onFailure() ${t?.message}, response: $response")

                    val err: Throwable =
                        t ?: RuntimeException("SSE failure, HTTP=${response?.code}")
                    trySend(SseMessage.Error(err))
                    close(err)
                }

                override fun onClosed(eventSource: EventSource) {
                    println("😵‍💫 ChatSseDataSourceImpl onClosed()")
                    trySend(SseMessage.Closed)
                    close()
                }
            }
        )

        awaitClose {
            println("😱ChatSseDataSourceImpl Flow awaitClose")
            eventSource.cancel()
        }
    }
}