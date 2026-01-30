package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal data class RectSpan(
    val rect: Rect,
    val charStart: Int,
    val charEnd: Int,
    val line: Int
)

internal sealed interface SpanSegmentation {
    /** Current behavior: one RectSpan per affected layout line */
    data object Lines : SpanSegmentation

    /** Split each affected layout line into word token spans */
    data class Words(
        val includePunctuationAsOwnToken: Boolean = false
    ) : SpanSegmentation

    /** Split each affected layout line into fixed-size char chunks */
    data class FixedChars(
        val charsPerChunk: Int
    ) : SpanSegmentation
}

internal fun calculateBoundingRectSpans(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int,
    segmentation: SpanSegmentation = SpanSegmentation.Lines
): List<RectSpan> {
    val text = textLayoutResult.layoutInput.text
    if (text.isEmpty()) return emptyList()
    if (startIndex > endIndex) return emptyList()

    val lastIndex = text.length - 1
    val safeStart = startIndex.coerceIn(0, lastIndex)
    val safeEnd = endIndex.coerceIn(0, lastIndex)

    val startLine = textLayoutResult.getLineForOffset(safeStart)
    val endLine = textLayoutResult.getLineForOffset(safeEnd)

    val spans = mutableListOf<RectSpan>()

    for (line in startLine..endLine) {
        val lineStartIndex = textLayoutResult.getLineStart(line)
        val lineLastVisible = lastVisibleOffsetOnLine(layout = textLayoutResult, line = line)

        val spanStart = if (line == startLine) safeStart else lineStartIndex
        val spanEnd = if (line == endLine) minOf(safeEnd, lineLastVisible) else lineLastVisible

        if (spanStart > spanEnd) continue

        when (segmentation) {
            SpanSegmentation.Lines -> {
                // Your original "full line rect" logic, now extracted as a helper.
                buildLineSpan(
                    layout = textLayoutResult,
                    startLine = startLine,
                    endLine = endLine,
                    line = line,
                    spanStart = spanStart,
                    spanEnd = spanEnd
                )?.let { spans += it }
            }

            is SpanSegmentation.Words -> {
                spans += splitLineIntoWordSpans(
                    layout = textLayoutResult,
                    line = line,
                    startIndex = spanStart,
                    endIndex = spanEnd,
                    includePunctuationAsOwnToken = segmentation.includePunctuationAsOwnToken
                )
            }

            is SpanSegmentation.FixedChars -> {
                spans += splitLineIntoFixedCharSpans(
                    layout = textLayoutResult,
                    line = line,
                    startIndex = spanStart,
                    endIndex = spanEnd,
                    charsPerChunk = segmentation.charsPerChunk
                )
            }
        }
    }

    return spans
}

private fun buildLineSpan(
    layout: TextLayoutResult,
    startLine: Int,
    endLine: Int,
    line: Int,
    spanStart: Int,
    spanEnd: Int
): RectSpan? {

    val lineTop = layout.getLineTop(line)
    val lineBottom = layout.getLineBottom(line)
    val lineLeft = layout.getLineLeft(line)

    val endOnThisLine = minOf(spanEnd, lastVisibleOffsetOnLine(layout = layout, line = line))

    val rect = when {
        // single line overall range
        startLine == endLine -> {
            val startRect = safeBoxOrCursor(layout = layout, offset = spanStart)
            val endRect = safeBoxOrCursor(layout = layout, offset = endOnThisLine)
            val unionRect = startRect.union(endRect)
            Rect(
                topLeft = Offset(unionRect.left, lineTop),
                bottomRight = Offset(unionRect.right, lineBottom)
            )
        }

        // first line of a multi-line range
        line == startLine -> {
            val startRect = safeBoxOrCursor(layout = layout, offset = spanStart)
            val endRect = safeBoxOrCursor(layout = layout, offset = endOnThisLine)
            Rect(
                topLeft = Offset(startRect.left, lineTop),
                bottomRight = Offset(endRect.right, lineBottom)
            )
        }

        // last line of a multi-line range
        line == endLine -> {
            val endRect = safeBoxOrCursor(layout = layout, offset = endOnThisLine)
            Rect(
                topLeft = Offset(lineLeft, lineTop),
                bottomRight = Offset(endRect.right, lineBottom)
            )
        }

        // middle lines
        else -> {
            val endRect = safeBoxOrCursor(layout = layout, offset = endOnThisLine)
            Rect(
                topLeft = Offset(lineLeft, lineTop),
                bottomRight = Offset(endRect.right, lineBottom)
            )
        }
    }

    return if (rect.width > 0f && rect.height > 0f) {
        RectSpan(rect = rect, charStart = spanStart, charEnd = endOnThisLine, line = line)
    } else null
}

private fun splitLineIntoWordSpans(
    layout: TextLayoutResult,
    line: Int,
    startIndex: Int,
    endIndex: Int,
    includePunctuationAsOwnToken: Boolean
): List<RectSpan> {
    val text = layout.layoutInput.text.text
    val lineTop = layout.getLineTop(line)
    val lineBottom = layout.getLineBottom(line)

    fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_'
    fun isWhitespace(c: Char): Boolean = c.isWhitespace()

    val spans = mutableListOf<RectSpan>()

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
                j - 1
            }
            else -> {
                if (includePunctuationAsOwnToken) {
                    i
                } else {
                    var j = i
                    while (j <= endIndex && !isWhitespace(text[j]) && !isWordChar(text[j])) j++
                    j - 1
                }
            }
        }

        val safeStart = tokenStart.coerceAtLeast(startIndex)
        val safeEnd = tokenEnd.coerceAtMost(endIndex)

        if (safeStart <= safeEnd) {
            val startRect = safeBoxOrCursor(layout = layout, offset = safeStart)
            val endRect = safeBoxOrCursor(layout = layout, offset = safeEnd)
            val union = startRect.union(endRect)

            val rect = Rect(
                topLeft = Offset(union.left, lineTop),
                bottomRight = Offset(union.right, lineBottom)
            )

            if (rect.width > 0f && rect.height > 0f) {
                spans += RectSpan(rect = rect, charStart = safeStart, charEnd = safeEnd, line = line)
            }
        }

        i = tokenEnd + 1
    }

    return spans
}

