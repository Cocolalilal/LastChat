package me.rerere.rikkahub.ui.components.richtext

import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.CoroutineScope
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
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.AtomOneDarkPalette
import me.rerere.rikkahub.ui.theme.AtomOneLightPalette
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.base64Encode
import kotlin.time.Clock

private const val PREVIEW_MAX_HEIGHT = 200
private const val FADE_HEIGHT = 48f

enum class CodeBlockState(val expanded: Boolean) {
    Collapsed(false),
    Preview(true),
    Expanded(true)
}

@Composable
fun HighlightCodeBlock(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
    completeCodeBlock: Boolean = true,
    style: TextStyle? = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
) {
    val darkMode = LocalDarkMode.current
    val colorPalette = if (darkMode) AtomOneDarkPalette else AtomOneLightPalette
    val horizontalScrollState = rememberScrollState()
    val verticalScrollState = rememberScrollState()
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val context = LocalContext.current
    val settings = LocalSettings.current
    val effectiveDisplay = settings.getEffectiveDisplaySetting()
    
    // Get code font from settings
    val codeFontFamily = me.rerere.rikkahub.ui.theme.rememberFontFamilyFromConfig(effectiveDisplay.fontSettings.codeFont)

    // Determine initial state based on generation status
    // When generating (!completeCodeBlock): Preview (show code with fade)
    // When complete: Collapsed (banner) or Expanded (auto-collapse setting)
    var expandState by remember(effectiveDisplay.codeBlockAutoCollapse, completeCodeBlock) {
        mutableStateOf(
            when {
                !completeCodeBlock -> CodeBlockState.Preview // Still generating - show preview
                effectiveDisplay.codeBlockAutoCollapse -> CodeBlockState.Collapsed
                else -> CodeBlockState.Expanded
            }
        )
    }
    val autoWrap = effectiveDisplay.codeBlockAutoWrap

    // Auto-scroll to bottom when generating (like reasoning card)
    LaunchedEffect(code, completeCodeBlock, expandState) {
        if (!completeCodeBlock && expandState == CodeBlockState.Preview) {
            verticalScrollState.animateScrollTo(verticalScrollState.maxValue)
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

    // Get display name for the language
    val languageDisplayName = remember(language) {
        getLanguageDisplayName(language)
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
        shape = AppShapes.MessageBubbleInner, // Optical roundness inside message bubbles (20dp - 12dp = 8dp)
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .clipToBounds()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.7f,
                        stiffness = 300f
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Physics-based header with press feedback - always full width
            val headerInteractionSource = remember { MutableInteractionSource() }
            val isHeaderPressed by headerInteractionSource.collectIsPressedAsState()
            val headerScale by animateFloatAsState(
                targetValue = if (isHeaderPressed) 0.97f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.4f,
                    stiffness = 400f
                ),
                label = "header_scale"
            )
            val headerAlpha by animateFloatAsState(
                targetValue = if (isHeaderPressed) 0.7f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = 300f
                ),
                label = "header_alpha"
            )

            // Header row - always full width with icons at far right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = headerScale
                        scaleY = headerScale
                        alpha = headerAlpha
                    }
                    // No clip needed - outer shape is already small (8dp)
                    .clickable(
                        onClick = { toggle() },
                        indication = LocalIndication.current,
                        interactionSource = headerInteractionSource
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .semantics { role = Role.Button },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left side: Icon and title
                Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$languageDisplayName Snippet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Spacer to push icons to the right
                Spacer(modifier = Modifier.weight(1f))
                
                // Right side: Action icons (always visible) + chevron
                // Copy icon
                IconButton(
                    onClick = {
                        scope.launch {
                            clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("code", code)))
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = stringResource(id = R.string.code_block_copy),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Download icon
                IconButton(
                    onClick = {
                        val extension = getFileExtension(language)
                        createDocumentLauncher.launch(
                            "code_${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())}.$extension"
                        )
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = stringResource(id = R.string.chat_page_save),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Play icon (HTML only)
                if (language.lowercase() == "html") {
                    IconButton(
                        onClick = {
                            navController.navigate(Screen.WebView(content = code.base64Encode()))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(id = R.string.code_block_preview),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                // Expand/collapse chevron
                Icon(
                    imageVector = if (expandState.expanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // Code content with fade gradients (only when expanded)
            if (expandState.expanded) {
                val textStyle = LocalTextStyle.current.merge(style)
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (expandState == CodeBlockState.Preview) {
                                it
                                    .graphicsLayer { alpha = 0.99f } // Trigger offscreen rendering for mask
                                    .drawWithCache {
                                        // Create top and bottom fade gradients
                                        val brush = Brush.verticalGradient(
                                            startY = 0f,
                                            endY = size.height,
                                            colorStops = arrayOf(
                                                0.0f to Color.Transparent,
                                                (FADE_HEIGHT / size.height).coerceIn(0f, 0.3f) to Color.Black,
                                                (1 - FADE_HEIGHT / size.height).coerceIn(0.7f, 1f) to Color.Black,
                                                1.0f to Color.Transparent
                                            )
                                        )
                                        onDrawWithContent {
                                            drawContent()
                                            drawRect(
                                                brush = brush,
                                                size = Size(size.width, size.height),
                                                blendMode = BlendMode.DstIn
                                            )
                                        }
                                    }
                                    .heightIn(max = PREVIEW_MAX_HEIGHT.dp)
                                    .verticalScroll(verticalScrollState)
                            } else {
                                it // Full expanded - no height limit
                            }
                        }
                ) {
                    SelectionContainer {
                        HighlightText(
                            code = code,
                            language = language,
                            modifier = Modifier
                                .then(
                                    if (autoWrap) Modifier
                                    else Modifier.horizontalScroll(horizontalScrollState)
                                ),
                            fontSize = textStyle.fontSize,
                            lineHeight = textStyle.lineHeight,
                            colors = colorPalette,
                            overflow = TextOverflow.Visible,
                            softWrap = autoWrap,
                            fontFamily = codeFontFamily
                        )
                    }
                }
            }
        }
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
        "dockerfile" -> "Dockerfile"
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
        "c" -> "c"
        "html" -> "html"
        "css" -> "css"
        "xml" -> "xml"
        "json" -> "json"
        "yaml", "yml" -> "yml"
        "markdown", "md" -> "md"
        "sql" -> "sql"
        "sh", "bash" -> "sh"
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
    
    val highlighted by produceState<AnnotatedString?>(initialValue = null, code, language, darkMode) {
        try {
            val tokens = highlighter.highlight(code, language)
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
