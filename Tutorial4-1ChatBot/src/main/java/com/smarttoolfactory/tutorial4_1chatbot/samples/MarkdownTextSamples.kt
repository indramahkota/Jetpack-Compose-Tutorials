package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.BlockQuote
import com.halilibo.richtext.ui.CodeBlock
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.Heading
import com.halilibo.richtext.ui.HorizontalRule
import com.halilibo.richtext.ui.InfoPanel
import com.halilibo.richtext.ui.InfoPanelType
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.RichTextThemeProvider
import com.halilibo.richtext.ui.Table
import com.halilibo.richtext.ui.string.RichTextStringStyle

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
fun MarkdownComponentPreview() {
    BasicRichText(
        modifier = Modifier.background(color = Color.White)
    ) {
        Heading(0, "Paragraphs")
        Text("Simple paragraph.")
        Text("Paragraph with\nmultiple lines.")
        Text("Paragraph with really long line that should be getting wrapped.")

        Heading(0, "Lists")
        Heading(1, "Unordered")
        Heading(1, "Ordered")

        Heading(0, "Horizontal Line")
        Text("Above line")
        HorizontalRule()
        Text("Below line")

        Heading(0, "Code Block")
        CodeBlock(
            """
      {
        "Hello": "world!"
      }
    """.trimIndent()
        )

        Heading(0, "Block Quote")
        BlockQuote {
            Text("These paragraphs are quoted.")
            Text("More text.")
            BlockQuote {
                Text("Nested block quote.")
            }
        }

        Heading(0, "Info Panel")
        InfoPanel(InfoPanelType.Primary, "Only text primary info panel")
        InfoPanel(InfoPanelType.Success) {
            Column {
                Text("Successfully sent some data")
                HorizontalRule()
                BlockQuote {
                    Text("This is a quote")
                }
            }
        }

        Heading(0, "Table")
        Table(headerRow = {
            cell { Text("Column 1") }
            cell { Text("Column 2") }
        }) {
            row {
                cell { Text("Hello") }
                cell {
                    CodeBlock("Foo bar")
                }
            }
            row {
                cell {
                    BlockQuote {
                        Text("Stuff")
                    }
                }
                cell { Text("Hello world this is a really long line that is going to wrap hopefully") }
            }
        }
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
                            fontSize = 32.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 36.sp
                        )
                        2 -> textStyle.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 34.sp
                        )
                        3 -> textStyle.copy(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 32.sp
                        )
                        4 -> textStyle.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 30.sp
                        )
                        5 -> textStyle.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 26.sp
                        )
                        else -> textStyle.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp,
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
fun MarkdownStylePreview() {
    val parser: CommonmarkAstNodeParser = CommonmarkAstNodeParser()
    val astNode: AstNode = parser.parse(markdownText)

    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {

        Text(
            text = "Default Style",
            fontSize = 22.sp,
            color = Color.Red,
            fontWeight = FontWeight.SemiBold
        )
        BasicRichText(modifier = Modifier) {
            BasicMarkdown(astNode = astNode)
        }

        Text(
            text = "BasicRichText Style",
            fontSize = 22.sp,
            color = Color.Red,
            fontWeight = FontWeight.SemiBold
        )

        BasicRichText(
            modifier = Modifier.padding(vertical = 16.dp),
            style = RichTextStyle.Default
                .copy(
                    paragraphSpacing = 16.sp,
//                        listStyle = ListStyle(),
                    codeBlockStyle = CodeBlockStyle(
                        textStyle = TextStyle.Default.copy(color = Color.Blue)
                    ),
                    stringStyle = RichTextStringStyle(
                        boldStyle = SpanStyle(
                            color = Color.Magenta,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        italicStyle = SpanStyle(
                            color = Color.Cyan,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                )
        ) {
            Markdown(markdownText)
        }

        Text(
            text = "RichTextThemeProvider + \nBasicRichText Style",
            fontSize = 22.sp,
            color = Color.Red,
            fontWeight = FontWeight.SemiBold
        )
        RichTextThemeProvider(
//            // Overrides every other style in BasicRichText
//            textStyleProvider = {
//                TextStyle.Default.copy(
//                    fontSize = 18.sp,
//                    lineHeight = 22.sp
//                )
//            },
//            textStyleBackProvider = { style, content ->
//                ProvideTextStyle(style.copy(color = Color.Red)) {
//                    Box(modifier = Modifier.border(1.dp, Color.Green).padding(8.dp)) {
//                        content()
//                    }
//                }
//            },
//            contentColorProvider = {
//                // This is Text Color
//                Color(0xffFF8F00)
//            },
//            contentColorBackProvider = { color, content ->
//                Box(modifier = Modifier.border(3.dp, Color.Red)) {
//                    content()
//                }
//            }
        ) {

            BasicRichText(
                modifier = Modifier,
                style = RichTextStyle.Default
                    .copy(
                        paragraphSpacing = 16.sp,
                        codeBlockStyle = CodeBlockStyle(
                            textStyle = TextStyle.Default.copy(color = Color.Blue)
                        ),
                        headingStyle = { index, textStyle ->
                            when (index) {
                                1 -> textStyle.copy(
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 32.sp,
                                    color = Color(0xffF44336)
                                )

                                2 -> textStyle.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 28.sp,
                                    color = Color(0xff8E24AA)
                                )

                                3 -> textStyle.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 26.sp,
                                    color = Color(0xff1E88E5)
                                )

                                4 -> textStyle.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 24.sp,
                                    color = Color(0xff43A047)
                                )

                                5 -> textStyle.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 22.sp,
                                    color = Color(0xff546E7A)
                                )

                                else -> textStyle.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 20.sp,
                                    color = Color(0xff6D4C41)
                                )
                            }
                        },
                        stringStyle = RichTextStringStyle(
                            boldStyle = SpanStyle(
                                color = Color.Magenta,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    )
            ) {
                BasicMarkdown(astNode = astNode)
            }
        }

        RichTextThemeProvider(
            // Base paragraph style for EVERYTHING.
            // Keep it as your default paragraph size.
            textStyleProvider = {
                TextStyle(
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    color = Color.Red
                )
            },

            textStyleBackProvider = { style, content ->
                ProvideTextStyle(style) { content() }
            },

            contentColorProvider = { LocalContentColor.current },
            contentColorBackProvider = { color, content ->
                CompositionLocalProvider(LocalContentColor provides color) { content() }
            }
        ) {
            BasicRichText(
                style = RichTextStyle.Default.copy(
                    paragraphSpacing = 16.sp,

                    // Heading sizes override the base
                    headingStyle = { level, base ->
                        when (level) {
                            1 -> base.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
                            2 -> base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
                            3 -> base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                            4 -> base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
                            5 -> base.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                            else -> base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                        }
                    },

                    // Code blocks can have their own font size
                    codeBlockStyle = CodeBlockStyle(
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    ),

                    // Inline spans (bold/italic/code) can adjust size too
                    stringStyle = RichTextStringStyle(
                        boldStyle = SpanStyle(fontWeight = FontWeight.Bold),
                        codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    )
                )
            ) {
                BasicMarkdown(astNode = astNode)
            }
        }
    }
}
