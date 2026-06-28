package me.rerere.rikkahub.ui.components.richtext

import me.rerere.rikkahub.ui.context.LocalChatAnimationsEnabled
import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rerere.highlight.HighlightText
import me.rerere.highlight.LocalHighlighter
import me.rerere.highlight.buildHighlightText
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.modifier.fadeEdges
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.AtomOneDarkPalette
import me.rerere.rikkahub.ui.theme.AtomOneLightPalette
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.base64Encode
import kotlin.time.Clock

private const val COLLAPSED_PEEK_MAX_HEIGHT = 108
internal const val CODE_BLOCK_BODY_TAG = "code_block_body"
internal const val CODE_BLOCK_FOOTER_TAG = "code_block_footer"

enum class CodeBlockState(val expanded: Boolean) {
    Collapsed(false),
    Preview(false),
    Expanded(true)
}

internal enum class CodeBlockFooterAction {
    Expand,
    Collapse
}

internal fun initialCodeBlockState(
    autoCollapse: Boolean,
    completeCodeBlock: Boolean,
): CodeBlockState = when {
    !completeCodeBlock -> CodeBlockState.Preview
    autoCollapse -> CodeBlockState.Collapsed
    else -> CodeBlockState.Expanded
}

internal fun codeBlockFooterAction(expandState: CodeBlockState): CodeBlockFooterAction {
    return if (expandState == CodeBlockState.Expanded) {
        CodeBlockFooterAction.Collapse
    } else {
        CodeBlockFooterAction.Expand
    }
}

internal fun codeBlockMaxHeight(expandState: CodeBlockState): Int? {
    return when (expandState) {
        CodeBlockState.Collapsed,
        CodeBlockState.Preview -> COLLAPSED_PEEK_MAX_HEIGHT
        CodeBlockState.Expanded -> null
    }
}

internal fun updatePreviewAutoFollowPaused(
    currentlyPaused: Boolean,
    isAtBottom: Boolean,
    scrollDelta: Int,
    userScrollInProgress: Boolean,
    programmaticScrollInProgress: Boolean,
): Boolean = when {
    isAtBottom -> false
    userScrollInProgress && !programmaticScrollInProgress && scrollDelta < 0 -> true
    else -> currentlyPaused
}

