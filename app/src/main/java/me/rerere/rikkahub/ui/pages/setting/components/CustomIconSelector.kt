package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import org.koin.compose.koinInject

private const val LOBEHUB_PNG_META_URL = "https://unpkg.com/@lobehub/icons-static-png@latest/?meta"

@Composable
fun CustomIconSelector(
    customIconUri: String?,
    onPickFile: () -> Unit,
    onPickLobeHubIcon: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Modifier) -> Unit,
) {
    var showPickOption by remember { mutableStateOf(false) }
    var showLobeHubPicker by remember { mutableStateOf(false) }
    val haptics = rememberPremiumHaptics()
    val hasUserCustomIcon = customIconUri.isUserCustomIconUri()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "customIconSelectorScale",
    )

    Box(modifier = modifier.size(56.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) {
                    haptics.perform(HapticPattern.Pop)
                    if (hasUserCustomIcon) {
                        onReset()
                    } else {
                        showPickOption = true
                    }
                },
        ) {
            icon(Modifier.fillMaxSize())
        }

        if (hasUserCustomIcon) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shadowElevation = 2.dp,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.reset),
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }

    if (showPickOption) {
        AlertDialog(
            onDismissRequest = { showPickOption = false },
            title = {
                Text(text = stringResource(R.string.setting_provider_page_select_icon))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            showPickOption = false
                            onPickFile()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Image, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(R.string.setting_provider_page_pick_icon_file))
                    }
                    Button(
                        onClick = {
                            haptics.perform(HapticPattern.Pop)
                            showPickOption = false
                            showLobeHubPicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Widgets, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(R.string.setting_provider_page_search_lobehub))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showPickOption = false
                    },
                ) {
                    Text(stringResource(id = R.string.cancel))
                }
            },
        )
    }

    if (showLobeHubPicker) {
        LobeHubIconPickerSheet(
            onPickLobeHubIcon = { slug ->
                showLobeHubPicker = false
                onPickLobeHubIcon(slug)
            },
            onDismiss = { showLobeHubPicker = false },
        )
    }
}

@Composable
private fun LobeHubIconPickerSheet(
    onPickLobeHubIcon: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberPremiumHaptics()
    val httpClient = koinInject<PlatformHttpClient>()
    var searchQuery by remember { mutableStateOf("") }
    var fetchAttempt by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var allIcons by remember { mutableStateOf<List<LobeHubIconChoice>>(emptyList()) }

    LaunchedEffect(fetchAttempt) {
        isLoading = true
        loadError = null
        runCatching {
            fetchLobeHubIcons(httpClient)
        }.onSuccess { icons ->
            allIcons = icons
        }.onFailure { error ->
            loadError = error.message ?: "Failed to load icons"
        }
        isLoading = false
    }

    val filteredIcons = remember(searchQuery, allIcons) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            allIcons
        } else {
            allIcons.filter { option ->
                option.label.contains(query, ignoreCase = true) ||
                    option.slug.contains(query, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_provider_page_search_lobehub),
                style = MaterialTheme.typography.headlineSmall,
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.icon_picker_search_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.SearchField,
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                } else {
                    null
                },
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                loadError != null -> {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = loadError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Button(
                            onClick = {
                                haptics.perform(HapticPattern.Pop)
                                fetchAttempt++
                            },
                            shape = AppShapes.ButtonPill,
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 112.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredIcons, key = { it.slug }) { option ->
                            LobeHubIconOption(
                                option = option,
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    onPickLobeHubIcon(option.slug)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LobeHubIconOption(
    option: LobeHubIconChoice,
    onClick: () -> Unit,
) {
    val darkMode = LocalDarkMode.current

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.CardMedium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = lobeHubPngUrl(option.slug, darkMode),
                    contentDescription = option.label,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private suspend fun fetchLobeHubIcons(httpClient: PlatformHttpClient): List<LobeHubIconChoice> {
    return withContext(Dispatchers.IO) {
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = LOBEHUB_PNG_META_URL,
            )
        )
        if (response.statusCode !in 200..299) {
            error("LobeHub icons failed: HTTP ${response.statusCode}")
        }

        val root = JsonInstant.parseToJsonElement(response.body.decodeToString()) as? JsonObject
            ?: error("LobeHub icons response was not an object")
        val files = root["files"] as? JsonArray
            ?: error("LobeHub icons response did not include files")

        files
            .mapNotNull { element ->
                val file = element as? JsonObject ?: return@mapNotNull null
                file["path"]
                    ?.jsonPrimitiveOrNull
                    ?.contentOrNull
            }
            .mapNotNull { path -> path.toLightPngSlugOrNull() }
            .filterNot { slug -> slug.endsWith("-text") }
            .distinct()
            .sorted()
            .map { slug ->
                LobeHubIconChoice(
                    slug = slug,
                    label = slug.toIconLabel(),
                )
            }
    }
}

private fun String.toLightPngSlugOrNull(): String? {
    if (!startsWith("/light/") || !endsWith(".png")) {
        return null
    }
    return substringAfterLast('/')
        .removeSuffix(".png")
        .takeIf { it.isNotBlank() }
}

private fun String.toIconLabel(): String {
    return split('-', '_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            when {
                part.length <= 3 -> part.uppercase()
                else -> part.replaceFirstChar { it.uppercase() }
            }
        }
}

private fun lobeHubPngUrl(slug: String, darkMode: Boolean): String {
    val theme = if (darkMode) "dark" else "light"
    return "https://registry.npmmirror.com/@lobehub/icons-static-png/latest/files/$theme/$slug.png"
}

private fun String?.isUserCustomIconUri(): Boolean {
    if (isNullOrBlank()) return false
    val lower = lowercase()
    return !lower.contains("/catalog/icons/") &&
        !lower.contains("raw.githubusercontent.com/cocolalilal/lastchat") &&
        !lower.startsWith("icons/") &&
        !lower.startsWith("/icons/") &&
        !lower.contains("file:///android_asset/icons/")
}

private data class LobeHubIconChoice(
    val slug: String,
    val label: String,
)
