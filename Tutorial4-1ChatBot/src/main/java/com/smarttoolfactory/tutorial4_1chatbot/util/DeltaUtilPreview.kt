package com.smarttoolfactory.tutorial4_1chatbot.util

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextThemeProvider
import com.smarttoolfactory.tutorial4_1chatbot.markdown.MarkdownComposer
import com.smarttoolfactory.tutorial4_1chatbot.markdown.MarkDownStyle
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
        markdownDeltaTestFlow()
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

            var completed1 by remember {
                mutableStateOf(false)
            }

            Text(
                text = "TrailFadeInTextWithCallback",
                fontSize = 18.sp,
                color = if (completed1) Color.Green else Color.Red
            )

            BasicRichText(
                modifier = Modifier,
                style = MarkDownStyle.DefaultTextStyle
            ) {

                MarkdownComposer(
                    markdown = rendered,
                    debug = true,
                    onCompleted = {
                        completed1 = true
                    }
                )
            }
        }
    }
}

private fun markdownDeltaTestFlow(): Flow<String> = flowOf(
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

fun markdownDeltaTestFlow3(): Flow<String> = flow {
    val deltas = listOf(
        "`inline", "Code()", "`.\n\n",
        "Mixed nesting should be atomic:\n",
        "- ", "**bold with _ita", "lic inside_**", "\n",
    )

    for (d in deltas) emit(d)
}

/**
 * Emits the given markdown as "SSE-like" deltas that intentionally split inside markdown constructs:
 * - splits inside **bold**, _italic_, `code`, ~~strike~~
 * - splits around list/table markers
 * - includes a few 1–3 char micro-deltas to stress buffering logic
 *
 * Use this to validate:
 * - your tokenizer never emits bare delimiters (** / _ / ` / ~~)
 * - your innerRange trimming excludes delimiters
 * - your rects stay stable as markdown seals
 */
fun markdownDeltaTestFlow2(): Flow<String> = flow {
    val deltas = listOf(
        "# Markdown Rect Trim & Inner-Range Test\n\n",

        "This para", "graph contains ",
        "**bo", "ld**", ", ",
        "__bo", "ld-alt__", ", ",
        "*ita", "lic*", ", ",
        "_ita", "lic-alt_", ", ",
        "~~str", "ike~~", ", and ",
        "`inline", "Code()", "`.\n\n",
        "Mixed nesting should be atomic:\n",
        "- ", "**bold with _ita", "lic inside_**", "\n",
        "- ", "__bold with *ita", "lic inside*__", "\n",
        "- ", "**bold with `inline ", "code` inside**", "\n",
        "- ", "*italic with **bo", "ld** inside*", "\n\n",

        "Punctuation adjacency:\n",
        "**bold", ",** ", "_ita", "lic._ ", "~~stri", "ke!~~ ", "`co", "de?`", "\n\n",

        "Whitespace padding:\n",
        " ** ", "bold with spa", "ces ", "** ", "\n",
        "__   ", "bold-alt with spa", "ces   ", "__\n\n",

        "---\n\n",

        "## Incomplete spans (must not emit partial rects)\n\n",
        "You should not see ", "**pa", "rt\n",
        "You should not see ", "__pa", "rt\n",
        "You should not see ", "~~pa", "r\n",
        "You should not see ", "`pa", "r\n",
        "These should fail-open at newline but never emit bare delimiters.\n\n",

        "---\n\n",

        "## Lists\n\n",
        "- First bullet with ", "**bo", "ld**", "\n",
        "- Second bullet with ", "_ita", "lic_", "\n",
        "- Third bullet with ", "~~str", "ike~~", " and ", "`co", "de`", "\n",
        "- Bullet with ", "**nested _ita", "lic_ and `co", "de`**", "\n\n",

        "1. Ordered item with ", "**bo", "ld**", "\n",
        "2. Ordered item with ", "_ita", "lic_", "\n",
        "3. Ordered item with ", "`co", "de`", "\n\n",

        "---\n\n",

        "## Blockquotes\n\n",
        "> Blockquote with ", "**bo", "ld**", "\n",
        "> Blockquote with ", "_ita", "lic_", "\n",
        "> Blockquote with ", "`co", "de`", "\n\n",

        "---\n\n",

        "## Links & Images (inner text only)\n\n",
        "A link: ", "[Click **he", "re**](https://example.com)\n",
        "An image: ", "![Alt **te", "xt**](https://example.com/image.png)\n\n",
        "Nested:\n",
        "[**Bold link _te", "xt_**](https://example.com)\n\n",

        "---\n\n",

        "## Tables (expect reflow)\n\n",
        "| Feature | Example | Notes |\n",
        "|--------:|:--------|:------|\n",
        "| Bold    | ", "**Hel", "lo**", " | waits until closed |\n",
        "| Italic  | ", "_Wor", "ld_", " | safe streaming |\n",
        "| Strike  | ", "~~Do", "ne~~", " | safe streaming |\n",
        "| Code    | ", "`val ", "x = 1`", " | waits until closing backtick |\n",
        "| Mixed   | ", "**A _B", "_ C**", " | nested emphasis |\n\n",

        "---\n\n",

        "## Headings with inline spans\n\n",
        "### Heading with ", "**bo", "ld**", "\n",
        "#### Heading with ", "_ita", "lic_", " and ", "`co", "de`", "\n",
        "##### Heading with ", "**nested _ita", "lic_**", "\n\n",

        "---\n\n",

        "## Final edge cases\n\n",
        "Parentheses: (", "**bo", "ld**", ")\n",
        "Quotes: \"", "**bo", "ld**", "\"\n",
        "Underscores in words should NOT be emphasis: foo_bar_baz\n\n",
        "End.\n"
    )

    for (d in deltas) emit(d)
}

val markdownTextString = """
//# Markdown Rect Trim & Inner-Range Test
//
//This paragraph contains **bold**, __bold-alt__, *italic*, _italic-alt_, ~~strike~~, and `inlineCode()`.
//
Mixed nesting should be atomic:
- **bold with _italic inside_**
//- __bold with *italic inside*__
//- **bold with `inline code` inside**
//- *italic with **bold** inside*
//
//Punctuation adjacency:
//**bold,** _italic._ ~~strike!~~ `code?`
//
//Whitespace padding:
// ** bold with spaces ** 
//__   bold-alt with spaces   __
//
//---
//
//## Incomplete spans (must not emit partial rects)
//
//You should not see **part
//You should not see __part
//You should not see ~~par
//You should not see `par
//These should fail-open at newline but never emit bare delimiters.
//
//---
//
//## Lists
//
//- First bullet with **bold**
//- Second bullet with _italic_
//- Third bullet with ~~strike~~ and `code`
//- Bullet with **nested _italic_ and `code`**
//
//1. Ordered item with **bold**
//2. Ordered item with _italic_
//3. Ordered item with `code`
//
//---
//
//## Blockquotes
//
//> Blockquote with **bold**
//> Blockquote with _italic_
//> Blockquote with `code`
//
//---
//
//## Links & Images (inner text only)
//
//A link: [Click **here**](https://example.com)
//An image: ![Alt **text**](https://example.com/image.png)
//
//Nested:
//[**Bold link _text_**](https://example.com)
//
//---
//
//## Tables (expect reflow)
//
//| Feature | Example | Notes |
//|--------:|:--------|:------|
//| Bold    | **Hello** | waits until closed |
//| Italic  | _World_ | safe streaming |
//| Strike  | ~~Done~~ | safe streaming |
//| Code    | `val x = 1` | waits until closing backtick |
//| Mixed   | **A _B_ C** | nested emphasis |
//
//---
//
//## Headings with inline spans
//
//### Heading with **bold**
//#### Heading with _italic_ and `code`
//##### Heading with **nested _italic_**
//
//---
//
//## Final edge cases
//
//Parentheses: (**bold**)
//Quotes: "**bold**"
//Underscores in words should NOT be emphasis: foo_bar_baz
//
//End.

""".trimIndent()