package com.smarttoolfactory.tutorial4_1chatbot

import android.R.attr.startOffset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

    val deltas = remember {
        listOf(
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

                val endIndex = if (text[text.lastIndex] == '\n'){
                    text.lastIndex -1
                }else {
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

                if (sameLine) {

                    val left = textLayout.getBoundingBox(startIndex).left
                    val right = textLayout.getBoundingBox(endIndex).right
                    val top = textLayout.getLineTop(startLine)
                    val bottom = textLayout.getLineBottom(startLine)

                    val rect = Rect(
                        topLeft = Offset(left, top),
                        bottomRight = Offset(right, bottom)
                    )
                    println("left: $left, right: $right, top:$top, bottom:$bottom, Adding in same line $rect")

                    rectList.add(rect)
                } else {
                    println("Next line required... startLine: $startLine, endLine: $endLine")

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

                val endIndex = if (text[text.lastIndex] == '\n'){
                    text.lastIndex -1
                }else {
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