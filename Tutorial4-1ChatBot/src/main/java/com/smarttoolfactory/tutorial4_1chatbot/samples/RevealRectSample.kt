import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Preview(showBackground = true)
@Composable
fun DiffRectRevealTextPreview() {
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

    LaunchedEffect(Unit) {
        delay(1000)
        deltas.forEach {
            println("Delta: $it")
            text += it
            delay(100)
        }
    }


    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DiffRectRevealText(
            text = text,
            style = TextStyle(fontSize = 18.sp),
            sequential = true
        )
    }
}


fun computeDiffRects(
    layout: TextLayoutResult,
    start: Int,
    endExclusive: Int
): List<Rect> {
    if (start >= endExclusive) return emptyList()

    val safeStart = start.coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))
    val safeEnd =
        (endExclusive - 1).coerceIn(0, (layout.layoutInput.text.length - 1).coerceAtLeast(0))

    val startLine = layout.getLineForOffset(safeStart)
    val endLine = layout.getLineForOffset(safeEnd)

    val rects = ArrayList<Rect>(endLine - startLine + 1)

    for (line in startLine..endLine) {
        val lineStart = layout.getLineStart(line)
        val lineEndExclusive = layout.getLineEnd(line, visibleEnd = true)

        val segStart = maxOf(safeStart, lineStart)
        val segEnd = minOf(safeEnd, lineEndExclusive - 1)
        if (segStart > segEnd) continue

        val rectStartIndex = layout.getBoundingBox(segStart)
        val rectEndIndex = layout.getBoundingBox(segEnd)

        val left = minOf(rectStartIndex.left, rectEndIndex.left)
        val right = maxOf(rectStartIndex.right, rectEndIndex.right)

        val top = layout.getLineTop(line)
        val bottom = layout.getLineBottom(line)

        val r = Rect(left, top, right, bottom)
        if (r.width > 0f && r.height > 0f) rects.add(r)
    }

    return rects
}

data class DiffRange(val start: Int, val endExclusive: Int)

fun computeDiffRange(old: String, new: String): DiffRange? {
    if (old == new) return null
    val oldLen = old.length
    val newLen = new.length

    var prefix = 0
    val minLen = minOf(oldLen, newLen)
    while (prefix < minLen && old[prefix] == new[prefix]) prefix++

    var suffix = 0
    while (
        suffix < (minOf(oldLen - prefix, newLen - prefix)) &&
        old[oldLen - 1 - suffix] == new[newLen - 1 - suffix]
    ) suffix++

    val start = prefix
    val endExclusive = (newLen - suffix).coerceAtLeast(start)
    if (start == endExclusive) return null
    return DiffRange(start, endExclusive)
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

    val diff = remember(previousText, text) { computeDiffRange(previousText, text) }

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
        modifier = modifier.drawWithContent {
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
                    size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
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
