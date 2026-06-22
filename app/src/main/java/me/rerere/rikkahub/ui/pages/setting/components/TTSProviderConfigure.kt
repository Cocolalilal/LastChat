package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.OutlinedNumberInput
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.android.LocalTtsVoice
import me.rerere.tts.provider.android.discoverLocalTtsVoices
import me.rerere.rikkahub.ui.pages.setting.components.CustomIconSelector
import me.rerere.rikkahub.ui.components.ui.lobeHubIconUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import me.rerere.tts.provider.android.TTSManager
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height

@Composable
private fun visibilityToggleDescription(isVisible: Boolean): String {
    return stringResource(if (isVisible) R.string.a11y_hide else R.string.a11y_show)
}

@Composable
fun TTSProviderConfigure(
    setting: TTSProviderSetting,
    modifier: Modifier = Modifier,
    showVoiceFields: Boolean = true,
    scrollable: Boolean = true,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    val context = LocalContext.current
    val iconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    onValueChange(setting.copyProvider(customIconUri = it.toString()))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = if (scrollable) {
            modifier.verticalScroll(rememberScrollState())
        } else {
            modifier
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (setting !is TTSProviderSetting.SystemTTS) {
                CustomIconSelector(
                    customIconUri = setting.customIconUri,
                    onPickFile = {
                        iconPickerLauncher.launch(arrayOf("image/*", "image/svg+xml"))
                    },
                    onPickLobeHubIcon = { slug ->
                        onValueChange(setting.copyProvider(customIconUri = lobeHubIconUri(slug)))
                    },
                    onReset = {
                        onValueChange(setting.copyProvider(customIconUri = null))
                    },
                    modifier = Modifier.size(56.dp),
                    icon = { modifier ->
                        TTSProviderIcon(provider = setting, catalogSnapshot = null, modifier = modifier.size(48.dp))
                    }
                )
            }
            
            // Name
            FormItem(
                label = { Text(stringResource(R.string.setting_tts_page_name)) },
                description = { Text(stringResource(R.string.setting_tts_page_name_description)) },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = setting.name,
                    onValueChange = { newName ->
                        onValueChange(setting.copyProvider(name = newName))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.setting_tts_page_name_placeholder)) },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
            }
        }
        // Provider-specific fields
        when (setting) {
            is TTSProviderSetting.OpenAI -> OpenAITTSConfiguration(setting, showVoiceFields, onValueChange)
            is TTSProviderSetting.Gemini -> GeminiTTSConfiguration(setting, showVoiceFields, onValueChange)
            is TTSProviderSetting.MiniMax -> MiniMaxTTSConfiguration(setting, showVoiceFields, onValueChange)
            is TTSProviderSetting.ElevenLabs -> ElevenLabsTTSConfiguration(setting, showVoiceFields, onValueChange)
            is TTSProviderSetting.Qwen -> QwenTTSConfiguration(setting, showVoiceFields, onValueChange)
            is TTSProviderSetting.SystemTTS -> SystemTTSConfiguration(setting, showVoiceFields, onValueChange)
            is TTSProviderSetting.Cartesia,
            is TTSProviderSetting.FishAudio,
            is TTSProviderSetting.PlayHT -> {
                // Not implemented yet
            }
        }
    }
}


