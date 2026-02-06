package com.smarttoolfactory.tutorial4_1chatbot.ui.component.button

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
fun JumpToBottomButtonPreview() {

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {

        var enabled by remember {
            mutableStateOf(false)
        }

        Button(
            onClick = {
                enabled = enabled.not()
            }
        ) {
            Text("Enabled: $enabled")
        }

        Spacer(modifier = Modifier.height(16.dp))

        JumpToBottomButton(enabled = enabled) {}
    }
}

@Composable
internal fun JumpToBottomButton(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = enabled,
        enter = fadeIn(
            animationSpec = tween(
                delayMillis = 500,
                durationMillis = 1000
            )
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = 1000
            )
        )
    ) {
        FloatingActionButton(
            shape = CircleShape,
            containerColor = Color.White,
            modifier = Modifier
                .padding(16.dp)
                .size(48.dp),
            onClick = onClick
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = Color.Black
            )
        }
    }
}