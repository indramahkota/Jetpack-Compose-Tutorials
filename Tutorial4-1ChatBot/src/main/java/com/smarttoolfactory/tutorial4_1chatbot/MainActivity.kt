package com.smarttoolfactory.tutorial4_1chatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chuckerteam.chucker.api.ChuckerInterceptor
import okhttp3.OkHttpClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = this
        enableEdgeToEdge()
        setContent {

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(ChuckerInterceptor(context))
                .build()


            ChatScreen(ChatViewModel(okHttpClient))

        }
    }
}
