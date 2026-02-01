package com.smarttoolfactory.tutorial4_1chatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.RichTextThemeProvider
import com.smarttoolfactory.tutorial4_1chatbot.markdown.LocalRevealStore
import com.smarttoolfactory.tutorial4_1chatbot.markdown.RevealStore
import com.smarttoolfactory.tutorial4_1chatbot.ui.ChatScreen
import com.smarttoolfactory.tutorial4_1chatbot.ui.ChatViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            RichTextThemeProvider(
                textStyleProvider = {
                    TextStyle.Default.copy(
                        fontSize = 18.sp,
                        lineHeight = 24.sp
                    )
                }
            ) {
                val revealStore = remember { RevealStore() }
                CompositionLocalProvider(LocalRevealStore provides revealStore) {
                    ChatScreen(viewModel)
                }
            }
        }
    }
}
