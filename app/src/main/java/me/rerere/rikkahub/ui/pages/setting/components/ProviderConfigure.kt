package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.Model
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
import org.koin.core.parameter.parametersOf
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material3.Card
import androidx.compose.material3.SuggestionChipDefaults

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.ai.provider.OpenAICompatibilityMode
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.ReasoningRequestBehavior
import me.rerere.ai.provider.withComfyDefaults
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomBodies
import me.rerere.rikkahub.ui.components.ui.DebouncedTextField
import me.rerere.rikkahub.ui.components.ui.AutoSaveIndicator
import me.rerere.rikkahub.ui.theme.AppShapes
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.size
import me.rerere.rikkahub.ui.components.ui.ProviderIcon
import me.rerere.rikkahub.ui.components.ui.lobeHubIconUri
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.utils.ImageUtils
import me.rerere.common.http.replaceUrlEncodedPathOrNull
import me.rerere.common.http.urlPartsOrNull
import java.nio.charset.Charset
import kotlin.reflect.KClass

@Composable
fun ProviderConfigure(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    showSavingIndicator: Boolean = false,
    showEnabledToggle: Boolean = true,
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
        if (showEnabledToggle) {
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
                AutoSaveIndicator(visible = showSavingIndicator)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
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
        } else if (showSavingIndicator) {
            AutoSaveIndicator(visible = true)
        }

        // 2. Type selector (for non-built-in remote providers)
        if (!provider.builtIn && provider !is ProviderSetting.ComfyUI) {
            ProviderTypeSelector(
                selectedType = provider::class,
                onTypeSelected = { type ->
                    onEdit(provider.convertTo(type))
                }
            )
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
            DebouncedTextField(
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
                enabled = true,
                stateKey = "provider_name_${provider.id}",
                label = stringResource(id = R.string.setting_provider_page_name),
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

            is ProviderSetting.ComfyUI -> {
                ProviderConfigureComfyUI(provider, onEdit)
            }
        }
    }
}

@Composable
private fun ProviderTypeSelector(
    selectedType: KClass<out ProviderSetting>,
    onTypeSelected: (KClass<out ProviderSetting>) -> Unit
) {
    val haptics = rememberPremiumHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy((-1).dp)
    ) {
        ProviderSetting.Types.forEachIndexed { index, type ->
            val selected = selectedType == type
            val interactionSource = remember(type) { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.96f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                label = "provider_type_scale"
            )
            val containerColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "provider_type_container"
            )
            val contentColor by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                label = "provider_type_content"
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .zIndex(if (selected) 1f else 0f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            if (!selected) {
                                onTypeSelected(type)
                            }
                        }
                    ),
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ProviderSetting.Types.size
                ),
                color = containerColor,
                contentColor = contentColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(
                        text = type.simpleName ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
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

    val convertedName = convertProviderNameTo(type)
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
        else -> ""
    }
    val targetDefaultBaseUrl = when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI().baseUrl
        ProviderSetting.Google::class -> ProviderSetting.Google().baseUrl
        ProviderSetting.Claude::class -> ProviderSetting.Claude().baseUrl
        ProviderSetting.ComfyUI::class -> ProviderSetting.ComfyUI().baseUrl
        else -> return this
    }
    val convertedBaseUrl = sourceBaseUrl.convertToTargetBaseUrl(targetDefaultBaseUrl)

    return when (type) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI(
            id = this.id,
            enabled = this.enabled,
            name = convertedName,
            models = this.models,
            proxy = this.proxy,
            balanceOption = this.balanceOption,
            tags = this.tags,
            customIconUri = this.customIconUri,
            builtIn = this.builtIn,
            apiKey = apiKey,
            baseUrl = convertedBaseUrl,
            chatCompletionsPath = if (this is ProviderSetting.OpenAI) this.chatCompletionsPath else ProviderSetting.OpenAI().chatCompletionsPath,
            useResponseApi = if (this is ProviderSetting.OpenAI) this.useResponseApi else false,
            reasoningBehavior = if (this is ProviderSetting.OpenAI) this.reasoningBehavior else null,
            streamOptionsMode = if (this is ProviderSetting.OpenAI) this.streamOptionsMode else OpenAICompatibilityMode.AUTO,
            imageResponseModalitiesMode = if (this is ProviderSetting.OpenAI) this.imageResponseModalitiesMode else OpenAICompatibilityMode.AUTO,
            reasoningContentReplayMode = if (this is ProviderSetting.OpenAI) this.reasoningContentReplayMode else OpenAICompatibilityMode.AUTO,
            promptCacheMode = if (this is ProviderSetting.OpenAI) this.promptCacheMode else OpenAICompatibilityMode.AUTO,
        )

        ProviderSetting.Google::class -> ProviderSetting.Google(
            id = this.id,
            enabled = this.enabled,
            name = convertedName,
            models = this.models,
            proxy = this.proxy,
            balanceOption = this.balanceOption,
            tags = this.tags,
            customIconUri = this.customIconUri,
            builtIn = this.builtIn,
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
            name = convertedName,
            models = this.models,
            proxy = this.proxy,
            balanceOption = this.balanceOption,
            tags = this.tags,
            customIconUri = this.customIconUri,
            builtIn = this.builtIn,
            apiKey = apiKey,
            baseUrl = convertedBaseUrl
        )

        ProviderSetting.ComfyUI::class -> ProviderSetting.ComfyUI(
            id = this.id,
            enabled = this.enabled,
            name = convertedName,
            models = this.models.map { it.withComfyDefaults() },
            proxy = this.proxy,
            balanceOption = this.balanceOption,
            tags = this.tags,
            customIconUri = this.customIconUri,
            builtIn = this.builtIn,
            baseUrl = if (this is ProviderSetting.ComfyUI) this.baseUrl else convertedBaseUrl,
            workflowJson = if (this is ProviderSetting.ComfyUI) this.workflowJson else "",
            promptNodeId = if (this is ProviderSetting.ComfyUI) this.promptNodeId else "",
            promptInputName = if (this is ProviderSetting.ComfyUI) this.promptInputName else "text",
            modelNodeId = if (this is ProviderSetting.ComfyUI) this.modelNodeId else "",
            modelInputName = if (this is ProviderSetting.ComfyUI) this.modelInputName else "ckpt_name",
        )

        else -> this
    }
}