@Composable
fun HighlightCodeBlock(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
    completeCodeBlock: Boolean = true,
    onExpandedStreamingContentChanged: (() -> Unit)? = null,
    style: TextStyle? = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
) {
    val darkMode = LocalDarkMode.current
    val colorPalette = if (darkMode) AtomOneDarkPalette else AtomOneLightPalette
    val normalizedLanguage = remember(language) { normalizeCodeBlockLanguage(language) }
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val navController = if (normalizedLanguage == "html") LocalNavController.current else null
    val context = LocalContext.current
    val settings = LocalSettings.current
    val effectiveDisplay = settings.getEffectiveDisplaySetting()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val colorScheme = MaterialTheme.colorScheme
    val shellColor = colorScheme.surfaceContainerHigh
    val headerColor = colorScheme.surfaceContainerHighest
    val bodyColor = colorScheme.surfaceContainerLow
    val footerColor = bodyColor
    val outlineColor = colorScheme.outline.copy(alpha = 0.18f)
    val actionTextColor = colorScheme.onSurfaceVariant
    
    // Get code font from settings
    val codeFontFamily = me.rerere.rikkahub.ui.theme.rememberFontFamilyFromConfig(effectiveDisplay.fontSettings.codeFont)

    // Determine initial state based on generation status
    // When generating (!completeCodeBlock): Preview (show code with fade)
    // When complete: Collapsed (banner) or Expanded (auto-collapse setting)
    var expandState by remember(effectiveDisplay.codeBlockAutoCollapse, completeCodeBlock) {
        mutableStateOf(
            initialCodeBlockState(
                autoCollapse = effectiveDisplay.codeBlockAutoCollapse,
                completeCodeBlock = completeCodeBlock
            )
        )
    }
    var previewAutoFollowPaused by remember(completeCodeBlock, expandState) { mutableStateOf(false) }
    var programmaticScrollInProgress by remember { mutableStateOf(false) }
    var previousScrollValue by remember(completeCodeBlock, expandState) { mutableIntStateOf(0) }
    var previousCodeLength by remember { mutableIntStateOf(0) }
    val autoWrap = effectiveDisplay.codeBlockAutoWrap
    val footerAction = codeBlockFooterAction(expandState)
    val footerText = if (footerAction == CodeBlockFooterAction.Collapse) {
        stringResource(id = R.string.code_block_collapse)
    } else {
        stringResource(id = R.string.code_block_expand)
    }

    LaunchedEffect(expandState, completeCodeBlock) {
        if (completeCodeBlock || expandState != CodeBlockState.Preview) {
            previewAutoFollowPaused = false
            previousScrollValue = verticalScrollState.value
        }
    }

    LaunchedEffect(expandState, completeCodeBlock, verticalScrollState) {
        if (!completeCodeBlock && expandState == CodeBlockState.Preview) {
            snapshotFlow {
                Triple(
                    verticalScrollState.value,
                    verticalScrollState.maxValue,
                    verticalScrollState.isScrollInProgress
                )
            }.collect { (scrollValue, maxValue, isScrollInProgress) ->
                val isAtBottom = scrollValue >= maxValue
                previewAutoFollowPaused = updatePreviewAutoFollowPaused(
                    currentlyPaused = previewAutoFollowPaused,
                    isAtBottom = isAtBottom,
                    scrollDelta = scrollValue - previousScrollValue,
                    userScrollInProgress = isScrollInProgress,
                    programmaticScrollInProgress = programmaticScrollInProgress
                )
                previousScrollValue = scrollValue
            }
        }
    }

    var pulseTrigger by remember { mutableIntStateOf(0) }
    val pulseAlpha by animateFloatAsState(
        targetValue = if (pulseTrigger > 0) 0.88f else 1f,
        animationSpec = tween(durationMillis = 110, easing = LinearOutSlowInEasing),
        finishedListener = {
            if (it == 0.88f) pulseTrigger = 0
        },
        label = "code_block_pulse_alpha"
    )

    LaunchedEffect(code, completeCodeBlock, expandState, previewAutoFollowPaused) {
        val codeGrew = code.length > previousCodeLength
        if (codeGrew && !completeCodeBlock) {
            pulseTrigger = 1
        }
        previousCodeLength = code.length
        if (!codeGrew || completeCodeBlock) {
            return@LaunchedEffect
        }

        when (expandState) {
            CodeBlockState.Preview -> {
                if (!previewAutoFollowPaused) {
                    programmaticScrollInProgress = true
                    try {
                        verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
                    } finally {
                        programmaticScrollInProgress = false
                    }
                }
            }
            CodeBlockState.Expanded -> onExpandedStreamingContentChanged?.invoke()
            CodeBlockState.Collapsed -> Unit
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(code.toByteArray())
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val languageLabel = remember(normalizedLanguage) {
        getLanguageDisplayName(normalizedLanguage).lowercase()
    }

    fun toggle() {
        expandState = when {
            // When generating: toggle between Preview and Expanded
            !completeCodeBlock -> {
                if (expandState == CodeBlockState.Expanded) CodeBlockState.Preview
                else CodeBlockState.Expanded
            }
            // When complete: toggle between Collapsed and Expanded
            else -> {
                if (expandState == CodeBlockState.Collapsed) CodeBlockState.Expanded
                else CodeBlockState.Collapsed
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = AppShapes.InputField,
        color = shellColor,
        contentColor = colorScheme.onSurface,
        border = BorderStroke(1.dp, outlineColor),
    ) {
        val chatAnimationsEnabled = LocalChatAnimationsEnabled.current
        Column(
            modifier = Modifier
                .clipToBounds()
                .then(
                    if (chatAnimationsEnabled) {
                        Modifier.animateContentSize(
                            animationSpec = tween(
                                durationMillis = 180,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    } else {
                        Modifier
                    }
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                CodeBlockHeader(
                    languageLabel = languageLabel,
                    showPreview = normalizedLanguage == "html",
                    actionTextColor = actionTextColor,
                    onSave = {
                        val extension = getFileExtension(normalizedLanguage)
                        createDocumentLauncher.launch(
                            "code_${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())}.$extension"
                        )
                    },
                    onCopy = {
                        scope.launch {
                            clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("code", code)))
                        }
                    },
                    onPreview = {
                        navController?.navigate(Screen.WebView(content = code.base64Encode()))
                    },
                    hapticsEnabled = settings.displaySetting.enableUIHaptics
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = pulseAlpha }
                    .background(bodyColor)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                CodeBlockText(
                    code = code,
                    language = normalizedLanguage,
                    expandState = expandState,
                    autoWrap = autoWrap,
                    horizontalScrollState = horizontalScrollState,
                    verticalScrollState = verticalScrollState,
                    colorPalette = colorPalette,
                    codeFontFamily = codeFontFamily,
                    style = style
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(footerColor)
            ) {
                CodeBlockFooter(
                    expanded = expandState.expanded,
                    footerText = footerText,
                    footerAction = footerAction,
                    onToggle = {
                        haptics.perform(HapticPattern.Pop)
                        toggle()
                    }
                )
            }
        }
    }
}

@Composable
private fun CodeBlockHeader(
    languageLabel: String,
    showPreview: Boolean,
    actionTextColor: Color,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onPreview: () -> Unit,
    hapticsEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = languageLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = actionTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier.height(28.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CodeBlockHeaderActions(
                showPreview = showPreview,
                actionTextColor = actionTextColor,
                onSave = onSave,
                onCopy = onCopy,
                onPreview = onPreview,
                hapticsEnabled = hapticsEnabled
            )
        }
    }
}

@Composable
private fun CodeBlockHeaderActions(
    showPreview: Boolean,
    actionTextColor: Color,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onPreview: () -> Unit,
    hapticsEnabled: Boolean,
) {
    CodeBlockActionButton(
        icon = Icons.Rounded.Download,
        onClick = onSave,
        contentDescription = stringResource(id = R.string.chat_page_save),
        tint = actionTextColor,
        hapticsEnabled = hapticsEnabled
    )
    CodeBlockActionButton(
        icon = Icons.Rounded.ContentCopy,
        onClick = onCopy,
        contentDescription = stringResource(id = R.string.code_block_copy),
        tint = actionTextColor,
        hapticsEnabled = hapticsEnabled
    )
    if (showPreview) {
        CodeBlockActionButton(
            icon = Icons.Rounded.PlayArrow,
            onClick = onPreview,
            contentDescription = stringResource(id = R.string.code_block_preview),
            tint = MaterialTheme.colorScheme.primary,
            hapticsEnabled = hapticsEnabled
        )
    }
}

@Composable
internal fun CodeBlockActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescription: String?,
    tint: Color,
    hapticsEnabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "code_action_scale"
    )
    val haptics = rememberPremiumHaptics(enabled = hapticsEnabled)

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(28.dp)
            .clip(AppShapes.ButtonPill)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = tint
        )
    }
}

