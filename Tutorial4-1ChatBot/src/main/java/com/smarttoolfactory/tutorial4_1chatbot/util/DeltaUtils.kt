package com.smarttoolfactory.tutorial4_1chatbot.util

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.RichTextThemeProvider
import com.smarttoolfactory.tutorial4_1chatbot.markdown.MarkdownComposer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Preview that demonstrates streaming-safe markdown chunking.
 *
 */
@Preview(showBackground = true, widthDp = 420)
@Composable
fun MarkdownTokenStreamPreview() {

    // Longer, more varied simulated SSE deltas.
    // These intentionally split paired markdown delimiters so your tokenizer must WAIT until closed.
    val deltas = remember {
        flowOf(
            "# Ti", "tle: Strea", "ming Mark", "down\n\n",

            "## Sec", "tion A — Emph", "asis\n\n",

            "This para", "graph contains ",
            "**bo", "ld**", ", ",
            "__bo", "ld-alt__", ", ",
            "~~str", "ike~~", ", and ",
            "`inli", "neCode()", "`.\n\n",

            "It also contains mixed emphasis like ",
            "**bold with _ita", "lic inside_**",
            " and ",
            "__bold with *ita", "lic inside*__",
            ".\n\n",

            "### Incom", "plete spans should never show mid-stre", "am\n",
            "You should not see ",
            "**par", "t, ",
            "__par", "t, ",
            "~~par", ", or ",
            "`par",
            " in the rendered output until they close.\n\n",

            "---\n\n",

            "## Sec", "tion B — Li", "sts\n\n",

            "- First bullet with ", "**bo", "ld**", "\n",
            "- Second bullet with ", "_ita", "lic_", "\n",
            "- Third bullet with ", "~~str", "ike~~", " and ", "`co", "de`", "\n",
            "  - Nested bullet with ", "**bold _and ita", "lic_**", "\n",
            "  - Nested bullet with ", "__double underscore bo", "ld__", "\n\n",

            "1. First ordered i", "tem\n",
            "2. Second ordered item with ", "**bo", "ld**", "\n",
            "3. Third ordered item with ", "_ita", "lic_", " and ", "`co", "de`", "\n\n",

            "> Blockquote line one with ", "**bo", "ld**", "\n",
            "> Blockquote line two with ", "_ita", "lic_", "\n\n",

            "---\n\n",

            "## Sec", "tion C — Ta", "ble\n\n",

            "| Feature | Example | Notes |\n",
            "|--------:|:--------|:------|\n",

            "| Bold    | ", "**Hel", "lo**", " | waits until closed |\n",
            "| Italic  | ", "_Wor", "ld_", " | safe streaming |\n",
            "| Strike  | ", "~~Do", "ne~~", " | safe streaming |\n",
            "| Code    | ", "`val x ", "= 1`", " | waits until closing backtick |\n",
            "| Mixed   | ", "**A _B", "_ C**", " | nested emphasis |\n\n",

            "---\n\n",

            "## Sec", "tion D — More headers & te", "xt\n\n",

            "#### Subheading Level ", "4\n",
            "Some trailing text with ",
            "**fi", "nal bo", "ld**",
            " and ",
            "_fi", "nal ita", "lic_",
            ".\n\n",

            "###### Subheading Level ", "6\n",
            "End.\n"
        )
    }

    // Build the visible text incrementally by appending emitted chunks.
    var rendered by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // Reset each time preview recomposes (so it replays deterministically)
        rendered = ""

        deltas
            .deltasToWordStableMarkdownTokensWithDelay(
                delayMillis = 60
            )
//            .deltasToMarkdownTokensWithDelay(
//                delayMillis = 60
//            )
            .collect {
                println("Text: $it")
                rendered += it
            }
    }

    Column(
        Modifier
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        RichTextThemeProvider(
            // Overrides every other style in BasicRichText
            textStyleProvider = {
                TextStyle.Default.copy(
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
            }
        ) {
            BasicRichText(
                modifier = Modifier,
                style = RichTextStyle.Default
            ) {
                MarkdownComposer(markdown = rendered)
            }

//        BasicRichText(
//            modifier = Modifier,
//            style = RichTextStyle.Default
//        ) {
//            Markdown(rendered)
//        }
        }
    }
}

/**
 * Stream as WORD-STABLE tokens for trail-rect animations.
 *
 * Guarantees:
 * - Emits full "word + optional single space" tokens (outside of markdown spans).
 * - Emits completed markdown spans as ONE atomic token:
 *      **...**, __...__, ~~...~~, `...`
 * - Newlines are emitted as their own token.
 *
 * Design goal:
 * - Prevent reflow surprises for rectangle-based animations by NOT emitting words that might later
 *   become bold/italic/code due to an unfinished opening delimiter upstream.
 */
