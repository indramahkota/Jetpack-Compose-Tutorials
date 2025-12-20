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
import androidx.compose.ui.geometry.Offset
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
import com.halilibo.richtext.ui.util.detectTapGesturesIf
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal fun buildLineRects(
    layout: TextLayoutResult,
    startLine: Int,
    endLine: Int
): List<Rect> {
    val rects = ArrayList<Rect>(endLine - startLine + 1)
    for (line in startLine..endLine) {
        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)

        // These APIs exist on TextLayoutResult in Compose.
        val left = layout.getLineLeft(line)
        val right = layout.getLineRight(line)

        rects.add(Rect(left, top, right, bottom))
    }
    return rects
}

/**
 * Per-character bounding boxes are unioned per line to reduce draw ops and avoid
 * jagged “per-glyph” rectangles.
 */
internal fun buildPerLineUnionBoundingRects(
    layout: TextLayoutResult,
    startOffset: Int,
    endOffsetExclusive: Int
): List<Rect> {
    val byLine = LinkedHashMap<Int, Rect>() // lineIndex -> unionRect

    for (i in startOffset until endOffsetExclusive) {
        val line = layout.getLineForOffset(i)
        val box = layout.getBoundingBox(i)

        // Ignore empty/invalid boxes (can happen for some control chars)
        if (box.isEmpty) continue

        val existing = byLine[line]
        byLine[line] = if (existing == null) box else existing.unionWith(box)
    }

    return byLine.values.toList()
}

internal fun Rect.union(other: Rect): Rect {
    return Rect(
        left = min(this.left, other.left),
        top = min(this.top, other.top),
        right = max(this.right, other.right),
        bottom = max(this.bottom, other.bottom)
    )
}

internal fun Rect.unionWith(other: Rect): Rect {
    if (this.isEmpty) return other
    if (other.isEmpty) return this
    return Rect(
        left = min(this.left, other.left),
        top = min(this.top, other.top),
        right = max(this.right, other.right),
        bottom = max(this.bottom, other.bottom)
    )
}

data class RectWithColor(
    val rect: Rect,
    val color: Color
)

private fun calculateBoundingRectList(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int
): List<RectWithColor> {
    val startLine = textLayoutResult.getLineForOffset(startIndex)
    val endLine = textLayoutResult.getLineForOffset(endIndex)


    val rectList = mutableListOf<RectWithColor>()

    for (currentLine in startLine..endLine) {
        if (currentLine == startLine && startLine == endLine) {
            //  This line contains both start and end indices
            //  get bounding rects for start and end indices and create union of them
            val startRect = textLayoutResult.getBoundingBox(startIndex)
            val endRect = textLayoutResult.getBoundingBox(endIndex)
            val finalRect = startRect.union(endRect)

            rectList.add(
                RectWithColor(
                    rect = finalRect,
                    color = Color.Magenta
                )
            )
        } else if (currentLine == startLine) {
            // start index is in this line but end index is not in this line
            // get bounding rect of char at start index and char at end of the line

            val startRect = textLayoutResult.getBoundingBox(startIndex)

            // 🔥EndRect does not return correct values if line ends with no width char like \n
            // If \n is 11th character endRect becomes 12th instead of being last char of this line

            val lineEndX = textLayoutResult.getLineRight(currentLine)
            val lineEndY = textLayoutResult.getLineBottom(currentLine)

            val finalRect = Rect(
                topLeft = startRect.topLeft,
                bottomRight = Offset(lineEndX, lineEndY)
            )
            rectList.add(
                RectWithColor(
                    rect = finalRect,
                    color = Color.Blue
                )
            )
        } else if (currentLine == endLine) {
            // end index is in this line but start index was in one of the lines or line above
            // get start of the line and bounding rect of end index and union them
            val lineStartIndex = textLayoutResult.getLineStart(currentLine)
            val startRect = textLayoutResult.getBoundingBox(lineStartIndex)

            val endRect = textLayoutResult.getBoundingBox(endIndex)
            val finalRect = startRect.union(endRect)
            rectList.add(
                RectWithColor(
                    rect = finalRect,
                    color = Color.Cyan
                )
            )
        } else {
            // this is a intermediary line between the lines that start and end chars exist
            // get full line as rect or divide it to equal parts for better reveal effect

            val lineStartIndex = textLayoutResult.getLineStart(currentLine)
            val lineEndIndex = textLayoutResult.getLineEnd(currentLine)

            val startRect = textLayoutResult.getBoundingBox(lineStartIndex)
            val endRect = textLayoutResult.getBoundingBox(lineEndIndex)

            val lineStartX = textLayoutResult.getLineLeft(currentLine)
            val lineStartY = textLayoutResult.getLineTop(currentLine)
            val lineEndX = textLayoutResult.getLineRight(currentLine)
            val lineEndY = textLayoutResult.getLineBottom(currentLine)

            // 🔥 EndRect does not return correct value when previous line ends with \n
            val wrongRect = startRect.union(endRect)

            val finalRect = Rect(
                topLeft = Offset(lineStartX, lineStartY),
                bottomRight = Offset(lineEndX, lineEndY)
            )

            println("lineStartX: $lineStartX, lineStartY: $lineStartY, lineEndX: $lineEndX, lineEndY: $lineEndY")
            println(
                "startRect: $startRect\n" +
                        "endRect: $endRect\n" +
                        "finalRect: $finalRect"
            )

            rectList.add(
                RectWithColor(
                    rect = finalRect,
                    color = Color.DarkGray
                )
            )

            // For debugging
            rectList.add(
                RectWithColor(
                    rect = wrongRect,
                    color = Color.Red
                )
            )
        }
    }
    return rectList
}

