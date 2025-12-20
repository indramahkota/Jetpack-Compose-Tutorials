package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.util.detectTapGesturesIf

@Preview
@Composable
fun GetTextBoundingRectPreview() {
    var boundingRect by remember {
        mutableStateOf(Rect.Zero)
    }

    val text =
        "Lorem Ipsum\n is simply **dummy** çöüğı<>₺ş- text of the printing and typesetting industry.\n" +
                "Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, " +
                "when an unknown printer took a galley of type and scrambled " +
                "it to make a type specimen book. It has survived not only five centuries, but " +
                "also the leap into electronic typesetting, remaining essentially unchanged. "

    var textLayout by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }

    var cursorRect by remember {
        mutableStateOf(Rect.Zero)
    }

    var startIndex by remember {
        mutableIntStateOf(0)
    }

    var indexString by remember {
        mutableStateOf(startIndex.toString())
    }

    var infoText by remember {
        mutableStateOf("")
    }

    LaunchedEffect(textLayout, startIndex) {
        textLayout?.let { textLayout: TextLayoutResult ->
            boundingRect = textLayout.getBoundingBox(startIndex)
            cursorRect = textLayout.getCursorRect(startIndex)
            infoText = getTextLayoutResultAsString(textLayout, startIndex, text)
        }
    }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(infoText)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Bounding Rect\n" +
                    "size: ${boundingRect.size}\n" +
                    "left: ${boundingRect.left} " +
                    "top: ${boundingRect.top}, " +
                    "right: ${boundingRect.right}, " +
                    "end: ${boundingRect.bottom}"
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Cursor Rect\n" +
                    "size: ${cursorRect.size}\n" +
                    "left: ${cursorRect.left} " +
                    "top: ${cursorRect.top}, " +
                    "right: ${cursorRect.right}, " +
                    "end: ${cursorRect.bottom}"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            modifier = Modifier.fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGesturesIf { offset ->
                        textLayout?.let { layoutResult ->
                            startIndex = layoutResult.getOffsetForPosition(offset)
                            indexString = startIndex.toString()
                        }
                    }
                }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        color = Color.Red,
                        topLeft = boundingRect.topLeft,
                        size = boundingRect.size,
                        style = Stroke(2.dp.toPx())
                    )
                    drawRect(
                        color = Color.Blue,
                        topLeft = cursorRect.topLeft,
                        size = cursorRect.size,
                        style = Stroke(
                            width = 3.dp.toPx(),
                        )
                    )
                },
            onTextLayout = { textLayoutResult: TextLayoutResult ->
                textLayout = textLayoutResult
            },
            fontSize = 20.sp,
            text = text
        )

        Row {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = indexString,
                onValueChange = {
                    indexString = it
                    indexString.toIntOrNull()?.let {
                        startIndex = it
                    }
                },
                label = {
                    Text("Start Index")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = {
                    startIndex++
                }
            ) {
                Text("+")
            }
            OutlinedButton(
                onClick = {
                    startIndex = (startIndex - 1).coerceAtLeast(0)
                }
            ) {
                Text("-")
            }
        }
    }
}
