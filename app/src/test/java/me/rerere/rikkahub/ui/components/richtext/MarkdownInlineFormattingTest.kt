package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import me.rerere.rikkahub.data.datastore.RpStyleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownInlineFormattingTest {

    @Test
    fun `preProcess converts highlight syntax to mark tags`() {
        val input = "This is ==highlighted text== in markdown."
        val expected = "This is <mark>highlighted text</mark> in markdown."
        assertEquals(expected, preProcess(input))
    }

    @Test
    fun `preProcess converts underline syntax to u tags`() {
        val input = "This is ++underlined text++ in markdown."
        val expected = "This is <u>underlined text</u> in markdown."
        assertEquals(expected, preProcess(input))
    }

    @Test
    fun `preProcess handles multiple and mixed highlights and underlines`() {
        val input = "==first== and ++second++ and ==third=="
        val expected = "<mark>first</mark> and <u>second</u> and <mark>third</mark>"
        assertEquals(expected, preProcess(input))
    }

    @Test
    fun `preProcess ignores inline code with double equals or pluses`() {
        val input = "Code `==not highlighted==` and `++not underlined++`"
        assertEquals(input, preProcess(input))
    }

    @Test
    fun `preProcess ignores fenced code blocks with double equals or pluses`() {
        val input = """
            ```python
            if x == y:
                count += 1
            ```
        """.trimIndent()
        assertEquals(input, preProcess(input))
    }

    @Test
    fun `preProcess ignores math blocks with double equals`() {
        val input = "$$ x == y $$ and $ a == b $"
        assertEquals(input, preProcess(input))
    }

    @Test
    fun `preProcess ignores comparison operators in normal text`() {
        val input = "if (a == b) return true;"
        assertEquals(input, preProcess(input))
    }

    @Test
    fun `parseColorSafe parses 6-digit hex, 8-digit hex, and named colors`() {
        assertEquals(Color(0xFFFF0000), parseColorSafe("#FF0000"))
        assertEquals(Color(0xFF00FF00), parseColorSafe("#00FF00"))
        assertEquals(Color(0x80123456), parseColorSafe("#80123456"))
        assertEquals(Color.Blue, parseColorSafe("blue"))
        assertNull(parseColorSafe("invalid-color-value"))
    }

    @Test
    fun `resolveInlineHtmlStyle resolves mark tag with default theme colors`() {
        val colorScheme = lightColorScheme()
        val style = resolveInlineHtmlStyle(
            tagName = "mark",
            attrsString = "",
            colorScheme = colorScheme,
            rpStyleRules = emptyList()
        )
        assertNotNull(style)
        assertEquals(colorScheme.tertiaryContainer.copy(alpha = 0.55f), style?.background)
        assertEquals(colorScheme.onTertiaryContainer, style?.color)
        assertEquals(FontWeight.Medium, style?.fontWeight)
    }

    @Test
    fun `resolveInlineHtmlStyle resolves mark tag with custom rpStyleRule`() {
        val colorScheme = lightColorScheme()
        val rules = listOf(
            RpStyleRule(
                pattern = "==",
                colorHex = "#FF5500",
                enabled = true
            )
        )
        val style = resolveInlineHtmlStyle(
            tagName = "mark",
            attrsString = "",
            colorScheme = colorScheme,
            rpStyleRules = rules
        )
        assertNotNull(style)
        val expectedColor = Color(0xFFFF5500)
        assertEquals(expectedColor, style?.color)
        assertEquals(expectedColor.copy(alpha = 0.25f), style?.background)
    }

    @Test
    fun `resolveInlineHtmlStyle resolves mark tag with inline CSS styles`() {
        val colorScheme = lightColorScheme()
        val style = resolveInlineHtmlStyle(
            tagName = "mark",
            attrsString = "style=\"background-color: #00FF00; color: #0000FF;\"",
            colorScheme = colorScheme,
            rpStyleRules = emptyList()
        )
        assertNotNull(style)
        assertEquals(Color(0xFF00FF00), style?.background)
        assertEquals(Color(0xFF0000FF), style?.color)
    }

    @Test
    fun `resolveInlineHtmlStyle resolves u and ins tags with underline decoration`() {
        val colorScheme = lightColorScheme()
        val uStyle = resolveInlineHtmlStyle(
            tagName = "u",
            attrsString = "",
            colorScheme = colorScheme,
            rpStyleRules = emptyList()
        )
        assertNotNull(uStyle)
        assertEquals(TextDecoration.Underline, uStyle?.textDecoration)

        val insStyle = resolveInlineHtmlStyle(
            tagName = "ins",
            attrsString = "",
            colorScheme = colorScheme,
            rpStyleRules = emptyList()
        )
        assertNotNull(insStyle)
        assertEquals(TextDecoration.Underline, insStyle?.textDecoration)
    }

    @Test
    fun `resolveInlineHtmlStyle resolves u tag with custom rpStyleRule color`() {
        val colorScheme = lightColorScheme()
        val rules = listOf(
            RpStyleRule(
                pattern = "++",
                colorHex = "#9C27B0",
                enabled = true
            )
        )
        val style = resolveInlineHtmlStyle(
            tagName = "u",
            attrsString = "",
            colorScheme = colorScheme,
            rpStyleRules = rules
        )
        assertNotNull(style)
        assertEquals(TextDecoration.Underline, style?.textDecoration)
        assertEquals(Color(0xFF9C27B0), style?.color)
    }

    @Test
    fun `resolveInlineHtmlStyle resolves span tag with inline css styles`() {
        val colorScheme = lightColorScheme()
        val style = resolveInlineHtmlStyle(
            tagName = "span",
            attrsString = "style=\"color: #E91E63; font-weight: bold; text-decoration: underline\"",
            colorScheme = colorScheme,
            rpStyleRules = emptyList()
        )
        assertNotNull(style)
        assertEquals(Color(0xFFE91E63), style?.color)
        assertEquals(FontWeight.Bold, style?.fontWeight)
        assertEquals(TextDecoration.Underline, style?.textDecoration)
    }
}
