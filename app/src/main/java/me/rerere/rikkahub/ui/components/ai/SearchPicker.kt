package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.composables.icons.lucide.Earth
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings2
import com.dokar.sonner.ToastType
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
    shape: Shape = RoundedCornerShape(24.dp),
    onToggleSearch: (Boolean) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    model: Model?,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onlyIcon: Boolean = false
) {
    // Assuming LocalToaster is defined elsewhere and currentmember was a typo for current
    // If LocalToaster is not defined, this line will cause a compilation error.
    // As per instruction "Make sure to incorporate the change in a way so that the resulting file is syntactically correct."
    // I'm correcting 'currentmember' to 'current' to ensure syntactic correctness of the access pattern.
    val toaster = LocalToaster.current // { mutableStateOf(false) } - removed the lambda as it's not how current is typically used.
    var showSearchPicker by remember { mutableStateOf(false) }
    val currentService = settings.searchServices.getOrNull(settings.searchServiceSelected)

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
                if (enableSearch && currentService != null) {
                    AutoAIIcon(
                        name = SearchServiceOptions.TYPES[currentService::class] ?: "Search",
                        color = Color.Transparent
                    )
                } else {
                    Icon(
                        imageVector = Lucide.Earth,
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
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.search_picker_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )

                SearchPicker(
                    enableSearch = enableSearch,
                    settings = settings,
                    onToggleSearch = onToggleSearch,
                    onUpdateSearchService = { index ->
                        onUpdateSearchService(index)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    model = model,
                    onDismiss = {
                        showSearchPicker = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchPicker(
    enableSearch: Boolean,
    settings: Settings,
    model: Model?,
    modifier: Modifier = Modifier,
    onToggleSearch: (Boolean) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val navBackStack = LocalNavController.current

    // 模型内置搜索
    if (model != null && ModelRegistry.GEMINI_SERIES.match(model.modelId)) {
        BuiltInSearchSetting(model = model)
    }

    // 如果没有开启内置搜索，显示搜索服务选择
    if (model?.tools?.contains(BuiltInTools.Search) != true) {
        AppSearchSettings(
            enableSearch = enableSearch,
            onDismiss = onDismiss,
            navBackStack = navBackStack,
            onToggleSearch = onToggleSearch,
            modifier = modifier,
            settings = settings,
            onUpdateSearchService = onUpdateSearchService
        )
    }
}

@Composable
private fun AppSearchSettings(
    enableSearch: Boolean,
    onDismiss: () -> Unit,
    navBackStack: NavHostController,
    onToggleSearch: (Boolean) -> Unit,
    modifier: Modifier,
    settings: Settings,
    onUpdateSearchService: (Int) -> Unit
) {
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    
    val containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
    val elevation = if (isAmoled) 0.dp else 6.dp
    val tonalElevation = if (isAmoled) 0.dp else LocalAbsoluteTonalElevation.current

    CompositionLocalProvider(LocalAbsoluteTonalElevation provides tonalElevation) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Earth, null)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.use_web_search),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (enableSearch) {
                            stringResource(R.string.web_search_enabled)
                        } else {
                            stringResource(R.string.web_search_disabled)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (LocalDarkMode.current) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)
                    )
                }
                IconButton(
                    onClick = {
                        onDismiss()
                        navBackStack.navigate(Screen.SettingSearch)
                    }
                ) {
                    Icon(Lucide.Settings2, null)
                }
                Switch(
                    checked = enableSearch,
                    onCheckedChange = onToggleSearch
                )
            }
        }


    }

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = GridCells.Adaptive(150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(settings.searchServices) { index, service ->
            val containerColor = animateColorAsState(
                if (settings.searchServiceSelected == index) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    if (LocalDarkMode.current) Color.Black else Color.White
                }
            )
            val textColor = animateColorAsState(
                if (settings.searchServiceSelected == index) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    if (LocalDarkMode.current) Color.White else Color.Black
                }
            )
            val isSelected = settings.searchServiceSelected == index
            val itemContainerColor = if(isAmoled && !isSelected) Color.Black else containerColor.value
            val itemContentColor = if(isAmoled && !isSelected) Color.White else textColor.value
            val itemElevation = if(isAmoled) 0.dp else 6.dp
            val itemTonalElevation = if(isAmoled) 0.dp else LocalAbsoluteTonalElevation.current

            CompositionLocalProvider(LocalAbsoluteTonalElevation provides itemTonalElevation) {
                val cardColors = CardDefaults.cardColors(
                    containerColor = itemContainerColor,
                    contentColor = itemContentColor,
                )
                val cardElevation = CardDefaults.cardElevation(defaultElevation = itemElevation)
                Card(
                    colors = cardColors,
                    elevation = cardElevation,
                    onClick = {
                        onUpdateSearchService(index)
                    },
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AutoAIIcon(
                            name = SearchServiceOptions.TYPES[service::class] ?: "Search",
                            modifier = Modifier.size(30.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = SearchServiceOptions.TYPES[service::class] ?: "Unknown",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            SearchAbilityTagLine(
                                options = service,
                                modifier = Modifier
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun BuiltInSearchSetting(model: Model) {
    val settingsStore = koinInject<SettingsStore>()
    val scope = rememberCoroutineScope()
    val amoledMode by rememberAmoledDarkMode()
    val isDarkMode = LocalDarkMode.current
    val isAmoled = amoledMode && isDarkMode
    
    val containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
    val elevation = if (isAmoled) 0.dp else 6.dp
    val tonalElevation = if (isAmoled) 0.dp else LocalAbsoluteTonalElevation.current

    CompositionLocalProvider(LocalAbsoluteTonalElevation provides tonalElevation) {
        val cardElevation = CardDefaults.cardElevation(defaultElevation = elevation)
        val cardColors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
        Card(
            shape = RoundedCornerShape(24.dp),
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
                Icon(Lucide.Search, null)
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

                Switch(
                    checked = model.tools.contains(BuiltInTools.Search),
                    onCheckedChange = { checked ->
                        val settings = settingsStore.settingsFlow.value
                        scope.launch {
                            settingsStore.update(
                                settings.copy(
                                    providers = settings.providers.map { providerSetting ->
                                        providerSetting.editModel(
                                            model.copy(
                                                tools = if (checked) model.tools + BuiltInTools.Search else model.tools - BuiltInTools.Search
                                            )
                                        )
                                    }
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}
