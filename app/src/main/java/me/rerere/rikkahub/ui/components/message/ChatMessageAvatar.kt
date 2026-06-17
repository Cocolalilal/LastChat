package me.rerere.rikkahub.ui.components.message
import me.rerere.ai.ui.*

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage

import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.data.datastore.getEffectiveDisplaySetting
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.toLocalString

@Composable
fun ChatMessageUserAvatar(
    message: UIMessage,
    previousRole: MessageRole?,
    avatar: Avatar,
    nickname: String,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val effectiveDisplay = settings.getEffectiveDisplaySetting()
    if (message.role == MessageRole.USER && previousRole != MessageRole.USER && !message.parts.isEmptyUIMessage() && effectiveDisplay.showUserAvatar) {
        Row(
            modifier = modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier,
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = message.createdAt.toJavaLocalDateTime().toLocalTime().toString().substring(0, 5), // HH:mm format
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    maxLines = 1,
                )
                Text(
                    text = nickname.ifEmpty { stringResource(R.string.user_default_name) },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    color = LocalContentColor.current.copy(alpha = 0.85f),
                )
            }
            UIAvatar(
                name = nickname,
                modifier = Modifier.size(36.dp),
                value = avatar,
                loading = false,
            )
        }
    }
}

@Composable
fun ChatMessageAssistantAvatar(
    message: UIMessage,
    previousRole: MessageRole?,
    loading: Boolean,
    model: Model?,
    assistant: Assistant?,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettings.current
    val effectiveDisplay = settings.getEffectiveDisplaySetting()
    val showIcon = effectiveDisplay.showModelIcon
    // Show when role is assistant, previous role is different, and we have either model or assistant info
    if (message.role == MessageRole.ASSISTANT && previousRole != message.role && (model != null || assistant != null)) {
        // Use assistant info when available, otherwise use model name but with UIAvatar (not model icon)
        val defaultName = stringResource(R.string.assistant_page_default_assistant)
        val avatarName = assistant?.name?.ifEmpty { null } ?: model?.displayName ?: defaultName
        val avatarValue = assistant?.avatar ?: Avatar.Dummy
        
        Row(
            modifier = modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showIcon) {
                UIAvatar(
                    name = avatarName,
                    modifier = Modifier.size(36.dp),
                    value = avatarValue,
                    loading = loading,
                )
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if(effectiveDisplay.showModelName) {
                    Text(
                        text = message.createdAt.toJavaLocalDateTime().toLocalTime().toString().substring(0, 5), // HH:mm format
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                    Text(
                        text = avatarName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
