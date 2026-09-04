package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.graphics.Color

data class HighlightColorPalette(
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val operator: Color,
    val punctuation: Color,
    val className: Color,
    val property: Color,
    val boolean: Color,
    val variable: Color,
    val tag: Color,
    val attrName: Color,
    val attrValue: Color,
    val fallback: Color,
)

val AtomOneDarkHighlightPalette = HighlightColorPalette(
    keyword = Color(0xFFC678DD),
    string = Color(0xFF98C379),
    number = Color(0xFFD19A66),
    comment = Color(0xFF5C6370),
    function = Color(0xFF61AFEF),
    operator = Color(0xFF56B6C2),
    punctuation = Color(0xFFABB2BF),
    className = Color(0xFFE5C07B),
    property = Color(0xFFE06C75),
    boolean = Color(0xFFD19A66),
    variable = Color(0xFFE06C75),
    tag = Color(0xFFE06C75),
    attrName = Color(0xFFD19A66),
    attrValue = Color(0xFF98C379),
    fallback = Color(0xFFABB2BF),
)

val AtomOneLightHighlightPalette = HighlightColorPalette(
    keyword = Color(0xFFA626A4),
    string = Color(0xFF50A14F),
    number = Color(0xFFC18401),
    comment = Color(0xFF9CA0A4),
    function = Color(0xFF4078F2),
    operator = Color(0xFF0184BC),
    punctuation = Color(0xFF383A42),
    className = Color(0xFFC18401),
    property = Color(0xFFE45649),
    boolean = Color(0xFFC18401),
    variable = Color(0xFFE45649),
    tag = Color(0xFFE45649),
    attrName = Color(0xFFC18401),
    attrValue = Color(0xFF50A14F),
    fallback = Color(0xFF383A42),
)
