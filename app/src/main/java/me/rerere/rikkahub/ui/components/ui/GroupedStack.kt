package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
private class GroupedStackState {
    val keys = mutableStateListOf<Any>()

    fun register(key: Any) {
        if (!keys.contains(key)) {
            keys.add(key)
        }
    }

    fun unregister(key: Any) {
        keys.remove(key)
    }
}

private val LocalGroupedStackState = staticCompositionLocalOf<GroupedStackState?> { null }

@Composable
fun GroupedStack(
    content: @Composable () -> Unit
) {
    val state = remember { GroupedStackState() }
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGroupedStackState provides state,
        content = content,
    )
}

@Composable
fun rememberGroupedStackPosition(
    explicitPosition: ItemPosition? = null
): ItemPosition {
    explicitPosition?.let { return it }

    val state = LocalGroupedStackState.current ?: return ItemPosition.ONLY
    val key = remember { Any() }

    DisposableEffect(state, key) {
        state.register(key)
        onDispose {
            state.unregister(key)
        }
    }

    val index = state.keys.indexOf(key)
    val count = state.keys.size
    return groupedStackPositionFor(index = index, count = count)
}

@Composable
fun GroupedStackItem(
    content: @Composable (ItemPosition) -> Unit
) {
    content(rememberGroupedStackPosition())
}

internal fun groupedStackPositionFor(
    index: Int,
    count: Int
): ItemPosition {
    return when {
        count <= 1 -> ItemPosition.ONLY
        index <= 0 -> ItemPosition.FIRST
        index >= count - 1 -> ItemPosition.LAST
        else -> ItemPosition.MIDDLE
    }
}
