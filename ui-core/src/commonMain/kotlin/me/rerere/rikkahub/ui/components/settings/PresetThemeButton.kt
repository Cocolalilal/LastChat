package me.rerere.rikkahub.ui.components.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.presetColorScheme

data class PresetThemeInfo(
    val id: String,
    val name: String,
)

val PRESET_THEMES = listOf(
    PresetThemeInfo("ios", "iOS"),
    PresetThemeInfo("seafoam_mint", "Seafoam"),
    PresetThemeInfo("ocean", "Ocean"),
    PresetThemeInfo("sakura", "Sakura"),
    PresetThemeInfo("spring", "Spring"),
    PresetThemeInfo("autumn", "Autumn"),
    PresetThemeInfo("black", "Monochrome"),
)

@Composable
fun PresetThemeButton(
    id: String,
    name: String,
    selected: Boolean,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = remember(id, darkTheme) { presetColorScheme(id, darkTheme) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .clip(CircleShape)
                    .size(48.dp)
            ) {
                // Quadrant 1 (background): primaryContainer
                drawRect(
                    color = scheme.primaryContainer,
                    size = size,
                )
                // Quadrant 2 (top right): secondaryContainer
                drawRect(
                    color = scheme.secondaryContainer,
                    size = size,
                    topLeft = Offset(x = size.width / 2, y = 0f),
                )
                // Quadrant 3 (bottom right): tertiaryContainer
                drawRect(
                    color = scheme.tertiaryContainer,
                    size = size,
                    topLeft = Offset(x = size.width / 2, y = size.height / 2),
                )
                // Center circular focal badge: primary
                drawCircle(
                    color = scheme.primary,
                    radius = if (selected) 12.dp.toPx() else 8.dp.toPx(),
                    center = Offset(x = size.width / 2, y = size.height / 2),
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.primary,
        )
    }
}

@Composable
fun PresetThemeButtonGroup(
    selectedThemeId: String,
    darkTheme: Boolean,
    onChangeTheme: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PRESET_THEMES.forEach { theme ->
            PresetThemeButton(
                id = theme.id,
                name = theme.name,
                selected = theme.id == selectedThemeId,
                darkTheme = darkTheme,
                onClick = { onChangeTheme(theme.id) },
            )
        }
    }
}
