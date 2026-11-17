package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.AdditionalContext
import me.rerere.rikkahub.ui.pages.setting.components.AddonEmptyState
import me.rerere.rikkahub.ui.pages.setting.components.AddonListItem
import me.rerere.rikkahub.ui.pages.setting.components.addonItemPosition
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.utils.plus

@Composable
internal fun AdditionalContextContent(
    settings: Settings,
    onEdit: (AdditionalContext) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    if (settings.additionalContexts.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            AddonEmptyState(Icons.Rounded.Notes, "No additional context")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().consumeWindowInsets(contentPadding),
        contentPadding = contentPadding + PaddingValues(16.dp) + PaddingValues(bottom = 45.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(settings.additionalContexts, key = { _, it -> it.id }) { index, snippet ->
            AddonListItem(
                title = snippet.name.ifBlank { "Untitled context" }, subtitle = snippet.prompt,
                position = addonItemPosition(index, settings.additionalContexts.size),
                leading = {
                    Box(Modifier.size(40.dp).clip(AppShapes.Avatar).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Notes, null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp))
                    }
                },
                trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(20.dp)) },
                onClick = { onEdit(snippet) },
            )
        }
    }
}
