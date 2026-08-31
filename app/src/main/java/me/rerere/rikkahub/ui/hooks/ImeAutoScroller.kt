package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

@Composable
fun ImeLazyListAutoScroller(
    lazyListState: LazyListState,
) {
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    var imeHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(density) {
        var pendingScroll = 0f
        snapshotFlow {
            ime.getBottom(density)
        }.collect { currentImeHeight ->
            val diff = currentImeHeight - imeHeight
            imeHeight = currentImeHeight

            if (diff > 0) {
                pendingScroll += diff
            } else if (diff < 0) {
                pendingScroll = (pendingScroll + diff).coerceAtLeast(0f)
            }

            if (pendingScroll > 0f && !lazyListState.isScrollInProgress) {
                val toScroll = pendingScroll
                try {
                    val consumed = lazyListState.scrollBy(toScroll)
                    pendingScroll = (pendingScroll - consumed).coerceAtLeast(0f)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
        }
    }
}
