package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalToaster
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.size
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.ClickableIconPicker
import me.rerere.rikkahub.ui.components.ui.ProviderIcon
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.reflect.KClass

@Composable
fun ProviderConfigure(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    showSavingIndicator: Boolean = false,
    onEdit: (provider: ProviderSetting) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // 1. Enable/Disable Toggle with text
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (provider.enabled) {
                    stringResource(id = R.string.setting_provider_page_enabled)
                } else {
                    stringResource(id = R.string.setting_provider_page_disabled)
                },
                modifier = Modifier.weight(1f)
            )
            if (showSavingIndicator) {
                Text(
                    text = "Saving...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
            }
            HapticSwitch(
                checked = provider.enabled,
                onCheckedChange = { enabled ->
                    val updated = when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(enabled = enabled)
                        is ProviderSetting.Google -> provider.copy(enabled = enabled)
                        is ProviderSetting.Claude -> provider.copy(enabled = enabled)
                    }
                    onEdit(updated)
                }
            )
        }

        // 2. Type selector (for non-built-in providers)
        if (!provider.builtIn) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                ProviderSetting.Types.forEachIndexed { index, type ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ProviderSetting.Types.size
                        ),
                        label = {
                            Text(type.simpleName ?: "")
                        },
                        selected = provider::class == type,
                        onClick = {
                            onEdit(provider.convertTo(type))
                        }
                    )
                }
            }
        }

        // 3. Name field with icon picker
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ClickableIconPicker(
                currentIconUri = provider.customIconUri,
                defaultContent = {
                    ProviderIcon(
                        provider = provider,
                        modifier = Modifier.size(40.dp)
                    )
                },
                onIconSelected = { uri ->
                    val updated = when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(customIconUri = uri.toString())
                        is ProviderSetting.Google -> provider.copy(customIconUri = uri.toString())
                        is ProviderSetting.Claude -> provider.copy(customIconUri = uri.toString())
                    }
                    onEdit(updated)
                },
                onIconCleared = {
                    val updated = when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(customIconUri = null)
                        is ProviderSetting.Google -> provider.copy(customIconUri = null)
                        is ProviderSetting.Claude -> provider.copy(customIconUri = null)
                    }
                    onEdit(updated)
                },
                iconSize = 48.dp
            )
            OutlinedTextField(
                value = provider.name,
                onValueChange = { newName ->
                    val updated = when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(name = newName)
                        is ProviderSetting.Google -> provider.copy(name = newName)
                        is ProviderSetting.Claude -> provider.copy(name = newName)
                    }
                    onEdit(updated)
                },
                label = {
                    Text(stringResource(id = R.string.setting_provider_page_name))
                },
                modifier = Modifier.weight(1f),
            )
        }

        // 4. Provider-specific configuration
        when (provider) {
            is ProviderSetting.OpenAI -> {
                ProviderConfigureOpenAI(provider, onEdit)
            }

            is ProviderSetting.Google -> {
                ProviderConfigureGoogle(provider, onEdit)
            }

            is ProviderSetting.Claude -> {
                ProviderConfigureClaude(provider, onEdit)
            }
        }
    }
}

/**
 * Convert a provider to a different type while preserving all common properties.
 */
fun ProviderSetting.convertTo(type: KClass<out ProviderSetting>): ProviderSetting {
    // If same type, return unchanged
    if (this::class == type) return this

    // Extract API key from current provider
    val apiKey = when (this) {
        is ProviderSetting.OpenAI -> this.apiKey
        is ProviderSetting.Google -> this.apiKey
        is ProviderSetting.Claude -> this.apiKey
    }

    // Convert to target type while preserving common properties
    return when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI(
            id = this.id,
            enabled = this.enabled,
            name = this.name,
            models = this.models,
            proxy = this.proxy,
            balanceOption = this.balanceOption,
            tags = this.tags,
            customIconUri = this.customIconUri,
            apiKey = apiKey,
            baseUrl = if (this is ProviderSetting.OpenAI) this.baseUrl else "https://api.openai.com/v1"
        )
        ProviderSetting.Google::class -> ProviderSetting.Google(
            id = this.id,
            enabled = this.enabled,
            name = this.name,
            models = this.models,
            proxy = this.proxy,
            balanceOption = this.balanceOption,
            tags = this.tags,
            customIconUri = this.customIconUri,
            apiKey = apiKey,
            baseUrl = if (this is ProviderSetting.Google) this.baseUrl else "https://generativelanguage.googleapis.com/v1beta"
        )
        ProviderSetting.Claude::class -> ProviderSetting.Claude(
            id = this.id,
            enabled = this.enabled,
            name = this.name,
            models = this.models,
            proxy = this.proxy,
            balanceOption = this.balanceOption,
            tags = this.tags,
            customIconUri = this.customIconUri,
            apiKey = apiKey,
            baseUrl = if (this is ProviderSetting.Claude) this.baseUrl else "https://api.anthropic.com/v1"
        )
        else -> this // Return unchanged if unknown type
    }
}

