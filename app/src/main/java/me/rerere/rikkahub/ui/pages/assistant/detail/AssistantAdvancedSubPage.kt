package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup
import me.rerere.rikkahub.ui.theme.LocalDarkMode

/**
 * Advanced tab - Message formatting and custom request settings.
 */
@Composable
fun AssistantAdvancedSubPage(
    assistant: Assistant,
    onUpdate: (Assistant) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Message formatting settings moved from Prompts tab
        MessageTemplateSettingsCard(
            assistant = assistant,
            onUpdate = onUpdate
        )

        MessageRegexSettingsCard(
            assistant = assistant,
            onUpdate = onUpdate
        )

        // Custom request settings
        SettingsGroup(title = "Custom Request") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CustomHeaders(
                        headers = assistant.customHeaders,
                        onUpdate = { onUpdate(assistant.copy(customHeaders = it)) }
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (LocalDarkMode.current) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    CustomBodies(
                        customBodies = assistant.customBodies,
                        onUpdate = { onUpdate(assistant.copy(customBodies = it)) }
                    )
                }
            }
        }
    }
}
