package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview
@Composable
fun TextBoundsRect2Preview() {
    var boundingRecStart by remember {
        mutableStateOf(Rect.Zero)
    }

    var boundingRectEnd by remember {
        mutableStateOf(Rect.Zero)
    }

    val boundingRectList = remember {
        mutableStateListOf<RectWithColor>()
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

    var startIndex by remember {
        mutableIntStateOf(0)
    }

    var indexStringStart by remember {
        mutableStateOf(startIndex.toString())
    }

    var endIndex by remember {
        mutableIntStateOf(0)
    }

    var indexStringEnd by remember {
        mutableStateOf(startIndex.toString())
    }

    LaunchedEffect(text, textLayout, startIndex, endIndex) {
        textLayout?.let { textLayout: TextLayoutResult ->
            if (text.isNotEmpty()) {
                boundingRecStart = textLayout.getBoundingBox(startIndex)
                boundingRectEnd = textLayout.getBoundingBox(endIndex)
                boundingRectList.clear()

                boundingRectList.addAll(
                    calculateBoundingRectList(
                        textLayoutResult = textLayout,
                        startIndex = startIndex,
                        endIndex = endIndex
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier.padding(vertical = 16.dp)
    ) {

        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Start index: $startIndex\n" +
                    "Rect: " +
                    "size: ${boundingRecStart.size}\n" +
                    "left: ${boundingRecStart.left} " +
                    "top: ${boundingRecStart.top}, " +
                    "right: ${boundingRecStart.right}, " +
                    "end: ${boundingRecStart.bottom}"
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "End index: $endIndex\n" +
                    "Rect: " +
                    "size: ${boundingRectEnd.size}\n" +
                    "left: ${boundingRectEnd.left} " +
                    "top: ${boundingRectEnd.top}, " +
                    "right: ${boundingRectEnd.right}, " +
                    "end: ${boundingRectEnd.bottom}"
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = text,
            modifier = Modifier.fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            textLayout?.let { layoutResult ->
                                endIndex = layoutResult.getOffsetForPosition(offset)
                                endIndex = endIndex.coerceAtMost(text.lastIndex - 1)
                                indexStringEnd = endIndex.toString()
                            }
                        },
                        onLongPress = { offset ->
                            textLayout?.let { layoutResult ->
                                startIndex = layoutResult.getOffsetForPosition(offset)
                                indexStringStart = startIndex.toString()
                            }
                        }
                    )
                }
                .drawWithContent {
                    drawContent()
                    boundingRectList.forEach { rectWithColor ->

                        val rect = rectWithColor.rect
                        val color = rectWithColor.color
                        drawRect(
                            color = color,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }

                    drawRect(
                        color = Color.Red,
                        topLeft = boundingRecStart.topLeft,
                        size = boundingRecStart.size,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                        )
                    )
                    drawRect(
                        color = Color.Blue,
                        topLeft = boundingRectEnd.topLeft,
                        size = boundingRectEnd.size,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                        )
                    )
                },
            onTextLayout = { textLayoutResult: TextLayoutResult ->
                textLayout = textLayoutResult
            },
            fontSize = 18.sp
        )

        Row {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = indexStringStart,
                onValueChange = {
                    indexStringStart = it
                    indexStringStart.toIntOrNull()?.let {
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

        Row {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = indexStringEnd,
                onValueChange = {
                    indexStringEnd = it
                    indexStringEnd.toIntOrNull()?.let {
                        endIndex = it
                    }
                },
                label = {
                    Text("End Index")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Spacer(modifier = Modifier.height(32.dp))
            OutlinedButton(
                onClick = {
                    endIndex++
                }
            ) {
                Text("+")
            }
            OutlinedButton(
                onClick = {
                    endIndex = (endIndex - 1).coerceAtLeast(0)
                }
            ) {
                Text("-")
            }
        }
    }
}