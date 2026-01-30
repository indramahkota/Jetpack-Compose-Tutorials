package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.RectWithColor
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.calculateBoundingRecWithColorList
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.computeDiffRects
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.randomColor

@Preview
@Composable
fun TextBoundsRect3Preview() {
    var boundingRecStart by remember {
        mutableStateOf(Rect.Zero)
    }

    var boundingRectEnd by remember {
        mutableStateOf(Rect.Zero)
    }

    var cursorRectEnd by remember {
        mutableStateOf(Rect.Zero)
    }

    var horizontalPosForEnd by remember {
        mutableFloatStateOf(0f)
    }

    val boundingRectList = remember {
        mutableStateListOf<RectWithColor>()
    }

    val boundingRectList2 = remember {
        mutableStateListOf<RectWithColor>()
    }

    val deltas = remember {
        listOf(
            "defined ", "by volatility, complexity", ", and accelerating\n",
            "change. Markets evolve ", "faster than planning\n",
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. ",
            "cycles", "customer", " expectations shift", " continuously."
        )
    }

    var text by remember {
        mutableStateOf("")
    }

    var deltaIndex by remember {
        mutableIntStateOf(0)
    }

    var textLayout by remember {
        mutableStateOf<TextLayoutResult?>(null)
    }

    var startIndex by remember {
        mutableIntStateOf(0)
    }

    var endIndex by remember {
        mutableIntStateOf(0)
    }

    LaunchedEffect(text, textLayout, startIndex, endIndex) {
        textLayout?.let { textLayout: TextLayoutResult ->
            if (text.isNotEmpty()) {
                boundingRecStart = textLayout.getBoundingBox(startIndex)
                boundingRectEnd = textLayout.getBoundingBox(endIndex)
                cursorRectEnd = textLayout.getCursorRect(endIndex)

                horizontalPosForEnd = textLayout.getHorizontalPosition(
                    offset = endIndex,
                    usePrimaryDirection = true
                )

                val rectList1 = calculateBoundingRecWithColorList(
                    textLayoutResult = textLayout,
                    startIndex = startIndex,
                    endIndex = endIndex
                )
                boundingRectList.addAll(rectList1)

                val rectList2 = computeDiffRects(
                    layout = textLayout,
                    start = startIndex,
                    endExclusive = endIndex + 1
                ).map {
                    RectWithColor(
                        rect = it,
                        color = randomColor()
                    )
                }

                boundingRectList2.addAll(rectList2)
            }
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Start index: $startIndex, char: ${text.getOrNull(startIndex)}\n" +
                    "Rect: " +
                    "size: ${boundingRecStart.size}\n" +
                    "left: ${boundingRecStart.left} " +
                    "top: ${boundingRecStart.top}, " +
                    "right: ${boundingRecStart.right}, " +
                    "end: ${boundingRecStart.bottom}"
        )
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "End index: $endIndex, char: ${text.getOrNull(endIndex)}\n" +
                    "Rect: " +
                    "size: ${boundingRectEnd.size}\n" +
                    "left: ${boundingRectEnd.left} " +
                    "top: ${boundingRectEnd.top}, " +
                    "right: ${boundingRectEnd.right}, " +
                    "end: ${boundingRectEnd.bottom}"
        )


        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "horizontalPosForEnd: $horizontalPosForEnd\n" +
                    "End CursorRect: " +
                    "size: ${cursorRectEnd.size}\n" +
                    "left: ${cursorRectEnd.left} " +
                    "top: ${cursorRectEnd.top}, " +
                    "right: ${cursorRectEnd.right}, " +
                    "end: ${cursorRectEnd.bottom}"
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Calculate Bounding Rects",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Red
        )

        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            textLayout?.let { layoutResult ->
                                endIndex = layoutResult.getOffsetForPosition(offset)
                                endIndex = endIndex.coerceAtMost(text.lastIndex - 1)
                            }
                        },
                        onLongPress = { offset ->
                            textLayout?.let { layoutResult ->
                                startIndex = layoutResult.getOffsetForPosition(offset)
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
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
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
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Compute Diff Rects",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Red
        )
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    boundingRectList2.forEach { rectWithColor ->

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
                },

            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Spacer(Modifier.height(16.dp))

        Button(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            onClick = {
                text += deltas[deltaIndex]
                if (deltaIndex != 0) {
                    startIndex = endIndex + 1
                }
                deltaIndex++
                deltaIndex = deltaIndex.coerceAtMost(deltas.lastIndex)
                endIndex = text.lastIndex
            }
        ) {
            Text("Update text")
        }

        Spacer(Modifier.height(16.dp))

        Button(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            onClick = {
                cursorRectEnd
                text = ""
                deltaIndex = 0
                startIndex = 0
                endIndex = 0
                boundingRectList.clear()
                boundingRectList2.clear()
                boundingRecStart = Rect.Zero
                boundingRectEnd = Rect.Zero
                cursorRectEnd = Rect.Zero
                horizontalPosForEnd = 0f
            }
        ) {
            Text("Reset")
        }
    }
}