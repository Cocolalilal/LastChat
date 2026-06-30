package me.rerere.common.text

fun String.escapeHtml(): String {
    val output = StringBuilder(length)
    for (char in this) {
        when (char) {
            '&' -> output.append("&amp;")
            '<' -> output.append("&lt;")
            '>' -> output.append("&gt;")
            '"' -> output.append("&quot;")
            else -> output.append(char)
        }
    }
    return output.toString()
}

fun String.unescapeHtml(): String {
    val output = StringBuilder(length)
    var index = 0
    while (index < length) {
        if (this[index] != '&') {
            output.append(this[index])
            index++
            continue
        }

        val semicolon = indexOf(';', startIndex = index + 1)
        if (semicolon == -1) {
            output.append(this[index])
            index++
            continue
        }

        val entity = substring(index + 1, semicolon)
        val decoded = entity.decodeHtmlEntity()
        if (decoded == null) {
            output.append('&')
            index++
        } else {
            output.append(decoded)
            index = semicolon + 1
        }
    }
    return output.toString()
}

private fun String.decodeHtmlEntity(): String? {
    return when (this) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        "nbsp" -> "\u00A0"
        else -> decodeNumericHtmlEntity()
    }
}

private fun String.decodeNumericHtmlEntity(): String? {
    if (!startsWith("#")) return null
    val codePoint = if (startsWith("#x", ignoreCase = true)) {
        substring(2).toIntOrNull(radix = 16)
    } else {
        substring(1).toIntOrNull()
    } ?: return null
    return if (codePoint in Char.MIN_VALUE.code..Char.MAX_VALUE.code) {
        codePoint.toChar().toString()
    } else {
        null
    }
}
