package me.rerere.rikkahub.utils

import kotlin.text.Regex

private val REGEX_CODE_BLOCK = Regex("```[\\s\\S]*?```|`[^`]*?`")
private val REGEX_IMAGE_LINK = Regex("!?\\[([^\\]]+)\\]\\([^\\)]*\\)")
private val REGEX_BOLD = Regex("\\*\\*([^*]+?)\\*\\*")
private val REGEX_ITALIC = Regex("\\*([^*]+?)\\*")
private val REGEX_UNDERLINE_DOUBLE = Regex("__([^_]+?)__")
private val REGEX_UNDERLINE_SINGLE = Regex("_([^_]+?)_")
private val REGEX_STRIKETHROUGH = Regex("~~([^~]+?)~~")
private val REGEX_HIGHLIGHT = Regex("==([^=]+?)==|<mark>(.*?)</mark>")
private val REGEX_UNDERLINE_PLUS = Regex("\\+\\+([^+]+?)\\+\\+")
private val REGEX_HTML_FORMATTING_TAG = Regex("(?i)</?(?:u|ins|mark|b|strong|i|em|s|del|strike|sub|sup|small|code|span|font)(?:\\s+[^>]*)?>")
private val REGEX_HEADING = Regex("(?m)^#+\\s*")
private val REGEX_LIST_BULLET = Regex("(?m)^\\s*[-*+]\\s+")
private val REGEX_LIST_NUMBERED = Regex("(?m)^\\s*\\d+\\.\\s+")
private val REGEX_BLOCKQUOTE = Regex("(?m)^>\\s*")
private val REGEX_HORIZONTAL_RULE = Regex("(?m)^(\\s*[-*_]){3,}\\s*$")
private val REGEX_MULTIPLE_NEWLINES = Regex("\n{3,}")

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
        // 移除高亮 (==...== 和 <mark>...</mark>)
        .replace(REGEX_HIGHLIGHT) { it.groupValues[1].ifEmpty { it.groupValues[2] } }
        // 移除加号下划线 (++...++)
        .replace(REGEX_UNDERLINE_PLUS, "$1")
        // 移除行内HTML格式化标签
        .replace(REGEX_HTML_FORMATTING_TAG, "")
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