@Composable
private fun ColumnScope.ProviderConfigureOpenAI(
    provider: ProviderSetting.OpenAI,
    onEdit: (provider: ProviderSetting.OpenAI) -> Unit
) {
    val latestProvider by rememberUpdatedState(provider)
    val toaster = LocalToaster.current

    provider.description()

    var apiKeyVisible by remember { mutableStateOf(false) }
    var localApiKey by remember(provider.id) { mutableStateOf(provider.apiKey) }
    LaunchedEffect(provider.apiKey) {
        if (provider.apiKey != localApiKey) {
            localApiKey = provider.apiKey
        }
    }
    LaunchedEffect(localApiKey) {
        delay(300)
        val latest = latestProvider
        if (localApiKey != latest.apiKey) {
            onEdit(latest.copy(apiKey = localApiKey.trim()))
        }
    }
    OutlinedTextField(
        value = localApiKey,
        onValueChange = { localApiKey = it },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_key))
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) apiKeyVisible = false },
        maxLines = if (apiKeyVisible) 3 else 1,
        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                Icon(
                    imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (apiKeyVisible) "Hide" else "Show"
                )
            }
        }
    )

    // Local state for URL fields with debouncing to prevent lag
    var localBaseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    
    // Sync from external changes (e.g., preset selection)
    LaunchedEffect(provider.baseUrl) {
        if (provider.baseUrl != localBaseUrl) {
            localBaseUrl = provider.baseUrl
        }
    }
    
    // Debounce commits to parent
    LaunchedEffect(localBaseUrl) {
        delay(300)
        val latest = latestProvider
        if (localBaseUrl != latest.baseUrl) {
            onEdit(latest.copy(baseUrl = localBaseUrl.trim()))
        }
    }

    OutlinedTextField(
        value = localBaseUrl,
        onValueChange = { localBaseUrl = it },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_base_url))
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (!provider.useResponseApi) {
        var localPath by remember(provider.id) { mutableStateOf(provider.chatCompletionsPath) }
        
        LaunchedEffect(provider.chatCompletionsPath) {
            if (provider.chatCompletionsPath != localPath) {
                localPath = provider.chatCompletionsPath
            }
        }
        
        LaunchedEffect(localPath) {
            delay(300)
            val latest = latestProvider
            if (localPath != latest.chatCompletionsPath) {
                onEdit(latest.copy(chatCompletionsPath = localPath.trim()))
            }
        }

        OutlinedTextField(
            value = localPath,
            onValueChange = { localPath = it },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_api_path))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !provider.builtIn
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(id = R.string.setting_provider_page_response_api), modifier = Modifier.weight(1f))
        val responseAPIWarning = stringResource(id = R.string.setting_provider_page_response_api_warning)
        Checkbox(
            checked = provider.useResponseApi,
            onCheckedChange = {
                onEdit(provider.copy(useResponseApi = it))

                if(it && provider.baseUrl.toHttpUrlOrNull()?.host != "api.openai.com") {
                    toaster.show(
                        message = responseAPIWarning,
                        type = ToastType.Warning
                    )
                }
            }
        )
    }
}

