package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import me.rerere.rikkahub.ui.components.ui.ToastType
import me.rerere.rikkahub.ui.context.LocalToaster
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.ai.registry.ModelRegistry
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.setting.SearchAbilityTagLine
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import me.rerere.rikkahub.ui.hooks.rememberAmoledDarkMode
import org.koin.compose.koinInject

import androidx.compose.ui.graphics.Shape

@Composable
fun SearchPickerButton(
    enableSearch: Boolean,
    settings: Settings,
    modifier: Modifier = Modifier,
    shape: Shape = me.rerere.rikkahub.ui.theme.AppShapes.CardMedium,
    onToggleSearch: (Boolean) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    model: Model?,
    selectedProviderIndex: Int = -1, // -1 means use global setting, otherwise use this index
    isBuiltInMode: Boolean = false, // true when assistant's searchMode is BuiltIn
    preferBuiltInSearch: Boolean = false, // true when prefer built-in search toggle is ON
    onTogglePreferBuiltInSearch: (Boolean) -> Unit = {}, // callback to update assistant.preferBuiltInSearch
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onlyIcon: Boolean = false
) {
    val toaster = LocalToaster.current
    var showSearchPicker by remember { mutableStateOf(false) }
    
    // Track the last valid provider index locally to persist across on/off toggles
    // This prevents "jumping" when toggling search off and back on
    var lastValidProviderIndex by remember { mutableStateOf(
        if (selectedProviderIndex >= 0) selectedProviderIndex 
        else settings.searchServiceSelected.coerceIn(0, (settings.searchServices.size - 1).coerceAtLeast(0))
    ) }
    
    // Update lastValidProviderIndex when a valid external index is provided
    LaunchedEffect(selectedProviderIndex) {
        if (selectedProviderIndex >= 0 && selectedProviderIndex < settings.searchServices.size) {
            lastValidProviderIndex = selectedProviderIndex
        }
    }
    
    // Use the external index if valid, otherwise use our tracked last valid index
    val effectiveProviderIndex = if (selectedProviderIndex >= 0 && selectedProviderIndex < settings.searchServices.size) {
        selectedProviderIndex
    } else {
        lastValidProviderIndex.coerceIn(0, (settings.searchServices.size - 1).coerceAtLeast(0))
    }
    val currentService = settings.searchServices.getOrNull(effectiveProviderIndex)

    ToggleSurface(
        modifier = modifier,
        checked = enableSearch || model?.tools?.contains(BuiltInTools.Search) == true,
        checkedColor = Color.Transparent,
        uncheckedColor = Color.Transparent,
        contentColor = contentColor,
        onClick = {
            showSearchPicker = true
        }
    ) {
        Row(
            modifier = Modifier
                .padding(if (onlyIcon) 8.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                // Determine if built-in search is effectively active
                val modelSupportsBuiltIn = model != null && me.rerere.ai.registry.ModelRegistry.GEMINI_SERIES.match(model.modelId)
                val isUsingBuiltIn = preferBuiltInSearch && modelSupportsBuiltIn
                
                // Show globe icon when: using built-in search, or no provider selected, or search is off
                // Show provider icon only when: search is on, NOT using built-in, and has a provider
                if (enableSearch && !isUsingBuiltIn && currentService != null) {
                    AutoAIIcon(
                        name = SearchServiceOptions.TYPES[currentService::class] ?: "Search",
                        color = Color.Transparent
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = stringResource(R.string.use_web_search),
                    )
                }
            }
        }
    }

    if (showSearchPicker) {
        ModalBottomSheet(
            onDismissRequest = { showSearchPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 16.dp), // Extra padding at bottom for navigation bar
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.search_picker_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                SearchPicker(
                    enableSearch = enableSearch,
                    settings = settings,
                    onToggleSearch = { enabled ->
                        if (enabled) {
                            // When turning on, also set the provider to the last known valid index
                            onUpdateSearchService(effectiveProviderIndex)
                        }
                        onToggleSearch(enabled)
                    },
                    onUpdateSearchService = { index ->
                        lastValidProviderIndex = index
                        onUpdateSearchService(index)
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    model = model,
                    selectedProviderIndex = effectiveProviderIndex,
                    preferBuiltInSearch = preferBuiltInSearch,
                    onTogglePreferBuiltInSearch = onTogglePreferBuiltInSearch,
                    onDismiss = {
                        showSearchPicker = false
                    }
                )
            }
        }
    }
}


