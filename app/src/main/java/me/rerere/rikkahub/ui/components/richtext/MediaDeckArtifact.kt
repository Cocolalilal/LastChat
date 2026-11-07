package me.rerere.rikkahub.ui.components.richtext

import android.annotation.SuppressLint
import android.util.LruCache
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.ui.components.richtext.HighlightCodeBlock
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.HapticPattern
import me.rerere.rikkahub.ui.hooks.rememberPremiumHaptics
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.extractJsonPayload
import me.rerere.rikkahub.utils.writeClipboardText

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

private val mediaDeckHeightCache = LruCache<String, Int>(60)

private val deckJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

private class MediaDeckAndroidBridge(
    private val onAddToWatchlist: (id: Int, type: String) -> Unit,
    private val onHeightChanged: (Int) -> Unit
) {
    @JavascriptInterface
    fun addToTmdbWatchlist(id: Int, type: String) {
        onAddToWatchlist(id, type)
    }

    @JavascriptInterface
    fun onHeightChanged(h: Int) {
        onHeightChanged(h)
    }
}

/**
 * 3D Interactive Media Recommendation Deck & Visual Showcase artifact.
 * Supports arbitrary number of items (N >= 1) with Cover Flow geometry, touch gestures,
 * ambient theme glow, and live TMDB watchlist integration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
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
    val haptics = rememberPremiumHaptics(enabled = settings.displaySetting.enableUIHaptics)
    val scope = rememberCoroutineScope()
    var showActionSheet by remember { mutableStateOf(false) }

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

    val estimatedInitial = remember(code) {
        mediaDeckHeightCache.get(code) ?: 420
    }
    var contentHeightDp by remember(code) {
        mutableIntStateOf(estimatedInitial)
    }

    val bridge = remember(code) {
        MediaDeckAndroidBridge(
            onAddToWatchlist = { id, type ->
                haptics.perform(HapticPattern.Success)
                toaster.show("Added to Watchlist")
            },
            onHeightChanged = { measuredHeight ->
                if (measuredHeight > 50) {
                    val finalH = measuredHeight + 10
                    contentHeightDp = finalH
                    mediaDeckHeightCache.put(code, finalH)
                }
            }
        )
    }

    val htmlDoc = remember(payload, darkMode) {
        buildDeckDocument(payload, darkMode)
    }

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
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Button(
                    onClick = {
                        writeClipboardText(context, code)
                        toaster.show("Copied deck JSON to clipboard")
                        showActionSheet = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Copy Code")
                }
            }
        }
    }

    AnimatedContent(
        targetState = hasEnd,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "MediaDeckPhaseTransition"
    ) { finished ->
        if (!finished) {
            ArtifactOrbGenerating(modifier = modifier)
        } else {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .height(contentHeightDp.dp)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.TopStart
            ) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val wv = WebView(ctx).apply {
                                setBackgroundColor(0)
                                overScrollMode = View.OVER_SCROLL_NEVER
                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false

                                with(this.settings) {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                }

                                addJavascriptInterface(bridge, "AndroidInterface")
                                loadDataWithBaseURL("https://image.tmdb.org", htmlDoc, "text/html", "UTF-8", null)
                            }
                            addView(
                                wv,
                                FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    },
                    update = { container ->
                        val webView = container.getChildAt(0) as? WebView ?: return@AndroidView
                        if (webView.tag != htmlDoc) {
                            webView.tag = htmlDoc
                            webView.loadDataWithBaseURL("https://image.tmdb.org", htmlDoc, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(contentHeightDp.dp)
                )

                // Top-right Menu Button (Direct Options Access)
                IconButton(
                    onClick = {
                        haptics.perform(HapticPattern.Pop)
                        showActionSheet = true
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Deck Options",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun buildDeckDocument(payload: MediaDeckPayload, darkMode: Boolean): String {
    val items = payload.items
    val total = items.size

    val jsonItems = buildString {
        append("[")
        items.forEachIndexed { i, item ->
            val finalId = item.tmdb_id ?: item.id ?: 0
            val finalTitle = item.title.ifBlank { item.name.orEmpty() }.replace("\"", "\\\"")
            val finalType = item.media_type ?: item.type
            val finalHook = item.hook.ifBlank { item.commentary.orEmpty() }.replace("\"", "\\\"")
            val finalRating = item.rating.ifBlank { "8.0" }
            val finalYear = item.year.ifBlank { "" }
            val finalProgress = item.progress.ifBlank { "" }.replace("\"", "\\\"")
            val posterUrl = item.poster.ifBlank { item.backdrop.orEmpty() }
            val glowColor = item.glow ?: "rgba(225, 29, 72, 0.45)"
            val badgesList = (item.badges.ifEmpty { item.genres }).joinToString(",") { "\"${it.replace("\"", "")}\"" }

            append("""{
                id: $finalId,
                type: "$finalType",
                title: "$finalTitle",
                year: "$finalYear",
                rating: "$finalRating",
                progress: "$finalProgress",
                genres: [$badgesList],
                glow: "$glowColor",
                poster: "$posterUrl",
                hook: "$finalHook"
            }""")
            if (i < items.size - 1) append(",")
        }
        append("]")
    }

    val headerTitle = payload.title?.replace("\"", "\\\"") ?: "Velvet Picks"
    val isSingle = total == 1

    return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
  <style>
    :root {
      --bg-surface: #14151b;
      --bg-card: rgba(25, 27, 36, 0.75);
      --bg-accent: rgba(225, 29, 72, 0.12);
      --border-subtle: rgba(255, 255, 255, 0.08);
      --border-accent: rgba(225, 29, 72, 0.35);
      --text-main: #f3f4f6;
      --text-muted: #9ca3af;
      --text-dim: #6b7280;
      --accent-color: #f43f5e;
      --accent-glow: rgba(244, 63, 94, 0.4);
      --star-gold: #fbbf24;
      --btn-bg: rgba(255, 255, 255, 0.07);
      --btn-hover: rgba(255, 255, 255, 0.12);
    }
    * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; user-select: none; }
    html, body {
      background: transparent !important;
      color: var(--text-main);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      overflow: hidden !important;
      height: auto !important;
      width: 100%;
    }
    #deck-root {
      width: 100%;
      max-width: 390px;
      margin: 0 auto;
      background: var(--bg-surface);
      border: 1px solid var(--border-subtle);
      border-radius: 28px;
      padding: 20px 16px 18px;
      box-shadow: 0 20px 50px rgba(0, 0, 0, 0.7);
      position: relative;
      overflow: hidden;
    }
    .ambient-glow {
      position: absolute;
      top: -40px;
      left: 50%;
      transform: translateX(-50%);
      width: 260px;
      height: 180px;
      background: radial-gradient(circle, var(--accent-glow) 0%, transparent 70%);
      opacity: 0.55;
      filter: blur(40px);
      pointer-events: none;
      transition: background 0.6s ease;
    }
    .deck-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 14px;
      position: relative;
      z-index: 2;
    }
    .deck-label {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 11px;
      font-weight: 700;
      letter-spacing: 0.8px;
      text-transform: uppercase;
      color: var(--accent-color);
      background: var(--bg-accent);
      padding: 4px 10px;
      border-radius: 100px;
      border: 1px solid var(--border-accent);
    }
    .deck-label svg { width: 12px; height: 12px; fill: currentColor; }
    .deck-counter { font-size: 12px; font-weight: 600; color: var(--text-dim); }
    .stage-3d {
      position: relative;
      width: 100%;
      height: 236px;
      perspective: 1100px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16px;
      touch-action: pan-y;
    }
    .carousel-track {
      position: relative;
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      transform-style: preserve-3d;
    }
    .poster-card {
      position: absolute;
      width: 145px;
      height: 218px;
      border-radius: 16px;
      overflow: hidden;
      cursor: pointer;
      transform-origin: center center;
      transition: transform 0.48s cubic-bezier(0.2, 0.8, 0.25, 1), opacity 0.45s ease, filter 0.45s ease, box-shadow 0.45s ease;
      background: #1e2029;
      box-shadow: 0 16px 36px rgba(0, 0, 0, 0.65);
    }
    .poster-card img { width: 100%; height: 100%; object-fit: cover; display: block; pointer-events: none; }
    .poster-card .card-shine {
      position: absolute;
      inset: 0;
      background: linear-gradient(135deg, rgba(255,255,255,0.2) 0%, transparent 60%);
      pointer-events: none;
      opacity: 0;
      transition: opacity 0.4s ease;
    }
    .poster-card.state-center {
      transform: translateX(0) translateZ(45px) rotateY(0deg) scale(1);
      z-index: 10;
      opacity: 1;
      filter: brightness(1);
      box-shadow: 0 20px 45px rgba(0, 0, 0, 0.8), 0 0 20px var(--accent-glow);
      border: 1px solid rgba(255, 255, 255, 0.16);
    }
    .poster-card.state-center .card-shine { opacity: 1; }
    .poster-card.state-left {
      transform: translateX(-66%) translateZ(-40px) rotateY(30deg) scale(0.82);
      z-index: 5;
      opacity: 0.55;
      filter: brightness(0.65);
      border: 1px solid rgba(255, 255, 255, 0.05);
    }
    .poster-card.state-right {
      transform: translateX(66%) translateZ(-40px) rotateY(-30deg) scale(0.82);
      z-index: 5;
      opacity: 0.55;
      filter: brightness(0.65);
      border: 1px solid rgba(255, 255, 255, 0.05);
    }
    .poster-card.state-hidden {
      transform: translateX(0) translateZ(-120px) scale(0.6);
      z-index: 1;
      opacity: 0;
      pointer-events: none;
    }
    .nav-arrow {
      position: absolute;
      top: 50%;
      transform: translateY(-50%);
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: rgba(20, 22, 30, 0.85);
      border: 1px solid rgba(255, 255, 255, 0.12);
      color: var(--text-main);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      z-index: 20;
      backdrop-filter: blur(8px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5);
      transition: background 0.2s, transform 0.2s;
    }
    .nav-arrow:active { transform: translateY(-50%) scale(0.92); }
    .nav-arrow.prev { left: 4px; }
    .nav-arrow.next { right: 4px; }
    .nav-arrow svg { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-width: 2.2; stroke-linecap: round; stroke-linejoin: round; }
    .content-sheet { position: relative; z-index: 2; transition: opacity 0.25s ease, transform 0.25s ease; }
    .content-sheet.animating { opacity: 0.4; transform: translateY(4px); }
    .title-row { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 6px; }
    .show-title { font-size: 19px; font-weight: 700; color: var(--text-main); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .show-rating {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-size: 13px;
      font-weight: 700;
      color: var(--star-gold);
      background: rgba(251, 191, 36, 0.1);
      padding: 3px 8px;
      border-radius: 6px;
      border: 1px solid rgba(251, 191, 36, 0.2);
      flex-shrink: 0;
    }
    .show-rating svg { width: 13px; height: 13px; fill: currentColor; }
    .badges-row { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
    .meta-pill {
      font-size: 11px;
      font-weight: 600;
      color: var(--text-muted);
      background: rgba(255, 255, 255, 0.05);
      padding: 3px 8px;
      border-radius: 6px;
      border: 1px solid rgba(255, 255, 255, 0.05);
    }
    .meta-pill.highlight { color: #38bdf8; background: rgba(56, 189, 248, 0.1); border-color: rgba(56, 189, 248, 0.25); }
    .velvet-hook {
      background: var(--bg-card);
      border: 1px solid var(--border-subtle);
      border-left: 3px solid var(--accent-color);
      border-radius: 12px;
      padding: 10px 12px;
      margin-bottom: 14px;
    }
    .velvet-hook-label { font-size: 10px; font-weight: 800; letter-spacing: 0.6px; text-transform: uppercase; color: var(--accent-color); margin-bottom: 4px; }
    .velvet-hook-text { font-size: 13px; line-height: 1.45; color: #e5e7eb; font-style: italic; }
    .actions-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
    .action-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      height: 40px;
      border-radius: 12px;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      border: 1px solid transparent;
      transition: all 0.2s cubic-bezier(0.2, 0.8, 0.2, 1);
    }
    .action-btn:active { transform: scale(0.97); }
    .btn-secondary { background: var(--btn-bg); border-color: var(--border-subtle); color: var(--text-main); }
    .btn-primary {
      background: linear-gradient(135deg, #e11d48 0%, #be123c 100%);
      color: #ffffff;
      box-shadow: 0 4px 16px rgba(225, 29, 72, 0.35);
    }
    .btn-primary.added { background: rgba(16, 185, 129, 0.15); border-color: rgba(16, 185, 129, 0.4); color: #34d399; box-shadow: none; }
    .action-btn svg { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-width: 2.2; stroke-linecap: round; stroke-linejoin: round; }
    .dots-row { display: flex; align-items: center; justify-content: center; gap: 6px; margin-top: 14px; }
    .dot { width: 6px; height: 6px; border-radius: 100px; background: rgba(255, 255, 255, 0.2); transition: all 0.3s ease; cursor: pointer; }
    .dot.active { width: 20px; background: var(--accent-color); }
  </style>
</head>
<body>
  <div id="deck-root">
    <div class="ambient-glow" id="ambientGlow"></div>
    <div class="deck-header">
      <div class="deck-label">
        <svg viewBox="0 0 24 24"><path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"/></svg>
        $headerTitle
      </div>
      <div class="deck-counter" id="deckCounter">1 of $total</div>
    </div>

    <div class="stage-3d" id="stage">
      ${if (!isSingle) """<button class="nav-arrow prev" id="prevBtn"><svg viewBox="0 0 24 24"><polyline points="15 18 9 12 15 6"></polyline></svg></button>""" else ""}
      <div class="carousel-track" id="carouselTrack"></div>
      ${if (!isSingle) """<button class="nav-arrow next" id="nextBtn"><svg viewBox="0 0 24 24"><polyline points="9 18 15 12 9 6"></polyline></svg></button>""" else ""}
    </div>

    <div class="content-sheet" id="contentSheet">
      <div class="title-row">
        <h2 class="show-title" id="showTitle"></h2>
        <div class="show-rating" id="showRating">
          <svg viewBox="0 0 24 24"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg>
          <span id="ratingVal"></span>
        </div>
      </div>
      <div class="badges-row" id="badgesRow"></div>
      <div class="velvet-hook" id="hookCard">
        <div class="velvet-hook-label">Companion Note</div>
        <div class="velvet-hook-text" id="hookText"></div>
      </div>
      <div class="actions-row">
        <button class="action-btn btn-secondary" id="trackBtn">
          <svg viewBox="0 0 24 24"><polygon points="5 3 19 12 5 21 5 3"></polygon></svg>
          <span id="trackLabel">Log Progress</span>
        </button>
        <button class="action-btn btn-primary" id="watchlistBtn">
          <svg viewBox="0 0 24 24" id="watchlistIcon"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
          <span id="watchlistLabel">Watchlist</span>
        </button>
      </div>
    </div>
    <div class="dots-row" id="dotsRow"></div>
  </div>

  <script>
    const SHOWS = $jsonItems;
    let currentIndex = 0;
    const track = document.getElementById('carouselTrack');
    const dotsRow = document.getElementById('dotsRow');
    const contentSheet = document.getElementById('contentSheet');
    const ambientGlow = document.getElementById('ambientGlow');
    const deckCounter = document.getElementById('deckCounter');
    const watchlistStates = {};

    function initCarousel() {
      track.innerHTML = '';
      dotsRow.innerHTML = '';
      SHOWS.forEach((show, idx) => {
        const card = document.createElement('div');
        card.className = 'poster-card';
        card.id = 'card-' + idx;
        card.innerHTML = '<img src="' + show.poster + '" alt="' + show.title + '" loading="eager" crossorigin="anonymous" onerror="this.style.opacity=\'0.25\'"><div class="card-shine"></div>';
        card.addEventListener('click', () => { if (idx !== currentIndex) goTo(idx); });
        track.appendChild(card);

        const dot = document.createElement('div');
        dot.className = 'dot ' + (idx === currentIndex ? 'active' : '');
        dot.addEventListener('click', () => goTo(idx));
        dotsRow.appendChild(dot);
      });
      updateLayout();
    }

    function updateLayout() {
      const total = SHOWS.length;
      deckCounter.textContent = (currentIndex + 1) + ' of ' + total;
      SHOWS.forEach((_, idx) => {
        const card = document.getElementById('card-' + idx);
        const dot = dotsRow.children[idx];
        card.className = 'poster-card';
        const diff = (idx - currentIndex + total) % total;
        if (diff === 0) {
          card.classList.add('state-center');
        } else if (diff === 1 || (total === 2 && diff === 1)) {
          card.classList.add('state-right');
        } else if (diff === total - 1) {
          card.classList.add('state-left');
        } else {
          card.classList.add('state-hidden');
        }
        if (dot) dot.className = 'dot ' + (idx === currentIndex ? 'active' : '');
      });

      contentSheet.classList.add('animating');
      setTimeout(() => {
        const current = SHOWS[currentIndex];
        document.getElementById('showTitle').textContent = current.title;
        document.getElementById('ratingVal').textContent = current.rating;
        const hookCard = document.getElementById('hookCard');
        if (current.hook && current.hook.trim()) {
          hookCard.style.display = 'block';
          document.getElementById('hookText').textContent = '"' + current.hook + '"';
        } else {
          hookCard.style.display = 'none';
        }
        if (current.progress) {
          document.getElementById('trackLabel').textContent = current.progress;
        }

        ambientGlow.style.background = 'radial-gradient(circle, ' + (current.glow || 'var(--accent-glow)') + ' 0%, transparent 70%)';
        const badgesRow = document.getElementById('badgesRow');
        badgesRow.innerHTML = '';
        if (current.progress) {
          const p = document.createElement('span');
          p.className = 'meta-pill highlight';
          p.textContent = current.progress;
          badgesRow.appendChild(p);
        }
        if (current.year) {
          const y = document.createElement('span');
          y.className = 'meta-pill';
          y.textContent = current.year;
          badgesRow.appendChild(y);
        }
        (current.genres || []).forEach(g => {
          const gp = document.createElement('span');
          gp.className = 'meta-pill';
          gp.textContent = g;
          badgesRow.appendChild(gp);
        });

        updateWatchlistButton(current.id);
        contentSheet.classList.remove('animating');
        reportHeight();
      }, 120);
    }

    function updateWatchlistButton(id) {
      const btn = document.getElementById('watchlistBtn');
      const label = document.getElementById('watchlistLabel');
      const icon = document.getElementById('watchlistIcon');
      if (watchlistStates[id]) {
        btn.classList.add('added');
        label.textContent = 'In Watchlist ✓';
        icon.innerHTML = '<polyline points="20 6 9 17 4 12"></polyline>';
      } else {
        btn.classList.remove('added');
        label.textContent = 'Watchlist';
        icon.innerHTML = '<line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line>';
      }
    }

    function goTo(index) {
      const total = SHOWS.length;
      currentIndex = (index + total) % total;
      updateLayout();
    }

    const prevBtn = document.getElementById('prevBtn');
    if (prevBtn) prevBtn.addEventListener('click', () => goTo(currentIndex - 1));
    const nextBtn = document.getElementById('nextBtn');
    if (nextBtn) nextBtn.addEventListener('click', () => goTo(currentIndex + 1));

    document.getElementById('watchlistBtn').addEventListener('click', () => {
      const current = SHOWS[currentIndex];
      watchlistStates[current.id] = !watchlistStates[current.id];
      updateWatchlistButton(current.id);
      if (window.AndroidInterface && window.AndroidInterface.addToTmdbWatchlist) {
        window.AndroidInterface.addToTmdbWatchlist(current.id, current.type);
      }
    });

    let touchStartX = 0;
    let touchEndX = 0;
    const stage = document.getElementById('stage');
    stage.addEventListener('touchstart', (e) => { touchStartX = e.changedTouches[0].screenX; }, { passive: true });
    stage.addEventListener('touchend', (e) => {
      touchEndX = e.changedTouches[0].screenX;
      const diff = touchEndX - touchStartX;
      if (Math.abs(diff) > 35) {
        if (diff > 0) goTo(currentIndex - 1);
        else goTo(currentIndex + 1);
      }
    }, { passive: true });

    function reportHeight() {
      try {
        const root = document.getElementById('deck-root');
        if (!root) return;
        const rect = root.getBoundingClientRect();
        const h = Math.ceil(rect.height || root.offsetHeight || 0);
        if (h > 50 && window.AndroidInterface && window.AndroidInterface.onHeightChanged) {
          window.AndroidInterface.onHeightChanged(h);
        }
      } catch(e) {}
    }

    window.addEventListener('load', () => {
      reportHeight();
      setTimeout(reportHeight, 150);
      setTimeout(reportHeight, 500);
    });
    if (window.ResizeObserver) {
      new ResizeObserver(reportHeight).observe(document.getElementById('deck-root'));
    }

    initCarousel();
  </script>
</body>
</html>
    """.trimIndent()
}
