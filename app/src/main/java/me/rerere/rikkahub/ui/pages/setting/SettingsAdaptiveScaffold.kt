package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.LocalBackButtonVisible
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsNavigationPane
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsPaneEntry
import me.rerere.rikkahub.ui.components.settings.LastChatSettingsPaneGroup

val LocalSettingsWideLayout = staticCompositionLocalOf { false }

private var settingsPaneScrollIndex = 0
private var settingsPaneScrollOffset = 0
private var lastSettingsPaneSelected: SettingsDestination? = null

enum class SettingsDestination {
    Display,
    Assistants,
    PromptInjections,
    Models,
    Providers,
    ProviderModels,
    Search,
    Tts,
    Mcp,
    Web,
    AndroidIntegration,
    Backup,
    BackupWebDav,
    BackupLocal,
    ChatStorage,
    Lorebooks,
    Skills,
    About,
    Fonts,
    UiCustomization,
    RpOptimizations,
    Workspaces,
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
    val navController = LocalNavController.current

    if (!useWideLayout) {
        compactContent?.invoke() ?: detailContent()
        return
    }

    BackHandler {
        handleSettingsPaneBack(navController)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CompositionLocalProvider(LocalSettingsWideLayout provides true) {
            SettingsNavigationPane(selected = selected)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = 900.dp)
                    .fillMaxWidth()
            ) {
                CompositionLocalProvider(
                    LocalBackButtonVisible provides false,
                    LocalSettingsWideLayout provides true,
                ) {
                    detailContent()
                }
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
    var displayedSelected by remember { mutableStateOf(lastSettingsPaneSelected ?: selected) }
    val selectedMain = displayedSelected.mainDestination()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = settingsPaneScrollIndex,
        initialFirstVisibleItemScrollOffset = settingsPaneScrollOffset,
    )

    LaunchedEffect(selected) {
        displayedSelected = selected
        lastSettingsPaneSelected = selected
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            settingsPaneScrollIndex = index
            settingsPaneScrollOffset = offset
        }
    }

    val haptics = rememberPremiumHaptics()
    LastChatSettingsNavigationPane(
        groups = settingsPaneGroupsForRenderer(groups),
        selectedId = displayedSelected.name,
        selectedMainId = selectedMain.name,
        title = stringResource(R.string.settings),
        listState = listState,
        onBack = { handleSettingsPaneBack(navController) },
        onHaptic = { haptics.perform(HapticPattern.Pop) },
        onNavigate = { destinationId ->
            findSettingsPaneEntry(groups, destinationId)?.let { destination ->
                navigateSettingsPane(navController, destination)
            }
        },
    )
}

private data class SettingsPaneEntry(
    val destination: SettingsDestination,
    val titleRes: Int,
    val descriptionRes: Int?,
    val icon: ImageVector,
    val screen: Screen,
    val children: List<SettingsPaneEntry> = emptyList(),
)

private data class SettingsPaneGroup(
    val titleRes: Int,
    val entries: List<SettingsPaneEntry>,
)

@Composable
private fun settingsPaneGroupsForRenderer(
    groups: List<SettingsPaneGroup>,
): List<LastChatSettingsPaneGroup> = buildList {
    for (group in groups) {
        add(
            LastChatSettingsPaneGroup(
                id = group.titleRes.toString(),
                title = stringResource(group.titleRes),
                entries = buildList {
                    for (entry in group.entries) add(entry.forRenderer())
                },
            )
        )
    }
}

@Composable
private fun SettingsPaneEntry.forRenderer(): LastChatSettingsPaneEntry =
    LastChatSettingsPaneEntry(
        id = destination.name,
        title = stringResource(titleRes),
        description = descriptionRes?.let { stringResource(it) },
        icon = icon,
        children = buildList {
            for (child in children) add(child.forRenderer())
        },
    )

private fun findSettingsPaneEntry(
    groups: List<SettingsPaneGroup>,
    destinationId: String,
): SettingsPaneEntry? {
    fun SettingsPaneEntry.find(): SettingsPaneEntry? {
        if (destination.name == destinationId) return this
        for (child in children) child.find()?.let { return it }
        return null
    }
    for (group in groups) {
        for (entry in group.entries) entry.find()?.let { return it }
    }
    return null
}

private fun navigateSettingsPane(
    navController: NavHostController,
    entry: SettingsPaneEntry,
) {
    navController.navigate(entry.screen) {
        launchSingleTop = true
    }
}

private fun handleSettingsPaneBack(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntry?.destination?.route.orEmpty()
    val parent = settingsDetailParent(currentRoute)
    if (parent != null) {
        navController.navigate(parent) {
            launchSingleTop = true
        }
        return
    }

    repeat(24) {
        val route = navController.currentBackStackEntry?.destination?.route
        if (!isSettingsPaneRoute(route)) {
            return
        }
        if (!navController.popBackStack()) {
            return
        }
    }
}

private fun settingsDetailParent(route: String): Screen? {
    return when {
        route.contains("SettingProviderDetail") -> Screen.SettingProvider
        route.contains("SettingTTSProviderDetail") -> Screen.SettingTTS
        route.contains("SettingLorebookDetail") -> Screen.SettingLorebooks
        route.contains("AssistantDetail") -> Screen.Assistant
        else -> null
    }
}

