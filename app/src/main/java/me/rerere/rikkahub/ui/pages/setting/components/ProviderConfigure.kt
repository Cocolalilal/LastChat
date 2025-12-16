package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dokar.sonner.ToastType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalToaster
import androidx.compose.ui.text.font.FontFamily
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlin.reflect.KClass

@Composable
fun ProviderConfigure(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
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
            Switch(
                checked = provider.enabled,
                onCheckedChange = { enabled ->
                    val updated = when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(enabled = enabled)
                        is ProviderSetting.Google -> provider.copy(enabled = enabled)
                        is ProviderSetting.Claude -> provider.copy(enabled = enabled)
                        is ProviderSetting.NanoGPT -> provider.copy(enabled = enabled)
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

        // 3. Name field (shared)
        OutlinedTextField(
            value = provider.name,
            onValueChange = { newName ->
                val updated = when (provider) {
                    is ProviderSetting.OpenAI -> provider.copy(name = newName.trim())
                    is ProviderSetting.Google -> provider.copy(name = newName.trim())
                    is ProviderSetting.Claude -> provider.copy(name = newName.trim())
                    is ProviderSetting.NanoGPT -> provider.copy(name = newName.trim())
                }
                onEdit(updated)
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_name))
            },
            modifier = Modifier.fillMaxWidth(),
        )

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

            is ProviderSetting.NanoGPT -> {
                ProviderConfigureNanoGPT(provider, onEdit)
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
        is ProviderSetting.NanoGPT -> this.apiKey
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
            apiKey = apiKey,
            baseUrl = if (this is ProviderSetting.Claude) this.baseUrl else "https://api.anthropic.com/v1"
        )
        ProviderSetting.NanoGPT::class -> ProviderSetting.NanoGPT(
            id = this.id,
            enabled = this.enabled,
            name = this.name,
            models = this.models,
            proxy = this.proxy,
            balanceOption = this.balanceOption,
            apiKey = apiKey,
            baseUrl = if (this is ProviderSetting.NanoGPT) this.baseUrl else "https://nano-gpt.com/api"
        )
        else -> this // Return unchanged if unknown type
    }
}

@Composable
private fun ColumnScope.ProviderConfigureOpenAI(
    provider: ProviderSetting.OpenAI,
    onEdit: (provider: ProviderSetting.OpenAI) -> Unit
) {
    val toaster = LocalToaster.current

    provider.description()

    OutlinedTextField(
        value = provider.apiKey,
        onValueChange = {
            onEdit(provider.copy(apiKey = it.trim()))
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_key))
        },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )

    OutlinedTextField(
        value = provider.baseUrl,
        onValueChange = {
            onEdit(provider.copy(baseUrl = it.trim()))
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_base_url))
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (!provider.useResponseApi) {
        OutlinedTextField(
            value = provider.chatCompletionsPath,
            onValueChange = {
                onEdit(provider.copy(chatCompletionsPath = it.trim()))
            },
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
    provider.description()

    OutlinedTextField(
        value = provider.apiKey,
        onValueChange = {
            onEdit(provider.copy(apiKey = it.trim()))
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_key))
        },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = provider.baseUrl,
        onValueChange = {
            onEdit(provider.copy(baseUrl = it.trim()))
        },
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
        OutlinedTextField(
            value = provider.apiKey,
            onValueChange = {
                onEdit(provider.copy(apiKey = it.trim()))
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_api_key))
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )

        OutlinedTextField(
            value = provider.baseUrl,
            onValueChange = {
                onEdit(provider.copy(baseUrl = it.trim()))
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_api_base_url))
            },
            modifier = Modifier.fillMaxWidth(),
            isError = !provider.baseUrl.endsWith("/v1beta"),
            supportingText = if (!provider.baseUrl.endsWith("/v1beta")) {
                {
                    Text("The base URL usually ends with `/v1beta`")
                }
            } else null
        )
    } else {
        OutlinedTextField(
            value = provider.serviceAccountEmail,
            onValueChange = {
                onEdit(provider.copy(serviceAccountEmail = it.trim()))
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_service_account_email))
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = provider.privateKey,
            onValueChange = {
                onEdit(provider.copy(privateKey = it.trim()))
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_private_key))
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 6,
            minLines = 3,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
        OutlinedTextField(
            value = provider.location,
            onValueChange = {
                onEdit(provider.copy(location = it.trim()))
            },
            label = {
                // https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations#available-regions
                Text(stringResource(id = R.string.setting_provider_page_location))
            },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = provider.projectId,
            onValueChange = {
                onEdit(provider.copy(projectId = it.trim()))
            },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_project_id))
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ColumnScope.ProviderConfigureNanoGPT(
    provider: ProviderSetting.NanoGPT,
    onEdit: (provider: ProviderSetting.NanoGPT) -> Unit
) {
    provider.description()

    OutlinedTextField(
        value = provider.apiKey,
        onValueChange = {
            onEdit(provider.copy(apiKey = it.trim()))
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_key))
        },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 3,
    )

    OutlinedTextField(
        value = provider.baseUrl,
        onValueChange = {
            onEdit(provider.copy(baseUrl = it.trim()))
        },
        label = {
            Text(stringResource(id = R.string.setting_provider_page_api_base_url))
        },
        modifier = Modifier.fillMaxWidth()
    )
}
