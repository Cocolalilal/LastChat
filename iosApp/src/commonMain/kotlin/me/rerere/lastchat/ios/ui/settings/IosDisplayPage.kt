package me.rerere.lastchat.ios.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.lastchat.ios.IosAppearancePreferences
import me.rerere.lastchat.ios.IosColorMode
import me.rerere.rikkahub.ui.components.settings.LastChatFormItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupInputItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingGroupItem
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsGroup
import me.rerere.rikkahub.ui.components.settings.PresetThemeButtonGroup
import me.rerere.rikkahub.ui.theme.AppShapes

@Composable
fun IosDisplayPage(
    appearance: IosAppearancePreferences,
    darkTheme: Boolean,
    onSaveAppearance: (themeId: String, colorMode: IosColorMode, amoledBlack: Boolean, useDynamicColor: Boolean) -> Unit,
    onSaveFontSettings: (usePhoneSystemFont: Boolean) -> Unit,
    onSaveUiCustomization: (showAssistantBubbles: Boolean, fontSizeRatio: Float) -> Unit,
    onHapticPop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var themeId by remember(appearance.themeId) { mutableStateOf(appearance.themeId) }
    var colorMode by remember(appearance.colorMode) { mutableStateOf(appearance.colorMode) }
    var amoledBlack by remember(appearance.amoledBlack) { mutableStateOf(appearance.amoledBlack) }
    var useDynamicColor by remember(appearance.useDynamicColor) { mutableStateOf(appearance.useDynamicColor) }
    var usePhoneSystemFont by remember(appearance.usePhoneSystemFont) { mutableStateOf(appearance.usePhoneSystemFont) }
    var showAssistantBubbles by remember(appearance.showAssistantBubbles) { mutableStateOf(appearance.showAssistantBubbles) }
    var fontSizeRatio by remember(appearance.fontSizeRatio) { mutableFloatStateOf(appearance.fontSizeRatio) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Theme & Palette Group
        item {
            LastChatSettingsGroup(title = "Theme settings") {
                LastChatFormItem(label = { Text("Color mode") }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IosColorMode.entries.forEach { mode ->
                            val label = mode.name.lowercase().replaceFirstChar { it.uppercase() }
                            val isSelected = colorMode == mode
                            if (isSelected) {
                                Button(
                                    onClick = {},
                                    shape = AppShapes.ButtonPill,
                                ) { Text(label) }
                            } else {
                                TextButton(
                                    onClick = {
                                        onHapticPop()
                                        colorMode = mode
                                        onSaveAppearance(themeId, colorMode, amoledBlack, useDynamicColor)
                                    },
                                    shape = AppShapes.ButtonPill,
                                ) { Text(label) }
                            }
                        }
                    }
                }

                LastChatFormItem(
                    label = { Text("AMOLED Pure Black") },
                    description = { Text("Use true black #000000 for backgrounds and cards in dark mode") },
                    tail = {
                        Switch(
                            checked = amoledBlack,
                            onCheckedChange = {
                                onHapticPop()
                                amoledBlack = it
                                onSaveAppearance(themeId, colorMode, amoledBlack, useDynamicColor)
                            },
                        )
                    },
                )

                LastChatFormItem(
                    label = { Text("Dynamic Color") },
                    description = { Text("Derive palette accents dynamically") },
                    tail = {
                        Switch(
                            checked = useDynamicColor,
                            onCheckedChange = {
                                onHapticPop()
                                useDynamicColor = it
                                onSaveAppearance(themeId, colorMode, amoledBlack, useDynamicColor)
                            },
                        )
                    },
                )

                LastChatFormItem(
                    label = { Text("Theme palette") },
                    description = { Text("Swipe to see all available palettes") },
                ) {
                    PresetThemeButtonGroup(
                        selectedThemeId = themeId,
                        darkTheme = darkTheme,
                        onChangeTheme = { newId ->
                            onHapticPop()
                            themeId = newId
                            onSaveAppearance(themeId, colorMode, amoledBlack, useDynamicColor)
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }

        // Typography Group
        item {
            LastChatSettingsGroup(title = "Fonts & Typography") {
                LastChatFormItem(
                    label = { Text("Use phone system font") },
                    description = { Text("Use Apple's native San Francisco font throughout the app") },
                    tail = {
                        Switch(
                            checked = usePhoneSystemFont,
                            onCheckedChange = {
                                onHapticPop()
                                usePhoneSystemFont = it
                                onSaveFontSettings(it)
                            },
                        )
                    },
                )
            }
        }

        // UI Presentation & Scale Group
        item {
            LastChatSettingsGroup(title = "Chat presentation") {
                LastChatFormItem(
                    label = { Text("Assistant message bubbles") },
                    description = { Text("Display filled bubble containers for assistant messages") },
                    tail = {
                        Switch(
                            checked = showAssistantBubbles,
                            onCheckedChange = {
                                onHapticPop()
                                showAssistantBubbles = it
                                onSaveUiCustomization(showAssistantBubbles, fontSizeRatio)
                            },
                        )
                    },
                )

                LastChatFormItem(
                    label = { Text("Chat font size") },
                    description = { Text("${(fontSizeRatio * 100).toInt()}%") },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Slider(
                            value = fontSizeRatio,
                            onValueChange = { fontSizeRatio = it },
                            onValueChangeFinished = {
                                onSaveUiCustomization(showAssistantBubbles, fontSizeRatio)
                            },
                            valueRange = 0.5f..2f,
                            steps = 11,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Sample Preview Card
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ) {
                                        Text(
                                            text = "Sample outgoing message",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontSizeRatio,
                                            ),
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                                        color = if (showAssistantBubbles) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    ) {
                                        Text(
                                            text = "The quick brown fox jumps over the lazy dog.",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontSizeRatio,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}
