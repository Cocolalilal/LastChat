package me.rerere.ai.generation

/**
 * LastChat message templates are a small Pebble subset: `message` / `role` /
 * `time` / `date`, filters, comments, whitespace control, and if/elseif/else.
 * Pebble itself cannot run on Kotlin/Native. Hosts share [renderMessageTemplate];
 * Android may wrap Pebble behind [PortableTemplateRuntime] for leftover syntax.
 */
fun renderMessageTemplate(
    template: String,
    context: Map<String, String>,
): String {
    if (template.isBlank() || template.trim() == "{{ message }}") {
        return context["message"].orEmpty()
    }
    val withoutComments = stripComments(template)
    return renderNodes(withoutComments, context)
}

@Deprecated("Use renderMessageTemplate", ReplaceWith("renderMessageTemplate(template, context)"))
fun renderSimpleMessageTemplate(
    template: String,
    context: Map<String, String>,
): String = renderMessageTemplate(template, context)

fun interface PortableTemplateRuntime {
    fun render(template: String, context: Map<String, String>): String
}

object DefaultPortableTemplateRuntime : PortableTemplateRuntime {
    override fun render(template: String, context: Map<String, String>): String =
        renderMessageTemplate(template, context)
}

private val COMMENT_REGEX = Regex(
    """\{#-?\s*.*?\s*-?#\}""",
    setOf(RegexOption.DOT_MATCHES_ALL),
)

private fun stripComments(template: String): String = COMMENT_REGEX.replace(template, "")

private fun renderNodes(input: String, context: Map<String, String>): String {
    val out = StringBuilder()
    var index = 0
    while (index < input.length) {
        val nextInterp = input.indexOf("{{", index)
        val nextTag = input.indexOf("{%", index)
        val next = when {
            nextInterp < 0 -> nextTag
            nextTag < 0 -> nextInterp
            else -> minOf(nextInterp, nextTag)
        }
        if (next < 0) {
            out.append(input.substring(index))
            break
        }
        out.append(input.substring(index, next))
        if (next == nextTag) {
            val tag = readDelimited(input, next, "{%", "%}") ?: break
            applyLeadingTrim(out, tag.trimStart)
            val body = tag.body.trim()
            when {
                body.startsWith("if ") || body == "if" -> {
                    val bodyStart = if (tag.trimEnd) skipWhitespace(input, tag.end) else tag.end
                    val block = readIfBlock(input, bodyStart) ?: break
                    val chosen = chooseIfBranch(tag.body, block, context)
                    val rendered = renderNodes(chosen, context)
                    out.append(rendered)
                    index = if (block.trimEnd) skipWhitespace(input, block.end) else block.end
                }
                body.startsWith("elseif") || body.startsWith("else") || body.startsWith("endif") -> {
                    // Orphan control tags are ignored so a truncated template still interpolates.
                    index = if (tag.trimEnd) skipWhitespace(input, tag.end) else tag.end
                }
                else -> {
                    out.append(input.substring(next, tag.end))
                    index = tag.end
                }
            }
            continue
        }
        val interp = readDelimited(input, next, "{{", "}}") ?: break
        applyLeadingTrim(out, interp.trimStart)
        out.append(evaluateExpression(interp.body, context))
        index = if (interp.trimEnd) skipWhitespace(input, interp.end) else interp.end
    }
    return out.toString()
}

private data class Delimited(
    val body: String,
    val end: Int,
    val trimStart: Boolean,
    val trimEnd: Boolean,
)

private data class IfBlock(
    val branches: List<Pair<String?, String>>,
    val end: Int,
    val trimStart: Boolean,
    val trimEnd: Boolean,
)

private fun readDelimited(
    input: String,
    start: Int,
    open: String,
    close: String,
): Delimited? {
    if (!input.startsWith(open, start)) return null
    val closeIndex = input.indexOf(close, start + open.length)
    if (closeIndex < 0) return null
    val raw = input.substring(start + open.length, closeIndex)
    val trimStart = raw.startsWith("-")
    val trimEnd = raw.endsWith("-")
    val body = raw
        .removePrefix("-")
        .removeSuffix("-")
        .trim()
    return Delimited(
        body = body,
        end = closeIndex + close.length,
        trimStart = trimStart,
        trimEnd = trimEnd,
    )
}

private fun readIfBlock(input: String, start: Int): IfBlock? {
    var depth = 1
    var cursor = start
    var branchCondition: String? = "if"
    val branchStart = intArrayOf(start)
    val branches = mutableListOf<Pair<String?, String>>()
    var trimStart = false
    var lastTrimEnd = false
    while (cursor < input.length) {
        val tagStart = input.indexOf("{%", cursor)
        if (tagStart < 0) return null
        val tag = readDelimited(input, tagStart, "{%", "%}") ?: return null
        val keyword = tag.body.substringBefore(' ').lowercase()
        when {
            keyword == "if" -> depth++
            keyword == "endif" && depth == 1 -> {
                branches += branchCondition to input.substring(branchStart[0], tagStart)
                trimStart = tag.trimStart
                lastTrimEnd = tag.trimEnd
                return IfBlock(branches, tag.end, trimStart, lastTrimEnd)
            }
            keyword == "endif" -> depth--
            depth == 1 && (keyword == "elseif" || keyword == "else") -> {
                branches += branchCondition to input.substring(branchStart[0], tagStart)
                branchCondition = if (keyword == "else") null else tag.body
                branchStart[0] = tag.end
                if (tag.trimStart) {
                    val last = branches.last()
                    branches[branches.lastIndex] = last.first to last.second.trimEnd()
                }
                if (tag.trimEnd) {
                    branchStart[0] = skipWhitespace(input, tag.end)
                }
            }
        }
        cursor = tag.end
    }
    return null
}

