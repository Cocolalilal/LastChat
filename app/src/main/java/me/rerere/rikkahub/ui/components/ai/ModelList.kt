package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.ViewModule
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.rikkahub.ui.components.ui.ProviderIcon
import me.rerere.rikkahub.ui.components.ui.ModelIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.icons.HeartIcon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.toDp
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@Composable
fun ModelSelector(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    allowClear: Boolean = false,
    onSelect: (Model) -> Unit
) {
    var popup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val model = providers.findModelById(modelId ?: Uuid.random())

    if (!onlyIcon) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    popup = true
                },
                modifier = modifier
            ) {
                model?.let { m ->
                    val provider = m.findProvider(providers = providers)
                    ModelIcon(
                        model = m,
                        provider = provider,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(36.dp)
                    )
                }
                Text(
                    text = model?.displayName ?: stringResource(R.string.model_list_select_model),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (allowClear && model != null) {
                IconButton(
                    onClick = {
                        onSelect(Model())
                    }
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear"
                    )
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .clickable { popup = true },
            contentAlignment = Alignment.Center
        ) {
            if (model != null) {
                val provider = model.findProvider(providers = providers)
                ModelIcon(
                    model = model,
                    provider = provider,
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                )
            } else {
                Icon(
                    Icons.Rounded.ViewModule,
                    contentDescription = stringResource(R.string.setting_model_page_chat_model),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (popup) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                popup = false
            },
            sheetState = state,
            sheetGesturesEnabled = false,
            dragHandle = {
                IconButton(
                    onClick = {
                        scope.launch {
                            state.hide()
                            popup = false
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
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val filteredProviderSettings = providers.fastFilter {
                    it.enabled && it.models.fastAny { model -> model.type == type }
                }
                ModelList(
                    currentModel = modelId,
                    providers = filteredProviderSettings,
                    modelType = type,
                    onSelect = {
                        onSelect(it)
                        scope.launch {
                            state.hide()
                            popup = false
                        }
                    },
                    onDismiss = {
                        scope.launch {
                            state.hide()
                            popup = false
                        }
                    }
                )
            }
        }
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
internal fun ColumnScope.ModelList(
    currentModel: Uuid? = null,
    providers: List<ProviderSetting>,
    modelType: ModelType,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = koinInject<SettingsStore>()
    val settings = settingsStore.settingsFlow
        .collectAsStateWithLifecycle()

    val favoriteModels = settings.value.favoriteModels.mapNotNull { modelId ->
        val model = settings.value.providers.findModelById(modelId) ?: return@mapNotNull null
        if (model.type != modelType) return@mapNotNull null
        val provider = model.findProvider(providers = settings.value.providers, checkOverwrite = false) ?: return@mapNotNull null
        model to provider
    }

    var searchKeywords by remember { mutableStateOf("") }

    // Build a flat list of items for the LazyColumn - this enables precise scrolling to any model
    // Structure: [provider header, model, model, ...] for each provider
    
    val providerListItems = remember(providers, modelType, searchKeywords, settings.value.favoriteModels) {
        buildList {
            providers.forEach { providerSetting ->
                val filteredModels = providerSetting.models.fastFilter {
                    it.type == modelType && it.displayName.contains(searchKeywords, true)
                }
                
                // Add provider header
                add(ProviderListItem.Header(providerSetting))
                
                // Add each model as individual item
                filteredModels.forEachIndexed { index, model ->
                    val itemPosition = when {
                        filteredModels.size == 1 -> ModelItemPosition.SINGLE
                        index == 0 -> ModelItemPosition.FIRST
                        index == filteredModels.size - 1 -> ModelItemPosition.LAST
                        else -> ModelItemPosition.MIDDLE
                    }
                    add(ProviderListItem.ModelEntry(
                        model = model,
                        provider = providerSetting,
                        position = itemPosition,
                        isFavorite = settings.value.favoriteModels.contains(model.id)
                    ))
                }
            }
        }
    }
    
    // Calculate position of selected model in the flat list
    val selectedModelPosition = remember(currentModel, favoriteModels, providerListItems) {
        if (currentModel == null) return@remember 0

        var position = 0

        // Skip no-providers placeholder
        if (providers.isEmpty()) {
            position += 1
        }

        // Check if in favorites list - favorites are individual items
        val favoriteIndex = favoriteModels.indexOfFirst { it.first.id == currentModel }
        if (favoriteIndex >= 0) {
            if (favoriteModels.isNotEmpty()) {
                position += 1 // favorite sticky header
            }
            position += favoriteIndex
            return@remember position
        }

        // Skip all favorites
        if (favoriteModels.isNotEmpty()) {
            position += 1 // favorite sticky header
            position += favoriteModels.size
        }

        // Find the model in the flat provider list
        val modelIndexInProviderList = providerListItems.indexOfFirst { item ->
            item is ProviderListItem.ModelEntry && item.model.id == currentModel
        }
        if (modelIndexInProviderList >= 0) {
            return@remember position + modelIndexInProviderList
        }

        0
    }

    // List state for scrolling
    val lazyListState = rememberLazyListState()
    
    // Get viewport height for centering calculation
    val density = androidx.compose.ui.platform.LocalDensity.current
    var viewportHeight by remember { mutableStateOf(0) }
    
    // Scroll to selected model centered on first composition
    LaunchedEffect(currentModel, viewportHeight) {
        if (currentModel != null && selectedModelPosition > 0 && viewportHeight > 0) {
            // Small delay to ensure list is composed
            delay(100)
            // Scroll with negative offset to center the item (approximate item height ~60dp)
            val itemHeightPx = with(density) { 60.dp.toPx().toInt() }
            val centerOffset = -(viewportHeight / 2) + (itemHeightPx / 2)
            lazyListState.animateScrollToItem(selectedModelPosition, centerOffset)
        }
    }
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 计算favorite models在列表中的位置偏移
        var favoriteStartIndex = 0
        if (providers.isEmpty()) {
            favoriteStartIndex = 1 // no providers item
        }
        if (favoriteModels.isNotEmpty()) {
            favoriteStartIndex += 1 // favorite header
        }

        val fromIndex = from.index - favoriteStartIndex
        val toIndex = to.index - favoriteStartIndex

        // 只处理favorite models范围内的拖拽
        if (fromIndex >= 0 && toIndex >= 0 &&
            fromIndex < favoriteModels.size && toIndex < favoriteModels.size
        ) {
            val newFavoriteModels = settings.value.favoriteModels.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            coroutineScope.launch {
                settingsStore.update { oldSettings ->
                    oldSettings.copy(favoriteModels = newFavoriteModels)
                }
            }
        }
    }
    val haptics = rememberPremiumHaptics(enabled = settings.value.displaySetting.enableUIHaptics)

    // Calculate the LazyColumn item index for each provider header
    val providerPositions = remember(providers, favoriteModels, providerListItems) {
        var baseIndex = 0
        if (providers.isEmpty()) {
            baseIndex = 1 // no providers item takes index 0
        }
        if (favoriteModels.isNotEmpty()) {
            baseIndex += 1 // favorite header
            baseIndex += favoriteModels.size // each favorite model is one item
        }

        // Find each provider header's position in the flat list
        providers.mapNotNull { provider ->
            val headerIndex = providerListItems.indexOfFirst { item ->
                item is ProviderListItem.Header && item.provider.id == provider.id
            }
            if (headerIndex >= 0) {
                provider.id to (baseIndex + headerIndex)
            } else null
        }.toMap()
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    OutlinedTextField(
        value = searchKeywords,
        onValueChange = { searchKeywords = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        leadingIcon = {
            Icon(Icons.Rounded.Search, null)
        },
        placeholder = {
            Text(stringResource(R.string.model_list_search_placeholder))
        },
        maxLines = 1,
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = androidx.compose.ui.text.input.ImeAction.Done
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onDone = {
                focusManager.clearFocus()
            }
        )
    )


    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                viewportHeight = coordinates.size.height
            }
    ) {
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (providers.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.model_list_no_providers),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendColors.gray6,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (favoriteModels.isNotEmpty()) {
                stickyHeader {
                    Text(
                        text = stringResource(R.string.model_list_favorite),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(bottom = 4.dp, top = 8.dp)
                    )
                }

                items(
                    items = favoriteModels,
                    key = { "favorite:" + it.first.id.toString() }
                ) { (model, provider) ->
                    ReorderableItem(
                        state = reorderableState,
                        key = "favorite:" + model.id.toString()
                    ) { isDragging ->
                        ModelItem(
                            model = model,
                            onSelect = onSelect,
                            modifier = Modifier
                                .scale(if (isDragging) 0.95f else 1f)
                                .animateItem(),
                            providerSetting = provider,
                            select = model.id == currentModel,
                            onDismiss = {
                                onDismiss()
                            },
                            tail = {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            settingsStore.update { settings ->
                                                settings.copy(
                                                    favoriteModels = settings.favoriteModels.filter { it != model.id }
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        HeartIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            dragHandle = {
                                Icon(
                                    imageVector = Icons.Rounded.DragIndicator,
                                    contentDescription = null,
                                    modifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            haptics.perform(HapticPattern.DragStart)
                                        },
                                        onDragStopped = {
                                            haptics.perform(HapticPattern.DragEnd)
                                        }
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // Render flattened provider items - each model is its own item
            items(
                items = providerListItems,
                key = { item ->
                    when (item) {
                        is ProviderListItem.Header -> "provider-header:${item.provider.id}"
                        is ProviderListItem.ModelEntry -> "provider-model:${item.provider.id}:${item.model.id}"
                    }
                }
            ) { item ->
                when (item) {
                    is ProviderListItem.Header -> {
                        Row(
                            modifier = Modifier
                                .padding(bottom = 4.dp, top = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.provider.name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            ProviderBalanceText(
                                providerSetting = item.provider,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    is ProviderListItem.ModelEntry -> {
                        ModelItem(
                            model = item.model,
                            onSelect = onSelect,
                            modifier = Modifier.animateItem(),
                            providerSetting = item.provider,
                            select = currentModel == item.model.id,
                            inGroup = true,
                            position = item.position,
                            onDismiss = {
                                onDismiss()
                            },
                            tail = {
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            settingsStore.update { settings ->
                                                if (item.isFavorite) {
                                                    settings.copy(
                                                        favoriteModels = settings.favoriteModels.filter { it != item.model.id }
                                                    )

                                                } else {
                                                    settings.copy(
                                                        favoriteModels = settings.favoriteModels + item.model.id
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    if (item.isFavorite) {
                                        Icon(
                                            HeartIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.FavoriteBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 供应商Badge行
    val providerBadgeListState = rememberLazyListState()
    LaunchedEffect(lazyListState) {
        // 当LazyColumn滚动时，LazyRow也跟随滚动
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(100) // 防抖处理
            .collect { index ->
                if (index > 0) {
                    val currentProvider = providerPositions.entries.findLast {
                        index > it.value
                    }
                    val index = providers.indexOfFirst { it.id == currentProvider?.key }
                    if (index >= 0) {
                        providerBadgeListState.animateScrollToItem(index)
                    } else {
                        providerBadgeListState.requestScrollToItem(0)
                    }
                } else {
                    providerBadgeListState.requestScrollToItem(0)
                }
            }
    }
    if (providers.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            state = providerBadgeListState
        ) {
            items(providers) { provider ->
                AssistChip(
                    onClick = {
                        val position = providerPositions[provider.id] ?: 0
                        coroutineScope.launch {
                            lazyListState.animateScrollToItem(position)
                        }
                    },
                    label = {
                        Text(provider.name)
                    },
                    leadingIcon = {
                        ProviderIcon(
                            provider = provider,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                )
            }
        }
    }
}

// Position in a group for determining corner radius
private enum class ModelItemPosition {
    FIRST,   // Top rounded (24dp top, 10dp bottom)
    MIDDLE,  // All corners 10dp
    LAST,    // Bottom rounded (10dp top, 24dp bottom)
    SINGLE   // All corners 24dp (only item in group)
}

// Sealed class for flattened provider list items (enables precise scrolling)
private sealed class ProviderListItem {
    data class Header(val provider: ProviderSetting) : ProviderListItem()
    data class ModelEntry(
        val model: Model,
        val provider: ProviderSetting,
        val position: ModelItemPosition,
        val isFavorite: Boolean
    ) : ProviderListItem()
}
@Composable
private fun ModelItem(
    model: Model,
    providerSetting: ProviderSetting,
    select: Boolean,
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    tail: @Composable RowScope.() -> Unit = {},
    dragHandle: @Composable (RowScope.() -> Unit)? = null,
    inGroup: Boolean = false,
    position: ModelItemPosition = ModelItemPosition.SINGLE
) {
    val navController = LocalNavController.current
    val interactionSource = remember { MutableInteractionSource() }
    
    // Calculate shape based on position - edges get 24dp, connections get 10dp
    val itemShape = if (select) {
        RoundedCornerShape(50.dp)  // Selected items are fully round
    } else {
        when (position) {
            ModelItemPosition.FIRST -> RoundedCornerShape(
                topStart = 24.dp, topEnd = 24.dp,
                bottomStart = 10.dp, bottomEnd = 10.dp
            )
            ModelItemPosition.MIDDLE -> RoundedCornerShape(10.dp)
            ModelItemPosition.LAST -> RoundedCornerShape(
                topStart = 10.dp, topEnd = 10.dp,
                bottomStart = 24.dp, bottomEnd = 24.dp
            )
            ModelItemPosition.SINGLE -> RoundedCornerShape(24.dp)
        }
    }
    
    if(inGroup) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxWidth()
                .clip(itemShape)
                .background(
                    color = if (select) MaterialTheme.colorScheme.primaryContainer else if (LocalDarkMode.current) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        enabled = true,
                        onLongClick = {
                            onDismiss()
                            navController.navigate(
                                Screen.SettingProviderDetail(
                                    providerSetting.id.toString()
                                )
                            )
                        },
                        onClick = { onSelect(model) },
                        interactionSource = interactionSource,
                        indication = LocalIndication.current
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ModelIcon(
                    model = model,
                    provider = providerSetting,
                    modifier = Modifier.size(32.dp),
                    color = Color.Transparent,
                    contentColor = if (select) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (select) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ModelTypeTag(model = model)

                        ModelModalityTag(model = model)

                        ModelAbilityTag(model = model)
                    }
                }
                tail()
            }
            dragHandle?.let { it() }
        }
    } else {
        Card(
            modifier = modifier,
            shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
            colors = CardDefaults.cardColors(
                containerColor = if (select) MaterialTheme.colorScheme.primaryContainer else if (LocalDarkMode.current) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (select) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            enabled = true,
                            onLongClick = {
                                onDismiss()
                                navController.navigate(
                                    Screen.SettingProviderDetail(
                                        providerSetting.id.toString()
                                    )
                                )
                            },
                            onClick = { onSelect(model) },
                            interactionSource = interactionSource,
                            indication = LocalIndication.current
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ModelIcon(
                        model = model,
                        provider = providerSetting,
                        modifier = Modifier.size(32.dp),
                        color = Color.Transparent,
                        contentColor = Color.White
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ModelTypeTag(model = model)

                            ModelModalityTag(model = model)

                            ModelAbilityTag(model = model)
                        }
                    }
                    tail()
                }
                dragHandle?.let { it() }
            }
        }
    }
}

@Composable
fun ModelTypeTag(model: Model) {
    Tag(
        type = TagType.INFO
    ) {
        Text(
            text = stringResource(
                when (model.type) {
                    ModelType.CHAT -> R.string.setting_provider_page_chat_model
                    ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                    ModelType.IMAGE -> R.string.setting_provider_page_image_model
                }
            )
        )
    }
}

@Composable
fun ModelModalityTag(model: Model) {
    Tag(
        type = TagType.SUCCESS
    ) {
        model.inputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> Icons.Rounded.Title
                    Modality.IMAGE -> Icons.Rounded.Image
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp)
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp())
        )
        model.outputModalities.fastForEach { modality ->
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> Icons.Rounded.Title
                    Modality.IMAGE -> Icons.Rounded.Image
                },
                contentDescription = null,
                modifier = Modifier
                    .size(LocalTextStyle.current.lineHeight.toDp())
                    .padding(1.dp)
            )
        }
    }
}

@Composable
fun ModelAbilityTag(model: Model) {
    model.abilities.fastForEach { ability ->
        when (ability) {
            ModelAbility.TOOL -> {
                Tag(
                    type = TagType.WARNING
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Build,
                        contentDescription = null,
                        modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp())
                    )
                }
            }

            ModelAbility.REASONING -> {
                Tag(
                    type = TagType.INFO
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lightbulb,
                        contentDescription = null,
                        modifier = Modifier.size(LocalTextStyle.current.lineHeight.toDp()),
                    )
                }
            }
        }
    }
}
