package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal fun calculateBoundingRectList(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int
): List<Rect> {

    val text = textLayoutResult.layoutInput.text
    if (text.isEmpty()) return emptyList()
    if (startIndex > endIndex) return emptyList()

    val lastIndex = (text.length - 1).coerceAtLeast(0)

    val safeStart = startIndex.coerceIn(0, lastIndex)
    val safeEnd = endIndex.coerceIn(0, lastIndex)

    val startLine = textLayoutResult.getLineForOffset(safeStart)
    val endLine = textLayoutResult.getLineForOffset(safeEnd)

    val rectList = mutableListOf<Rect>()

    for (currentLine in startLine..endLine) {
        val rect = getBoundingRectForLine(
            textLayoutResult = textLayoutResult,
            startIndex = safeStart,
            endIndex = safeEnd,
            startLine = startLine,
            endLine = endLine,
            currentLine = currentLine
        )

        if (rect.width > 0f && rect.height > 0f) {
            rectList.add(rect)
        }
    }
    return rectList
}

private fun lastVisibleOffsetOnLine(
    layout: TextLayoutResult,
    line: Int
): Int {
    val visibleEndExclusive = layout.getLineEnd(line, visibleEnd = true)
    val lineStart = layout.getLineStart(line)
    // last visible *character* offset on that line
    return (visibleEndExclusive - 1).coerceAtLeast(lineStart)
}

private fun safeBoxOrCursor(layout: TextLayoutResult, offset: Int): Rect {
    val boundingRect = layout.getBoundingBox(offset)
    return if (boundingRect.width <= 0f) layout.getCursorRect(offset) else boundingRect
}

private fun getBoundingRectForLine(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int,
    currentLine: Int,
    startLine: Int,
    endLine: Int
): Rect {

    val lineTop: Float = textLayoutResult.getLineTop(currentLine)
    val lineBottom: Float = textLayoutResult.getLineBottom(currentLine)
    val lineLeft: Float = textLayoutResult.getLineLeft(currentLine)

    // Clamp endIndex to the last visible glyph on this line.
    // This fixes "\n" and trailing spaces on that line.
    val endOnThisLine = minOf(endIndex, lastVisibleOffsetOnLine(textLayoutResult, currentLine))

    return when {
        currentLine == startLine && startLine == endLine -> {
            // Single line range: [startIndex..endOnThisLine]
            val startRect: Rect = safeBoxOrCursor(textLayoutResult, startIndex)
            val endRect: Rect = safeBoxOrCursor(textLayoutResult, endOnThisLine)

            // Use union, but keep vertical extent aligned to the line (more stable)
            val unionRect: Rect = startRect.union(endRect)
            Rect(
                topLeft = Offset(unionRect.left, lineTop),
                bottomRight = Offset(unionRect.right, lineBottom)
            )
        }

        currentLine == startLine -> {
            // Multi-line: first line from startIndex to visual end of start line
            val startRect: Rect = safeBoxOrCursor(textLayoutResult, startIndex)
            val endRect: Rect = safeBoxOrCursor(textLayoutResult, lastVisibleOffsetOnLine(textLayoutResult, currentLine))

            Rect(
                topLeft = Offset(startRect.left, lineTop),
                bottomRight = Offset(endRect.right, lineBottom)
            )
        }

        currentLine == endLine -> {
            // Multi-line: last line from line start to endOnThisLine
            val endRect: Rect = safeBoxOrCursor(textLayoutResult, endOnThisLine)

            Rect(
                topLeft = Offset(lineLeft, lineTop),
                bottomRight = Offset(endRect.right, lineBottom)
            )
        }

        else -> {
            // Middle lines: full visible line rect (left -> right of last visible glyph)
            val endRect = safeBoxOrCursor(textLayoutResult, lastVisibleOffsetOnLine(textLayoutResult, currentLine))

            Rect(
                topLeft = Offset(lineLeft, lineTop),
                bottomRight = Offset(endRect.right, lineBottom)
            )
        }
    }
}

internal fun calculateBoundingRects(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int
): List<Rect> {

    if (startIndex > endIndex) return emptyList()

    val safeStart =
        startIndex.coerceIn(0, (textLayoutResult.layoutInput.text.length - 1).coerceAtLeast(0))
    val safeEnd =
        (endIndex).coerceIn(0, (textLayoutResult.layoutInput.text.length - 1).coerceAtLeast(0))

    val startLine = textLayoutResult.getLineForOffset(safeStart)
    val endLine = textLayoutResult.getLineForOffset(safeEnd)

    val rectList = mutableListOf<Rect>()

    for (currentLine in startLine..endLine) {
        val rect = getBoundingRectForCurrentLine(
            textLayoutResult = textLayoutResult,
            startIndex = safeStart,
            endIndex = safeEnd,
            startLine = startLine,
            endLine = endLine,
            currentLine = currentLine
        )

        if (rect.width > 0 && rect.height > 0) {
            rectList.add(rect)
        }
    }
    return rectList
}

