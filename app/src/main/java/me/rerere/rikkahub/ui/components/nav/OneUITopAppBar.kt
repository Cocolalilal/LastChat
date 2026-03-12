package me.rerere.rikkahub.ui.components.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/**
 * Samsung One UI-style large collapsible app bar with centered title.
 * Takes 30% of screen height when expanded for one-handed use.
 * 
 * Behavior:
 * - Title is vertically centered in the header area below the nav bar
 * - On scroll down: header collapses and title fades out
 * - On scroll up: title only reappears when fully scrolled to top
 * - Collapsed state shows title in the top bar
 * 
 * @param title The title to display
 * @param scrollBehavior The scroll behavior from TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
 * @param modifier Modifier for the app bar
 * @param navigationIcon The navigation icon (usually BackButton)
 * @param actions The action buttons
 */
@Composable
fun OneUITopAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeight = configuration.screenHeightDp.dp
    
    // Get status bar height
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = 56.dp
    
    // Total expanded height is 30% of screen, INCLUDING status bar
    val expandedHeight = screenHeight * 0.30f
    val collapsedHeight = statusBarHeight + navBarHeight
    
    // The area available for the large title (below status bar + nav bar)
    val titleAreaHeight = expandedHeight - collapsedHeight
    
    // Calculate the height difference for scroll behavior
    val heightDifference = expandedHeight - collapsedHeight
    val heightDifferencePx = with(density) { heightDifference.toPx() }
    
    // Always set the height offset limit to the correct value
    SideEffect {
        scrollBehavior.state.heightOffsetLimit = -heightDifferencePx
    }
    
    // Get collapse progress from scroll behavior (0 = expanded, 1 = collapsed)
    val collapseProgress = scrollBehavior.state.collapsedFraction
    
    // Interpolate height based on collapse progress
    val currentHeight = lerp(expandedHeight, collapsedHeight, collapseProgress)
    
    // Large centered title: fade out slowly (at 50% scroll)
    val expandedTitleAlpha = if (collapseProgress < 0.50f) {
        1f - (collapseProgress / 0.50f)
    } else {
        0f
    }
    
    // Collapsed title in top bar: fade in after we've scrolled past 80%
    val collapsedTitleAlpha = if (collapseProgress > 0.80f) {
        ((collapseProgress - 0.80f) / 0.20f).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    // Current title area height (shrinks as we collapse)
    val currentTitleAreaHeight = lerp(titleAreaHeight, 0.dp, collapseProgress)
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(currentHeight)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Status bar spacer
        Spacer(modifier = Modifier.height(statusBarHeight))
        
        // Top nav bar row with back button, collapsed title, and actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(navBarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Navigation icon
            navigationIcon()
            
            // Collapsed title (appears when scrolled)
            Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                if (collapsedTitleAlpha > 0f) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = collapsedTitleAlpha)
                    )
                }
            }
            
            // Actions
            Row(
                horizontalArrangement = Arrangement.End,
                content = actions
            )
        }
        
        // Large centered title area (visible when expanded)
        if (currentTitleAreaHeight > 0.dp && expandedTitleAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentTitleAreaHeight)
                    .graphicsLayer {
                        alpha = expandedTitleAlpha
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp,
                        lineHeight = 38.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                )
            }
        }
    }
}




