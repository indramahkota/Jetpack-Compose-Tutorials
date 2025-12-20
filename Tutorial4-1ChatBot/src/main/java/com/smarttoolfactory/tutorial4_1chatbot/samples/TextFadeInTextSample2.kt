package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

// --------------------------------------------
// 1) Token delta source (SSE-like abstraction)
// --------------------------------------------

interface TokenDeltaSource {
    /** Emits token deltas in arrival order (e.g., SSE `response.output_text.delta`). */
    fun deltas(): Flow<String>
}

/** Fake SSE stream for demo/preview. Replace with your OkHttp-SSE -> Flow<String>. */
class FakeSseTokenSource(
    private val text: String,
    private val minDelayMs: Long = 12L,
    private val maxDelayMs: Long = 45L,
    private val chunkMin: Int = 1,
    private val chunkMax: Int = 6,
) : TokenDeltaSource {
    override fun deltas(): Flow<String> = flow {
        var i = 0
        while (i < text.length) {
            val chunkSize = (chunkMin..chunkMax).random()
            val next = min(text.length, i + chunkSize)
            emit(text.substring(i, next))
            i = next
            delay((minDelayMs..maxDelayMs).random())
        }
    }
}

// --------------------------------------------
// 2) ViewModel: frame-throttled text assembly
// --------------------------------------------

data class RevealDiff(
    val oldText: String,
    val newText: String,
    val newRange: IntRange, // [start..endInclusive], inclusive
)

class StreamingRevealViewModel(
    private val source: TokenDeltaSource = FakeSseTokenSource(
        text = buildString {
            append("Streaming reveal sample.\n\n")
            append("This demonstrates a ChatGPT-style effect: only the newly arrived ")
            append("text region receives a soft gradient fade that resolves to full opacity.\n\n")
            append("Key constraints:\n")
            append("- Stream token deltas\n")
            append("- Detect changed range\n")
            append("- Compute rects once per update\n")
            append("- Animate overlay, not per-glyph styling\n")
            append("- Fallback for large chunks\n")
        }
    )
) : ViewModel() {

    private val _diff = MutableStateFlow(
        RevealDiff(oldText = "", newText = "", newRange = 0..-1)
    )
    val diff: StateFlow<RevealDiff> = _diff.asStateFlow()

    private var job: Job? = null

    init {
        start()
    }

    fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            // Assemble text from deltas, but apply updates at most once per frame
            val textUpdates: Flow<String> = source.deltas()
                .frameThrottledAppend(initial = "")

            var prev = ""
            textUpdates.collect { current ->
                val range = computeNewRange(prev, current)
                _diff.value = RevealDiff(oldText = prev, newText = current, newRange = range)
                prev = current
            }
        }
    }
}

/**
 * Frame-throttled appender:
 * - Collects incoming deltas continuously
 * - Flushes accumulated deltas once per frame (or as close as possible)
 *
 * This avoids recomposition per token when tokens arrive faster than 60fps.
 */
private fun Flow<String>.frameThrottledAppend(
    initial: String,
): Flow<String> = channelFlow {
    val buffer = StringBuilder()
    var current = initial

    // Collector: keeps buffering incoming deltas
    val collector = launch(Dispatchers.Default) {
        collect { delta ->
            buffer.append(delta)
        }
    }

    // Flusher: emits at most once per frame
    val flusher = launch {
        while (isActive) {
            // roughly one frame (Compose will align this well in practice)
            // If you want stricter coupling, flush from Composable with withFrameNanos.
            delay(16L)

            if (buffer.isNotEmpty()) {
                val chunk = buffer.toString()
                buffer.clear()
                current += chunk
                send(current)
            }
        }
    }

    awaitClose {
        collector.cancel()
        flusher.cancel()
    }
}

// --------------------------------------------
// 3) Diff range: common prefix + common suffix
// --------------------------------------------

/**
 * Returns an IntRange representing newly added/changed region in `newText`
 * compared to `oldText`, using:
 * - longest common prefix
 * - longest common suffix (after removing prefix overlap)
 *
 * If no new region, returns 0..-1 (empty).
 */
private fun computeNewRange(oldText: String, newText: String): IntRange {
    if (oldText == newText) return 0..-1
    if (oldText.isEmpty()) return 0 until newText.length

    val minLen = min(oldText.length, newText.length)

    // Common prefix
    var prefix = 0
    while (prefix < minLen && oldText[prefix] == newText[prefix]) {
        prefix++
    }

    // Common suffix (avoid overlapping prefix)
    var suffix = 0
    while (
        suffix < (minLen - prefix) &&
        oldText[oldText.lastIndex - suffix] == newText[newText.lastIndex - suffix]
    ) {
        suffix++
    }

    val start = prefix
    val endExclusive = newText.length - suffix
    if (start >= endExclusive) return 0..-1
    return start until endExclusive
}

// --------------------------------------------
// 4) Rect computation and fallback strategy
// --------------------------------------------

private data class RevealGeometry(
    val rects: List<RectWithColor>,
    val isFallback: Boolean
)

/**
 * Convert a newRange into draw rects.
 *
 * Strategy:
 * - Small change: per-char bounding boxes -> union per line
 * - Large change: line rects (fast) to avoid getBoundingBox loops
 */
