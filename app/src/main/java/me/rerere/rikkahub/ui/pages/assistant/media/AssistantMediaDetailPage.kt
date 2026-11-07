package me.rerere.rikkahub.ui.pages.assistant.media

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
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
import me.rerere.rikkahub.data.model.TmdbEpisodeSummary
import me.rerere.rikkahub.data.model.TmdbSeasonSummary
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
fun AssistantMediaDetailPage(
    assistantId: String,
    mediaId: Long,
    mediaType: String,
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
    val savedItem = remember(allItems, mediaId, mediaType) {
        allItems.firstOrNull { it.tmdbId == mediaId && it.mediaType == mediaType }
    }

    var detailsTitle by remember { mutableStateOf(savedItem?.title.orEmpty()) }
    var detailsTagline by remember { mutableStateOf("") }
    var detailsOverview by remember { mutableStateOf(savedItem?.overview.orEmpty()) }
    var detailsPoster by remember { mutableStateOf(savedItem?.posterPath) }
    var detailsBackdrop by remember { mutableStateOf(savedItem?.backdropPath) }
    var detailsRating by remember { mutableStateOf(savedItem?.voteAverage?.toString() ?: "0.0") }
    var detailsYear by remember { mutableStateOf(savedItem?.releaseDate.orEmpty()) }
    var seasonsList by remember { mutableStateOf<List<TmdbSeasonSummary>>(emptyList()) }
    var selectedSeasonNumber by remember { mutableIntStateOf(1) }
    var currentSeasonEpisodes by remember { mutableStateOf<List<TmdbEpisodeSummary>>(emptyList()) }
    var recommendationsList by remember { mutableStateOf<List<AssistantMediaItem>>(emptyList()) }
    var isLoadingDetails by remember { mutableStateOf(true) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    var showCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Fetch Full TMDB Details
    LaunchedEffect(mediaId, mediaType) {
        isLoadingDetails = true
        val detailsJson = tmdbService.getDetails(mediaId.toInt(), mediaType)
        val obj = detailsJson.jsonObject
        if (obj["error"] == null) {
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: detailsTitle
            val tagline = obj["tagline"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val overview = obj["overview"]?.jsonPrimitive?.contentOrNull ?: detailsOverview
            val poster = obj["poster"]?.jsonPrimitive?.contentOrNull ?: detailsPoster
            val backdrop = obj["backdrop"]?.jsonPrimitive?.contentOrNull ?: detailsBackdrop
            val rating = obj["rating"]?.jsonPrimitive?.contentOrNull ?: detailsRating
            val year = obj["year"]?.jsonPrimitive?.contentOrNull ?: detailsYear

            detailsTitle = title
            detailsTagline = tagline
            detailsOverview = overview
            detailsPoster = poster
            detailsBackdrop = backdrop
            detailsRating = rating
            detailsYear = year

            val seasonsRaw = obj["seasons"]?.jsonArray
            if (seasonsRaw != null) {
                seasonsList = seasonsRaw.mapNotNull { item ->
                    val sObj = item.jsonObject
                    val sNum = sObj["season_number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    val sName = sObj["name"]?.jsonPrimitive?.contentOrNull ?: "Season $sNum"
                    val epCount = sObj["episode_count"]?.jsonPrimitive?.intOrNull ?: 0
                    val sOverview = sObj["overview"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val sPoster = sObj["poster"]?.jsonPrimitive?.contentOrNull
                    TmdbSeasonSummary(
                        seasonNumber = sNum,
                        name = sName,
                        episodeCount = epCount,
                        overview = sOverview,
                        poster = sPoster
                    )
                }.filter { it.seasonNumber > 0 } // Filter out specials/season 0 for main list
            }

            // If we have saved item, update its metadata
            if (savedItem != null) {
                mediaRepo.upsertItem(
                    assistantId,
                    savedItem.copy(
                        title = title,
                        overview = overview,
                        posterPath = poster,
                        backdropPath = backdrop,
                        voteAverage = rating.toDoubleOrNull(),
                        releaseDate = year,
                        totalSeasons = seasonsList.size.takeIf { it > 0 },
                        totalEpisodes = seasonsList.sumOf { it.episodeCount }.takeIf { it > 0 }
                    )
                )
            }
        }
        isLoadingDetails = false

        // Fetch Recommendations
        val recsJson = tmdbService.getRecommendations(mediaId.toInt(), mediaType)
        val recsArray = recsJson.jsonObject["recommendations"]?.jsonArray
        if (recsArray != null) {
            recommendationsList = recsArray.mapNotNull { item ->
                val rObj = item.jsonObject
                val rId = rObj["id"]?.jsonPrimitive?.intOrNull?.toLong() ?: return@mapNotNull null
                val rTitle = rObj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val rPoster = rObj["poster"]?.jsonPrimitive?.contentOrNull
                val rRating = rObj["rating"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val rYear = rObj["year"]?.jsonPrimitive?.contentOrNull.orEmpty()
                AssistantMediaItem(
                    tmdbId = rId,
                    mediaType = mediaType,
                    title = rTitle,
                    posterPath = rPoster,
                    voteAverage = rRating,
                    releaseDate = rYear,
                    status = AssistantMediaCategory.PLAN_TO_WATCH.name
                )
            }
        }
    }

    // Fetch Episodes when selected season changes
    LaunchedEffect(mediaId, mediaType, selectedSeasonNumber) {
        if (mediaType == "tv") {
            isLoadingEpisodes = true
            val epJson = tmdbService.getSeasonEpisodes(mediaId.toInt(), selectedSeasonNumber)
            val epArray = epJson.jsonObject["episodes"]?.jsonArray
            if (epArray != null) {
                currentSeasonEpisodes = epArray.mapNotNull { epItem ->
                    val epObj = epItem.jsonObject
                    val epNum = epObj["episode_number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    val sNum = epObj["season_number"]?.jsonPrimitive?.intOrNull ?: selectedSeasonNumber
                    val name = epObj["name"]?.jsonPrimitive?.contentOrNull ?: "Episode $epNum"
                    val overview = epObj["overview"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val airDate = epObj["air_date"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val rating = epObj["rating"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val still = epObj["still"]?.jsonPrimitive?.contentOrNull
                    TmdbEpisodeSummary(
                        episodeNumber = epNum,
                        seasonNumber = sNum,
                        name = name,
                        overview = overview,
                        airDate = airDate,
                        rating = rating,
                        still = still
                    )
                }
            }
            isLoadingEpisodes = false
        }
    }

    Scaffold(
        topBar = {
            OneUITopAppBar(
                title = detailsTitle,
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackButton() },
                actions = {
                    if (savedItem != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove from Library")
                        }
                    }
                }
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .consumeWindowInsets(contentPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 1. Cinematic Backdrop Banner
            item(key = "backdrop") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (detailsBackdrop != null) {
                        AsyncImage(
                            model = detailsBackdrop,
                            contentDescription = detailsTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Bottom gradient fade into surface
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )
                }
            }

            // 2. Hero Card: Floating Poster + Title + Metadata + Category Picker
            item(key = "hero_info") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 2:3 Floating Poster Card
                        Box(
                            modifier = Modifier
                                .width(105.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            if (detailsPoster != null) {
                                AsyncImage(
                                    model = detailsPoster,
                                    contentDescription = detailsTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Title & Metadata
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = detailsTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (detailsTagline.isNotBlank()) {
                                Text(
                                    text = detailsTagline,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (detailsRating.isNotBlank() && detailsRating != "0.0") {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = detailsRating.take(3),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                if (detailsYear.isNotBlank()) {
                                    Text(
                                        text = detailsYear.take(4),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (mediaType == "tv" && seasonsList.isNotEmpty()) {
                                    Text(
                                        text = "•  ${seasonsList.size} Seasons",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Category Status Button ("Pick the appropriate category")
                            val currentCategory = savedItem?.let {
                                AssistantMediaCategory.fromString(it.status)
                            } ?: AssistantMediaCategory.PLAN_TO_WATCH

                            OutlinedButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Tick)
                                    showCategoryDialog = true
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (savedItem == null) "Add to Library" else currentCategory.label,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        Icons.Rounded.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 3. Overview Synopsis
                    if (detailsOverview.isNotBlank()) {
                        Text(
                            text = "Overview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = detailsOverview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }

            // 4. Seasons & Episodes Tracker (TV shows only)
            if (mediaType == "tv" && seasonsList.isNotEmpty()) {
                item(key = "seasons_header") {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Episodes",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Mark all season as watched
                            if (savedItem != null && currentSeasonEpisodes.isNotEmpty()) {
                                val currentSeasonWatchedCount = currentSeasonEpisodes.count { ep ->
                                    savedItem.watchedEpisodes.contains("s${selectedSeasonNumber}e${ep.episodeNumber}")
                                }
                                val allSeasonWatched = currentSeasonWatchedCount == currentSeasonEpisodes.size

                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            mediaRepo.markSeasonWatched(
                                                assistantId = assistantId,
                                                tmdbId = mediaId,
                                                season = selectedSeasonNumber,
                                                episodeCount = currentSeasonEpisodes.size,
                                                watched = !allSeasonWatched
                                            )
                                            haptics.perform(HapticPattern.Success)
                                        }
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.DoneAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (allSeasonWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (allSeasonWatched) "Mark Unwatched" else "Mark Season Watched",
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Season Selector Chips
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(seasonsList) { season ->
                                val isSelected = selectedSeasonNumber == season.seasonNumber
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        haptics.perform(HapticPattern.Selection)
                                        selectedSeasonNumber = season.seasonNumber
                                    },
                                    label = { Text(season.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                }

                // Episode Cards List
                if (isLoadingEpisodes) {
                    item(key = "episodes_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    items(currentSeasonEpisodes, key = { "ep_${it.seasonNumber}_${it.episodeNumber}" }) { ep ->
                        val isWatched = savedItem?.watchedEpisodes?.contains("s${ep.seasonNumber}e${ep.episodeNumber}") == true

                        Surface(
                            onClick = {
                                scope.launch {
                                    // If not in library yet, auto-add to Watching
                                    if (savedItem == null) {
                                        val newItem = AssistantMediaItem(
                                            tmdbId = mediaId,
                                            mediaType = mediaType,
                                            title = detailsTitle,
                                            posterPath = detailsPoster,
                                            backdropPath = detailsBackdrop,
                                            overview = detailsOverview,
                                            releaseDate = detailsYear,
                                            voteAverage = detailsRating.toDoubleOrNull(),
                                            status = AssistantMediaCategory.WATCHING.name,
                                            totalSeasons = seasonsList.size,
                                            totalEpisodes = seasonsList.sumOf { it.episodeCount }
                                        )
                                        mediaRepo.upsertItem(assistantId, newItem)
                                    }
                                    mediaRepo.toggleEpisodeWatched(assistantId, mediaId, ep.seasonNumber, ep.episodeNumber)
                                    haptics.perform(HapticPattern.Tick)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isWatched) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            if (savedItem == null) {
                                                val newItem = AssistantMediaItem(
                                                    tmdbId = mediaId,
                                                    mediaType = mediaType,
                                                    title = detailsTitle,
                                                    posterPath = detailsPoster,
                                                    backdropPath = detailsBackdrop,
                                                    overview = detailsOverview,
                                                    releaseDate = detailsYear,
                                                    voteAverage = detailsRating.toDoubleOrNull(),
                                                    status = AssistantMediaCategory.WATCHING.name
                                                )
                                                mediaRepo.upsertItem(assistantId, newItem)
                                            }
                                            mediaRepo.toggleEpisodeWatched(assistantId, mediaId, ep.seasonNumber, ep.episodeNumber)
                                            haptics.perform(HapticPattern.Success)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isWatched) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = if (isWatched) "Watched" else "Mark Watched",
                                        tint = if (isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "E${ep.episodeNumber} • ${ep.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (ep.airDate.isNotBlank()) {
                                        Text(
                                            text = ep.airDate,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (ep.overview.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = ep.overview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recommendations Carousel
            if (recommendationsList.isNotEmpty()) {
                item(key = "recommendations_header") {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Recommendations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }

                item(key = "recommendations_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(recommendationsList) { rec ->
                            Column(
                                modifier = Modifier
                                    .width(90.dp)
                                    .clickable {
                                        haptics.perform(HapticPattern.Tick)
                                        navController.navigate(
                                            Screen.AssistantMediaDetail(
                                                assistantId = assistantId,
                                                mediaId = rec.tmdbId,
                                                mediaType = rec.mediaType
                                            )
                                        )
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                ) {
                                    if (rec.posterPath != null) {
                                        AsyncImage(
                                            model = rec.posterPath,
                                            contentDescription = rec.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = rec.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // "Pick the appropriate category" Dialog (Matching Screenshot 2)
    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = {
                Text(
                    text = "Pick the appropriate category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AssistantMediaCategory.entries.forEach { cat ->
                        val isSelected = savedItem?.status == cat.name
                        Surface(
                            onClick = {
                                scope.launch {
                                    if (savedItem == null) {
                                        val newItem = AssistantMediaItem(
                                            tmdbId = mediaId,
                                            mediaType = mediaType,
                                            title = detailsTitle,
                                            posterPath = detailsPoster,
                                            backdropPath = detailsBackdrop,
                                            overview = detailsOverview,
                                            releaseDate = detailsYear,
                                            voteAverage = detailsRating.toDoubleOrNull(),
                                            status = cat.name,
                                            totalSeasons = seasonsList.size.takeIf { it > 0 },
                                            totalEpisodes = seasonsList.sumOf { it.episodeCount }.takeIf { it > 0 }
                                        )
                                        mediaRepo.upsertItem(assistantId, newItem)
                                    } else {
                                        mediaRepo.updateStatus(assistantId, mediaId, mediaType, cat.name)
                                    }
                                    haptics.perform(HapticPattern.Success)
                                    toaster.show("Moved to ${cat.label}")
                                    showCategoryDialog = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove from Library?") },
            text = { Text("Remove $detailsTitle and all its watched episode records from this companion's library?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            mediaRepo.deleteItem(assistantId, mediaId, mediaType)
                            haptics.perform(HapticPattern.Success)
                            toaster.show("Removed from library")
                            showDeleteConfirm = false
                            navController.popBackStack()
                        }
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
