package me.rerere.rikkahub.ui.components.richtext

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.core.net.toUri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import me.rerere.rikkahub.data.datastore.RpStyleRule
import me.rerere.rikkahub.ui.components.table.DataTable
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.BidiDirection
import me.rerere.rikkahub.utils.appLocale
import me.rerere.rikkahub.utils.resolveBidiDirection
import me.rerere.rikkahub.utils.toDp
import me.rerere.rikkahub.utils.saveToDownloads
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

private val flavour by lazy {
    GFMFlavourDescriptor(
        makeHttpsAutoLinks = true, useSafeLinks = true
    )
}

private val parser by lazy {
    MarkdownParser(flavour)
}

private val INLINE_LATEX_REGEX = Regex("\\\\\\((.+?)\\\\\\)")
private val BLOCK_LATEX_REGEX = Regex("\\\\\\[(.+?)\\\\\\]", RegexOption.DOT_MATCHES_ALL)
// Matches <think>...</think> or <thinking>...</thinking> with optional closing tag
val THINKING_REGEX = Regex("<think(?:ing)?>([\\s\\S]*?)(?:</think(?:ing)?>|$)", RegexOption.DOT_MATCHES_ALL)
// Matches orphaned closing tags: content followed by </think> or </thinking> without opening tag
private val ORPHAN_CLOSE_TAG_REGEX = Regex("^([\\s\\S]*?)</think(?:ing)?>", RegexOption.DOT_MATCHES_ALL)
private val CODE_BLOCK_REGEX = Regex("```[\\s\\S]*?```|`[^`\n]*`", RegexOption.DOT_MATCHES_ALL)
private val BREAK_LINE_REGEX = Regex("(?i)<br\\s*/?>")
private const val LTR_ISOLATE = '\u2066'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'

/**
 * CompositionLocal for RP style rules - enables color customization throughout the markdown tree
 */
val LocalRpStyleRules = compositionLocalOf<List<RpStyleRule>> { emptyList() }

private data class StreamingTextReveal(
    val startOffset: Int,
    val endOffset: Int,
    val alpha: Float,
    val color: Color
)

private val LocalStreamingTextReveal = compositionLocalOf<StreamingTextReveal?> { null }

/**
 * Safely get color from RP style rule for a given pattern.
 * Returns null if pattern not found, not enabled, or color parsing fails.
 */
@Composable
private fun getRpColor(pattern: String): Color? {
    val rules = LocalRpStyleRules.current
    val rule = rules.find { it.pattern == pattern && it.enabled } ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(rule.colorHex)) }.getOrNull()
}

// Standard markdown patterns that are handled by the AST parser
private val STANDARD_PATTERNS = setOf("*", "**", "~~", "`", "#", "##", "###", "####", "#####", "######", ">")

/**
 * Append text to AnnotatedString.Builder, scanning for custom RP patterns.
 * Custom patterns are those NOT in STANDARD_PATTERNS (which are handled by the markdown AST).
 * For each custom pattern, builds a regex like `pattern(.+?)pattern` and applies the color.
 */
private fun AnnotatedString.Builder.appendTextWithCustomPatterns(
    text: String,
    rpStyleRules: List<RpStyleRule>
) {
    // Get custom patterns only (exclude standard markdown patterns)
    val customRules = rpStyleRules.filter { it.enabled && it.pattern !in STANDARD_PATTERNS }
    
    if (customRules.isEmpty()) {
        append(text)
        return
    }
    
    // Build a combined regex for all custom patterns
    // Each pattern matches: pattern + content + pattern (non-greedy)
    val patternRegexes = customRules.mapNotNull { rule ->
        val escaped = Regex.escape(rule.pattern)
        runCatching {
            val color = Color(android.graphics.Color.parseColor(rule.colorHex))
            Regex("$escaped(.+?)$escaped") to color
        }.getOrNull()
    }
    
    if (patternRegexes.isEmpty()) {
        append(text)
        return
    }
    
    // Find all matches from all patterns
    data class Match(val range: IntRange, val content: String, val color: Color)
    val allMatches = mutableListOf<Match>()
    
    patternRegexes.forEach { (regex, color) ->
        regex.findAll(text).forEach { matchResult ->
            allMatches.add(Match(
                range = matchResult.range,
                content = matchResult.groupValues[1],
                color = color
            ))
        }
    }
    
    // Sort by start position
    allMatches.sortBy { it.range.first }
    
    // Remove overlapping matches (keep earlier ones)
    val nonOverlapping = mutableListOf<Match>()
    var lastEnd = -1
    allMatches.forEach { match ->
        if (match.range.first > lastEnd) {
            nonOverlapping.add(match)
            lastEnd = match.range.last
        }
    }
    
    // Build the annotated string
    var currentIndex = 0
    nonOverlapping.forEach { match ->
        // Append text before this match
        if (match.range.first > currentIndex) {
            append(text.substring(currentIndex, match.range.first))
        }
        // Append the styled content (without the pattern delimiters)
        withStyle(SpanStyle(color = match.color)) {
            append(match.content)
        }
        currentIndex = match.range.last + 1
    }
    
    // Append remaining text
    if (currentIndex < text.length) {
        append(text.substring(currentIndex))
    }
}

