package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.ttsProviderIconUri
import me.rerere.rikkahub.ui.components.ui.AutoAIIconWithUrl
import me.rerere.tts.provider.TTSProviderSetting

@Composable
fun TTSProviderIcon(
    provider: TTSProviderSetting,
    catalogSnapshot: ModelCatalogSnapshot?,
    modifier: Modifier = Modifier.size(40.dp),
    tint: Color = LocalContentColor.current
) {
    if (provider is TTSProviderSetting.SystemTTS) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tint
            )
        }
    } else {
        AutoAIIconWithUrl(
            name = provider.name,
            customIconUri = provider.customIconUri ?: catalogSnapshot?.ttsProviderIconUri(provider.name),
            modifier = modifier
        )
    }
}
