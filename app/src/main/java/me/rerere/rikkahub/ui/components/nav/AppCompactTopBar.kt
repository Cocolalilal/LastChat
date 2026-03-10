package me.rerere.rikkahub.ui.components.nav

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.rerere.rikkahub.ui.theme.AppSurfaceLevel
import me.rerere.rikkahub.ui.theme.appSurfaceColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCompactTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = appSurfaceColor(AppSurfaceLevel.Flat),
            scrolledContainerColor = appSurfaceColor(AppSurfaceLevel.Container),
        ),
    )
}
