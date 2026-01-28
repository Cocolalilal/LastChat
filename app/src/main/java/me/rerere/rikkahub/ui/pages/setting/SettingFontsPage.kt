@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package me.rerere.rikkahub.ui.pages.setting

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.FontConfig
import me.rerere.rikkahub.data.datastore.FontSettings
import me.rerere.rikkahub.data.datastore.FontSource
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.FontFileManager
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt


@Composable
fun SettingFontsPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Use local state for UI, separate from persisted settings
    var localFontSettings by remember(settings) { mutableStateOf(settings.displaySetting.fontSettings) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fontManager = remember { FontFileManager(context) }
    
    var showResetAllDialog by remember { mutableStateOf(false) }
    
    // Persist changes to settings
    fun persistFontSettings(fontSettings: FontSettings) {
        val newDisplaySetting = settings.displaySetting.copy(fontSettings = fontSettings)
        vm.updateSettings(settings.copy(displaySetting = newDisplaySetting))
    }
    
    // Update local state immediately, persist when requested
    fun updateLocalFontSettings(fontSettings: FontSettings, persist: Boolean = false) {
        localFontSettings = fontSettings
        if (persist) {
            persistFontSettings(fontSettings)
        }
    }
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()
    
    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = "Fonts",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(onClick = { showResetAllDialog = true }) {
                        Icon(Icons.Rounded.Refresh, "Reset All")
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = lazyListState,
            contentPadding = contentPadding + PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Unified Font Toggle
            item {
                SettingsGroup(title = "General") {
                    SettingGroupItem(
                        title = "Use same font for headers and content",
                        subtitle = "Link header and content font settings together",
                        trailing = {
                            HapticSwitch(
                                checked = localFontSettings.useSameFontForHeadersAndContent,
                                onCheckedChange = {
                                    val newSettings = if (it) {
                                        // When enabling, copy header settings to content
                                        localFontSettings.copy(
                                            useSameFontForHeadersAndContent = true,
                                            contentFont = localFontSettings.headerFont
                                        )
                                    } else {
                                        localFontSettings.copy(useSameFontForHeadersAndContent = false)
                                    }
                                    updateLocalFontSettings(newSettings, persist = true)
                                }
                            )
                        }
                    )
                }
            }
            
            // Headers Section (or Headers & Content when unified)
            item {
                key("headers") {
                    FontConfigSection(
                        title = if (localFontSettings.useSameFontForHeadersAndContent) "Headers & Content" else "Headers",
                        subtitle = if (localFontSettings.useSameFontForHeadersAndContent) "All text except code blocks" else "Titles, headings, and display text",
                        config = localFontSettings.headerFont,
                        fontManager = fontManager,
                        onConfigChange = { newConfig, persist ->
                            val newSettings = if (localFontSettings.useSameFontForHeadersAndContent) {
                                localFontSettings.copy(
                                    headerFont = newConfig,
                                    contentFont = newConfig
                                )
                            } else {
                                localFontSettings.copy(headerFont = newConfig)
                            }
                            updateLocalFontSettings(newSettings, persist = persist)
                        },
                        onReset = {
                            val newSettings = if (localFontSettings.useSameFontForHeadersAndContent) {
                                localFontSettings.copy(
                                    headerFont = FontConfig.DEFAULT_EXPRESSIVE,
                                    contentFont = FontConfig.DEFAULT_EXPRESSIVE
                                )
                            } else {
                                localFontSettings.copy(headerFont = FontConfig.DEFAULT_EXPRESSIVE)
                            }
                            updateLocalFontSettings(newSettings, persist = true)
                        }
                    )
                }
            }
            
            // Content Section (hidden when unified)
            item {
                AnimatedVisibility(
                    visible = !localFontSettings.useSameFontForHeadersAndContent,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    key("content") {
                        FontConfigSection(
                            title = "Content",
                            subtitle = "Body text, paragraphs, and messages",
                            config = localFontSettings.contentFont,
                            fontManager = fontManager,
                            onConfigChange = { newConfig, persist ->
                                updateLocalFontSettings(localFontSettings.copy(contentFont = newConfig), persist = persist)
                            },
                            onReset = {
                                updateLocalFontSettings(localFontSettings.copy(contentFont = FontConfig.DEFAULT_EXPRESSIVE), persist = true)
                            }
                        )
                    }
                }
            }
            
            // Code Blocks Section
            item {
                key("code") {
                    FontConfigSection(
                        title = "Code Blocks",
                        subtitle = "Code, inline code, and monospace text",
                        config = localFontSettings.codeFont,
                        fontManager = fontManager,
                        isCodeFont = true,
                        onConfigChange = { newConfig, persist ->
                            updateLocalFontSettings(localFontSettings.copy(codeFont = newConfig), persist = persist)
                        },
                        onReset = {
                            updateLocalFontSettings(localFontSettings.copy(codeFont = FontConfig.DEFAULT_CODE), persist = true)
                        }
                    )
                }
            }
            
            // Preview Section
            item {
                Spacer(Modifier.height(8.dp))
                SettingsGroup(title = "Preview") {
                    FontPreviewCard(fontSettings = localFontSettings)
                }
            }
        }
    }
    
    // Reset All Dialog
    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Reset All Fonts?") },
            text = { Text("This will reset all font settings to their defaults. Custom fonts will be kept but no longer applied.") },
            confirmButton = {
                TextButton(onClick = {
                    updateLocalFontSettings(FontSettings(), persist = true)
                    showResetAllDialog = false
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                    if (nameIndex >= 0) cursor.getString(nameIndex) else "Custom Font"
                } ?: "Custom Font"
                
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
            onClick = { expanded = !expanded },
            modifier = Modifier.animateContentSize()
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
                            "Reset",
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
                            text = "Font Source",
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
                                            FontSource.System -> "Default"
                                            FontSource.SystemCode -> "Default"
                                            FontSource.Custom -> "Custom"
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
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                            text = "Variable font · ${config.customAxes.size} axes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    config.customFontPath?.let { fontManager.deleteFont(it) }
                                    onConfigChange(config.copy(
                                        fontSource = FontSource.System,
                                        customFontPath = null,
                                        customFontName = null,
                                        customAxes = emptyList(),
                                        features = emptyList()
                                    ), true)
                                }) {
                                    Icon(Icons.Rounded.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        
                        // Variable Font Axes (System fonts or custom with axes)
                        if (config.fontSource != FontSource.Custom || config.customAxes.isEmpty()) {
                            // System font axes
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Font Axes",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            FontAxisSlider(
                                label = "Width",
                                value = config.width,
                                valueRange = 75f..125f,
                                steps = 4,
                                onValueChange = { onConfigChange(config.copy(width = it), false) },
                                onValueChangeFinished = { onConfigChange(config, true) }
                            )
                            
                            if (config.fontSource == FontSource.System) {
                                FontAxisSlider(
                                    label = "Roundness",
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
                                text = "Font Axes",
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
                                text = "Typography",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            FontAxisSlider(
                                label = "Size",
                                value = config.fontSize,
                                valueRange = 0.5f..2f,
                                steps = 14,
                                formatValue = { "${(it * 100).roundToInt()}%" },
                                onValueChange = { onConfigChange(config.copy(fontSize = it), false) },
                                onValueChangeFinished = { onConfigChange(config, true) }
                            )
                            
                            FontAxisSlider(
                                label = "Letter Spacing",
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
                                text = "OpenType Features",
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
            title = { Text("Reset $title Font?") },
            text = { Text("This will reset the font settings for $title to defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    showResetDialog = false
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
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
    // Create actual font families from the font configs
    val headerFontFamily = rememberFontFamilyFromConfig(fontSettings.headerFont)
    val contentFontFamily = rememberFontFamilyFromConfig(fontSettings.contentFont)
    val codeFontFamily = rememberFontFamilyFromConfig(fontSettings.codeFont)
    
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
                text = "Header Preview",
                style = TextStyle(
                    fontFamily = headerFontFamily,
                    fontWeight = FontWeight(fontSettings.headerFont.weight.roundToInt()),
                    fontSize = (24 * fontSettings.headerFont.fontSize).sp,
                    lineHeight = (32 * fontSettings.headerFont.lineHeight).sp,
                    letterSpacing = fontSettings.headerFont.letterSpacing.sp
                )
            )
            
            // Content preview
            Text(
                text = "This is a preview of the content font. It shows how body text will appear in messages and other content areas.",
                style = TextStyle(
                    fontFamily = contentFontFamily,
                    fontWeight = FontWeight(fontSettings.contentFont.weight.roundToInt()),
                    fontSize = (14 * fontSettings.contentFont.fontSize).sp,
                    lineHeight = (20 * fontSettings.contentFont.lineHeight).sp,
                    letterSpacing = fontSettings.contentFont.letterSpacing.sp
                )
            )
            
            // Code preview - use configured code font
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Text(
                    text = "fun main() {\n    println(\"Hello, World!\")\n}",
                    style = TextStyle(
                        fontFamily = codeFontFamily,
                        fontWeight = FontWeight(fontSettings.codeFont.weight.roundToInt()),
                        fontSize = (13 * fontSettings.codeFont.fontSize).sp,
                        lineHeight = (18 * fontSettings.codeFont.lineHeight).sp,
                        letterSpacing = fontSettings.codeFont.letterSpacing.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Create a FontFamily from a FontConfig with proper variation settings.
 */
@Composable
private fun rememberFontFamilyFromConfig(config: FontConfig): FontFamily {
    val context = LocalContext.current
    
    return remember(
        config.fontSource,
        config.customFontPath,
        config.weight,
        config.width,
        config.roundness,
        config.grade,
        config.customAxes
    ) {
        when (config.fontSource) {
            FontSource.System -> {
                // Use Google Sans Flex with roundness
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    FontFamily(
                        Font(
                            R.font.google_sans_flex,
                            weight = FontWeight(config.weight.roundToInt()),
                            variationSettings = FontVariation.Settings(
                                FontVariation.weight(config.weight.roundToInt()),
                                FontVariation.width(config.width),
                                FontVariation.Setting("ROND", config.roundness),
                                FontVariation.Setting("GRAD", config.grade)
                            )
                        )
                    )
                } else {
                    FontFamily(Font(R.font.google_sans_flex, weight = FontWeight(config.weight.roundToInt())))
                }
            }
            FontSource.SystemCode -> {
                // Use Google Sans Code (monospace)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    FontFamily(
                        Font(
                            R.font.google_sans_code,
                            weight = FontWeight(config.weight.roundToInt()),
                            variationSettings = FontVariation.Settings(
                                FontVariation.weight(config.weight.roundToInt())
                            )
                        )
                    )
                } else {
                    FontFamily(Font(R.font.google_sans_code, weight = FontWeight(config.weight.roundToInt())))
                }
            }
            FontSource.Custom -> {
                // Custom font from file
                config.customFontPath?.let { path ->
                    try {
                        val fontFile = java.io.File(path)
                        if (fontFile.exists()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config.customAxes.isNotEmpty()) {
                                // Variable font with custom axes
                                val settings = mutableListOf<FontVariation.Setting>()
                                config.customAxes.forEach { axis ->
                                    settings.add(FontVariation.Setting(axis.tag, axis.currentValue))
                                }
                                FontFamily(
                                    Font(
                                        fontFile,
                                        weight = FontWeight(config.weight.roundToInt()),
                                        variationSettings = FontVariation.Settings(*settings.toTypedArray())
                                    )
                                )
                            } else {
                                FontFamily(Font(fontFile, weight = FontWeight(config.weight.roundToInt())))
                            }
                        } else {
                            // Fallback to default if file doesn't exist
                            FontFamily.Default
                        }
                    } catch (e: Exception) {
                        FontFamily.Default
                    }
                } ?: FontFamily.Default
            }
        }
    }
}

private fun getFontSourceLabel(config: FontConfig): String {
    return when (config.fontSource) {
        FontSource.System -> "Default"
        FontSource.SystemCode -> "Google Sans Code"
        FontSource.Custom -> config.customFontName ?: "Custom Font"
    }
}
