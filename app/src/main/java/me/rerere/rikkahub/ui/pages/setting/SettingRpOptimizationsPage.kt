package me.rerere.rikkahub.ui.pages.setting

import me.rerere.rikkahub.ui.theme.LocalDarkMode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import me.rerere.rikkahub.ui.components.ui.HapticSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.RpStyleRule
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingRpOptimizationsPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var displaySetting by remember(settings) { mutableStateOf(settings.displaySetting) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RpStyleRule?>(null) }

    fun updateDisplaySetting(rules: List<RpStyleRule>) {
        val newSetting = displaySetting.copy(rpStyleRules = rules)
        displaySetting = newSetting
        vm.updateSettings(settings.copy(displaySetting = newSetting))
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val lazyListState = rememberLazyListState()

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = stringResource(R.string.setting_rp_optimizations_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    BackButton()
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = AppShapes.CardLarge
            ) {
                Icon(Icons.Rounded.Add, stringResource(R.string.setting_rp_add_rule))
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(contentPadding),
            state = lazyListState,
            contentPadding = contentPadding + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Description
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    shape = AppShapes.CardLarge
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.setting_rp_custom_text_styling),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.setting_rp_custom_text_styling_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Supported patterns section
                        Text(
                            text = stringResource(R.string.setting_rp_supported_patterns),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "*" to stringResource(R.string.setting_rp_italic),
                                "**" to stringResource(R.string.setting_rp_bold),
                                "~~" to stringResource(R.string.setting_rp_strikethrough),
                                "`" to stringResource(R.string.setting_rp_inline_code),
                                "#" to stringResource(R.string.setting_rp_heading_level, 1),
                                "##" to stringResource(R.string.setting_rp_heading_level, 2),
                                "###" to stringResource(R.string.setting_rp_heading_level, 3),
                                "####" to stringResource(R.string.setting_rp_heading_level, 4),
                                "#####" to stringResource(R.string.setting_rp_heading_level, 5),
                                "######" to stringResource(R.string.setting_rp_heading_level, 6),
                                ">" to stringResource(R.string.setting_rp_blockquotes)
                            ).forEach { (pattern, desc) ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = pattern,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Rules list
            if (displaySetting.rpStyleRules.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        shape = AppShapes.CardLarge
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.setting_rp_no_rules),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(displaySetting.rpStyleRules, key = { it.id }) { rule ->
                    RpStyleRuleItem(
                        rule = rule,
                        onToggle = { enabled ->
                            updateDisplaySetting(
                                displaySetting.rpStyleRules.map {
                                    if (it.id == rule.id) it.copy(enabled = enabled) else it
                                }
                            )
                        },
                        onEdit = { editingRule = rule },
                        onDelete = {
                            updateDisplaySetting(
                                displaySetting.rpStyleRules.filter { it.id != rule.id }
                            )
                        }
                    )
                }
            }

            // Preview section
            if (displaySetting.rpStyleRules.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.setting_fonts_preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        shape = AppShapes.CardLarge
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            displaySetting.rpStyleRules.filter { it.enabled }.forEach { rule ->
                                val color = try {
                                    Color(android.graphics.Color.parseColor(rule.colorHex))
                                } catch (e: Exception) {
                                    MaterialTheme.colorScheme.onSurface
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(
                                        text = stringResource(R.string.setting_rp_example_text, rule.pattern),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = color
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog || editingRule != null) {
        RpStyleRuleDialog(
            rule = editingRule,
            onDismiss = {
                showAddDialog = false
                editingRule = null
            },
            onSave = { newRule ->
                if (editingRule != null) {
                    // Edit existing
                    updateDisplaySetting(
                        displaySetting.rpStyleRules.map {
                            if (it.id == editingRule!!.id) newRule else it
                        }
                    )
                } else {
                    // Add new
                    updateDisplaySetting(displaySetting.rpStyleRules + newRule)
                }
                showAddDialog = false
                editingRule = null
            }
        )
    }
}

@Composable
private fun RpStyleRuleItem(
    rule: RpStyleRule,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val previewColor = try {
        Color(android.graphics.Color.parseColor(rule.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = AppShapes.CardLarge,
        onClick = onEdit
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                    )
                    Text(stringResource(R.string.setting_rp_custom_preview, rule.pattern))
                }
            },
            supportingContent = {
                Text(
                    text = rule.colorHex,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.Delete,
                            stringResource(R.string.setting_tts_filter_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    HapticSwitch(
                        checked = rule.enabled,
                        onCheckedChange = onToggle
                    )
                }
            }
        )
    }
}

@Composable
private fun RpStyleRuleDialog(
    rule: RpStyleRule?,
    onDismiss: () -> Unit,
    onSave: (RpStyleRule) -> Unit
) {
    var pattern by remember { mutableStateOf(rule?.pattern ?: "") }
    var colorHex by remember { mutableStateOf(rule?.colorHex ?: "#808080") }    
    // Parse current color for sliders
    val currentColor = try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        Color.Gray
    }
    var red by remember { mutableStateOf((currentColor.red * 255).toInt()) }
    var green by remember { mutableStateOf((currentColor.green * 255).toInt()) }
    var blue by remember { mutableStateOf((currentColor.blue * 255).toInt()) }
    
    // Update colorHex when RGB sliders change
    fun updateColorFromRgb() {
        colorHex = String.format("#%02X%02X%02X", red, green, blue)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (rule != null) {
                    stringResource(R.string.setting_rp_edit_rule)
                } else {
                    stringResource(R.string.setting_rp_add_rule)
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(stringResource(R.string.setting_rp_pattern)) },
                    placeholder = { Text(stringResource(R.string.setting_rp_pattern_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        when (pattern) {
                            "#", "##", "###", "####", "#####", "######" -> 
                                Text(stringResource(R.string.setting_rp_heading_level, pattern.length))
                            ">" -> Text(stringResource(R.string.setting_rp_blockquotes))
                            "*" -> Text(stringResource(R.string.setting_rp_italic))
                            "**" -> Text(stringResource(R.string.setting_rp_bold))
                            "~~" -> Text(stringResource(R.string.setting_rp_strikethrough))
                            "`" -> Text(stringResource(R.string.setting_rp_inline_code))
                            else -> if (pattern.isNotEmpty()) {
                                Text(stringResource(R.string.setting_rp_custom_preview, pattern))
                            } else {
                                Text(stringResource(R.string.setting_rp_enter_pattern))
                            }
                        }
                    },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
                
                OutlinedTextField(
                    value = colorHex,
                    onValueChange = { 
                        colorHex = it
                        // Try to update RGB sliders from hex
                        try {
                            val c = Color(android.graphics.Color.parseColor(it))
                            red = (c.red * 255).toInt()
                            green = (c.green * 255).toInt()
                            blue = (c.blue * 255).toInt()
                        } catch (e: Exception) { }
                    },
                    label = { Text(stringResource(R.string.setting_rp_color_hex)) },
                    placeholder = { Text("#808080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        val color = try {
                            Color(android.graphics.Color.parseColor(colorHex))
                        } catch (e: Exception) {
                            Color.Gray
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.InputField,
                )
                
                // Color presets row
                Text(
                    text = stringResource(R.string.setting_rp_presets),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "#808080", // Gray
                        "#FFD700", // Yellow
                        "#87CEEB", // Light Blue
                        "#90EE90", // Light Green
                        "#FFB6C1", // Pink
                        "#FF6B6B"  // Red
                    ).forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { 
                                    colorHex = hex
                                    val c = Color(android.graphics.Color.parseColor(hex))
                                    red = (c.red * 255).toInt()
                                    green = (c.green * 255).toInt()
                                    blue = (c.blue * 255).toInt()
                                }
                        )
                    }
                }
                
                // RGB sliders - always visible for custom color selection
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // Red slider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("R", modifier = Modifier.width(20.dp), color = Color.Red)
                        androidx.compose.material3.Slider(
                            value = red.toFloat(),
                            onValueChange = { red = it.toInt(); updateColorFromRgb() },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("$red", modifier = Modifier.width(36.dp))
                    }
                    // Green slider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("G", modifier = Modifier.width(20.dp), color = Color.Green)
                        androidx.compose.material3.Slider(
                            value = green.toFloat(),
                            onValueChange = { green = it.toInt(); updateColorFromRgb() },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("$green", modifier = Modifier.width(36.dp))
                    }
                    // Blue slider
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("B", modifier = Modifier.width(20.dp), color = Color.Blue)
                        androidx.compose.material3.Slider(
                            value = blue.toFloat(),
                            onValueChange = { blue = it.toInt(); updateColorFromRgb() },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("$blue", modifier = Modifier.width(36.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (pattern.isNotBlank()) {
                        onSave(
                            RpStyleRule(
                                id = rule?.id ?: kotlin.uuid.Uuid.random().toString(),
                                pattern = pattern,
                                colorHex = colorHex,
                                enabled = rule?.enabled ?: true
                            )
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

