package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.lightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MarkdownWebTest {
    @Test
    fun `preview html uses rtl dir for arabic markdown`() {
        val html = renderMarkdownPreviewHtml(
            htmlTemplate = "<html lang=\"{{LANGUAGE_TAG}}\" dir=\"{{BASE_DIR}}\"></html>",
            markdown = "### مرحبا",
            colorScheme = lightColorScheme(),
            appLocale = Locale("ar")
        )

        assertTrue(html.contains("lang=\"ar\""))
        assertTrue(html.contains("dir=\"rtl\""))
    }

    @Test
    fun `preview html falls back to app locale for neutral markdown`() {
        val html = renderMarkdownPreviewHtml(
            htmlTemplate = "<html lang=\"{{LANGUAGE_TAG}}\" dir=\"{{BASE_DIR}}\"></html>",
            markdown = "12345",
            colorScheme = lightColorScheme(),
            appLocale = Locale("ar")
        )

        assertTrue(html.contains("dir=\"rtl\""))
    }

    @Test
    fun `preview html keeps rtl dir for arabic tables`() {
        val html = renderMarkdownPreviewHtml(
            htmlTemplate = "<html lang=\"{{LANGUAGE_TAG}}\" dir=\"{{BASE_DIR}}\"></html>",
            markdown = """
                | الصنف | الكمية |
                | --- | --- |
                | طماطم | 3 |
            """.trimIndent(),
            colorScheme = lightColorScheme(),
            appLocale = Locale("ar")
        )

        assertTrue(html.contains("dir=\"rtl\""))
    }
}