@Composable
private fun CodeBlockText(
    code: String,
    language: String,
    expandState: CodeBlockState,
    autoWrap: Boolean,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
    verticalScrollState: androidx.compose.foundation.ScrollState,
    colorPalette: me.rerere.highlight.HighlightTextColorPalette,
    codeFontFamily: FontFamily?,
    style: TextStyle?,
) {
    val textStyle = LocalTextStyle.current.merge(style)
    val previewLikeState = expandState != CodeBlockState.Expanded
    val maxHeight = codeBlockMaxHeight(expandState)

    val contentModifier = Modifier
        .testTag(CODE_BLOCK_BODY_TAG)
        .fillMaxWidth()
        .let {
            when {
                maxHeight != null && expandState == CodeBlockState.Preview -> {
                    it
                        .fadeEdges(fadeTop = true, fadeBottom = true)
                        .heightIn(max = maxHeight.dp)
                        .verticalScroll(verticalScrollState)
                }
                maxHeight != null -> {
                    it
                        .fadeEdges(fadeTop = false, fadeBottom = true)
                        .heightIn(max = maxHeight.dp)
                }
                else -> it
            }
        }

    Column(modifier = contentModifier) {
        SelectionContainer {
            HighlightText(
                code = code,
                language = language,
                modifier = Modifier.then(
                    if (autoWrap || previewLikeState && expandState == CodeBlockState.Collapsed) {
                        Modifier
                    } else {
                        Modifier.horizontalScroll(horizontalScrollState)
                    }
                ),
                fontSize = textStyle.fontSize,
                lineHeight = textStyle.lineHeight,
                colors = colorPalette,
                overflow = TextOverflow.Visible,
                softWrap = autoWrap || expandState == CodeBlockState.Collapsed,
                fontFamily = codeFontFamily ?: FontFamily.Monospace
            )
        }
    }
}