// 6) Split a single layout line into fixed-size char spans (new)
private fun splitLineIntoFixedCharSpans(
    layout: TextLayoutResult,
    line: Int,
    startIndex: Int,
    endIndex: Int,
    charsPerChunk: Int
): List<RectSpan> {
    val chunk = charsPerChunk.coerceAtLeast(1)

    val lineTop = layout.getLineTop(line)
    val lineBottom = layout.getLineBottom(line)

    val spans = mutableListOf<RectSpan>()

    var i = startIndex
    while (i <= endIndex) {
        val chunkStart = i
        val chunkEnd = minOf(endIndex, i + chunk - 1)

        val startRect = safeBoxOrCursor(layout = layout, offset = chunkStart)
        val endRect = safeBoxOrCursor(layout = layout, offset = chunkEnd)
        val union = startRect.union(endRect)

        val rect = Rect(
            topLeft = Offset(union.left, lineTop),
            bottomRight = Offset(union.right, lineBottom)
        )

        if (rect.width > 0f && rect.height > 0f) {
            spans += RectSpan(rect = rect, charStart = chunkStart, charEnd = chunkEnd, line = line)
        }

        i = chunkEnd + 1
    }

    return spans
}


internal fun calculateBoundingRectSpans(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int
): List<RectSpan> {
    val text = textLayoutResult.layoutInput.text
    if (text.isEmpty()) return emptyList()
    if (startIndex > endIndex) return emptyList()

    val lastIndex = text.length - 1
    val safeStart = startIndex.coerceIn(0, lastIndex)
    val safeEnd = endIndex.coerceIn(0, lastIndex)

    val startLine = textLayoutResult.getLineForOffset(safeStart)
    val endLine = textLayoutResult.getLineForOffset(safeEnd)

    val spans = mutableListOf<RectSpan>()

    for (line in startLine..endLine) {
        val lineStartIndex = textLayoutResult.getLineStart(line)
        val lineLastVisible = lastVisibleOffsetOnLine(layout = textLayoutResult, line = line)

        val spanStart = when {
            line == startLine -> safeStart
            else -> lineStartIndex
        }

        val spanEnd = when {
            line == endLine -> minOf(safeEnd, lineLastVisible)
            else -> lineLastVisible
        }

        if (spanStart > spanEnd) continue

        val lineTop = textLayoutResult.getLineTop(line)
        val lineBottom = textLayoutResult.getLineBottom(line)
        val lineLeft = textLayoutResult.getLineLeft(line)

        val rect = when {
            // single line
            startLine == endLine -> {
                val startRect = safeBoxOrCursor(layout = textLayoutResult, offset = spanStart)
                val endRect = safeBoxOrCursor(layout = textLayoutResult, offset = spanEnd)
                val unionRect = startRect.union(endRect)
                Rect(
                    topLeft = Offset(unionRect.left, lineTop),
                    bottomRight = Offset(unionRect.right, lineBottom)
                )
            }

            // first line
            line == startLine -> {
                val startRect = safeBoxOrCursor(layout = textLayoutResult, offset = spanStart)
                val endRect = safeBoxOrCursor(layout = textLayoutResult, offset = spanEnd)
                Rect(
                    topLeft = Offset(startRect.left, lineTop),
                    bottomRight = Offset(endRect.right, lineBottom)
                )
            }

            // last line
            line == endLine -> {
                val endRect = safeBoxOrCursor(layout = textLayoutResult, offset = spanEnd)
                Rect(
                    topLeft = Offset(lineLeft, lineTop),
                    bottomRight = Offset(endRect.right, lineBottom)
                )
            }

            // middle lines
            else -> {
                val endRect = safeBoxOrCursor(layout = textLayoutResult, offset = spanEnd)
                Rect(
                    topLeft = Offset(lineLeft, lineTop),
                    bottomRight = Offset(endRect.right, lineBottom)
                )
            }
        }

        if (rect.width > 0f && rect.height > 0f) {
            spans += RectSpan(rect = rect, charStart = spanStart, charEnd = spanEnd, line = line)
        }
    }

    return spans
}

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

internal fun calculateBoundingRecWithColorList(
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

data class RectWithAnimation(
    val id: String = "",
    val startIndex: Int,
    val endIndex: Int,
    val rect: Rect,
    val animatable: Animatable<Float, AnimationVector1D> = Animatable(0f),
)

internal data class RectWithAnimatable(
    val id: String,
    val rect: Rect,
    val charStart: Int,
    val charEnd: Int,
    val batchId: Long = 0L,
    val animatable: Animatable<Float, AnimationVector1D> = Animatable(0f),
)

internal fun RectWithAnimatable.covers(index: Int): Boolean = index in charStart..charEnd

data class RectWithColor(
    val rect: Rect,
    val color: Color
)

fun randomColor() = Color(
    Random.nextInt(256),
    Random.nextInt(256),
    Random.nextInt(256)
)