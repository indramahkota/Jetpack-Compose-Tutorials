package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

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

internal fun calculateBoundingRectList(
    textLayoutResult: TextLayoutResult,
    startIndex: Int,
    endIndex: Int
): List<RectWithColor> {
    val startLine = textLayoutResult.getLineForOffset(startIndex)
    val endLine = textLayoutResult.getLineForOffset(endIndex)

    val rectList = mutableListOf<RectWithColor>()

    for (currentLine in startLine..endLine) {
        if (currentLine == startLine && startLine == endLine) {

            println("startIndex: $startIndex, endIndex: $endIndex")
            //  This line contains both start and end indices
            //  get bounding rects for start and end indices and create union of them
            val startRect = textLayoutResult.getBoundingBox(startIndex)
            var endRect = textLayoutResult.getBoundingBox(endIndex)
            if (endRect.width <= 0) {
                endRect = textLayoutResult.getCursorRect(endIndex)
            }

            val finalRect = startRect.union(endRect)

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

            // 🔥 EndRect does not return correct value when previous line ends with \n
            // For debugging
//            val lineStartIndex = textLayoutResult.getLineStart(currentLine)
//            val lineEndIndex = textLayoutResult.getLineEnd(currentLine)
//            val startRect = textLayoutResult.getBoundingBox(lineStartIndex)
//            val endRect = textLayoutResult.getBoundingBox(lineEndIndex)
//            val wrongRect = startRect.union(endRect)
//            rectList.add(
//                RectWithColor(
//                    rect = wrongRect,
//                    color = Color.Red
//                )
//            )
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

fun randomColor() = Color(
    Random.nextInt(256),
    Random.nextInt(256),
    Random.nextInt(256)
)