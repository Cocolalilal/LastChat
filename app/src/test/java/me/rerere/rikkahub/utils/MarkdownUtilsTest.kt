package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownUtilsTest {

    @Test
    fun `stripMarkdown removes highlight syntax`() {
        val input = "Here is ==highlighted text== in a sentence."
        assertEquals("Here is highlighted text in a sentence.", input.stripMarkdown())
    }

    @Test
    fun `stripMarkdown removes mark tags`() {
        val input = "Here is <mark>highlighted text</mark> in a sentence."
        assertEquals("Here is highlighted text in a sentence.", input.stripMarkdown())
    }

    @Test
    fun `stripMarkdown removes plus underline syntax`() {
        val input = "Here is ++underlined text++ in a sentence."
        assertEquals("Here is underlined text in a sentence.", input.stripMarkdown())
    }

    @Test
    fun `stripMarkdown removes u and ins tags`() {
        val input = "Here is <u>underlined</u> and <ins>inserted</ins> text."
        assertEquals("Here is underlined and inserted text.", input.stripMarkdown())
    }

    @Test
    fun `stripMarkdown removes inline html formatting tags with attributes`() {
        val input = "Here is <span style=\"color: red;\">colored text</span> and <font color=\"blue\">blue text</font>."
        assertEquals("Here is colored text and blue text.", input.stripMarkdown())
    }

    @Test
    fun `stripMarkdown removes bold italic strikethrough and code`() {
        val input = "**bold** *italic* ~~strike~~ `code` [link text](https://example.com)"
        assertEquals("bold italic strike  link text", input.stripMarkdown())
    }

    @Test
    fun `stripMarkdown removes headings blockquotes and lists`() {
        val input = """
            # Heading 1
            > Quote line
            - Item 1
            1. Numbered item
            Paragraph content.
        """.trimIndent()
        val expected = """
            Heading 1
            Quote line
            Item 1
            Numbered item
            Paragraph content.
        """.trimIndent()
        assertEquals(expected, input.stripMarkdown())
    }
}