internal fun getTextLayoutResultAsString(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    text: String
): String {

    // Which line this character with startIndex is in this text
    val lineIndex = textLayoutResult.getLineForOffset(startIndex)
    // Start index of this line
    val lineStart = textLayoutResult.getLineStart(lineIndex)
    // Start offset of this line in px
    val lineLeft = textLayoutResult.getLineLeft(lineIndex)
    // 🔥 End index of this line. If this line ends with empty char this index returns next index,
    // Which might be start index of next line
    val lineEnd = textLayoutResult.getLineEnd(lineIndex)
    // End offset of this line in px
    val lineRight = textLayoutResult.getLineRight(lineIndex)
    // Top of this line in px
    val lineTop = textLayoutResult.getLineTop(lineIndex)
    // Bottom of this line in px
    val lineBottom = textLayoutResult.getLineBottom(lineIndex)
    // Baseline position of this line in px
    val lineBaseline = textLayoutResult.getLineBaseline(lineIndex)
    // x value of start of the character at index in px(changes LtR and RtL layouts)
    val horizontalPosition =
        textLayoutResult.getHorizontalPosition(offset = startIndex, usePrimaryDirection = false)

    return """
                index: $startIndex
                Char: ${text[startIndex]}
                lineIndex: $lineIndex
                lineStart: ${lineStart}th index
                lineLeft: $lineLeft px
                lineEnd: ${lineEnd}th index
                lineRight: $lineRight px
                lineTop: $lineTop px
                lineBottom: $lineBottom px
                lineBaseline: $lineBaseline px
                horizontalPosition: $horizontalPosition
            """.trimIndent()
}

@Preview
@Composable
fun TextBoundsRectPreview() {
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

    LaunchedEffect(textLayout, startIndex, endIndex) {
        textLayout?.let { textLayout: TextLayoutResult ->
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

    Column(
        modifier = Modifier.padding(16.dp)
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
            modifier = Modifier.fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            textLayout?.let { layoutResult ->
                                endIndex = layoutResult.getOffsetForPosition(offset)
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
            fontSize = 24.sp,
            text = text
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

fun randomColor() = Color(
    Random.nextInt(256),
    Random.nextInt(256),
    Random.nextInt(256)
)