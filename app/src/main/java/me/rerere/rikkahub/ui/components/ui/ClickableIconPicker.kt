package me.rerere.rikkahub.ui.components.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.rememberAvatarShape
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.ImageUtils

// Common LobeHub provider slugs for quick selection
private val LOBEHUB_PROVIDER_SLUGS = listOf(
    "openai", "anthropic", "google", "mistral", "meta", "deepseek", 
    "cohere", "ai21", "alibaba", "baidu", "bytedance", "tencent",
    "openrouter", "perplexity", "together", "fireworks", "groq",
    "replicate", "stability", "midjourney", "runway", "elevenlabs",
    "minimax", "moonshot", "zhipu", "01-ai", "spark", "hunyuan"
)

/**
 * A clickable icon picker that allows users to select a custom icon image.
 * 
 * States:
 * - No custom icon: Shows defaultContent, tap to open selection dialog
 * - Has custom icon: Shows custom icon with X badge, tap to clear
 * 
 * @param currentIconUri The current custom icon URI (null = no custom icon)
 * @param defaultContent Composable to show when no custom icon is set
 * @param onIconSelected Called when user picks an image, with the persisted URI
 * @param onIconCleared Called when user clears the custom icon
 * @param modifier Modifier for the outer container
 * @param iconSize Size of the icon
 */
@Composable
fun ClickableIconPicker(
    currentIconUri: String?,
    defaultContent: @Composable () -> Unit,
    onIconSelected: (Uri) -> Unit,
    onIconCleared: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 40.dp
) {
    val context = LocalContext.current
    var showSelectionDialog by remember { mutableStateOf(false) }
    var showLobeHubSearch by remember { mutableStateOf(false) }
    
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            // Copy to app's internal storage to persist
            val persistedUri = ImageUtils.copyImageToInternalStorage(context, uri, "custom_icon_${System.currentTimeMillis()}.png")
            if (persistedUri != null) {
                onIconSelected(persistedUri)
            }
        }
    }
    
    val hasCustomIcon = !currentIconUri.isNullOrBlank()
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Main icon area - clickable
        Surface(
            modifier = Modifier
                .size(iconSize)
                .clip(rememberAvatarShape(false))
                .clickable {
                    if (hasCustomIcon) {
                        // Clear the custom icon
                        onIconCleared()
                    } else {
                        // Show selection dialog
                        showSelectionDialog = true
                    }
                },
            shape = rememberAvatarShape(false),
            color = Color.Transparent
        ) {
            if (hasCustomIcon) {
                // Show custom icon
                AsyncImage(
                    model = Uri.parse(currentIconUri),
                    contentDescription = "Custom icon",
                    modifier = Modifier.size(iconSize),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Show default content
                defaultContent()
            }
        }
        
        // X badge when custom icon is set
        if (hasCustomIcon) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear icon",
                    modifier = Modifier
                        .size(12.dp)
                        .padding(1.dp),
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
    
    // Selection dialog
    if (showSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showSelectionDialog = false },
            title = { Text(stringResource(R.string.setting_provider_page_select_icon)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Option 1: Pick from gallery
                    Surface(
                        onClick = {
                            showSelectionDialog = false
                            imagePickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null)
                            Text(stringResource(R.string.setting_provider_page_pick_from_gallery))
                        }
                    }
                    
                    // Option 2: Search LobeHub
                    Surface(
                        onClick = {
                            showSelectionDialog = false
                            showLobeHubSearch = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                            Text(stringResource(R.string.setting_provider_page_search_lobehub))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSelectionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    
    // LobeHub search dialog
    if (showLobeHubSearch) {
        LobeHubIconSearchDialog(
            onDismiss = { showLobeHubSearch = false },
            onIconSelected = { url ->
                showLobeHubSearch = false
                // Save the LobeHub URL directly (it's a web URL, no need to copy locally)
                onIconSelected(Uri.parse(url))
            }
        )
    }
}

@Composable
private fun LobeHubIconSearchDialog(
    onDismiss: () -> Unit,
    onIconSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val darkMode = LocalDarkMode.current
    // Monochrome icons: dark mode = dark icons, light mode = light icons (inverted for visibility)
    val monoTheme = if (darkMode) "dark" else "light"
    
    // Create the list of slugs to show: user's query first (if not empty), then filtered presets
    val displaySlugs = remember(searchQuery) {
        val query = searchQuery.trim().lowercase().replace(" ", "-").replace("_", "-")
        if (query.isBlank()) {
            LOBEHUB_PROVIDER_SLUGS
        } else {
            // Put user's exact query first, then matching presets (excluding duplicates)
            val matchingPresets = LOBEHUB_PROVIDER_SLUGS.filter { 
                it.contains(query, ignoreCase = true) 
            }
            listOf(query) + matchingPresets.filter { it != query }
        }
    }
    
    // Create list of monochrome icons only
    data class IconOption(val url: String, val slug: String, val key: String)
    val iconOptions = remember(displaySlugs, monoTheme) {
        displaySlugs.map { slug ->
            IconOption(
                url = "https://registry.npmmirror.com/@lobehub/icons-static-png/latest/files/$monoTheme/$slug.png",
                slug = slug,
                key = slug
            )
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_provider_page_search_lobehub)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.setting_provider_page_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = me.rerere.rikkahub.ui.theme.AppShapes.SearchField
                )
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(iconOptions.size, key = { iconOptions[it].key }) { index ->
                        val option = iconOptions[index]
                        LobeHubIconItem(
                            url = option.url,
                            slug = option.slug,
                            onSelect = { onIconSelected(option.url) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun LobeHubIconItem(
    url: String,
    slug: String,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.size(56.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        AsyncImage(
            model = url,
            contentDescription = slug,
            modifier = Modifier
                .padding(8.dp)
                .size(40.dp),
            contentScale = ContentScale.Fit
        )
    }
}
