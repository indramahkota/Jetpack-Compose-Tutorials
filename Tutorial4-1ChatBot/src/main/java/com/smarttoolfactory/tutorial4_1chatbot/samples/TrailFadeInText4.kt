@file:OptIn(ExperimentalMaterial3Api::class)

package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One-file, copy/paste implementation that combines:
 * 1) Fade-repair when existing tail characters MOVE (reflow/line-wrap changes)
 * 2) Reveal fade-in for NEWLY appended text (ChatGPT-like "new chunk is slightly faded then settles")
 *
 * Approach:
 * - We can’t style ranges inside a single Text() directly, so we use draw overlays:
 *   - Reveal overlay: draws a surface-colored gradient mask over the newly appended range and animates it to 0.
 *   - Repair overlay: draws a brief tinted overlay over moved characters and fades out quickly.
 */

// -------------------- Geometry capture & movement detection --------------------

@Stable
private data class CharGeom(val offset: Int, val rect: Rect)

private fun approxSame(a: Rect, b: Rect, eps: Float): Boolean {
    fun close(x: Float, y: Float) = abs(x - y) <= eps
    return close(a.left, b.left) && close(a.top, b.top) && close(
        a.right,
        b.right
    ) && close(a.bottom, b.bottom)
}

private fun captureTailGeoms(
    layout: TextLayoutResult,
    textLen: Int,
    tailChars: Int,
    useCursor: Boolean = false,
): List<CharGeom> {
    if (textLen <= 0) return emptyList()
    val start = (textLen - tailChars).coerceAtLeast(0)
    val end = (textLen - 1).coerceAtLeast(0)
    val out = ArrayList<CharGeom>(end - start + 1)
    for (i in start..end) {
        val r = if (useCursor) layout.getCursorRect(i) else layout.getBoundingBox(i)
        out.add(CharGeom(i, r))
    }
    return out
}

private fun detectMovedOffsets(
    before: List<CharGeom>,
    afterLayout: TextLayoutResult,
    epsPx: Float = 0.5f,
    useCursor: Boolean = false,
): IntArray {
    if (before.isEmpty()) return IntArray(0)
    val moved = IntArray(before.size)
    var count = 0
    val len = afterLayout.layoutInput.text.length
    for (g in before) {
        if (g.offset !in 0 until len) continue
        val newRect =
            if (useCursor) afterLayout.getCursorRect(g.offset) else afterLayout.getBoundingBox(g.offset)
        if (!approxSame(g.rect, newRect, epsPx)) moved[count++] = g.offset
    }
    return moved.copyOf(count)
}

// -------------------- Patch building (rect merging) --------------------

private data class Patch(val rect: Rect)

private fun buildPatches(
    layout: TextLayoutResult,
    offsets: IntArray,
    textLen: Int,
    yTolerancePx: Float = 2f,
    xGapTolerancePx: Float = 1.5f,
): List<Patch> {
    if (offsets.isEmpty()) return emptyList()

    val rects = ArrayList<Rect>(offsets.size)
    for (off in offsets) {
        if (off in 0 until textLen) rects.add(layout.getBoundingBox(off))
    }
    if (rects.isEmpty()) return emptyList()

    rects.sortWith(compareBy<Rect> { it.top }.thenBy { it.left })

    val rows = ArrayList<MutableList<Rect>>()
    var currentRow = mutableListOf<Rect>()
    var currentTop = rects.first().top
    for (r in rects) {
        if (abs(r.top - currentTop) <= yTolerancePx) {
            currentRow.add(r)
        } else {
            rows.add(currentRow)
            currentRow = mutableListOf(r)
            currentTop = r.top
        }
    }
    rows.add(currentRow)

    val patches = ArrayList<Patch>(rows.size)
    for (row in rows) {
        row.sortBy { it.left }
        var acc = row.first()
        for (i in 1 until row.size) {
            val r = row[i]
            val gap = r.left - acc.right
            if (gap <= xGapTolerancePx && abs(r.top - acc.top) <= yTolerancePx) {
                acc = Rect(
                    left = min(acc.left, r.left),
                    top = min(acc.top, r.top),
                    right = max(acc.right, r.right),
                    bottom = max(acc.bottom, r.bottom)
                )
            } else {
                patches.add(Patch(acc))
                acc = r
            }
        }
        patches.add(Patch(acc))
    }
    return patches
}