// 预处理markdown内容
private fun preProcess(content: String): String {
    // 先找出所有代码块的位置
    val codeBlocks = mutableListOf<IntRange>()
    CODE_BLOCK_REGEX.findAll(content).forEach { match ->
        codeBlocks.add(match.range)
    }

    // 检查位置是否在代码块内
    fun isInCodeBlock(position: Int): Boolean {
        return codeBlocks.any { range -> position in range }
    }

    // 替换行内公式 \( ... \) 到 $ ... $，但跳过代码块内的内容
    var result = INLINE_LATEX_REGEX.replace(content) { matchResult ->
        if (isInCodeBlock(matchResult.range.first)) {
            matchResult.value // 保持原样
        } else {
            "$" + matchResult.groupValues[1] + "$"
        }
    }

    // 替换块级公式 \[ ... \] 到 $$ ... $$，但跳过代码块内的内容
    result = BLOCK_LATEX_REGEX.replace(result) { matchResult ->
        if (isInCodeBlock(matchResult.range.first)) {
            matchResult.value // 保持原样
        } else {
            "$$" + matchResult.groupValues[1] + "$$"
        }
    }

    // 替换思考 - handles both <think> and <thinking> tags
    result = result.replace(THINKING_REGEX) { matchResult ->
        matchResult.groupValues[1].lines().filter { it.isNotBlank() }.joinToString("\n") { ">$it" }
    }

    // Handle orphaned closing tags (missing opening tag) - common with some models
    result = result.replace(ORPHAN_CLOSE_TAG_REGEX) { matchResult ->
        matchResult.groupValues[1].lines().filter { it.isNotBlank() }.joinToString("\n") { ">$it" }
    }

    return result
}

@Composable
private fun rememberContentDirection(text: String): BidiDirection {
    val appLocale = LocalContext.current.appLocale()
    return remember(text, appLocale) {
        resolveBidiDirection(text = text, fallbackLocale = appLocale)
    }
}

private fun BidiDirection.toComposeTextDirection(): TextDirection {
    return if (this == BidiDirection.Rtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr
}

private fun BidiDirection.toLayoutDirection(): LayoutDirection {
    return if (this == BidiDirection.Rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
}

private fun isolateLtr(text: String): String {
    return buildString(text.length + 2) {
        append(LTR_ISOLATE)
        append(text)
        append(POP_DIRECTIONAL_ISOLATE)
    }
}


@Preview(showBackground = true)
@Composable
private fun MarkdownPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MarkdownBlock(
                content = "Hi there!", modifier = Modifier.background(Color.Red)
            )
            MarkdownBlock(
                content = """
                    ### 🌍 This is Markdown Test This Markdown Test
                    1. How many roads must a man walk down
                        * the slings and arrows of outrageous fortune, Or to take arms against a sea of troubles,
                        * by opposing end them.
                            * How many times must a man look up, Before he can see the sky?
                            * How many times $ f(x) = \sum_{n=0}^{\infty} \frac{f^{(n)}(a)}{n!}(x-a)^n$
                    2. How many times must a man look up, Before he can see the sky?

                    * [ ] Before they're allowed to be free? Yes, 'n' how many times can a man turn his head
                    * [x] Before they're allowed to be free? Yes, 'n' how many times can a man turn his head

                    4. For in that sleep of death what dreams may come [citation](1)

                    This is Markdown Test, This <br/> is Markdown Test.
                    ha<br/>ha

                    ***
                    This is Markdown Test, This is Markdown Test.

                    | Name | Age | Address | Email | Job | Homepage |
                    | ---- | --- | ------- | ----- | --- | -------- |
                    | John | 25  | New York | john@example.com | Software Engineer | john.com |
                    | Jane | 26  | London   | jane@example.com | Data Scientist | jane.com |

                    ## HTML Escaping
                    This is a &gt;  test

                """.trimIndent()
            )
        }
    }
}

@Composable
fun MarkdownBlock(
    content: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    streamingTextReveal: Boolean = false,
    onExpandedStreamingCodeBlockChanged: (() -> Unit)? = null,
    onClickCitation: (String) -> Unit = {}
) {
    // Read rpStyleRules from settings
    val settings = LocalSettings.current
    val rpStyleRules = settings.displaySetting.rpStyleRules
    val contentColor = style.color.takeOrElse { LocalContentColor.current }
    val revealAlpha = remember { Animatable(1f) }
    var revealStartOffset by remember { mutableStateOf(0) }
    var previousStreamingContent by remember { mutableStateOf(preProcess(content)) }
    var lastStreamUpdateMillis by remember { mutableStateOf(0L) }
    
    var (data, setData) = remember {
        val preprocessed = preProcess(content)
        val astTree = parser.buildMarkdownTreeFromString(preprocessed)
        mutableStateOf(
            value = preprocessed to astTree,
            policy = referentialEqualityPolicy(),
        )
    }

    // 监听内容变化，重新解析AST树
    // 这里在后台线程解析AST树, 防止频繁更新的时候掉帧
    val updatedContent by rememberUpdatedState(content)
    LaunchedEffect(Unit) {
        snapshotFlow { updatedContent }.distinctUntilChanged().mapLatest {
            val preprocessed = preProcess(it)
            val astTree = parser.buildMarkdownTreeFromString(preprocessed)
            preprocessed to astTree
        }.catch { exception -> exception.printStackTrace() }.flowOn(Dispatchers.Default) // 在后台线程解析AST树
            .collect {
                setData(it)
            }
    }

    val (preprocessed, astTree) = data
    val blockDirection = rememberContentDirection(preprocessed)
    LaunchedEffect(content, streamingTextReveal) {
        val nextContent = preProcess(content)
        if (!streamingTextReveal) {
            previousStreamingContent = nextContent
            revealAlpha.snapTo(1f)
            return@LaunchedEffect
        }

        val previousContent = previousStreamingContent
        previousStreamingContent = nextContent

        if (nextContent.length > previousContent.length) {
            val now = SystemClock.uptimeMillis()
            val elapsedMillis = if (lastStreamUpdateMillis == 0L) Long.MAX_VALUE else now - lastStreamUpdateMillis
            val appendedLength = nextContent.length - previousContent.length
            revealStartOffset = streamingRevealWordStart(
                content = nextContent,
                offset = commonPrefixLength(previousContent, nextContent)
            )
            lastStreamUpdateMillis = now
            revealAlpha.snapTo(streamingRevealInitialAlpha(elapsedMillis, appendedLength))
            revealAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 360,
                    easing = LinearOutSlowInEasing
                )
            )
        } else if (nextContent != previousContent) {
            revealAlpha.snapTo(1f)
        }
    }
    val streamingReveal = if (streamingTextReveal && revealAlpha.value < 0.995f) {
        StreamingTextReveal(
            startOffset = revealStartOffset.coerceIn(0, preprocessed.length),
            endOffset = preprocessed.length,
            alpha = revealAlpha.value.coerceIn(0f, 1f),
            color = contentColor
        )
    } else {
        null
    }

    // Provide rpStyleRules to entire tree via CompositionLocal
    CompositionLocalProvider(
        LocalRpStyleRules provides rpStyleRules,
        LocalStreamingTextReveal provides streamingReveal,
        LocalLayoutDirection provides blockDirection.toLayoutDirection(),
    ) {
        ProvideTextStyle(style) {
            Column(
                modifier = modifier.padding(start = 4.dp)
            ) {
                astTree.children.fastForEach { child ->
                    MarkdownNode(
                        node = child,
                        content = preprocessed,
                        onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                        onClickCitation = onClickCitation
                    )
                }
            }
        }
    }
}