@Composable
internal fun CodeBlockFooter(
    expanded: Boolean,
    footerText: String,
    footerAction: CodeBlockFooterAction,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "code_footer_scale"
    )

    Row(
        modifier = Modifier
            .testTag(CODE_BLOCK_FOOTER_TAG)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onToggle
            )
            .padding(vertical = 12.dp)
            .semantics {
                role = Role.Button
                stateDescription = footerAction.name
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = footerText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun normalizeCodeBlockLanguage(language: String): String {
    val token = language
        .lineSequence()
        .firstOrNull()
        ?.trim()
        ?.substringBefore(' ')
        ?.substringBefore('\t')
        ?.trim('.', '{', '}', ',', ';', ':', '"', '\'')
        ?: ""
    val normalized = token
        .removePrefix("language-")
        .removePrefix("lang-")
        .lowercase()

    return when (normalized) {
        "", "plain", "text", "txt", "none" -> "plaintext"
        "kt", "kts" -> "kotlin"
        "js", "mjs", "cjs" -> "javascript"
        "ts" -> "typescript"
        "py", "py3" -> "python"
        "rb" -> "ruby"
        "rs" -> "rust"
        "golang" -> "go"
        "sh", "shell", "zsh" -> "bash"
        "ps", "pwsh" -> "powershell"
        "c++" -> "cpp"
        "c#", "cs" -> "csharp"
        "objective-c", "objc" -> "objectivec"
        "md" -> "markdown"
        "yml" -> "yaml"
        "jsonl" -> "json"
        "dockerfile" -> "docker"
        "plantuml" -> "plant-uml"
        else -> normalized
    }
}

private fun getLanguageDisplayName(language: String): String {
    return when (language.lowercase()) {
        "kotlin", "kt" -> "Kotlin"
        "java" -> "Java"
        "python", "py" -> "Python"
        "javascript", "js" -> "JavaScript"
        "typescript", "ts" -> "TypeScript"
        "cpp", "c++" -> "C++"
        "csharp", "c#" -> "C#"
        "c" -> "C"
        "html" -> "HTML"
        "css" -> "CSS"
        "xml" -> "XML"
        "json" -> "JSON"
        "yaml", "yml" -> "YAML"
        "markdown", "md" -> "Markdown"
        "sql" -> "SQL"
        "sh", "bash", "shell" -> "Shell"
        "swift" -> "Swift"
        "rust", "rs" -> "Rust"
        "go" -> "Go"
        "ruby", "rb" -> "Ruby"
        "php" -> "PHP"
        "dart" -> "Dart"
        "lua" -> "Lua"
        "r" -> "R"
        "scala" -> "Scala"
        "groovy" -> "Groovy"
        "perl" -> "Perl"
        "haskell", "hs" -> "Haskell"
        "clojure", "clj" -> "Clojure"
        "elixir", "ex" -> "Elixir"
        "erlang", "erl" -> "Erlang"
        "docker", "dockerfile" -> "Dockerfile"
        "powershell" -> "PowerShell"
        "plant-uml" -> "PlantUML"
        "toml" -> "TOML"
        "ini" -> "INI"
        "graphql", "gql" -> "GraphQL"
        "mermaid" -> "Mermaid"
        else -> language.replaceFirstChar { it.uppercaseChar() }
    }
}

private fun getFileExtension(language: String): String {
    return when (language.lowercase()) {
        "kotlin" -> "kt"
        "java" -> "java"
        "python" -> "py"
        "javascript" -> "js"
        "typescript" -> "ts"
        "cpp", "c++" -> "cpp"
        "csharp", "c#" -> "cs"
        "c" -> "c"
        "html" -> "html"
        "css" -> "css"
        "xml" -> "xml"
        "json" -> "json"
        "yaml", "yml" -> "yml"
        "markdown", "md" -> "md"
        "sql" -> "sql"
        "sh", "bash" -> "sh"
        "powershell" -> "ps1"
        "docker", "dockerfile" -> "Dockerfile"
        else -> "txt"
    }
}



@Composable
fun rememberHighlightCodeVisualTransformation(
    code: String,
    language: String,
): VisualTransformation {
    val highlighter = LocalHighlighter.current
    val darkMode = LocalDarkMode.current
    val colorPalette = if (darkMode) AtomOneDarkPalette else AtomOneLightPalette
    val normalizedLanguage = remember(language) { normalizeCodeBlockLanguage(language) }
    
    val highlighted by produceState<AnnotatedString?>(initialValue = null, code, normalizedLanguage, darkMode) {
        try {
            val tokens = highlighter.highlight(code, normalizedLanguage)
            value = buildAnnotatedString {
                tokens.forEach { token ->
                    buildHighlightText(token, colorPalette)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            value = AnnotatedString(code)
        }
    }

    return remember(highlighted) {
        VisualTransformation { text ->
            val currentHighlight = highlighted
            if (currentHighlight != null && currentHighlight.text == text.text) {
                TransformedText(
                    text = currentHighlight,
                    offsetMapping = OffsetMapping.Identity
                )
            } else {
                TransformedText(
                    text = text,
                    offsetMapping = OffsetMapping.Identity
                )
            }
        }
    }
}
