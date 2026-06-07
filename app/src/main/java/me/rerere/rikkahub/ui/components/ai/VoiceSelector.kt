package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.tts.provider.TTSProviderSetting
import me.rerere.tts.provider.TTSVoice
import me.rerere.tts.provider.findTtsVoice
import kotlin.uuid.Uuid

@Composable
fun VoiceSelector(
    voiceId: Uuid?,
    providers: List<TTSProviderSetting>,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    onClear: (() -> Unit)? = null,
    onSelect: (TTSVoice) -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    val selected = providers.findTtsVoice(voiceId)
    val scope = rememberCoroutineScope()

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { showSheet = true }, modifier = modifier) {
            Text(
                text = selected?.second?.name?.ifBlank { selected.second.providerVoiceId }
                    ?: "Select voice",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (allowClear && selected != null) {
            IconButton(onClick = { onClear?.invoke() }) {
                Icon(Icons.Rounded.Close, null)
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            sheetGesturesEnabled = false,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = {
                IconButton(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            showSheet = false
                        }
                    }
                ) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null)
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                VoiceList(
                    currentVoice = voiceId,
                    providers = providers.filter { it.voices.isNotEmpty() },
                    onSelect = {
                        onSelect(it)
                        scope.launch {
                            sheetState.hide()
                            showSheet = false
                        }
                    },
                )
            }
        }
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun ColumnScope.VoiceList(
    currentVoice: Uuid?,
    providers: List<TTSProviderSetting>,
    onSelect: (TTSVoice) -> Unit,
) {
    var activeProviderId by remember { mutableStateOf<Uuid?>(null) }
    var searchKeywords by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val lazyListState = rememberLazyListState()
    val providerBadgeListState = rememberLazyListState()
    val density = LocalDensity.current
    val settings = LocalSettings.current
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val coroutineScope = rememberCoroutineScope()
    var viewportHeight by remember { mutableStateOf(0) }

    val providerListItems = remember(providers, searchKeywords) {
        buildList {
            providers.forEach { provider ->
                val filteredVoices = provider.voices.filter { voice ->
                    searchKeywords.isBlank() ||
                        voice.name.contains(searchKeywords, ignoreCase = true) ||
                        voice.providerVoiceId.contains(searchKeywords, ignoreCase = true) ||
                        voice.locale?.contains(searchKeywords, ignoreCase = true) == true ||
                        provider.name.contains(searchKeywords, ignoreCase = true)
                }
                if (filteredVoices.isNotEmpty()) {
                    add(VoiceListItem.Header(provider))
                    filteredVoices.forEachIndexed { index, voice ->
                        add(
                            VoiceListItem.VoiceEntry(
                                voice = voice,
                                provider = provider,
                                position = voiceGroupItemPosition(
                                    index = index,
                                    groupSize = filteredVoices.size,
                                ),
                            )
                        )
                    }
                }
            }
        }
    }

    val selectedVoicePosition = remember(currentVoice, providerListItems) {
        providerListItems.indexOfFirst { item ->
            item is VoiceListItem.VoiceEntry && item.voice.id == currentVoice
        }.coerceAtLeast(0)
    }

    val providerPositions = remember(providers, providerListItems) {
        providers.mapNotNull { provider ->
            val headerIndex = providerListItems.indexOfFirst { item ->
                item is VoiceListItem.Header && item.provider.id == provider.id
            }
            if (headerIndex >= 0) provider.id to headerIndex else null
        }.toMap()
    }

    LaunchedEffect(currentVoice, viewportHeight) {
        if (currentVoice != null && selectedVoicePosition > 0 && viewportHeight > 0) {
            delay(100)
            val itemHeightPx = with(density) { 60.dp.toPx().toInt() }
            val centerOffset = -(viewportHeight / 2) + (itemHeightPx / 2)
            lazyListState.animateScrollToItem(selectedVoicePosition, centerOffset)
        }
    }

    LaunchedEffect(lazyListState, providerPositions) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(100)
            .collect { index ->
                val currentProvider = providerPositions.entries.findLast { index >= it.value }
                val badgeIndex = providers.indexOfFirst { it.id == currentProvider?.key }
                if (badgeIndex >= 0) {
                    providerBadgeListState.animateScrollToItem(badgeIndex)
                } else {
                    providerBadgeListState.requestScrollToItem(0)
                }
            }
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
    ) {
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 84.dp,
                bottom = 100.dp,
            ),
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    viewportHeight = coordinates.size.height
                },
        ) {
            if (providers.isEmpty()) {
                item {
                    Text(
                        text = "No TTS voices",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendColors.gray6,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            items(
                items = providerListItems,
                key = { item ->
                    when (item) {
                        is VoiceListItem.Header -> "voice-provider:${item.provider.id}"
                        is VoiceListItem.VoiceEntry -> "voice:${item.provider.id}:${item.voice.id}"
                    }
                },
            ) { item ->
                when (item) {
                    is VoiceListItem.Header -> VoiceSectionHeader(
                        title = item.provider.name.ifBlank { "TTS Provider" },
                    )
                    is VoiceListItem.VoiceEntry -> VoiceItem(
                        voice = item.voice,
                        provider = item.provider,
                        selected = currentVoice == item.voice.id,
                        position = item.position,
                        onSelect = onSelect,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        val thresholdPx = remember { with(density) { 24.dp.toPx() } }
        val topFadeAlpha by remember {
            derivedStateOf {
                if (lazyListState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (lazyListState.firstVisibleItemScrollOffset / thresholdPx).coerceIn(0f, 1f)
                }
            }
        }

        if (topFadeAlpha > 0f) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = topFadeAlpha }
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    Color.Transparent,
                                )
                            )
                        )
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(84.dp)
        ) {
            val borderOutlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            val searchFieldShape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField
            OutlinedTextField(
                value = searchKeywords,
                onValueChange = { searchKeywords = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp)
                    .border(
                        width = 2.dp,
                        color = borderOutlineColor,
                        shape = searchFieldShape,
                    ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    focusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                ),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search voices") },
                maxLines = 1,
                singleLine = true,
                shape = searchFieldShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.surfaceContainerLow,
                            )
                        )
                    )
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
        }

        if (providers.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    state = providerBadgeListState,
                ) {
                    items(providers) { provider ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.85f else 1f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                            label = "voice_provider_badge_scale",
                        )
                        val isActive = activeProviderId == provider.id
                        Surface(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                activeProviderId = provider.id
                                val position = providerPositions[provider.id] ?: 0
                                coroutineScope.launch {
                                    lazyListState.animateScrollToItem(position)
                                }
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = (if (isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }).copy(alpha = 0.2f),
                            ),
                            interactionSource = interactionSource,
                            shape = me.rerere.rikkahub.ui.theme.AppShapes.ButtonPill,
                            color = if (isActive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            contentColor = if (isActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        ) {
                            Text(
                                text = provider.name.ifBlank { "TTS" },
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class VoiceItemPosition {
    FIRST,
    MIDDLE,
    LAST,
    SINGLE,
}

private sealed class VoiceListItem {
    data class Header(val provider: TTSProviderSetting) : VoiceListItem()
    data class VoiceEntry(
        val voice: TTSVoice,
        val provider: TTSProviderSetting,
        val position: VoiceItemPosition,
    ) : VoiceListItem()
}

private data class VoiceItemCornerRadii(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
)

private fun voiceGroupItemPosition(index: Int, groupSize: Int): VoiceItemPosition = when {
    groupSize <= 1 -> VoiceItemPosition.SINGLE
    index == 0 -> VoiceItemPosition.FIRST
    index == groupSize - 1 -> VoiceItemPosition.LAST
    else -> VoiceItemPosition.MIDDLE
}

private fun groupedVoiceItemCornerRadii(
    selected: Boolean,
    position: VoiceItemPosition,
): VoiceItemCornerRadii {
    if (selected) {
        return VoiceItemCornerRadii(50.dp, 50.dp, 50.dp, 50.dp)
    }
    return when (position) {
        VoiceItemPosition.FIRST -> VoiceItemCornerRadii(24.dp, 24.dp, 10.dp, 10.dp)
        VoiceItemPosition.MIDDLE -> VoiceItemCornerRadii(10.dp, 10.dp, 10.dp, 10.dp)
        VoiceItemPosition.LAST -> VoiceItemCornerRadii(10.dp, 10.dp, 24.dp, 24.dp)
        VoiceItemPosition.SINGLE -> VoiceItemCornerRadii(24.dp, 24.dp, 24.dp, 24.dp)
    }
}

@Composable
private fun rememberAnimatedGroupedVoiceItemShape(
    selected: Boolean,
    position: VoiceItemPosition,
): RoundedCornerShape {
    val target = remember(selected, position) {
        groupedVoiceItemCornerRadii(selected = selected, position = position)
    }
    val topStart by animateDpAsState(target.topStart, spring(dampingRatio = 0.5f, stiffness = 400f), label = "voice_top_start")
    val topEnd by animateDpAsState(target.topEnd, spring(dampingRatio = 0.5f, stiffness = 400f), label = "voice_top_end")
    val bottomStart by animateDpAsState(target.bottomStart, spring(dampingRatio = 0.5f, stiffness = 400f), label = "voice_bottom_start")
    val bottomEnd by animateDpAsState(target.bottomEnd, spring(dampingRatio = 0.5f, stiffness = 400f), label = "voice_bottom_end")
    return RoundedCornerShape(topStart, topEnd, bottomStart, bottomEnd)
}

@Composable
private fun VoiceSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 12.dp,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.weight(1f))
        trailingContent()
    }
}

@Composable
private fun VoiceItem(
    voice: TTSVoice,
    provider: TTSProviderSetting,
    selected: Boolean,
    position: VoiceItemPosition,
    onSelect: (TTSVoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = rememberAnimatedGroupedVoiceItemShape(selected = selected, position = position)
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else if (LocalDarkMode.current) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .combinedClickable(
                enabled = true,
                onClick = { onSelect(voice) },
                interactionSource = interactionSource,
                indication = LocalIndication.current,
            )
            .padding(vertical = 12.dp, horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = voice.name.ifBlank { voice.providerVoiceId.ifBlank { "Voice" } },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor,
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val locale = voice.locale
                if (!locale.isNullOrBlank()) {
                    Tag(type = TagType.INFO) { Text(locale) }
                }
                Tag(type = TagType.SUCCESS) { Text("x${"%.2f".format(voice.speed)}") }
                Tag(type = TagType.WARNING) { Text("p${"%.2f".format(voice.pitch)}") }
                if (provider !is TTSProviderSetting.SystemTTS) {
                    Tag { Text(provider.name.ifBlank { "TTS" }) }
                }
            }
        }
    }
}