private fun commonPrefixLength(left: String, right: String): Int {
    val limit = minOf(left.length, right.length)
    for (index in 0 until limit) {
        if (left[index] != right[index]) return index
    }
    return limit
}

private fun streamingRevealInitialAlpha(elapsedMillis: Long, appendedLength: Int): Float {
    val cadenceAlpha = when {
        elapsedMillis < 90L -> 0.22f
        elapsedMillis < 180L -> 0.30f
        elapsedMillis < 420L -> 0.42f
        elapsedMillis < 900L -> 0.58f
        else -> 0.76f
    }
    val tinyDeltaLift = when {
        appendedLength <= 1 -> 0.16f
        appendedLength <= 3 -> 0.08f
        else -> 0f
    }
    return (cadenceAlpha + tinyDeltaLift).coerceAtMost(0.82f)
}

private fun streamingRevealWordStart(content: String, offset: Int): Int {
    var index = offset.coerceIn(0, content.length)
    while (index > 0 && content[index - 1].isStreamingWordCharacter()) {
        index--
    }
    return index
}

private fun Char.isStreamingWordCharacter(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '-' || this == '\''
}

// for debug
private fun dumpAst(node: ASTNode, text: String, indent: String = "") {
    println("$indent${node.type} ${if (node.children.isEmpty()) node.getTextInNode(text) else ""} | ${node.javaClass.simpleName}")
    node.children.fastForEach {
        dumpAst(it, text, "$indent  ")
    }
}

object HeaderStyle {
    val H1 = TextStyle(
        fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold, fontSize = 24.sp
    )

    val H2 = TextStyle(
        fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold, fontSize = 20.sp
    )

    val H3 = TextStyle(
        fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold, fontSize = 18.sp
    )

    val H4 = TextStyle(
        fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold, fontSize = 16.sp
    )

    val H5 = TextStyle(
        fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold, fontSize = 14.sp
    )

    val H6 = TextStyle(
        fontStyle = FontStyle.Normal, fontWeight = FontWeight.Bold, fontSize = 12.sp
    )
}

