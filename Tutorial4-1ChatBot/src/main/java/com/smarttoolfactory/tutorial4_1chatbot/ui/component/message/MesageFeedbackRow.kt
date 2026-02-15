package com.smarttoolfactory.tutorial4_1chatbot.ui.component.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.smarttoolfactory.tutorial4_1chatbot.R
import com.smarttoolfactory.tutorial4_1chatbot.ui.Message

@Composable
fun MessageFeedbackRow(
    modifier: Modifier = Modifier,
    message: Message
) {
    Row(
        modifier = modifier.padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        IconButton(
            modifier = Modifier.size(28.dp),
            onClick = {

            }
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                tint = Color.Gray,
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = null
            )
        }
        IconButton(
            modifier = Modifier.size(28.dp),
            onClick = {

            }
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                tint = Color.Gray,
                painter = painterResource(R.drawable.ic_thumbs_up),
                contentDescription = null
            )
        }

        IconButton(
            modifier = Modifier.size(28.dp),
            onClick = {

            }
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                tint = Color.Gray,
                painter = painterResource(R.drawable.ic_thumbs_down),
                contentDescription = null
            )
        }

        IconButton(
            modifier = Modifier.size(28.dp),
            onClick = {

            }
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                tint = Color.Gray,
                painter = painterResource(R.drawable.ic_ellipsis_vertical),
                contentDescription = null
            )
        }
    }
}