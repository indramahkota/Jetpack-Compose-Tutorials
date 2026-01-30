package com.smarttoolfactory.tutorial4_1chatbot.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stream text in Markdown-safe chunks:
 * - NEVER emits an opening paired markdown delimiter unless its closing delimiter is present:
 *   **...**, __...__, ~~...~~, `...`, ```...```
 * - Newlines are emitted as their own chunk.
 * - Plain text is emitted as word-ish units (word + optional trailing single space).
 *
 * This prevents broken intermediate Markdown such as "**hello" from being displayed.
 */
fun Flow<String>.deltasToMarkdownTokensWithDelay(
    delayMillis: Long = 16L,
    flushRemainderOnComplete: Boolean = true,
    maxCompletedWordsPerFlush: Int = Int.MAX_VALUE,
    maxCompletedMarkdownSpansPerFlush: Int = Int.MAX_VALUE,
    normalizeSpaces: Boolean = true
): Flow<String> = flow {
    val sb = StringBuilder()

    // Paired delimiters that must be complete before emitting.
    val pairedDelims = listOf("```", "**", "__", "~~", "`")

    fun StringBuilder.startsWithToken(token: String): Boolean {
        if (length < token.length) return false
        for (i in token.indices) if (this[i] != token[i]) return false
        return true
    }

    fun StringBuilder.indexOfToken(token: String, start: Int): Int {
        val max = length - token.length
        var i = start
        while (i <= max) {
            var ok = true
            for (k in token.indices) {
                if (this[i + k] != token[k]) {
                    ok = false
                    break
                }
            }
            if (ok) return i
            i++
        }
        return -1
    }

    fun countWordsIn(text: String): Int {
        // Count "words" as runs of non-whitespace.
        // This is used for budget limiting; it does not affect output content.
        var inWord = false
        var count = 0
        for (ch in text) {
            val isWs = ch.isWhitespace()
            if (!isWs && !inWord) {
                inWord = true
                count++
            } else if (isWs) {
                inWord = false
            }
        }
        return count
    }

    fun normalizeChunk(token: String): String {
        if (!normalizeSpaces) return token
        return when (token) {
            "\t" -> " "
            else -> token
        }
    }

    suspend fun emitChunk(token: String) {
        emit(normalizeChunk(token))
        delay(delayMillis)
    }

    /**
     * Try to consume one *safe* chunk from the front of the buffer.
     *
     * Returns:
     * - chunk string to emit, and bookkeeping info
     * - or null if we must wait for more input (e.g., open "**" without closing "**")
     */
    data class Consumed(
        val chunk: String,
        val isMarkdownSpan: Boolean,
        val wordsInChunk: Int
    )

    fun StringBuilder.consumeSafeChunkOrNull(): Consumed? {
        if (isEmpty()) return null

        // 1) Newline as atomic chunk (very important for markdown layout).
        if (startsWithToken("\n")) {
            delete(0, 1)
            return Consumed(chunk = "\n", isMarkdownSpan = false, wordsInChunk = 0)
        }

        // 2) If the buffer starts with a paired delimiter, only emit if we can see the closing delimiter.
        for (delim in pairedDelims) {
            if (startsWithToken(delim)) {
                val start = delim.length
                val endIdx = indexOfToken(delim, start)
                if (endIdx == -1) {
                    // We have an opening delimiter but no closing one yet -> wait.
                    return null
                }

                // Include both delimiters: "**hello**", "`code`", "```block```"
                val spanEndExclusive = endIdx + delim.length
                val span = substring(0, spanEndExclusive)
                delete(0, spanEndExclusive)

                return Consumed(
                    chunk = span,
                    isMarkdownSpan = true,
                    wordsInChunk = countWordsIn(span)
                )
            }
        }

        // 3) Basic link/image safety: if it starts like [..](..) or ![..](..),
        //    do not emit partial; wait until closing ')' exists.
        //    This avoids streaming "[text](" which renders poorly.
        if (startsWithToken("![") || startsWithToken("[")) {
            val bang = startsWithToken("![")
            val openBracketPos = if (bang) 1 else 0
            // Find the closing ']' first.
            val closeBracket = indexOfToken("]", openBracketPos + 1)
            if (closeBracket == -1) return null

            // Must be followed by '(' to be a link/image; if not, fall through to plain text behavior.
            val hasParen =
                closeBracket + 1 < length && this[closeBracket + 1] == '('

            if (hasParen) {
                val closeParen = indexOfToken(")", closeBracket + 2)
                if (closeParen == -1) return null

                val endExclusive = closeParen + 1
                val link = substring(0, endExclusive)
                delete(0, endExclusive)

                return Consumed(
                    chunk = link,
                    isMarkdownSpan = true, // treat as atomic markdown construct
                    wordsInChunk = countWordsIn(link)
                )
            }
        }

        // 4) Whitespace: emit a single normalized space (or newline handled above).
        //    Optionally collapse runs of spaces/tabs to one space.
        val first = this[0]
        if (first == ' ' || first == '\t') {
            var i = 0
            while (i < length && (this[i] == ' ' || this[i] == '\t')) i++
            delete(0, i)
            return Consumed(chunk = " ", isMarkdownSpan = false, wordsInChunk = 0)
        }

        // 5) Headings / list / quote markers at the start are safe as atomic-ish markers,
        //    but we must not accidentally emit part of a paired delimiter.
        //    (Paired delimiters were already checked above.)
        val singleSpecials = listOf("#", ">", "-", "*", "+", "|", "(", ")", "[", "]", "!")
        for (sp in singleSpecials) {
            if (startsWithToken(sp)) {
                delete(0, sp.length)
                return Consumed(chunk = sp, isMarkdownSpan = false, wordsInChunk = 0)
            }
        }

        // 6) Plain word-ish chunk: consume until whitespace/newline or until we reach a paired delimiter boundary.
        //    We deliberately stop before a paired delimiter so we can enforce completeness on the next iteration.
        var i = 0
        while (i < length) {
            val ch = this[i]
            if (ch == '\n' || ch == ' ' || ch == '\t') break

            // Stop if a paired delimiter begins at this position (e.g., "... **bold" case).
            var hitsPaired = false
            for (delim in pairedDelims) {
                val canCheck = i + delim.length <= length
                if (canCheck) {
                    var ok = true
                    for (k in delim.indices) {
                        if (this[i + k] != delim[k]) {
                            ok = false
                            break
                        }
                    }
                    if (ok) {
                        hitsPaired = true
                        break
                    }
                }
            }
            if (hitsPaired) break

            i++
        }

        if (i == 0) {
            // Nothing safe to consume (likely because we’re at a delimiter boundary but not handled)
            return null
        }

        val word = substring(0, i)
        delete(0, i)

        // Optionally consume a single following space/tab and attach it to reduce token count/recomposition.
        // (Newline must remain separate.)
        if (isNotEmpty() && (this[0] == ' ' || this[0] == '\t')) {
            var j = 0
            while (j < length && (this[j] == ' ' || this[j] == '\t')) j++
            delete(0, j)
            return Consumed(
                chunk = word + " ",
                isMarkdownSpan = false,
                wordsInChunk = 1
            )
        }

        return Consumed(
            chunk = word,
            isMarkdownSpan = false,
            wordsInChunk = 1
        )
    }

    collect { delta ->
        if (delta.isEmpty()) return@collect

        // Normalize CRLF/CR to LF so newline handling is consistent.
        sb.append(delta.replace("\r\n", "\n").replace("\r", "\n"))

        var wordsBudget = maxCompletedWordsPerFlush
        var markdownBudget = maxCompletedMarkdownSpansPerFlush

        while (wordsBudget > 0 && markdownBudget > 0) {
            val consumed = sb.consumeSafeChunkOrNull() ?: break

            // Enforce budgets:
            // - wordsBudget counts words in *any* emitted chunk (including completed markdown spans)
            // - markdownBudget counts completed paired spans + completed links/images
            val isSpan = consumed.isMarkdownSpan
            val words = consumed.wordsInChunk

            if (isSpan) {
                if (markdownBudget <= 0) {
                    // Put it back (rare; budgets usually checked in loop condition).
                    sb.insert(0, consumed.chunk)
                    break
                }
                markdownBudget -= 1
            }

            if (words > 0) {
                if (wordsBudget - words < 0) {
                    // Put it back; wait for next flush.
                    sb.insert(0, consumed.chunk)
                    break
                }
                wordsBudget -= words
            }

            emitChunk(consumed.chunk)
        }
    }

    if (flushRemainderOnComplete && sb.isNotEmpty()) {
        // At completion, emit remaining buffer even if it contains incomplete markdown.
        // (If you prefer to *never* emit incomplete constructs, set flushRemainderOnComplete=false.)
        emitChunk(sb.toString())
    }
}