@Composable
private fun MarkdownNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onExpandedStreamingCodeBlockChanged: (() -> Unit)? = null,
    onClickCitation: (String) -> Unit = {},
    listLevel: Int = 0
) {
    when (node.type) {
        // 文件根节点
        MarkdownElementTypes.MARKDOWN_FILE -> {
            node.children.fastForEach { child ->
                MarkdownNode(
                    node = child,
                    content = content,
                    modifier = modifier,
                    onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                    onClickCitation = onClickCitation
                )
            }
        }

        // 段落
        MarkdownElementTypes.PARAGRAPH -> {
            Paragraph(
                node = node,
                content = content,
                modifier = modifier,
                onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                onClickCitation = onClickCitation
            )
        }

        // 标题
        MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2, MarkdownElementTypes.ATX_3, MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 -> {
            val (baseStyle, pattern) = when (node.type) {
                MarkdownElementTypes.ATX_1 -> HeaderStyle.H1 to "#"
                MarkdownElementTypes.ATX_2 -> HeaderStyle.H2 to "##"
                MarkdownElementTypes.ATX_3 -> HeaderStyle.H3 to "###"
                MarkdownElementTypes.ATX_4 -> HeaderStyle.H4 to "####"
                MarkdownElementTypes.ATX_5 -> HeaderStyle.H5 to "#####"
                MarkdownElementTypes.ATX_6 -> HeaderStyle.H6 to "######"
                else -> throw IllegalArgumentException("Unknown header type")
            }
            // Get RP color for this heading level
            val rpColor = getRpColor(pattern)
            val style = if (rpColor != null) baseStyle.copy(color = rpColor) else baseStyle
            ProvideTextStyle(value = style) {
                node.children.fastForEach { child ->
                    if (child.type == MarkdownTokenTypes.ATX_CONTENT) {
                        Paragraph(
                            node = child,
                            content = content,
                            onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                            onClickCitation = onClickCitation,
                            modifier = modifier.padding(vertical = 16.dp),
                            trim = true,
                        )
                    }
                }
            }
        }

        // 列表
        MarkdownElementTypes.UNORDERED_LIST -> {
            UnorderedListNode(
                node = node,
                content = content,
                modifier = modifier.padding(vertical = 4.dp),
                onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                onClickCitation = onClickCitation,
                level = listLevel
            )
        }

        MarkdownElementTypes.ORDERED_LIST -> {
            OrderedListNode(
                node = node,
                content = content,
                modifier = modifier.padding(vertical = 4.dp),
                onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                onClickCitation = onClickCitation,
                level = listLevel
            )
        }

        // Checkbox
        GFMTokenTypes.CHECK_BOX -> {
            val isChecked = node.getTextInNode(content).trim() == "[x]"
            Surface(
                shape = RoundedCornerShape(2.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = modifier,
            ) {
                Box(
                    modifier = Modifier
                        .padding(2.dp)
                        .size(LocalTextStyle.current.fontSize.toDp() * 0.8f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isChecked) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // 引用块
        MarkdownElementTypes.BLOCK_QUOTE -> {
            // Get RP color for blockquotes
            val rpColor = getRpColor(">")
            val quoteDirection = rememberContentDirection(node.getTextInNode(content))
            val quoteLayoutDirection = quoteDirection.toLayoutDirection()
            val textStyle = LocalTextStyle.current.copy(
                fontStyle = FontStyle.Italic,
                color = rpColor ?: Color.Unspecified
            )
            ProvideTextStyle(textStyle) {
                val borderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                CompositionLocalProvider(LocalLayoutDirection provides quoteLayoutDirection) {
                    Column(
                        modifier = Modifier
                            .drawWithContent {
                                drawContent()
                                drawRect(color = bgColor, size = size)
                                val borderOffset = if (quoteLayoutDirection == LayoutDirection.Rtl) {
                                    Offset(size.width - 10f, 0f)
                                } else {
                                    Offset.Zero
                                }
                                drawRect(
                                    color = borderColor,
                                    topLeft = borderOffset,
                                    size = Size(10f, size.height)
                                )
                            }
                            .padding(8.dp)
                    ) {
                        node.children.fastForEach { child ->
                            MarkdownNode(
                                node = child,
                                content = content,
                                onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                                onClickCitation = onClickCitation
                            )
                        }
                    }
                }
            }
        }

        // 链接
        MarkdownElementTypes.INLINE_LINK -> {
            val linkText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)
                ?.findChildOfTypeRecursive(GFMTokenTypes.GFM_AUTOLINK, MarkdownTokenTypes.TEXT)?.getTextInNode(content)
                ?: ""
            val linkDest =
                node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content) ?: ""
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            Text(
                text = linkText,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = modifier.clickable {
                    Log.d("Markdown", "Link clicked: text='$linkText', dest='$linkDest'")
                    val uri = linkDest.toUri()
                    Log.d("Markdown", "Parsed URI: scheme=${uri.scheme}, authority=${uri.authority}, packageName=${context.packageName}")
                    // Handle content:// URIs as downloads (files from sandbox/fileprovider)
                    if (uri.scheme == "content") {
                        val fileName = if (linkText.isNotEmpty() && !linkText.contains("/")) linkText else uri.lastPathSegment ?: "downloaded_file"
                        Log.d("Markdown", "Content URI detected, saving to downloads: $fileName")
                        scope.launch {
                            context.saveToDownloads(uri, fileName)
                        }
                    } else if (uri.scheme in listOf("http", "https", "mailto")) {
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        context.startActivity(intent)
                    } else {
                        // Try to open with ACTION_VIEW for other schemes (file://, etc)
                        Log.d("Markdown", "Non-content scheme '${uri.scheme}', trying ACTION_VIEW")
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Log.e("Markdown", "Failed to open link: $linkDest", e)
                        }
                    }
                })
        }

        // 加粗和斜体
        MarkdownElementTypes.EMPH -> {
            ProvideTextStyle(TextStyle(fontStyle = FontStyle.Italic)) {
                node.children.fastForEach { child ->
                    MarkdownNode(
                        node = child, content = content, modifier = modifier, onClickCitation = onClickCitation
                    )
                }
            }
        }

        MarkdownElementTypes.STRONG -> {
            ProvideTextStyle(TextStyle(fontWeight = FontWeight.SemiBold)) {
                node.children.fastForEach { child ->
                    MarkdownNode(
                        node = child, content = content, modifier = modifier, onClickCitation = onClickCitation
                    )
                }
            }
        }

        // GFM 特殊元素
        GFMElementTypes.STRIKETHROUGH -> {
            val direction = rememberContentDirection(node.getTextInNode(content))
            CompositionLocalProvider(LocalLayoutDirection provides direction.toLayoutDirection()) {
                Text(
                    text = node.getTextInNode(content),
                    textDecoration = TextDecoration.LineThrough,
                    modifier = modifier,
                    style = LocalTextStyle.current.copy(
                        textDirection = direction.toComposeTextDirection()
                    )
                )
            }
        }

        GFMElementTypes.TABLE -> {
            TableNode(node = node, content = content, modifier = modifier)
        }

        MarkdownTokenTypes.HORIZONTAL_RULE -> {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )
        }

        // 图片
        MarkdownElementTypes.IMAGE -> {
            val altText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content) ?: ""
            val imageUrl =
                node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content) ?: ""
            Column(
                modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 这里可以使用Coil等图片加载库加载图片
                ZoomableAsyncImage(
                    model = imageUrl,
                    contentDescription = altText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .widthIn(min = 120.dp)
                        .heightIn(min = 120.dp),
                )
            }
        }

        GFMElementTypes.INLINE_MATH -> {
            val formula = node.getTextInNode(content)
            MathInline(
                formula, modifier = modifier.padding(horizontal = 1.dp)
            )
        }

        GFMElementTypes.BLOCK_MATH -> {
            val formula = node.getTextInNode(content)
            MathBlock(
                formula, modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )
        }

        MarkdownElementTypes.CODE_SPAN -> {
            val code = node.getTextInNode(content).trim('`')
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = isolateLtr(code),
                    modifier = modifier,
                    style = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        textDirection = TextDirection.ContentOrLtr
                    )
                )
            }
        }

        MarkdownElementTypes.CODE_BLOCK -> {
            val code = node.getTextInNode(content)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                HighlightCodeBlock(
                    code = code,
                    language = "plaintext",
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .fillMaxWidth(),
                    onExpandedStreamingContentChanged = onExpandedStreamingCodeBlockChanged,
                    completeCodeBlock = true
                )
            }
        }

        // 代码块
        MarkdownElementTypes.CODE_FENCE -> {
            // 这里不能直接取CODE_FENCE_CONTENT的内容，因为首行indent没有包含在内
            // 因此，需要往上找到最后一个EOL元素，用它来作为代码块的起始offset
            val contentStartIndex = node.children.indexOfFirst { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
            if (contentStartIndex == -1) return
            val eolElement =
                node.children.subList(0, contentStartIndex).findLast { it.type == MarkdownTokenTypes.EOL } ?: return
            val codeContentStartOffset = eolElement.endOffset
            val codeContentEndOffset =
                node.children.findLast { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }?.endOffset ?: return
            val code = content.substring(
                codeContentStartOffset, codeContentEndOffset
            ).trimIndent()

            val language =
                node.findChildOfTypeRecursive(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(content) ?: "plaintext"
            val hasEnd = node.findChildOfTypeRecursive(MarkdownTokenTypes.CODE_FENCE_END) != null

            // Mermaid diagrams: render directly without HighlightCodeBlock wrapper
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                if (hasEnd && language == "mermaid") {
                    Mermaid(
                        code = code,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .fillMaxWidth(),
                    )
                } else {
                    HighlightCodeBlock(
                        code = code,
                        language = language,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .fillMaxWidth(),
                        onExpandedStreamingContentChanged = onExpandedStreamingCodeBlockChanged,
                        completeCodeBlock = hasEnd
                    )
                }
            }
        }

        MarkdownTokenTypes.TEXT -> {
            val text = node.getTextInNode(content)
            val direction = rememberContentDirection(text)
            val streamingReveal = LocalStreamingTextReveal.current
            val revealText = remember(text, streamingReveal) {
                buildAnnotatedString {
                    val outputStart = length
                    append(text)
                    applyStreamingRevealStyle(
                        reveal = streamingReveal,
                        sourceStart = node.startOffset,
                        sourceEnd = node.endOffset,
                        outputStart = outputStart,
                        outputEnd = length
                    )
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction.toLayoutDirection()) {
                Text(
                    text = revealText,
                    modifier = modifier,
                    style = LocalTextStyle.current.copy(
                        textDirection = direction.toComposeTextDirection()
                    ),
                )
            }
        }

        MarkdownElementTypes.HTML_BLOCK -> {
            val text = node.getTextInNode(content)
            SimpleHtmlBlock(
                html = text, modifier = modifier
            )
        }

        // 其他类型的节点，递归处理子节点
        else -> {
            // 递归处理其他节点的子节点
            node.children.fastForEach { child ->
                MarkdownNode(
                    node = child,
                    content = content,
                    modifier = modifier,
                    onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                    onClickCitation = onClickCitation
                )
            }
        }
    }
}

