@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package me.rerere.rikkahub.ui.pages.setting

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.FontConfig
import me.rerere.rikkahub.data.datastore.FontSettings
import me.rerere.rikkahub.data.datastore.FontSource
import me.rerere.rikkahub.data.datastore.normalize
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.appFontConfigFromSettings
import me.rerere.rikkahub.ui.theme.rememberAppFontFamily
import me.rerere.rikkahub.ui.theme.rememberFontFamilyFromConfig
import me.rerere.rikkahub.utils.FontFileManager
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt


internal const val PhoneSystemFontToggleTag = "phone_system_font_toggle"

@Composable
fun SettingFontsPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var localFontSettings by remember(settings) {
        mutableStateOf(settings.displaySetting.fontSettings.normalize())
    }
    val context = LocalContext.current
    val fontManager = remember { FontFileManager(context) }

    var showResetAllDialog by remember { mutableStateOf(false) }

    fun persistFontSettings(fontSettings: FontSettings) {
        val normalizedFontSettings = fontSettings.normalize()
        val newDisplaySetting = settings.displaySetting.copy(fontSettings = normalizedFontSettings)
        vm.updateSettings(settings.copy(displaySetting = newDisplaySetting))
    }

    fun updateLocalFontSettings(fontSettings: FontSettings, persist: Boolean = false) {
        val normalizedFontSettings = fontSettings.normalize()
        localFontSettings = normalizedFontSettings
        if (persist) {
            persistFontSettings(normalizedFontSettings)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_fonts_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(onClick = { showResetAllDialog = true }) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.setting_fonts_reset_all))
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        FontSettingsContent(
            fontSettings = localFontSettings,
            fontManager = fontManager,
            contentPadding = contentPadding,
            onFontSettingsChange = ::updateLocalFontSettings
        )
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text(stringResource(R.string.setting_fonts_reset_all_title)) },
            text = { Text(stringResource(R.string.setting_fonts_reset_all_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    updateLocalFontSettings(FontSettings(), persist = true)
                    showResetAllDialog = false
                }) {
                    Text(stringResource(R.string.setting_fonts_reset_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
internal fun FontSettingsContent(
    fontSettings: FontSettings,
    fontManager: FontFileManager,
    onFontSettingsChange: (FontSettings, Boolean) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val normalizedFontSettings = fontSettings.normalize()
    val lazyListState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(contentPadding),
        state = lazyListState,
        contentPadding = contentPadding + PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            SettingsGroup(title = stringResource(R.string.setting_fonts_general)) {
                SettingGroupItem(
                    title = stringResource(R.string.setting_fonts_use_system),
                    subtitle = stringResource(R.string.setting_fonts_use_system_desc),
                    trailing = {
                        HapticSwitch(
                            checked = normalizedFontSettings.usePhoneSystemFont,
                            onCheckedChange = { enabled ->
                                onFontSettingsChange(
                                    normalizedFontSettings.copy(usePhoneSystemFont = enabled),
                                    true
                                )
                            },
                            modifier = Modifier.testTag(PhoneSystemFontToggleTag)
                        )
                    }
                )
            }
        }

        item {
            AnimatedVisibility(
                visible = !normalizedFontSettings.usePhoneSystemFont,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                FontConfigSection(
                    title = stringResource(R.string.setting_fonts_app_font),
                    subtitle = stringResource(R.string.setting_fonts_app_font_desc),
                    config = normalizedFontSettings.headerFont,
                    fontManager = fontManager,
                    onConfigChange = { newConfig, persist ->
                        onFontSettingsChange(
                            normalizedFontSettings.copy(
                                headerFont = newConfig,
                                contentFont = newConfig
                            ),
                            persist
                        )
                    },
                    onReset = {
                        onFontSettingsChange(
                            normalizedFontSettings.copy(
                                headerFont = FontConfig.DEFAULT_EXPRESSIVE,
                                contentFont = FontConfig.DEFAULT_EXPRESSIVE
                            ),
                            true
                        )
                    }
                )
            }
        }

        item {
            AnimatedVisibility(
                visible = !normalizedFontSettings.usePhoneSystemFont,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                FontConfigSection(
                    title = stringResource(R.string.setting_fonts_code_blocks),
                    subtitle = stringResource(R.string.setting_fonts_code_blocks_desc),
                    config = normalizedFontSettings.codeFont,
                    fontManager = fontManager,
                    isCodeFont = true,
                    onConfigChange = { newConfig, persist ->
                        onFontSettingsChange(
                            normalizedFontSettings.copy(codeFont = newConfig),
                            persist
                        )
                    },
                    onReset = {
                        onFontSettingsChange(
                            normalizedFontSettings.copy(codeFont = FontConfig.DEFAULT_CODE),
                            true
                        )
                    }
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            SettingsGroup(title = stringResource(R.string.setting_fonts_preview)) {
                FontPreviewCard(fontSettings = normalizedFontSettings)
            }
        }
    }
}

@Composable
private fun FontConfigSection(
    title: String,
    subtitle: String,
    config: FontConfig,
    fontManager: FontFileManager,
    isCodeFont: Boolean = false,
    onConfigChange: (FontConfig, Boolean) -> Unit, // (config, persist)
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    
    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                // Take persistable permission
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Permission might not be persistable, continue anyway
                }
                
                // Get display name
                val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.moveToFirst()
                    if (nameIndex >= 0) cursor.getString(nameIndex) else context.getString(R.string.setting_fonts_source_custom_fallback)
                } ?: context.getString(R.string.setting_fonts_source_custom_fallback)
                
                // Import font
                val fontPath = fontManager.importFont(uri, displayName)
                if (fontPath != null) {
                    // Detect axes and features
                    val axes = fontManager.detectFontAxes(fontPath)
                    val features = fontManager.detectFontFeatures(fontPath)
                    
                    onConfigChange(config.copy(
                        fontSource = FontSource.Custom,
                        customFontPath = fontPath,
                        customFontName = displayName,
                        customAxes = axes,
                        features = features
                    ), true) // Persist immediately for font selection
                }
            }
        }
    }
    
    SettingsGroup(title = title) {
        // Main item (expandable)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (LocalDarkMode.current) 
                    MaterialTheme.colorScheme.surfaceContainerLow 
                else 
                    MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = AppShapes.CardLarge,
            onClick = { expanded = !expanded }
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = getFontSourceLabel(config),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showResetDialog = true }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            stringResource(R.string.setting_fonts_reset_all),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Expanded content
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Spacer(Modifier.height(4.dp))
                        
                        // Font Source Selector
                        Text(
                            text = stringResource(R.string.setting_fonts_font_source),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Filter sources based on font type
                            val availableSources = if (isCodeFont) {
                                listOf(FontSource.SystemCode, FontSource.Custom)
                            } else {
                                listOf(FontSource.System, FontSource.Custom)
                            }
                            
                            availableSources.forEach { source ->
                                val isSelected = config.fontSource == source
                                OutlinedButton(
                                        onClick = {
                                        if (source == FontSource.Custom) {
                                            fontPicker.launch(arrayOf(
                                                "font/ttf",
                                                "font/otf",
                                                "application/x-font-ttf",
                                                "application/x-font-otf",
                                                "application/octet-stream"
                                            ))
                                        } else {
                                            onConfigChange(config.copy(
                                                fontSource = source,
                                                roundness = if (source == FontSource.System) 100f else 0f
                                            ), true) // Persist immediately
                                        }
                                    },
                                    colors = if (isSelected) {
                                        ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    } else {
                                        ButtonDefaults.outlinedButtonColors()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                        Text(
                                            text = when (source) {
                                                FontSource.System -> stringResource(R.string.setting_fonts_builtin)
                                                FontSource.SystemCode -> stringResource(R.string.setting_fonts_builtin)
                                                FontSource.Custom -> stringResource(R.string.setting_fonts_custom)
                                            },
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                }
                            }
                        }
                        
                        // Custom font info
                        if (config.fontSource == FontSource.Custom && config.customFontName != null) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = config.customFontName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (config.customAxes.isNotEmpty()) {
                                        Text(
                                            text = stringResource(
                                                R.string.setting_fonts_variable_font,
                                                config.customAxes.size
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    config.customFontPath?.let { fontManager.deleteFont(it) }
                                    onConfigChange(config.copy(
                                        fontSource = if (isCodeFont) FontSource.SystemCode else FontSource.System,
                                        customFontPath = null,
                                        customFontName = null,
                                        customAxes = emptyList(),
                                        features = emptyList()
                                    ), true)
                                }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        stringResource(R.string.setting_fonts_remove),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        
                        // Variable Font Axes (System fonts or custom with axes)
                        if (config.fontSource != FontSource.Custom || config.customAxes.isEmpty()) {
                            // System font axes
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.setting_fonts_axes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            FontAxisSlider(
                                label = stringResource(R.string.setting_fonts_width),
                                value = config.width,
                                valueRange = 75f..125f,
                                steps = 4,
                                onValueChange = { onConfigChange(config.copy(width = it), false) },
                                onValueChangeFinished = { onConfigChange(config, true) }
                            )
                            
                            if (config.fontSource == FontSource.System) {
                                FontAxisSlider(
                                    label = stringResource(R.string.setting_fonts_roundness),
                                    value = config.roundness,
                                    valueRange = 0f..100f,
                                    steps = 9,
                                    onValueChange = { onConfigChange(config.copy(roundness = it), false) },
                                    onValueChangeFinished = { onConfigChange(config, true) }
                                )
                            }
                        } else {
                            // Custom font axes
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.setting_fonts_axes),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            config.customAxes.forEach { axis ->
                                FontAxisSlider(
                                    label = axis.name,
                                    value = axis.currentValue,
                                    valueRange = axis.minValue..axis.maxValue,
                                    steps = ((axis.maxValue - axis.minValue) / 10).toInt().coerceIn(1, 20),
                                    onValueChange = { newValue ->
                                        val updatedAxes = config.customAxes.map {
                                            if (it.tag == axis.tag) it.copy(currentValue = newValue) else it
                                        }
                                        onConfigChange(config.copy(customAxes = updatedAxes), false)
                                    },
                                    onValueChangeFinished = { onConfigChange(config, true) }
                                )
                            }
                            
                            // Typography adjustments only for custom fonts
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.setting_fonts_typography),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            FontAxisSlider(
                                label = stringResource(R.string.setting_fonts_size),
                                value = config.fontSize,
                                valueRange = 0.5f..2f,
                                steps = 14,
                                formatValue = { "${(it * 100).roundToInt()}%" },
                                onValueChange = { onConfigChange(config.copy(fontSize = it), false) },
                                onValueChangeFinished = { onConfigChange(config, true) }
                            )
                            
                            FontAxisSlider(
                                label = stringResource(R.string.setting_fonts_letter_spacing),
                                value = config.letterSpacing,
                                valueRange = -0.05f..0.1f,
                                steps = 14,
                                formatValue = { String.format("%.2f em", it) },
                                onValueChange = { onConfigChange(config.copy(letterSpacing = it), false) },
                                onValueChangeFinished = { onConfigChange(config, true) }
                            )
                        }
                        
                        // OpenType Features (if any)
                        if (config.features.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.setting_fonts_opentype_features),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            config.features.forEach { feature ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = feature.name,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = feature.tag,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    HapticSwitch(
                                        checked = feature.enabled,
                                        onCheckedChange = { enabled ->
                                            val updatedFeatures = config.features.map {
                                                if (it.tag == feature.tag) it.copy(enabled = enabled) else it
                                            }
                                            onConfigChange(config.copy(features = updatedFeatures), true)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Reset Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.setting_fonts_reset_title, title)) },
            text = { Text(stringResource(R.string.setting_fonts_reset_desc, title)) },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetDialog = false
                }) {
                    Text(stringResource(R.string.setting_fonts_reset_all))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * Slider with local state for smooth dragging.
 * Updates the parent on every change for preview, but only persists on finish.
 */
@Composable
private fun FontAxisSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    formatValue: (Float) -> String = { it.roundToInt().toString() },
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    // Use local state for smooth dragging
    var localValue by remember { mutableFloatStateOf(value) }
    
    // Sync with external value when it changes
    LaunchedEffect(value) {
        localValue = value
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(90.dp)
        )
        Slider(
            value = localValue,
            onValueChange = { newValue ->
                localValue = newValue
                onValueChange(newValue)
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatValue(localValue),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(50.dp)
        )
    }
}

