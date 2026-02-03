package com.smarttoolfactory.tutorial4_1chatbot.samples

import android.R.id.message
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextThemeProvider
import com.smarttoolfactory.tutorial4_1chatbot.markdown.MarkdownComposer
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.calculateBoundingRectList
import com.smarttoolfactory.tutorial4_1chatbot.ui.component.MarkDownStyle
import kotlinx.coroutines.delay

@Preview
@Composable
fun TrailFadeInText1Preview() {
    var text by remember { mutableStateOf("") }
    var chunkText by remember { mutableStateOf("") }

    val deltas = remember {
        listOf(
            "defined ", "by volatility, complexity", ", and acce", "lerating\n" ,
                    " hello world " ,
                    " something hello"
        )
    }

    LaunchedEffect(Unit) {
        delay(16)
        deltas.forEach {
            chunkText += it
            delay(700)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {

        RichTextThemeProvider(
            // Overrides every other style in BasicRichText
            textStyleProvider = {
                TextStyle.Default.copy(
                    fontSize = 18.sp,
                    lineHeight = 22.sp
                )
            }
        ) {
            BasicRichText(
                modifier = Modifier,
                style = MarkDownStyle.DefaultTextStyle
            ) {
                MarkdownComposer(
                    markdown = chunkText,
                    debug = true,
                    animate = true,
                )
            }

        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedTextField(
            modifier = Modifier
                .imePadding()
                .fillMaxWidth(),
            value = text,
            onValueChange = { text = it }
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { chunkText += text }
        ) {
            Text("Append")
        }
    }
}

@Composable
private fun TrailFadeInText(
    modifier: Modifier = Modifier,
    text: String,
    style: TextStyle = TextStyle.Default.copy(fontSize = 18.sp)
) {
    // Tracks "what we consider already processed"
    var committedStartIndex by remember { mutableIntStateOf(0) }

    // Tracks previous full text length, to detect append + compute appendStart
    var lastTextLen by remember { mutableIntStateOf(0) }

    val rectList = remember { mutableStateListOf<Rect>() }

    Text(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer()
            .drawWithContent {
                drawContent()
                rectList.forEach { r ->
                    drawRect(
                        color = Color.Red,
                        topLeft = r.topLeft,
                        size = r.size,
                        style = Stroke(2.dp.toPx())
                    )
                }
            },
        text = text,
        style = style,
        onTextLayout = { layout: TextLayoutResult ->
            if (text.isEmpty()) {
                rectList.clear()
                committedStartIndex = 0
                lastTextLen = 0
                return@Text
            }

            val textLen = text.length
            val endIndex = textLen - 1

            // ✅ Append start is the previous length (where new chars begin)
            // (If text shrank, treat as full recompute)
            val appendStart = if (textLen >= lastTextLen) lastTextLen else 0

            // ✅ Reflow anchor: character right before appended text
            // If appendStart == 0, anchor is invalid => recompute from 0
            val reflowStart = if (appendStart > 0) {
                val anchor = (appendStart - 1).coerceIn(0, endIndex)
                val anchorLine = layout.getLineForOffset(anchor)
                layout.getLineStart(anchorLine) // start of the line that will reflow
            } else {
                0
            }

            // We must include moved text, so compute from reflowStart
            val computeStart = reflowStart.coerceIn(0, endIndex)

            // Debug: replace instead of accumulating duplicates
            rectList.clear()

            val newRects = calculateBoundingRectList(
                textLayoutResult = layout,
                startIndex = computeStart,
                endIndex = endIndex
            )

            rectList.addAll(newRects)

            // Mark progress as committed; next time appendStart will be lastTextLen
            committedStartIndex = endIndex + 1
            lastTextLen = textLen
        }
    )
}
