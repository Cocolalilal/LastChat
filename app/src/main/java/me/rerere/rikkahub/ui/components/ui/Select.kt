package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Select(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionToString: @Composable (T) -> String = { it.toString() },
    optionLeading: @Composable ((T) -> Unit)? = null,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberPremiumHaptics()

    // Physics: Standard spring for rotation
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 400f
        ),
        label = "select_arrow_rotation"
    )

    // Physics: Scale on press
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.6f, // Round/Clicky Standard
            stiffness = 300f
        ),
        label = "select_scale"
    )

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { newExpanded ->
            // Only trigger haptics if the state actually changed via external dismissal
            // (e.g. clicking outside). Anchor clicks are handled manually.
            if (expanded != newExpanded) {
                 expanded = newExpanded
            }
        }
    ) {
        // We wrap the anchor in a Box to apply the menuAnchor modifier.
        // This allows the inner Surface to handle clicks and animations independently
        // without conflict from the exposed dropdown logic.
        Box(
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        ) {
            Surface(
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    expanded = !expanded
                },
                tonalElevation = 4.dp,
                shape = AppShapes.ButtonPill,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                interactionSource = interactionSource
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    leading()
                    Text(
                        text = optionToString(selectedOption),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    trailing()
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "expand",
                        modifier = Modifier.rotate(rotation)
                    )
                }
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            },
            shape = AppShapes.CardMedium
        ) {
            options.fastForEach { option ->
                DropdownMenuItem(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        onOptionSelected(option)
                        expanded = false
                    },
                    text = {
                        Text(
                            text = optionToString(option),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    leadingIcon = optionLeading?.let {
                        { it(option) }
                    }
                )
            }
        }
    }
}
