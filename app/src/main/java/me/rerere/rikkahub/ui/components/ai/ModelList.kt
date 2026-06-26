package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.Dp
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
import androidx.compose.material.icons.rounded.Mic
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.height


@Composable
fun ModelSelector(
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    type: ModelType,
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    allowClear: Boolean = false,
    modelFilter: (Model) -> Boolean = { true },
    onClear: (() -> Unit)? = null,
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
                        onClear?.invoke() ?: onSelect(Model())
                    }
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_search)
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
containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
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
                val modelMetadataResolver = org.koin.compose.koinInject<me.rerere.rikkahub.data.ai.models.ModelMetadataResolver>()
                val filteredProviderSettings = providers.fastFilter {
                    it.enabled && it.models.fastAny { model ->
                        val isTypeMatch = if (type == ModelType.STT) {
                            val resolvedModel = modelMetadataResolver.applyToModel(model, it)
                            resolvedModel.type == ModelType.STT
                        } else {
                            model.type == type
                        }
                        isTypeMatch && modelFilter(model)
                    }
                }
                ModelList(
                    currentModel = modelId,
                    providers = filteredProviderSettings,
                    modelType = type,
                    modelFilter = modelFilter,
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
    modelFilter: (Model) -> Boolean = { true },
    onSelect: (Model) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val settingsStore = koinInject<SettingsStore>()
    val settings = settingsStore.settingsFlow
        .collectAsStateWithLifecycle()
    val modelMetadataResolver = org.koin.compose.koinInject<me.rerere.rikkahub.data.ai.models.ModelMetadataResolver>()
    
    var activeProviderId by remember { mutableStateOf<Uuid?>(null) }
    
    // activeProviderId is null initially and is set upon explicit user click

    val favoriteModels = settings.value.favoriteModels.mapNotNull { modelId ->
        val model = providers.findModelById(modelId) ?: return@mapNotNull null
        val provider = model.findProvider(providers = providers, checkOverwrite = false) ?: return@mapNotNull null
        val isTypeMatch = if (modelType == ModelType.STT) {
            val resolvedModel = modelMetadataResolver.applyToModel(model, provider)
            resolvedModel.type == ModelType.STT
        } else {
            model.type == modelType
        }
        if (!isTypeMatch || !modelFilter(model)) return@mapNotNull null
        model to provider
    }
    val favoriteListItems = remember(favoriteModels) {
        favoriteModels.mapIndexed { index, (model, provider) ->
            FavoriteListItem(
                model = model,
                provider = provider,
                position = groupItemPosition(index = index, groupSize = favoriteModels.size)
            )
        }
    }

    var searchKeywords by remember { mutableStateOf("") }

    // Build a flat list of items for the LazyColumn - this enables precise scrolling to any model
    // Structure: [provider header, model, model, ...] for each provider
    
    val providerListItems = remember(providers, modelType, searchKeywords, settings.value.favoriteModels, modelFilter) {
        buildList {
            providers.forEach { providerSetting ->
                val filteredModels = providerSetting.models.fastFilter { model ->
                    val isTypeMatch = if (modelType == ModelType.STT) {
                        val resolvedModel = modelMetadataResolver.applyToModel(model, providerSetting)
                        resolvedModel.type == ModelType.STT
                    } else {
                        model.type == modelType
                    }
                    isTypeMatch &&
                        modelFilter(model) &&
                        model.displayName.contains(searchKeywords, true)
                }
                
                // Add provider header
                add(ProviderListItem.Header(providerSetting))
                
                // Add each model as individual item
                filteredModels.forEachIndexed { index, model ->
                    add(ProviderListItem.ModelEntry(
                        model = model,
                        provider = providerSetting,
                        position = groupItemPosition(index = index, groupSize = filteredModels.size),
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
                position += 1 // favorite header
            }
            position += favoriteIndex
            return@remember position
        }

        // Skip all favorites
        if (favoriteModels.isNotEmpty()) {
            position += 1 // favorite header
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
    
    // 供应商Badge行
    val providerBadgeListState = rememberLazyListState()
    LaunchedEffect(lazyListState) {
        // 当LazyColumn滚动时，LazyRow也跟随滚动，但不会改变已选中的provider id
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(100) // 防抖处理
            .collect { index ->
                if (index > 0) {
                    val currentProvider = providerPositions.entries.findLast {
                        index >= it.value
                    }
                    val currentProviderId = currentProvider?.key
                    
                    val badgeIndex = providers.indexOfFirst { it.id == currentProviderId }
                    if (badgeIndex >= 0) {
                        providerBadgeListState.animateScrollToItem(badgeIndex)
                    } else {
                        providerBadgeListState.requestScrollToItem(0)
                    }
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
            // 1. Scrollable List of Models
            LazyColumn(
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 84.dp, // Leave space for top floating search bar
                    bottom = 100.dp // Leave space for bottom floating provider options
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
                            text = stringResource(R.string.model_list_no_providers),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendColors.gray6,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (favoriteModels.isNotEmpty()) {
                    item(key = "favorite-header") {
                        ModelSectionHeader(
                            title = stringResource(R.string.model_list_favorite),
                            topPadding = 8.dp
                        )
                    }

                    items(
                        items = favoriteListItems,
                        key = { "favorite:" + it.model.id.toString() }
                    ) { favoriteItem ->
                        ReorderableItem(
                            state = reorderableState,
                            key = "favorite:" + favoriteItem.model.id.toString()
                        ) { isDragging ->
                            val dragScale by animateFloatAsState(
                                targetValue = if (isDragging) 0.95f else 1f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                                label = "favorite_drag_scale"
                            )
                            ModelItem(
                                model = favoriteItem.model,
                                onSelect = onSelect,
                                modifier = Modifier
                                    .scale(dragScale)
                                    .animateItem(),
                                providerSetting = favoriteItem.provider,
                                select = favoriteItem.model.id == currentModel,
                                inGroup = true,
                                position = favoriteItem.position,
                                onDismiss = {
                                    onDismiss()
                                },
                                tail = {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                settingsStore.update { settings ->
                                                    settings.copy(
                                                        favoriteModels = settings.favoriteModels.filter { it != favoriteItem.model.id }
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
                            ModelSectionHeader(
                                title = item.provider.name
                            ) {
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

            // 2. Strong Top Fade and Floating Search Bar Background (fades in/out depending on scroll position)
            val density = androidx.compose.ui.platform.LocalDensity.current
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

            // Top fade overlay (combines a solid background and a vertical gradient below it)
            if (topFadeAlpha > 0f) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = topFadeAlpha }
                ) {
                    // Solid background covering the top half of the search bar area
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    )
                    // Strong vertical gradient fade starting from the middle of the search bar area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }

            // Search field (always fully opaque, container color matches the sheet background surfaceContainerLow, outline border is onSurfaceVariant and thicker)
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
                            shape = searchFieldShape
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
                        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurface
                    ),
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null)
                    },
                    placeholder = {
                        Text(stringResource(R.string.model_list_search_placeholder))
                    },
                    maxLines = 1,
                    singleLine = true,
                    shape = searchFieldShape,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                        }
                    )
                )
            }

            // 3. Strong Bottom Floating Fade (Gradient + Solid background covering provider badges area)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                // Gradient part (from transparent to solid surfaceContainerLow)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                )
                            )
                        )
                )
                // Solid part (completely covering the provider pills area)
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }

            // 4. Bottom floating provider options row
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
                        state = providerBadgeListState
                    ) {
                        items(providers) { provider ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val scale by animateFloatAsState(
                                targetValue = if (isPressed) 0.85f else 1f,
                                animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
                                label = "provider_badge_scale"
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
                                    color = (if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface).copy(alpha = 0.2f)
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
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ProviderIcon(
                                        provider = provider,
                                        modifier = Modifier.size(22.dp),
                                        contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
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

private data class FavoriteListItem(
    val model: Model,
    val provider: ProviderSetting,
    val position: ModelItemPosition
)

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

private fun groupItemPosition(index: Int, groupSize: Int): ModelItemPosition = when {
    groupSize <= 1 -> ModelItemPosition.SINGLE
    index == 0 -> ModelItemPosition.FIRST
    index == groupSize - 1 -> ModelItemPosition.LAST
    else -> ModelItemPosition.MIDDLE
}

private data class ModelItemCornerRadii(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp
)

private fun groupedModelItemCornerRadii(
    select: Boolean,
    position: ModelItemPosition
): ModelItemCornerRadii {
    if (select) {
        return ModelItemCornerRadii(
            topStart = 50.dp,
            topEnd = 50.dp,
            bottomStart = 50.dp,
            bottomEnd = 50.dp
        )
    }

    return when (position) {
        ModelItemPosition.FIRST -> ModelItemCornerRadii(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 10.dp,
            bottomEnd = 10.dp
        )
        ModelItemPosition.MIDDLE -> ModelItemCornerRadii(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomStart = 10.dp,
            bottomEnd = 10.dp
        )
        ModelItemPosition.LAST -> ModelItemCornerRadii(
            topStart = 10.dp,
            topEnd = 10.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )
        ModelItemPosition.SINGLE -> ModelItemCornerRadii(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 24.dp,
            bottomEnd = 24.dp
        )
    }
}

@Composable
private fun rememberAnimatedGroupedModelItemShape(
    select: Boolean,
    position: ModelItemPosition
): RoundedCornerShape {
    val targetCornerRadii = remember(select, position) {
        groupedModelItemCornerRadii(select = select, position = position)
    }
    val topStart by animateDpAsState(
        targetValue = targetCornerRadii.topStart,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "model_item_top_start"
    )
    val topEnd by animateDpAsState(
        targetValue = targetCornerRadii.topEnd,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "model_item_top_end"
    )
    val bottomStart by animateDpAsState(
        targetValue = targetCornerRadii.bottomStart,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "model_item_bottom_start"
    )
    val bottomEnd by animateDpAsState(
        targetValue = targetCornerRadii.bottomEnd,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "model_item_bottom_end"
    )

    return RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd
    )
}

@Composable
private fun ModelSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    topPadding: Dp = 12.dp,
    trailingContent: @Composable RowScope.() -> Unit = {}
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
    val groupedItemShape = rememberAnimatedGroupedModelItemShape(
        select = select,
        position = position
    )

    if(inGroup) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
                .fillMaxWidth()
                .clip(groupedItemShape)
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
                    ModelType.STT -> R.string.setting_provider_page_stt_model
                }
            )
        )
    }
}

@Composable
fun ModelModalityTag(model: Model) {
    if (model.type == ModelType.STT) return
    
    Tag(
        type = TagType.SUCCESS
    ) {
        model.inputModalities.fastForEach { modality ->
            if (modality == Modality.AUDIO) return@fastForEach
            Icon(
                imageVector = when (modality) {
                    Modality.TEXT -> Icons.Rounded.Title
                    Modality.IMAGE -> Icons.Rounded.Image
                    Modality.AUDIO -> Icons.Rounded.Mic
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
                    Modality.AUDIO -> Icons.Rounded.Mic
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