@Composable
private fun FontPreviewCard(fontSettings: FontSettings) {
    val normalizedFontSettings = fontSettings.normalize()
    val appFontConfig = appFontConfigFromSettings(normalizedFontSettings)
    val appFontFamily = rememberAppFontFamily(normalizedFontSettings)
    val codeFontFamily = rememberFontFamilyFromConfig(normalizedFontSettings.codeFont)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) 
                MaterialTheme.colorScheme.surfaceContainerLow 
            else 
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = AppShapes.CardLarge
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header preview
            Text(
                text = stringResource(R.string.setting_fonts_header_preview),
                style = TextStyle(
                    fontFamily = appFontFamily,
                    fontWeight = FontWeight(appFontConfig.weight.roundToInt()),
                    fontSize = (24 * appFontConfig.fontSize).sp,
                    lineHeight = (32 * appFontConfig.lineHeight).sp,
                    letterSpacing = appFontConfig.letterSpacing.sp
                )
            )
            
            // Content preview
            Text(
                text = stringResource(R.string.setting_fonts_content_preview),
                style = TextStyle(
                    fontFamily = appFontFamily,
                    fontWeight = FontWeight(appFontConfig.weight.roundToInt()),
                    fontSize = (14 * appFontConfig.fontSize).sp,
                    lineHeight = (20 * appFontConfig.lineHeight).sp,
                    letterSpacing = appFontConfig.letterSpacing.sp
                )
            )
            
            // Code preview - use configured code font
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.setting_fonts_code_preview),
                    style = TextStyle(
                        fontFamily = codeFontFamily,
                        fontWeight = FontWeight(normalizedFontSettings.codeFont.weight.roundToInt()),
                        fontSize = (13 * normalizedFontSettings.codeFont.fontSize).sp,
                        lineHeight = (18 * normalizedFontSettings.codeFont.lineHeight).sp,
                        letterSpacing = normalizedFontSettings.codeFont.letterSpacing.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getFontSourceLabel(config: FontConfig): String {
    return when (config.fontSource) {
        FontSource.System -> stringResource(R.string.setting_fonts_source_default)
        FontSource.SystemCode -> stringResource(R.string.setting_fonts_source_code)
        FontSource.Custom -> config.customFontName ?: stringResource(R.string.setting_fonts_source_custom_fallback)
    }
}
