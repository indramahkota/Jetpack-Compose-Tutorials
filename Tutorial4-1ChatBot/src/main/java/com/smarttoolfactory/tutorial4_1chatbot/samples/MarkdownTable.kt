package com.smarttoolfactory.tutorial4_1chatbot.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.commonmark.Markdown
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
import com.halilibo.richtext.markdown.node.AstSoftLineBreak
import com.halilibo.richtext.markdown.node.AstStrikethrough
import com.halilibo.richtext.markdown.node.AstStrongEmphasis
import com.halilibo.richtext.markdown.node.AstTableBody
import com.halilibo.richtext.markdown.node.AstTableCell
import com.halilibo.richtext.markdown.node.AstTableCellAlignment
import com.halilibo.richtext.markdown.node.AstTableHeader
import com.halilibo.richtext.markdown.node.AstTableRoot
import com.halilibo.richtext.markdown.node.AstTableRow
import com.halilibo.richtext.markdown.node.AstText
import com.halilibo.richtext.ui.BasicRichText
import com.halilibo.richtext.ui.RichTextScope
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.Table
import com.halilibo.richtext.ui.TableStyle
import com.halilibo.richtext.ui.string.InlineContent
import com.halilibo.richtext.ui.string.RichTextString
import com.halilibo.richtext.ui.string.Text
import com.halilibo.richtext.ui.string.withFormat

