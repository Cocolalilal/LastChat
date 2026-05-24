package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.LocalBackButtonVisible
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup

enum class SettingsDestination {
    Display,
    Assistants,
    PromptInjections,
    Models,
    Providers,
    Search,
    Tts,
    Mcp,
    Web,
    AndroidIntegration,
    Backup,
    ChatStorage,
    Lorebooks,
    Skills,
    About,
    Fonts,
    UiCustomization,
    RpOptimizations,
}

@Composable
fun AdaptiveSettingsScaffold(
    selected: SettingsDestination,
    modifier: Modifier = Modifier,
    compactContent: (@Composable () -> Unit)? = null,
    detailContent: @Composable () -> Unit,
) {
    val windowSize = currentWindowDpSize()
    val useWideLayout = windowSize.width >= 840.dp && windowSize.height >= 600.dp

    if (!useWideLayout) {
        compactContent?.invoke() ?: detailContent()
        return
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SettingsNavigationPane(selected = selected)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            CompositionLocalProvider(LocalBackButtonVisible provides false) {
                detailContent()
            }
        }
    }
}

@Composable
private fun SettingsNavigationPane(
    selected: SettingsDestination,
    navController: NavHostController = LocalNavController.current,
) {
    val groups = settingsPaneGroups()

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(336.dp)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
            }

            groups.forEach { group ->
                item(key = group.titleRes) {
                    SettingsGroup(
                        title = stringResource(group.titleRes),
                        horizontalPadding = 0.dp,
                        titleStartPadding = 12.dp,
                    ) {
                        group.entries.forEach { entry ->
                            SettingsPaneItem(
                                title = stringResource(entry.titleRes),
                                icon = entry.icon,
                                selected = selected == entry.destination,
                                onClick = {
                                    navigateSettingsPane(navController, entry.screen)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SettingsPaneEntry(
    val destination: SettingsDestination,
    val titleRes: Int,
    val icon: ImageVector,
    val screen: Screen,
)

private data class SettingsPaneGroup(
    val titleRes: Int,
    val entries: List<SettingsPaneEntry>,
)

private fun navigateSettingsPane(
    navController: NavHostController,
    screen: Screen,
) {
    runCatching {
        navController.navigate(screen) {
            launchSingleTop = true
            popUpTo(Screen.Setting) {
                inclusive = false
                saveState = false
            }
        }
    }.onFailure {
        navController.navigate(screen) {
            launchSingleTop = true
        }
    }
}

@Composable
private fun SettingsPaneItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "settings_pane_item_scale"
    )
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = {
            if (!selected) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Row(
            modifier = Modifier
                .height(58.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(19.dp))
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun settingsPaneGroups(): List<SettingsPaneGroup> {
    return listOf(
        SettingsPaneGroup(
            titleRes = R.string.setting_page_general_settings,
            entries = listOf(
                SettingsPaneEntry(SettingsDestination.Display, R.string.setting_page_display_setting, Icons.Rounded.DesktopWindows, Screen.SettingDisplay),
                SettingsPaneEntry(SettingsDestination.UiCustomization, R.string.setting_ui_customization_title, Icons.Rounded.Brush, Screen.SettingUICustomization),
                SettingsPaneEntry(SettingsDestination.Fonts, R.string.setting_fonts_title, Icons.Rounded.Tune, Screen.SettingFonts),
                SettingsPaneEntry(SettingsDestination.RpOptimizations, R.string.setting_rp_optimizations_title, Icons.Rounded.AutoAwesome, Screen.SettingRpOptimizations),
                SettingsPaneEntry(SettingsDestination.Assistants, R.string.setting_page_assistant, Icons.Rounded.Group, Screen.Assistant),
                SettingsPaneEntry(SettingsDestination.PromptInjections, R.string.setting_page_prompt_injections, Icons.Rounded.Extension, Screen.SettingPromptInjections),
                SettingsPaneEntry(SettingsDestination.Skills, R.string.prompt_injections_page_skills, Icons.Rounded.Code, Screen.SettingSkills()),
                SettingsPaneEntry(SettingsDestination.Lorebooks, R.string.prompt_injections_page_lorebooks, Icons.Rounded.Folder, Screen.SettingLorebooks),
            )
        ),
        SettingsPaneGroup(
            titleRes = R.string.setting_page_model_and_services,
            entries = listOf(
                SettingsPaneEntry(SettingsDestination.Models, R.string.setting_page_default_model, Icons.Rounded.AccountTree, Screen.SettingModels),
                SettingsPaneEntry(SettingsDestination.Providers, R.string.setting_page_providers, Icons.Rounded.Cloud, Screen.SettingProvider),
                SettingsPaneEntry(SettingsDestination.Search, R.string.setting_page_search_service, Icons.Rounded.Public, Screen.SettingSearch),
                SettingsPaneEntry(SettingsDestination.Tts, R.string.setting_page_tts_service, Icons.Rounded.RecordVoiceOver, Screen.SettingTTS),
                SettingsPaneEntry(SettingsDestination.Mcp, R.string.setting_page_mcp, Icons.Rounded.Settings, Screen.SettingMcp),
                SettingsPaneEntry(SettingsDestination.Web, R.string.setting_page_web_server, Icons.Rounded.Language, Screen.SettingWeb),
                SettingsPaneEntry(SettingsDestination.AndroidIntegration, R.string.setting_android_integration, Icons.Rounded.PhoneAndroid, Screen.SettingAndroidIntegration),
            )
        ),
        SettingsPaneGroup(
            titleRes = R.string.setting_page_data_settings,
            entries = listOf(
                SettingsPaneEntry(SettingsDestination.Backup, R.string.setting_page_data_backup, Icons.Rounded.CloudUpload, Screen.Backup),
                SettingsPaneEntry(SettingsDestination.ChatStorage, R.string.setting_page_chat_storage, Icons.Rounded.Storage, Screen.SettingChatStorage),
            )
        ),
        SettingsPaneGroup(
            titleRes = R.string.setting_page_about,
            entries = listOf(
                SettingsPaneEntry(SettingsDestination.About, R.string.setting_page_about, Icons.Rounded.Info, Screen.SettingAbout),
            )
        ),
    )
}
