package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Translate
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_OCR_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TITLE_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRANSLATION_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_STT_PROMPT
import me.rerere.rikkahub.data.datastore.DISABLED_MODEL_ID
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.ui.components.ai.ReasoningPicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LightbulbCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.AutoAwesome

import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.ai.VoiceSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingModelPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    
    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_model_page_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                SettingsGroup(title = stringResource(R.string.setting_model_page_group_conversation)) {
                    DefaultChatModelSetting(settings = settings, vm = vm)
                    DefaultTtsVoiceSetting(settings = settings, vm = vm)
                    DefaultSTTModelSetting(settings = settings, vm = vm)
                    DefaultTitleModelSetting(settings = settings, vm = vm)
                    DefaultSummarizerModelSetting(settings = settings, vm = vm)
                    DefaultSubagentModelSetting(settings = settings, vm = vm)
                    DefaultSuggestionModelSetting(settings = settings, vm = vm)
                }
            }

            item {
                SettingsGroup(title = stringResource(R.string.setting_model_page_group_processing)) {
                    DefaultImageGenerationModelSetting(settings = settings, vm = vm)
                    DefaultOcrModelSetting(settings = settings, vm = vm)
                    DefaultEmbeddingModelSetting(settings = settings, vm = vm)
                }
            }
        }
    }
}

@Composable
private fun DefaultTtsVoiceSetting(
    settings: Settings,
    vm: SettingVM
) {
    ModelFeatureCard(
        title = { Text("Default Voice", maxLines = 1) },
        description = { Text("Voice used for manual and automatic TTS") },
        icon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                VoiceSelector(
                    voiceId = settings.selectedTTSVoiceId,
                    providers = settings.ttsProviders,
                    modifier = Modifier.wrapContentWidth(),
                    onSelect = { voice ->
                        val provider = settings.ttsProviders.firstOrNull { provider ->
                            provider.voices.any { it.id == voice.id }
                        }
                        vm.updateSettings(
                            settings.copy(
                                selectedTTSVoiceId = voice.id,
                                selectedTTSProviderId = provider?.id ?: settings.selectedTTSProviderId,
                            )
                        )
                    }
                )
            }
        }
    )
}

