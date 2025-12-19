package com.smarttoolfactory.tutorial4_1chatbot.data

// data/openai/OpenAiRequestFactory.kt
import com.smarttoolfactory.tutorial4_1chatbot.Configs
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@JsonClass(generateAdapter = true)
data class ChatCompletionsRequest(
    val model: String? = null,
    val stream: Boolean = true,
    val messages: List<Message>? = null
) {
    @JsonClass(generateAdapter = true)
    data class Message(
        val role: String? = null,
        val content: String? = null
    )
}

class OpenAiRequestFactory @Inject constructor(moshi: Moshi) {

    private val adapter =
        moshi.adapter(ChatCompletionsRequest::class.java)

    fun chatCompletionsStreamRequest(request: ChatCompletionsRequest): Request {

        val bodyJson = adapter.toJson(request)
        val body = bodyJson.toRequestBody("application/json".toMediaType())

        return Request.Builder()
            .url(Configs.url)
            .post(body)
            .build()
    }
}

/** Minimal JSON string escaping for embedding prompt safely */
private fun String.jsonEscape(): String =
    buildString {
        append('"')
        for (c in this@jsonEscape) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
