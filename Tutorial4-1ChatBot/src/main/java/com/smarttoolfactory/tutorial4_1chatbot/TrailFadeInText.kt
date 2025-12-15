package com.smarttoolfactory.tutorial4_1chatbot

import android.service.autofill.Validators.and
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random


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

                val endIndex = text.lastIndex

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

                if (sameLine) {
                    println("Adding in same line")

                    // Get top-left position of the word
                    val startOffset: Offset = textLayout.getCursorRect(startIndex).topLeft

                    // Get bottom-right position
                    val endOffset: Offset = textLayout.getBoundingBox(endIndex).bottomRight

                    val rect = Rect(
                        topLeft = startOffset,
                        bottomRight = endOffset
                    )

                    rectList.add(rect)
                } else {
                    println("Next line required... startLine: $startLine, endLine: $endLine")

                    val startRect: Rect = textLayout.getCursorRect(startIndex)
                    val startLineEnd = textLayout.getLineRight(startLine)
                    val endRect: Rect = textLayout.getBoundingBox(endIndex)

                    var rect = Rect(
                        topLeft = startRect.topLeft,
                        bottomRight = Offset(startLineEnd, startRect.bottom)
                    )
                    rectList.add(rect)
                    println("Adding in same line first rect: $rect")

                    rect = Rect(
                        topLeft = Offset(0f, endRect.top),
                        bottomRight = endRect.bottomRight
                    )
                    rectList.add(rect)
                }

                startIndex = endIndex + 1
            }
        },
        text = text,
        fontSize = 26.sp
    )
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

    LaunchedEffect(Unit) {
        val deltas = listOf(
            "defined ", "by volatility, complexity", ", and accelerating\n",
            "change. Markets evolve", "faster than planning\n",
            "cycles", "customer", " expectations shift", " continuously."
        )

        delay(2000)
        deltas.forEach {
            chunkText += it
            delay(150)
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
        mutableStateMapOf<Int, RectWithAlpha>()
    }

    var startIndex by remember {
        mutableIntStateOf(0)
    }

    var tick by remember {
        mutableStateOf(false)
    }


    val mutex = remember {
        Mutex()
    }


    LaunchedEffect(tick) {
        delay(33)

        var indexToRemove = -1
        rectMap.forEach { (index, rect) ->
            val alpha = (rect.alpha.value + .04f).coerceIn(0f, 1f)
            if (alpha >= 1f) {
                indexToRemove = index
                println("Index to remove: $indexToRemove")
            } else {
                rect.alpha.value = alpha
            }
        }

        rectMap.remove(indexToRemove)

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

                println("Draw rect size: $size")

                rectMap.forEach { (index: Int, rectWithAlpha: RectWithAlpha) ->

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

                val endIndex = text.lastIndex

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

                if (sameLine) {
                    println("Adding in same line")

                    // Get top-left position of the word
                    val startOffset: Offset = textLayout.getCursorRect(startIndex).topLeft

                    // Get bottom-right position
                    val endOffset: Offset = textLayout.getBoundingBox(endIndex).bottomRight

                    val rect = Rect(
                        topLeft = startOffset,
                        bottomRight = endOffset
                    )

                    rectMap[startIndex] = RectWithAlpha(rect = rect)
                } else {
                    println("Next line required... startLine: $startLine, endLine: $endLine")

                    val startRect: Rect = textLayout.getCursorRect(startIndex)
                    val startLineEnd = textLayout.getLineRight(startLine)
                    val endRect: Rect = textLayout.getBoundingBox(endIndex)

                    var rect = Rect(
                        topLeft = startRect.topLeft,
                        bottomRight = Offset(startLineEnd, startRect.bottom)
                    )
                    rectMap[startIndex] = RectWithAlpha(rect = rect)

                    println("Adding in same line first rect: $rect")

                    rect = Rect(
                        topLeft = Offset(0f, endRect.top),
                        bottomRight = endRect.bottomRight
                    )
                    rectMap[endIndex] = RectWithAlpha(rect = rect)
                }

                startIndex = endIndex + 1
            }
        },
        text = text,
        fontSize = 14.sp
    )
}