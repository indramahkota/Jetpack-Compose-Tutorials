package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Preview
@Composable
fun TrailFadeInText1Preview() {
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
//            println("Delta: $it")
            chunkText += it
            delay(1000)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)
    ) {
//        TrailFadeInText(text = chunkText)

        TrailFadeInText(text = "Hello\nWorld")

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
private fun TrailFadeInText(
    modifier: Modifier = Modifier,
    text: String,
    start: Int = 0,
    end: Int = text.lastIndex,
    style: TextStyle = TextStyle.Default.copy(fontSize = 18.sp)
) {

    var startIndex by remember {
        mutableIntStateOf(start)
    }

    var endIndex by remember {
        mutableIntStateOf(end)
    }

    val rectList = remember {
        mutableStateListOf<RectWithColor>()
    }

    Text(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer()
            .drawWithContent {
                drawContent()
                rectList.forEach { rectWithAnimation ->
                    drawRect(
                        color = rectWithAnimation.color,
                        topLeft = rectWithAnimation.rect.topLeft,
                        size = rectWithAnimation.rect.size,
                        style = Stroke(2.dp.toPx())
                    )
                }
            },
        onTextLayout = { textLayout: TextLayoutResult ->
            endIndex = text.lastIndex

            if (text.isNotEmpty()) {

                println("Text: $text, startIndex: $startIndex, endIndex: $endIndex")

                val newList: List<RectWithColor> = calculateBoundingRecWithColorList(
                    textLayoutResult = textLayout,
                    startIndex = startIndex,
                    endIndex = endIndex
                )

//                val newList: List<RectWithColor> = computeDiffRects(
//                    layout = textLayout,
//                    start = startIndex,
//                    endExclusive = endIndex + 1
//                ).map {
//                    RectWithColor(color = randomColor(), rect = it)
//                }

                rectList.addAll(newList)

                startIndex = endIndex + 1
            }
        },
        text = text,
        style = style
    )
}