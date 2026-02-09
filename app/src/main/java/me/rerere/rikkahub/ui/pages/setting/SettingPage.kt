package me.rerere.rikkahub.ui.pages.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.isNotConfigured
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.utils.countChatFiles
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    
    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.settings),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    if(settings.developerMode) {
                        IconButton(
                            onClick = {
                                navController.navigate(Screen.Developer)
                            }
                        ) {
                            Icon(Icons.Rounded.Build, "Developer")
                        }
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (settings.isNotConfigured()) {
                item {
                    ProviderConfigWarningCard(navController)
                }
            }

            // Update Available Banner
            item {
                UpdateAvailableBanner(
                    checkForUpdates = settings.displaySetting.checkForUpdates,
                    navController = navController
                )
            }

            // General Settings Section
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_general_settings)
                ) {
                    var colorMode by rememberColorMode()
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_color_mode),
                        icon = { Icon(Icons.Rounded.InvertColors, null, modifier = Modifier.size(20.dp)) },
                        trailing = {
                            Select(
                                options = ColorMode.entries,
                                selectedOption = colorMode,
                                onOptionSelected = {
                                    colorMode = it
                                    navController.navigate(Screen.Setting) {
                                        launchSingleTop = true
                                        popUpTo(Screen.Setting) { inclusive = true }
                                    }
                                },
                                optionToString = {
                                    when (it) {
                                        ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                        ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                        ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                                    }
                                },
                                modifier = Modifier.wrapContentWidth()
                            )
                        }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_display_setting),
                        subtitle = stringResource(R.string.setting_page_display_setting_desc),
                        icon = { Icon(Icons.Rounded.DesktopWindows, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingDisplay) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_assistant),
                        subtitle = stringResource(R.string.setting_page_assistant_desc),
                        icon = { Icon(Icons.Rounded.Group, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.Assistant) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_prompt_injections),
                        subtitle = stringResource(R.string.setting_page_prompt_injections_desc),
                        icon = { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingModes()) }
                    )
                }
            }

            // Models & Services Section
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_model_and_services)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_default_model),
                        subtitle = stringResource(R.string.setting_page_default_model_desc),
                        icon = { Icon(Icons.Rounded.AccountTree, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingModels) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_providers),
                        subtitle = stringResource(R.string.setting_page_providers_desc),
                        icon = { Icon(Icons.Rounded.Cloud, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingProvider) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_search_service),
                        subtitle = stringResource(R.string.setting_page_search_service_desc),
                        icon = { Icon(Icons.Rounded.Public, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingSearch) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_tts_service),
                        subtitle = stringResource(R.string.setting_page_tts_service_desc),
                        icon = { Icon(Icons.Rounded.RecordVoiceOver, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingTTS) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_mcp),
                        subtitle = stringResource(R.string.setting_page_mcp_desc),
                        icon = { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingMcp) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_android_integration),
                        subtitle = stringResource(R.string.setting_android_integration_desc),
                        icon = { Icon(Icons.Rounded.PhoneAndroid, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingAndroidIntegration) }
                    )
                }
            }

            // Data Section
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_data_settings)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_data_backup),
                        subtitle = stringResource(R.string.setting_page_data_backup_desc),
                        icon = { Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.Backup) }
                    )
                    val context = LocalContext.current
                    val storageState by produceState(-1 to 0L) {
                        value = context.countChatFiles()
                    }
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_chat_storage),
                        subtitle = if (storageState.first == -1) {
                            stringResource(R.string.calculating)
                        } else {
                            stringResource(
                                R.string.setting_page_chat_storage_desc,
                                storageState.first,
                                storageState.second / 1024 / 1024.0
                            )
                        },
                        icon = { Icon(Icons.Rounded.Storage, null, modifier = Modifier.size(20.dp)) }
                    )
                }
            }

            // About Section
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_about)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_about),
                        subtitle = stringResource(R.string.setting_page_about_desc),
                        icon = { Icon(Icons.Rounded.Info, null, modifier = Modifier.size(20.dp)) },
                        onClick = { navController.navigate(Screen.SettingAbout) }
                    )
                    
                    val context = LocalContext.current
                    SettingGroupItem(
                        title = "Buy Me a Coffee",
                        subtitle = "Support the development",
                        icon = { Icon(Icons.Rounded.Favorite, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) },
                        onClick = { 
                            context.openUrl("https://buymeacoffee.com/cocolalilal")
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun ProviderConfigWarningCard(navController: NavHostController) {
    Card(
        onClick = { navController.navigate(Screen.SettingProvider) },
        shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.setting_page_config_api_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.setting_page_config_api_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun SettingItem(
    navController: NavHostController,
    title: @Composable () -> Unit,
    description: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    link: Screen? = null,
    onClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = rememberPremiumHaptics()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "setting_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.8f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "setting_alpha"
    )
    
    Surface(
        onClick = {
            haptics.perform(HapticPattern.Tick)
            if (link != null) navController.navigate(link)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    ) {
        ListItem(
            headlineContent = {
                title()
            },
            supportingContent = {
                description()
            },
            leadingContent = {
                icon()
            }
        )
    }
}

@Composable
private fun UpdateAvailableBanner(
    checkForUpdates: Boolean,
    navController: NavHostController
) {
    if (!checkForUpdates) return
    
    val updateChecker = org.koin.compose.koinInject<me.rerere.rikkahub.utils.UpdateChecker>()
    // Remember the flow to prevent creating a new one on each recomposition
    val updateFlow = remember(updateChecker) { updateChecker.checkUpdate() }
    val updateState by updateFlow.collectAsStateWithLifecycle(initialValue = me.rerere.rikkahub.utils.UiState.Loading)
    var showUpdateDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    when (val state = updateState) {
        is me.rerere.rikkahub.utils.UiState.Success -> {
            val updateInfo = state.data
            val currentVersion = me.rerere.rikkahub.BuildConfig.VERSION_NAME
            val isNewer = me.rerere.rikkahub.utils.Version(updateInfo.version) > me.rerere.rikkahub.utils.Version(currentVersion)
            
            if (isNewer && updateInfo.downloads.isNotEmpty()) {
                Card(
                    onClick = { showUpdateDialog = true },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Update Available",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Version ${updateInfo.version} is available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                if (showUpdateDialog) {
                    AlertDialog(
                        onDismissRequest = { showUpdateDialog = false },
                        title = { Text("Update to ${updateInfo.version}") },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Changelog:",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = updateInfo.changelog.ifEmpty { "No changelog available" },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    updateInfo.downloads.firstOrNull()?.let { download ->
                                        updateChecker.downloadUpdate(context, download)
                                    }
                                    showUpdateDialog = false
                                }
                            ) {
                                Text("Download")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUpdateDialog = false }) {
                                Text("Later")
                            }
                        }
                    )
                }
            }
        }
        else -> { /* Loading or Error - don't show anything */ }
    }
}
