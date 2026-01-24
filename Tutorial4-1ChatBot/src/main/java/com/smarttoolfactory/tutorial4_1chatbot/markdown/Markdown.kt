package com.smarttoolfactory.tutorial4_1chatbot.markdown

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.AstBlockNodeComposer
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.markdown.node.AstBlockNodeType
import com.halilibo.richtext.markdown.node.AstCode
import com.halilibo.richtext.markdown.node.AstEmphasis
import com.halilibo.richtext.markdown.node.AstHardLineBreak
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstLink
import com.halilibo.richtext.markdown.node.AstLinkReferenceDefinition
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstNodeType
import com.halilibo.richtext.markdown.node.AstParagraph
import com.halilibo.richtext.markdown.node.AstSoftLineBreak
import com.halilibo.richtext.markdown.node.AstStrikethrough
import com.halilibo.richtext.markdown.node.AstStrongEmphasis
import com.halilibo.richtext.markdown.node.AstTableRoot
import com.halilibo.richtext.markdown.node.AstText
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.string.InlineContent
import com.halilibo.richtext.ui.string.RichTextString
import com.halilibo.richtext.ui.string.Text
import com.halilibo.richtext.ui.string.withFormat

@Composable
private fun CustomComposer(
    markdown: String,
    content: @Composable RichTextScope.(astNode: AstNode, visitChildren: @Composable ((AstNode) -> Unit)) -> Unit
) {
    val commonmarkAstNodeParser: CommonmarkAstNodeParser = remember {
        CommonmarkAstNodeParser()
    }

    val astRootNode by produceState<AstNode?>(
        initialValue = null,
        key1 = commonmarkAstNodeParser,
        key2 = markdown
    ) {
        value = commonmarkAstNodeParser.parse(markdown)
    }

    val tableBlockNodeComposer: AstBlockNodeComposer = remember {
        object : AstBlockNodeComposer {

            override fun predicate(astBlockNodeType: AstBlockNodeType): Boolean {
                // Intercept tables
                val isTable = astBlockNodeType == AstTableRoot
                // Intercept Text
                val isText = astBlockNodeType == AstParagraph
                println(
                    "isTable: $isTable, " +
                            "isText: $isText," +
                            " astBlockNodeType: $astBlockNodeType"
                )
                return isTable || isText
            }

            @Composable
            override fun RichTextScope.Compose(
                astNode: AstNode,
                visitChildren: @Composable ((AstNode) -> Unit)
            ) {
                if (astNode.type is AstTableRoot) {
                    content(astNode, visitChildren)
                } else if (astNode.type is AstParagraph) {
                    Box(modifier = Modifier.border(2.dp, Color.Green)) {
                        MarkdownRichText(astNode)
                    }
                }
            }
        }
    }

    astRootNode?.let { astNode ->
        RichTextScope.BasicMarkdown(astNode, tableBlockNodeComposer)
    }
}

@Composable
internal fun RichTextScope.MarkdownRichText(astNode: AstNode, modifier: Modifier = Modifier) {
    // Assume that only RichText nodes reside below this level.
    val richText: RichTextString = remember(astNode) {
        computeRichTextString(astNode)
    }

    Text(
        text = richText,
        modifier = modifier
    )
}

