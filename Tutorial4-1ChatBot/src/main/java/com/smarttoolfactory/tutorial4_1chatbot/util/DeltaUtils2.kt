package com.smarttoolfactory.tutorial4_1chatbot.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Stream text as WORD-STABLE, Markdown-safe tokens suitable for
 * rectangle-based trail / fade-in animations.
 *
 * ## Core guarantees
 *
 * - **Never emits partial Markdown delimiters** such as `"*"`, `"**"`, `"_"`, `"__"`, `"~"`, or ``"` ``.
 * - Emits **completed Markdown spans atomically**:
 *   - `**bold**`, `__bold__`, `~~strike~~`, `*italic*`, `_italic_`
 *   - Inline code: `` `code` ``
 *   - Code fences: ``` ```code``` ``` (if `strictTripleBackticks = true`)
 * - Emits **plain text as full word tokens**, optionally with a single trailing space.
 * - Emits **newlines (`\n`) as their own token**.
 *
 * ## Why this exists
 *
 * This function is designed for **streaming UIs that animate text by bounding boxes**
 * (e.g. ChatGPT-style trail reveal effects).
 *
 * Emitting text before Markdown is structurally complete causes:
 * - layout reflow (bold / italic width changes),
 * - table column recalculation,
 * - line breaks shifting,
 * which invalidates previously computed rectangles.
 *
 * This tokenizer avoids that by:
 * - buffering until Markdown structure is stable,
 * - and only emitting tokens whose rendered position will not change later.
 *
 * ## Handling incomplete Markdown
 *
 * Some models intentionally emit *unclosed* spans (e.g. `"**part"` with no closing `"**"`).
 * Fully blocking on these would stall the entire stream.
 *
 * This function therefore applies a **newline-based fail-open policy**:
 *
 * - If an opening delimiter does **not** close before the next newline:
 *   - it is treated as **literal text**,
 *   - BUT it is **never emitted as a standalone delimiter token**.
 * - Instead, the delimiter is **attached to the next emitted word** so tokens like `"**"`
 *   never appear by themselves.
 *
 * This preserves forward progress while preventing visual artifacts.
 *
 * ## Important limitation
 *
 * Markdown tables may still reflow earlier rows when later rows arrive.
 * For perfect rect stability inside tables, consider buffering entire table rows
 * (until `\n`) before emitting.
 *
 * ---
 *
 * @param delayMillis
 * Delay applied **after each emitted token**.
 * Used to pace streaming and control animation cadence.
 *
 * @param normalizeSpaces
 * If `true`, normalizes tabs (`\t`) into spaces.
 * Newlines are preserved verbatim.
 *
 * @param attachSingleTrailingSpace
 * If `true`, collapses any run of spaces following a word into **one**
 * trailing space attached to that word token.
 *
 * This reduces token count and recomposition frequency while keeping layout stable.
 *
 * @param strictTripleBackticks
 * If `true`, code fences (` ``` `) are treated as **strict paired spans** and will
 * not emit until a matching closing fence is present.
 *
 * If `false`, triple backticks are treated as literal text.
 *
 * @param failOpenUnclosedSpansAtNewline
 * If `true`, an opening delimiter that does not close **before the next newline**
 * is treated as literal text.
 *
 * The delimiter is never emitted alone; it is merged into the next emitted token.
 *
 * If `false`, the stream will stall until the delimiter closes or the flow completes.
 *
 * @param flushRemainderOnComplete
 * If `true`, any remaining buffered text is emitted when the upstream flow completes,
 * even if it contains incomplete Markdown.
 *
 * For rectangle-accurate animations, keeping this `false` is usually safer.
 *
 * @param maxWordTokensPerFlush
 * Maximum number of **plain word tokens** emitted per incoming delta.
 * Useful to throttle recomposition under very fast SSE streams.
 *
 * @param maxSpansPerFlush
 * Maximum number of **completed Markdown span tokens** emitted per incoming delta.
 *
 * @return
 * A [Flow] emitting **stable text tokens** that can be appended directly to the UI
 * without causing later layout shifts.
 */
fun Flow<String>.deltasToWordStableMarkdownTokensWithDelay(
    delayMillis: Long = 16L,
    normalizeSpaces: Boolean = true,
    attachSingleTrailingSpace: Boolean = true,
    strictTripleBackticks: Boolean = true,
    failOpenUnclosedSpansAtNewline: Boolean = true,
    flushRemainderOnComplete: Boolean = false,
    maxWordTokensPerFlush: Int = Int.MAX_VALUE,
    maxSpansPerFlush: Int = Int.MAX_VALUE,
): Flow<String> = flow {

    val sb = StringBuilder()

    // Used to ensure we never emit "*", "**", "_", or "`" as standalone tokens
    // when failing open on incomplete spans.
    var pendingLiteralPrefix = ""

    fun normalizeIn(text: String): String {
        if (!normalizeSpaces) return text
        return buildString(text.length) {
            for (c in text) append(if (c == '\t') ' ' else c)
        }
    }

    suspend fun emitToken(raw: String) {
        if (raw.isEmpty() && pendingLiteralPrefix.isEmpty()) return

        val token =
            if (pendingLiteralPrefix.isNotEmpty()) {
                val merged = pendingLiteralPrefix + raw
                pendingLiteralPrefix = ""
                merged
            } else raw

        emit(token)
        delay(delayMillis)
    }

    val pairedDelims = listOf("```", "**", "__", "~~", "`", "*", "_")

    fun isDelimiterStartChar(c: Char): Boolean =
        c == '*' || c == '_' || c == '~' || c == '`'

    fun StringBuilder.startsWithToken(token: String): Boolean {
        if (length < token.length) return false
        for (i in token.indices) if (this[i] != token[i]) return false
        return true
    }

    fun StringBuilder.indexOfToken(token: String, start: Int, endExclusive: Int = length): Int {
        val max = endExclusive - token.length
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

    fun StringBuilder.findNextNewline(from: Int = 0): Int {
        for (i in from until length) if (this[i] == '\n') return i
        return -1
    }

    data class Out(val token: String, val isSpan: Boolean)

    fun StringBuilder.consumeOne(): Out? {
        if (isEmpty()) return null

        if (this[0] == '\n') {
            delete(0, 1)
            return Out("\n", false)
        }

        if (this[0] == ' ') {
            var i = 0
            while (i < length && this[i] == ' ') i++
            delete(0, i)
            return Out(" ", false)
        }

        if (isDelimiterStartChar(this[0])) {
            val candidates = pairedDelims
                .filter { it[0] == this[0] }
                .sortedByDescending { it.length }

            val maxLen = candidates.maxOfOrNull { it.length } ?: 1
            if (length < maxLen) return null

            for (delim in candidates) {
                if (length < delim.length || !startsWithToken(delim)) continue

                if (delim == "```" && !strictTripleBackticks) {
                    delete(0, 3)
                    return Out("```", false)
                }

                val start = delim.length
                val nl = if (failOpenUnclosedSpansAtNewline) findNextNewline(start) else -1
                val searchEnd = if (nl == -1) length else nl

                val endIdx = indexOfToken(delim, start, searchEnd)
                if (endIdx == -1) {
                    if (failOpenUnclosedSpansAtNewline && nl != -1) {
                        delete(0, delim.length)
                        pendingLiteralPrefix += delim
                        return null
                    }
                    return null
                }

                val span = substring(0, endIdx + delim.length)
                delete(0, endIdx + delim.length)
                return Out(span, true)
            }

            delete(0, 1)
            pendingLiteralPrefix += this[0]
            return null
        }

        var i = 0
        while (i < length) {
            val ch = this[i]
            if (ch == '\n' || ch == ' ' || isDelimiterStartChar(ch)) break
            i++
        }

        if (i == 0) return null

        val word = substring(0, i)
        delete(0, i)

        if (attachSingleTrailingSpace && isNotEmpty() && this[0] == ' ') {
            var j = 0
            while (j < length && this[j] == ' ') j++
            delete(0, j)
            return Out(word + " ", false)
        }

        return Out(word, false)
    }

    collect { delta ->
        if (delta.isEmpty()) return@collect
        sb.append(normalizeIn(delta).replace("\r\n", "\n").replace("\r", "\n"))

        var wordsLeft = maxWordTokensPerFlush
        var spansLeft = maxSpansPerFlush

        while (true) {
            val out = sb.consumeOne() ?: break

            if (out.isSpan) {
                if (spansLeft-- <= 0) {
                    sb.insert(0, out.token)
                    break
                }
                emitToken(out.token)
            } else {
                if (wordsLeft-- <= 0) {
                    sb.insert(0, out.token)
                    break
                }
                emitToken(out.token)
            }
        }
    }

    if (flushRemainderOnComplete) {
        if (pendingLiteralPrefix.isNotEmpty()) emit(pendingLiteralPrefix)
        if (sb.isNotEmpty()) emit(sb.toString())
    }
}
