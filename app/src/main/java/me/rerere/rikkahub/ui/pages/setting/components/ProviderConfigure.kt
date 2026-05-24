package me.rerere.rikkahub.ui.pages.setting.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.ai.provider.OpenAICompatibilityMode
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.ReasoningRequestBehavior
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomBodies
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.size
import me.rerere.rikkahub.ui.components.ui.ProviderIcon
import me.rerere.rikkahub.ui.components.ui.lobeHubIconUri
import me.rerere.rikkahub.utils.ImageUtils
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.nio.charset.Charset
import kotlin.reflect.KClass

@Composable
fun ProviderConfigure(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    showSavingIndicator: Boolean = false,
    onEdit: (provider: ProviderSetting) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val iconPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val extension = iconFileExtension(context, uri)
            val copiedUri = withContext(Dispatchers.IO) {
                ImageUtils.copyImageToInternalStorage(
                    context = context,
                    sourceUri = uri,
                    fileName = "provider_icon_${provider.id}.$extension",
                )
            }
            copiedUri?.let { iconUri ->
                onEdit(provider.copyProvider(customIconUri = iconUri.toString()))
            }
        }
    }

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
                    text = stringResource(R.string.setting_provider_page_saving),
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
                        is ProviderSetting.ComfyUI -> provider.copy(enabled = enabled)
                    }
                    onEdit(updated)
                }
            )
        }

        // 2. Type selector (for non-built-in remote providers)
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

        // 3. Name field with catalog icon preview
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CustomIconSelector(
                customIconUri = provider.customIconUri,
                onPickFile = {
                    iconPickerLauncher.launch(arrayOf("image/*", "image/svg+xml"))
                },
                onPickLobeHubIcon = { slug ->
                    onEdit(provider.copyProvider(customIconUri = lobeHubIconUri(slug)))
                },
                onReset = {
                    onEdit(provider.copyProvider(customIconUri = null))
                },
            ) { iconModifier ->
                ProviderIcon(
                    provider = provider,
                    modifier = iconModifier,
                )
            }
            OutlinedTextField(
                value = provider.name,
                onValueChange = { newName ->
                    val updated = when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(name = newName)
                        is ProviderSetting.Google -> provider.copy(name = newName)
                        is ProviderSetting.Claude -> provider.copy(name = newName)
                        is ProviderSetting.ComfyUI -> provider.copy(name = newName)
                    }
                    onEdit(updated)
                },
                label = {
                    Text(stringResource(id = R.string.setting_provider_page_name))
                },
                modifier = Modifier.weight(1f),
                shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
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

            is ProviderSetting.ComfyUI -> {
                ProviderConfigureComfyUI(provider, onEdit)
            }
        }
    }
}

/**
 * Convert a provider to a different type while preserving all common properties.
 */
fun ProviderSetting.convertTo(type: KClass<out ProviderSetting>): ProviderSetting {
    if (this::class == type) {
        return this
    }

    val apiKey = when (this) {
        is ProviderSetting.OpenAI -> this.apiKey
        is ProviderSetting.Google -> this.apiKey
        is ProviderSetting.Claude -> this.apiKey
        is ProviderSetting.ComfyUI -> ""
    }

    val sourceBaseUrl = when (this) {
        is ProviderSetting.OpenAI -> this.baseUrl
        is ProviderSetting.Google -> this.baseUrl
        is ProviderSetting.Claude -> this.baseUrl
        is ProviderSetting.ComfyUI -> this.baseUrl
    }
    val targetDefaultBaseUrl = when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI().baseUrl
        ProviderSetting.Google::class -> ProviderSetting.Google().baseUrl
        ProviderSetting.Claude::class -> ProviderSetting.Claude().baseUrl
        ProviderSetting.ComfyUI::class -> ProviderSetting.ComfyUI().baseUrl
        else -> error("Unsupported provider type: $type")
    }
    val convertedBaseUrl = sourceBaseUrl.convertToTargetBaseUrl(targetDefaultBaseUrl)

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
            builtIn = this.builtIn,
            description = this.description,
            shortDescription = this.shortDescription,
            apiKey = apiKey,
            baseUrl = convertedBaseUrl,
            chatCompletionsPath = if (this is ProviderSetting.OpenAI) this.chatCompletionsPath else ProviderSetting.OpenAI().chatCompletionsPath,
            useResponseApi = if (this is ProviderSetting.OpenAI) this.useResponseApi else false,
            reasoningBehavior = if (this is ProviderSetting.OpenAI) this.reasoningBehavior else null,
            streamOptionsMode = if (this is ProviderSetting.OpenAI) this.streamOptionsMode else OpenAICompatibilityMode.AUTO,
            imageResponseModalitiesMode = if (this is ProviderSetting.OpenAI) this.imageResponseModalitiesMode else OpenAICompatibilityMode.AUTO,
            reasoningContentReplayMode = if (this is ProviderSetting.OpenAI) this.reasoningContentReplayMode else OpenAICompatibilityMode.AUTO,
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
            builtIn = this.builtIn,
            description = this.description,
            shortDescription = this.shortDescription,
            apiKey = apiKey,
            baseUrl = convertedBaseUrl,
            vertexAI = if (this is ProviderSetting.Google) this.vertexAI else false,
            privateKey = if (this is ProviderSetting.Google) this.privateKey else ProviderSetting.Google().privateKey,
            serviceAccountEmail = if (this is ProviderSetting.Google) this.serviceAccountEmail else ProviderSetting.Google().serviceAccountEmail,
            location = if (this is ProviderSetting.Google) this.location else ProviderSetting.Google().location,
            projectId = if (this is ProviderSetting.Google) this.projectId else ProviderSetting.Google().projectId
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
            builtIn = this.builtIn,
            description = this.description,
            shortDescription = this.shortDescription,
            apiKey = apiKey,
            baseUrl = convertedBaseUrl
        )

        ProviderSetting.ComfyUI::class -> ProviderSetting.ComfyUI(
            id = this.id,
            enabled = this.enabled,
            name = this.name,
            models = this.models,
            proxy = this.proxy,
            balanceOption = this.balanceOption,
            tags = this.tags,
            customIconUri = this.customIconUri,
            builtIn = this.builtIn,
            description = this.description,
            shortDescription = this.shortDescription,
            baseUrl = if (this is ProviderSetting.ComfyUI) this.baseUrl else convertedBaseUrl,
            workflowJson = if (this is ProviderSetting.ComfyUI) this.workflowJson else "",
            promptNodeId = if (this is ProviderSetting.ComfyUI) this.promptNodeId else "",
            promptInputName = if (this is ProviderSetting.ComfyUI) this.promptInputName else "text",
            modelNodeId = if (this is ProviderSetting.ComfyUI) this.modelNodeId else "",
            modelInputName = if (this is ProviderSetting.ComfyUI) this.modelInputName else "ckpt_name",
        )

        else -> error("Unsupported provider type: $type")
    }
}

