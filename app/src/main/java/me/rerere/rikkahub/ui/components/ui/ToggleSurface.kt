package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun ToggleSurface(
    checked: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(50),
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val colors =
        if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    Surface(
        onClick = onClick,
        color = colors,
        modifier = modifier,
        shape = shape,
        tonalElevation = if (checked) 8.dp else 0.dp
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            content()
        }
    }
}
