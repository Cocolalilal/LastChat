package me.rerere.rikkahub.ui.pages.setting

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.LocalBackButtonVisible
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics

val LocalSettingsWideLayout = staticCompositionLocalOf { false }

private var settingsPaneScrollIndex = 0
private var settingsPaneScrollOffset = 0
private var lastSettingsPaneSelected: SettingsDestination? = null

private const val SettingsPanePressMillis = 80
private const val SettingsPaneFadeMillis = 90
private const val SettingsPaneShapeMillis = 120
private const val SettingsPaneExpandMillis = 140

private val SettingsPaneItemOuterRadius = 24.dp
private val SettingsPaneItemInnerRadius = 8.dp
private val SettingsPaneChildOuterRadius = 20.dp
private val SettingsPaneChildInnerRadius = 8.dp

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

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(336.dp)
            .statusBarsPadding()
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val haptics = rememberPremiumHaptics()
                    IconButton(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            handleSettingsPaneBack(navController)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            groups.forEach { group ->
                item(key = group.titleRes) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(group.titleRes),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp)
                        )
                        SettingsPaneSection(
                            group = group,
                            selected = displayedSelected,
                            selectedMain = selectedMain,
                            onNavigate = { destination ->
                                navigateSettingsPane(navController, destination)
                            }
                        )
                    }
                }
            }
        }
    }
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

@Composable
private fun SettingsPaneSection(
    group: SettingsPaneGroup,
    selected: SettingsDestination,
    selectedMain: SettingsDestination,
    onNavigate: (SettingsPaneEntry) -> Unit,
) {
    val expandedEntry = group.entries.firstOrNull { entry ->
        selectedMain == entry.destination && entry.children.isNotEmpty()
    }
    val hasExpandedEntry = expandedEntry != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = SettingsPaneExpandMillis,
                    easing = FastOutSlowInEasing
                )
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        group.entries.forEachIndexed { index, entry ->
            val expanded = entry == expandedEntry
            val groupedWithSection = !hasExpandedEntry
            val topRadius = when {
                expanded -> SettingsPaneItemOuterRadius
                groupedWithSection && index == 0 -> SettingsPaneItemOuterRadius
                groupedWithSection -> SettingsPaneItemInnerRadius
                else -> SettingsPaneItemOuterRadius
            }
            val bottomRadius = when {
                expanded -> SettingsPaneItemInnerRadius
                groupedWithSection && index == group.entries.lastIndex -> SettingsPaneItemOuterRadius
                groupedWithSection -> SettingsPaneItemInnerRadius
                else -> SettingsPaneItemOuterRadius
            }
            val itemPadding by animateDpAsState(
                targetValue = if (hasExpandedEntry && !expanded) 8.dp else 0.dp,
                animationSpec = tween(
                    durationMillis = SettingsPaneShapeMillis,
                    easing = FastOutSlowInEasing
                ),
                label = "settings_pane_section_item_padding"
            )

            SettingsPaneEntryGroup(
                entry = entry,
                selected = selected,
                selectedMain = selectedMain,
                expanded = expanded,
                topRadius = topRadius,
                bottomRadius = bottomRadius,
                verticalPadding = itemPadding,
                onNavigate = onNavigate,
            )
        }
    }
}

