package com.smarttoolfactory.tutorial4_1chatbot.markdown

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.collections.set

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
    segmentation: LineSegmentation = LineSegmentation.None,
    onCompleted: () -> Unit = {},
    revealStore: RevealStore? = null,
    animate: Boolean = true
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

    /**
     * Persist startIndex OUTSIDE the node composable so it won't reset to 0 when:
     * - the markdown becomes valid (e.g. ** closes),
     * - AST is rebuilt,
     * - paragraph Text() composable gets recreated.
     *
     * IMPORTANT: DO NOT key this by markdown for streaming messages.
     * If revealStore is provided (LazyColumn), use that instead.
     */
    val localFallbackStarts = remember { mutableStateMapOf<String, Int>() }
    val localFallbackCompleted = remember { mutableStateMapOf<String, Boolean>() }

    val startIndexByNodeKey = revealStore?.startIndexByNodeKey ?: localFallbackStarts
    val completedByNodeKey = revealStore?.completedByNodeKey ?: localFallbackCompleted

    val tableBlockNodeComposer: AstBlockNodeComposer = remember {
        object : AstBlockNodeComposer {

            override fun predicate(astBlockNodeType: AstBlockNodeType): Boolean {
                // Intercept tables
                val isTable = astBlockNodeType == AstTableRoot
                // Intercept Text
                val isText = astBlockNodeType == AstParagraph
//                println(
//                    "isTable: $isTable, " +
//                            "isText: $isText," +
//                            " astBlockNodeType: $astBlockNodeType"
//                )
                return isTable || isText
            }

            @Composable
            override fun RichTextScope.Compose(
                astNode: AstNode,
                visitChildren: @Composable ((AstNode) -> Unit)
            ) {
                if (astNode.type is AstTableRoot) {
                    CustomTable(tableRoot = astNode)
                } else if (astNode.type is AstParagraph) {

                    val nodeKey = remember(astNode) { astNode.stablePathKey() }

                    val startIndexForNode = startIndexByNodeKey[nodeKey] ?: 0

//                    println("✅ nodeKey: $nodeKey, startIndex: $startIndexForNode")

                    var completedUi by remember { mutableStateOf(false) }

                    val alreadyCompleted = completedByNodeKey[nodeKey] == true
                    val shouldAnimate = animate && !alreadyCompleted

                    MarkdownFadeInRichText(
                        modifier = Modifier.border(
                            2.dp,
                            if (completedUi || alreadyCompleted) Color.Magenta else Color.Cyan
                        ),
                        astNode = astNode,
                        segmentation = segmentation,
                        debug = debug,
                        startIndex = startIndexForNode,
                        onStartIndexChange = { newStart ->
                            // monotonic to avoid regressions
                            val old = startIndexByNodeKey[nodeKey] ?: 0
                            startIndexByNodeKey[nodeKey] = maxOf(old, newStart)
                        },
                        onCompleted = {
                            completedByNodeKey[nodeKey] = true
                            completedUi = true
                            onCompleted()
                        },
                        animate = shouldAnimate
                    )
                }
            }
        }
    }

    astRootNode?.let { astNode ->
        RichTextScope.BasicMarkdown(astNode, tableBlockNodeComposer)
    }
}