@Composable
internal fun SearchPicker(
    enableSearch: Boolean,
    settings: Settings,
    model: Model?,
    modifier: Modifier = Modifier,
    onToggleSearch: (Boolean) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    selectedProviderIndex: Int = -1,
    preferBuiltInSearch: Boolean = false,
    onTogglePreferBuiltInSearch: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    val navBackStack = LocalNavController.current

    // 模型内置搜索 (only show if model supports it)
    if (model != null && ModelRegistry.GEMINI_SERIES.match(model.modelId)) {
        BuiltInSearchSetting(
            preferBuiltInSearch = preferBuiltInSearch,
            onTogglePreferBuiltInSearch = onTogglePreferBuiltInSearch
        )
    }

    // 显示搜索服务选择 (always show, but selection only applies when not using built-in)
    AppSearchSettings(
        enableSearch = enableSearch,
        onDismiss = onDismiss,
        navBackStack = navBackStack,
        onToggleSearch = onToggleSearch,
        modifier = modifier,
        settings = settings,
        selectedProviderIndex = selectedProviderIndex,
        onUpdateSearchService = onUpdateSearchService
    )
}

@Composable
private fun AppSearchSettings(
    enableSearch: Boolean,
    onDismiss: () -> Unit,
    navBackStack: NavHostController,
    onToggleSearch: (Boolean) -> Unit,
    modifier: Modifier,
    settings: Settings,
    selectedProviderIndex: Int = -1,
    onUpdateSearchService: (Int) -> Unit
) {
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    
    val numProviders = settings.searchServices.size
    
    // Calculate total items for position-based corners
    // Items: Web Search Toggle + all search providers
    val totalItems = 1 + numProviders
    
    // Position-based corner shape calculator
    fun getItemShape(index: Int, totalCount: Int, isSelected: Boolean): RoundedCornerShape {
        if (isSelected) return RoundedCornerShape(50) // Selected = fully round
        return when {
            totalCount == 1 -> RoundedCornerShape(24.dp) // Single item
            index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
            index == totalCount - 1 -> RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            else -> RoundedCornerShape(10.dp)
        }
    }
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // If no providers, just show the toggle
        if (numProviders == 0) {
            SearchToggleItem(
                enableSearch = enableSearch,
                onToggleSearch = onToggleSearch,
                onDismiss = onDismiss,
                navBackStack = navBackStack,
                shape = RoundedCornerShape(24.dp),
                isAmoled = isAmoled,
                isDarkMode = isDarkMode
            )
        }
        // If 1 provider, show toggle only (no selection needed)
        else if (numProviders == 1) {
            SearchToggleItem(
                enableSearch = enableSearch,
                onToggleSearch = onToggleSearch,
                onDismiss = onDismiss,
                navBackStack = navBackStack,
                shape = RoundedCornerShape(24.dp),
                isAmoled = isAmoled,
                isDarkMode = isDarkMode
            )
        }
        // If 2 providers, group toggle + providers together
        else if (numProviders == 2) {
            // Toggle at position 0
            SearchToggleItem(
                enableSearch = enableSearch,
                onToggleSearch = onToggleSearch,
                onDismiss = onDismiss,
                navBackStack = navBackStack,
                shape = getItemShape(0, totalItems, false),
                isAmoled = isAmoled,
                isDarkMode = isDarkMode
            )
            // Providers at positions 1-2
            settings.searchServices.forEachIndexed { index, service ->
                val isSelected = enableSearch && selectedProviderIndex == index
                SearchProviderItem(
                    service = service,
                    isSelected = isSelected,
                    onClick = { onUpdateSearchService(index) },
                    shape = getItemShape(index + 1, totalItems, isSelected),
                    isAmoled = isAmoled,
                    isDarkMode = isDarkMode,
                    index = index + 1,
                    totalCount = totalItems
                )
            }
        }
        // 3+ providers: grouped list with last item spanning 2 columns behavior (simplified to list)
        else {
            // Toggle at position 0
            SearchToggleItem(
                enableSearch = enableSearch,
                onToggleSearch = onToggleSearch,
                onDismiss = onDismiss,
                navBackStack = navBackStack,
                shape = getItemShape(0, totalItems, false),
                isAmoled = isAmoled,
                isDarkMode = isDarkMode
            )
            // All providers
            settings.searchServices.forEachIndexed { index, service ->
                val isSelected = enableSearch && selectedProviderIndex == index
                SearchProviderItem(
                    service = service,
                    isSelected = isSelected,
                    onClick = { onUpdateSearchService(index) },
                    shape = getItemShape(index + 1, totalItems, isSelected),
                    isAmoled = isAmoled,
                    isDarkMode = isDarkMode,
                    index = index + 1,
                    totalCount = totalItems
                )
            }
        }
    }
}

