package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.withFrameMillis
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
import androidx.compose.ui.unit.Dp
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
import kotlin.math.floor
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

/**
 * Maximum content length that is still parsed synchronously on the UI thread for complete
 * (non-streaming) messages. Realistic assistant/tool messages are far below this; the cap only
 * exists as a safety valve so a single pathologically huge message (e.g. a large embedded base64
 * blob) can still fall back to background parsing instead of risking a long UI-thread hang.
 */
private const val SYNC_PARSE_MAX_LENGTH = 200_000

private const val MARKDOWN_AST_CACHE_SIZE = 64

/**
 * Process-wide LRU cache of parsed markdown ASTs keyed by the raw (pre-`preProcess`) content.
 *
 * This lets [MarkdownBlock] parse a complete message synchronously on first composition (so the
 * item has its correct height immediately, avoiding a scroll-anchor "jump") while paying the parse
 * cost at most once per unique message — repeated compositions (e.g. scrolling a turn off screen
 * and back) reuse the cached AST instantly.
 */
private val markdownAstCache = object : LinkedHashMap<String, Pair<String, ASTNode>>(
    16, 0.75f, /* accessOrder = */ true
) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, ASTNode>>): Boolean {
        return size > MARKDOWN_AST_CACHE_SIZE
    }
}

@Synchronized
private fun parseMarkdownCached(content: String): Pair<String, ASTNode> {
    markdownAstCache[content]?.let { return it }
    val preprocessed = preProcess(content)
    val astTree = parser.buildMarkdownTreeFromString(preprocessed)
    val result = preprocessed to astTree
    markdownAstCache[content] = result
    return result
}

/** Intrinsic pixel dimensions of an image, used to reserve its layout box before it decodes. */
private data class ImageDisplayInfo(val widthPx: Int, val heightPx: Int) {
    val aspectRatio: Float get() = widthPx.toFloat() / heightPx.toFloat()
}

/**
 * Cache of known image dimensions, keyed by model string. Populated either synchronously (local
 * files, via a header-only decode) or lazily when a remote image finishes loading.
 */
private val imageDisplayInfoCache = java.util.concurrent.ConcurrentHashMap<String, ImageDisplayInfo>()

/**
 * Best-effort intrinsic dimensions for [model], used to reserve the correct box for an image
 * *before* it decodes. Without this an image renders at its placeholder height and then jumps to
 * full size once loaded, shoving the LazyColumn scroll anchor (a visible "jump" when scrolling up
 * into a message that contains an image).
 *
 * For local `file://`, absolute-path and `content://` sources the size is read synchronously from
 * the image header (only bounds are decoded, not the pixels — cheap). Remote sources return a
 * previously learned size if available, otherwise null (we can't cheaply probe them).
 */
private fun resolveImageDisplayInfo(context: Context, model: String?): ImageDisplayInfo? {
    if (model.isNullOrBlank()) return null
    imageDisplayInfoCache[model]?.let { return it }
    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    try {
        when {
            model.startsWith("file://") || model.startsWith("/") -> {
                val path = if (model.startsWith("file://")) model.toUri().path else model
                if (path != null) android.graphics.BitmapFactory.decodeFile(path, options)
            }

            model.startsWith("content://") -> {
                context.contentResolver.openInputStream(model.toUri())?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream, null, options)
                }
            }

            else -> return null
        }
    } catch (_: Throwable) {
        return null
    }
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    val info = ImageDisplayInfo(options.outWidth, options.outHeight)
    imageDisplayInfoCache[model] = info
    return info
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
    val ranges: List<StreamingSettleRange>,
    val nowMillis: Long,
    val color: Color,
    val smoothedCharsPerSecond: Float
)

internal data class StreamingSettleRange(
    val startOffset: Int,
    val endOffset: Int,
    val revealedAtMillis: Long
)

