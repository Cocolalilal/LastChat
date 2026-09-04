package me.rerere.rikkahub.ui.core.components.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp

/**
 * Samsung One UI-style large collapsible app bar with centered title.
 * Takes up to 28% of screen height when expanded for comfortable one-handed use.
 *
 * Behavior:
 * - Title is vertically centered in the header area below the nav bar
 * - On scroll down: header collapses and title fades out smoothly (at 50% scroll)
 * - On scroll up: title reappears when fully scrolled to top
 * - Collapsed state shows title in the top bar (fades in at 80% scroll)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUITopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val screenHeight = maxHeight

        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarHeight = 56.dp

        val expandedHeight = (screenHeight * 0.28f).coerceIn(120.dp, 240.dp)
        val collapsedHeight = statusBarHeight + navBarHeight

        val titleAreaHeight = (expandedHeight - collapsedHeight).coerceAtLeast(0.dp)
        val heightDifference = expandedHeight - collapsedHeight
        val heightDifferencePx = with(density) { heightDifference.toPx() }

        SideEffect {
            scrollBehavior?.state?.heightOffsetLimit = -heightDifferencePx
        }

        val collapseProgress = scrollBehavior?.state?.collapsedFraction ?: 0f

        val currentHeight = lerp(expandedHeight, collapsedHeight, collapseProgress)

        val expandedTitleAlpha = if (collapseProgress < 0.50f) {
            1f - (collapseProgress / 0.50f)
        } else {
            0f
        }

        val collapsedTitleAlpha = if (collapseProgress > 0.80f) {
            ((collapseProgress - 0.80f) / 0.20f).coerceIn(0f, 1f)
        } else {
            0f
        }

        val currentTitleAreaHeight = lerp(titleAreaHeight, 0.dp, collapseProgress)

        Column(
            modifier = modifier
                .fillMaxWidth()
                .height(currentHeight)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Spacer(modifier = Modifier.height(statusBarHeight))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(navBarHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                navigationIcon()

                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    if (collapsedTitleAlpha > 0f) {
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = collapsedTitleAlpha),
                                maxLines = 1,
                            )
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = collapsedTitleAlpha),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }

            if (currentTitleAreaHeight > 0.dp && expandedTitleAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(currentTitleAreaHeight)
                        .graphicsLayer {
                            alpha = expandedTitleAlpha
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                lineHeight = 34.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
