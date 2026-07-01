package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
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
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPage(
    vm: SettingVM = koinViewModel(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val mainSettingItemPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp)
    
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
                            Icon(Icons.Rounded.Build, stringResource(R.string.developer))
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

            // General Settings Section
            item {
                SettingsGroup(
                    title = stringResource(R.string.setting_page_general_settings)
                ) {
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_display_setting),
                        icon = { Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.SettingDisplay) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_assistant),
                        icon = { Icon(Icons.Rounded.Group, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.Assistant) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_prompt_injections),
                        icon = { Icon(Icons.Rounded.Category, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.SettingPromptInjections) }
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
                        icon = { Icon(Icons.Rounded.AccountTree, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.SettingModels) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_providers),
                        icon = { Icon(Icons.Rounded.Cloud, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.SettingProvider) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_mcp),
                        icon = { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.SettingMcp) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_web_server),
                        icon = { Icon(Icons.Rounded.Language, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.SettingWeb) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.setting_android_integration),
                        icon = { Icon(Icons.Rounded.PhoneAndroid, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.SettingAndroidIntegration) }
                    )

                    SettingGroupItem(
                        title = stringResource(R.string.extensions_page_workspace),
                        icon = { Icon(Icons.Rounded.Code, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.Workspaces) }
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
                        icon = { Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.Backup()) }
                    )
                    SettingGroupItem(
                        title = stringResource(R.string.setting_page_chat_storage),
                        icon = { Icon(Icons.Rounded.Storage, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.SettingChatStorage) }
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
                        icon = { Icon(Icons.Rounded.Info, null, modifier = Modifier.size(20.dp)) },
                        contentPadding = mainSettingItemPadding,
                        onClick = { navController.navigate(Screen.SettingAbout) }
                    )
                    
                    val context = LocalContext.current
                    SettingGroupItem(
                        title = stringResource(R.string.buy_me_a_coffee),
                        icon = { Icon(Icons.Rounded.Favorite, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) },
                        contentPadding = mainSettingItemPadding,
                        trailing = {
                            Icon(
                                Icons.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
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