@Composable
private fun SettingsPaneEntryGroup(
    entry: SettingsPaneEntry,
    selected: SettingsDestination,
    selectedMain: SettingsDestination,
    expanded: Boolean,
    topRadius: Dp,
    bottomRadius: Dp,
    verticalPadding: Dp,
    onNavigate: (SettingsPaneEntry) -> Unit,
) {
    val selectedInGroup = selected == entry.destination
    val groupPadding by animateDpAsState(
        targetValue = if (expanded) 4.dp else verticalPadding,
        animationSpec = tween(
            durationMillis = SettingsPaneShapeMillis,
            easing = FastOutSlowInEasing
        ),
        label = "settings_pane_entry_group_padding"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = groupPadding.coerceAtLeast(0.dp) / 2)
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = SettingsPaneExpandMillis,
                    easing = FastOutSlowInEasing
                )
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SettingsPaneItem(
            entry = entry,
            selected = selectedInGroup,
            expanded = expanded,
            showDescription = false,
            isChild = false,
            topRadius = topRadius,
            bottomRadius = bottomRadius,
            onClick = { onNavigate(entry) }
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = SettingsPaneFadeMillis,
                    easing = LinearOutSlowInEasing
                )
            ) + expandVertically(
                animationSpec = tween(
                    durationMillis = SettingsPaneExpandMillis,
                    easing = FastOutSlowInEasing
                )
            ),
            exit = fadeOut(
                animationSpec = tween(durationMillis = SettingsPaneFadeMillis)
            ) + shrinkVertically(
                animationSpec = tween(
                    durationMillis = SettingsPaneExpandMillis,
                    easing = FastOutSlowInEasing
                )
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                entry.children.forEachIndexed { index, child ->
                    SettingsPaneItem(
                        entry = child,
                        selected = selected == child.destination,
                        expanded = false,
                        showDescription = false,
                        isChild = true,
                        topRadius = SettingsPaneChildInnerRadius,
                        bottomRadius = if (index == entry.children.lastIndex) {
                            SettingsPaneChildOuterRadius
                        } else {
                            SettingsPaneChildInnerRadius
                        },
                        onClick = { onNavigate(child) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPaneItem(
    entry: SettingsPaneEntry,
    selected: Boolean,
    expanded: Boolean,
    showDescription: Boolean,
    isChild: Boolean,
    topRadius: Dp,
    bottomRadius: Dp,
    onClick: () -> Unit,
) {
    val haptics = rememberPremiumHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(
            durationMillis = SettingsPanePressMillis,
            easing = FastOutSlowInEasing
        ),
        label = "settings_pane_item_scale"
    )
    val animatedTopRadius by animateDpAsState(
        targetValue = topRadius,
        animationSpec = tween(
            durationMillis = SettingsPaneShapeMillis,
            easing = FastOutSlowInEasing
        ),
        label = "settings_pane_item_top_radius"
    )
    val animatedBottomRadius by animateDpAsState(
        targetValue = bottomRadius,
        animationSpec = tween(
            durationMillis = SettingsPaneShapeMillis,
            easing = FastOutSlowInEasing
        ),
        label = "settings_pane_item_bottom_radius"
    )
    val itemHeight by animateDpAsState(
        targetValue = when {
            showDescription -> 78.dp
            isChild -> 48.dp
            else -> 58.dp
        },
        animationSpec = tween(
            durationMillis = SettingsPaneShapeMillis,
            easing = FastOutSlowInEasing
        ),
        label = "settings_pane_item_height"
    )
    val targetContainerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    }
    val targetContentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(
            durationMillis = SettingsPaneShapeMillis,
            easing = FastOutSlowInEasing
        ),
        label = "settings_pane_item_container_color"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = tween(
            durationMillis = SettingsPaneShapeMillis,
            easing = FastOutSlowInEasing
        ),
        label = "settings_pane_item_content_color"
    )

    Surface(
        onClick = {
            if (!selected) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            } else if (entry.children.isNotEmpty()) {
                haptics.perform(HapticPattern.Pop)
                onClick()
            }
        },
        interactionSource = interactionSource,
        shape = RoundedCornerShape(
            topStart = animatedTopRadius,
            topEnd = animatedTopRadius,
            bottomStart = animatedBottomRadius,
            bottomEnd = animatedBottomRadius,
        ),
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier
                .height(itemHeight)
                .padding(horizontal = if (isChild) 16.dp else 14.dp),
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
                modifier = Modifier.size(if (isChild) 28.dp else 34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(entry.icon, null, modifier = Modifier.size(if (isChild) 16.dp else 19.dp))
                }
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(entry.titleRes),
                    style = if (isChild) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showDescription && entry.descriptionRes != null) {
                    Text(
                        text = stringResource(entry.descriptionRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (entry.children.isNotEmpty()) {
                if (expanded) {
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
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
