package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal fun calculateBoundingRects(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int
): List<Rect> {

    if (startIndex >= endIndex) return emptyList()

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
            startIndex = startIndex,
            endIndex = endIndex,
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

internal fun calculateBoundingRectList(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int
): List<RectWithColor> {
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
            val startRect: Rect = textLayoutResult.getBoundingBox(startIndex)
            var endRect: Rect = textLayoutResult.getBoundingBox(endIndex)
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
        } else if (currentLine == startLine) {
            // start index is in this line but end index is not in this line
            // get bounding rect of char at start index and char at end of the line
            println("2")

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
            println("3")
        } else {
            // this is a intermediary line between the lines that start and end chars exist
            // get full line as rect or divide it to equal parts for better reveal effect
            println("4")

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


internal fun getTextLayoutResultAsString(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    text: String
): String {

    // Which line this character with startIndex is in this text
    val lineIndex = textLayoutResult.getLineForOffset(startIndex)
    // Start index of this line
    val lineStart = textLayoutResult.getLineStart(lineIndex)
    // Start offset of this line in px
    val lineLeft = textLayoutResult.getLineLeft(lineIndex)
    // 🔥 End index of this line. If this line ends with empty char this index returns next index,
    // Which might be start index of next line
    val lineEnd = textLayoutResult.getLineEnd(lineIndex)
    // End offset of this line in px
    val lineRight = textLayoutResult.getLineRight(lineIndex)
    // Top of this line in px
    val lineTop = textLayoutResult.getLineTop(lineIndex)
    // Bottom of this line in px
    val lineBottom = textLayoutResult.getLineBottom(lineIndex)
    // Baseline position of this line in px
    val lineBaseline = textLayoutResult.getLineBaseline(lineIndex)
    // x value of start of the character at index in px(changes LtR and RtL layouts)
    val horizontalPosition =
        textLayoutResult.getHorizontalPosition(offset = startIndex, usePrimaryDirection = false)

    return """
                index: $startIndex
                Char: ${text[startIndex]}
                lineIndex: $lineIndex
                lineStart: ${lineStart}th index
                lineLeft: $lineLeft px
                lineEnd: ${lineEnd}th index
                lineRight: $lineRight px
                lineTop: $lineTop px
                lineBottom: $lineBottom px
                lineBaseline: $lineBaseline px
                horizontalPosition: $horizontalPosition
            """.trimIndent()
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