private fun computeRevealGeometry(
    layout: TextLayoutResult,
    newRange: IntRange,
    maxPerGlyphChars: Int = 180,
    maxPerGlyphLines: Int = 6
): RevealGeometry {
    if (newRange.isEmpty() || newRange.first < 0) {
        return RevealGeometry(emptyList(), isFallback = false)
    }

    val safeStart = newRange.first.coerceIn(0, layout.layoutInput.text.length)
    val safeEndExclusive = (newRange.last + 1).coerceIn(0, layout.layoutInput.text.length)
    if (safeStart >= safeEndExclusive) {
        return RevealGeometry(emptyList(), isFallback = false)
    }

    val startLine = layout.getLineForOffset(safeStart)
    val endLine = layout.getLineForOffset(safeEndExclusive - 1)
    val lineCount = (endLine - startLine + 1)
    val charCount = (safeEndExclusive - safeStart)

    val useFallback = charCount > maxPerGlyphChars || lineCount > maxPerGlyphLines
    return if (useFallback) {
        RevealGeometry(
            rects = buildLineRects(layout, startLine, endLine).map {
                RectWithColor(
                    rect = it,
                    color = Color.Red
                )
            },
            isFallback = true
        )
    } else {
        RevealGeometry(
            rects = buildPerLineUnionBoundingRects(layout, safeStart, safeEndExclusive).map {
                RectWithColor(
                    rect = it,
                    color = randomColor()
                )
            },
            isFallback = false
        )
    }
}

private fun IntRange.isEmpty(): Boolean = first > last

// --------------------------------------------
// 5) Composable: cached geometry + drawWithCache overlay
// --------------------------------------------

@Composable
fun StreamingRevealMessage(
    diff: RevealDiff,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle.Default,
    overlayColor: Color = Color.Black, // “dim mask” that fades out
    overlayMaxAlpha: Float = 0.55f,
    animationMs: Int = 220
) {
    // Animation progress for the overlay (1 -> 0 alpha feel)
    val progress = remember { Animatable(1f) }

    // Layout + geometry state (recomputed only when text changes)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var geometry by remember { mutableStateOf(RevealGeometry(emptyList(), isFallback = false)) }

    val rectList = remember {
        mutableStateListOf<RectWithColor>()
    }

    // Whenever the text changes, reset animation and recompute geometry once layout is available.
    LaunchedEffect(diff.newText) {
        // Start “more masked” then fade mask away
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = animationMs)
        )
    }

    BasicText(
        text = diff.newText,
        style = textStyle.copy(fontSize = 24.sp),
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val rects = geometry.rects
                val p = progress.value.coerceIn(0f, 1f)
                val a = (overlayMaxAlpha * p).coerceIn(0f, 1f)

                // A gentle vertical gradient inside each rect. The mask fades out as progress -> 0.
                // We compute the brush lazily per draw pass; cost is dominated by rect count.
                val makeBrush: (Rect) -> Brush = { r ->
                    Brush.verticalGradient(
                        colors = listOf(
                            overlayColor.copy(alpha = a),
                            overlayColor.copy(alpha = a * 0.45f),
                            overlayColor.copy(alpha = 0f)
                        ),
                        startY = r.top,
                        endY = r.bottom
                    )
                }

                onDrawWithContent {
                    drawContent()
                    if (rects.isNotEmpty() && a > 0.001f) {
                        // Draw the mask only over the newly added region.
                        for (r in rects) {
                            val rect = r.rect
                            drawRect(
                                brush = makeBrush(rect),
                                topLeft = Offset(rect.left, rect.top),
                                size = Size(rect.width, rect.height)
                            )
                        }
                    }

                    rectList.forEach { (rect, color) ->
                        drawRect(
                            color = color,
                            topLeft = Offset(rect.left, rect.top),
                            size = Size(rect.width, rect.height),
                            style = Stroke(2.dp.toPx())
                        )
                    }
                }
            },
        onTextLayout = { tlr ->
            layout = tlr
            // Only recompute geometry when we have layout AND the diff range is relevant.
            geometry = computeRevealGeometry(
                layout = tlr,
                newRange = diff.newRange
            )

            rectList.addAll(geometry.rects)
        },
    )
}

// --------------------------------------------
// 6) Screen usage: ViewModel + message composable
// --------------------------------------------

@Composable
fun StreamingRevealScreen(vm: StreamingRevealViewModel = StreamingRevealViewModel()) {
    val diff by vm.diff.collectAsState()

    val density = LocalDensity.current
    Column(Modifier.padding(16.dp)) {
        StreamingRevealMessage(
            diff = diff,
            textStyle = TextStyle.Default,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Optional: tiny debug info (helps validate fallback behavior)
        val layoutPaddingPx = with(density) { 8.dp.toPx() }
        BasicText(
            text = "NewRange=${diff.newRange.first}..${diff.newRange.last}  (len=${diff.newText.length})",
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 420)
@Composable
private fun PreviewStreamingRevealScreen() {
    StreamingRevealScreen(
        vm = StreamingRevealViewModel(
            source = FakeSseTokenSource(
                text = "Preview: streaming text reveals with a soft gradient fade over the new region.\n" +
                        "This is a composable-only demo; swap the source with OkHttp-SSE.\n\n" +
                        "Line wrapping is supported, and large chunks fall back to line-rect masking."
            )
        )
    )
}