private val LocalStreamingTextReveal = compositionLocalOf<StreamingTextReveal?> { null }
val LocalMarkdownParagraphSpacing = compositionLocalOf { 4.dp }
val LocalMarkdownWorkspaceId = compositionLocalOf<String?> { null }

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
    paragraphSpacing: Dp = 4.dp,
    streamingTextReveal: Boolean = false,
    workspaceId: String? = null,
    onExpandedStreamingCodeBlockChanged: (() -> Unit)? = null,
    onClickCitation: (String) -> Unit = {}
) {
    // Read rpStyleRules from settings
    val settings = LocalSettings.current
    val rpStyleRules = settings.displaySetting.rpStyleRules
    val contentColor = style.color.takeOrElse { LocalContentColor.current }
    val streamingPresentation = remember { StreamingTextPresentationState(content) }
    var displayContent by remember { mutableStateOf(content) }
    var settleRanges by remember { mutableStateOf(emptyList<StreamingSettleRange>()) }
    var streamingFrameMillis by remember { mutableStateOf(0L) }
    
    var (data, setData) = remember {
        // Complete (non-streaming) messages MUST parse synchronously so the block reports its
        // correct height on first composition. Deferring to a background parse renders the block
        // at ~0 height and then grows it a frame later; when the user scrolls up into a large
        // tool-usage turn, that growth shoves the LazyColumn scroll anchor and produces the
        // visible "scroll up, snap down, then re-follow" jump. Parsing is memoized in
        // [markdownAstCache], so this UI-thread cost is paid at most once per unique message.
        //
        // Only actively STREAMING content keeps the deferred empty-AST path: there the text
        // changes every frame, so parsing large content on the UI thread would drop frames. Very
        // small content parses synchronously even while streaming (cheap, avoids an initial flash),
        // and pathologically huge content always defers as an ANR safety valve.
        val parseSynchronously = displayContent.length < 4000 ||
            (!streamingTextReveal && displayContent.length <= SYNC_PARSE_MAX_LENGTH)
        if (parseSynchronously) {
            mutableStateOf(
                value = parseMarkdownCached(displayContent),
                policy = referentialEqualityPolicy(),
            )
        } else {
            val astTree = parser.buildMarkdownTreeFromString("")
            mutableStateOf(
                value = "" to astTree,
                policy = referentialEqualityPolicy(),
            )
        }
    }

    // 监听内容变化，重新解析AST树
    // 这里在后台线程解析AST树, 防止频繁更新的时候掉帧
    val updatedDisplayContent by rememberUpdatedState(displayContent)
    LaunchedEffect(Unit) {
        // The synchronous parse above already produced the AST for the initial content.
        // The first snapshotFlow emission mirrors that same content; without this guard
        // it would call setData with a new Pair instance and, due to referentialEqualityPolicy,
        // trigger a full redundant recomposition + re-measure of the whole markdown tree
        // right after the item first appears on screen (a visible stutter, amplified by the
        // multiple text bubbles present in tool-usage turns).
        var lastPreprocessed = data.first
        snapshotFlow { updatedDisplayContent }.distinctUntilChanged().mapLatest {
            parseMarkdownCached(it)
        }.catch { exception -> exception.printStackTrace() }.flowOn(Dispatchers.Default) // 在后台线程解析AST树
            .collect { parsed ->
                if (parsed.first != lastPreprocessed) {
                    lastPreprocessed = parsed.first
                    setData(parsed)
                }
            }
    }

    val (preprocessed, astTree) = data
    val blockDirection = rememberContentDirection(preprocessed)
    LaunchedEffect(content, streamingTextReveal) {
        if (!streamingTextReveal) {
            streamingPresentation.snapTo(content)
            displayContent = streamingPresentation.displayContent
            settleRanges = emptyList()
            return@LaunchedEffect
        }

        val now = withFrameMillis { it }
        streamingPresentation.acceptRawContent(content, now)
        displayContent = streamingPresentation.displayContent
        settleRanges = streamingPresentation.settleRanges
        streamingFrameMillis = now
        onExpandedStreamingCodeBlockChanged?.invoke()
    }
    LaunchedEffect(streamingTextReveal) {
        if (!streamingTextReveal) return@LaunchedEffect

        var previousFrameMillis = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val changed = streamingPresentation.step(
                nowMillis = now,
                elapsedMillis = (now - previousFrameMillis).coerceAtLeast(0L)
            )
            previousFrameMillis = now
            streamingFrameMillis = now
            if (changed) {
                displayContent = streamingPresentation.displayContent
                onExpandedStreamingCodeBlockChanged?.invoke()
            }
            settleRanges = streamingPresentation.settleRanges
        }
    }
    val streamingReveal = if (streamingTextReveal && settleRanges.isNotEmpty()) {
        StreamingTextReveal(
            ranges = settleRanges,
            nowMillis = streamingFrameMillis,
            color = contentColor,
            smoothedCharsPerSecond = streamingPresentation.smoothedCharsPerSecond
        )
    } else {
        null
    }

    // Provide rpStyleRules to entire tree via CompositionLocal
    CompositionLocalProvider(
        LocalRpStyleRules provides rpStyleRules,
        LocalStreamingTextReveal provides streamingReveal,
        LocalMarkdownParagraphSpacing provides paragraphSpacing,
        LocalMarkdownWorkspaceId provides workspaceId,
        LocalLayoutDirection provides blockDirection.toLayoutDirection(),
    ) {
        ProvideTextStyle(style) {
            Column(
                modifier = modifier
                    .then(
                        if (streamingTextReveal) {
                            Modifier.animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = 420f
                                )
                            )
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 4.dp)
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

internal class StreamingTextPresentationState(
    initialRawContent: String,
    nowMillis: Long = 0L
) {
    var rawContent: String = initialRawContent
        private set
    var displayContent: String = initialRawContent
        private set
    var settleRanges: List<StreamingSettleRange> = emptyList()
        private set

    var smoothedCharsPerSecond = STREAMING_DEFAULT_CHARS_PER_SECOND
        private set
    private var lastRawUpdateMillis = nowMillis
    private var firstPendingSinceMillis = 0L
    private var revealCarry = 0f
    private var lastRevealMillis = nowMillis
    private var currentRevealCharsPerSecond = STREAMING_DEFAULT_CHARS_PER_SECOND

    fun acceptRawContent(nextRawContent: String, nowMillis: Long): Boolean {
        if (nextRawContent == rawContent) {
            settleRanges = settleRanges.pruneStreamingSettleRanges(nowMillis)
            return false
        }

        if (!nextRawContent.startsWith(displayContent)) {
            snapTo(nextRawContent)
            lastRawUpdateMillis = nowMillis
            lastRevealMillis = nowMillis
            currentRevealCharsPerSecond = STREAMING_DEFAULT_CHARS_PER_SECOND
            return true
        }

        val previousRawContent = rawContent
        rawContent = nextRawContent

        if (nextRawContent.length > previousRawContent.length && nextRawContent.startsWith(previousRawContent)) {
            val elapsedMillis = (nowMillis - lastRawUpdateMillis).coerceAtLeast(1L)
            val appendedLength = nextRawContent.length - previousRawContent.length
            val instantCharsPerSecond = appendedLength * 1000f / elapsedMillis
            smoothedCharsPerSecond = smoothStreamingRate(
                previousCharsPerSecond = smoothedCharsPerSecond,
                instantCharsPerSecond = instantCharsPerSecond
            )
            if (firstPendingSinceMillis == 0L && displayContent.length < rawContent.length) {
                firstPendingSinceMillis = nowMillis
            }
        } else if (!nextRawContent.startsWith(previousRawContent)) {
            snapTo(nextRawContent)
            lastRawUpdateMillis = nowMillis
            lastRevealMillis = nowMillis
            currentRevealCharsPerSecond = STREAMING_DEFAULT_CHARS_PER_SECOND
            return true
        }

        lastRawUpdateMillis = nowMillis
        settleRanges = settleRanges.pruneStreamingSettleRanges(nowMillis)
        return false
    }

    fun step(nowMillis: Long, elapsedMillis: Long): Boolean {
        settleRanges = settleRanges.pruneStreamingSettleRanges(nowMillis)
        if (displayContent == rawContent) {
            firstPendingSinceMillis = 0L
            revealCarry = 0f
            currentRevealCharsPerSecond = approachStreamingRate(
                current = currentRevealCharsPerSecond,
                target = 0f,
                elapsedMillis = elapsedMillis,
                timeConstantMillis = STREAMING_DECEL_TIME_CONSTANT_MILLIS
            )
            return false
        }

        val pendingLength = rawContent.length - displayContent.length
        val pendingAgeMillis = if (firstPendingSinceMillis == 0L) 0L else nowMillis - firstPendingSinceMillis
        if (pendingAgeMillis < STREAMING_INITIAL_BUFFER_MILLIS && pendingLength < STREAMING_TINY_PENDING_LENGTH) {
            return false
        }

        val backlogMillis = pendingLength * 1000f / smoothedCharsPerSecond.coerceAtLeast(1f)
        val catchUpMultiplier = when {
            backlogMillis <= STREAMING_CATCH_UP_AFTER_MILLIS -> 1f
            else -> (backlogMillis / STREAMING_SMOOTHING_WINDOW_MILLIS).coerceIn(1f, STREAMING_MAX_CATCH_UP_MULTIPLIER)
        }
        val baseRevealChars = smoothedCharsPerSecond
            .coerceIn(STREAMING_MIN_CHARS_PER_SECOND, STREAMING_MAX_CHARS_PER_SECOND) * catchUpMultiplier

        val stalledMillis = (nowMillis - lastRawUpdateMillis - STREAMING_STALL_GRACE_MILLIS).coerceAtLeast(0L)
        val stallProgress = (stalledMillis / STREAMING_STALL_DECEL_MILLIS.toFloat()).coerceIn(0f, 1f)
        val stallEase = stallProgress * stallProgress * (3f - 2f * stallProgress)
        val targetRevealChars = baseRevealChars * (1f - (1f - STREAMING_STALL_MIN_MULTIPLIER) * stallEase)
        currentRevealCharsPerSecond = approachStreamingRate(
            current = currentRevealCharsPerSecond,
            target = targetRevealChars,
            elapsedMillis = elapsedMillis,
            timeConstantMillis = if (targetRevealChars > currentRevealCharsPerSecond) {
                STREAMING_ACCEL_TIME_CONSTANT_MILLIS
            } else {
                STREAMING_DECEL_TIME_CONSTANT_MILLIS
            }
        ).coerceIn(STREAMING_MIN_CHARS_PER_SECOND * STREAMING_STALL_MIN_MULTIPLIER, STREAMING_MAX_CHARS_PER_SECOND)

        revealCarry += currentRevealCharsPerSecond * elapsedMillis.coerceAtLeast(1L) / 1000f
        val revealBudget = floor(revealCarry).toInt().coerceAtMost(pendingLength)
        val starved = nowMillis - lastRevealMillis >= STREAMING_STARVED_REVEAL_MILLIS
        if (revealBudget <= 0 && !starved) {
            return false
        }

        val count = chooseStreamingRevealCount(
            pending = rawContent.substring(displayContent.length),
            budget = revealBudget.coerceAtLeast(if (starved) 1 else 0),
            starved = starved
        )
        if (count <= 0) {
            return false
        }

        val start = displayContent.length
        displayContent = rawContent.substring(0, start + count)
        revealCarry = (revealCarry - count).coerceAtLeast(0f)
        lastRevealMillis = nowMillis
        if (displayContent == rawContent) {
            firstPendingSinceMillis = 0L
        }
        val settledRanges = streamingSettleRangesForReveal(
            content = displayContent,
            revealStart = start,
            revealEnd = displayContent.length,
            nowMillis = nowMillis
        )
        settleRanges = (settleRanges + settledRanges)
            .mergeAdjacentStreamingSettleRanges()
            .pruneStreamingSettleRanges(nowMillis)
            .takeLast(STREAMING_MAX_SETTLE_RANGES)
        return true
    }

    fun snapTo(content: String) {
        rawContent = content
        displayContent = content
        settleRanges = emptyList()
        firstPendingSinceMillis = 0L
        revealCarry = 0f
        currentRevealCharsPerSecond = STREAMING_DEFAULT_CHARS_PER_SECOND
    }
}

private fun approachStreamingRate(
    current: Float,
    target: Float,
    elapsedMillis: Long,
    timeConstantMillis: Long,
): Float {
    if (timeConstantMillis <= 0L) return target
    val progress = (elapsedMillis.coerceAtLeast(1L) / timeConstantMillis.toFloat()).coerceIn(0f, 1f)
    return current + (target - current) * progress
}

internal fun smoothStreamingRate(
    previousCharsPerSecond: Float,
    instantCharsPerSecond: Float
): Float {
    val target = instantCharsPerSecond.coerceIn(STREAMING_MIN_CHARS_PER_SECOND, STREAMING_MAX_CHARS_PER_SECOND)
    return (previousCharsPerSecond * STREAMING_RATE_KEEP_WEIGHT + target * (1f - STREAMING_RATE_KEEP_WEIGHT))
        .coerceIn(STREAMING_MIN_CHARS_PER_SECOND, STREAMING_MAX_CHARS_PER_SECOND)
}

internal fun chooseStreamingRevealCount(
    pending: String,
    budget: Int,
    starved: Boolean
): Int {
    if (pending.isEmpty() || budget <= 0) return 0
    if (budget >= pending.length) return pending.length

    val cappedBudget = budget.coerceIn(1, pending.length)
    if (pending[cappedBudget - 1].isWhitespace()) {
        return cappedBudget
    }

    val previousBoundary = pending.streamingBoundaryAtOrBefore(cappedBudget)
    if (previousBoundary > 0 && previousBoundary >= cappedBudget - STREAMING_BOUNDARY_BACKTRACK) {
        return previousBoundary
    }

    val nextBoundary = pending.streamingBoundaryAfter(cappedBudget)
    if (nextBoundary in 1..(cappedBudget + STREAMING_BOUNDARY_LOOKAHEAD) && nextBoundary <= pending.length) {
        return nextBoundary
    }

    val firstWordEnd = pending.indexOfFirst { !it.isStreamingWordCharacter() }.let { if (it == -1) pending.length else it }
    return when {
        firstWordEnd <= STREAMING_SHORT_WORD_LENGTH && !starved -> 0
        firstWordEnd <= STREAMING_MEDIUM_WORD_LENGTH &&
            cappedBudget >= firstWordEnd - STREAMING_BOUNDARY_LOOKAHEAD -> firstWordEnd
        firstWordEnd > STREAMING_LONG_WORD_LENGTH -> cappedBudget
        starved -> cappedBudget
        else -> 0
    }
}

internal fun streamingSettleRangesForReveal(
    content: String,
    revealStart: Int,
    revealEnd: Int,
    nowMillis: Long
): List<StreamingSettleRange> {
    if (revealEnd <= revealStart) return emptyList()
    val safeStart = revealStart.coerceIn(0, content.length)
    val safeEnd = revealEnd.coerceIn(safeStart, content.length)
    val ranges = mutableListOf<StreamingSettleRange>()
    var index = safeStart

    while (index < safeEnd) {
        while (index < safeEnd && content[index].isWhitespace()) {
            index++
        }
        if (index >= safeEnd) break

        val start = index
        while (index < safeEnd && !content[index].isWhitespace()) {
            index++
        }
        ranges.add(StreamingSettleRange(start, index, nowMillis))
    }

    return ranges
}

private fun String.streamingBoundaryAtOrBefore(limit: Int): Int {
    val cappedLimit = limit.coerceIn(1, length)
    for (index in cappedLimit downTo 1) {
        if (this[index - 1].isStreamingBoundaryCharacter()) {
            return index
        }
    }
    return 0
}

private fun String.streamingBoundaryAfter(offset: Int): Int {
    val start = offset.coerceIn(1, length)
    for (index in start..length) {
        if (this[index - 1].isStreamingBoundaryCharacter()) {
            return index
        }
    }
    return 0
}

private fun List<StreamingSettleRange>.pruneStreamingSettleRanges(nowMillis: Long): List<StreamingSettleRange> {
    return filter { range -> nowMillis - range.revealedAtMillis < STREAMING_SETTLE_MAX_MILLIS }
}

private fun List<StreamingSettleRange>.mergeAdjacentStreamingSettleRanges(): List<StreamingSettleRange> {
    if (isEmpty()) return emptyList()
    return sortedWith(compareBy<StreamingSettleRange> { it.revealedAtMillis }.thenBy { it.startOffset })
        .fold(mutableListOf()) { merged, range ->
            val previous = merged.lastOrNull()
            if (previous != null &&
                previous.revealedAtMillis == range.revealedAtMillis &&
                previous.endOffset == range.startOffset
            ) {
                merged[merged.lastIndex] = previous.copy(endOffset = range.endOffset)
            } else {
                merged.add(range)
            }
            merged
        }
}

private fun Char.isStreamingWordCharacter(): Boolean {
    return isLetterOrDigit() || this == '_' || this == '-' || this == '\''
}

private fun Char.isStreamingBoundaryCharacter(): Boolean {
    return isWhitespace() || this in ".,;:!?)]}\"'"
}

private const val STREAMING_INITIAL_BUFFER_MILLIS = 64L
private const val STREAMING_SMOOTHING_WINDOW_MILLIS = 220f
private const val STREAMING_CATCH_UP_AFTER_MILLIS = 420f
private const val STREAMING_SETTLE_MIN_MILLIS = 180L
private const val STREAMING_SETTLE_MAX_MILLIS = 360L
private const val STREAMING_SETTLE_ALPHA_FAST = 0.42f
private const val STREAMING_SETTLE_ALPHA_SLOW = 0.65f
private const val STREAMING_SPEED_SLOW_THRESHOLD = 30f
private const val STREAMING_SPEED_FAST_THRESHOLD = 150f
private const val STREAMING_STARVED_REVEAL_MILLIS = 180L
private const val STREAMING_MIN_CHARS_PER_SECOND = 18f
private const val STREAMING_DEFAULT_CHARS_PER_SECOND = 44f
private const val STREAMING_MAX_CHARS_PER_SECOND = 220f
private const val STREAMING_MAX_CATCH_UP_MULTIPLIER = 3.2f
private const val STREAMING_RATE_KEEP_WEIGHT = 0.82f
private const val STREAMING_ACCEL_TIME_CONSTANT_MILLIS = 140L
private const val STREAMING_DECEL_TIME_CONSTANT_MILLIS = 620L
private const val STREAMING_STALL_GRACE_MILLIS = 140L
private const val STREAMING_STALL_DECEL_MILLIS = 900L
private const val STREAMING_STALL_MIN_MULTIPLIER = 0.18f
private const val STREAMING_TINY_PENDING_LENGTH = 4
private const val STREAMING_SHORT_WORD_LENGTH = 7
private const val STREAMING_MEDIUM_WORD_LENGTH = 10
private const val STREAMING_LONG_WORD_LENGTH = 12
private const val STREAMING_BOUNDARY_LOOKAHEAD = 3
private const val STREAMING_BOUNDARY_BACKTRACK = 2
private const val STREAMING_MAX_SETTLE_RANGES = 12

// for debug
private fun dumpAst(node: ASTNode, text: String, indent: String = "") {
    println("$indent${node.type} ${if (node.children.isEmpty()) node.getTextInNode(text) else ""} | ${node::class.simpleName}")
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
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.Medium,
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
            val originalImageUrl = node.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(content) ?: ""
            
            var imageModel: String = originalImageUrl
            val workspaceId = LocalMarkdownWorkspaceId.current
            if (workspaceId != null && originalImageUrl.startsWith("/workspace/")) {
                val context = LocalContext.current
                val localFile = java.io.File(context.filesDir, "workspaces/$workspaceId/files" + originalImageUrl.removePrefix("/workspace"))
                if (localFile.exists()) {
                    imageModel = "file://" + localFile.absolutePath
                }
            }

            val imageContext = LocalContext.current
            val density = LocalDensity.current
            // Reserve the image's real box up front so it doesn't grow from a placeholder height to
            // full size on load and shove the scroll anchor. Recomputed only when the model changes;
            // remote images learn their size via onSizeResolved below. The reserved width is capped
            // to the image's intrinsic width so the displayed size matches the previous behaviour
            // (Coil's ContentScale.Fit never upscales past the natural size).
            var reservedInfo by remember(imageModel) {
                mutableStateOf(resolveImageDisplayInfo(imageContext, imageModel))
            }

            Column(
                modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val info = reservedInfo
                val reservedModifier = if (info != null && info.widthPx > 0 && info.heightPx > 0) {
                    val intrinsicWidthDp = with(density) { info.widthPx.toDp() }
                    Modifier
                        .widthIn(min = 120.dp, max = intrinsicWidthDp)
                        .fillMaxWidth()
                        .aspectRatio(info.aspectRatio)
                } else {
                    Modifier
                        .widthIn(min = 120.dp)
                        .heightIn(min = 120.dp)
                }
                // 这里可以使用Coil等图片加载库加载图片
                ZoomableAsyncImage(
                    model = imageModel,
                    contentDescription = altText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .then(reservedModifier),
                    onSizeResolved = { resolved ->
                        if (reservedInfo == null) {
                            val newInfo = ImageDisplayInfo(
                                widthPx = resolved.width.toInt(),
                                heightPx = resolved.height.toInt(),
                            )
                            imageDisplayInfoCache[imageModel] = newInfo
                            reservedInfo = newInfo
                        }
                    },
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
            val normalizedLanguage = normalizeCodeBlockLanguage(language)
            val hasEnd = node.findChildOfTypeRecursive(MarkdownTokenTypes.CODE_FENCE_END) != null

            // Mermaid diagrams: render directly without HighlightCodeBlock wrapper
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                if (hasEnd && normalizedLanguage == "mermaid") {
                    Mermaid(
                        code = code,
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .fillMaxWidth(),
                    )
                } else {
                    HighlightCodeBlock(
                        code = code,
                        language = normalizedLanguage,
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
        val paragraphSpacing = LocalMarkdownParagraphSpacing.current
        Text(
            text = annotatedString,
            modifier = modifier.then(
                if (node.nextRenderableSibling() != null && paragraphSpacing > 0.dp) {
                    Modifier.padding(bottom = paragraphSpacing)
                } else {
                    Modifier
                }
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
                withStyle(
                    SpanStyle(
                        color = colorScheme.primary.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
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
                                        .background(colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center) {
                                    Text(
                                        text = domain,
                                        modifier = Modifier.wrapContentSize(),
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            lineHeight = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Light
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
                                    color = colorScheme.primary.copy(alpha = 0.9f),
                                    textDecoration = TextDecoration.Underline,
                                    fontWeight = FontWeight.Medium
                                ),
                            )
                        })
                )
                appendInlineContent(inlineKey)
            } else {
                withLink(LinkAnnotation.Url(linkDest)) {
                    withStyle(
                        SpanStyle(
                            color = colorScheme.primary.copy(alpha = 0.9f),
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Medium
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
                    withStyle(
                        SpanStyle(
                            color = colorScheme.primary.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
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
    if (reveal == null || reveal.ranges.isEmpty() || sourceEnd <= sourceStart || outputEnd <= outputStart) return

    val sourceLength = sourceEnd - sourceStart
    val outputLength = outputEnd - outputStart
    
    // Compute speed-adaptive constants
    val speedProgress = ((reveal.smoothedCharsPerSecond - STREAMING_SPEED_SLOW_THRESHOLD) / 
        (STREAMING_SPEED_FAST_THRESHOLD - STREAMING_SPEED_SLOW_THRESHOLD)).coerceIn(0f, 1f)
    
    // Faster speed = longer settle duration (for softer fade during bursts)
    val settleDurationMillis = STREAMING_SETTLE_MIN_MILLIS + 
        (STREAMING_SETTLE_MAX_MILLIS - STREAMING_SETTLE_MIN_MILLIS) * speedProgress
        
    // Faster speed = lower start alpha (for more visible "wave" effect)
    val startAlpha = STREAMING_SETTLE_ALPHA_SLOW + 
        (STREAMING_SETTLE_ALPHA_FAST - STREAMING_SETTLE_ALPHA_SLOW) * speedProgress

    reveal.ranges.fastForEach { settleRange ->
        val ageMillis = reveal.nowMillis - settleRange.revealedAtMillis
        if (ageMillis < 0 || ageMillis >= settleDurationMillis) return@fastForEach

        val overlapStart = maxOf(sourceStart, settleRange.startOffset)
        val overlapEnd = minOf(sourceEnd, settleRange.endOffset)
        if (overlapEnd <= overlapStart) return@fastForEach

        val progress = (ageMillis / settleDurationMillis).coerceIn(0f, 1f)
        // Smooth-step curve: 3t^2 - 2t^3
        val easedProgress = progress * progress * (3f - 2f * progress)
        val alpha = startAlpha + (1f - startAlpha) * easedProgress
        if (alpha >= 0.995f) return@fastForEach

        val rangeStart = outputStart + ((overlapStart - sourceStart) * outputLength / sourceLength)
        val rangeEnd = outputStart + ((overlapEnd - sourceStart) * outputLength / sourceLength)
        if (rangeEnd <= rangeStart) return@fastForEach

        addStyle(
            style = SpanStyle(color = reveal.color.copy(alpha = alpha)),
            start = rangeStart.coerceIn(outputStart, outputEnd),
            end = rangeEnd.coerceIn(outputStart, outputEnd)
        )
    }
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

private fun ASTNode.nextRenderableSibling(): ASTNode? {
    val siblings = this.parent?.children ?: return null
    val currentIndex = siblings.indexOf(this).takeIf { it >= 0 } ?: return null
    return siblings
        .drop(currentIndex + 1)
        .firstOrNull { it.type != MarkdownTokenTypes.EOL }
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
