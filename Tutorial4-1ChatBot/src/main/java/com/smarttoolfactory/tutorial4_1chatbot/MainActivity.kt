package com.smarttoolfactory.tutorial4_1chatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.smarttoolfactory.tutorial4_1chatbot.ui.ChatScreen
import com.smarttoolfactory.tutorial4_1chatbot.ui.ChatViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = this
        enableEdgeToEdge()
        setContent {

            ChatScreen(viewModel)

//            val okHttpClient = OkHttpClient.Builder()
//                .addInterceptor(ChuckerInterceptor(context))
//                .build()
//
//
//            ChatScreen(ChatViewModel(okHttpClient))

        }
    }
}
