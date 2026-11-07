package me.rerere.rikkahub.ui.pages.assistant.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.AssistantMediaCategory
import me.rerere.rikkahub.data.model.AssistantMediaItem
import me.rerere.rikkahub.data.repository.AssistantMediaRepository
import me.rerere.rikkahub.data.service.TmdbService
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.AppShapes
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import org.koin.compose.koinInject

@Composable
fun AssistantMediaPage(
    assistantId: String,
    mediaRepo: AssistantMediaRepository = koinInject(),
    tmdbService: TmdbService = koinInject(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val haptics = rememberPremiumHaptics()
    val navController = LocalNavController.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()

    val mediaFlow = remember(assistantId) { mediaRepo.getMediaFlow(assistantId) }
    val allItems by mediaFlow.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val categories = remember {
        listOf("All") + AssistantMediaCategory.entries.map { it.label }
    }

    val filteredItems = remember(allItems, selectedTabIndex, searchQuery) {
        val selectedCategoryName = if (selectedTabIndex == 0) null else {
            AssistantMediaCategory.entries[selectedTabIndex - 1].name
        }
        allItems.filter { item ->
            val matchesCategory = selectedCategoryName == null || item.status == selectedCategoryName
            val matchesSearch = searchQuery.isBlank() || item.title.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = "Media & Watchlist",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = "Add Media")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptics.perform(HapticPattern.Tick)
                    showSearchDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add title")
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding)
        ) {
            // Category Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 16.dp,
                divider = {},
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEachIndexed { index, title ->
                    val count = if (index == 0) allItems.size else {
                        val catName = AssistantMediaCategory.entries[index - 1].name
                        allItems.count { it.status == catName }
                    }
                    val isSelected = selectedTabIndex == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            haptics.perform(HapticPattern.Selection)
                            selectedTabIndex = index
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (count > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 3-Column Tablet Grid
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = if (selectedTabIndex == 0) "No titles saved yet" else "No titles in ${categories[selectedTabIndex]}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap + to search TMDB and add movies or series",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { "${it.mediaType}_${it.tmdbId}" }) { item ->
                        MediaPosterCard(
                            item = item,
                            onClick = {
                                haptics.perform(HapticPattern.Tick)
                                navController.navigate(
                                    Screen.AssistantMediaDetail(
                                        assistantId = assistantId,
                                        mediaId = item.tmdbId,
                                        mediaType = item.mediaType
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSearchDialog) {
        var tmdbQuery by remember { mutableStateOf("") }
        var isSearching by remember { mutableStateOf(false) }
        var searchResults by remember { mutableStateOf<List<AssistantMediaItem>>(emptyList()) }

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = {
                Text("Search TMDB", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = tmdbQuery,
                        onValueChange = { tmdbQuery = it },
                        placeholder = { Text("Search movie or show...") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (tmdbQuery.isNotEmpty()) {
                                IconButton(onClick = { tmdbQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (tmdbQuery.isNotBlank()) {
                                isSearching = true
                                scope.launch {
                                    val res = tmdbService.searchMedia(tmdbQuery.trim(), "multi")
                                    val resultsArray = res.jsonObject["results"]?.jsonArray
                                    if (resultsArray != null) {
                                        searchResults = resultsArray.mapNotNull { element ->
                                            val obj = element.jsonObject
                                            val id = obj["id"]?.jsonPrimitive?.intOrNull?.toLong() ?: return@mapNotNull null
                                            val mediaType = obj["media_type"]?.jsonPrimitive?.contentOrNull ?: "tv"
                                            if (mediaType !in listOf("tv", "movie")) return@mapNotNull null
                                            val title = obj["title"]?.jsonPrimitive?.contentOrNull
                                                ?: obj["name"]?.jsonPrimitive?.contentOrNull
                                                ?: "Unknown"
                                            val poster = obj["poster"]?.jsonPrimitive?.contentOrNull
                                                ?: obj["poster_path"]?.jsonPrimitive?.contentOrNull?.let { "https://image.tmdb.org/t/p/w342$it" }
                                            val backdrop = obj["backdrop_path"]?.jsonPrimitive?.contentOrNull?.let { "https://image.tmdb.org/t/p/w780$it" }
                                            val overview = obj["overview"]?.jsonPrimitive?.contentOrNull
                                            val year = obj["year"]?.jsonPrimitive?.contentOrNull
                                                ?: obj["release_date"]?.jsonPrimitive?.contentOrNull?.take(4)
                                                ?: obj["first_air_date"]?.jsonPrimitive?.contentOrNull?.take(4)
                                            val rating = obj["rating"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                                                ?: obj["vote_average"]?.jsonPrimitive?.doubleOrNull

                                            AssistantMediaItem(
                                                tmdbId = id,
                                                mediaType = mediaType,
                                                title = title,
                                                posterPath = poster,
                                                backdropPath = backdrop,
                                                overview = overview,
                                                releaseDate = year,
                                                voteAverage = rating,
                                                status = AssistantMediaCategory.PLAN_TO_WATCH.name
                                            )
                                        }
                                    }
                                    isSearching = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Search")
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        Text(
                            text = "Results (${searchResults.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults) { result ->
                                Surface(
                                    onClick = {
                                        scope.launch {
                                            mediaRepo.upsertItem(assistantId, result)
                                            haptics.perform(HapticPattern.Success)
                                            toaster.show("Added ${result.title} to Plan to Watch")
                                            showSearchDialog = false
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 40.dp, height = 60.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                        ) {
                                            if (result.posterPath != null) {
                                                AsyncImage(
                                                    model = result.posterPath,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = result.title,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${result.mediaType.uppercase()} • ${result.releaseDate.orEmpty()}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            Icons.Rounded.Add,
                                            contentDescription = "Add",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun MediaPosterCard(
    item: AssistantMediaItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (item.posterPath != null) {
                AsyncImage(
                    model = item.posterPath,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.mediaType == "tv") Icons.Rounded.Tv else Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Watching badge overlay on top-right
            if (item.status == AssistantMediaCategory.WATCHING.name && item.mediaType == "tv") {
                Surface(
                    shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = "S${item.currentSeason} E${item.currentEpisode}",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val yearText = item.releaseDate?.take(4) ?: ""
        if (yearText.isNotBlank()) {
            Text(
                text = yearText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}
