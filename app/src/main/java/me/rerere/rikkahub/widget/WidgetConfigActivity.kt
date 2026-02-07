package me.rerere.rikkahub.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import me.rerere.rikkahub.utils.LogUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.theme.RikkahubTheme
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
                
                LogUtil.d(TAG, "Rendering with ${assistants.size} assistants")
                
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.widget_config_title)) }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(assistants) { assistant ->
                            AssistantCard(
                                assistant = assistant,
                                defaultName = defaultName,
                                onClick = { onAssistantSelected(assistant) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    private fun onAssistantSelected(assistant: Assistant) {
        LogUtil.d(TAG, "Assistant selected: ${assistant.name}, avatar: ${assistant.avatar}")
        
        // Convert avatar to type and data strings
        val (avatarType, avatarData) = when (val avatar = assistant.avatar) {
            is Avatar.Dummy -> "dummy" to ""
            is Avatar.Emoji -> "emoji" to avatar.content
            is Avatar.Image -> "image" to avatar.url
            is Avatar.Resource -> "resource" to avatar.id.toString()
        }
        
        LogUtil.d(TAG, "Saving config: widgetId=$appWidgetId, avatarType=$avatarType, avatarData=$avatarData")
        
        // Update widget state using Glance's state management - this will trigger an update
        lifecycleScope.launch {
            try {
                val glanceManager = GlanceAppWidgetManager(this@WidgetConfigActivity)
                val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                
                LogUtil.d(TAG, "Updating widget state for glanceId=$glanceId")
                
                // Use the static helper on AssistantWidget to update state and refresh
                AssistantWidget.updateWidgetState(
                    context = this@WidgetConfigActivity,
                    glanceId = glanceId,
                    assistantId = assistant.id.toString(),
                    assistantName = assistant.name.ifEmpty { "Assistant" },
                    avatarType = avatarType,
                    avatarData = avatarData
                )
                
                LogUtil.d(TAG, "Widget state updated successfully")
                
            } catch (e: Exception) {
                LogUtil.e(TAG, "Error updating widget state", e)
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