@Composable
private fun UnorderedListNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onExpandedStreamingCodeBlockChanged: (() -> Unit)? = null,
    onClickCitation: (String) -> Unit = {},
    level: Int = 0
) {
    val bulletStyle = when (level % 3) {
        0 -> "\u2022"
        1 -> "\u25E6"
        else -> "\u25AA"
    }

    val markerSlotWidth = rememberMarkerSlotWidth(1)

    Column(
        modifier = modifier.padding(start = (level * 8).dp)
    ) {
        node.children.fastForEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                ListItemNode(
                    node = child,
                    content = content,
                    bulletText = bulletStyle,
                    markerSlotWidth = markerSlotWidth,
                    onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                    onClickCitation = onClickCitation,
                    level = level
                )
            }
        }
    }
}

@Composable
private fun OrderedListNode(
    node: ASTNode,
    content: String,
    modifier: Modifier = Modifier,
    onExpandedStreamingCodeBlockChanged: (() -> Unit)? = null,
    onClickCitation: (String) -> Unit = {},
    level: Int = 0
) {
    val listItems = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
    val markerTexts = remember(listItems, content) {
        listItems.mapIndexed { index, child ->
            child.findChildOfTypeRecursive(MarkdownTokenTypes.LIST_NUMBER)?.getTextInNode(content)
                ?: "${index + 1}."
        }
    }
    val markerSlotWidth = rememberMarkerSlotWidth(
        markerTexts.maxOfOrNull { it.trim().length } ?: 1
    )

    Column(modifier.padding(start = (level * 8).dp)) {
        listItems.forEachIndexed { index, child ->
            ListItemNode(
                node = child,
                content = content,
                bulletText = markerTexts[index],
                markerSlotWidth = markerSlotWidth,
                onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                onClickCitation = onClickCitation,
                level = level
            )
        }
    }
}

@Composable
private fun ListItemNode(
    node: ASTNode,
    content: String,
    bulletText: String,
    markerSlotWidth: androidx.compose.ui.unit.Dp,
    onExpandedStreamingCodeBlockChanged: (() -> Unit)? = null,
    onClickCitation: (String) -> Unit = {},
    level: Int
) {
    Column {
        // 分离列表项的直接内容和嵌套列表
        val (directContent, nestedLists) = separateContentAndLists(node)
        val itemDirection = rememberContentDirection(
            directContent.joinToString(separator = " ") { it.getTextInNode(content) }
                .ifBlank { node.getTextInNode(content) }
        )
        // directContent 渲染处理
        if (directContent.isNotEmpty()) {
            CompositionLocalProvider(LocalLayoutDirection provides itemDirection.toLayoutDirection()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val contentColumn: @Composable () -> Unit = {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            directContent.fastForEach { contentChild ->
                                MarkdownNode(
                                    node = contentChild,
                                    content = content,
                                    onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                                    onClickCitation = onClickCitation,
                                    listLevel = level,
                                )
                            }
                        }
                    }
                    ListMarker(
                        markerText = bulletText.trim(),
                        markerSlotWidth = markerSlotWidth,
                    )
                    contentColumn()
                }
            }
        }
        // nestedLists 渲染处理
        nestedLists.fastForEach { nestedList ->
            CompositionLocalProvider(LocalLayoutDirection provides itemDirection.toLayoutDirection()) {
                MarkdownNode(
                    node = nestedList,
                    content = content,
                    onClickCitation = onClickCitation,
                    listLevel = level + 1 // 增加层级
                )
            }
        }
    }
}

