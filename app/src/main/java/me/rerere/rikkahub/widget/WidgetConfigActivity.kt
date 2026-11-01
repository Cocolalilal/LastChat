package me.rerere.rikkahub.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.LogUtil
import org.koin.android.ext.android.inject

private const val TAG = "WidgetConfig"

/**
 * Configuration activity for the Assistant Widget.
 * Allows users to select which assistant the widget should open.
 */
class WidgetConfigActivity : ComponentActivity() {
    
    private val settingsStore: SettingsStore by inject()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Set result to CANCELED in case user backs out
        setResult(RESULT_CANCELED)
        
        // Get the widget ID from the intent
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        
        LogUtil.d(TAG, "Widget config started for widgetId: $appWidgetId")
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            LogUtil.e(TAG, "Invalid widget ID, finishing")
            finish()
            return
        }
        
        setContent {
            RikkahubTheme {
                val settings by settingsStore.settingsFlow.collectAsState()
                val assistants = settings.assistants
                val defaultName = stringResource(R.string.assistant_page_default_assistant)
                
                var selectedAssistantId by rememberSaveable { mutableStateOf<String?>(null) }
                var selectedPresetId by rememberSaveable { mutableStateOf(WidgetAvatarPreset.Happy.id) }
                var saving by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                val selectedAssistant = assistants.firstOrNull { it.id.toString() == selectedAssistantId }
                val animated = selectedAssistant?.avatar as? Avatar.Animated

                BackHandler(enabled = selectedAssistant != null || saving) {
                    if (!saving) {
                        selectedAssistantId = null
                        error = null
                    }
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(if (animated != null) "Choose an expression"
                                    else stringResource(R.string.widget_config_title))
                            },
                            navigationIcon = {
                                if (selectedAssistant != null) {
                                    TextButton(enabled = !saving, onClick = {
                                        selectedAssistantId = null
                                        error = null
                                    }) { Text("Back") }
                                }
                            },
                        )
                    },
                    bottomBar = {
                        if (animated != null) {
                            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                Button(
                                    enabled = !saving,
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        saving = true
                                        error = null
                                        onAssistantSelected(requireNotNull(selectedAssistant), selectedPresetId) {
                                            saving = false
                                            error = "Couldn't add widget. Please try again."
                                        }
                                    },
                                ) { Text(if (saving) "Adding…" else "Add widget") }
                            }
                        }
                    },
                ) { padding ->
                    if (animated != null) {
                        Column(Modifier.fillMaxSize().padding(padding)) {
                            Text(
                                text = "Pick a look for ${selectedAssistant?.name?.ifEmpty { defaultName } ?: defaultName}. " +
                                    "This expression will stay on your home screen.",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            WidgetExpressionPicker(
                                avatar = animated,
                                selectedPresetId = selectedPresetId,
                                enabled = !saving,
                                onSelect = { selectedPresetId = it },
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            error?.let { message ->
                                item { Text(message, color = MaterialTheme.colorScheme.error) }
                            }
                            items(assistants, key = { it.id.toString() }) { assistant ->
                                AssistantCard(
                                    assistant = assistant,
                                    defaultName = defaultName,
                                    enabled = !saving,
                                    onClick = {
                                        error = null
                                        if (assistant.avatar is Avatar.Animated) {
                                            selectedAssistantId = assistant.id.toString()
                                            selectedPresetId = WidgetAvatarPreset.Happy.id
                                        } else {
                                            saving = true
                                            onAssistantSelected(assistant) {
                                                saving = false
                                                error = "Couldn't add widget. Please try again."
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun onAssistantSelected(
        assistant: Assistant,
        presetId: String? = null,
        onError: () -> Unit,
    ) {
        LogUtil.d(TAG, "Assistant selected: ${assistant.name}, avatar: ${assistant.avatar}")
        
        // Convert avatar to type and data strings
        val (avatarType, avatarData) = when (val avatar = assistant.avatar) {
            is Avatar.Dummy -> "dummy" to ""
            is Avatar.Emoji -> "emoji" to avatar.content
            is Avatar.Image -> "image" to avatar.url
            is Avatar.Resource -> "resource" to avatar.id.toString()
            is Avatar.Animated -> "animated" to JsonInstant.encodeToString(
                Avatar.Animated.serializer(),
                avatar.copy(traits = null)
            )
        }

        val assistantName = assistant.name.ifEmpty { "Assistant" }
        val seed = WidgetPrefs.generateSeed(appWidgetId)
        val initialVariant = 0
        val initialFaceIndex = WidgetAvatarRenderer.faceIndexFor(seed, initialVariant)

        LogUtil.d(TAG, "Saving config: widgetId=$appWidgetId, avatarType=$avatarType, seed=$seed, variant=$initialVariant")

        lifecycleScope.launch {
            try {
                check(WidgetPrefs.saveConfig(
                    context = this@WidgetConfigActivity,
                    widgetId = appWidgetId,
                    assistantId = assistant.id.toString(),
                    assistantName = assistantName,
                    avatarType = avatarType,
                    avatarData = avatarData,
                    seed = seed,
                    variant = initialVariant,
                    faceIndex = initialFaceIndex,
                    presetId = presetId,
                )) { "Could not save widget configuration" }

                val glanceManager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                
                LogUtil.d(TAG, "Updating widget state for glanceId=$glanceId")
                
                // Use the static helper on AssistantWidget to update state and refresh
                AssistantWidget.updateWidgetState(
                    context = this@WidgetConfigActivity,
                    glanceId = glanceId,
                    assistantId = assistant.id.toString(),
                    assistantName = assistantName,
                    avatarType = avatarType,
                    avatarData = avatarData,
                    seed = seed,
                    variant = initialVariant,
                    presetId = presetId,
                )
                
                LogUtil.d(TAG, "Widget state updated successfully")
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error updating widget state", e)
                onError()
                return@launch
            }
            
            // Set result and finish
            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
            LogUtil.d(TAG, "Finishing activity")
            finish()
        }
    }
}

@Composable
private fun AssistantCard(
    assistant: Assistant,
    defaultName: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UIAvatar(
                name = assistant.name.ifEmpty { defaultName },
                value = assistant.avatar,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assistant.name.ifEmpty { defaultName },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (assistant.systemPrompt.isNotBlank()) {
                    Text(
                        text = assistant.systemPrompt.take(50) + if (assistant.systemPrompt.length > 50) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetExpressionPicker(
    avatar: Avatar.Animated,
    selectedPresetId: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val haptics = rememberPremiumHaptics()
    // Render off the UI thread, using exactly the same renderer and pose as the final widget.
    val previews by produceState<Map<String, android.graphics.Bitmap?>>(emptyMap(), avatar) {
        value = withContext(Dispatchers.Default) {
            WidgetAvatarPreset.entries.associate { preset ->
                preset.id to WidgetAvatarRenderer.render(
                    context, avatar, seed = 0, sizePx = 256, variant = 0, presetId = preset.id,
                )
            }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.selectableGroup(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(WidgetAvatarPreset.entries, key = { it.id }) { preset ->
            val selected = selectedPresetId == preset.id
            Card(
                modifier = Modifier.selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = {
                        haptics.perform(HapticPattern.Selection)
                        onSelect(preset.id)
                    },
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                        val bitmap = previews[preset.id]
                        if (bitmap != null) {
                            Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                        } else if (previews.containsKey(preset.id)) {
                            Text("Preview unavailable", textAlign = TextAlign.Center)
                        } else {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = null, enabled = enabled)
                        Spacer(Modifier.width(8.dp))
                        Text(preset.label, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}
