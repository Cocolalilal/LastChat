package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import me.rerere.rikkahub.ui.theme.AtomOneDarkHighlightPalette
import me.rerere.rikkahub.ui.theme.AtomOneLightHighlightPalette
import me.rerere.rikkahub.ui.theme.HighlightColorPalette

private const val CODE_BLOCK_MAX_COLLAPSED_HEIGHT = 160

@Composable
fun LastChatCodeBlock(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    initiallyExpanded: Boolean = false,
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var expanded by remember(initiallyExpanded) { mutableStateOf(initiallyExpanded) }

    val lineCount = remember(code) { code.lines().size }
    val isLongCode = lineCount > 10
    val palette = if (darkTheme) AtomOneDarkHighlightPalette else AtomOneLightHighlightPalette

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val highlightedText = remember(code, language, darkTheme) {
        tokenizeCode(code, language, palette)
    }

    val headerBackground = if (darkTheme) Color(0xFF21252B) else Color(0xFFE5E5E6)
    val bodyBackground = if (darkTheme) Color(0xFF282C34) else Color(0xFFFAFAFA)
    val borderColor = if (darkTheme) Color(0xFF3B4048) else Color(0xFFD0D0D1)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bodyBackground,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Language + Copy + Expand
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBackground)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = language.ifBlank { "code" }.lowercase(),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Copy button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(code))
                                copied = true
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AnimatedContent(
                            targetState = copied,
                            label = "copy-icon",
                        ) { isCopied ->
                            if (isCopied) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Copied",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Text(
                            text = if (copied) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Expand / Collapse if long
                    if (isLongCode) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { expanded = !expanded }
                                .padding(4.dp),
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Code Content with Line Numbers and Horizontal Scroll
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isLongCode && !expanded) Modifier.heightIn(max = CODE_BLOCK_MAX_COLLAPSED_HEIGHT.dp)
                        else Modifier
                    )
                    .padding(vertical = 10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 12.dp),
                ) {
                    // Line numbers
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = "$i",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            )
                        }
                    }

                    // Code text
                    Text(
                        text = highlightedText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                        color = palette.fallback,
                    )
                }
            }

            if (isLongCode && !expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBackground.copy(alpha = 0.6f))
                        .clickable { expanded = true }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Show all $lineCount lines",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Fast, lightweight Kotlin Multiplatform code tokenizer for syntax highlighting.
 */
private fun tokenizeCode(
    code: String,
    language: String,
    palette: HighlightColorPalette,
): AnnotatedString = buildAnnotatedString {
    val lang = language.lowercase().trim()
    val keywords = when (lang) {
        "kotlin", "kt" -> setOf(
            "package", "import", "class", "interface", "object", "val", "var", "fun", "return",
            "if", "else", "when", "for", "while", "do", "try", "catch", "finally", "throw",
            "null", "true", "false", "this", "super", "is", "as", "in", "out", "suspend",
            "override", "private", "protected", "public", "internal", "data", "sealed", "companion"
        )
        "java" -> setOf(
            "package", "import", "class", "interface", "enum", "extends", "implements", "public",
            "private", "protected", "static", "final", "void", "return", "if", "else", "for",
            "while", "do", "switch", "case", "break", "continue", "new", "try", "catch", "finally",
            "throw", "throws", "null", "true", "false", "this", "super", "instanceof"
        )
        "python", "py" -> setOf(
            "def", "class", "import", "from", "as", "return", "if", "elif", "else", "for", "while",
            "break", "continue", "try", "except", "finally", "raise", "yield", "with", "pass",
            "lambda", "None", "True", "False", "is", "in", "not", "and", "or", "async", "await"
        )
        "javascript", "js", "typescript", "ts", "jsx", "tsx" -> setOf(
            "function", "const", "let", "var", "return", "if", "else", "for", "while", "do",
            "switch", "case", "break", "continue", "new", "try", "catch", "finally", "throw",
            "null", "true", "false", "undefined", "this", "class", "extends", "import", "export",
            "default", "from", "as", "async", "await", "typeof", "instanceof", "interface", "type"
        )
        "json" -> setOf("true", "false", "null")
        "sql" -> setOf(
            "select", "from", "where", "join", "inner", "left", "right", "full", "on", "group", "by",
            "order", "having", "limit", "offset", "insert", "into", "values", "update", "set",
            "delete", "create", "table", "alter", "drop", "index", "as", "distinct", "and", "or",
            "not", "in", "is", "null", "like", "union", "all", "case", "when", "then", "end"
        )
        "bash", "sh", "shell", "zsh" -> setOf(
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until", "do",
            "done", "in", "function", "select", "time", "return", "exit", "echo", "export", "source"
        )
        else -> emptySet()
    }

    val lines = code.lines()
    lines.forEachIndexed { lineIdx, line ->
        var i = 0
        val len = line.length

        while (i < len) {
            val ch = line[i]

            // Line Comments (// or # or --)
            if ((ch == '/' && i + 1 < len && line[i + 1] == '/') ||
                (ch == '#' && (lang in setOf("python", "py", "bash", "sh", "shell", "yaml", "yml"))) ||
                (ch == '-' && i + 1 < len && line[i + 1] == '-' && lang == "sql")
            ) {
                withStyle(SpanStyle(color = palette.comment)) {
                    append(line.substring(i))
                }
                break
            }

            // Strings ("..." or '...')
            if (ch == '"' || ch == '\'' || ch == '`') {
                val quote = ch
                val start = i
                i++
                while (i < len) {
                    if (line[i] == '\\' && i + 1 < len) {
                        i += 2
                    } else if (line[i] == quote) {
                        i++
                        break
                    } else {
                        i++
                    }
                }
                withStyle(SpanStyle(color = palette.string)) {
                    append(line.substring(start, i))
                }
                continue
            }

            // Numbers
            if (ch.isDigit()) {
                val start = i
                while (i < len && (line[i].isDigit() || line[i] == '.' || line[i] == 'x' || line[i] in 'a'..'f' || line[i] in 'A'..'F')) {
                    i++
                }
                withStyle(SpanStyle(color = palette.number)) {
                    append(line.substring(start, i))
                }
                continue
            }

            // Identifiers / Keywords
            if (ch.isLetter() || ch == '_') {
                val start = i
                while (i < len && (line[i].isLetterOrDigit() || line[i] == '_')) {
                    i++
                }
                val word = line.substring(start, i)
                val isKeyword = if (lang == "sql") keywords.contains(word.lowercase()) else keywords.contains(word)

                if (isKeyword) {
                    withStyle(SpanStyle(color = palette.keyword, fontWeight = FontWeight.Bold)) {
                        append(word)
                    }
                } else if (i < len && line[i] == '(') {
                    // Function call
                    withStyle(SpanStyle(color = palette.function)) {
                        append(word)
                    }
                } else if (word.first().isUpperCase()) {
                    // Type / Class
                    withStyle(SpanStyle(color = palette.className)) {
                        append(word)
                    }
                } else {
                    withStyle(SpanStyle(color = palette.fallback)) {
                        append(word)
                    }
                }
                continue
            }

            // Operators & Punctuation
            if (ch in "=+-*/%&|^!<>?:;,.~") {
                withStyle(SpanStyle(color = palette.operator)) {
                    append(ch)
                }
                i++
                continue
            }

            // Default character
            withStyle(SpanStyle(color = palette.fallback)) {
                append(ch)
            }
            i++
        }

        if (lineIdx < lines.lastIndex) {
            append("\n")
        }
    }
}
