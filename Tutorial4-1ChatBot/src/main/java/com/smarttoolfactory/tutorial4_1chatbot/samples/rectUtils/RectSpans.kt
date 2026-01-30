package com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult

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
