package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

private val gfmFlavour = GFMFlavourDescriptor()
private val markdownParser by lazy { MarkdownParser(gfmFlavour) }

// Regex to detect <think>...</think> or <thinking>...</thinking>
private val THINKING_TAG_REGEX = Regex("<think(?:ing)?>([\\s\\S]*?)(?:</think(?:ing)?>|$)", RegexOption.DOT_MATCHES_ALL)

/**
 * Production-ready Compose Multiplatform Markdown Renderer.
 * Shared between Android and iOS without any platform-specific SDK dependencies.
 */
@Composable
fun LastChatMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    paragraphSpacing: Dp = 6.dp,
    darkTheme: Boolean = isSystemInDarkTheme(),
    onClickLink: ((String) -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val contentColor = style.color.takeOrElse { LocalContentColor.current }

    // Split thinking tags from main markdown content
    val parsedBlocks = remember(content) {
        splitThinkingBlocks(content)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(paragraphSpacing),
    ) {
        parsedBlocks.forEach { block ->
            when (block) {
                is ContentBlock.Thinking -> {
                    LastChatThinkBlock(
                        thinkingText = block.text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is ContentBlock.Markdown -> {
                    val astRoot = remember(block.text) {
                        markdownParser.buildMarkdownTreeFromString(block.text)
                    }

                    ProvideTextStyle(style.copy(color = contentColor)) {
                        astRoot.children.forEach { childNode ->
                            MarkdownNodeRenderer(
                                node = childNode,
                                content = block.text,
                                darkTheme = darkTheme,
                                onOpenUrl = { url ->
                                    if (onClickLink != null) onClickLink(url)
                                    else {
                                        try { uriHandler.openUri(url) } catch (_: Throwable) {}
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface ContentBlock {
    data class Thinking(val text: String) : ContentBlock
    data class Markdown(val text: String) : ContentBlock
}

private fun splitThinkingBlocks(rawContent: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    var lastEnd = 0

    THINKING_TAG_REGEX.findAll(rawContent).forEach { match ->
        val before = rawContent.substring(lastEnd, match.range.first).trim()
        if (before.isNotEmpty()) {
            blocks.add(ContentBlock.Markdown(before))
        }

        val thinkText = match.groupValues[1].trim()
        if (thinkText.isNotEmpty()) {
            blocks.add(ContentBlock.Thinking(thinkText))
        }

        lastEnd = match.range.last + 1
    }

    if (lastEnd < rawContent.length) {
        val remaining = rawContent.substring(lastEnd).trim()
        if (remaining.isNotEmpty()) {
            blocks.add(ContentBlock.Markdown(remaining))
        }
    }

    return blocks.ifEmpty { listOf(ContentBlock.Markdown(rawContent)) }
}

@Composable
private fun MarkdownNodeRenderer(
    node: ASTNode,
    content: String,
    darkTheme: Boolean,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    listLevel: Int = 0,
) {
    when (node.type) {
        MarkdownElementTypes.MARKDOWN_FILE -> {
            node.children.forEach { child ->
                MarkdownNodeRenderer(child, content, darkTheme, onOpenUrl, modifier, listLevel)
            }
        }

        MarkdownElementTypes.PARAGRAPH -> {
            val annotated = remember(node, content) {
                buildInlineMarkdown(node, content, onOpenUrl)
            }
            Text(
                text = annotated,
                style = LocalTextStyle.current,
                modifier = modifier.fillMaxWidth(),
            )
        }

        // Headings
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6 -> {
            val level = when (node.type) {
                MarkdownElementTypes.ATX_1 -> 1
                MarkdownElementTypes.ATX_2 -> 2
                MarkdownElementTypes.ATX_3 -> 3
                MarkdownElementTypes.ATX_4 -> 4
                MarkdownElementTypes.ATX_5 -> 5
                else -> 6
            }
            val headingStyle = when (level) {
                1 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                2 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                3 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                4 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium)
            }
            val atxContent = node.children.firstOrNull { it.type == MarkdownTokenTypes.ATX_CONTENT } ?: node
            val text = atxContent.getTextInNode(content).trim()
            val annotated = remember(text) {
                buildInlineMarkdown(atxContent, content, onOpenUrl)
            }
            Text(
                text = annotated,
                style = headingStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = modifier.padding(vertical = 4.dp),
            )
        }

        // Lists
        MarkdownElementTypes.UNORDERED_LIST -> {
            Column(
                modifier = modifier.fillMaxWidth().padding(start = (listLevel * 14).dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }.forEach { itemNode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "•",
                            style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            itemNode.children.forEach { itemChild ->
                                MarkdownNodeRenderer(itemChild, content, darkTheme, onOpenUrl, listLevel = listLevel + 1)
                            }
                        }
                    }
                }
            }
        }

        MarkdownElementTypes.ORDERED_LIST -> {
            var counter = 1
            Column(
                modifier = modifier.fillMaxWidth().padding(start = (listLevel * 14).dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }.forEach { itemNode ->
                    val num = counter++
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "$num.",
                            style = LocalTextStyle.current.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            itemNode.children.forEach { itemChild ->
                                MarkdownNodeRenderer(itemChild, content, darkTheme, onOpenUrl, listLevel = listLevel + 1)
                            }
                        }
                    }
                }
            }
        }

        // Code Blocks
        MarkdownElementTypes.CODE_BLOCK,
        MarkdownElementTypes.CODE_FENCE -> {
            val fenceLang = node.children.firstOrNull { it.type == MarkdownTokenTypes.FENCE_LANG }
                ?.getTextInNode(content)?.toString()?.trim() ?: ""
            val codeContent = if (node.type == MarkdownElementTypes.CODE_FENCE) {
                val start = node.children.firstOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.startOffset
                val end = node.children.lastOrNull { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.endOffset
                if (start != null && end != null && start <= end) {
                    content.substring(start, end).trimEnd()
                } else {
                    node.getTextInNode(content).toString()
                }
            } else {
                node.getTextInNode(content).toString().trimEnd()
            }

            LastChatCodeBlock(
                code = codeContent,
                language = fenceLang,
                darkTheme = darkTheme,
                modifier = modifier,
            )
        }

        // Blockquotes
        MarkdownElementTypes.BLOCK_QUOTE -> {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    node.children.forEach { child ->
                        MarkdownNodeRenderer(child, content, darkTheme, onOpenUrl, listLevel = listLevel)
                    }
                }
            }
        }

        // Horizontal Rule
        MarkdownTokenTypes.HORIZONTAL_RULE -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }

        // Tables (GFM)
        GFMElementTypes.TABLE -> {
            RenderGfmTable(node, content, modifier)
        }

        else -> {
            // Default fallback: render children
            if (node.children.isNotEmpty()) {
                node.children.forEach { child ->
                    MarkdownNodeRenderer(child, content, darkTheme, onOpenUrl, modifier, listLevel)
                }
            }
        }
    }
}

/**
 * Builds an AnnotatedString for inline markdown elements (bold, italic, code, link, etc.).
 */
private fun buildInlineMarkdown(
    node: ASTNode,
    content: String,
    onOpenUrl: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    fun traverse(curr: ASTNode) {
        when (curr.type) {
            MarkdownElementTypes.EMPH -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    curr.children.filter { it.type != MarkdownTokenTypes.EMPH }.forEach { traverse(it) }
                }
            }

            MarkdownElementTypes.STRONG -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    curr.children.filter { it.type != MarkdownTokenTypes.EMPH }.forEach { traverse(it) }
                }
            }

            GFMElementTypes.STRIKETHROUGH -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    curr.children.filter { it.type != GFMTokenTypes.TILDE }.forEach { traverse(it) }
                }
            }

            MarkdownElementTypes.CODE_SPAN -> {
                val codeText = curr.getTextInNode(content).removeSurrounding("`").trim()
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x22888888),
                        fontSize = 12.sp,
                    )
                ) {
                    append(" $codeText ")
                }
            }

            MarkdownElementTypes.INLINE_LINK -> {
                val textNode = curr.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                val destNode = curr.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                val linkText = textNode?.getTextInNode(content)?.toString()?.removeSurrounding("[", "]") ?: "link"
                val linkUrl = destNode?.getTextInNode(content)?.toString()?.trim() ?: ""

                val link = LinkAnnotation.Url(
                    url = linkUrl,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = Color(0xFF388E3C),
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium,
                        )
                    ),
                    linkInteractionListener = {
                        onOpenUrl(linkUrl)
                    }
                )

                withLink(link) {
                    append(linkText)
                }
            }

            GFMTokenTypes.CHECK_BOX -> {
                val checked = curr.getTextInNode(content).contains("x", ignoreCase = true)
                append(if (checked) "☑ " else "☐ ")
            }

            MarkdownTokenTypes.EOL -> {
                append("\n")
            }

            else -> {
                if (curr is LeafASTNode) {
                    append(curr.getTextInNode(content).toString())
                } else {
                    curr.children.forEach { traverse(it) }
                }
            }
        }
    }

    traverse(node)
}

/**
 * Renders a GitHub-Flavored Markdown table with horizontal scrolling.
 */
@Composable
private fun RenderGfmTable(
    tableNode: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(8.dp),
        ) {
            val headerNode = tableNode.children.firstOrNull { it.type == GFMElementTypes.HEADER }
            val rowNodes = tableNode.children.filter { it.type == GFMElementTypes.ROW }

            // Header Row
            if (headerNode != null) {
                val headerCells = headerNode.children.filter { it.type == GFMTokenTypes.CELL }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    headerCells.forEach { cell ->
                        Text(
                            text = cell.getTextInNode(content).toString().trim(),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // Data Rows
            rowNodes.forEachIndexed { index, rowNode ->
                val cells = rowNode.children.filter { it.type == GFMTokenTypes.CELL }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index % 2 == 1) MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    cells.forEach { cell ->
                        Text(
                            text = cell.getTextInNode(content).toString().trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
