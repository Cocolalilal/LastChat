package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.AdditionalContext
import me.rerere.rikkahub.ui.pages.setting.components.SettingGroupItem
import me.rerere.rikkahub.ui.pages.setting.components.SettingsGroup

@Composable
internal fun AdditionalContextContent(
    settings: Settings,
    onEdit: (AdditionalContext) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 45.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            SettingsGroup(title = "") {
                settings.additionalContexts.forEach { snippet ->
                    SettingGroupItem(
                        title = snippet.name.ifBlank { "Untitled context" },
                        subtitle = snippet.prompt.lineSequence().firstOrNull { it.isNotBlank() }?.take(100),
                        icon = { Icon(Icons.Rounded.Notes, contentDescription = null) },
                        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                        onClick = { onEdit(snippet) },
                    )
                }
            }
        }
    }
}
