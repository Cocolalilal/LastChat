package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun AppActionMenuContent(
    modifier: Modifier = Modifier,
    destructiveAction: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(min = 224.dp)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GroupedStack(content = content)
        destructiveAction?.invoke(this)
    }
}

@Composable
fun AppActionMenuItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onClick: () -> Unit,
) {
    AppPickerRow(
        icon = { Icon(icon, contentDescription = null) },
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        style = AppPickerRowStyle.PlacedSurface,
        onClick = onClick,
    )
}

@Composable
fun AppActionMenuDestructiveItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    onClick: () -> Unit,
) {
    AppPickerRow(
        icon = { Icon(icon, contentDescription = null) },
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        position = ItemPosition.ONLY,
        style = AppPickerRowStyle.Destructive,
        onClick = onClick,
    )
}