// 分离列表项的直接内容和嵌套列表
@Composable
private fun rememberMarkerSlotWidth(maxMarkerLength: Int): androidx.compose.ui.unit.Dp {
    val fontSize = LocalTextStyle.current.fontSize.toDp()
    return remember(fontSize, maxMarkerLength) {
        (fontSize * (maxMarkerLength.coerceAtLeast(1) * 0.75f + 0.75f)).coerceAtLeast(20.dp)
    }
}

@Composable
private fun ListMarker(
    markerText: String,
    markerSlotWidth: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier.widthIn(min = markerSlotWidth),
        contentAlignment = Alignment.TopEnd,
    ) {
        Text(
            text = markerText,
            style = LocalTextStyle.current.copy(
                textDirection = TextDirection.ContentOrLtr
            )
        )
    }
}

private fun separateContentAndLists(listItemNode: ASTNode): Pair<List<ASTNode>, List<ASTNode>> {
    val directContent = mutableListOf<ASTNode>()
    val nestedLists = mutableListOf<ASTNode>()
    listItemNode.children.fastForEach { child ->
        when (child.type) {
            MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> {
                nestedLists.add(child)
            }

            else -> {
                directContent.add(child)
            }
        }
    }
    return directContent to nestedLists
}

@Composable
private fun Paragraph(
    node: ASTNode,
    content: String,
    trim: Boolean = false,
    onExpandedStreamingCodeBlockChanged: (() -> Unit)? = null,
    onClickCitation: (String) -> Unit = {},
    modifier: Modifier,
) {
    // dumpAst(node, content)
    val paragraphDirection = rememberContentDirection(node.getTextInNode(content))
    if (node.findChildOfTypeRecursive(MarkdownElementTypes.IMAGE, GFMElementTypes.BLOCK_MATH) != null) {
        CompositionLocalProvider(LocalLayoutDirection provides paragraphDirection.toLayoutDirection()) {
            FlowRow(modifier = modifier) {
                node.children.fastForEach { child ->
                    MarkdownNode(
                        node = child,
                        content = content,
                        onExpandedStreamingCodeBlockChanged = onExpandedStreamingCodeBlockChanged,
                        onClickCitation = onClickCitation
                    )
                }
            }
        }
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val inlineContents = remember {
        mutableStateMapOf<String, InlineTextContent>()
    }
    val hasInlineMath = remember(node) {
        node.findChildOfTypeRecursive(GFMElementTypes.INLINE_MATH) != null
    }

    val textStyle = LocalTextStyle.current
    val density = LocalDensity.current
    val rpStyleRules = LocalSettings.current.displaySetting.rpStyleRules
    val streamingReveal = LocalStreamingTextReveal.current
    val annotatedString = remember(content, rpStyleRules, streamingReveal) {
        buildAnnotatedString {
            node.children.fastForEach { child ->
                appendMarkdownNodeContent(
                    node = child,
                    content = content,
                    inlineContents = inlineContents,
                    colorScheme = colorScheme,
                    onClickCitation = onClickCitation,
                    style = textStyle,
                    density = density,
                    trim = trim,
                    rpStyleRules = rpStyleRules,
                    streamingReveal = streamingReveal,
                )
            }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides paragraphDirection.toLayoutDirection()) {
        Text(
            text = annotatedString,
            modifier = modifier.then(
                if (node.nextSibling() != null) Modifier.padding(bottom = 4.dp)
                else Modifier
            ),
            inlineContent = inlineContents,
            softWrap = true,
            overflow = TextOverflow.Visible,
            style = textStyle.copy(
                lineHeight = if (hasInlineMath) TextUnit.Unspecified else textStyle.lineHeight,
                textDirection = paragraphDirection.toComposeTextDirection()
            )
        )
    }
}

@Composable
private fun TableNode(node: ASTNode, content: String, modifier: Modifier = Modifier) {
    // 提取表格的标题行和数据行
    val headerNode = node.children.find { it.type == GFMElementTypes.HEADER }
    val rowNodes = node.children.filter { it.type == GFMElementTypes.ROW }

    // 计算列数（从标题行获取）
    val columnCount = headerNode?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0

    // 检查是否有足够的列来显示表格
    if (columnCount == 0) return

    // 提取表头单元格文本
    val headerCells =
        headerNode?.children?.filter { it.type == GFMTokenTypes.CELL }?.map { it.getTextInNode(content).trim() }
            ?: emptyList()

    // 提取所有行的数据
    val rows = rowNodes.map { rowNode ->
        rowNode.children.filter { it.type == GFMTokenTypes.CELL }.map { it.getTextInNode(content).trim() }
    }
    val tableDirection = rememberContentDirection(
        buildString {
            append(headerCells.joinToString(separator = " "))
            if (rows.isNotEmpty()) {
                append(' ')
                append(rows.flatten().joinToString(separator = " "))
            }
        }
    )

    // 创建表头composable列表
    val headers = List(columnCount) { columnIndex ->
        @Composable {
            MarkdownBlock(
                content = if (columnIndex < headerCells.size) headerCells[columnIndex] else "",
            )
        }
    }

    // 创建行数据composable列表
    val rowComposables = rows.map { rowData ->
        List(columnCount) { columnIndex ->
            @Composable {
                MarkdownBlock(
                    content = if (columnIndex < rowData.size) rowData[columnIndex] else "",
                )
            }
        }
    }

    // 渲染表格
    CompositionLocalProvider(LocalLayoutDirection provides tableDirection.toLayoutDirection()) {
        DataTable(
            headers = headers,
            rows = rowComposables,
            modifier = modifier.padding(vertical = 8.dp),
            columnMinWidths = List(columnCount) { 80.dp },
            columnMaxWidths = List(columnCount) { 200.dp },
        )
    }
}

private fun AnnotatedString.Builder.appendMarkdownNodeContent(
    node: ASTNode,
    content: String,
    trim: Boolean = false,
    inlineContents: MutableMap<String, InlineTextContent>,
    colorScheme: ColorScheme,
    density: Density,
    style: TextStyle,
    onClickCitation: (String) -> Unit = {},
    rpStyleRules: List<RpStyleRule> = emptyList(),
    streamingReveal: StreamingTextReveal? = null,
) {
    val outputStart = length
    when {
        node.type == MarkdownTokenTypes.BLOCK_QUOTE -> {}

        node.type == GFMTokenTypes.GFM_AUTOLINK -> {
            val link = node.getTextInNode(content)
            withLink(LinkAnnotation.Url(link)) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(link)
                }
            }
        }

        node is LeafASTNode -> {
            val text = node.getTextInNode(content).let {
                if (trim) {
                    it.trim()
                } else {
                    it
                }.replace(BREAK_LINE_REGEX, "\n")
            }
            // Use custom pattern scanning for plain text
            appendTextWithCustomPatterns(text, rpStyleRules)
        }

        node.type == MarkdownElementTypes.EMPH -> {
            // Check for RP color rule for pattern "*" (single emphasis)
            val emphRule = rpStyleRules.find { it.pattern == "*" && it.enabled }
            val emphColor = emphRule?.let { runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrNull() }
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = emphColor ?: Color.Unspecified)) {
                node.children.trim(MarkdownTokenTypes.EMPH, 1).fastForEach {
                    appendMarkdownNodeContent(
                        node = it,
                        content = content,
                        inlineContents = inlineContents,
                        colorScheme = colorScheme,
                        density = density,
                        style = style,
                        onClickCitation = onClickCitation,
                        rpStyleRules = rpStyleRules,
                        streamingReveal = streamingReveal
                    )
                }
            }
        }

        node.type == MarkdownElementTypes.STRONG -> {
            // Check for RP color rule for pattern "**" (strong emphasis)
            val strongRule = rpStyleRules.find { it.pattern == "**" && it.enabled }
            val strongColor = strongRule?.let { runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrNull() }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = strongColor ?: Color.Unspecified)) {
                node.children.trim(MarkdownTokenTypes.EMPH, 2).fastForEach {
                    appendMarkdownNodeContent(
                        node = it,
                        content = content,
                        inlineContents = inlineContents,
                        colorScheme = colorScheme,
                        density = density,
                        style = style,
                        onClickCitation = onClickCitation,
                        rpStyleRules = rpStyleRules,
                        streamingReveal = streamingReveal
                    )
                }
            }
        }

        node.type == GFMElementTypes.STRIKETHROUGH -> {
            // Check for RP color rule for pattern "~~" (strikethrough)
            val strikeRule = rpStyleRules.find { it.pattern == "~~" && it.enabled }
            val strikeColor = strikeRule?.let { runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrNull() }
            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = strikeColor ?: Color.Unspecified)) {
                node.children.trim(GFMTokenTypes.TILDE, 2).fastForEach {
                    appendMarkdownNodeContent(
                        node = it,
                        content = content,
                        inlineContents = inlineContents,
                        colorScheme = colorScheme,
                        density = density,
                        style = style,
                        onClickCitation = onClickCitation,
                        rpStyleRules = rpStyleRules,
                        streamingReveal = streamingReveal
                    )
                }
            }
        }

        node.type == MarkdownElementTypes.INLINE_LINK -> {
            val linkDest =
                node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content) ?: ""
            val linkText = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(content)
                ?.trim { it == '[' || it == ']' } ?: linkDest
            if (linkText.startsWith("citation,")) {
                // 如果是引用，则特殊处理
                val domain = linkText.substringAfter("citation,")
                val id = linkDest
                if (id.length == 6) {
                    inlineContents.putIfAbsent(
                        "citation:$linkDest", InlineTextContent(
                            placeholder = Placeholder(
                                width = (domain.length * 7).sp,
                                height = 1.em,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                            ), children = {
                                Box(
                                    modifier = Modifier
                                        .clickable {
                                            onClickCitation(id.trim())
                                        }
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(colorScheme.tertiaryContainer.copy(0.2f)),
                                    contentAlignment = Alignment.Center) {
                                    Text(
                                        text = domain,
                                        modifier = Modifier.wrapContentSize(),
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            lineHeight = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = colorScheme.onTertiaryContainer,
                                            fontWeight = FontWeight.Thin
                                        ),
                                    )
                                }
                            })
                    )
                    appendInlineContent("citation:$linkDest")
                }
            } else if (linkDest.startsWith("content://")) {
                // Handle content:// URIs as downloadable files - looks like regular link
                val displayName = if (linkText.isNotEmpty() && !linkText.contains("/")) linkText else linkDest.substringAfterLast("/")
                val inlineKey = "download:$linkDest"
                inlineContents.putIfAbsent(
                    inlineKey, InlineTextContent(
                        placeholder = Placeholder(
                            width = (displayName.length * 12 + 32).sp, // Extra width to avoid clipping long download link text
                            height = (style.fontSize.value * 1.3f).sp, // Extra height for descenders (p, g, y)
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextBottom,
                        ), children = {
                            val context = LocalContext.current
                            val scope = rememberCoroutineScope()
                            Text(
                                text = displayName,
                                modifier = Modifier
                                    .clickable {
                                        Log.d("Markdown", "Download clicked: $displayName from $linkDest")
                                        val uri = linkDest.toUri()
                                        scope.launch {
                                            context.saveToDownloads(uri, displayName)
                                        }
                                    },
                                style = TextStyle(
                                    fontSize = style.fontSize,
                                    color = colorScheme.primary,
                                    textDecoration = TextDecoration.Underline
                                ),
                            )
                        })
                )
                appendInlineContent(inlineKey)
            } else {
                withLink(LinkAnnotation.Url(linkDest)) {
                    withStyle(
                        SpanStyle(
                            color = colorScheme.primary, textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(linkText)
                    }
                }
            }
        }

        node.type == MarkdownElementTypes.AUTOLINK -> {
            val links = node.children.trim(MarkdownTokenTypes.LT, 1).trim(MarkdownTokenTypes.GT, 1)
            links.fastForEach { link ->
                withLink(LinkAnnotation.Url(link.getTextInNode(content))) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(link.getTextInNode(content))
                    }
                }
            }
        }

        node.type == MarkdownElementTypes.CODE_SPAN -> {
            val code = node.getTextInNode(content).trim('`')
            // Check for RP color rule for pattern "`" (inline code)
            val codeRule = rpStyleRules.find { it.pattern == "`" && it.enabled }
            val codeColor = codeRule?.let { runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrNull() }
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 0.95.em,
                    background = colorScheme.secondaryContainer.copy(alpha = 0.2f),
                    color = codeColor ?: Color.Unspecified,
                )
            ) {
                append(isolateLtr(code))
            }
        }

        node.type == GFMElementTypes.INLINE_MATH -> {
            // formula as id
            val formula = node.getTextInNode(content)
            appendInlineContent(formula, "[Latex]")
            val (width, height) = with(density) {
                assumeLatexSize(
                    latex = formula, fontSize = style.fontSize.toPx()
                ).let {
                    it.width().toSp() to it.height().toSp()
                }
            }
            inlineContents.putIfAbsent(/* key = */ formula,/* value = */ InlineTextContent(
                placeholder = Placeholder(
                    width = width, height = height, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                ), children = {
                    MathInline(
                        latex = formula, modifier = Modifier
                    )
                })
            )
        }

        // 其他类型继续递归处理
        else -> {
            node.children.fastForEach {
            appendMarkdownNodeContent(
                    node = it,
                    content = content,
                    inlineContents = inlineContents,
                    colorScheme = colorScheme,
                    density = density,
                    style = style,
                    onClickCitation = onClickCitation,
                    rpStyleRules = rpStyleRules,
                    streamingReveal = streamingReveal
                )
            }
        }
    }
    applyStreamingRevealStyle(
        reveal = streamingReveal,
        sourceStart = node.startOffset,
        sourceEnd = node.endOffset,
        outputStart = outputStart,
        outputEnd = length
    )
}

