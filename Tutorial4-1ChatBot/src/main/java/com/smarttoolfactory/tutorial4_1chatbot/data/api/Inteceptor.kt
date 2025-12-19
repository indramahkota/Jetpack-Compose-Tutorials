package com.smarttoolfactory.tutorial4_1chatbot.data.api

import com.smarttoolfactory.tutorial4_1chatbot.Configs
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class OpenAiInterceptor @Inject constructor(
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val newRequest = original.newBuilder()
            .addHeader("Accept", "text/event-stream")
            .header("Authorization", "Bearer ${Configs.apiKey}")
            .header("Content-Type", "application/json")
            .build()

        return chain.proceed(newRequest)
    }
}