@Composable
private fun OpenAITTSConfiguration(
    setting: TTSProviderSetting.OpenAI,
    showVoiceFields: Boolean,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    var apiKeyVisible by remember { mutableStateOf(false) }
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) apiKeyVisible = false },
            placeholder = { Text(stringResource(R.string.setting_tts_page_api_key_placeholder_openai)) },
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(
                        imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = visibilityToggleDescription(apiKeyVisible)
                    )
                }
            },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    // Model
    var fetchingModels by remember { mutableStateOf(false) }
    var availableModels by remember { mutableStateOf<List<String>?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ttsManager = koinInject<TTSManager>()
    var modelExpanded by remember { mutableStateOf(false) }

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { if (availableModels != null) modelExpanded = !modelExpanded }
        ) {
            OutlinedTextField(
                value = setting.model,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(model = newModel))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                placeholder = { Text(stringResource(R.string.setting_tts_page_model_placeholder_openai)) },
                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                trailingIcon = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (fetchingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        fetchingModels = true
                                        val models = runCatching { ttsManager.listModels(setting) }.getOrNull()
                                        if (models != null) {
                                            availableModels = models.map { it.id }
                                            modelExpanded = true
                                        }
                                        fetchingModels = false
                                    }
                                }
                            ) {
                                Text("Fetch")
                            }
                        }
                    }
                }
            )
            
            if (availableModels != null) {
                ExposedDropdownMenu(
                    expanded = modelExpanded,
                    onDismissRequest = { modelExpanded = false }
                ) {
                    availableModels?.forEach { modelId ->
                        DropdownMenuItem(
                            text = { Text(modelId) },
                            onClick = {
                                modelExpanded = false
                                onValueChange(setting.copy(model = modelId))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showVoiceFields) {
        // Voice
        var voiceExpanded by remember { mutableStateOf(false) }
        val voices = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")

        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_voice)) },
            description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
        ) {
            ExposedDropdownMenuBox(
                expanded = voiceExpanded,
                onExpandedChange = { voiceExpanded = !voiceExpanded }
            ) {
                OutlinedTextField(
                    value = setting.voice,
                    onValueChange = { newVoice ->
                        onValueChange(setting.copy(voice = newVoice))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                    },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
                ExposedDropdownMenu(
                    expanded = voiceExpanded,
                    onDismissRequest = { voiceExpanded = false }
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice) },
                            onClick = {
                                voiceExpanded = false
                                onValueChange(setting.copy(voice = voice))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMaxTTSConfiguration(
    setting: TTSProviderSetting.MiniMax,
    showVoiceFields: Boolean,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    var apiKeyVisible by remember { mutableStateOf(false) }
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) apiKeyVisible = false },
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(
                        imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = visibilityToggleDescription(apiKeyVisible)
                    )
                }
            },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("speech-2.5-hd-preview") },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    if (showVoiceFields) {
        // Voice ID
        var voiceIdExpanded by remember { mutableStateOf(false) }
        val voiceIds = listOf(
            "male-qn-qingse",
            "male-qn-jingying",
            "male-qn-badao",
            "male-qn-daxuesheng",
            "female-shaonv",
            "female-yujie",
            "female-chengshu",
            "female-tianmei",
            "audiobook_male_1",
            "audiobook_female_1",
            "cartoon_pig"
        )

        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_voice_id)) },
            description = { Text(stringResource(R.string.setting_tts_page_voice_id_description)) }
        ) {
            ExposedDropdownMenuBox(
                expanded = voiceIdExpanded,
                onExpandedChange = { voiceIdExpanded = !voiceIdExpanded }
            ) {
                OutlinedTextField(
                    value = setting.voiceId,
                    onValueChange = { newVoiceId ->
                        onValueChange(setting.copy(voiceId = newVoiceId))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceIdExpanded)
                    },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
                ExposedDropdownMenu(
                    expanded = voiceIdExpanded,
                    onDismissRequest = { voiceIdExpanded = false }
                ) {
                    voiceIds.forEach { voiceId ->
                        DropdownMenuItem(
                            text = { Text(voiceId) },
                            onClick = {
                                voiceIdExpanded = false
                                onValueChange(setting.copy(voiceId = voiceId))
                            }
                        )
                    }
                }
            }
        }

        // Emotion
        var emotionExpanded by remember { mutableStateOf(false) }
        val emotions = listOf("calm", "happy", "sad", "angry", "fearful", "disgusted", "surprised")

        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_emotion)) },
            description = { Text(stringResource(R.string.setting_tts_page_emotion_description)) }
        ) {
            ExposedDropdownMenuBox(
                expanded = emotionExpanded,
                onExpandedChange = { emotionExpanded = !emotionExpanded }
            ) {
                OutlinedTextField(
                    value = setting.emotion,
                    onValueChange = { newEmotion ->
                        onValueChange(setting.copy(emotion = newEmotion))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = emotionExpanded)
                    },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
                ExposedDropdownMenu(
                    expanded = emotionExpanded,
                    onDismissRequest = { emotionExpanded = false }
                ) {
                    emotions.forEach { emotion ->
                        DropdownMenuItem(
                            text = { Text(emotion) },
                            onClick = {
                                emotionExpanded = false
                                onValueChange(setting.copy(emotion = emotion))
                            }
                        )
                    }
                }
            }
        }

        // Speed
        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_speed)) },
            description = { Text(stringResource(R.string.setting_tts_page_speed_description)) }
        ) {
            OutlinedNumberInput(
                value = setting.speed,
                onValueChange = { newSpeed ->
                    if (newSpeed in 0.25f..4.0f) {
                        onValueChange(setting.copy(speed = newSpeed))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.setting_tts_page_speed)
            )
        }
    }
}

