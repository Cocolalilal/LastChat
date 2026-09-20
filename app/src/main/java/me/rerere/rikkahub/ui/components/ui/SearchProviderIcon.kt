package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.data.ai.models.ModelCatalogSnapshot
import me.rerere.rikkahub.data.ai.models.searchProviderIconUri
import me.rerere.search.SearchServiceOptions

@Composable
fun SearchProviderIcon(
    name: String,
    modifier: Modifier = Modifier,
    service: SearchServiceOptions? = null,
    catalogSnapshot: ModelCatalogSnapshot? = null,
    contentColor: Color = LocalContentColor.current,
    color: Color = Color.Transparent,
) {
    if (isKeylessSearchProvider(name, service)) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = name,
            modifier = modifier,
            tint = contentColor,
        )
        return
    }
    AutoAIIconWithUrl(
        name = name,
        customIconUri = catalogSnapshot?.searchProviderIconUri(name),
        modifier = modifier,
        color = color,
        contentColor = contentColor,
    )
}

internal fun isKeylessSearchProvider(
    name: String,
    service: SearchServiceOptions? = null,
): Boolean {
    if (service is SearchServiceOptions.KeylessOptions) return true
    return name.equals("Keyless", ignoreCase = true)
}
