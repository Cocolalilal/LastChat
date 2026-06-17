package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.runtime.staticCompositionLocalOf
import me.rerere.common.html.SimpleHtmlParser

internal val LocalSimpleHtmlParser = staticCompositionLocalOf<SimpleHtmlParser> {
    JsoupSimpleHtmlParser
}
