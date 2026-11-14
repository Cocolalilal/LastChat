package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Decorations cannot enlarge a wrap-content platform window or its touch region. */
@Composable
internal fun FloatingOverlayLayout(
    content: @Composable () -> Unit,
    decoration: @Composable BoxScope.() -> Unit,
) {
    Box {
        content()
        Box(Modifier.matchParentSize(), content = decoration)
    }
}
