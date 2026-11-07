package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.service.TmdbService
import me.rerere.rikkahub.ui.components.richtext.ArtifactOrbGenerating
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import me.rerere.rikkahub.ui.components.ui.ImagePreviewDialog
import me.rerere.rikkahub.ui.context.LocalChatSendMessage
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.extractJsonPayload
import me.rerere.rikkahub.utils.openUrl
import me.rerere.rikkahub.utils.writeClipboardText
import org.koin.compose.koinInject
import kotlin.math.absoluteValue

@Serializable
data class MediaDeckItem(
    val id: Int? = null,
    val tmdb_id: Int? = null,
    val type: String = "tv",
    val media_type: String? = null,
    val title: String = "",
    val name: String? = null,
    val year: String = "",
    val rating: String = "",
    val progress: String = "",
    val poster: String = "",
    val backdrop: String? = null,
    val genres: List<String> = emptyList(),
    val badges: List<String> = emptyList(),
    val hook: String = "",
    val commentary: String? = null,
    val glow: String? = null,
)

@Serializable
data class MediaDeckPayload(
    val mode: String = "deck",
    val title: String? = null,
    val note: String? = null,
    val items: List<MediaDeckItem> = emptyList(),
)

private val deckJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * 100% Native Jetpack Compose Media Recommendation & Showcase Artifact.
 * High-performance, butter-smooth native carousel with standard 2:3 aspect-ratio posters,
 * clean typography, full-screen zoom preview, and direct TMDB watchlist integration.
 * Zero WebViews, zero HTML/CSS lag, zero thread contention.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MediaDeckArtifact(
    code: String,
    hasEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    val darkMode = LocalDarkMode.current
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val settings = LocalSettings.current
    val tmdbService = koinInject<TmdbService>()
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val scope = rememberCoroutineScope()

    var showActionSheet by remember { mutableStateOf(false) }
    var selectedPreviewImage by remember { mutableStateOf<String?>(null) }
    var showMediaGallery by remember { mutableStateOf(false) }
    var galleryLoading by remember { mutableStateOf(false) }
    var galleryPosters by remember { mutableStateOf<List<String>>(emptyList()) }
    var galleryBackdrops by remember { mutableStateOf<List<String>>(emptyList()) }
    var galleryError by remember { mutableStateOf<String?>(null) }
    var activeGalleryTmdbId by remember { mutableIntStateOf(0) }

    val payload = remember(code) {
        runCatching {
            val jsonStr = extractJsonPayload(code)
            deckJson.decodeFromString<MediaDeckPayload>(jsonStr)
        }.getOrNull()
    }

    if (payload == null || payload.items.isEmpty()) {
        if (!hasEnd) {
            ArtifactOrbGenerating(modifier = modifier)
        } else {
            HighlightCodeBlock(
                code = code,
                language = "json",
                modifier = modifier,
                completeCodeBlock = true
            )
        }
        return
    }

    val items = payload.items
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { items.size })

    if (showActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Copy Media Deck JSON") },
                    leadingContent = {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    },
                    modifier = Modifier
                        .clickable {
                            context.writeClipboardText(code)
                            toaster.show("Copied deck JSON to clipboard")
                            showActionSheet = false
                        }
                        .padding(vertical = 4.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (selectedPreviewImage != null) {
        ImagePreviewDialog(
            images = listOf(selectedPreviewImage!!),
            onDismissRequest = { selectedPreviewImage = null }
        )
    }

    if (showMediaGallery) {
        val galleryPagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
        val configuration = LocalConfiguration.current
        val sheetMaxHeight = (configuration.screenHeightDp.dp * 0.85f).coerceAtLeast(360.dp)

        ModalBottomSheet(
            onDismissRequest = { showMediaGallery = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight)
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()
                        val currentTitle = currentItem.title.ifBlank { currentItem.name.orEmpty() }
                        Text(
                            text = currentTitle.ifBlank { "Media Gallery" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "TMDB Media Gallery",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                    IconButton(
                        onClick = { showMediaGallery = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tabs: Posters & Backdrops
                SecondaryTabRow(
                    selectedTabIndex = galleryPagerState.currentPage,
                    containerColor = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Tab(
                        selected = galleryPagerState.currentPage == 0,
                        onClick = {
                            scope.launch {
                                galleryPagerState.animateScrollToPage(0)
                            }
                        },
                        text = {
                            val posterCount = galleryPosters.size
                            Text(
                                text = if (posterCount > 0) "Posters ($posterCount)" else "Posters",
                                fontWeight = if (galleryPagerState.currentPage == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = galleryPagerState.currentPage == 1,
                        onClick = {
                            scope.launch {
                                galleryPagerState.animateScrollToPage(1)
                            }
                        },
                        text = {
                            val backdropCount = galleryBackdrops.size
                            Text(
                                text = if (backdropCount > 0) "Backdrops ($backdropCount)" else "Backdrops",
                                fontWeight = if (galleryPagerState.currentPage == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Gallery Pager
                HorizontalPager(
                    state = galleryPagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) { page ->
                    if (galleryLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Fetching media assets...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else if (galleryError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = galleryError ?: "Failed to load media assets",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (page == 0) {
                        // Posters Tab
                        if (galleryPosters.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No posters found for this title",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 105.dp),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(galleryPosters) { _, url ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ),
                                        modifier = Modifier
                                            .aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                haptics.perform(HapticPattern.Pop)
                                                selectedPreviewImage = url
                                            }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(url)
                                                .crossfade(200)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Backdrops Tab
                        if (galleryBackdrops.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No backdrops found for this title",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 180.dp),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(galleryBackdrops) { _, url ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ),
                                        modifier = Modifier
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                haptics.perform(HapticPattern.Pop)
                                                selectedPreviewImage = url
                                            }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(url)
                                                .crossfade(200)
                                                .build(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    AnimatedContent(
        targetState = hasEnd,
        transitionSpec = {
            (fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300)))
                .using(SizeTransform { _, _ -> null })
        },
        label = "MediaDeckPhaseTransition"
    ) { finished ->
        if (!finished) {
            ArtifactOrbGenerating(modifier = modifier)
        } else {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Header Row: Title, Counter & Action Menu
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            val headerTitle = payload.title?.ifBlank { null } ?: "Recommendations"
                            Text(
                                text = headerTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (items.size > 1) {
                                Text(
                                    text = "${pagerState.currentPage + 1} of ${items.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptics.perform(HapticPattern.Pop)
                                    showActionSheet = true
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Deck Options",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Posters Showcase (Fluid Native Carousel with Centered Active Poster)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val configuration = LocalConfiguration.current
                        val isTablet = configuration.screenWidthDp >= 480
                        val posterHeight = if (isTablet) 230.dp else 195.dp
                        val posterWidth = posterHeight * (2f / 3f)
                        val containerWidth = maxWidth

                        if (items.size == 1) {
                            val item = items.first()
                            val posterUrl = item.poster.ifBlank { item.backdrop.orEmpty() }

                            Card(
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                                modifier = Modifier
                                    .width(posterWidth)
                                    .height(posterHeight)
                                    .clickable {
                                        if (posterUrl.isNotBlank()) {
                                            selectedPreviewImage = posterUrl
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(posterUrl)
                                        .crossfade(200)
                                        .build(),
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            val horizontalPadding = ((containerWidth - posterWidth) / 2).coerceAtLeast(16.dp)

                            HorizontalPager(
                                state = pagerState,
                                contentPadding = PaddingValues(horizontal = horizontalPadding),
                                pageSpacing = 16.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(posterHeight + 12.dp)
                            ) { page ->
                                val item = items[page]
                                val posterUrl = item.poster.ifBlank { item.backdrop.orEmpty() }
                                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                                val scale = (1f - (pageOffset * 0.12f)).coerceIn(0.86f, 1f)
                                val alpha = (1f - (pageOffset * 0.35f)).coerceIn(0.55f, 1f)

                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(
                                                alpha = if (page == pagerState.currentPage) 0.65f else 0.25f
                                            )
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ),
                                        modifier = Modifier
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                                this.alpha = alpha
                                            }
                                            .width(posterWidth)
                                            .height(posterHeight)
                                            .clickable {
                                                if (page == pagerState.currentPage) {
                                                    if (posterUrl.isNotBlank()) {
                                                        selectedPreviewImage = posterUrl
                                                    }
                                                } else {
                                                    scope.launch {
                                                        pagerState.animateScrollToPage(page)
                                                    }
                                                }
                                            }
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(posterUrl)
                                                .crossfade(200)
                                                .build(),
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Active Item Details Card
                    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()
                    val currentTitle = currentItem.title.ifBlank { currentItem.name.orEmpty() }
                    val currentRating = currentItem.rating.trim()
                    val currentYear = currentItem.year.trim()
                    val currentProgress = currentItem.progress.trim()
                    val currentHook = currentItem.hook.ifBlank { currentItem.commentary.orEmpty() }.trim()
                    val currentBadges = (currentItem.badges.ifEmpty { currentItem.genres }).filter { it.isNotBlank() }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        // Title & Rating
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (currentRating.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB800),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = currentRating,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Metadata & Chips
                        val metaItems = buildList {
                            if (currentYear.isNotBlank()) add(currentYear)
                            if (currentProgress.isNotBlank()) add(currentProgress)
                            addAll(currentBadges)
                        }
                        if (metaItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                metaItems.forEach { meta ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Velvet's Companion Note / Commentary
                        if (currentHook.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(30.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(2.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "NOTE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        SelectionContainer {
                                            Text(
                                                text = currentHook,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                lineHeight = 20.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Watchlist & Navigation Footer Row
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tmdbId = currentItem.tmdb_id ?: currentItem.id ?: 0
                            var isInWatchlist by remember(currentItem.id, currentItem.tmdb_id) { mutableStateOf(false) }
                            var isMutatingWatchlist by remember(currentItem.id, currentItem.tmdb_id) { mutableStateOf(false) }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Watchlist Button (Always visible)
                                FilledTonalButton(
                                    onClick = {
                                        if (isMutatingWatchlist) return@FilledTonalButton
                                        if (!settings.tmdbSetting.enabled) {
                                            toaster.show("Enable TMDB in Settings -> Plugins -> Movies & TV to sync your watchlist.")
                                            return@FilledTonalButton
                                        }
                                        if (tmdbId <= 0) {
                                            toaster.show("No TMDB ID available for this title.")
                                            return@FilledTonalButton
                                        }
                                        scope.launch {
                                            isMutatingWatchlist = true
                                            val action = if (isInWatchlist) "remove" else "add"
                                            val type = currentItem.media_type ?: currentItem.type
                                            val res = tmdbService.manageWatchlist(action, tmdbId, type)
                                            isMutatingWatchlist = false
                                            val success = res.jsonObject["success"]?.jsonPrimitive?.booleanOrNull ?: false
                                            if (success) {
                                                isInWatchlist = !isInWatchlist
                                                haptics.perform(HapticPattern.Success)
                                                toaster.show(if (isInWatchlist) "Added to TMDB Watchlist" else "Removed from Watchlist")
                                            } else {
                                                val err = res.jsonObject["error"]?.jsonPrimitive?.contentOrNull ?: "Failed to update watchlist"
                                                toaster.show(err)
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    if (isMutatingWatchlist) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    } else {
                                        Icon(
                                            imageVector = if (isInWatchlist) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = if (isInWatchlist) "In Watchlist" else "Watchlist",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                // Tell Agent Button (Ask agent about current title)
                                val chatSendMessage = LocalChatSendMessage.current
                                IconButton(
                                    onClick = {
                                        haptics.perform(HapticPattern.Pop)
                                        val title = currentTitle
                                        if (title.isNotBlank()) {
                                            chatSendMessage("Tell me about this show: $title")
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Tell me about this show",
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Media Gallery Button (Posters & Backdrops)
                                if (tmdbId > 0) {
                                    IconButton(
                                        onClick = {
                                            haptics.perform(HapticPattern.Pop)
                                            if (activeGalleryTmdbId != tmdbId) {
                                                galleryPosters = emptyList()
                                                galleryBackdrops = emptyList()
                                                activeGalleryTmdbId = tmdbId
                                            }
                                            showMediaGallery = true
                                            if (galleryPosters.isEmpty() && galleryBackdrops.isEmpty() && !galleryLoading) {
                                                scope.launch {
                                                    galleryLoading = true
                                                    galleryError = null
                                                    val endpoint = if (currentItem.media_type == "movie" || currentItem.type == "movie") "movie" else "tv"
                                                    val res = tmdbService.getImages(tmdbId, endpoint)
                                                    galleryLoading = false
                                                    val obj = res.jsonObject
                                                    if (obj.containsKey("error")) {
                                                        galleryError = obj["error"]?.jsonPrimitive?.contentOrNull ?: "Failed to fetch images"
                                                    } else {
                                                        galleryPosters = obj["posters"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                                                        galleryBackdrops = obj["backdrops"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Collections,
                                            contentDescription = "Media Gallery",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // External TMDB Info Button
                                if (tmdbId > 0) {
                                    val endpoint = if (currentItem.media_type == "movie" || currentItem.type == "movie") "movie" else "tv"
                                    IconButton(
                                        onClick = {
                                            haptics.perform(HapticPattern.Pop)
                                            context.openUrl("https://www.themoviedb.org/$endpoint/$tmdbId")
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.OpenInNew,
                                            contentDescription = "Open in TMDB",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Pager Navigation (Chevrons + Animated Dots)
                            if (items.size > 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                        },
                                        enabled = pagerState.currentPage > 0,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronLeft,
                                            contentDescription = "Previous",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Capsule Page Indicators
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        items.indices.forEach { index ->
                                            val isSelected = index == pagerState.currentPage
                                            val dotWidth by animateDpAsState(
                                                targetValue = if (isSelected) 14.dp else 5.dp,
                                                label = "dotWidth"
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .height(5.dp)
                                                    .width(dotWidth)
                                                    .background(
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(100)
                                                    )
                                                    .clickable {
                                                        scope.launch {
                                                            pagerState.animateScrollToPage(index)
                                                        }
                                                    }
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                        },
                                        enabled = pagerState.currentPage < items.size - 1,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = "Next",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