private fun String.convertToTargetBaseUrl(targetDefaultBaseUrl: String): String {
    val sourceUrl = this.toHttpUrlOrNull() ?: return this
    val sourceHost = sourceUrl.host.lowercase()
    if (sourceHost in OFFICIAL_PROVIDER_HOSTS) {
        return targetDefaultBaseUrl
    }

    val targetUrl = targetDefaultBaseUrl.toHttpUrlOrNull() ?: return this
    val convertedPath = sourceUrl.encodedPath.convertToTargetPath(targetUrl.encodedPath)
    return sourceUrl.newBuilder()
        .encodedPath(convertedPath)
        .build()
        .toString()
}

private fun String.convertToTargetPath(targetPath: String): String {
    val source = this.normalizePath()
    val target = targetPath.normalizePath()

    val replaced = when {
        source.lowercase().endsWith(V1_BETA_SUFFIX) -> source.dropLast(V1_BETA_SUFFIX.length) + target
        source.lowercase().endsWith(V1_SUFFIX) -> source.dropLast(V1_SUFFIX.length) + target
        source.isBlank() -> target
        else -> source + target
    }

    return replaced.normalizePath()
}

private fun String.normalizePath(): String {
    val value = this.trim()
    if (value.isEmpty() || value == "/") {
        return ""
    }
    val path = if (value.startsWith("/")) value else "/$value"
    return path.trimEnd('/')
}

private const val OPENAI_OFFICIAL_HOST = "api.openai.com"
private const val GOOGLE_OFFICIAL_HOST = "generativelanguage.googleapis.com"
private const val CLAUDE_OFFICIAL_HOST = "api.anthropic.com"
private const val V1_SUFFIX = "/v1"
private const val V1_BETA_SUFFIX = "/v1beta"

private val OFFICIAL_PROVIDER_HOSTS = setOf(
    OPENAI_OFFICIAL_HOST,
    GOOGLE_OFFICIAL_HOST,
    CLAUDE_OFFICIAL_HOST
)

@Composable
private fun ColumnScope.ProviderConfigureComfyUI(
    provider: ProviderSetting.ComfyUI,
    onEdit: (provider: ProviderSetting.ComfyUI) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestProvider by rememberUpdatedState(provider)
    val workflowLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val workflow = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charset.forName("UTF-8"))
                }.orEmpty()
            }
            if (workflow.isNotBlank()) {
                onEdit(latestProvider.copy(workflowJson = workflow))
            }
        }
    }

    provider.description()

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
        label = { Text(stringResource(R.string.setting_provider_page_api_base_url)) },
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
    )

    Button(
        onClick = { workflowLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.setting_provider_page_comfyui_import_workflow))
    }

    Text(
        text = stringResource(
            if (provider.workflowJson.isBlank()) {
                R.string.setting_provider_page_comfyui_workflow_missing
            } else {
                R.string.setting_provider_page_comfyui_workflow_ready
            }
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = provider.promptNodeId,
        onValueChange = { onEdit(provider.copy(promptNodeId = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_comfyui_prompt_node)) },
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
    )

    OutlinedTextField(
        value = provider.promptInputName,
        onValueChange = { onEdit(provider.copy(promptInputName = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_comfyui_prompt_input)) },
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
    )

    OutlinedTextField(
        value = provider.modelNodeId,
        onValueChange = { onEdit(provider.copy(modelNodeId = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_comfyui_model_node)) },
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
    )

    OutlinedTextField(
        value = provider.modelInputName,
        onValueChange = { onEdit(provider.copy(modelInputName = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_comfyui_model_input)) },
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
    )
}

