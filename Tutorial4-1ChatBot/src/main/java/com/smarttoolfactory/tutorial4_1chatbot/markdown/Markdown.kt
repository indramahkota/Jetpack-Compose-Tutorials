package com.smarttoolfactory.tutorial4_1chatbot.markdown

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.markdown.node.AstCode
import com.halilibo.richtext.markdown.node.AstEmphasis
import com.halilibo.richtext.markdown.node.AstHardLineBreak
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstLink
import com.halilibo.richtext.markdown.node.AstLinkReferenceDefinition
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstNodeType
import com.halilibo.richtext.markdown.node.AstSoftLineBreak
import com.halilibo.richtext.markdown.node.AstStrikethrough
import com.halilibo.richtext.markdown.node.AstStrongEmphasis
import com.halilibo.richtext.markdown.node.AstText
import com.halilibo.richtext.ui.string.InlineContent
import com.halilibo.richtext.ui.string.RichTextString
import com.halilibo.richtext.ui.string.withFormat

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
