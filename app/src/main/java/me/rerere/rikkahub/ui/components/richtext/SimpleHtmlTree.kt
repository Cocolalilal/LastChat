package me.rerere.rikkahub.ui.components.richtext

import me.rerere.common.html.SimpleHtmlDocument
import me.rerere.common.html.SimpleHtmlElement
import me.rerere.common.html.SimpleHtmlNode
import me.rerere.common.html.SimpleHtmlParser
import me.rerere.common.html.SimpleHtmlText
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal object JsoupSimpleHtmlParser : SimpleHtmlParser {
    override fun parse(html: String): SimpleHtmlDocument {
        val document = runCatching { Jsoup.parse(html) }.getOrElse {
            Jsoup.parse("<p>Error parsing HTML: ${it.message}</p>")
        }
        return SimpleHtmlDocument(body = document.body().toSimpleHtmlElement())
    }
}

private fun Node.toSimpleHtmlNode(): SimpleHtmlNode? {
    return when (this) {
        is TextNode -> SimpleHtmlText(text())
        is Element -> toSimpleHtmlElement()
        else -> null
    }
}

private fun Element.toSimpleHtmlElement(): SimpleHtmlElement {
    return SimpleHtmlElement(
        tagName = tagName().lowercase(),
        attributes = attributes().associate { attribute -> attribute.key to attribute.value },
        children = childNodes().mapNotNull { it.toSimpleHtmlNode() },
        text = text()
    )
}
