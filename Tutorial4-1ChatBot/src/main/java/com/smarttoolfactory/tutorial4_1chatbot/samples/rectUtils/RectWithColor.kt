package com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult

/**
 * This is one of the functions initially written to check out rects with random colors
 * for checking out how rects are generated.
 */
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
