package me.rerere.rikkahub.ui.pages.setting

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.Slider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.CloudUpload
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.isNotConfigured
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.pages.setting.components.PresetThemeButtonGroup
import me.rerere.rikkahub.ui.theme.ColorMode
import me.rerere.rikkahub.utils.countChatFiles
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingPage(vm: SettingVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navController = LocalNavController.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(text = stringResource(R.string.settings))
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
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
            contentPadding = innerPadding + PaddingValues(8.dp),
        ) {
            if (settings.isNotConfigured()) {
                item {
                    ProviderConfigWarningCard(navController)
                }
            }

            stickyHeader {
                Text(
                    text = stringResource(R.string.setting_page_general_settings),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item("colorMode") {
                var colorMode by rememberColorMode()
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.setting_page_color_mode))
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.InvertColors, null)
                    },
                    trailingContent = {
                        Select(
                            options = ColorMode.entries,
                            selectedOption = colorMode,
                            onOptionSelected = {
                                colorMode = it
                                navController.navigate(Screen.Setting) {
                                    launchSingleTop = true
                                    popUpTo(Screen.Setting) {
                                        inclusive = true
                                    }
                                }
                            },
                            optionToString = {
                                when (it) {
                                    ColorMode.SYSTEM -> stringResource(R.string.setting_page_color_mode_system)
                                    ColorMode.LIGHT -> stringResource(R.string.setting_page_color_mode_light)
                                    ColorMode.DARK -> stringResource(R.string.setting_page_color_mode_dark)
                                }
                            },
                            modifier = Modifier.width(150.dp)
                        )
                    }
                )
            }


            item {
                SettingItem(
                    navController = navController,
                    title = { Text(stringResource(R.string.setting_page_display_setting)) },
                    description = { Text(stringResource(R.string.setting_page_display_setting_desc)) },
                    icon = { Icon(Icons.Rounded.DesktopWindows, "Display Setting") },
                    link = Screen.SettingDisplay
                )
            }

            item {
                SettingItem(
                    navController = navController,
                    title = { Text(stringResource(R.string.setting_page_assistant)) },
                    description = { Text(stringResource(R.string.setting_page_assistant_desc)) },
                    icon = { Icon(Icons.Rounded.Group, "Assistant") },
                    link = Screen.Assistant
                )
            }

            stickyHeader {
                Text(
                    text = stringResource(R.string.setting_page_model_and_services),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingItem(
                    navController = navController,
                    title = { Text(stringResource(R.string.setting_page_default_model)) },
                    description = { Text(stringResource(R.string.setting_page_default_model_desc)) },
                    icon = { Icon(Icons.Rounded.AccountTree, stringResource(R.string.setting_page_default_model)) },
                    link = Screen.SettingModels
                )
            }

            item {
                SettingItem(
                    navController = navController,
                    title = { Text(stringResource(R.string.setting_page_providers)) },
                    description = { Text(stringResource(R.string.setting_page_providers_desc)) },
                    icon = { Icon(Icons.Rounded.Cloud, "Models") },
                    link = Screen.SettingProvider
                )
            }

            item {
                SettingItem(
                    navController = navController,
                    title = { Text(stringResource(R.string.setting_page_search_service)) },
                    description = { Text(stringResource(R.string.setting_page_search_service_desc)) },
                    icon = { Icon(Icons.Rounded.Public, "Search") },
                    link = Screen.SettingSearch
                )
            }

            item {
                SettingItem(
                    navController = navController,
                    title = { Text(stringResource(R.string.setting_page_tts_service)) },
                    description = { Text(stringResource(R.string.setting_page_tts_service_desc)) },
                    icon = { Icon(Icons.Rounded.RecordVoiceOver, "TTS") },
                    link = Screen.SettingTTS
                )
            }

            item {
                SettingItem(
                    navController = navController,
                    title = { Text(stringResource(R.string.setting_page_mcp)) },
                    description = { Text(stringResource(R.string.setting_page_mcp_desc)) },
                    icon = { Icon(Icons.Rounded.Code, "MCP") },
                    link = Screen.SettingMcp
                )
            }



            stickyHeader {
                Text(
                    text = stringResource(R.string.setting_page_data_settings),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingItem(
                    navController = navController,
                    title = { Text(stringResource(R.string.setting_page_data_backup)) },
                    description = { Text(stringResource(R.string.setting_page_data_backup_desc)) },
                    icon = { Icon(Icons.Rounded.CloudUpload, "Backup") },
                    link = Screen.Backup
                )
            }

            item {
                val context = LocalContext.current
                val storageState by produceState(-1 to 0L) {
                    value = context.countChatFiles()
                }
                SettingItem(
                    navController = navController,
                    title = { Text(stringResource(R.string.setting_page_chat_storage)) },
                    description = {
                        if (storageState.first == -1) {
                            Text(stringResource(R.string.calculating))
                        } else {
                            Text(
                                stringResource(
                                    R.string.setting_page_chat_storage_desc,
                                    storageState.first,
                                    storageState.second / 1024 / 1024.0
                                )
                            )
                        }
                    },
                    icon = {
                        Icon(Icons.Rounded.Storage, "Storage")
                    },
                )
            }

            stickyHeader {
                Text(
                    text = stringResource(R.string.setting_page_about),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                SettingItem(
                    navController = navController,
                    title = { Text(stringResource(R.string.setting_page_about)) },
                    description = { Text(stringResource(R.string.setting_page_about_desc)) },
                    icon = { Icon(Icons.Rounded.Info, "About") },
                    link = Screen.SettingAbout
                )
            }






        }
    }
}

@Composable
private fun ProviderConfigWarningCard(navController: NavHostController) {
    Card(
        modifier = Modifier.padding(8.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.setting_page_config_api_title))
                },
                supportingContent = {
                    Text(stringResource(R.string.setting_page_config_api_desc))
                },
                leadingContent = {
                    Icon(Icons.Rounded.Warning, null)
                },
                colors = ListItemDefaults.colors(
                    containerColor = Color.Transparent
                )
            )

            TextButton(
                onClick = {
                    navController.navigate(Screen.SettingProvider)
                }
            ) {
                Text(stringResource(R.string.setting_page_config))
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
    Surface(
        onClick = {
            if (link != null) navController.navigate(link)
            onClick()
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
