package me.rerere.rikkahub.ui.components.richtext

import android.content.Context
import androidx.compose.material3.ColorScheme
import me.rerere.rikkahub.utils.appLocale
import me.rerere.rikkahub.utils.resolveBidiDirection
import me.rerere.rikkahub.utils.toHtmlDir
import me.rerere.rikkahub.utils.base64Encode
import me.rerere.rikkahub.utils.toCssHex
import java.util.Locale

/**
 * Build HTML page for markdown preview with support for:
 * - Markdown rendering via marked.js
 * - LaTeX math via KaTeX
 * - Mermaid diagrams
 * - Syntax highlighting via highlight.js
 */
fun buildMarkdownPreviewHtml(context: Context, markdown: String, colorScheme: ColorScheme): String {
    val htmlTemplate = context.assets.open("html/mark.html").bufferedReader().use { it.readText() }
    return renderMarkdownPreviewHtml(
        htmlTemplate = htmlTemplate,
        markdown = markdown,
        colorScheme = colorScheme,
        appLocale = context.appLocale(),
    )
}

internal fun renderMarkdownPreviewHtml(
    htmlTemplate: String,
    markdown: String,
    colorScheme: ColorScheme,
    appLocale: Locale,
): String {
    val baseDir = resolveBidiDirection(markdown, appLocale).toHtmlDir()
    return htmlTemplate
        .replace("{{MARKDOWN_BASE64}}", markdown.base64Encode())
        .replace("{{LANGUAGE_TAG}}", appLocale.toLanguageTag())
        .replace("{{BASE_DIR}}", baseDir)
        .replace("{{BACKGROUND_COLOR}}", colorScheme.background.toCssHex())
        .replace("{{ON_BACKGROUND_COLOR}}", colorScheme.onBackground.toCssHex())
        .replace("{{SURFACE_COLOR}}", colorScheme.surface.toCssHex())
        .replace("{{ON_SURFACE_COLOR}}", colorScheme.onSurface.toCssHex())
        .replace("{{SURFACE_VARIANT_COLOR}}", colorScheme.surfaceVariant.toCssHex())
        .replace("{{ON_SURFACE_VARIANT_COLOR}}", colorScheme.onSurfaceVariant.toCssHex())
        .replace("{{PRIMARY_COLOR}}", colorScheme.primary.toCssHex())
        .replace("{{OUTLINE_COLOR}}", colorScheme.outline.toCssHex())
        .replace("{{OUTLINE_VARIANT_COLOR}}", colorScheme.outlineVariant.toCssHex())
}
