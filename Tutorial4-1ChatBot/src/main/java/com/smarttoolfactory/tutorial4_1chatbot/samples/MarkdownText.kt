package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.RichTextThemeProvider
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin

val markdownText = """
    # Demo
    
    ## Demo
    
    ### Demo
    
    #### Demo
    
    ##### Demo
    
    ###### Demo

    Emphasis, aka italics, with *asterisks* or _underscores_. Strong emphasis, aka bold, with **asterisks** or __underscores__. Combined emphasis with **asterisks and _underscores_**. [Links with two blocks, text in square-brackets, destination is in parentheses.](https://www.example.com). Inline `code` has `back-ticks around` it.

    1. First ordered list item
    2. Another item
        * Unordered sub-list.
    3. And another item.
        You can have properly indented paragraphs within list items. Notice the blank line above, and the leading spaces (at least one, but we'll use three here to also align the raw Markdown).

    * Unordered list can use asterisks
    - Or minuses
    + Or pluses
    ---

    ```javascript
    var s = "code blocks use monospace font";
    alert(s);
    ```

    Markdown | Table | Extension
    --- | --- | ---
    *renders* | `beautiful images` | ![random image](https://picsum.photos/seed/picsum/400/400 "Text 1")
    1 | 2 | 3

    > Blockquotes are very handy in email to emulate reply text.
    > This line is part of the same quote.
    """.trimIndent()

val tableContent = """
<table>
  <tr>
    <th>Company</th>
    <th>Contact</th>
    <th>Country</th>
  </tr>
  <tr>
    <td>Alfreds Futterkiste</td>
    <td>Maria Anders</td>
    <td>Germany</td>
  </tr>
  <tr>
    <td>Centro comercial Moctezuma</td>
    <td>Francisco Chang</td>
    <td>Mexico</td>
  </tr>
</table>
    """.trimIndent()

@Preview
@Composable
fun MarkdownTextSample() {
    val parser: CommonmarkAstNodeParser = CommonmarkAstNodeParser()
    val astNode: AstNode = parser.parse(markdownText)

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Compose-RichText",
            fontSize = 28.sp,
            color = Color.Red,
            fontWeight = FontWeight.SemiBold
        )

        RichTextThemeProvider(
            // Overrides every other style in BasicRichText
//            textStyleProvider = {
//                TextStyle(color = Color.Red)
//            }
        ) {
            BasicRichText(
                modifier = Modifier.padding(vertical = 16.dp),
                style = RichTextStyle.Default
//                    .copy(
//                        headingStyle = { index, textStyle ->
//                            textStyle
//                        }
//                    )
            ) {
                Markdown(markdownText)
            }
        }


        BasicRichText(
            modifier = Modifier.padding(16.dp)
        ) {
            BasicMarkdown(astNode = astNode)
        }

        RichTextScope.BasicMarkdown(astNode)
    }
}

@Preview
@Composable
fun MarkDownHeaderStylePreview() {
    val text = """
        # Demo
        
        ## Demo
        
        ### Demo
        
        #### Demo
        
        ##### Demo
        
        ###### Demo
        This is **regular** text for reference
        
        This is __regular__ text for reference
    """.trimIndent()

    Column {

        Text("Default Header Style", fontSize = 20.sp)
        BasicRichText(
            modifier = Modifier.padding(vertical = 16.dp),
            style = RichTextStyle.Default
        ) {
            Markdown(text)
        }


        Text("Custom Header Style", fontSize = 20.sp)
        BasicRichText(
            modifier = Modifier.padding(vertical = 16.dp),
            style = RichTextStyle.Default.copy(
                headingStyle = { index, textStyle ->
                    when (index) {
                        1 -> textStyle.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 32.sp
                        )
                        2 -> textStyle.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 28.sp
                        )
                        3 -> textStyle.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 26.sp
                        )
                        4 -> textStyle.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 24.sp
                        )
                        5 -> textStyle.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp
                        )
                        else -> textStyle.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 20.sp
                        )
                    }
                }
            )
        ) {
            Markdown(text)
        }

    }
}


@Preview
@Composable
fun MarkWonTextPreview() {
    val context = LocalContext.current

    val markwon = Markwon.builder(context)
        .usePlugin(TablePlugin.create(context))
        .build()
}
