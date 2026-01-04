package com.smarttoolfactory.tutorial4_1chatbot.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Convert arbitrary token deltas into discrete word emissions.
 *
 * - Emits each word followed by a single trailing space (configurable).
 * - Correctly handles words split across deltas (e.g., "Hel" + "lo").
 * - Treats whitespace (space/tab/newline) as separators.
 */
fun Flow<String>.deltasToWordsWithDelay(
    delayMillis: Long = 60L,
    emitTrailingSpace: Boolean = true,
    flushRemainderOnComplete: Boolean = true
): Flow<String> = flow {
    val sb = StringBuilder()

    fun StringBuilder.consumeNextWordOrNull(): String? {
        // Skip leading whitespace
        while (isNotEmpty() && this[0].isWhitespace()) deleteCharAt(0)
        if (isEmpty()) return null

        // Find end of word
        var i = 0
        while (i < length && !this[i].isWhitespace()) i++

        // If no whitespace found, word may be incomplete (wait for more)
        if (i == length) return null

        val word = substring(0, i)
        delete(0, i)
        return word
    }

    collect { delta ->
        if (delta.isEmpty()) return@collect
        sb.append(delta)

        while (true) {
            val word = sb.consumeNextWordOrNull() ?: break
            emit(if (emitTrailingSpace) "$word " else word)
            delay(delayMillis)
        }
    }

    if (flushRemainderOnComplete) {
        // Emit any leftover (final word without trailing whitespace)
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) {
            emit(if (emitTrailingSpace) "$tail " else tail)
            delay(delayMillis)
        }
    }
}

fun Flow<String>.chunkDeltasWithDelay(
    wordMax: Int = 10,
    delayMillis: Long = 300L
): Flow<String> = flow {
    val buffer = mutableListOf<String>()

    collect { delta ->
        val words = delta
            .split(" ", "\n", "\t")
            .filter { it.isNotBlank() }

        for (word in words) {
            buffer.add(word)
            if (buffer.size == wordMax) {
                emit(buffer.joinToString(" ") + " ")
                delay(delayMillis)
                buffer.clear()
            }
        }
    }

    // Flush remaining words on completion
    if (buffer.isNotEmpty()) {
        emit(buffer.joinToString(" "))
        delay(delayMillis)
    }
}

fun Flow<String>.deltasToLinesNormalized(
    flushRemainderOnComplete: Boolean = true
): Flow<String> = flow {
    val sb = StringBuilder()

    collect { delta ->
        if (delta.isEmpty()) return@collect

        // Normalize CRLF and CR into LF so line splitting is consistent
        val normalized = delta.replace("\r\n", "\n").replace("\r", "\n")
        sb.append(normalized)

        while (true) {
            val idx = sb.indexOf('\n')
            if (idx < 0) break
            emit(sb.substring(0, idx))
            sb.delete(0, idx + 1)
        }
    }

    if (flushRemainderOnComplete && sb.isNotEmpty()) emit(sb.toString())
}

fun Flow<String>.deltasToLinesWithDelay(
    emitTrailingNewline: Boolean = false,
    flushRemainderOnComplete: Boolean = true,
    delayMillis: Long = 16L
): Flow<String> = flow {
    val sb = StringBuilder()

    collect { delta ->
        if (delta.isEmpty()) return@collect

        // Normalize CRLF / CR to LF
        sb.append(delta.replace("\r\n", "\n").replace("\r", "\n"))

        while (true) {
            val newlineIndex = sb.indexOf('\n')
            if (newlineIndex < 0) break

            val line = sb.substring(0, newlineIndex)
            emit(if (emitTrailingNewline) "$line\n" else line)
            delay(delayMillis)

            // Remove emitted line + newline
            sb.delete(0, newlineIndex + 1)
        }
    }

    if (flushRemainderOnComplete && sb.isNotEmpty()) {
        emit(sb.toString())
        delay(delayMillis)
    }
}