internal fun computeRichTextString(astNode: AstNode): RichTextString {
    val richTextStringBuilder = RichTextString.Builder()

    // Modified pre-order traversal with pushFormat, popFormat support.
    var iteratorStack = listOf(
        AstNodeTraversalEntry(
            astNode = astNode,
            isVisited = false,
            formatIndex = null
        )
    )

    while (iteratorStack.isNotEmpty()) {
        val (currentNode, isVisited, formatIndex) = iteratorStack.first().copy()
        iteratorStack = iteratorStack.drop(1)

        if (!isVisited) {
            val newFormatIndex = when (val currentNodeType = currentNode.type) {
                is AstCode -> {
                    richTextStringBuilder.withFormat(RichTextString.Format.Code) {
                        append(currentNodeType.literal)
                    }
                    null
                }

                is AstEmphasis -> richTextStringBuilder.pushFormat(RichTextString.Format.Italic)
                is AstStrikethrough -> richTextStringBuilder.pushFormat(
                    RichTextString.Format.Strikethrough
                )

                is AstImage -> {
                    richTextStringBuilder.appendInlineContent(
                        content = InlineContent(
                            initialSize = {
                                IntSize(128.dp.roundToPx(), 128.dp.roundToPx())
                            }
                        ) {
//                            MarkdownImage(
//                                url = currentNodeType.destination,
//                                contentDescription = currentNodeType.title,
//                                modifier = Modifier.fillMaxWidth(),
//                                contentScale = ContentScale.Inside
//                            )
                        }
                    )
                    null
                }

                is AstLink -> richTextStringBuilder.pushFormat(
                    RichTextString.Format.Link(
                        destination = currentNodeType.destination
                    )
                )

                is AstSoftLineBreak -> {
                    richTextStringBuilder.append(" ")
                    null
                }

                is AstHardLineBreak -> {
                    richTextStringBuilder.append("\n")
                    null
                }

                is AstStrongEmphasis -> richTextStringBuilder.pushFormat(RichTextString.Format.Bold)
                is AstText -> {
                    richTextStringBuilder.append(currentNodeType.literal)
                    null
                }

                is AstLinkReferenceDefinition -> richTextStringBuilder.pushFormat(
                    RichTextString.Format.Link(destination = currentNodeType.destination)
                )

                else -> null
            }

            iteratorStack = iteratorStack.addFirst(
                AstNodeTraversalEntry(
                    astNode = currentNode,
                    isVisited = true,
                    formatIndex = newFormatIndex
                )
            )

            // Do not visit children of terminals such as Text, Image, etc.
            if (!currentNode.isRichTextTerminal()) {
                currentNode.childrenSequence(reverse = true).forEach {
                    iteratorStack = iteratorStack.addFirst(
                        AstNodeTraversalEntry(
                            astNode = it,
                            isVisited = false,
                            formatIndex = null
                        )
                    )
                }
            }
        }

        if (formatIndex != null) {
            richTextStringBuilder.pop(formatIndex)
        }
    }

    return richTextStringBuilder.toRichTextString()
}

private data class AstNodeTraversalEntry(
    val astNode: AstNode,
    val isVisited: Boolean,
    val formatIndex: Int?
)

private inline fun <reified T> List<T>.addFirst(item: T): List<T> {
    return listOf(item) + this
}


internal fun AstNode.childrenSequence(
    reverse: Boolean = false
): Sequence<AstNode> {
    return if (!reverse) {
        generateSequence(this.links.firstChild) { it.links.next }
    } else {
        generateSequence(this.links.lastChild) { it.links.previous }
    }
}

/**
 * Markdown rendering is susceptible to have assumptions. Hence, some rendering rules
 * may force restrictions on children. So, valid children nodes should be selected
 * before traversing. This function returns a LinkedList of children which conforms to
 * [filter] function.
 *
 * @param filter A lambda to select valid children.
 */
internal fun AstNode.filterChildren(
    reverse: Boolean = false,
    filter: (AstNode) -> Boolean
): Sequence<AstNode> {
    return childrenSequence(reverse).filter(filter)
}

internal inline fun <reified T : AstNodeType> AstNode.filterChildrenType(): Sequence<AstNode> {
    return filterChildren { it.type is T }
}

/**
 * These ASTNode types should never have any children. If any exists, ignore them.
 */
internal fun AstNode.isRichTextTerminal(): Boolean {
    return type is AstText
            || type is AstCode
            || type is AstImage
            || type is AstSoftLineBreak
            || type is AstHardLineBreak
}


/** -------- AstNode helpers (links-based traversal) -------- */

private fun AstNode.children(): Sequence<AstNode> = sequence {
    var child = links.firstChild
    while (child != null) {
        yield(child)
        child = child.links.next
    }
}
