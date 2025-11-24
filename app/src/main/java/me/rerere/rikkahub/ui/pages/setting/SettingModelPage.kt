package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Translate
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingModelPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.setting_model_page_title))
                },
                navigationIcon = {
                    BackButton()
                }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                DefaultChatModelSetting(settings = settings, vm = vm)
            }

            item {
                DefaultTitleModelSetting(settings = settings, vm = vm)
            }

            item {
                DefaultSuggestionModelSetting(settings = settings, vm = vm)
            }

            item {
                DefaultTranslationModelSetting(settings = settings, vm = vm)
            }

            item {
                LearningModePromptSetting(settings = settings, vm = vm)
            }

            item {
                DefaultOcrModelSetting(settings = settings, vm = vm)
            }

            item {
                DefaultEmbeddingModelSetting(settings = settings, vm = vm)
            }
        }
    }
}

@Composable
private fun DefaultTranslationModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(
                stringResource(R.string.setting_model_page_translate_model),
                maxLines = 1
            )
        },
        description = {
            Text(stringResource(R.string.setting_model_page_translate_model_desc))
        },
        icon = {
            Icon(Icons.Rounded.Translate, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.translateModeId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                translateModeId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            IconButton(
                onClick = {
                    showModal = true
                }
            ) {
                Icon(Icons.Rounded.Settings, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_translate_prompt_vars))
                    }
                ) {
                    OutlinedTextField(
                        value = settings.translatePrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    translatePrompt = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    translatePrompt = DEFAULT_TRANSLATION_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultSuggestionModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(
                text = stringResource(R.string.setting_model_page_suggestion_model),
                maxLines = 1
            )
        },
        description = {
            Text(stringResource(R.string.setting_model_page_suggestion_model_desc))
        },
        icon = {
            Icon(Icons.Rounded.TipsAndUpdates, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.suggestionModelId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                suggestionModelId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    allowClear = true,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            IconButton(
                onClick = {
                    showModal = true
                }
            ) {
                Icon(Icons.Rounded.Settings, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_suggestion_prompt_vars))
                    }
                ) {
                    OutlinedTextField(
                        value = settings.suggestionPrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    suggestionPrompt = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    suggestionPrompt = DEFAULT_SUGGESTION_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultTitleModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(stringResource(R.string.setting_model_page_title_model), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_title_model_desc))
        },
        icon = {
            Icon(Icons.Rounded.Title, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.titleModelId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                titleModelId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            IconButton(
                onClick = {
                    showModal = true
                }
            ) {
                Icon(Icons.Rounded.Settings, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_suggestion_prompt_vars))
                    }
                ) {
                    OutlinedTextField(
                        value = settings.titlePrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    titlePrompt = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    titlePrompt = DEFAULT_TITLE_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultChatModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    ModelFeatureCard(
        icon = {
            Icon(Icons.Rounded.Chat, null)
        },
        title = {
            Text(stringResource(R.string.setting_model_page_chat_model), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_chat_model_desc))
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.chatModelId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                chatModelId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
    )
}

@Composable
private fun LearningModePromptSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(stringResource(R.string.setting_model_page_learning_mode), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_learning_mode_desc))
        },
        icon = {
            Icon(Icons.Rounded.School, null)
        },
        actions = {
            IconButton(
                onClick = {
                    showModal = true
                }
            ) {
                Icon(Icons.Rounded.Settings, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                ) {
                    OutlinedTextField(
                        value = settings.learningModePrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    learningModePrompt = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    learningModePrompt = DEFAULT_LEARNING_MODE_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultOcrModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(
                stringResource(R.string.setting_model_page_ocr_model),
                maxLines = 1
            )
        },
        description = {
            Text(stringResource(R.string.setting_model_page_ocr_model_desc))
        },
        icon = {
            Icon(Icons.Rounded.DocumentScanner, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.ocrModelId,
                    type = ModelType.CHAT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                ocrModelId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    modifier = Modifier.wrapContentWidth()
                )
            }
            IconButton(
                onClick = {
                    showModal = true
                }
            ) {
                Icon(Icons.Rounded.Settings, null)
            }
        }
    )

    if (showModal) {
        ModalBottomSheet(
            onDismissRequest = {
                showModal = false
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                    description = {
                        Text(stringResource(R.string.setting_model_page_ocr_prompt_vars))
                    }
                ) {
                    OutlinedTextField(
                        value = settings.ocrPrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    ocrPrompt = it
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    ocrPrompt = DEFAULT_OCR_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultEmbeddingModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    ModelFeatureCard(
        title = {
            Text(
                stringResource(R.string.setting_model_page_embedding_model),
                maxLines = 1
            )
        },
        description = {
            Text(stringResource(R.string.setting_model_page_embedding_model_desc))
        },
        icon = {
            Icon(Icons.Rounded.Psychology, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.embeddingModelId,
                    type = ModelType.EMBEDDING,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                embeddingModelId = it.id
                            )
                        )
                    },
                    providers = settings.providers,
                    allowClear = true,
                    modifier = Modifier.wrapContentWidth()
                )
            }
        }
    )
}

@Composable
private fun ModelFeatureCard(
    modifier: Modifier = Modifier,
    description: @Composable () -> Unit = {},
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        icon()
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                            title()
                        }
                    }
                    ProvideTextStyle(
                        MaterialTheme.typography.bodySmall.copy(
                            color = LocalContentColor.current.copy(
                                alpha = 0.7f
                            )
                        )
                    ) {
                        description()
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                actions()
            }
        }
    }
}