// -------------------- Offset helpers for append range --------------------

private fun buildAppendOffsets(
    oldLen: Int,
    newLen: Int,
    maxNewChars: Int
): IntArray {
    if (newLen <= oldLen) return IntArray(0)
    val start = oldLen.coerceAtLeast(0)
    val endExclusive = newLen.coerceAtLeast(0)

    val totalNew = endExclusive - start
    if (totalNew <= 0) return IntArray(0)

    // Cap work for performance (only last maxNewChars of the appended range)
    val cappedStart = max(start, endExclusive - maxNewChars)
    val size = endExclusive - cappedStart

    val out = IntArray(size)
    var idx = 0
    for (o in cappedStart until endExclusive) out[idx++] = o
    return out
}

// -------------------- Overlay drawing --------------------

private fun Modifier.repairOverlay(
    patches: List<Patch>,
    alpha: Float,
    color: Color,
) = drawWithContent {
    drawContent()
    if (alpha <= 0f || patches.isEmpty()) return@drawWithContent

    val c = color.copy(alpha = (0.28f * alpha).coerceIn(0f, 0.35f))
    for (p in patches) {
        drawRect(color = c, topLeft = p.rect.topLeft, size = p.rect.size)
        drawRect(
            topLeft = p.rect.topLeft,
            size = p.rect.size,
            color = Color.Green,
            style = Stroke(2.dp.toPx())
        )
    }
}

private fun Modifier.revealOverlay(
    patches: List<Patch>,
    alpha: Float,
    maskColor: Color,
) = drawWithContent {
    drawContent()
    if (alpha <= 0f || patches.isEmpty()) return@drawWithContent

    // “Reveal”: cover the new text with a vertical gradient mask, then fade the mask away.
    // Higher alpha => more hidden. Animate alpha to 0 => fully revealed.
    val bottomA = (0.72f * alpha).coerceIn(0f, 0.75f)
    val topA = (0.12f * alpha).coerceIn(0f, 0.20f)

    println("Alpha: $alpha, patches size: ${patches.size}")

    for (p in patches) {
        val topY = p.rect.top
        val bottomY = p.rect.bottom
        val brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to maskColor.copy(alpha = topA),      // slightly masked at top
                1.0f to maskColor.copy(alpha = bottomA)   // more masked at bottom
            ),
            startY = topY,
            endY = bottomY
        )
        drawRect(brush = brush, topLeft = p.rect.topLeft, size = p.rect.size)


//        drawRect(
//            topLeft = p.rect.topLeft,
//            size = p.rect.size,
//            color = maskColor.copy(alpha = alpha),
//        )

        drawRect(
            topLeft = p.rect.topLeft,
            size = p.rect.size,
            color = lerp(Color.Green, Color.Red, alpha),
            style = Stroke(2.dp.toPx())
        )
    }
}

// -------------------- Main composable: streaming + reveal + repair --------------------