@Composable
private fun GeminiTTSConfiguration(
    setting: TTSProviderSetting.Gemini,
    showVoiceFields: Boolean,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    var apiKeyVisible by remember { mutableStateOf(false) }
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) apiKeyVisible = false },
            placeholder = { Text(stringResource(R.string.setting_tts_page_api_key_placeholder_gemini)) },
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(
                        imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = visibilityToggleDescription(apiKeyVisible)
                    )
                }
            },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    // Base URL
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_base_url_placeholder)) },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    // Model
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.setting_tts_page_model_placeholder_gemini)) },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    if (showVoiceFields) {
        // Voice Name
        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_voice_name)) },
            description = { Text(stringResource(R.string.setting_tts_page_voice_name_description)) }
        ) {
            OutlinedTextField(
                value = setting.voiceName,
                onValueChange = { newVoiceName ->
                    onValueChange(setting.copy(voiceName = newVoiceName))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.setting_tts_page_voice_name_placeholder)) },
                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
            )
        }
    }
}

private fun LocalTtsVoice.displayLabel(): String {
    return if (localeTag.isBlank()) {
        name
    } else {
        "$name - $localeTag"
    }
}

@Composable
private fun SystemTTSConfiguration(
    setting: TTSProviderSetting.SystemTTS,
    showVoiceFields: Boolean,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    if (!showVoiceFields) {
        return
    }

    val context = LocalContext.current
    val localVoices by produceState(
        initialValue = emptyList<LocalTtsVoice>(),
        setting.enginePackageName,
        context,
    ) {
        value = discoverLocalTtsVoices(context, setting.enginePackageName)
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_engine)) },
        description = { Text(stringResource(R.string.setting_tts_page_engine_description)) }
    ) {
        OutlinedTextField(
            value = setting.enginePackageName.orEmpty(),
            onValueChange = { onValueChange(setting.copy(enginePackageName = it.takeIf(String::isNotBlank))) },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    if (localVoices.isNotEmpty()) {
        var voiceExpanded by remember { mutableStateOf(false) }
        val selectedVoice = localVoices.firstOrNull { it.name == setting.voiceName }
        val selectedVoiceLabel = selectedVoice?.displayLabel()
            ?: setting.voiceName?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.setting_tts_page_default_voice)

        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_voice)) },
            description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
        ) {
            ExposedDropdownMenuBox(
                expanded = voiceExpanded,
                onExpandedChange = { voiceExpanded = !voiceExpanded }
            ) {
                OutlinedTextField(
                    value = selectedVoiceLabel,
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                    },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
                ExposedDropdownMenu(
                    expanded = voiceExpanded,
                    onDismissRequest = { voiceExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.setting_tts_page_default_voice)) },
                        onClick = {
                            voiceExpanded = false
                            onValueChange(setting.copy(voiceName = null))
                        }
                    )
                    localVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.displayLabel()) },
                            onClick = {
                                voiceExpanded = false
                                onValueChange(setting.copy(voiceName = voice.name))
                            }
                        )
                    }
                }
            }
        }
    }

    // Speech Rate
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_speech_rate)) },
        description = { Text(stringResource(R.string.setting_tts_page_speech_rate_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.speechRate,
            onValueChange = { newRate ->
                if (newRate in 0.1f..3.0f) {
                    onValueChange(setting.copy(speechRate = newRate))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_speech_rate)
        )
    }

    // Pitch
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_pitch)) },
        description = { Text(stringResource(R.string.setting_tts_page_pitch_description)) }
    ) {
        OutlinedNumberInput(
            value = setting.pitch,
            onValueChange = { newPitch ->
                if (newPitch in 0.1f..2.0f) {
                    onValueChange(setting.copy(pitch = newPitch))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.setting_tts_page_pitch)
        )
    }
}