private fun AnnotatedString.Builder.applyStreamingRevealStyle(
    reveal: StreamingTextReveal?,
    sourceStart: Int,
    sourceEnd: Int,
    outputStart: Int,
    outputEnd: Int
) {
    if (reveal == null || reveal.alpha >= 0.995f || sourceEnd <= sourceStart || outputEnd <= outputStart) return
    val overlapStart = maxOf(sourceStart, reveal.startOffset)
    val overlapEnd = minOf(sourceEnd, reveal.endOffset)
    if (overlapEnd <= overlapStart) return

    val sourceLength = sourceEnd - sourceStart
    val outputLength = outputEnd - outputStart
    val rangeStart = outputStart + ((overlapStart - sourceStart) * outputLength / sourceLength)
    val rangeEnd = outputStart + ((overlapEnd - sourceStart) * outputLength / sourceLength)
    if (rangeEnd <= rangeStart) return

    addStyle(
        style = SpanStyle(color = reveal.color.copy(alpha = reveal.alpha)),
        start = rangeStart.coerceIn(outputStart, outputEnd),
        end = rangeEnd.coerceIn(outputStart, outputEnd)
    )
}

private fun ASTNode.getTextInNode(text: String): String {
    return text.substring(startOffset, endOffset)
}

private fun ASTNode.getTextInNode(text: String, type: IElementType): String {
    var startOffset = -1
    var endOffset = -1
    children.fastForEach {
        if (it.type == type) {
            if (startOffset == -1) {
                startOffset = it.startOffset
            }
            endOffset = it.endOffset
        }
    }
    if (startOffset == -1 || endOffset == -1) {
        return ""
    }
    return text.substring(startOffset, endOffset)
}

private fun ASTNode.nextSibling(): ASTNode? {
    val brother = this.parent?.children ?: return null
    for (i in brother.indices) {
        if (brother[i] == this) {
            if (i + 1 < brother.size) {
                return brother[i + 1]
            }
        }
    }
    return null
}

private fun ASTNode.findChildOfTypeRecursive(vararg types: IElementType): ASTNode? {
    if (this.type in types) return this
    for (child in children) {
        val result = child.findChildOfTypeRecursive(*types)
        if (result != null) return result
    }
    return null
}

private fun ASTNode.traverseChildren(
    action: (ASTNode) -> Unit
) {
    children.fastForEach { child ->
        action(child)
        child.traverseChildren(action)
    }
}

private fun List<ASTNode>.trim(type: IElementType, size: Int): List<ASTNode> {
    if (this.isEmpty() || size <= 0) return this
    var start = 0
    var end = this.size
    // 从头裁剪
    var trimmed = 0
    while (start < end && trimmed < size && this[start].type == type) {
        start++
        trimmed++
    }
    // 从尾裁剪
    trimmed = 0
    while (end > start && trimmed < size && this[end - 1].type == type) {
        end--
        trimmed++
    }
    return this.subList(start, end)
}
