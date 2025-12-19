package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.util.detectTapGesturesIf
import kotlinx.coroutines.delay
import kotlin.random.Random


@Preview
@Composable
fun GetTextBoundingRectPreview() {
    var boundingRect by remember {
        mutableStateOf(Rect.Zero)
    }

    val text =
        "Lorem Ipsum\n is simply **dummy** çöüğı<>₺ş- text of the printing and typesetting industry.\n" +
                " Lorem Ipsum has been the industry's standard dummy text ever since the 1500s," +
                " when an unknown printer took a galley of type and scrambled " +
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

    var infoText by remember {
        mutableStateOf("")
    }

    LaunchedEffect(textLayout, startIndex) {
        textLayout?.let { textLayout: TextLayoutResult ->
            boundingRect = textLayout.getBoundingBox(startIndex)
            cursorRect = textLayout.getCursorRect(startIndex)

            val lineIndex = textLayout.getLineForOffset(startIndex)
            val lineStart = textLayout.getLineStart(lineIndex)
            val lineLeft = textLayout.getLineLeft(lineIndex)
            val lineEnd = textLayout.getLineStart(lineIndex)
            val lineRight = textLayout.getLineLeft(lineIndex)
            val lineTop = textLayout.getLineTop(lineIndex)
            val lineBottom = textLayout.getLineBottom(lineIndex)
            val lineBaseline = textLayout.getLineBaseline(lineIndex)
            val horizontalPosition =
                textLayout.getHorizontalPosition(offset = startIndex, usePrimaryDirection = false)

            infoText = """
                lineStart: $lineStart
                lineLeft: $lineLeft
                lineEnd: $lineEnd
                lineRight: $lineRight
                lineTop: $lineTop
                lineBottom: $lineBottom
                lineBaseline: $lineBaseline
                horizontalPosition: $horizontalPosition
            """.trimIndent()
        }
    }

    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(infoText)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Char: ${text[startIndex]} -> " +
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
                value = startIndex.toString(),
                onValueChange = {
                    it.toIntOrNull()?.let {
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

@Preview
@Composable
fun TexChunkDetectTextPreview() {
    var text by remember {
        mutableStateOf("")
    }

    var chunkText by remember {
        mutableStateOf("")
    }

    val deltas = remember {
        listOf(
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. ",
            "defined ", "by volatility, complexity", ", and accelerating\n",
            "change. Markets evolve ", "faster than planning\n",
            "cycles", "customer", " expectations shift", " continuously."
        )
    }

    LaunchedEffect(Unit) {
        delay(1000)
        deltas.forEach {
            println("Delta: $it")
            chunkText += it
            delay(1000)
        }
    }

    Column {
        TexChunkDetectText(chunkText)
        Spacer(modifier = Modifier.weight(1f))

        OutlinedTextField(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth(),
            value = text,
            onValueChange = {
                text = it
            }
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                chunkText += text
            }
        ) {
            Text("Append")
        }
    }
}


@Composable
fun TexChunkDetectText(text: String) {

    val rectList = remember {
        mutableStateListOf<Rect>()
    }

    var startIndex by remember {
        mutableIntStateOf(0)
    }

    Text(
        modifier = Modifier
            .border(2.dp, Color.Yellow)
            .fillMaxWidth().drawWithContent {
                rectList.forEach { rect ->
                    drawRect(
                        randomColor(),
                        topLeft = rect.topLeft,
                        size = rect.size
                    )
                }
                drawContent()

            },
        onTextLayout = { textLayout: TextLayoutResult ->
            if (text.isNotEmpty()) {

                val endIndex = if (text[text.lastIndex] == '\n') {
                    text.lastIndex - 1
                } else {
                    text.lastIndex
                }

                println("Text in textLayout: $text")
                // Get the line of the first and last character of the word
                val startLine = textLayout.getLineForOffset(startIndex)
                val endLine = textLayout.getLineForOffset(endIndex)
//
                println(
                    "startIndex:$startIndex, " +
                            "endIndex: $endIndex, " +
                            "startLine: $startLine, " +
                            "endLine: $endLine, " +
                            "Same line: ${startLine == endLine}"
                )

                val sameLine = startLine == endLine

                for (line in startLine..endLine) {

                }

                if (sameLine) {
                    val rect = getTextBoundingRect(textLayout, startIndex, endIndex, startLine)

                    rectList.add(rect)
                } else {

                    val startRect: Rect = textLayout.getCursorRect(startIndex)
                    val startLineEnd = textLayout.getLineRight(startLine)

                    var rect = Rect(
                        topLeft = startRect.topLeft,
                        bottomRight = Offset(startLineEnd, startRect.bottom)
                    )
                    rectList.add(rect)
                    println("ADDING in same line FIRST rect: $rect")

                    val left = textLayout.getBoundingBox(endIndex).left
                    val right = textLayout.getBoundingBox(endIndex).right
                    val top = textLayout.getLineTop(endLine)
                    val bottom = textLayout.getLineBottom(endLine)

                    rect = Rect(
                        topLeft = Offset(0f, top),
                        bottomRight = Offset(left.coerceAtLeast(right), bottom)
                    )

                    rectList.add(rect)
                    println("ADDING in same line Second rect: $rect")
                }

                startIndex = endIndex + 1
            }
        },
        text = text,
        fontSize = 14.sp
    )
}

private fun getTextBoundingRect(
    textLayout: TextLayoutResult,
    startIndex: Int,
    endIndex: Int,
    line: Int
): Rect {
    val left = textLayout.getBoundingBox(startIndex).left
    val right = textLayout.getBoundingBox(endIndex).right
    val top = textLayout.getLineTop(line)
    val bottom = textLayout.getLineBottom(line)

    val rect = Rect(
        topLeft = Offset(left, top),
        bottomRight = Offset(right, bottom)
    )
    return rect
}

@Preview
@Composable
fun TrailFadeInTextPreview() {
    var text by remember {
        mutableStateOf("")
    }

    var chunkText by remember {
        mutableStateOf("")
    }

    val deltas = remember {
        listOf(
            "defined ", "by volatility, complexity", ", and accelerating\n",
            "change. Markets evolve ", "faster than planning\n",
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. ",
            "cycles", "customer", " expectations shift", " continuously."
        )
    }

    LaunchedEffect(Unit) {
        delay(1000)
        deltas.forEach {
            println("Delta: $it")
            chunkText += it
            delay(100)
        }
    }

    Column {
        TrailFadeInText(chunkText)
        Spacer(modifier = Modifier.weight(1f))

        OutlinedTextField(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth(),
            value = text,
            onValueChange = {
                text = it
            }
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                chunkText += text
            }
        ) {
            Text("Append")
        }
    }
}

data class RectWithAlpha(
    val rect: Rect,
    val alpha: MutableState<Float> = mutableStateOf(0f)
)

@Composable
fun TrailFadeInText(text: String) {
    val rectMap = remember {
        mutableStateListOf<RectWithAlpha>()
    }

    var startIndex by remember {
        mutableIntStateOf(0)
    }

    var tick by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(tick) {

        var indexToRemove = -1
        rectMap.forEachIndexed { index, rect ->
            val alpha = (rect.alpha.value + .1f).coerceIn(0f, 1f)
            if (alpha >= 1f) {
                indexToRemove = index
            } else {
                rect.alpha.value = alpha
            }
        }

//        rectMap.remove(indexToRemove)
        delay(16)
        tick = !tick
    }

    Text(
        modifier = Modifier
            .border(2.dp, Color.Yellow)
            .fillMaxWidth()
            .graphicsLayer()
            .drawWithContent {
                drawContent()

                val size = rectMap.size

                rectMap.forEachIndexed { index, rectWithAlpha: RectWithAlpha ->

                    val alpha = 1 - rectWithAlpha.alpha.value

                    if (alpha > 0f) {
                        drawRect(
                            Color.White,
                            topLeft = rectWithAlpha.rect.topLeft,
                            size = rectWithAlpha.rect.size,
                            alpha = alpha
                        )

                    }
                }

            },
        onTextLayout = { textLayout: TextLayoutResult ->
            if (text.isNotEmpty()) {

                val endIndex = if (text[text.lastIndex] == '\n') {
                    text.lastIndex - 1
                } else {
                    text.lastIndex
                }

                // Get the line of the first and last character of the word
                val startLine = textLayout.getLineForOffset(startIndex)
                val endLine = textLayout.getLineForOffset(endIndex)
                val sameLine = startLine == endLine

                println("TEXT: $text")

                if (sameLine) {
                    println("Adding in same line index: $startIndex")

                    // Get top-left position of the word
                    val startOffset: Offset = textLayout.getCursorRect(startIndex).topLeft

                    // Get bottom-right position
                    val endOffset: Offset = textLayout.getBoundingBox(endIndex).bottomRight

                    val rect = Rect(
                        topLeft = startOffset,
                        bottomRight = endOffset
                    )

                    rectMap.add(RectWithAlpha(rect = rect))
                } else {
                    println("Next line required... startLine: $startLine, endLine: $endLine")

                    val startRect: Rect = textLayout.getCursorRect(startIndex)
                    val startLineEnd = textLayout.getLineRight(startLine)

                    var rect = Rect(
                        topLeft = startRect.topLeft,
                        bottomRight = Offset(startLineEnd, startRect.bottom)
                    )
                    rectMap.add(RectWithAlpha(rect = rect))

                    val left = textLayout.getBoundingBox(endIndex).left
                    val right = textLayout.getBoundingBox(endIndex).right
                    val top = textLayout.getLineTop(endLine)
                    val bottom = textLayout.getLineBottom(endLine)

                    rect = Rect(
                        topLeft = Offset(0f, top),
                        bottomRight = Offset(left.coerceAtLeast(right), bottom)
                    )
                    rectMap.add(RectWithAlpha(rect = rect))
                }

                startIndex = endIndex + 1
            }
        },
        text = text,
        fontSize = 18.sp
    )
}