@Composable
private fun QwenTTSConfiguration(
    setting: TTSProviderSetting.Qwen,
    showVoiceFields: Boolean,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    var apiKeyVisible by remember { mutableStateOf(false) }
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) apiKeyVisible = false },
            placeholder = { Text("sk-xxx") },
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(
                        imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = visibilityToggleDescription(apiKeyVisible)
                    )
                }
            },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_base_url)) },
        description = { Text(stringResource(R.string.setting_tts_page_base_url_description)) }
    ) {
        OutlinedTextField(
            value = setting.baseUrl,
            onValueChange = { newBaseUrl ->
                onValueChange(setting.copy(baseUrl = newBaseUrl))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://dashscope.aliyuncs.com/api/v1") },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description)) }
    ) {
        OutlinedTextField(
            value = setting.model,
            onValueChange = { newModel ->
                onValueChange(setting.copy(model = newModel))
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("qwen3-tts-flash") },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    if (showVoiceFields) {
        var voiceExpanded by remember { mutableStateOf(false) }
        val voices = listOf(
            "Cherry", "Serene", "Ethan", "Chelsie",
            "Momo", "Vivian", "Moon", "Maia", "Kai",
            "Nofish", "Bella", "Jennifer", "Ryan",
            "Katerina", "Aiden", "Eldric Sage", "Mia",
            "Mochi", "Bellona", "Vincent", "Bunny",
            "Neil", "Elias", "Arthur", "Nini"
        )

        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_voice)) },
            description = { Text(stringResource(R.string.setting_tts_page_voice_description)) }
        ) {
            ExposedDropdownMenuBox(
                expanded = voiceExpanded,
                onExpandedChange = { voiceExpanded = !voiceExpanded }
            ) {
                OutlinedTextField(
                    value = setting.voice,
                    onValueChange = { newVoice ->
                        onValueChange(setting.copy(voice = newVoice))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                    },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
                ExposedDropdownMenu(
                    expanded = voiceExpanded,
                    onDismissRequest = { voiceExpanded = false }
                ) {
                    voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice) },
                            onClick = {
                                voiceExpanded = false
                                onValueChange(setting.copy(voice = voice))
                            }
                        )
                    }
                }
            }
        }

        var languageExpanded by remember { mutableStateOf(false) }
        val languageTypes = listOf("Auto", "Chinese", "English", "Japanese", "Korean")

        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_language_type)) },
            description = { Text(stringResource(R.string.setting_tts_page_language_type_description)) }
        ) {
            ExposedDropdownMenuBox(
                expanded = languageExpanded,
                onExpandedChange = { languageExpanded = !languageExpanded }
            ) {
                OutlinedTextField(
                    value = setting.languageType,
                    onValueChange = { newLanguageType ->
                        onValueChange(setting.copy(languageType = newLanguageType))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded)
                    },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
                ExposedDropdownMenu(
                    expanded = languageExpanded,
                    onDismissRequest = { languageExpanded = false }
                ) {
                    languageTypes.forEach { languageType ->
                        DropdownMenuItem(
                            text = { Text(languageType) },
                            onClick = {
                                languageExpanded = false
                                onValueChange(setting.copy(languageType = languageType))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ElevenLabsTTSConfiguration(
    setting: TTSProviderSetting.ElevenLabs,
    showVoiceFields: Boolean,
    onValueChange: (TTSProviderSetting) -> Unit
) {
    // API Key
    var apiKeyVisible by remember { mutableStateOf(false) }
    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_api_key)) },
        description = { Text(stringResource(R.string.setting_tts_page_api_key_description_elevenlabs)) }
    ) {
        OutlinedTextField(
            value = setting.apiKey,
            onValueChange = { newApiKey ->
                onValueChange(setting.copy(apiKey = newApiKey))
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) apiKeyVisible = false },
            placeholder = { Text("xi-...") },
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(
                        imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = visibilityToggleDescription(apiKeyVisible)
                    )
                }
            },
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }

    if (showVoiceFields) {
        // Voice ID
        FormItem(
            label = { Text(stringResource(R.string.setting_tts_page_voice_id)) },
            description = { Text(stringResource(R.string.setting_tts_page_voice_id_description_elevenlabs)) }
        ) {
            OutlinedTextField(
                value = setting.voiceId,
                onValueChange = { newVoiceId ->
                    onValueChange(setting.copy(voiceId = newVoiceId))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("21m00Tcm4TlvDq8ikWAM") },
                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
            )
        }
    }

    // Model ID
    var modelExpanded by remember { mutableStateOf(false) }
    val models = listOf(
        "eleven_multilingual_v2",
        "eleven_flash_v2_5",
        "eleven_turbo_v2_5",
        "eleven_monolingual_v1"
    )

    FormItem(
        label = { Text(stringResource(R.string.setting_tts_page_model)) },
        description = { Text(stringResource(R.string.setting_tts_page_model_description_elevenlabs)) }
    ) {
        ExposedDropdownMenuBox(
            expanded = modelExpanded,
            onExpandedChange = { modelExpanded = !modelExpanded }
        ) {
            OutlinedTextField(
                value = setting.modelId,
                onValueChange = { newModel ->
                    onValueChange(setting.copy(modelId = newModel))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded)
                },
                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
            )
            ExposedDropdownMenu(
                expanded = modelExpanded,
                onDismissRequest = { modelExpanded = false }
            ) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            modelExpanded = false
                            onValueChange(setting.copy(modelId = model))
                        }
                    )
                }
            }
        }
    }
}