@Composable
private fun SearchToggleItem(
    enableSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    navBackStack: NavHostController,
    shape: RoundedCornerShape,
    isAmoled: Boolean,
    isDarkMode: Boolean
) {
    // Use surfaceContainerHigh for Light Mode consistency
    val containerColor = if (isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Public, null, tint = contentColor)
        Text(
            text = stringResource(R.string.use_web_search),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = {
                onDismiss()
                navBackStack.navigate(Screen.SettingSearch)
            }
        ) {
            Icon(Icons.Rounded.Settings, null, tint = contentColor)
        }
        HapticSwitch(
            checked = enableSearch,
            onCheckedChange = onToggleSearch
        )
    }
}

@Composable
private fun SearchProviderItem(
    service: SearchServiceOptions,
    isSelected: Boolean,
    onClick: () -> Unit,
    shape: RoundedCornerShape,
    isAmoled: Boolean,
    isDarkMode: Boolean,
    index: Int = 0,
    totalCount: Int = 1
) {
    val haptics = me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics()
    
    // Animated corner radius - selected items animate to fully round
    val topCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSelected) 50.dp else when {
            totalCount == 1 -> 24.dp
            index == 0 -> 24.dp
            else -> 10.dp
        },
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "topCorner"
    )
    val bottomCorner by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSelected) 50.dp else when {
            totalCount == 1 -> 24.dp
            index == totalCount - 1 -> 24.dp
            else -> 10.dp
        },
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "bottomCorner"
    )
    
    val animatedShape = RoundedCornerShape(
        topStart = topCorner, topEnd = topCorner,
        bottomStart = bottomCorner, bottomEnd = bottomCorner
    )
    
    // Animated colors for smooth selection transition
    val targetContainerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else if (isDarkMode) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val targetContentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "contentColor"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(animatedShape)
            .background(containerColor)
            .clickable {
                haptics.perform(me.rerere.rikkahub.ui.hooks.HapticPattern.Pop)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AutoAIIcon(
            name = SearchServiceOptions.TYPES[service::class] ?: "Search",
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = SearchServiceOptions.TYPES[service::class] ?: "Unknown",
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BuiltInSearchSetting(
    preferBuiltInSearch: Boolean,
    onTogglePreferBuiltInSearch: (Boolean) -> Unit
) {
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    
    val containerColor = if (isDarkMode) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface

    CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
        val cardElevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        val cardColors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
        Card(
            shape = me.rerere.rikkahub.ui.theme.AppShapes.CardLarge,
            elevation = cardElevation,
            colors = cardColors
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.built_in_search_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.built_in_search_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.8f)
                    )
                }

                HapticSwitch(
                    checked = preferBuiltInSearch,
                    onCheckedChange = { checked ->
                        onTogglePreferBuiltInSearch(checked)
                    }
                )
            }
        }
    }
}