private fun chooseIfBranch(
    opening: String,
    block: IfBlock,
    context: Map<String, String>,
): String {
    block.branches.forEachIndexed { index, (condition, body) ->
        val expr = if (index == 0) opening else condition
        if (expr == null || evaluateCondition(expr, context)) {
            return body
        }
    }
    return ""
}

private fun evaluateCondition(raw: String, context: Map<String, String>): Boolean {
    val expr = raw
        .removePrefix("if")
        .removePrefix("elseif")
        .trim()
        .removePrefix("if")
        .trim()
    if (expr.isEmpty()) return false
    val negated = expr.startsWith("not ")
    val rest = if (negated) expr.removePrefix("not ").trim() else expr
    val result = when {
        " is not empty" in rest -> contextValue(rest.substringBefore(" is not empty").trim(), context).isNotBlank()
        " is empty" in rest -> contextValue(rest.substringBefore(" is empty").trim(), context).isBlank()
        " != " in rest -> {
            val (left, right) = splitOnce(rest, " != ")
            resolveOperand(left, context) != resolveOperand(right, context)
        }
        " == " in rest -> {
            val (left, right) = splitOnce(rest, " == ")
            resolveOperand(left, context) == resolveOperand(right, context)
        }
        else -> contextValue(rest, context).isNotBlank()
    }
    return if (negated) !result else result
}

private fun evaluateExpression(raw: String, context: Map<String, String>): String {
    val parts = splitFilters(raw)
    if (parts.isEmpty()) return ""
    var value = resolveOperand(parts.first(), context)
    parts.drop(1).forEach { filter ->
        value = applyFilter(value, filter, context)
    }
    return value
}

private fun splitFilters(raw: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    raw.forEach { ch ->
        when {
            quote != null -> {
                current.append(ch)
                if (ch == quote) quote = null
            }
            ch == '\'' || ch == '"' -> {
                quote = ch
                current.append(ch)
            }
            ch == '|' -> {
                parts += current.toString().trim()
                current.clear()
            }
            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) parts += current.toString().trim()
    return parts.filter { it.isNotEmpty() }
}

private fun applyFilter(value: String, spec: String, context: Map<String, String>): String {
    val name = spec.substringBefore('(').trim().lowercase()
    val args = parseFilterArgs(spec.substringAfter('(', missingDelimiterValue = "").substringBeforeLast(')'))
        .map { resolveOperand(it, context) }
    return when (name) {
        "trim" -> value.trim()
        "upper" -> value.uppercase()
        "lower" -> value.lowercase()
        "capitalize" -> value.lowercase().replaceFirstChar { it.titlecase() }
        "length" -> value.length.toString()
        "escape" -> value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        "default" -> value.ifBlank { args.firstOrNull().orEmpty() }
        "replace" -> value.replace(args.getOrNull(0).orEmpty(), args.getOrNull(1).orEmpty())
        else -> value
    }
}

private fun parseFilterArgs(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val args = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    raw.forEach { ch ->
        when {
            quote != null -> {
                current.append(ch)
                if (ch == quote) quote = null
            }
            ch == '\'' || ch == '"' -> {
                quote = ch
                current.append(ch)
            }
            ch == ',' -> {
                args += current.toString().trim()
                current.clear()
            }
            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) args += current.toString().trim()
    return args
}

private fun resolveOperand(raw: String, context: Map<String, String>): String {
    val trimmed = raw.trim()
    if (trimmed.length >= 2) {
        val quote = trimmed.first()
        if ((quote == '\'' || quote == '"') && trimmed.last() == quote) {
            return trimmed.substring(1, trimmed.lastIndex)
        }
    }
    return contextValue(trimmed, context)
}

private fun contextValue(key: String, context: Map<String, String>): String {
    val normalized = key.trim()
    if (normalized.isEmpty()) return ""
    return context[normalized].orEmpty()
}

private fun splitOnce(raw: String, delimiter: String): Pair<String, String> {
    val index = raw.indexOf(delimiter)
    if (index < 0) return raw to ""
    return raw.substring(0, index).trim() to raw.substring(index + delimiter.length).trim()
}

private fun applyLeadingTrim(out: StringBuilder, trim: Boolean) {
    if (!trim) return
    while (out.isNotEmpty() && out.last().isWhitespace()) {
        out.deleteAt(out.lastIndex)
    }
}

private fun skipWhitespace(input: String, start: Int): Int {
    var index = start
    while (index < input.length && input[index].isWhitespace()) index++
    return index
}