private fun StringBuilder.indexOf(ch: Char): Int {
    for (i in 0 until length) if (this[i] == ch) return i
    return -1
}

/**
 * Stream text as "tokens" suitable for Markdown:
 * - Emits markdown markers like **, ##, `, ``` as atomic tokens
 * - Emits \n as its own token (important for headings/lists/tables)
 * - Emits normal words/text runs between separators
 * - Adds delay after each emit
 *
 * This prevents breaking Markdown syntax mid-stream.
 */
fun Flow<String>.deltasToMarkdownTokensWithDelay(
    delayMillis: Long = 16L,
    flushRemainderOnComplete: Boolean = true
): Flow<String> = flow {
    val sb = StringBuilder()

    // Prefer longer tokens first to avoid splitting (e.g., "```" before "`", "**" before "*")
    val specials = listOf(
        "```", "**", "__", "~~", // strong/emphasis/strike/code fence
        "#######", "######", "#####", "####", "###", "##", "#", // headings
        "\n", "\t", " ",         // whitespace (newline emitted as-is; spaces/tabs normalized)
        "`", ">", "-", "*", "+", // common markdown list/quote markers
        "|",                     // tables
        "[", "]", "(", ")",      // links
        "!"                      // image/link prefix
    )

    fun StringBuilder.startsWithToken(token: String): Boolean {
        if (length < token.length) return false
        for (i in token.indices) if (this[i] != token[i]) return false
        return true
    }

    fun StringBuilder.consumeSpecialOrNull(): String? {
        for (t in specials) {
            // Don't emit a partial special token; wait for more input
            if (startsWithToken(t)) {
                delete(0, t.length)
                return t
            }
        }
        return null
    }

    fun StringBuilder.consumeTextRunOrNull(): String? {
        if (isEmpty()) return null

        // If the buffer begins with a special token, don't consume text
        if (specials.any { startsWithToken(it) }) return null

        // Consume until we reach a special token boundary
        var i = 0
        while (i < length) {
            val hit = specials.any { token ->
                // check boundary at i
                if (i + token.length > length) false
                else {
                    var ok = true
                    for (k in token.indices) {
                        if (this[i + k] != token[k]) {
                            ok = false; break
                        }
                    }
                    ok
                }
            }
            if (hit) break
            i++
        }

        // If we consumed all and there's no boundary, this may be incomplete; wait for more
        if (i == length) return null

        val run = substring(0, i)
        delete(0, i)
        return run
    }

    suspend fun emitToken(token: String) {
        // Normalize spaces/tabs to a single space; keep newlines as-is
        val out = when (token) {
            " ", "\t" -> " "
            else -> token
        }
        emit(out)
        delay(delayMillis)
    }

    collect { delta ->
        if (delta.isEmpty()) return@collect

        // Normalize CRLF/CR to LF so newline handling is consistent
        sb.append(delta.replace("\r\n", "\n").replace("\r", "\n"))

        while (true) {
            // 1) Prefer emitting specials (markdown markers/newlines) atomically
            val special = sb.consumeSpecialOrNull()
            if (special != null) {
                emitToken(special)
                continue
            }

            // 2) Otherwise emit a text run (word-like chunk between markers/whitespace)
            val run = sb.consumeTextRunOrNull()
            if (run != null) {
                emitToken(run)
                continue
            }

            // Need more input to decide (prevents breaking markers like "**" or "```")
            break
        }
    }

    if (flushRemainderOnComplete && sb.isNotEmpty()) {
        // At completion, emit whatever is left (even if incomplete markers)
        emit(sb.toString())
        delay(delayMillis)
    }
}


/**
 * Markdown-aware chunker:
 * - Parses deltas into markdown "tokens" (special markers + text runs)
 * - Batches tokens into chunks to reduce UI recompositions
 * - Flushes immediately on hard boundaries (newline/code fence/table/heading/list markers)
 * - Delays once per emitted chunk
 */
