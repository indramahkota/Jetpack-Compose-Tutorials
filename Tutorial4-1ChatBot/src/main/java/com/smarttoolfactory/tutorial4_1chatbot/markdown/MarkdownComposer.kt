package com.smarttoolfactory.tutorial4_1chatbot.markdown

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.AstBlockNodeComposer
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.markdown.node.AstBlockNodeType
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstParagraph
import com.halilibo.richtext.markdown.node.AstTableRoot
import com.halilibo.richtext.ui.RichTextScope
import com.smarttoolfactory.tutorial4_1chatbot.samples.CustomTable
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.LineSegmentation

/**
 * Build a stable key for the current node based on its position in the AST tree.
 * This survives markdown re-parses as long as this paragraph's relative position doesn't change.
 */
private fun AstNode.stablePathKey(): String {
    val indices = ArrayDeque<Int>()
    var current: AstNode? = this

    while (current != null) {
        var i = 0
        var prev = current.links.previous
        while (prev != null) {
            i++
            prev = prev.links.previous
        }
        indices.addFirst(i)
        current = current.links.parent
    }

    val typeName = this.type::class.simpleName ?: "node"
    return "$typeName:${indices.joinToString(separator = "/")}"
}

@Composable
internal fun MarkdownComposer(
    markdown: String,
    debug: Boolean = false,
    animate: Boolean = true,
    messageKey: String? = null,
    segmentation: LineSegmentation = LineSegmentation.None,
    onCompleted: () -> Unit = {}
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
                val isTable = astBlockNodeType == AstTableRoot
                val isText = astBlockNodeType == AstParagraph
                return isTable || isText
            }

            @Composable
            override fun RichTextScope.Compose(
                astNode: AstNode,
                visitChildren: @Composable ((AstNode) -> Unit)
            ) {

                if (animate) {
                    val revealStore = LocalRevealStore.current

                    val localFallbackStarts = remember { mutableStateMapOf<String, Int>() }
                    val localFallbackCompleted = remember { mutableStateMapOf<String, Boolean>() }

                    val startIndexByNodeKey =
                        revealStore.startIndexByNodeKey.ifEmpty { localFallbackStarts }
                    val completedByNodeKey =
                        revealStore.completedByNodeKey.ifEmpty { localFallbackCompleted }

                    val rawNodeKey = remember(astNode) { astNode.stablePathKey() }

                    val nodeKey = if (messageKey != null) {
                        "$messageKey|$rawNodeKey"
                    } else {
                        rawNodeKey
                    }

                    println("Composer $messageKey, rawNodeKey: $rawNodeKey")

                    val startIndexForNode = startIndexByNodeKey[nodeKey] ?: 0
                    val alreadyCompleted = completedByNodeKey[nodeKey] == true
                    val shouldAnimate = !alreadyCompleted

                    if (astNode.type is AstTableRoot) {
                        CustomTable(tableRoot = astNode)
                    } else if (astNode.type is AstParagraph) {
                        MarkdownFadeInRichText(
                            modifier = Modifier.border(
                                2.dp,
                                if (shouldAnimate) Color.Cyan else Color.Magenta
                            ),
                            astNode = astNode,
                            segmentation = segmentation,
                            debug = debug,
                            startIndex = startIndexForNode,
                            onStartIndexChange = { newStart ->
                                val old = startIndexByNodeKey[nodeKey] ?: 0
                                startIndexByNodeKey[nodeKey] = maxOf(old, newStart)
                            },
                            onCompleted = {
                                completedByNodeKey[nodeKey] = true
                                onCompleted()
                            },
                            animate = shouldAnimate
                        )
                    }
                } else {
                    // ✅ untouched non-animated path
                    if (astNode.type is AstTableRoot) {
                        CustomTable(tableRoot = astNode)
                    } else if (astNode.type is AstParagraph) {
                        Box(Modifier.border(2.dp, Color(0xff546E7A))) {
                            BasicMarkdown(astNode)
                        }
                    }
                }
            }
        }
    }

    astRootNode?.let { astNode ->
        RichTextScope.BasicMarkdown(astNode, tableBlockNodeComposer)
    }
}
