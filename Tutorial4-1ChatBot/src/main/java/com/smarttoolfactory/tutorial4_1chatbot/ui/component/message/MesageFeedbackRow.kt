package com.smarttoolfactory.tutorial4_1chatbot.ui.component.message

import android.R.attr.onClick
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.ThumbDownAlt
import androidx.compose.material.icons.filled.ThumbUpAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.smarttoolfactory.tutorial4_1chatbot.ui.Message

@Composable
fun MessageFeedbackRow(
    modifier: Modifier = Modifier,
    message: Message
) {
    Row(
        modifier = modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            modifier = Modifier.size(20.dp),
            onClick = {

            }
        ) {
            Icon(
                tint = Color.Gray,
                imageVector = Icons.Filled.CopyAll,
                contentDescription = null
            )
        }
        IconButton(
            modifier = Modifier.size(20.dp),
            onClick = {

            }
        ) {
            Icon(
                tint = Color.Gray,
                imageVector = Icons.Filled.ThumbUpAlt,
                contentDescription = null
            )
        }

        IconButton(
            modifier = Modifier.size(20.dp),
            onClick = {

            }
        ) {
            Icon(
                tint = Color.Gray,
                imageVector = Icons.Filled.ThumbDownAlt,
                contentDescription = null
            )
        }
    }
}