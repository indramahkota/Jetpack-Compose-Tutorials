package com.smarttoolfactory.tutorial4_1chatbot.ui.component.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextStyle
import com.smarttoolfactory.tutorial4_1chatbot.markdown.MarkdownComposer
import com.smarttoolfactory.tutorial4_1chatbot.ui.Message
import com.smarttoolfactory.tutorial4_1chatbot.ui.MessageStatus
import com.smarttoolfactory.tutorial4_1chatbot.ui.Role

val style = RichTextStyle.Default.copy(
    headingStyle = { index, textStyle ->
        when (index) {
            1 -> textStyle.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 32.sp,
                color = Color.Red
            )

            2 -> textStyle.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 28.sp,
                color = Color.Red
            )

            3 -> textStyle.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 26.sp,
                color = Color.Red
            )

            4 -> textStyle.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp,
                color = Color.Red
            )

            5 -> textStyle.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
                color = Color.Red
            )

            else -> textStyle.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 20.sp,
                color = Color.Red
            )
        }
    }
)

@Composable
fun MessageRow(
    modifier: Modifier = Modifier,
    message: Message
) {
    val isUser = message.role == Role.User

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {

        if (isUser) {
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.padding(16.dp)) {
                    BasicRichText(
                        modifier = Modifier
                    ) {
                        Markdown(message.text)
                    }
                }
            }
        } else {
            when (message.messageStatus) {
                MessageStatus.Queued -> {
                    LoadingRow()
                }

                MessageStatus.Failed -> {
                    message.failure?.let {
                        ErrorMessageRow(message = message)
                    }
                }

                else -> {
                    Surface(
                        tonalElevation = 2.dp,
                        shape = MaterialTheme.shapes.medium,
                        color = Color.Transparent
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            BasicRichText(
                                modifier = Modifier,
                                style = RichTextStyle.Default
                            ) {
                                MarkdownComposer(
                                    markdown = message.text,
                                    debug = false
                                )
                            }

//                            BasicRichText(
//                                modifier = Modifier.fillMaxWidth(),
//                                style = style
//                            ) {
//                                Markdown(message.text)
//                            }
                            message.feedback?.let {
                                MessageFeedbackRow(message = message)
                            }
                        }
                    }
                }
            }
        }
    }
}