private fun getBoundingRectForCurrentLine(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int,
    currentLine: Int,
    startLine: Int,
    endLine: Int
): Rect {
    return if (currentLine == startLine && startLine == endLine) {
        //  This line contains both start and end indices
        //  get bounding rects for start and end indices and create union of them
        val startRect: Rect = textLayoutResult.getBoundingBox(startIndex)
        var endRect: Rect = textLayoutResult.getBoundingBox(endIndex)
        if (endRect.width <= 0) {
            endRect = textLayoutResult.getCursorRect(endIndex)
        }
        startRect.union(endRect)

    } else if (currentLine == startLine) {
        // start index is in this line but end index is not in this line
        // get bounding rect of char at start index and char at end of the line

        val startRect: Rect = textLayoutResult.getBoundingBox(startIndex)

        // 🔥EndRect does not return correct values if line ends with no width char like \n
        // If \n is 11th character endRect becomes 12th instead of being last char of this line

        val lineEndX: Float = textLayoutResult.getLineRight(currentLine)
        val lineEndY: Float = textLayoutResult.getLineBottom(currentLine)

        Rect(
            topLeft = startRect.topLeft,
            bottomRight = Offset(lineEndX, lineEndY)
        )

    } else if (currentLine == endLine) {
        // end index is in this line but start index was in one of the lines or line above
        // get start of the line and bounding rect of end index and union them
        val lineStartIndex: Int = textLayoutResult.getLineStart(currentLine)
        val startRect: Rect = textLayoutResult.getBoundingBox(lineStartIndex)

        var endRect: Rect = textLayoutResult.getBoundingBox(endIndex)
        if (endRect.width <= 0) {
            endRect = textLayoutResult.getCursorRect(endIndex)
        }
        startRect.union(endRect)

    } else {
        // this is a intermediary line between the lines that start and end chars exist
        // get full line as rect or divide it to equal parts for better reveal effect
        val lineStartX: Float = textLayoutResult.getLineLeft(currentLine)
        val lineStartY: Float = textLayoutResult.getLineTop(currentLine)
        val lineEndX: Float = textLayoutResult.getLineRight(currentLine)
        val lineEndY: Float = textLayoutResult.getLineBottom(currentLine)

        Rect(
            topLeft = Offset(lineStartX, lineStartY),
            bottomRight = Offset(lineEndX, lineEndY)
        )
    }
}

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

internal fun calculateBoundingRecWithColortList(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int
): List<RectWithColor> {

    if (startIndex > endIndex) return emptyList()

    val safeStart =
        startIndex.coerceIn(0, (textLayoutResult.layoutInput.text.length - 1).coerceAtLeast(0))
    val safeEnd =
        (endIndex).coerceIn(0, (textLayoutResult.layoutInput.text.length - 1).coerceAtLeast(0))

    val startLine = textLayoutResult.getLineForOffset(safeStart)
    val endLine = textLayoutResult.getLineForOffset(safeEnd)

    val rectList = mutableListOf<RectWithColor>()

    for (currentLine in startLine..endLine) {

        if (currentLine == startLine && startLine == endLine) {
            //  This line contains both start and end indices
            //  get bounding rects for start and end indices and create union of them
            val startRect: Rect = textLayoutResult.getBoundingBox(safeStart)
            var endRect: Rect = textLayoutResult.getBoundingBox(safeEnd)
            if (endRect.width <= 0) {
                endRect = textLayoutResult.getCursorRect(endIndex)
            }

            val finalRect: Rect = startRect.union(endRect)

            rectList.add(
                RectWithColor(
                    rect = finalRect,
                    color = Color.Magenta
                )
            )
            println("calculateBoundingRectList single line")

        } else if (currentLine == startLine) {
            // start index is in this line but end index is not in this line
            // get bounding rect of char at start index and char at end of the line
            println("calculateBoundingRectList starting line of multiple line")

            val startRect = textLayoutResult.getBoundingBox(startIndex)

            // 🔥EndRect does not return correct values if line ends with no width char like \n
            // If \n is 11th character endRect becomes 12th instead of being last char of this line

            val lineEndX = textLayoutResult.getLineRight(currentLine)
            val lineEndY = textLayoutResult.getLineBottom(currentLine)

            val finalRect = Rect(
                topLeft = startRect.topLeft,
                bottomRight = Offset(lineEndX, lineEndY)
            )
            rectList.add(
                RectWithColor(
                    rect = finalRect,
                    color = Color.Blue
                )
            )
        } else if (currentLine == endLine) {
            // end index is in this line but start index was in one of the lines or line above
            // get start of the line and bounding rect of end index and union them
            val lineStartIndex = textLayoutResult.getLineStart(currentLine)
            val startRect = textLayoutResult.getBoundingBox(lineStartIndex)

            var endRect = textLayoutResult.getBoundingBox(endIndex)
            if (endRect.width <= 0) {
                endRect = textLayoutResult.getCursorRect(endIndex)
            }

            val finalRect = startRect.union(endRect)
            rectList.add(
                RectWithColor(
                    rect = finalRect,
                    color = Color.Cyan
                )
            )
            println("calculateBoundingRectList end line of multiple line")
        } else {
            // this is a intermediary line between the lines that start and end chars exist
            // get full line as rect or divide it to equal parts for better reveal effect
            println("calculateBoundingRectList middle full line rect")

            val lineStartX = textLayoutResult.getLineLeft(currentLine)
            val lineStartY = textLayoutResult.getLineTop(currentLine)
            val lineEndX = textLayoutResult.getLineRight(currentLine)
            val lineEndY = textLayoutResult.getLineBottom(currentLine)

            val finalRect = Rect(
                topLeft = Offset(lineStartX, lineStartY),
                bottomRight = Offset(lineEndX, lineEndY)
            )

            rectList.add(
                RectWithColor(
                    rect = finalRect,
                    color = Color.DarkGray
                )
            )
        }
    }
    return rectList
}

internal fun Rect.union(other: Rect): Rect {
    return Rect(
        left = min(this.left, other.left),
        top = min(this.top, other.top),
        right = max(this.right, other.right),
        bottom = max(this.bottom, other.bottom)
    )
}

data class RectWithColor(
    val rect: Rect,
    val color: Color
)

fun randomColor() = Color(
    Random.nextInt(256),
    Random.nextInt(256),
    Random.nextInt(256)
)