@Composable
private fun ColumnScope.ProviderConfigureOpenAI(
    provider: ProviderSetting.OpenAI,
    onEdit: (provider: ProviderSetting.OpenAI) -> Unit
) {
    val latestProvider by rememberUpdatedState(provider)
    val toaster = LocalToaster.current

    provider.description()

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
    SecureOutlinedTextField(
        value = localApiKey,
        onValueChange = { localApiKey = it },
        label = stringResource(id = R.string.setting_provider_page_api_key),
        modifier = Modifier
            .fillMaxWidth(),
        maxVisibleLines = 3
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
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
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
            enabled = !provider.builtIn,
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
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

private fun iconFileExtension(context: android.content.Context, uri: android.net.Uri): String {
    val mimeType = context.contentResolver.getType(uri)?.lowercase()
    return when {
        mimeType == "image/svg+xml" -> "svg"
        mimeType == "image/png" -> "png"
        mimeType == "image/jpeg" -> "jpg"
        mimeType == "image/webp" -> "webp"
        !mimeType.isNullOrBlank() -> android.webkit.MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(mimeType)
            ?.takeIf { it.isNotBlank() }
            ?: "png"
        else -> uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.takeIf { it.length in 2..5 }
            ?: "png"
    }
}

@Composable
private fun OpenAICompatibilityModeSetting(
    label: String,
    selected: OpenAICompatibilityMode,
    onSelected: (OpenAICompatibilityMode) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        val modes = OpenAICompatibilityMode.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    label = {
                        Text(
                            when (mode) {
                                OpenAICompatibilityMode.AUTO -> stringResource(R.string.setting_provider_page_compatibility_auto)
                                OpenAICompatibilityMode.ENABLED -> stringResource(R.string.setting_provider_page_compatibility_on)
                                OpenAICompatibilityMode.DISABLED -> stringResource(R.string.setting_provider_page_compatibility_off)
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ReasoningBehaviorEditor(
    behavior: ReasoningRequestBehavior,
    onChange: (ReasoningRequestBehavior) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ReasoningBodiesSection(
            title = stringResource(R.string.reasoning_off),
            bodies = behavior.off,
            onUpdate = { onChange(behavior.copy(off = it)) },
        )
        ReasoningBodiesSection(
            title = stringResource(R.string.reasoning_auto),
            bodies = behavior.auto,
            onUpdate = { onChange(behavior.copy(auto = it)) },
        )
        ReasoningBodiesSection(
            title = stringResource(R.string.reasoning_light),
            bodies = behavior.low,
            onUpdate = { onChange(behavior.copy(low = it)) },
        )
        ReasoningBodiesSection(
            title = stringResource(R.string.reasoning_medium),
            bodies = behavior.medium,
            onUpdate = { onChange(behavior.copy(medium = it)) },
        )
        ReasoningBodiesSection(
            title = stringResource(R.string.reasoning_heavy),
            bodies = behavior.high,
            onUpdate = { onChange(behavior.copy(high = it)) },
        )
    }
}

@Composable
private fun ReasoningBodiesSection(
    title: String,
    bodies: List<me.rerere.ai.provider.CustomBody>,
    onUpdate: (List<me.rerere.ai.provider.CustomBody>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        CustomBodies(customBodies = bodies, onUpdate = onUpdate)
    }
}

@Composable
private fun ColumnScope.ProviderConfigureClaude(
    provider: ProviderSetting.Claude,
    onEdit: (provider: ProviderSetting.Claude) -> Unit
) {
    val latestProvider by rememberUpdatedState(provider)
    provider.description()

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
    SecureOutlinedTextField(
        value = localApiKey,
        onValueChange = { localApiKey = it },
        label = stringResource(id = R.string.setting_provider_page_api_key),
        modifier = Modifier.fillMaxWidth()
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
        modifier = Modifier.fillMaxWidth(),
        shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
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
        SecureOutlinedTextField(
            value = localApiKey,
            onValueChange = { localApiKey = it },
            label = stringResource(id = R.string.setting_provider_page_api_key),
            modifier = Modifier.fillMaxWidth(),
            maxVisibleLines = 3
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
                    Text(stringResource(R.string.setting_provider_page_vertex_ai_base_url_hint))
                }
            } else null,
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
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
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
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
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
        OutlinedTextField(
            value = localLocation,
            onValueChange = { localLocation = it },
            label = {
                // https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations#available-regions
                Text(stringResource(id = R.string.setting_provider_page_location))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
        OutlinedTextField(
            value = localProjectId,
            onValueChange = { localProjectId = it },
            label = {
                Text(stringResource(id = R.string.setting_provider_page_project_id))
            },
            modifier = Modifier.fillMaxWidth(),
            shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
        )
    }
}
