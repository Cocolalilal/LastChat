package me.rerere.common.html

data class SimpleHtmlDocument(
    val body: SimpleHtmlElement
)

fun interface SimpleHtmlParser {
    fun parse(html: String): SimpleHtmlDocument
}

sealed interface SimpleHtmlNode {
    val text: String
}

data class SimpleHtmlText(
    override val text: String
) : SimpleHtmlNode

data class SimpleHtmlElement(
    val tagName: String,
    val attributes: Map<String, String>,
    val children: List<SimpleHtmlNode>,
    override val text: String
) : SimpleHtmlNode {
    fun attr(name: String): String = attributes[name].orEmpty()

    fun hasAttr(name: String): Boolean = attributes.containsKey(name)

    fun childElements(): List<SimpleHtmlElement> = children.filterIsInstance<SimpleHtmlElement>()

    fun descendantElementsByTags(vararg tags: String): List<SimpleHtmlElement> {
        val tagSet = tags.map { it.lowercase() }.toSet()
        return buildList {
            fun visit(element: SimpleHtmlElement) {
                element.childElements().forEach { child ->
                    if (child.tagName in tagSet) add(child)
                    visit(child)
                }
            }
            visit(this@SimpleHtmlElement)
        }
    }
}