fun Flow<String>.deltasToMarkdownChunksWithDelay(
    delayMillis: Long = 60L,
    maxTokensPerChunk: Int = 12,
    maxCharsPerChunk: Int = 80,
    flushRemainderOnComplete: Boolean = true
): Flow<String> = flow {
    val sb = StringBuilder()

    // Prefer longer tokens first
    val specials = listOf(
        "```", "**", "__", "~~",
        "######", "#####", "####", "###", "##", "#",
        "\n",
        "`",
        "|",
        ">",
        "-",
        "*",
        "+",
        "[", "]", "(", ")", "!"
    )

    // Tokens that should force an immediate flush (keep Markdown stable)
    val hardBoundaries = setOf(
        "\n", "```", "|",
        "#", "##", "###", "####", "#####", "######",
        ">", "-", "*", "+"
    )

    fun StringBuilder.startsWithToken(token: String): Boolean {
        if (length < token.length) return false
        for (i in token.indices) if (this[i] != token[i]) return false
        return true
    }

    fun StringBuilder.consumeSpecialOrNull(): String? {
        for (t in specials) {
            if (startsWithToken(t)) {
                delete(0, t.length)
                return t
            }
        }
        return null
    }

    fun StringBuilder.consumeTextRunOrNull(): String? {
        if (isEmpty()) return null
        if (specials.any { startsWithToken(it) }) return null

        var i = 0
        while (i < length) {
            val hitBoundary = specials.any { token ->
                if (i + token.length > length) false
                else {
                    var ok = true
                    for (k in token.indices) {
                        if (this[i + k] != token[k]) {
                            ok = false; break
                        }
                    }
                    ok
                }
            }
            if (hitBoundary) break
            i++
        }

        // If no boundary found yet, wait for more (prevents partial token mistakes)
        if (i == length) return null

        val run = substring(0, i)
        delete(0, i)
        return run
    }

    fun normalizeToken(token: String): String =
        when (token) {
            "\t", " " -> " " // normalize whitespace into a space; we do not treat space as special
            else -> token
        }

    suspend fun emitChunkIfNeeded(chunk: StringBuilder) {
        if (chunk.isEmpty()) return
        emit(chunk.toString())
        delay(delayMillis)
        chunk.clear()
    }

    val chunk = StringBuilder()
    var tokenCount = 0

    fun wouldOverflow(next: String): Boolean =
        tokenCount >= maxTokensPerChunk || (chunk.length + next.length) > maxCharsPerChunk

    collect { delta ->
        if (delta.isEmpty()) return@collect

        // Normalize CRLF/CR into LF
        sb.append(delta.replace("\r\n", "\n").replace("\r", "\n"))

        while (true) {
            val special = sb.consumeSpecialOrNull()
            val tokenRaw = special ?: sb.consumeTextRunOrNull() ?: break

            val token = normalizeToken(tokenRaw)

            // If token is a hard boundary, flush current chunk BEFORE adding it
            if (tokenRaw in hardBoundaries) {
                emitChunkIfNeeded(chunk)
                chunk.append(token)
                tokenCount = 1
                // Hard boundary should flush immediately too (keeps rendering stable)
                emitChunkIfNeeded(chunk)
                tokenCount = 0
                continue
            }

            // For normal text runs / soft tokens: append until reaching chunk limits
            if (wouldOverflow(token)) {
                emitChunkIfNeeded(chunk)
                tokenCount = 0
            }

            chunk.append(token)
            tokenCount++
        }

        // Heuristic: if we collected a decent chunk and stream is fast, emit periodically
        if (chunk.length >= maxCharsPerChunk) {
            emitChunkIfNeeded(chunk)
            tokenCount = 0
        }
    }

    if (flushRemainderOnComplete) {
        // flush whatever is left in chunk first
        emitChunkIfNeeded(chunk)

        // emit any leftover buffer (even if incomplete)
        if (sb.isNotEmpty()) {
            emit(sb.toString())
            delay(delayMillis)
        }
    } else {
        emitChunkIfNeeded(chunk)
    }
}
