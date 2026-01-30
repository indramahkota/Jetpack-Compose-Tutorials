package com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult

/**
 * Compute rects of a single or multi-line text
 * @param  layout result of layout of Text Composable or TextMeasurer
 * @param start start index of text.
 * @param endExclusive index before last index of text. If text's end index is 5, pass 6
 */
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
        val lineStart: Int = layout.getLineStart(line)
        val lineEndExclusive: Int = layout.getLineEnd(line, visibleEnd = true)

        val segStart: Int = maxOf(safeStart, lineStart)
        val segEnd: Int = minOf(safeEnd, lineEndExclusive - 1)
        if (segStart > segEnd) continue

        val rectStartIndex: Rect = layout.getBoundingBox(segStart)
        val rectEndIndex: Rect = layout.getBoundingBox(segEnd)

        val left: Float = minOf(rectStartIndex.left, rectEndIndex.left)
        val right: Float = maxOf(rectStartIndex.right, rectEndIndex.right)

        val top: Float = layout.getLineTop(line)
        val bottom: Float = layout.getLineBottom(line)

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
