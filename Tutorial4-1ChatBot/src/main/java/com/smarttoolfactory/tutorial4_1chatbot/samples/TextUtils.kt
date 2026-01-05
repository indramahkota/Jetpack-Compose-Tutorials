package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun List<String>.toWordFlow(
    delayMillis: Long = 100,
    wordsPerEmission: Int = 1
): Flow<String> = flow {
    val chunks = mutableListOf<String>()

    // First, collect all chunks (split only on spaces, keep everything else)
    forEach { chunk ->
        var i = 0
        while (i < chunk.length) {
            when {
                chunk[i] == ' ' -> {
                    chunks.add(" ")
                    i++
                }

                else -> {
                    // Collect everything until we hit a space
                    val word = StringBuilder()
                    while (i < chunk.length && chunk[i] != ' ') {
                        word.append(chunk[i])
                        i++
                    }
                    chunks.add(word.toString())
                }
            }
        }
    }

    // Emit in groups
    var wordCount = 0
    val batch = StringBuilder()

    chunks.forEach { chunk ->
        batch.append(chunk)

        // Count as a word only if it's not just a space
        if (chunk != " ") {
            wordCount++
        }

        if (wordCount >= wordsPerEmission) {
            emit(batch.toString())
            delay(delayMillis)
            batch.clear()
            wordCount = 0
        }
    }

    // Emit remaining
    if (batch.isNotEmpty()) {
        emit(batch.toString())
    }
}

fun String.toWordFlow(
    delayMillis: Long = 100,
    wordsPerEmission: Int = 1
): Flow<String> = flow {
    val chunks = mutableListOf<String>()

    // Collect all chunks (split only on spaces, keep everything else)
    var i = 0
    while (i < length) {
        when {
            this@toWordFlow[i] == ' ' -> {
                chunks.add(" ")
                i++
            }

            else -> {
                // Collect everything until we hit a space
                val word = StringBuilder()
                while (i < length && this@toWordFlow[i] != ' ') {
                    word.append(this@toWordFlow[i])
                    i++
                }
                chunks.add(word.toString())
            }
        }
    }

    // Emit in groups
    var wordCount = 0
    val batch = StringBuilder()

    chunks.forEach { chunk ->
        batch.append(chunk)

        // Count as a word only if it's not just a space
        if (chunk != " ") {
            wordCount++
        }

        if (wordCount >= wordsPerEmission) {
            emit(batch.toString())
            delay(delayMillis)
            batch.clear()
            wordCount = 0
        }
    }

    // Emit remaining
    if (batch.isNotEmpty()) {
        emit(batch.toString())
    }
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