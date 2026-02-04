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
import com.halilibo.richtext.ui.ListStyle
import com.halilibo.richtext.ui.RichTextStyle
import com.smarttoolfactory.tutorial4_1chatbot.markdown.MarkdownComposer
import com.smarttoolfactory.tutorial4_1chatbot.ui.Message
import com.smarttoolfactory.tutorial4_1chatbot.ui.MessageStatus
import com.smarttoolfactory.tutorial4_1chatbot.ui.Role
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.MarkDownStyle


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

                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BasicRichText(
                            modifier = Modifier,
                            style = MarkDownStyle.DefaultTextStyle
                        ) {
                            MarkdownComposer(
                                markdown = message.text,
                                debug = false,
                                messageKey = message.uiKey,
                                // Completed paragraphs will auto-disable via completedByNodeKey.
                                animate = (message.messageStatus == MessageStatus.Streaming),
                            )
                        }

                        message.feedback?.let {
                            MessageFeedbackRow(message = message)
                        }
                    }

                }
            }
        }
    }
}
