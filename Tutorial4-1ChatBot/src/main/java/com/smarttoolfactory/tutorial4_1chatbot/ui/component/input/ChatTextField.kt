package com.smarttoolfactory.tutorial4_1chatbot.ui.component.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Preview
@Composable
fun ChatTextFieldPreview() {

    var text by remember {
        mutableStateOf("")
    }

    var enabled by remember {
        mutableStateOf(true)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {

        Button(
            onClick = {
                enabled = enabled.not()
            }
        ) {
            Text("Enabled: $enabled")
        }


        Spacer(modifier = Modifier.height(16.dp))

        ChatTextField(
            modifier = Modifier.fillMaxWidth(),
            value = text,
            enabled = enabled,
            onClick = {

            },
            onValueChange = {
                text = it
            }
        )
    }

}

@Composable
internal fun ChatTextField(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester = remember {
        FocusRequester()
    },
    onClick: () -> Unit,
    button: @Composable () -> Unit = {
        IconButton(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(40.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Red,
                disabledContainerColor = Color.Gray
            ),
            enabled = enabled,
            onClick = onClick
        ) {
            Icon(
                modifier = Modifier.rotate(-90f),
                tint = Color.White,
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null
            )
        }
    }
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color.Gray),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .padding(start = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    textStyle = TextStyle(fontSize = 18.sp),
                    enabled = enabled,
                    value = value,
                    onValueChange = onValueChange,
                    maxLines = 6,
                    cursorBrush = SolidColor(Color(0xff00897B)),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text("Message", fontSize = 18.sp)
                        }
                        innerTextField()
                    }
                )
            }

            button()
        }
    }
}
