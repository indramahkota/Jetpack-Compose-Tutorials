package com.smarttoolfactory.tutorial4_1chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSource
import java.io.IOException

class ChatViewModel(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) : ViewModel() {

    private val _messages = MutableStateFlow<List<UiMessage>>(emptyList())
    val messages: StateFlow<List<UiMessage>> = _messages

    private var call: Call? = null

    fun sendUserMessage(text: String) {
        // 1) Add user message
        _messages.update { it + UiMessage(role = Role.USER, text = text) }

        // 2) Create placeholder assistant message that will grow
        val assistantId = "asst_${System.currentTimeMillis()}"
        _messages.update {
            it + UiMessage(
                id = assistantId,
                role = Role.ASSISTANT,
                text = "",
                isStreaming = true
            )
        }

        // 3) Start streaming response (SSE)
        startStreaming(
            assistantMessageId = assistantId,
            userText = text
        )
    }

    fun cancelStreaming() {
        call?.cancel()
        call = null
        // Mark any streaming message as finished
        _messages.update { list ->
            list.map { if (it.isStreaming) it.copy(isStreaming = false) else it }
        }
    }

    private fun startStreaming(assistantMessageId: String, userText: String) {
        cancelStreaming()

        // Replace with your endpoint.
        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Authorization", "Bearer ${BuildConfig.API_KEY}")
            .post(RequestBody.create("application/json".toMediaTypeOrNull(), buildBody(userText)))
            .build()

        call = okHttpClient.newCall(request)
        call!!.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {

                viewModelScope.launch {
                    appendDelta(assistantMessageId, "\n\n[Stream error: ${e.message}]")
                    endStreaming(assistantMessageId)
                }
            }

            override fun onResponse(call: Call, response: Response) {

                println("RESPONSE: ${response.body.toString()}")
                if (!response.isSuccessful) {
                    viewModelScope.launch {
                        appendDelta(assistantMessageId, "\n\n[HTTP ${response.code}]")
                        endStreaming(assistantMessageId)
                    }
                    return
                }

                val source = response.body?.source() ?: run {
                    viewModelScope.launch { endStreaming(assistantMessageId) }
                    return
                }

                // Read SSE lines on background thread, push UI updates via viewModelScope
                viewModelScope.launch(Dispatchers.IO) {
                    readSse(source, assistantMessageId)
                }
            }
        })
    }

    /**
     * Minimal SSE parser:
     * - SSE events are lines like: "data: {...}"
     * - Events separated by blank line
     */
    private suspend fun readSse(source: BufferedSource, assistantMessageId: String) {
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                if (!line.startsWith("data:")) continue

                val payload = line.removePrefix("data:").trim()

                // Some servers use [DONE]
                if (payload == "[DONE]") {
                    endStreaming(assistantMessageId)
                    break
                }

                // Parse your JSON event format here.
                // Expect something like:
                // { "type": "response.output_text.delta", "delta": "Hel" }
                val event = parseEvent(payload)

                when (event.type) {
                    "response.output_text.delta" -> appendDelta(
                        assistantMessageId,
                        event.delta ?: ""
                    )

                    "response.completed" -> {
                        endStreaming(assistantMessageId)
                        break
                    }
                }
            }
        } catch (t: Throwable) {
            appendDelta(assistantMessageId, "\n\n[Stream error: ${t.message}]")
            endStreaming(assistantMessageId)
        }
    }

    private fun appendDelta(assistantMessageId: String, delta: String) {
        _messages.update { list ->
            list.map { msg ->
                if (msg.id == assistantMessageId) msg.copy(text = msg.text + delta) else msg
            }
        }
    }

    private fun endStreaming(assistantMessageId: String) {
        _messages.update { list ->
            list.map { msg ->
                if (msg.id == assistantMessageId) msg.copy(isStreaming = false) else msg
            }
        }
    }

    // ----- Helpers you replace with real ones -----

    private fun buildBody(userText: String): String {
        // Example body; match your server/OpenAI proxy contract.
        return """
            {
             "model": "gpt-4o-mini",
              "input": [
                {"role": "user", "content": [{"type": "input_text", "text": ${userText.jsonEscape()}}]}
              ],
              "stream": true
            }
        """.trimIndent()
    }

    data class StreamEvent(val type: String, val delta: String?)

    private fun parseEvent(json: String): StreamEvent {
        // Keep it simple in this snippet.
        // In production use kotlinx.serialization or Moshi.
        val type =
            "\"type\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(json)?.groupValues?.get(1) ?: "unknown"
        val delta = "\"delta\"\\s*:\\s*\"([^\"]*)\"".toRegex().find(json)?.groupValues?.get(1)
        return StreamEvent(type, delta)
    }
}

private fun String.jsonEscape(): String {
    // Wrap and escape for JSON string literal quickly; production: use a JSON lib.
    val escaped = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
    return "\"$escaped\""
}
