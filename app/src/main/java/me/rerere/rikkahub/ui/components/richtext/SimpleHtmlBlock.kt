package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import me.rerere.common.html.SimpleHtmlElement
import me.rerere.common.html.SimpleHtmlNode
import me.rerere.common.html.SimpleHtmlText
import me.rerere.rikkahub.ui.components.table.DataTable
import me.rerere.rikkahub.utils.BidiDirection
import me.rerere.rikkahub.utils.appLocale
import me.rerere.rikkahub.utils.resolveBidiDirection

private const val LTR_ISOLATE = '\u2066'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'

@Composable
private fun rememberElementDirection(text: String): BidiDirection {
    val appLocale = LocalContext.current.appLocale()
    return remember(text, appLocale) {
        resolveBidiDirection(text = text, fallbackLocale = appLocale)
    }
}

private fun BidiDirection.toComposeTextDirection(): TextDirection {
    return if (this == BidiDirection.Rtl) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr
}

private fun BidiDirection.toLayoutDirection(): LayoutDirection {
    return if (this == BidiDirection.Rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
}

@Composable
fun SimpleHtmlBlock(
    html: String,
    modifier: Modifier = Modifier
) {
    val htmlParser = LocalSimpleHtmlParser.current
    val document = remember(html, htmlParser) {
        htmlParser.parse(html)
    }

    val uriHandler = LocalUriHandler.current
    val blockDirection = rememberElementDirection(document.body.text)

    CompositionLocalProvider(LocalLayoutDirection provides blockDirection.toLayoutDirection()) {
        Column(modifier = modifier) {
            document.body.children.forEach { node ->
                RenderNode(
                    node = node,
                    onLinkClick = { url ->
                        try {
                            uriHandler.openUri(url)
                        } catch (e: Exception) {
                            // Handle link click error silently
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RenderNode(
    node: SimpleHtmlNode,
    onLinkClick: (String) -> Unit
) {
    when (node) {
        is SimpleHtmlText -> {
            if (node.text.isNotBlank()) {
                val direction = rememberElementDirection(node.text)
                CompositionLocalProvider(LocalLayoutDirection provides direction.toLayoutDirection()) {
                    Text(
                        text = node.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = LocalContentColor.current,
                            textDirection = direction.toComposeTextDirection()
                        )
                    )
                }
            }
        }

        is SimpleHtmlElement -> {
            when (node.tagName) {
                "p" -> {
                    val annotatedString = buildAnnotatedStringFromElement(node, onLinkClick)
                    if (annotatedString.text.isNotBlank()) {
                        val direction = rememberElementDirection(annotatedString.text)
                        // Parse inline styles for <p> element
                        val style = node.attr("style")
                        val inlineStyle = if (style.isNotEmpty()) parseInlineStyle(style) else null

                        CompositionLocalProvider(LocalLayoutDirection provides direction.toLayoutDirection()) {
                            Text(
                                text = annotatedString,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = inlineStyle?.color ?: LocalContentColor.current,
                                    fontWeight = inlineStyle?.fontWeight ?: FontWeight.Normal,
                                    textDirection = direction.toComposeTextDirection()
                                ),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                }

                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val headingLevel = node.tagName.substring(1).toIntOrNull() ?: 1
                    val textStyle = when (headingLevel) {
                        1 -> MaterialTheme.typography.headlineLarge
                        2 -> MaterialTheme.typography.headlineMedium
                        3 -> MaterialTheme.typography.headlineSmall
                        4 -> MaterialTheme.typography.titleLarge
                        5 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }

                    val annotatedString = buildAnnotatedStringFromElement(node, onLinkClick)
                    if (annotatedString.text.isNotBlank()) {
                        val direction = rememberElementDirection(annotatedString.text)
                        // Parse inline styles for heading elements
                        val style = node.attr("style")
                        val inlineStyle = if (style.isNotEmpty()) parseInlineStyle(style) else null

                        CompositionLocalProvider(LocalLayoutDirection provides direction.toLayoutDirection()) {
                            Text(
                                text = annotatedString,
                                style = textStyle.copy(
                                    color = inlineStyle?.color ?: LocalContentColor.current,
                                    fontWeight = inlineStyle?.fontWeight ?: textStyle.fontWeight,
                                    textDirection = direction.toComposeTextDirection()
                                ),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                "ul", "ol" -> {
                    RenderList(node, node.tagName == "ol", onLinkClick)
                }

                "details" -> {
                    RenderDetails(node, onLinkClick)
                }

                "img" -> {
                    RenderImage(node)
                }

                "progress" -> {
                    RenderProgress(node)
                }

                "table" -> {
                    RenderTable(node, onLinkClick)
                }

                "br" -> {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                "div" -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        node.children.forEach { childNode ->
                            RenderNode(childNode, onLinkClick)
                        }
                    }
                }

                else -> {
                    // Render other elements as text
                    val annotatedString = buildAnnotatedStringFromElement(node, onLinkClick)
                    if (annotatedString.text.isNotBlank()) {
                        val direction = rememberElementDirection(annotatedString.text)
                        // Parse inline styles for other elements
                        val style = node.attr("style")
                        val inlineStyle = if (style.isNotEmpty()) parseInlineStyle(style) else null

                        CompositionLocalProvider(LocalLayoutDirection provides direction.toLayoutDirection()) {
                            Text(
                                text = annotatedString,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = inlineStyle?.color ?: LocalContentColor.current,
                                    fontWeight = inlineStyle?.fontWeight ?: FontWeight.Normal,
                                    textDirection = direction.toComposeTextDirection()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderList(
    listElement: SimpleHtmlElement,
    isOrdered: Boolean,
    onLinkClick: (String) -> Unit
) {
    val listDirection = rememberElementDirection(listElement.text)
    val listItems = remember(listElement) {
        listElement.childElements().filter { it.tagName == "li" }
    }
    val maxMarkerLength = if (isOrdered) {
        listItems.size.toString().length + 1
    } else {
        1
    }
    val markerSlotWidth = 20.dp + ((maxMarkerLength - 1).coerceAtLeast(0) * 8).dp
    CompositionLocalProvider(LocalLayoutDirection provides listDirection.toLayoutDirection()) {
        Column(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
            listItems.forEachIndexed { index, item ->
                val itemDirection = rememberElementDirection(item.text)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val markerText = if (isOrdered) "${index + 1}." else "\u2022"
                    Box(
                        modifier = Modifier.widthIn(min = markerSlotWidth),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Text(
                            text = markerText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = LocalContentColor.current,
                                textDirection = TextDirection.ContentOrLtr
                            )
                        )
                    }
                    val annotatedString = buildAnnotatedStringFromElement(item, onLinkClick)
                    if (annotatedString.text.isNotBlank()) {
                        Text(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = LocalContentColor.current,
                                textDirection = itemDirection.toComposeTextDirection()
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderDetails(
    detailsElement: SimpleHtmlElement,
    onLinkClick: (String) -> Unit
) {
    val isOpenByDefault = detailsElement.hasAttr("open")
    var isExpanded by remember { mutableStateOf(isOpenByDefault) }

    val summaryElement = detailsElement.childElements().find {
        it.tagName == "summary"
    }
    val summaryText = summaryElement?.text ?: "Details"
    val summaryDirection = rememberElementDirection(summaryText)

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        // Summary (clickable header)
        CompositionLocalProvider(LocalLayoutDirection provides summaryDirection.toLayoutDirection()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val indicator: @Composable () -> Unit = {
                    Text(
                        text = if (isExpanded) "▼ " else "▶ ",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = LocalContentColor.current
                        )
                    )
                }
                val summaryAnnotatedString = if (summaryElement != null) {
                    buildAnnotatedStringFromElement(summaryElement, onLinkClick)
                } else {
                    AnnotatedString(summaryText)
                }
                val summaryContent: @Composable () -> Unit = {
                    Text(
                        text = summaryAnnotatedString,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = LocalContentColor.current,
                            fontWeight = FontWeight.Medium,
                            textDirection = summaryDirection.toComposeTextDirection()
                        )
                    )
                }
                if (summaryDirection == BidiDirection.Rtl) {
                    summaryContent()
                    indicator()
                } else {
                    indicator()
                    summaryContent()
                }
            }
        }

        // Details content (animated visibility)
        AnimatedVisibility(visible = isExpanded) {
            CompositionLocalProvider(LocalLayoutDirection provides summaryDirection.toLayoutDirection()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp)
                ) {
                    detailsElement.childElements().forEach { child ->
                        if (child.tagName != "summary") {
                            RenderNode(child, onLinkClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderImage(
    imgElement: SimpleHtmlElement
) {
    val src = imgElement.attr("src")
    val alt = imgElement.attr("alt")
    if (src.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            ZoomableAsyncImage(
                model = src,
                contentDescription = alt.takeIf { it.isNotEmpty() },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private fun buildAnnotatedStringFromElement(
    element: SimpleHtmlElement,
    onLinkClick: (String) -> Unit
): AnnotatedString {
    return buildAnnotatedString {
        processElementNodes(element, this, onLinkClick)
    }
}

private fun processElementNodes(
    element: SimpleHtmlElement,
    builder: AnnotatedString.Builder,
    onLinkClick: (String) -> Unit
) {
    element.children.forEach { node ->
        when (node) {
            is SimpleHtmlText -> {
                builder.append(node.text)
            }

            is SimpleHtmlElement -> {
                when (node.tagName) {
                    "b", "strong" -> {
                        val start = builder.length
                        processElementNodes(node, builder, onLinkClick)
                        builder.addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold),
                            start,
                            builder.length
                        )
                    }

                    "i", "em" -> {
                        val start = builder.length
                        processElementNodes(node, builder, onLinkClick)
                        builder.addStyle(
                            SpanStyle(fontStyle = FontStyle.Italic),
                            start,
                            builder.length
                        )
                    }

                    "u" -> {
                        val start = builder.length
                        processElementNodes(node, builder, onLinkClick)
                        builder.addStyle(
                            SpanStyle(textDecoration = TextDecoration.Underline),
                            start,
                            builder.length
                        )
                    }

                    "a" -> {
                        val href = node.attr("href")
                        val start = builder.length
                        processElementNodes(node, builder, onLinkClick)
                        if (href.isNotEmpty()) {
                            builder.addStyle(
                                SpanStyle(
                                    color = Color.Blue,
                                    textDecoration = TextDecoration.Underline
                                ),
                                start,
                                builder.length
                            )
                            builder.addStringAnnotation(
                                tag = "URL",
                                annotation = href,
                                start = start,
                                end = builder.length
                            )
                        }
                    }

                    "code" -> {
                        builder.append(LTR_ISOLATE)
                        val start = builder.length
                        processElementNodes(node, builder, onLinkClick)
                        builder.addStyle(
                            SpanStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                background = Color.Gray.copy(alpha = 0.2f)
                            ),
                            start,
                            builder.length
                        )
                        builder.append(POP_DIRECTIONAL_ISOLATE)
                    }

                    "br" -> {
                        builder.append("\n")
                    }

                    "span" -> {
                        val start = builder.length
                        processElementNodes(node, builder, onLinkClick)

                        // Handle inline styles
                        val style = node.attr("style")
                        if (style.isNotEmpty()) {
                            val spanStyle = parseInlineStyle(style)
                            if (spanStyle != null) {
                                builder.addStyle(
                                    spanStyle,
                                    start,
                                    builder.length
                                )
                            }
                        }
                    }

                    "font" -> {
                        val start = builder.length
                        processElementNodes(node, builder, onLinkClick)

                        // Handle font color attribute
                        val color = node.attr("color")
                        if (color.isNotEmpty()) {
                            val parsedColor = parseColor(color)
                            if (parsedColor != null) {
                                builder.addStyle(
                                    SpanStyle(color = parsedColor),
                                    start,
                                    builder.length
                                )
                            }
                        }
                    }

                    else -> {
                        processElementNodes(node, builder, onLinkClick)
                    }
                }
            }
        }
    }
}

private fun parseInlineStyle(style: String): SpanStyle? {
    val properties = style.split(";")
        .mapNotNull { property ->
            val parts = property.split(":")
            if (parts.size == 2) {
                parts[0].trim() to parts[1].trim()
            } else null
        }
        .toMap()

    var color: Color? = null
    var fontWeight: FontWeight? = null

    properties["color"]?.let { colorValue ->
        color = parseColor(colorValue)
    }

    properties["font-weight"]?.let { weightValue ->
        fontWeight = parseFontWeight(weightValue)
    }

    return if (color != null || fontWeight != null) {
        SpanStyle(
            color = color ?: Color.Unspecified,
            fontWeight = fontWeight
        )
    } else null
}

private fun parseColor(colorString: String): Color? {
    return try {
        when {
            colorString.startsWith("#") -> {
                // Hex color
                val hex = colorString.removePrefix("#")
                when (hex.length) {
                    6 -> hex.toLongOrNull(16)?.let { rgb -> Color(0xFF000000L or rgb) }
                    3 -> {
                        // Convert 3-digit hex to 6-digit
                        val r = hex[0].toString().repeat(2)
                        val g = hex[1].toString().repeat(2)
                        val b = hex[2].toString().repeat(2)
                        parseColor("#$r$g$b")
                    }

                    else -> null
                }
            }

            colorString.startsWith("rgb(") -> {
                // RGB color
                val rgb = colorString.removePrefix("rgb(").removeSuffix(")")
                val values = rgb.split(",").map { it.trim().toIntOrNull() }
                if (values.size == 3 && values.all { it != null && it in 0..255 }) {
                    Color(values[0]!!, values[1]!!, values[2]!!)
                } else null
            }

            colorString.startsWith("rgba(") -> {
                // RGBA color
                val rgba = colorString.removePrefix("rgba(").removeSuffix(")")
                val values = rgba.split(",").map { it.trim() }
                if (values.size == 4) {
                    val r = values[0].toIntOrNull()
                    val g = values[1].toIntOrNull()
                    val b = values[2].toIntOrNull()
                    val a = values[3].toFloatOrNull()
                    if (r != null && g != null && b != null && a != null &&
                        r in 0..255 && g in 0..255 && b in 0..255 && a in 0f..1f
                    ) {
                        Color(r, g, b, (a * 255).toInt())
                    } else null
                } else null
            }

            else -> {
                // Named colors
                when (colorString.lowercase()) {
                    "red" -> Color.Red
                    "green" -> Color.Green
                    "blue" -> Color.Blue
                    "black" -> Color.Black
                    "white" -> Color.White
                    "gray", "grey" -> Color.Gray
                    "yellow" -> Color.Yellow
                    "cyan" -> Color.Cyan
                    "magenta" -> Color.Magenta
                    "orange" -> Color(0xFFFFA500)
                    "purple" -> Color(0xFF800080)
                    "brown" -> Color(0xFFA52A2A)
                    "pink" -> Color(0xFFFFC0CB)
                    else -> null
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}

private fun parseFontWeight(weightString: String): FontWeight? {
    return when (weightString.lowercase()) {
        "normal" -> FontWeight.Normal
        "bold" -> FontWeight.SemiBold
        "bolder" -> FontWeight.ExtraBold
        "lighter" -> FontWeight.Light
        "100" -> FontWeight.W100
        "200" -> FontWeight.W200
        "300" -> FontWeight.W300
        "400" -> FontWeight.W400
        "500" -> FontWeight.W500
        "600" -> FontWeight.W600
        "700" -> FontWeight.W700
        "800" -> FontWeight.W800
        "900" -> FontWeight.W900
        else -> null
    }
}

@Composable
private fun RenderProgress(
    progressElement: SimpleHtmlElement
) {
    val value = progressElement.attr("value").toFloatOrNull() ?: 0f
    val max = progressElement.attr("max").toFloatOrNull() ?: 100f
    val progress = if (max > 0) (value / max).coerceIn(0f, 1f) else 0f

    // Check for width in style attribute first, then width attribute
    val style = progressElement.attr("style")
    var width = ""
    if (style.isNotEmpty()) {
        val properties = style.split(";")
            .mapNotNull { property ->
                val parts = property.split(":")
                if (parts.size == 2) {
                    parts[0].trim() to parts[1].trim()
                } else null
            }
            .toMap()
        width = properties["width"] ?: ""
    }
    if (width.isEmpty()) {
        width = progressElement.attr("width")
    }

    val widthModifier = if (width.isNotEmpty()) {
        when {
            width.endsWith("%") -> {
                val percentage = width.removeSuffix("%").toFloatOrNull()
                if (percentage != null && percentage > 0) {
                    Modifier.fillMaxWidth(percentage / 100f)
                } else {
                    Modifier.fillMaxWidth()
                }
            }

            width.endsWith("px") -> {
                val pixels = width.removeSuffix("px").toIntOrNull()
                if (pixels != null && pixels > 0) {
                    Modifier.width(pixels.dp)
                } else {
                    Modifier.fillMaxWidth()
                }
            }

            else -> {
                val pixels = width.toIntOrNull()
                if (pixels != null && pixels > 0) {
                    Modifier.width(pixels.dp)
                } else {
                    Modifier.fillMaxWidth()
                }
            }
        }
    } else {
        Modifier.fillMaxWidth()
    }

    LinearProgressIndicator(
        progress = { progress },
        modifier = widthModifier,
    )
}

@Composable
private fun RenderTable(
    tableElement: SimpleHtmlElement,
    onLinkClick: (String) -> Unit
) {
    val rows = mutableListOf<List<@Composable () -> Unit>>()
    var headers = emptyList<@Composable () -> Unit>()
    val tableDirection = rememberElementDirection(tableElement.text)

    // Extract table headers and rows
    tableElement.descendantElementsByTags("tr").forEach { tr ->
        val cells = mutableListOf<@Composable () -> Unit>()

        tr.descendantElementsByTags("th", "td").forEach { cell ->
            cells.add {
                val annotatedString = buildAnnotatedStringFromElement(cell, onLinkClick)
                if (annotatedString.text.isNotBlank()) {
                    val direction = rememberElementDirection(annotatedString.text)
                    CompositionLocalProvider(LocalLayoutDirection provides direction.toLayoutDirection()) {
                        Text(
                            text = annotatedString,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = LocalContentColor.current,
                                textDirection = direction.toComposeTextDirection()
                            )
                        )
                    }
                }
            }
        }

        if (cells.isNotEmpty()) {
            // Check if this row contains header cells (th)
            val isHeaderRow = tr.descendantElementsByTags("th").isNotEmpty()
            if (isHeaderRow && headers.isEmpty()) {
                headers = cells
            } else {
                rows.add(cells)
            }
        }
    }

    // If no headers found, create empty headers for consistency
    if (headers.isEmpty() && rows.isNotEmpty()) {
        headers = rows.firstOrNull()?.mapIndexed { _, _ ->
            @Composable { Text("") }
        } ?: emptyList()
    }

    if (headers.isNotEmpty() || rows.isNotEmpty()) {
        Box(modifier = Modifier.padding(vertical = 8.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides tableDirection.toLayoutDirection()) {
                DataTable(
                    headers = headers,
                    rows = rows,
                    cellBorder = null,
                    headerBackground = Color.Transparent,
                    zebraStriping = false
                )
            }
        }
    }
}
