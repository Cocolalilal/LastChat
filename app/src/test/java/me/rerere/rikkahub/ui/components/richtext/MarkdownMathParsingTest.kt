package me.rerere.rikkahub.ui.components.richtext

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownMathParsingTest {
    private fun parse(markdown: String): Pair<String, ASTNode> {
        val content = preProcess(markdown)
        return content to MarkdownParser(ChatMarkdownFlavour()).buildMarkdownTreeFromString(content)
    }

    private fun ASTNode.nodes(type: IElementType): List<ASTNode> =
        (if (this.type == type) listOf(this) else emptyList()) + children.flatMap { it.nodes(type) }

    private fun Pair<String, ASTNode>.math(): List<String> =
        second.nodes(GFMElementTypes.INLINE_MATH).map { first.substring(it.startOffset, it.endOffset) }

    @Test fun pricesRemainProseAndBoldFormattingSurvivesRendering() {
        val input = "Input **$1.50 / 1M** and output **$7.50 / 1M**. About $0.005 to $0.01 per request."
        val (content, tree) = parse(input)
        assertTrue(tree.nodes(GFMElementTypes.INLINE_MATH).isEmpty())
        assertEquals(2, tree.nodes(MarkdownElementTypes.STRONG).size)
        val rendered = HtmlGenerator(content, tree, ChatMarkdownFlavour()).generateHtml()
        assertTrue(rendered, rendered.contains("<strong>$1.50 / 1M</strong>"))
        assertTrue(rendered, rendered.contains("<strong>$7.50 / 1M</strong>"))
        assertTrue(rendered, rendered.contains("$0.005 to $0.01 per request."))
    }

    @Test fun currencyDoesNotConsumeNearbyEquations() {
        assertEquals(listOf("\$x + y\$", "\$2x + 1\$"),
            parse("Costs $5, while \$x + y\$ and \$2x + 1\$ are equations; budget $10.").math())
        assertEquals(listOf("\$x\$"), parse("$0.005 to $0.01, then \$x\$.").math())
    }

    @Test fun validInlineAndDisplayMathStillParse() {
        val parsed = parse("Inline \$x^2\$, \$2 + 2\$, and \\(\\frac{a}{b}\\).\n\n$$\nE = mc^2\n$$")
        assertEquals(listOf("\$x^2\$", "\$2 + 2\$", "\$\\frac{a}{b}\$"), parsed.math())
        assertEquals(1, parsed.second.nodes(GFMElementTypes.BLOCK_MATH).size)
    }

    @Test fun escapedDollarsAndCodeStayLiteral() {
        val parsed = parse("Escaped \\$5 and \\$10; `\$x\$ and $5`;\n\n```text\n\$y\$ and $10\n```\n\nLive \$z\$.")
        assertEquals(listOf("\$z\$"), parsed.math())
        assertEquals(1, parsed.second.nodes(MarkdownElementTypes.CODE_SPAN).size)
        assertEquals(1, parsed.second.nodes(MarkdownElementTypes.CODE_FENCE).size)
        assertTrue(parsed.first.contains("`\$x\$ and $5`"))
        assertTrue(parsed.first.contains("\\$5 and \\$10"))
    }

    @Test fun unmatchedAndWhitespaceDelimitedDollarsStayLiteral() {
        assertTrue(parse("A single $1.50 price.").math().isEmpty())
        assertTrue(parse("$ 5 and $ 10").math().isEmpty())
        assertTrue(parse("\$x \$ and \$ y\$").math().isEmpty())
    }
}