@Composable
fun StreamingTextWithRevealAndFadeRepair(
    text: String,
    modifier: Modifier = Modifier,
    tailCharsToCheck: Int = 256,
    maxNewCharsToReveal: Int = 512,
    detectUsingCursorRect: Boolean = false,

    // Reveal mask color should match the surface behind the Text.
    revealMaskColor: Color = MaterialTheme.colorScheme.surface,

    // Repair tint color (debug-friendly; in prod you may want a subtle onSurface tint)
    repairTintColor: Color = Color.Red,
) {
    var lastTextLen by remember { mutableIntStateOf(0) }
    var beforeTail by remember { mutableStateOf<List<CharGeom>>(emptyList()) }

    var revealPatches by remember { mutableStateOf<List<Patch>>(emptyList()) }
    var repairPatches by remember { mutableStateOf<List<Patch>>(emptyList()) }

    val revealAlpha = remember { Animatable(0f) }
    val repairAlpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Text(
        text = text,
        fontSize = 18.sp,
        modifier = modifier
            // Draw reveal overlay first (mask over new text), then repair tint on top if needed.
            .revealOverlay(revealPatches, revealAlpha.value, revealMaskColor)
            .repairOverlay(repairPatches, repairAlpha.value, repairTintColor)
        ,
        onTextLayout = { layout ->
            val newLen = text.length
            val appendOnly = newLen >= lastTextLen

            // 1) Reveal fade-in for appended text
            if (appendOnly && newLen > lastTextLen) {
                val newOffsets: IntArray = buildAppendOffsets(
                    oldLen = lastTextLen,
                    newLen = newLen,
                    maxNewChars = maxNewCharsToReveal
                )
                val patches: List<Patch> = buildPatches(
                    layout = layout,
                    offsets = newOffsets,
                    textLen = newLen,
                    yTolerancePx = 2f,
                    xGapTolerancePx = 1.5f
                )
                revealPatches = patches

                scope.launch {
                    revealAlpha.stop()
                    revealAlpha.snapTo(1f)
                    revealAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 100)
                    )
                }
            }

            // 2) Fade-repair for moved existing chars (tail reflow)
            if (appendOnly && beforeTail.isNotEmpty()) {
                val moved: IntArray = detectMovedOffsets(
                    before = beforeTail,
                    afterLayout = layout,
                    epsPx = 0.5f,
                    useCursor = detectUsingCursorRect
                )
                if (moved.isNotEmpty()) {
                    val patches: List<Patch> = buildPatches(
                        layout = layout,
                        offsets = moved,
                        textLen = newLen,
                        yTolerancePx = 2f,
                        xGapTolerancePx = 1.5f
                    )
                    repairPatches = patches

                    scope.launch {
                        repairAlpha.stop()
                        repairAlpha.snapTo(1f)
                        repairAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 100)
                        )
                        repairPatches = emptyList()
                    }
                }
            }

            // Snapshot tail for next delta
            beforeTail = captureTailGeoms(
                layout = layout,
                textLen = newLen,
                tailChars = tailCharsToCheck,
                useCursor = detectUsingCursorRect
            )
            lastTextLen = newLen
        }
    )
}

// -------------------- Preview (simulated SSE streaming) --------------------

@Preview
@Composable
fun Preview_StreamingTextWithRevealAndFadeRepair() {

//            var text by remember {
//                mutableStateOf(
//                    "Streaming demo (reveal + fade-repair).\n" +
//                            "Newly appended text is masked then reveals.\n" +
//                            "If wrapping causes earlier tail characters to move, a brief repair tint appears.\n\n"
//                )
//            }
//
//            LaunchedEffect(Unit) {
//                val deltas = listOf(
//                    "Short delta. ",
//                    "Another short delta. ",
//                    "A much longer delta that will almost certainly wrap on this preview width and cause some previous tail characters to reflow. ",
//                    "More words to keep the stream going. ",
//                    "\nNew paragraph: ",
//                    "Final delta."
//                )
//                for (d in deltas) {
//                    delay(600)
//                    text += d
//                }
//            }

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Reveal + Fade-repair",
            style = MaterialTheme.typography.titleMedium
        )
        Divider()

        StreamingTextWithRevealAndFadeRepair(
            text = text,
            tailCharsToCheck = 320,
            maxNewCharsToReveal = 420,
            detectUsingCursorRect = false,
            // Must match Surface color behind the Text
            revealMaskColor = MaterialTheme.colorScheme.surface,
            repairTintColor = Color.Red, // set to a subtle tint in production
            modifier = Modifier.fillMaxWidth()
        )
    }
}