private fun ProviderSetting.convertProviderNameTo(type: KClass<out ProviderSetting>): String {
    val currentDefaultName = this::class.defaultProviderName()
    val targetDefaultName = type.defaultProviderName()
    return if (name.isBlank() || name == currentDefaultName) {
        targetDefaultName
    } else {
        name
    }
}

private fun KClass<out ProviderSetting>.defaultProviderName(): String {
    return when (this) {
        ProviderSetting.OpenAI::class -> ProviderSetting.OpenAI().name
        ProviderSetting.Google::class -> ProviderSetting.Google().name
        ProviderSetting.Claude::class -> ProviderSetting.Claude().name
        ProviderSetting.ComfyUI::class -> ProviderSetting.ComfyUI().name
        else -> simpleName.orEmpty()
    }
}

private fun String.convertToTargetBaseUrl(targetDefaultBaseUrl: String): String {
    val sourceUrl = this.urlPartsOrNull() ?: return this
    val sourceHost = sourceUrl.host
    if (sourceHost in OFFICIAL_PROVIDER_HOSTS) {
        return targetDefaultBaseUrl
    }

    val targetUrl = targetDefaultBaseUrl.urlPartsOrNull() ?: return this
    val convertedPath = sourceUrl.encodedPath.convertToTargetPath(targetUrl.encodedPath)
    return this.replaceUrlEncodedPathOrNull(convertedPath) ?: this
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
    val haptics = rememberPremiumHaptics()
    var showAdvancedMapping by remember(provider.id) {
        mutableStateOf(provider.promptNodeId.isNotBlank() || provider.modelNodeId.isNotBlank())
    }
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.CardMedium,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_provider_page_comfyui_setup_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.setting_provider_page_comfyui_setup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
            )
        }
    }

    DebouncedTextField(
        value = provider.baseUrl,
        onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
        stateKey = "comfyui_base_url_${provider.id}",
        label = stringResource(R.string.setting_provider_page_comfyui_server_url),
        modifier = Modifier.fillMaxWidth(),
    )

    Button(
        onClick = {
            haptics.perform(HapticPattern.Pop)
            workflowLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        },
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.ButtonPill,
    ) {
        Text(stringResource(R.string.setting_provider_page_comfyui_import_workflow))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.CardMedium,
        color = if (provider.workflowJson.isBlank()) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        },
        contentColor = if (provider.workflowJson.isBlank()) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
    ) {
        Text(
            text = stringResource(
                if (provider.workflowJson.isBlank()) {
                    R.string.setting_provider_page_comfyui_workflow_missing
                } else {
                    R.string.setting_provider_page_comfyui_workflow_ready
                }
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(14.dp),
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.setting_provider_page_comfyui_advanced_mapping),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.setting_provider_page_comfyui_advanced_mapping_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HapticSwitch(
            checked = showAdvancedMapping,
            onCheckedChange = { showAdvancedMapping = it },
        )
    }

    if (showAdvancedMapping) {
        DebouncedTextField(
            value = provider.promptNodeId,
            onValueChange = { onEdit(provider.copy(promptNodeId = it.trim())) },
            stateKey = "comfyui_prompt_node_${provider.id}",
            label = stringResource(R.string.setting_provider_page_comfyui_prompt_node),
            modifier = Modifier.fillMaxWidth(),
        )

        DebouncedTextField(
            value = provider.promptInputName,
            onValueChange = { onEdit(provider.copy(promptInputName = it.trim())) },
            stateKey = "comfyui_prompt_input_${provider.id}",
            label = stringResource(R.string.setting_provider_page_comfyui_prompt_input),
            modifier = Modifier.fillMaxWidth(),
        )

        DebouncedTextField(
            value = provider.modelNodeId,
            onValueChange = { onEdit(provider.copy(modelNodeId = it.trim())) },
            stateKey = "comfyui_model_node_${provider.id}",
            label = stringResource(R.string.setting_provider_page_comfyui_model_node),
            modifier = Modifier.fillMaxWidth(),
        )

        DebouncedTextField(
            value = provider.modelInputName,
            onValueChange = { onEdit(provider.copy(modelInputName = it.trim())) },
            stateKey = "comfyui_model_input_${provider.id}",
            label = stringResource(R.string.setting_provider_page_comfyui_model_input),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ColumnScope.ProviderConfigureOpenAI(
    provider: ProviderSetting.OpenAI,
    onEdit: (provider: ProviderSetting.OpenAI) -> Unit
) {
    val latestProvider by rememberUpdatedState(provider)
    val toaster = LocalToaster.current

    DebouncedTextField(
        value = provider.apiKey,
        onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
        stateKey = "openai_api_key_${provider.id}",
        label = stringResource(id = R.string.setting_provider_page_api_key),
        modifier = Modifier.fillMaxWidth(),
        isSecure = true
    )

    DebouncedTextField(
        value = provider.baseUrl,
        onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
        stateKey = "openai_base_url_${provider.id}",
        label = stringResource(id = R.string.setting_provider_page_api_base_url),
        modifier = Modifier.fillMaxWidth(),
    )

    if (!provider.useResponseApi) {
        DebouncedTextField(
            value = provider.chatCompletionsPath,
            onValueChange = { onEdit(provider.copy(chatCompletionsPath = it.trim())) },
            stateKey = "openai_path_${provider.id}",
            label = stringResource(id = R.string.setting_provider_page_api_path),
            modifier = Modifier.fillMaxWidth(),
            enabled = !provider.builtIn,
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

                if(it && provider.baseUrl.urlPartsOrNull()?.host != "api.openai.com") {
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

    DebouncedTextField(
        value = provider.apiKey,
        onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
        stateKey = "claude_api_key_${provider.id}",
        label = stringResource(id = R.string.setting_provider_page_api_key),
        modifier = Modifier.fillMaxWidth(),
        isSecure = true
    )

    DebouncedTextField(
        value = provider.baseUrl,
        onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
        stateKey = "claude_base_url_${provider.id}",
        label = stringResource(id = R.string.setting_provider_page_api_base_url),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ColumnScope.ProviderConfigureGoogle(
    provider: ProviderSetting.Google,
    onEdit: (provider: ProviderSetting.Google) -> Unit
) {
    val latestProvider by rememberUpdatedState(provider)

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
        DebouncedTextField(
            value = provider.apiKey,
            onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
            stateKey = "google_api_key_${provider.id}",
            label = stringResource(id = R.string.setting_provider_page_api_key),
            modifier = Modifier.fillMaxWidth(),
            isSecure = true
        )

        DebouncedTextField(
            value = provider.baseUrl,
            onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
            stateKey = "google_base_url_${provider.id}",
            label = stringResource(id = R.string.setting_provider_page_api_base_url),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = if (!provider.baseUrl.endsWith("/v1beta")) {
                {
                    Text(
                        text = stringResource(R.string.setting_provider_page_vertex_ai_base_url_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            } else null
        )
    } else {
        DebouncedTextField(
            value = provider.serviceAccountEmail,
            onValueChange = { onEdit(provider.copy(serviceAccountEmail = it.trim())) },
            stateKey = "google_email_${provider.id}",
            label = stringResource(id = R.string.setting_provider_page_service_account_email),
            modifier = Modifier.fillMaxWidth(),
        )
        DebouncedTextField(
            value = provider.privateKey,
            onValueChange = { onEdit(provider.copy(privateKey = it.trim())) },
            stateKey = "google_private_key_${provider.id}",
            label = stringResource(id = R.string.setting_provider_page_private_key),
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            isSecure = true
        )
        DebouncedTextField(
            value = provider.location,
            onValueChange = { onEdit(provider.copy(location = it.trim())) },
            stateKey = "google_location_${provider.id}",
            label = stringResource(id = R.string.setting_provider_page_location),
            modifier = Modifier.fillMaxWidth(),
        )
        DebouncedTextField(
            value = provider.projectId,
            onValueChange = { onEdit(provider.copy(projectId = it.trim())) },
            stateKey = "google_project_id_${provider.id}",
            label = stringResource(id = R.string.setting_provider_page_project_id),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
