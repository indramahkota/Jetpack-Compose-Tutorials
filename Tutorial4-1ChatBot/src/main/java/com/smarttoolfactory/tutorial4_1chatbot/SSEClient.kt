package com.smarttoolfactory.tutorial4_1chatbot

import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

class SseClient(
    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // important for long-lived streams
        .build()
) {
    fun connect(
        request: Request,
        onEvent: (event: String?, data: String) -> Unit,
        onError: (Throwable) -> Unit,
        onClosed: () -> Unit
    ): EventSource {
        val factory = EventSources.createFactory(okHttp)

        return factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                // Connected
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {

                // `type` corresponds to the "event:" field; often null.
                // `data` corresponds to the "data:" payload (may be JSON).
                onEvent(type, data)
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                onError(t ?: RuntimeException("SSE failure, HTTP=${response?.code}"))
            }

            override fun onClosed(eventSource: EventSource) {
                onClosed()
            }
        })
    }
}