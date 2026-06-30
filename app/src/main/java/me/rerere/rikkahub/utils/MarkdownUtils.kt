package me.rerere.rikkahub.utils

import kotlin.text.Regex

private val REGEX_CODE_BLOCK = Regex("```[\\s\\S]*?```|`[^`]*?`")
private val REGEX_IMAGE_LINK = Regex("!?\\[([^\\]]+)\\]\\([^\\)]*\\)")
private val REGEX_BOLD = Regex("\\*\\*([^*]+?)\\*\\*")
private val REGEX_ITALIC = Regex("\\*([^*]+?)\\*")
private val REGEX_UNDERLINE_DOUBLE = Regex("__([^_]+?)__")
private val REGEX_UNDERLINE_SINGLE = Regex("_([^_]+?)_")
private val REGEX_STRIKETHROUGH = Regex("~~([^~]+?)~~")
private val REGEX_HEADING = Regex("(?m)^#+\\s*")
private val REGEX_LIST_BULLET = Regex("(?m)^\\s*[-*+]\\s+")
private val REGEX_LIST_NUMBERED = Regex("(?m)^\\s*\\d+\\.\\s+")
private val REGEX_BLOCKQUOTE = Regex("(?m)^>\\s*")
private val REGEX_HORIZONTAL_RULE = Regex("(?m)^(\\s*[-*_]){3,}\\s*$")
private val REGEX_MULTIPLE_NEWLINES = Regex("\n{3,}")
private val REGEX_BOLD_LINE = Regex("^\\*\\*(.+?)\\*\\*$")

/**
 * 移除字符串中的Markdown格式
 * @return 移除Markdown格式后的纯文本
 */
fun String.stripMarkdown(): String {
    return this
        // 移除代码块 (```...``` 和 `...`)
        .replace(REGEX_CODE_BLOCK, "")
        // 移除图片和链接，但保留其文本内容
        .replace(REGEX_IMAGE_LINK, "$1")
        // 移除加粗和斜体 (先处理两个星号的)
        .replace(REGEX_BOLD, "$1")
        .replace(REGEX_ITALIC, "$1")
        // 移除下划线
        .replace(REGEX_UNDERLINE_DOUBLE, "$1")
        .replace(REGEX_UNDERLINE_SINGLE, "$1")
        // 移除删除线
        .replace(REGEX_STRIKETHROUGH, "$1")
        // 移除标题标记 (多行模式)
        .replace(REGEX_HEADING, "")
        // 移除列表标记 (多行模式)
        .replace(REGEX_LIST_BULLET, "")
        .replace(REGEX_LIST_NUMBERED, "")
        // 移除引用标记 (多行模式)
        .replace(REGEX_BLOCKQUOTE, "")
        // 移除水平分割线
        .replace(REGEX_HORIZONTAL_RULE, "")
        // 将多个换行符压缩，以保留段落
        .replace(REGEX_MULTIPLE_NEWLINES, "\n\n")
        .trim()
}

fun String.extractGeminiThinkingTitle(): String? {
    // 按行分割文本
    val lines = this.lines()

    // 从后往前查找最后一个符合条件的加粗文本行
    for (i in lines.indices.reversed()) {
        val line = lines[i].trim()

        // 检查是否为加粗格式且独占一整行
        val match = REGEX_BOLD_LINE.find(line)

        if (match != null) {
            // 返回加粗标记内的文本内容
            return match.groupValues[1].trim()
        }
    }

    return null
}
