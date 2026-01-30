package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.union

sealed interface LineSegmentation {
    data object None : LineSegmentation

    /**
     * Split each affected line into word rects.
     * Word definition: contiguous letters/digits (optionally underscores), separated by whitespace/punct.
     */
    data class Words(
        val includePunctuationAsOwnToken: Boolean = false
    ) : LineSegmentation

    /**
     * Split each affected line into chunks of fixed character count (approx).
     * Useful when word splitting is too expensive or not desired.
     */
    data class FixedChars(
        val charsPerChunk: Int
    ) : LineSegmentation
}

internal fun calculateBoundingRectList(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int,
    segmentation: LineSegmentation = LineSegmentation.None
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
        // Determine the per-line range we want to cover
        val lineStart = textLayoutResult.getLineStart(currentLine)
        val lineEndInclusive = lastVisibleOffsetOnLine(textLayoutResult, currentLine)

        val segStart = if (currentLine == startLine) safeStart else lineStart
        val segEnd = if (currentLine == endLine) minOf(safeEnd, lineEndInclusive) else lineEndInclusive

        if (segStart > segEnd) continue

        when (segmentation) {
            LineSegmentation.None -> {
                val rect = getBoundingRectForLine(
                    textLayoutResult = textLayoutResult,
                    startIndex = safeStart,
                    endIndex = safeEnd,
                    startLine = startLine,
                    endLine = endLine,
                    currentLine = currentLine
                )
                if (rect.width > 0f && rect.height > 0f) rectList.add(rect)
            }

            is LineSegmentation.FixedChars -> {
                rectList += splitLineIntoFixedCharRects(
                    layout = textLayoutResult,
                    line = currentLine,
                    startIndex = segStart,
                    endIndex = segEnd,
                    charsPerChunk = segmentation.charsPerChunk
                )
            }

            is LineSegmentation.Words -> {
                rectList += splitLineIntoWordRects(
                    layout = textLayoutResult,
                    line = currentLine,
                    startIndex = segStart,
                    endIndex = segEnd,
                    includePunctuationAsOwnToken = segmentation.includePunctuationAsOwnToken
                )
            }
        }
    }

    return rectList
}

private fun splitLineIntoWordRects(
    layout: TextLayoutResult,
    line: Int,
    startIndex: Int,
    endIndex: Int,
    includePunctuationAsOwnToken: Boolean
): List<Rect> {
    val text = layout.layoutInput.text.text
    val lineTop = layout.getLineTop(line)
    val lineBottom = layout.getLineBottom(line)

    fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
    fun isWhitespace(c: Char): Boolean = c.isWhitespace()

    val rects = mutableListOf<Rect>()

    var i = startIndex
    while (i <= endIndex) {
        val c = text.getOrNull(i) ?: break

        // Skip whitespace
        if (isWhitespace(c)) {
            i++
            continue
        }

        val tokenStart = i

        val tokenEnd = when {
            isWordChar(c) -> {
                var j = i
                while (j <= endIndex && isWordChar(text[j])) j++
                (j - 1)
            }

            else -> {
                if (includePunctuationAsOwnToken) {
                    i // single char token
                } else {
                    // group consecutive non-whitespace non-word chars
                    var j = i
                    while (j <= endIndex && !isWhitespace(text[j]) && !isWordChar(text[j])) j++
                    (j - 1)
                }
            }
        }

        val startRect = safeBoxOrCursor(layout, tokenStart)
        val endRect = safeBoxOrCursor(layout, tokenEnd)
        val union = startRect.union(endRect)

        // Line-aligned vertical extent
        val rect = Rect(
            topLeft = Offset(union.left, lineTop),
            bottomRight = Offset(union.right, lineBottom)
        )

        if (rect.width > 0f && rect.height > 0f) rects.add(rect)

        i = tokenEnd + 1
    }

    return rects
}

private fun splitLineIntoFixedCharRects(
    layout: TextLayoutResult,
    line: Int,
    startIndex: Int,
    endIndex: Int,
    charsPerChunk: Int
): List<Rect> {
    val safeChunk = charsPerChunk.coerceAtLeast(1)

    val lineTop = layout.getLineTop(line)
    val lineBottom = layout.getLineBottom(line)

    val rects = mutableListOf<Rect>()

    var i = startIndex
    while (i <= endIndex) {
        val chunkStart = i
        val chunkEnd = minOf(endIndex, i + safeChunk - 1)

        val startRect = safeBoxOrCursor(layout, chunkStart)
        val endRect = safeBoxOrCursor(layout, chunkEnd)
        val union = startRect.union(endRect)

        val rect = Rect(
            topLeft = Offset(union.left, lineTop),
            bottomRight = Offset(union.right, lineBottom)
        )

        if (rect.width > 0f && rect.height > 0f) rects.add(rect)

        i = chunkEnd + 1
    }

    return rects
}

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
            val endRect: Rect = safeBoxOrCursor(
                textLayoutResult,
                lastVisibleOffsetOnLine(textLayoutResult, currentLine)
            )

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
            val endRect = safeBoxOrCursor(
                textLayoutResult,
                lastVisibleOffsetOnLine(textLayoutResult, currentLine)
            )

            Rect(
                topLeft = Offset(lineLeft, lineTop),
                bottomRight = Offset(endRect.right, lineBottom)
            )
        }
    }
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