@Preview
@Composable
fun MarkdownTableComposerPreview() {

    val markdownTable = """
        | Name  | Age |  City    |
        |-------|-----|-------|
        | **Alice** | 25  | New York |
        | Bob   | 30  | London   |
    """.trimIndent()

    Column(
        modifier = Modifier
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text("Default table without Borders", fontSize = 20.sp)
        BasicRichText(
            modifier = Modifier.border(
                width = 2.dp,
                color = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ),
            style = RichTextStyle(
                tableStyle = TableStyle(
                    borderColor = Color.Transparent
                )
            )
        ) {
            Markdown(markdownTable)
        }

        Text("Custom Table with Table", fontSize = 20.sp)

        TableComposer(
            markdown = markdownTable
        ) { astNode, visitChildren ->

            Table(
                headerRow = {
                    astNode.filterChildrenType<AstTableHeader>()
                        .firstOrNull()
                        ?.filterChildrenType<AstTableRow>()
                        ?.firstOrNull()
                        ?.filterChildrenType<AstTableCell>()
                        ?.forEach { tableCell ->
                            cell {
                                MarkdownRichText(tableCell)
//                                visitChildren(tableCell)
                            }
                        }
                }
            ) {
                astNode.filterChildrenType<AstTableBody>()
                    .firstOrNull()
                    ?.filterChildrenType<AstTableRow>()
                    ?.forEach { tableRow ->
                        row {
                            tableRow.filterChildrenType<AstTableCell>()
                                .forEach { tableCell: AstNode ->
                                    cell {
                                        MarkdownRichText(tableCell)
//                                        visitChildren(tableCell)
                                    }
                                }
                        }
                    }
            }
        }

        Text("Custom Table", fontSize = 20.sp)

        TableComposer(
            markdown = markdownTable
        ) { astNode, visitChildren ->
            CustomTable(
                tableRoot = astNode,
                cellPadding = 16.dp,
                minCellWidth = 120.dp,
                borderWidth = 2.dp
            )
        }

        Text("Custom Table with Columns and Rows", fontSize = 20.sp)
        TableComposer(
            markdown = markdownTable
        ) { astNode, visitChildren ->

            Column(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .border(2.dp, Color.Black, RoundedCornerShape(16.dp))
                    .fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    astNode.filterChildrenType<AstTableHeader>()
                        .firstOrNull()
                        ?.filterChildrenType<AstTableRow>()
                        ?.firstOrNull()
                        ?.filterChildrenType<AstTableCell>()
                        ?.forEach { tableCell: AstNode ->
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .weight(1f)
                                    .padding(start = 16.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                MarkdownRichText(
                                    modifier = Modifier
                                        .matchParentSize(),
                                    astNode = tableCell
                                )

//                                visitChildren(tableCell)
                            }
                        }
                }

                Column {
                    astNode.filterChildrenType<AstTableBody>()
                        .firstOrNull()
                        ?.filterChildrenType<AstTableRow>()
                        ?.forEach { tableRow: AstNode ->

                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                thickness = 2.dp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().background(
                                    MaterialTheme.colorScheme.surface
                                ),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {

                                val columnSequence: Sequence<AstNode> =
                                    tableRow.filterChildrenType<AstTableCell>()

                                columnSequence
                                    .forEachIndexed { index, tableCell ->
                                        Box(
                                            modifier = Modifier
                                                .height(44.dp)
                                                .weight(1f)
                                                .padding(start = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            MarkdownRichText(
                                                modifier = Modifier
                                                    .matchParentSize(),
                                                astNode = tableCell
                                            )

//                                            visitChildren(tableCell)
                                        }
                                    }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun RichTextScope.CustomTable(
    modifier: Modifier = Modifier,
    tableRoot: AstNode,
    cellPadding: Dp = 0.dp,
    borderWidth: Dp = 0.dp,
    minCellWidth: Dp = 120.dp
) {
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val headerBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)

    // Collect rows in display order: header rows first (if any), then body rows.
    val headerRows: Sequence<AstNode> = remember(tableRoot) {
        tableRoot.children()
            .firstOrNull { it.type == AstTableHeader }
            ?.children()
            ?.filter { it.type == AstTableRow }
            .orEmpty()
    }

    val bodyRows: Sequence<AstNode> = remember(tableRoot) {
        tableRoot.children()
            .firstOrNull { it.type == AstTableBody }
            ?.children()
            ?.filter { it.type == AstTableRow }
            .orEmpty()
    }

    val allRows: Sequence<AstNode> = remember(headerRows, bodyRows) {
        headerRows + bodyRows
    }

    if (allRows.count() == 0) return

    val columnCount: Int = remember(allRows) {
        allRows.maxOf { row ->
            row.children().count { it.type is AstTableCell }
        }.coerceAtLeast(1)
    }

    // Horizontal scroll to match ChatGPT behavior on narrow screens / many columns.
    Column(
        modifier = modifier
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp)) // outer border
            .horizontalScroll(rememberScrollState())
    ) {
        allRows.forEachIndexed { rowIndex, rowNode ->
            val isHeaderRow = rowIndex < headerRows.count()

            Row {
                val cells: List<AstNode> =
                    rowNode.children().filter { it.type is AstTableCell }.toList()

                // Pad missing cells so borders stay aligned.
                for (col in 0 until columnCount) {
                    val cellNode: AstNode? = cells.getOrNull(col)

                    val cellType: AstTableCell? = (cellNode?.type as? AstTableCell)
                    val isHeaderCell = isHeaderRow || (cellType?.header == true)

                    val alignment = when (cellType?.alignment) {
                        AstTableCellAlignment.LEFT -> Alignment.CenterStart
                        AstTableCellAlignment.CENTER -> Alignment.Center
                        AstTableCellAlignment.RIGHT -> Alignment.CenterEnd
                        else -> Alignment.CenterStart
                    }

                    Box(
                        modifier = Modifier
                            .widthIn(min = minCellWidth)
                            .defaultMinSize(minHeight = 44.dp)
                            .background(if (isHeaderCell) headerBg else MaterialTheme.colorScheme.surface)
                            .padding(cellPadding),
                        contentAlignment = alignment
                    ) {
                        if (cellNode != null) {
                            MarkdownRichText(
                                modifier = Modifier
                                    .matchParentSize(),
                                astNode = cellNode
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TableComposer(
    markdown: String,
    content: @Composable RichTextScope.(astNode: AstNode, visitChildren: @Composable ((AstNode) -> Unit)) -> Unit
) {

    val parser: CommonmarkAstNodeParser = remember {
        CommonmarkAstNodeParser()
    }
    val astNode: AstNode = parser.parse(markdown)

    val tableComposer = remember {
        object : AstBlockNodeComposer {

            override fun predicate(astBlockNodeType: AstBlockNodeType): Boolean {
                // Intercept only tables
                val isTable = astBlockNodeType == AstTableRoot
                println("isTable: $isTable, astBlockNodeType: $astBlockNodeType")
                return isTable
            }

            @Composable
            override fun RichTextScope.Compose(
                astNode: AstNode,
                visitChildren: @Composable ((AstNode) -> Unit)
            ) {
                content(astNode, visitChildren)
            }
        }
    }
    RichTextScope.BasicMarkdown(astNode, tableComposer)
}

@Composable
internal fun RichTextScope.MarkdownRichText(astNode: AstNode, modifier: Modifier = Modifier) {
    // Assume that only RichText nodes reside below this level.
    val richText = remember(astNode) {
        computeRichTextString(astNode)
    }

    Text(text = richText, modifier = modifier)
}

private fun computeRichTextString(astNode: AstNode): RichTextString {
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
