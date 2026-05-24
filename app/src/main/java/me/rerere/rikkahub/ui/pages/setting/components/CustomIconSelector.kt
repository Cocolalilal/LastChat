package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.lobeHubIconUri
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes

@Composable
fun CustomIconSelector(
    customIconUri: String?,
    onPickFile: () -> Unit,
    onPickLobeHubIcon: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Modifier) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val haptics = rememberPremiumHaptics()
    val hasUserCustomIcon = customIconUri.isUserCustomIconUri()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "customIconSelectorScale",
    )

    Box(modifier = modifier.size(56.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    haptics.perform(HapticPattern.Pop)
                    if (hasUserCustomIcon) {
                        onReset()
                    } else {
                        showPicker = true
                    }
                },
        ) {
            icon(Modifier.fillMaxSize())
        }

        if (hasUserCustomIcon) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shadowElevation = 2.dp,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.reset),
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }

    if (showPicker) {
        CustomIconPickerSheet(
            onPickFile = {
                showPicker = false
                onPickFile()
            },
            onPickLobeHubIcon = { slug ->
                showPicker = false
                onPickLobeHubIcon(slug)
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun CustomIconPickerSheet(
    onPickFile: () -> Unit,
    onPickLobeHubIcon: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptics = rememberPremiumHaptics()
    var selectedMode by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredIcons = remember(searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            LobeHubIconOptions
        } else {
            LobeHubIconOptions.filter { option ->
                option.label.contains(query, ignoreCase = true) ||
                    option.slug.contains(query, ignoreCase = true) ||
                    option.aliases.any { it.contains(query, ignoreCase = true) }
            }
        }
    }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        onDismissRequest = onDismiss,
        dragHandle = {
            IconButton(
                onClick = {
                    haptics.perform(HapticPattern.Pop)
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
            ) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_provider_page_select_icon),
                style = MaterialTheme.typography.headlineSmall,
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedMode == 0,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        selectedMode = 0
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(stringResource(R.string.setting_provider_page_pick_icon_file)) },
                    icon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                )
                SegmentedButton(
                    selected = selectedMode == 1,
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        selectedMode = 1
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(stringResource(R.string.setting_provider_page_search_lobehub)) },
                    icon = { Icon(Icons.Rounded.Widgets, contentDescription = null) },
                )
            }

            if (selectedMode == 0) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            onPickFile()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.ButtonPill,
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.setting_provider_page_pick_icon_file))
                    }
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.icon_picker_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.SearchField,
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.clear_search),
                                )
                            }
                        }
                    } else {
                        null
                    },
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredIcons, key = { it.slug }) { option ->
                        LobeHubIconOption(
                            option = option,
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                onPickLobeHubIcon(option.slug)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LobeHubIconOption(
    option: LobeHubIconChoice,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.CardMedium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                AutoAIIconWithUrl(
                    name = option.label,
                    customIconUri = lobeHubIconUri(option.slug),
                    modifier = Modifier.size(30.dp),
                    padding = 3.dp,
                )
            }
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun String?.isUserCustomIconUri(): Boolean {
    if (isNullOrBlank()) return false
    val lower = lowercase()
    return !lower.contains("/catalog/icons/") &&
        !lower.contains("raw.githubusercontent.com/cocolalilal/lastchat") &&
        !lower.startsWith("icons/") &&
        !lower.startsWith("/icons/") &&
        !lower.contains("file:///android_asset/icons/")
}

private data class LobeHubIconChoice(
    val slug: String,
    val label: String,
    val aliases: List<String> = emptyList(),
)

private val LobeHubIconOptions = listOf(
    LobeHubIconChoice("openai", "OpenAI", listOf("gpt", "chatgpt")),
    LobeHubIconChoice("anthropic", "Anthropic", listOf("claude")),
    LobeHubIconChoice("google", "Google", listOf("gemini", "palm")),
    LobeHubIconChoice("gemini", "Gemini", listOf("google")),
    LobeHubIconChoice("deepseek", "DeepSeek"),
    LobeHubIconChoice("mistral", "Mistral AI"),
    LobeHubIconChoice("meta", "Meta", listOf("llama")),
    LobeHubIconChoice("xai", "xAI", listOf("grok")),
    LobeHubIconChoice("grok", "Grok", listOf("xai")),
    LobeHubIconChoice("qwen", "Qwen", listOf("alibaba")),
    LobeHubIconChoice("moonshot", "Moonshot", listOf("kimi")),
    LobeHubIconChoice("cohere", "Cohere", listOf("command")),
    LobeHubIconChoice("perplexity", "Perplexity", listOf("sonar")),
    LobeHubIconChoice("openrouter", "OpenRouter"),
    LobeHubIconChoice("ollama", "Ollama"),
    LobeHubIconChoice("groq", "Groq"),
    LobeHubIconChoice("together", "Together AI"),
    LobeHubIconChoice("fireworks", "Fireworks AI"),
    LobeHubIconChoice("siliconflow", "SiliconFlow"),
    LobeHubIconChoice("zhipu", "Zhipu AI", listOf("glm")),
    LobeHubIconChoice("minimax", "MiniMax"),
    LobeHubIconChoice("bytedance", "ByteDance", listOf("doubao")),
    LobeHubIconChoice("doubao", "Doubao", listOf("bytedance")),
    LobeHubIconChoice("hunyuan", "Hunyuan", listOf("tencent")),
    LobeHubIconChoice("tencent", "Tencent", listOf("hunyuan")),
    LobeHubIconChoice("yi", "Yi", listOf("01-ai")),
    LobeHubIconChoice("baichuan", "Baichuan"),
    LobeHubIconChoice("baidu", "Baidu", listOf("ernie", "wenxin")),
    LobeHubIconChoice("ai21", "AI21"),
    LobeHubIconChoice("amazon", "Amazon", listOf("bedrock", "aws")),
    LobeHubIconChoice("microsoft", "Microsoft", listOf("azure")),
    LobeHubIconChoice("github", "GitHub", listOf("copilot")),
    LobeHubIconChoice("nvidia", "NVIDIA"),
    LobeHubIconChoice("cerebras", "Cerebras"),
    LobeHubIconChoice("cloudflare", "Cloudflare"),
    LobeHubIconChoice("huggingface", "Hugging Face"),
    LobeHubIconChoice("replicate", "Replicate"),
    LobeHubIconChoice("stability", "Stability AI", listOf("stable diffusion")),
    LobeHubIconChoice("black-forest-labs", "Black Forest Labs", listOf("flux")),
    LobeHubIconChoice("runway", "Runway"),
    LobeHubIconChoice("elevenlabs", "ElevenLabs"),
    LobeHubIconChoice("voyage", "Voyage AI"),
    LobeHubIconChoice("jina", "Jina AI"),
    LobeHubIconChoice("upstage", "Upstage"),
    LobeHubIconChoice("sambanova", "SambaNova"),
    LobeHubIconChoice("novita", "Novita"),
    LobeHubIconChoice("nebius", "Nebius"),
    LobeHubIconChoice("deepinfra", "DeepInfra"),
    LobeHubIconChoice("aiml", "AI/ML API"),
    LobeHubIconChoice("aionlabs", "AionLabs"),
    LobeHubIconChoice("aistudio", "AI Studio"),
    LobeHubIconChoice("inclusionai", "InclusionAI"),
    LobeHubIconChoice("inflection", "Inflection"),
    LobeHubIconChoice("liquid", "Liquid AI"),
    LobeHubIconChoice("poolside", "Poolside"),
    LobeHubIconChoice("arcee", "Arcee"),
    LobeHubIconChoice("essential", "Essential AI"),
    LobeHubIconChoice("friendli", "Friendli"),
    LobeHubIconChoice("scaleway", "Scaleway"),
    LobeHubIconChoice("spark", "Spark"),
    LobeHubIconChoice("stepfun", "StepFun"),
    LobeHubIconChoice("volcengine", "Volcengine"),
    LobeHubIconChoice("xiaomi", "Xiaomi"),
    LobeHubIconChoice("vercel", "Vercel"),
    LobeHubIconChoice("exa", "Exa"),
    LobeHubIconChoice("tavily", "Tavily"),
    LobeHubIconChoice("firecrawl", "Firecrawl"),
    LobeHubIconChoice("brave", "Brave"),
    LobeHubIconChoice("bing", "Bing"),
)