fun Flow<String>.deltasToWordStableMarkdownTokensWithDelay(
    delayMillis: Long = 16L,
    flushRemainderOnComplete: Boolean = true,
    normalizeSpaces: Boolean = true,
    // Attach one trailing space to the previous word token to reduce token count/recomposition
    attachSingleTrailingSpace: Boolean = true,
    // If true, keep triple fences as STRICT blocks (buffer until closing ```), otherwise treat ``` literally.
    strictTripleBackticks: Boolean = true,
    // Budget: max number of emitted word tokens per incoming delta flush.
    maxWordTokensPerFlush: Int = Int.MAX_VALUE,
    // Budget: max number of emitted completed spans per incoming delta flush.
    maxSpansPerFlush: Int = Int.MAX_VALUE,
): Flow<String> = flow {

    val sb = StringBuilder()

    fun normalizeIn(text: String): String {
        if (!normalizeSpaces) return text
        // normalize tabs to space; keep \n as-is
        return buildString(text.length) {
            for (c in text) append(if (c == '\t') ' ' else c)
        }
    }

    suspend fun emitToken(token: String) {
        if (token.isEmpty()) return
        emit(token)
        delay(delayMillis)
    }

    // We support these paired spans as atomic units
    val pairedDelims = listOf("```", "**", "__", "~~", "`")

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

    /**
     * Consume:
     * - newline as "\n"
     * - completed paired span as one token (e.g., "**bold**")
     * - otherwise, a full word token (optionally with ONE trailing space)
     *
     * Returns null if we need more input (e.g., buffer begins with an opening delimiter but no closing yet).
     */
    data class Out(val token: String, val isSpan: Boolean)

    fun StringBuilder.consumeOneWordStableOrNull(): Out? {
        if (isEmpty()) return null

        // 1) newline atomic
        if (this[0] == '\n') {
            delete(0, 1)
            return Out("\n", isSpan = false)
        }

        // 2) collapse leading spaces into at most one space token,
        //    BUT: for trail-rects you usually want spaces attached to words; we still handle leading spaces.
        if (this[0] == ' ') {
            var i = 0
            while (i < length && this[i] == ' ') i++
            delete(0, i)
            return Out(" ", isSpan = false)
        }

        // 3) if starts with paired delimiter, only emit if span completes (STRICT).
        //    This prevents "**" + "bo" + "ld" + "**" emissions.
        for (delim in pairedDelims) {
            if (!startsWithToken(delim)) continue

            // Special-case triple fence behavior
            if (delim == "```" && !strictTripleBackticks) {
                // treat literally
                delete(0, 3)
                return Out("```", isSpan = false)
            }

            val start = delim.length
            val endIdx = indexOfToken(delim, start)
            if (endIdx == -1) {
                // Wait: do NOT emit delimiter or inner text yet
                return null
            }

            val endExclusive = endIdx + delim.length
            val span = substring(0, endExclusive)
            delete(0, endExclusive)
            return Out(span, isSpan = true)
        }

        // 4) Not starting with delimiter: consume a full word token.
        //    Word token ends at whitespace/newline OR at a delimiter boundary.
        var i = 0
        while (i < length) {
            val ch = this[i]
            if (ch == '\n' || ch == ' ') break

            // stop before delimiter boundary so spans stay atomic
            var hitsDelim = false
            for (delim in pairedDelims) {
                val canCheck = i + delim.length <= length
                if (!canCheck) continue
                var ok = true
                for (k in delim.indices) {
                    if (this[i + k] != delim[k]) {
                        ok = false
                        break
                    }
                }
                if (ok) {
                    hitsDelim = true
                    break
                }
            }
            if (hitsDelim) break

            i++
        }

        if (i == 0) return null

        val word = substring(0, i)
        delete(0, i)

        if (attachSingleTrailingSpace && isNotEmpty() && this[0] == ' ') {
            // consume ALL following spaces but emit ONE (normalization)
            var j = 0
            while (j < length && this[j] == ' ') j++
            delete(0, j)
            return Out(word + " ", isSpan = false)
        }

        return Out(word, isSpan = false)
    }

    collect { delta ->
        if (delta.isEmpty()) return@collect
        sb.append(normalizeIn(delta).replace("\r\n", "\n").replace("\r", "\n"))

        var wordBudget = maxWordTokensPerFlush
        var spanBudget = maxSpansPerFlush

        while (true) {
            val out = sb.consumeOneWordStableOrNull() ?: break

            if (out.isSpan) {
                if (spanBudget <= 0) {
                    // put back
                    sb.insert(0, out.token)
                    break
                }
                spanBudget--
                // Note: spans may contain multiple words; for rects you likely animate them as one block.
                emitToken(out.token)
                continue
            }

            if (wordBudget <= 0) {
                sb.insert(0, out.token)
                break
            }
            wordBudget--
            emitToken(out.token)
        }
    }

    if (flushRemainderOnComplete && sb.isNotEmpty()) {
        // If remainder begins with an unclosed delimiter, emitting it can still cause reflow.
        // For rect-accuracy, you may prefer flushRemainderOnComplete=false.
        emitToken(sb.toString())
    }
}


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