@Composable
private fun DefaultSTTModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    var promptPending by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(stringResource(R.string.setting_page_stt_service), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_stt_settings_title))
        },
        icon = {
            Icon(Icons.Rounded.Mic, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.sttModelId,
                    type = ModelType.STT,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                sttModelId = it.id
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
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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
                HelperReasoningSettings(
                    reasoningTokens = settings.sttThinkingBudget,
                    onUpdateReasoningTokens = {
                        vm.updateSettings(
                            settings.copy(
                                sttThinkingBudget = it
                            )
                        )
                    }
                )

                FormItem(
                    label = {
                        Text(stringResource(R.string.setting_model_page_prompt))
                    },
                    tail = { AutoSaveIndicator(visible = promptPending) }
                ) {
                    DebouncedTextField(
                        value = settings.sttPrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    sttPrompt = it
                                )
                            )
                        },
                        stateKey = "stt_prompt",
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                        onPendingChange = { promptPending = it },
                    )
                    TextButton(
                        onClick = {
                            vm.updateSettings(
                                settings.copy(
                                    sttPrompt = DEFAULT_STT_PROMPT
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.setting_model_page_reset_to_default))
                    }
                }
                
                FormItem(
                    label = { Text(stringResource(R.string.setting_stt_replace_model_icon)) },
                    description = { Text(stringResource(R.string.setting_stt_replace_model_icon_desc)) },
                    tail = {
                        androidx.compose.material3.Switch(
                            checked = settings.displaySetting.sttReplaceModelIcon,
                            onCheckedChange = { newValue ->
                                vm.updateSettings(
                                    settings.copy(
                                        displaySetting = settings.displaySetting.copy(
                                            sttReplaceModelIcon = newValue
                                        )
                                    )
                                )
                            }
                        )
                    }
                )
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
    var promptPending by remember { mutableStateOf(false) }
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
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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
                    },
                    tail = { AutoSaveIndicator(visible = promptPending) }
                ) {
                    DebouncedTextField(
                        value = settings.translatePrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    translatePrompt = it
                                )
                            )
                        },
                        stateKey = "translate_prompt",
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                        onPendingChange = { promptPending = it },
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
    var promptPending by remember { mutableStateOf(false) }
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
                    onClear = {
                        vm.updateSettings(
                            settings.copy(
                                suggestionModelId = DISABLED_MODEL_ID
                            )
                        )
                    },
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
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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
                    },
                    tail = { AutoSaveIndicator(visible = promptPending) }
                ) {
                    DebouncedTextField(
                        value = settings.suggestionPrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    suggestionPrompt = it
                                )
                            )
                        },
                        stateKey = "suggestion_prompt",
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8,
                        onPendingChange = { promptPending = it },
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
                HelperReasoningSettings(
                    reasoningTokens = settings.suggestionThinkingBudget,
                    onUpdateReasoningTokens = { tokens ->
                        vm.updateSettings(
                            settings.copy(
                                suggestionThinkingBudget = tokens
                            )
                        )
                    }
                )
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
    var promptPending by remember { mutableStateOf(false) }
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
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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
                    },
                    tail = { AutoSaveIndicator(visible = promptPending) }
                ) {
                    DebouncedTextField(
                        value = settings.titlePrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    titlePrompt = it
                                )
                            )
                        },
                        stateKey = "title_prompt",
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 8,
                        onPendingChange = { promptPending = it },
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
                HelperReasoningSettings(
                    reasoningTokens = settings.titleThinkingBudget,
                    onUpdateReasoningTokens = { tokens ->
                        vm.updateSettings(
                            settings.copy(
                                titleThinkingBudget = tokens
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DefaultSummarizerModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(stringResource(R.string.setting_model_page_summarizer_model), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_summarizer_model_desc))
        },
        icon = {
            Icon(Icons.Rounded.Psychology, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.summarizerModelId,
                    type = ModelType.CHAT,
                    onSelect = { selectedModel ->
                        vm.updateSettings(
                            settings.copy(
                                summarizerModelId = settings.findModelById(selectedModel.id)?.id
                            )
                        )
                    },
                    providers = settings.providers,
                    allowClear = true,
                    onClear = {
                        vm.updateSettings(
                            settings.copy(
                                summarizerModelId = null
                            )
                        )
                    },
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
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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
                HelperReasoningSettings(
                    reasoningTokens = settings.summarizerThinkingBudget,
                    onUpdateReasoningTokens = { tokens ->
                        vm.updateSettings(
                            settings.copy(
                                summarizerThinkingBudget = tokens
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DefaultSubagentModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    ModelFeatureCard(
        title = {
            Text(stringResource(R.string.setting_model_page_subagent_model), maxLines = 1)
        },
        description = {
            Text(stringResource(R.string.setting_model_page_subagent_model_desc))
        },
        icon = {
            Icon(Icons.Rounded.Psychology, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.subagentModelId,
                    type = ModelType.CHAT,
                    onSelect = { selectedModel ->
                        vm.updateSettings(
                            settings.copy(
                                subagentModelId = settings.findModelById(selectedModel.id)?.id
                            )
                        )
                    },
                    providers = settings.providers,
                    allowClear = true,
                    onClear = {
                        vm.updateSettings(
                            settings.copy(
                                subagentModelId = null
                            )
                        )
                    },
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
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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
                HelperReasoningSettings(
                    reasoningTokens = settings.subagentThinkingBudget,
                    onUpdateReasoningTokens = { tokens ->
                        vm.updateSettings(
                            settings.copy(
                                subagentThinkingBudget = tokens
                            )
                        )
                    }
                )
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
            Icon(Icons.AutoMirrored.Rounded.Chat, null)
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
private fun DefaultOcrModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    var showModal by remember { mutableStateOf(false) }
    var promptPending by remember { mutableStateOf(false) }
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
                    modelFilter = { model -> model.inputModalities.contains(Modality.IMAGE) },
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
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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
                    },
                    tail = { AutoSaveIndicator(visible = promptPending) }
                ) {
                    DebouncedTextField(
                        value = settings.ocrPrompt,
                        onValueChange = {
                            vm.updateSettings(
                                settings.copy(
                                    ocrPrompt = it
                                )
                            )
                        },
                        stateKey = "ocr_prompt",
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 10,
                        onPendingChange = { promptPending = it },
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
                HelperReasoningSettings(
                    reasoningTokens = settings.ocrThinkingBudget,
                    onUpdateReasoningTokens = { tokens ->
                        vm.updateSettings(
                            settings.copy(
                                ocrThinkingBudget = tokens
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun DefaultImageGenerationModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    ModelFeatureCard(
        title = {
            Text(
                stringResource(R.string.setting_model_page_image_generation_model),
                maxLines = 1
            )
        },
        description = {
            Text(stringResource(R.string.setting_model_page_image_generation_model_desc))
        },
        icon = {
            Icon(Icons.Rounded.AutoAwesome, null)
        },
        actions = {
            Box(modifier = Modifier.weight(1f)) {
                ModelSelector(
                    modelId = settings.imageGenerationModelId,
                    type = ModelType.IMAGE,
                    onSelect = {
                        vm.updateSettings(
                            settings.copy(
                                imageGenerationModelId = it.id
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
private fun DefaultEmbeddingModelSetting(
    settings: Settings,
    vm: SettingVM
) {
    val toaster = LocalToaster.current
    var isRegenerating by remember { mutableStateOf(false) }
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.wrapContentWidth(),
                ) {
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
                        onClear = {
                            vm.updateSettings(
                                settings.copy(
                                    embeddingModelId = DISABLED_MODEL_ID
                                )
                            )
                        },
                        modifier = Modifier.wrapContentWidth()
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = settings.embeddingModelId != DISABLED_MODEL_ID,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()
                    ) {
                        IconButton(
                            enabled = !isRegenerating,
                            onClick = {
                                isRegenerating = true
                                vm.regenerateMemoryEmbeddings(
                                    onComplete = { success, failure ->
                                        isRegenerating = false
                                        toaster.show(
                                            "Updated $success embeddings${if (failure > 0) ", $failure failed" else ""}",
                                            type = if (failure > 0) ToastType.Warning else ToastType.Success,
                                        )
                                    },
                                    onError = { error ->
                                        isRegenerating = false
                                        toaster.show(error.message ?: "Embedding refresh failed", type = ToastType.Error)
                                    },
                                )
                            },
                        ) {
                            if (isRegenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh embeddings")
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun HelperReasoningSettings(
    reasoningTokens: Int,
    onUpdateReasoningTokens: (Int) -> Unit,
) {
    var showReasoningPicker by remember { mutableStateOf(false) }

    if (showReasoningPicker) {
        ReasoningPicker(
            reasoningTokens = reasoningTokens,
            onDismissRequest = { showReasoningPicker = false },
            onUpdateReasoningTokens = onUpdateReasoningTokens,
        )
    }

    val currentLevel = ReasoningLevel.fromBudgetTokens(reasoningTokens)
    val amoledMode by me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()

    val title = when (currentLevel) {
        ReasoningLevel.OFF -> stringResource(R.string.reasoning_off)
        ReasoningLevel.AUTO -> stringResource(R.string.reasoning_auto)
        ReasoningLevel.LOW -> stringResource(R.string.reasoning_light)
        ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium)
        ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy)
    }

    val subtitle = when (currentLevel) {
        ReasoningLevel.OFF -> "Reasoning disabled"
        ReasoningLevel.AUTO -> "Model decides reasoning level"
        ReasoningLevel.LOW -> stringResource(R.string.reasoning_light_desc)
        ReasoningLevel.MEDIUM -> stringResource(R.string.reasoning_medium_desc)
        ReasoningLevel.HIGH -> stringResource(R.string.reasoning_heavy_desc)
    }

    val icon = when (currentLevel) {
        ReasoningLevel.OFF -> Icons.Rounded.LightbulbCircle
        ReasoningLevel.AUTO -> Icons.Rounded.AutoAwesome
        else -> Icons.Rounded.Lightbulb
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.assistant_page_thinking_budget),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    color = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .clickable {
                    haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                    showReasoningPicker = true
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
        shape = RoundedCornerShape(10.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
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
