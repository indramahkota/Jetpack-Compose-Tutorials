package com.smarttoolfactory.tutorial4_1chatbot.markdown

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.AstBlockNodeComposer
import com.halilibo.richtext.markdown.BasicMarkdown
import com.halilibo.richtext.markdown.node.AstBlockNodeType
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstParagraph
import com.halilibo.richtext.markdown.node.AstTableRoot
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.string.RichTextString
import com.halilibo.richtext.ui.string.Text
import com.smarttoolfactory.tutorial4_1chatbot.samples.CustomTable
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.LineSegmentation
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.RectWithAnimation
import com.smarttoolfactory.tutorial4_1chatbot.samples.rectUtils.calculateBoundingRectList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    animate: Boolean = false,
    segmentation: LineSegmentation = LineSegmentation.None
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
     */
    val startIndexByNodeKey = remember(markdown) {
        mutableStateMapOf<String, Int>()
    }

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

                    val startIndexForNode = startIndexByNodeKey[nodeKey] ?: -1

                    println("nodeKey: $nodeKey, startIndex: $startIndexForNode")

                    MarkdownFadeInRichText(
                        astNode = astNode,
                        segmentation = segmentation,
                        debug = debug,
                        startIndex = startIndexForNode,
                        onStartIndexChange = { newStart ->
                            startIndexByNodeKey[nodeKey] = newStart
                        }
                    )
                }
            }
        }
    }

    astRootNode?.let { astNode ->
        RichTextScope.BasicMarkdown(astNode, tableBlockNodeComposer)
    }
}

@Composable
internal fun RichTextScope.MarkdownFadeInRichText(
    astNode: AstNode,
    modifier: Modifier = Modifier,
    delayInMillis: Long = 90L,
    revealCoefficient: Float = 4f,
    lingerInMillis: Long = 90L,
    segmentation: LineSegmentation = LineSegmentation.None,
    debug: Boolean = true
) {

    var startIndex by remember {
        mutableIntStateOf(0)
    }

    MarkdownFadeInRichText(
        astNode = astNode,
        modifier = modifier,
        delayInMillis = delayInMillis,
        revealCoefficient = revealCoefficient,
        lingerInMillis = lingerInMillis,
        segmentation = segmentation,
        debug = debug,
        startIndex = startIndex,
        onStartIndexChange = {
            startIndex = it
        }
    )
}

@Composable
internal fun RichTextScope.MarkdownFadeInRichText(
    astNode: AstNode,
    modifier: Modifier = Modifier,
    delayInMillis: Long = 90L,
    revealCoefficient: Float = 4f,
    lingerInMillis: Long = 90L,
    segmentation: LineSegmentation = LineSegmentation.None,
    debug: Boolean = true,
    startIndex: Int,
    onStartIndexChange: (Int) -> Unit
) {
    // Assume that only RichText nodes reside below this level.
    val richText: RichTextString = remember(astNode) {
        computeRichTextString(astNode)
    }

    val rectList = remember { mutableStateListOf<RectWithAnimation>() }

    // Queue of rect batches coming from onTextLayout
    val rectBatchChannel = remember {
        Channel<List<RectWithAnimation>>(
            capacity = Channel.UNLIMITED
        )
    }

    // Track jobs so each rect starts once, and can optionally clean up.
    val jobsByRectId = remember { mutableStateMapOf<String, Job>() }

    // One long-lived "dispatcher" coroutine; does not restart on new layouts.
    LaunchedEffect(Unit) {
        for (batch in rectBatchChannel) {
            batch.forEachIndexed { index, rectWithAnimation ->
                // If you might enqueue the same instance again, guard it.
                val id = rectWithAnimation.id
                if (jobsByRectId.containsKey(id)) return@forEachIndexed

                val job = launch {
                    // Optional stagger, keeps parallel but slightly cascaded.
                    delay(delayInMillis * index)
                    val duration = 1000
//                    val duration = (revealCoefficient * rectWithAnimation.rect.width).toInt()

                    try {
                        rectWithAnimation.animatable.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(duration, easing = LinearEasing)
                        )
                        delay(lingerInMillis)
                    } finally {
                        if (debug.not()) {
                            rectList.remove(rectWithAnimation)
                        }
                        jobsByRectId.remove(id)
                    }
                }

                jobsByRectId[id] = job
            }
        }
    }

    Box(modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawFadeInRects(rectList, debug)
        }
    ) {
        Text(
            text = richText,
            modifier = modifier,
            onTextLayout = { textLayout ->
                val text = textLayout.layoutInput.text
                val endIndex = text.lastIndex

                if (endIndex >= 0) {
                    /**
                     * When markdown re-parses, this Text may shrink/expand.
                     * Clamp startIndex so calculateBoundingRectList never receives an invalid range.
                     */
                    val safeStartIndex = startIndex.coerceIn(0, endIndex + 1)

                    val newRects = calculateBoundingRectList(
                        textLayoutResult = textLayout,
                        startIndex = safeStartIndex,
                        endIndex = endIndex,
                        segmentation = segmentation
                    ).map { rect ->
                        RectWithAnimation(
                            id = "${safeStartIndex}_${endIndex}_${rect.top}_${rect.left}_${rect.right}_${rect.bottom}",
                            rect = rect,
                            startIndex = safeStartIndex,
                            endIndex = endIndex
                        )
                    }
                    println("onTextLayout safeStartIndex: $safeStartIndex, endIndex: $endIndex, text: $text")
                    onStartIndexChange(endIndex + 1)

                    // Make them visible immediately
                    rectList.addAll(newRects)

                    // Kick off animations without causing cancellation of previous ones
                    rectBatchChannel.trySend(newRects)
                }
            }
        )
    }
}

private fun ContentDrawScope.drawFadeInRects(
    rectList: List<RectWithAnimation>,
    debug: Boolean = false
) {
    rectList.forEachIndexed { _, rectWithAnimation ->

        val progress = rectWithAnimation.animatable.value
        val rect = rectWithAnimation.rect
        val topLeft = rect.topLeft
        val rectSize = rect.size

        drawRect(
            color = Color.Red.copy(1 - progress),
            topLeft = topLeft,
            size = rectSize,
            blendMode = BlendMode.DstOut
        )

        // For Debugging
        if (debug) {
            drawRect(
                color = lerp(Color.Red, Color.Green, progress),
                topLeft = topLeft,
                size = rectSize,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
