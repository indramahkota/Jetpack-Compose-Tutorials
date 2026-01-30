package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.DiffRange
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.RectWithColor
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.computeDiffRange
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.computeDiffRects
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.randomColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Preview(showBackground = true)
@Composable
fun DiffRectComputePreview() {
    val deltas = remember {
        listOf(
            "defined ",
//            "by volatility, complexity", ", and accelerating\n",
//            "change. Markets evolve ", "faster than planning\n",
//            "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. ",
//            "cycles", "customer", " expectations shift", " continuously."
        )
    }

    val text = remember {
        deltas.joinToString()
    }

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

    var boundingRecStart by remember {
        mutableStateOf(Rect.Zero)
    }

    var boundingRectEnd by remember {
        mutableStateOf(Rect.Zero)
    }

    val boundingRectList = remember {
        mutableStateListOf<RectWithColor>()
    }

    LaunchedEffect(text, textLayout, startIndex, endIndex) {
        textLayout?.let { textLayout: TextLayoutResult ->
            if (text.isNotEmpty()) {
                boundingRecStart = textLayout.getBoundingBox(startIndex)
                boundingRectEnd = textLayout.getBoundingBox(endIndex)
                boundingRectList.clear()

                boundingRectList.addAll(
                    computeDiffRects(
                        layout = textLayout,
                        start = startIndex,
                        endExclusive = endIndex + 1
                    ).map {
                        RectWithColor(
                            rect = it,
                            color = randomColor()
                        )
                    }
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

//                    drawRect(
//                        color = Color.Red,
//                        topLeft = boundingRecStart.topLeft,
//                        size = boundingRecStart.size,
//                        style = Stroke(
//                            width = 3.dp.toPx(),
//                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
//                        )
//                    )
//                    drawRect(
//                        color = Color.Blue,
//                        topLeft = boundingRectEnd.topLeft,
//                        size = boundingRectEnd.size,
//                        style = Stroke(
//                            width = 3.dp.toPx(),
//                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
//                        )
//                    )
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

@Preview(showBackground = true)
@Composable
fun DiffRectRevealTextPreview() {
    val deltas = remember {
        listOf(
            "defined ",
            "by volatility, complexity", ", and accelerating\n",
            "change. Markets evolve ", "faster than planning\n",
            "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. ",
            "cycles", "customer", " expectations shift", " continuously."
        )
    }

    var text by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        delay(1000)
        deltas.forEach {
            println("Delta: $it")
            text += it
            delay(100)
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DiffRectRevealText(
            text = text,
            style = TextStyle(fontSize = 18.sp),
            sequential = true
        )
    }
}

@Composable
fun DiffRectRevealText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default.copy(fontSize = 18.sp),
    // Controls the reveal “soft edge” width (fraction of rect width)
    softnessFraction: Float = 0.22f,
    // Sequential animation per rect (ChatGPT-like)
    sequential: Boolean = true,
    durationMsPerRect: Int = 450,
    delayBetweenRectsMs: Int = 60,
) {
    var previousText by remember { mutableStateOf(text) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val diff: DiffRange? = remember(previousText, text) {
        computeDiffRange(previousText, text)
    }

    println("text: $text, diff: $diff")

    // Rects are computed after layout is available
    val diffRects = remember { mutableStateListOf<Rect>() }

    // One Animatable per rect, rebuilt when rect list changes
    val anims = remember(diffRects.size) { diffRects.map { Animatable(0f) } }

    // Kick animations when diff rects change
    LaunchedEffect(diffRects) {
        if (diffRects.isEmpty()) return@LaunchedEffect

        if (sequential) {
            for (i in anims.indices) {
                anims[i].snapTo(0f)
                anims[i].animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMsPerRect)
                )
                if (i != anims.lastIndex) delay(delayBetweenRectsMs.toLong())
            }
        } else {
            anims.forEach { it.snapTo(0f) }
            anims.forEach { anim ->
                launch {
                    anim.animateTo(1f, tween(durationMsPerRect))
                }
            }
        }
    }

    // Whenever text changes, we will compute rects on next layout pass,
    // then update previousText after we've computed.
    val pendingPreviousTextUpdate = remember(text) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                // Draw text first (destination)
                drawContent()

                val lr = layoutResult ?: return@drawWithContent
                if (diffRects.isEmpty() || anims.isEmpty()) return@drawWithContent

                // Keep all pixels by default (mask alpha = 1 everywhere)
                drawRect(
                    color = Color.Black,
                    blendMode = BlendMode.DstIn
                )

                // Apply per-rect reveal masks
                diffRects.forEachIndexed { index, rect ->
                    val alpha = anims.getOrNull(index)?.value ?: 1f
                    val width = rect.width.coerceAtLeast(1f)

                    println("index: $index, rect: $rect")

                    val softness = (width * softnessFraction).coerceIn(1f, width)
                    val revealX = rect.left + width * alpha
                    val softStartX = (revealX - softness).coerceAtLeast(rect.left)

                    // Mask rule (DstIn):
                    // - Left side alpha = 1 (visible)
                    // - Right side alpha = 0 (hidden)
                    // - Soft transition around reveal front
                    val brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black, // alpha 1
                            ((softStartX - rect.left) / width).coerceIn(0f, 1f) to Color.Black,
                            ((revealX - rect.left) / width).coerceIn(
                                0f,
                                1f
                            ) to Color.Transparent, // alpha 0
                            1f to Color.Transparent
                        ),
                        startX = rect.left,
                        endX = rect.right
                    )

//                drawRect(
//                    brush = brush,
//                    topLeft = Offset(rect.left, rect.top),
//                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
//                    blendMode = BlendMode.DstIn
//                )

                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
    ) {
        Text(
            text = text,
            style = style,
            onTextLayout = { textLayoutResult ->
                layoutResult = textLayoutResult

                // Compute rects once we have layout and a diff
                if (diff != null) {
                    diffRects.addAll(
                        computeDiffRects(
                            textLayoutResult,
                            diff.start,
                            diff.endExclusive
                        )
                    )
                    pendingPreviousTextUpdate.value = true
                } else {
//                    diffRects = emptyList()
                }

                // Update previousText AFTER we computed the rects for this change
                if (pendingPreviousTextUpdate.value) {
                    previousText = text
                    pendingPreviousTextUpdate.value = false
                }
            }
        )
    }
}
