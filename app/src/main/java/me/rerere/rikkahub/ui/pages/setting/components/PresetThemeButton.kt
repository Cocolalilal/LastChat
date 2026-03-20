package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
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
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.PresetTheme
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.ui.theme.normalizePresetThemeId

@Composable
fun PresetThemeButton(
    theme: PresetTheme,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val darkMode = LocalDarkMode.current
    val scheme = theme.getColorScheme(darkMode)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = {
                    onClick()
                }
            )
            .padding(8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .clip(CircleShape)
                    .size(48.dp)
            ) {
                drawRect(
                    color = scheme.primaryContainer,
                    size = size
                )
                drawRect(
                    color = scheme.secondaryContainer,
                    size = size,
                    topLeft = Offset(
                        x = size.width / 2,
                        y = 0f
                    ),
                )
                drawRect(
                    color = scheme.tertiaryContainer,
                    size = size,
                    topLeft = Offset(
                        x = size.width / 2,
                        y = size.height / 2
                    ),
                )
                drawCircle(
                    color = scheme.primary,
                    radius = if (selected) 12.dp.toPx() else 8.dp.toPx(),
                    center = Offset(
                        x = size.width / 2,
                        y = size.height / 2
                    )
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = scheme.contentColorFor(scheme.onPrimary)
                )
            }
        }
        ProvideTextStyle(
            value = MaterialTheme.typography.labelMedium.copy(color = scheme.primary)
        ) {
            theme.name()
        }
    }
}

@Composable
fun PresetThemeButtonGroup(
    themeId: String,
    modifier: Modifier = Modifier,
    onChangeTheme: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val selectedThemeId = normalizePresetThemeId(themeId)
    
    Column(
        modifier = modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                PresetThemes.fastForEach { theme ->
                    key(theme.id) {
                        PresetThemeButton(
                            theme = theme,
                            selected = theme.id == selectedThemeId,
                            onClick = {
                                onChangeTheme(theme.id)
                            },
                        )
                    }
                }
            }
            
            // Left fade when not at start
            if (scrollState.value > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(24.dp, 80.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                )
                            )
                        )
                )
            }
            
            // Right fade when not at end
            if (scrollState.value < scrollState.maxValue) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(24.dp, 80.dp)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PresetThemeButtonPreview() {
    var themeId by remember { mutableStateOf("ocean") }
    PresetThemeButtonGroup(
        themeId = themeId,
        onChangeTheme = { themeId = it }
    )
}