@Composable
private fun ColumnScope.ProviderConfigureClaude(
    provider: ProviderSetting.Claude,
    onEdit: (provider: ProviderSetting.Claude) -> Unit
) {
    val latestProvider by rememberUpdatedState(provider)
    provider.description()

    var apiKeyVisible by remember { mutableStateOf(false) }
    var localApiKey by remember(provider.id) { mutableStateOf(provider.apiKey) }
    LaunchedEffect(provider.apiKey) {
        if (provider.apiKey != localApiKey) {
            localApiKey = provider.apiKey
        }
    }
    LaunchedEffect(localApiKey) {
        delay(300)
        val latest = latestProvider
        if (localApiKey != latest.apiKey) {
            onEdit(latest.copy(apiKey = localApiKey.trim()))
        }
    }
    OutlinedTextField(
        value = localApiKey,
        onValueChange = { localApiKey = it },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_key))
        },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) apiKeyVisible = false },
        maxLines = 1,
        visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                Icon(
                    imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (apiKeyVisible) "Hide" else "Show"
                )
            }
        }
    )

    // Local state for URL field with debouncing to prevent lag
    var localBaseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    
    LaunchedEffect(provider.baseUrl) {
        if (provider.baseUrl != localBaseUrl) {
            localBaseUrl = provider.baseUrl
        }
    }
    
    LaunchedEffect(localBaseUrl) {
        delay(300)
        val latest = latestProvider
        if (localBaseUrl != latest.baseUrl) {
            onEdit(latest.copy(baseUrl = localBaseUrl.trim()))
        }
    }

    OutlinedTextField(
        value = localBaseUrl,
        onValueChange = { localBaseUrl = it },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_base_url))
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ColumnScope.ProviderConfigureGoogle(
    provider: ProviderSetting.Google,
    onEdit: (provider: ProviderSetting.Google) -> Unit
) {
    val latestProvider by rememberUpdatedState(provider)
    provider.description()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(id = R.string.setting_provider_page_vertex_ai), modifier = Modifier.weight(1f))
        Checkbox(
            checked = provider.vertexAI,
            onCheckedChange = {
                onEdit(provider.copy(vertexAI = it))
            }
        )
    }

    if (!provider.vertexAI) {
        var apiKeyVisible by remember { mutableStateOf(false) }
        var localApiKey by remember(provider.id) { mutableStateOf(provider.apiKey) }
        LaunchedEffect(provider.apiKey) {
            if (provider.apiKey != localApiKey) {
                localApiKey = provider.apiKey
            }
        }
        LaunchedEffect(localApiKey) {
            delay(300)
            val latest = latestProvider
            if (localApiKey != latest.apiKey) {
                onEdit(latest.copy(apiKey = localApiKey.trim()))
            }
        }
        OutlinedTextField(
            value = localApiKey,
            onValueChange = { localApiKey = it },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_api_key))
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) apiKeyVisible = false },
            maxLines = if (apiKeyVisible) 3 else 1,
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(
                        imageVector = if (apiKeyVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (apiKeyVisible) "Hide" else "Show"
                    )
                }
            }
        )

        // Local state for URL field with debouncing
        var localBaseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
        
        LaunchedEffect(provider.baseUrl) {
            if (provider.baseUrl != localBaseUrl) {
                localBaseUrl = provider.baseUrl
            }
        }
        
        LaunchedEffect(localBaseUrl) {
            delay(300)
            val latest = latestProvider
            if (localBaseUrl != latest.baseUrl) {
                onEdit(latest.copy(baseUrl = localBaseUrl.trim()))
            }
        }

        OutlinedTextField(
            value = localBaseUrl,
            onValueChange = { localBaseUrl = it },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_api_base_url))
            },
            modifier = Modifier.fillMaxWidth(),
            isError = !localBaseUrl.endsWith("/v1beta"),
            supportingText = if (!localBaseUrl.endsWith("/v1beta")) {
                {
                    Text("The base URL usually ends with `/v1beta`")
                }
            } else null
        )
    } else {
        // Local state for all Vertex AI text fields with debouncing
        var localEmail by remember(provider.id) { mutableStateOf(provider.serviceAccountEmail) }
        var localPrivateKey by remember(provider.id) { mutableStateOf(provider.privateKey) }
        var localLocation by remember(provider.id) { mutableStateOf(provider.location) }
        var localProjectId by remember(provider.id) { mutableStateOf(provider.projectId) }
        
        // Sync from external changes
        LaunchedEffect(provider.serviceAccountEmail) {
            if (provider.serviceAccountEmail != localEmail) localEmail = provider.serviceAccountEmail
        }
        LaunchedEffect(provider.privateKey) {
            if (provider.privateKey != localPrivateKey) localPrivateKey = provider.privateKey
        }
        LaunchedEffect(provider.location) {
            if (provider.location != localLocation) localLocation = provider.location
        }
        LaunchedEffect(provider.projectId) {
            if (provider.projectId != localProjectId) localProjectId = provider.projectId
        }
        
        // Debounce commits
        LaunchedEffect(localEmail) {
            delay(300)
            val latest = latestProvider
            if (localEmail != latest.serviceAccountEmail) onEdit(latest.copy(serviceAccountEmail = localEmail.trim()))
        }
        LaunchedEffect(localPrivateKey) {
            delay(300)
            val latest = latestProvider
            if (localPrivateKey != latest.privateKey) onEdit(latest.copy(privateKey = localPrivateKey.trim()))
        }
        LaunchedEffect(localLocation) {
            delay(300)
            val latest = latestProvider
            if (localLocation != latest.location) onEdit(latest.copy(location = localLocation.trim()))
        }
        LaunchedEffect(localProjectId) {
            delay(300)
            val latest = latestProvider
            if (localProjectId != latest.projectId) onEdit(latest.copy(projectId = localProjectId.trim()))
        }

        OutlinedTextField(
            value = localEmail,
            onValueChange = { localEmail = it },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_service_account_email))
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = localPrivateKey,
            onValueChange = { localPrivateKey = it },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_private_key))
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 6,
            minLines = 3,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        OutlinedTextField(
            value = localLocation,
            onValueChange = { localLocation = it },
            label = {
                // https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations#available-regions
                Text(stringResource(id = R.string.setting_provider_page_location))
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = localProjectId,
            onValueChange = { localProjectId = it },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_project_id))
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