private fun isSettingsPaneRoute(route: String?): Boolean {
    return route != null && (
        route.contains("Setting") ||
            route.contains("Assistant") ||
            route.contains("Backup") ||
            route.contains("Workspace")
        )
}

private fun SettingsDestination.mainDestination(): SettingsDestination {
    return when (this) {
        SettingsDestination.Fonts,
        SettingsDestination.UiCustomization,
        SettingsDestination.RpOptimizations -> SettingsDestination.Display
        SettingsDestination.BackupWebDav,
        SettingsDestination.BackupLocal -> SettingsDestination.Backup
        SettingsDestination.ProviderModels,
        SettingsDestination.Search,
        SettingsDestination.Tts -> SettingsDestination.Providers
        SettingsDestination.Skills,
        SettingsDestination.Lorebooks -> SettingsDestination.PromptInjections
        else -> this
    }
}

private fun settingsPaneGroups(): List<SettingsPaneGroup> {
    val displayChildren = listOf(
        SettingsPaneEntry(SettingsDestination.Fonts, R.string.setting_fonts_title, null, Icons.Rounded.Tune, Screen.SettingFonts),
        SettingsPaneEntry(SettingsDestination.UiCustomization, R.string.setting_ui_customization_title, null, Icons.Rounded.Brush, Screen.SettingUICustomization),
        SettingsPaneEntry(SettingsDestination.RpOptimizations, R.string.setting_rp_optimizations_title, null, Icons.Rounded.AutoAwesome, Screen.SettingRpOptimizations),
    )
    val providerChildren = listOf(
        SettingsPaneEntry(SettingsDestination.ProviderModels, R.string.setting_provider_page_title, null, Icons.Rounded.Cloud, Screen.SettingProvider),
        SettingsPaneEntry(SettingsDestination.Search, R.string.setting_page_search_service, null, Icons.Rounded.Public, Screen.SettingSearch),
        SettingsPaneEntry(SettingsDestination.Tts, R.string.setting_page_tts_service, null, Icons.AutoMirrored.Rounded.VolumeUp, Screen.SettingTTS),
    )
    val promptChildren = listOf(
        SettingsPaneEntry(SettingsDestination.Skills, R.string.prompt_injections_page_skills, null, Icons.Rounded.Code, Screen.SettingSkills()),
        SettingsPaneEntry(SettingsDestination.Lorebooks, R.string.prompt_injections_page_lorebooks, null, Icons.Rounded.Folder, Screen.SettingLorebooks),
    )
    val backupChildren = listOf(
        SettingsPaneEntry(SettingsDestination.BackupWebDav, R.string.backup_page_webdav_backup, null, Icons.Rounded.CloudUpload, Screen.BackupWebDav),
        SettingsPaneEntry(SettingsDestination.BackupLocal, R.string.backup_page_import_export, null, Icons.Rounded.FileUpload, Screen.BackupLocal),
    )

    return listOf(
        SettingsPaneGroup(
            titleRes = R.string.setting_page_general_settings,
            entries = listOf(
                SettingsPaneEntry(SettingsDestination.Display, R.string.setting_page_display_setting, null, Icons.Rounded.Tune, Screen.SettingDisplay, displayChildren),
                SettingsPaneEntry(SettingsDestination.Assistants, R.string.setting_page_assistant, null, Icons.Rounded.Group, Screen.Assistant),
                SettingsPaneEntry(SettingsDestination.PromptInjections, R.string.setting_page_prompt_injections, null, Icons.Rounded.Extension, Screen.SettingPromptInjections, promptChildren),
            )
        ),
        SettingsPaneGroup(
            titleRes = R.string.setting_page_model_and_services,
            entries = listOf(
                SettingsPaneEntry(SettingsDestination.Models, R.string.setting_page_default_model, null, Icons.Rounded.AccountTree, Screen.SettingModels),
                SettingsPaneEntry(SettingsDestination.Providers, R.string.setting_page_providers, null, Icons.Rounded.Cloud, Screen.SettingProvider, providerChildren),
                SettingsPaneEntry(SettingsDestination.Mcp, R.string.setting_page_mcp, null, Icons.Rounded.Code, Screen.SettingMcp),
                SettingsPaneEntry(SettingsDestination.Web, R.string.setting_page_web_server, null, Icons.Rounded.Language, Screen.SettingWeb),
                SettingsPaneEntry(SettingsDestination.AndroidIntegration, R.string.setting_android_integration, null, Icons.Rounded.PhoneAndroid, Screen.SettingAndroidIntegration),
                SettingsPaneEntry(SettingsDestination.Workspaces, R.string.extensions_page_workspace, null, Icons.Rounded.Code, Screen.Workspaces),
            )
        ),
        SettingsPaneGroup(
            titleRes = R.string.setting_page_data_settings,
            entries = listOf(
                SettingsPaneEntry(SettingsDestination.Backup, R.string.setting_page_data_backup, null, Icons.Rounded.CloudUpload, Screen.BackupWebDav, backupChildren),
                SettingsPaneEntry(SettingsDestination.ChatStorage, R.string.setting_page_chat_storage, null, Icons.Rounded.Storage, Screen.SettingChatStorage),
            )
        ),
        SettingsPaneGroup(
            titleRes = R.string.setting_page_about,
            entries = listOf(
                SettingsPaneEntry(SettingsDestination.About, R.string.setting_page_about, null, Icons.Rounded.Info, Screen.SettingAbout),
            )
        ),
    )
}
