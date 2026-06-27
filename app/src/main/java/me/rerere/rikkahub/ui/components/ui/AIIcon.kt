package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.css
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.toCssHex
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.common.http.urlPartsOrNull
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import me.rerere.rikkahub.utils.JsonInstant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import org.koin.compose.koinInject

private const val LOBEHUB_ICON_URI_PREFIX = "lobehub://"

fun lobeHubIconUri(slug: String): String {
    return LOBEHUB_ICON_URI_PREFIX + slug.normalizeLobeHubIconSlug()
}

/**
 * UNIFIED Provider Icon - Use this everywhere a provider icon is needed.
 * Takes the whole ProviderSetting object to ensure consistent display.
 * 
 * Priority:
 * 1. Custom icon (user-selected or catalog-assigned)
 * 2. Text avatar fallback
 */
@Composable
fun ProviderIcon(
    provider: ProviderSetting,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = LocalContentColor.current,
    padding: Dp = 4.dp,
) {
    val effectiveContentColor = if (provider.enabled) {
        contentColor
    } else {
        contentColor.copy(alpha = 0.38f)
    }


    AutoAIIconWithUrl(
        name = provider.name,
        customIconUri = provider.customIconUri,
        modifier = modifier,
        loading = loading,
        color = color,
        contentColor = effectiveContentColor,
        padding = padding
    )
}

/**
 * UNIFIED Model Icon - Use this everywhere a model icon is needed.
 * Takes the whole Model object and its parent provider to ensure consistent display.
 * 
 * Priority:
 * 1. Custom icon (user-selected or catalog-assigned)
 * 2. Catalog icon URL
 * 3. LobeHub provider/family slug
 * 4. Parent provider icon fallback
 * 5. Text avatar fallback
 */
@Composable
fun ModelIcon(
    model: Model,
    provider: ProviderSetting?,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = LocalContentColor.current,
    padding: Dp = 4.dp,
) {
    AutoAIIconWithUrl(
        name = model.displayName.ifBlank { model.modelId },
        iconUrl = model.iconUrl,
        customIconUri = model.customIconUri,
        providerSlug = model.providerSlug,
        modifier = modifier,
        loading = loading,
        color = color,
        contentColor = contentColor,
        padding = padding,
        fallback = provider?.let {
            {
                ProviderIcon(
                    provider = it,
                    modifier = modifier,
                    loading = loading,
                    color = color,
                    contentColor = contentColor,
                    padding = padding,
                )
            }
        },
    )
}

@Composable
private fun AIIcon(
    path: String,
    name: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = LocalContentColor.current,
    padding: Dp = 4.dp,
) {
    val context = LocalContext.current
    val model = remember(path, contentColor, context) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/icons/$path")
            .css(
                """
                svg {
                  color: ${contentColor.toCssHex()};
                  fill: ${contentColor.toCssHex()};
                }
            """.trimIndent()
            )
            .build()
    }
    Box(modifier = modifier.size(24.dp)) {
        AsyncImage(
            model = model,
            contentDescription = name,
            modifier = Modifier.padding(padding),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
fun AutoAIIcon(
    name: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = LocalContentColor.current,
    padding: Dp = 4.dp,
) {
    val path = remember(name) { computeAIIconByName(name) } ?: run {
        TextAvatar(text = name, modifier = modifier, loading = loading, color = color, contentColor = contentColor)
        return
    }
    AIIcon(
        path = path,
        name = name,
        modifier = modifier,
        loading = loading,
        color = color,
        contentColor = contentColor,
        padding = padding,
    )
}

/**
 * Auto icon for Providers (used in the providers page).
 * Uses fallback strategy:
 * 1. Local pattern matching (for known provider names)
 * 2. LobeHub CDN for known provider slugs or derived from name
 * 3. Text avatar (final fallback)
 */
@Composable
fun AutoProviderIcon(
    name: String,
    baseUrl: String? = null,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = LocalContentColor.current,
    padding: Dp = 4.dp,
) {
    val darkMode = LocalDarkMode.current
    
    // Priority 1: Local pattern matching
    val localPath = remember(name) { computeAIIconByName(name) }
    if (localPath != null) {
        AIIcon(
            path = localPath,
            name = name,
            modifier = modifier,
            loading = loading,
            color = color,
            padding = padding,
        )
        return
    }
    
    // Priority 2: LobeHub CDN - try known slug first, then derive from name
    val providerSlug = remember(name) { 
        getProviderSlugFromName(name) ?: name.lowercase().replace(" ", "-").replace("_", "-")
    }
    val lobeHubUrls = getLobeHubIconUrls(providerSlug, darkMode)
    RemoteIcon(
        url = lobeHubUrls.coloredUrl,
        iconKey = me.rerere.rikkahub.utils.IconStorageManager.generateIconKey(providerSlug, null, darkMode),
        fallbackUrl = lobeHubUrls.monochromeUrl,
        name = name,
        modifier = modifier,
        loading = loading,
        color = color,
        contentColor = contentColor,
        tint = !providerSlug.endsWith("-color"),
        padding = padding,
        fallback = {
            // Priority 3: Text avatar (final fallback)
            TextAvatar(
                text = name,
                modifier = modifier,
                loading = loading,
                color = color,
                contentColor = contentColor
            )
        }
    )
}


/**
 * Helper composable for provider favicon fallback
 */
@Composable
private fun ProviderFaviconFallback(
    name: String,
    baseUrl: String?,
    modifier: Modifier,
    loading: Boolean,
    color: Color,
    contentColor: Color,
    padding: Dp
) {
    val faviconUrl = remember(baseUrl) {
        baseUrl?.urlPartsOrNull()?.host?.let { host ->
            "https://favicone.com/$host"
        }
    }
    
    if (faviconUrl != null) {
        RemoteIcon(
            url = faviconUrl,
            iconKey = "favicon_${baseUrl?.hashCode()}",
            name = name,
            modifier = modifier,
            loading = loading,
            color = color,
            padding = padding,
            fallback = {
                TextAvatar(
                    text = name,
                    modifier = modifier,
                    loading = loading,
                    color = color,
                    contentColor = contentColor
                )
            }
        )
    } else {
        TextAvatar(
            text = name,
            modifier = modifier,
            loading = loading,
            color = color,
            contentColor = contentColor
        )
    }
}

/**
 * Get a provider slug from a provider name for LobeHub CDN lookup
 */
internal fun getProviderSlugFromName(name: String): String? {
    val lowerName = name.lowercase()
    return when {
        lowerName.contains("openai") -> "openai"
        lowerName.contains("anthropic") || lowerName.contains("claude") -> "anthropic"
        lowerName.contains("google") || lowerName.contains("gemini") -> "google"
        lowerName.contains("deepseek") -> "deepseek"
        lowerName.contains("mistral") -> "mistral"
        lowerName.contains("meta") || lowerName.contains("llama") -> "meta"
        lowerName.contains("cohere") -> "cohere"
        lowerName.contains("perplexity") -> "perplexity"
        lowerName.contains("groq") -> "groq"
        lowerName.contains("openrouter") -> "openrouter"
        lowerName.contains("together") -> "together"
        lowerName.contains("fireworks") -> "fireworks"
        lowerName.contains("nvidia") -> "nvidia"
        lowerName.contains("qwen") || lowerName.contains("alibaba") -> "qwen"
        lowerName.contains("zhipu") || lowerName.contains("glm") -> "zhipu"
        lowerName.contains("moonshot") || lowerName.contains("kimi") -> "moonshot"
        lowerName.contains("minimax") -> "minimax"
        lowerName.contains("xai") || lowerName.contains("grok") -> "xai"
        lowerName.contains("bytedance") || lowerName.contains("doubao") -> "bytedance"
        lowerName.contains("siliconflow") || lowerName.contains("silicon") -> "siliconflow"
        lowerName.contains("cerebras") -> "cerebras"
        lowerName.contains("cloudflare") -> "cloudflare"
        lowerName.contains("hunyuan") || lowerName.contains("tencent") -> "hunyuan"
        else -> null
    }
}

/**
 * AI Icon that uses a layered fallback strategy based on provider type:
 * 
 * For OpenRouter providers (openrouter.ai):
 * 1. Direct icon URL (if provided by API)
 * 2. LobeHub CDN via provider slug (colored â†’ monochrome fallback)
 * 3. Local pattern matching (for known patterns)
 * 4. Text avatar (final fallback)
 * 
 * For OpenAI providers (api.openai.com):
 * 1. Direct icon URL (if provided by API)
 * 2. Local pattern matching (for known patterns)
 * 3. OpenAI logo (fallback)
 * 
 * For Google providers:
 * 1. Direct icon URL (if provided by API)
 * 2. Local pattern matching (for known patterns)
 * 3. Google logo (fallback)
 * 
 * For other providers:
 * 1. Direct icon URL (if provided by API)
 * 2. Local pattern matching (for known patterns)
 * 3. Text avatar (final fallback)
 */
@Composable
fun AutoAIIconWithUrl(
    name: String,
    iconUrl: String? = null,
    providerSlug: String? = null,
    providerBaseUrl: String? = null,
    isGoogleProvider: Boolean = false,
    customIconUri: String? = null,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = LocalContentColor.current,
    padding: Dp = 4.dp,
    fallback: @Composable (() -> Unit)? = null,
) {
    // Priority 1: Custom icon URI (user-selected or catalog-assigned)
    if (!customIconUri.isNullOrBlank()) {
        val lobeHubSlug = customIconUri.extractLobeHubIconSlug()
        if (lobeHubSlug != null) {
            val darkMode = LocalDarkMode.current
            val lobeHubUrls = getLobeHubIconUrls(lobeHubSlug, darkMode)
            RemoteIcon(
                url = lobeHubUrls.coloredUrl,
                iconKey = me.rerere.rikkahub.utils.IconStorageManager.generateIconKey(
                    lobeHubSlug,
                    null,
                    darkMode,
                ),
                fallbackUrl = lobeHubUrls.monochromeUrl,
                name = name,
                modifier = modifier,
                loading = loading,
                color = color,
                contentColor = contentColor,
                tint = !lobeHubSlug.endsWith("-color"),
                padding = padding,
                fallback = fallback ?: {
                    TextAvatar(
                        text = name,
                        modifier = modifier,
                        loading = loading,
                        color = color,
                        contentColor = contentColor,
                    )
                },
            )
        } else if (customIconUri.isCatalogIconUrl()) {
            CatalogIcon(
                iconUri = customIconUri,
                name = name,
                modifier = modifier,
                loading = loading,
                color = color,
                contentColor = contentColor,
                padding = padding,
            )
        } else {
            // Non-catalog custom icon (user picked from gallery, etc.)
            Box(modifier = modifier.size(24.dp)) {
                AsyncImage(
                    model = android.net.Uri.parse(customIconUri),
                    contentDescription = name,
                    modifier = Modifier.padding(padding),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        return
    }

    // Priority 2: Catalog icon URL (from model family iconUrl)
    if (!iconUrl.isNullOrBlank() && iconUrl.isCatalogIconUrl()) {
        CatalogIcon(
            iconUri = iconUrl,
            name = name,
            modifier = modifier,
            loading = loading,
            color = color,
            contentColor = contentColor,
            padding = padding,
        )
        return
    }

    // Priority 3: Local known logo.
    val localPath = remember(name) { computeAIIconByName(name) }
    if (localPath != null) {
        AIIcon(
            path = localPath,
            name = name,
            modifier = modifier,
            loading = loading,
            color = color,
            contentColor = contentColor,
            padding = padding,
        )
        return
    }

    // Priority 4: LobeHub provider/family slug when metadata supplied one.
    val lobeHubSlug = remember(providerSlug) {
        providerSlug
            ?.normalizeLobeHubIconSlug()
            ?.takeIf { it.isNotBlank() }
    }
    if (lobeHubSlug != null) {
        val darkMode = LocalDarkMode.current
        val lobeHubUrls = getLobeHubIconUrls(lobeHubSlug, darkMode)
        RemoteIcon(
            url = lobeHubUrls.coloredUrl,
            iconKey = me.rerere.rikkahub.utils.IconStorageManager.generateIconKey(
                lobeHubSlug,
                null,
                darkMode,
            ),
            fallbackUrl = lobeHubUrls.monochromeUrl,
            name = name,
            modifier = modifier,
            loading = loading,
            color = color,
            contentColor = contentColor,
            tint = !lobeHubSlug.endsWith("-color"),
            padding = padding,
            fallback = fallback ?: {
                TextAvatar(
                    text = name,
                    modifier = modifier,
                    loading = loading,
                    color = color,
                    contentColor = contentColor,
                )
            },
        )
        return
    }

    if (fallback != null) {
        fallback()
        return
    }

    TextAvatar(
        text = name,
        modifier = modifier,
        loading = loading,
        color = color,
        contentColor = contentColor,
    )
}

@Composable
fun CatalogIcon(
    iconUri: String,
    name: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = LocalContentColor.current,
    padding: Dp = 4.dp,
) {
    val context = LocalContext.current
    val fileName = remember(iconUri) { extractCatalogIconFileName(iconUri) }
    var isBundled by remember(fileName) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(fileName) {
        if (fileName.isNullOrBlank()) {
            isBundled = false
        } else {
            isBundled = withContext(Dispatchers.IO) {
                try {
                    context.assets.open("icons/$fileName").use { }
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }
    }

    if (isBundled == true && fileName != null) {
        AIIcon(
            path = fileName,
            name = name,
            modifier = modifier,
            loading = loading,
            color = color,
            contentColor = contentColor,
            padding = padding,
        )
    } else if (isBundled == false) {
        val downloadUrl = remember(iconUri) {
            if (iconUri.startsWith("http://") || iconUri.startsWith("https://")) {
                iconUri
            } else {
                val cleanPath = iconUri.trimStart('/')
                "https://raw.githubusercontent.com/Cocolalilal/LastChat/main/catalog/$cleanPath"
            }
        }
        RemoteIcon(
            url = downloadUrl,
            iconKey = "catalog_${downloadUrl.hashCode()}",
            name = name,
            modifier = modifier,
            loading = loading,
            color = color,
            padding = padding,
            contentColor = contentColor,
            tint = true,
        )
    } else {
        // Loading/checking assets state placeholder
        Box(modifier = modifier.size(24.dp))
    }
}

private fun extractCatalogIconFileName(uri: String): String? {
    val lower = uri.lowercase()
    val markers = listOf("/catalog/icons/", "catalog/icons/", "/icons/", "icons/")
    for (marker in markers) {
        val index = lower.indexOf(marker)
        if (index != -1) {
            val start = index + marker.length
            return uri.substring(start).substringAfterLast('/')
        }
    }
    if (!uri.contains("/") && !uri.contains("\\")) {
        return uri
    }
    return uri.substringAfterLast('/')
}
private data class IconUrlPair(
    val coloredUrl: String,
    val monochromeUrl: String
)

/**
 * Get LobeHub CDN icon URLs from provider slug
 * Returns primary theme-appropriate URL and fallback to opposite theme
 * 
 * LobeHub structure:
 * - /dark/{slug}.png - dark icons (for dark backgrounds)
 * - /light/{slug}.png - light icons (for light backgrounds)
 */
private fun getLobeHubIconUrls(providerSlug: String, darkMode: Boolean): IconUrlPair {
    // Normalize the slug: lowercase and replace spaces/underscores with hyphens
    val normalizedSlug = providerSlug.normalizeLobeHubIconSlug()
    
    // Map some common provider slugs to their LobeHub equivalents
    val slug = when (normalizedSlug.replace("-", "")) {
        "metallama" -> "meta"
        "mistralai" -> "mistral"
        "01ai" -> "yi"  // 01-ai is Yi
        "moonshotai" -> "moonshot"
        else -> normalizedSlug
    }
    
    // For dark mode: use dark icons (light colored icons visible on dark bg)
    // For light mode: use light icons (dark colored icons visible on light bg)
    val primaryTheme = if (darkMode) "dark" else "light"
    val fallbackTheme = if (darkMode) "light" else "dark"
    
    // npmmirror CDN with correct path format
    return IconUrlPair(
        coloredUrl = "https://registry.npmmirror.com/@lobehub/icons-static-png/latest/files/$primaryTheme/$slug.png",
        monochromeUrl = "https://registry.npmmirror.com/@lobehub/icons-static-png/latest/files/$fallbackTheme/$slug.png"
    )
}

/**
 * Composable that loads a remote icon with persistent local storage.
 * 
 * Flow:
 * 1. Check if icon exists in local storage (instant load)
 * 2. If not, show loading placeholder while downloading
 * 3. Download and save to local storage
 * 4. Display from local storage
 * 
 * Fallback chain: localIcon -> url -> fallbackUrl -> fallback composable
 */
@Composable
private fun RemoteIcon(
    url: String,
    name: String,
    modifier: Modifier = Modifier,
    iconKey: String? = null,
    fallbackUrl: String? = null,
    loading: Boolean = false,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = LocalContentColor.current,
    tint: Boolean = false,
    padding: Dp = 4.dp,
    fallback: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val platformHttpClient = koinInject<PlatformHttpClient>()
    val iconManager = remember(context, platformHttpClient) {
        me.rerere.rikkahub.utils.IconStorageManager.getInstance(context, platformHttpClient)
    }
    val darkMode = LocalDarkMode.current
    
    // Generate icon key if not provided
    val effectiveKey = iconKey ?: remember(url, darkMode) {
        // Use URL hash as key if no specific key provided
        "url_${url.hashCode()}_${if (darkMode) "dark" else "light"}"
    }
    
    // Check local storage first
    var localUri by remember(effectiveKey) { 
        mutableStateOf(iconManager.getLocalIconUri(effectiveKey)) 
    }
    var isDownloading by remember(effectiveKey) { mutableStateOf(false) }
    var downloadFailed by remember(effectiveKey) { mutableStateOf(false) }
    var fallbackFailed by remember(url, fallbackUrl) { mutableStateOf(false) }
    
    // If we have a local icon, use it directly (instant!)
    if (localUri != null) {
        Box(modifier = modifier.size(24.dp)) {
            AsyncImage(
                model = localUri,
                contentDescription = name,
                modifier = Modifier.padding(padding),
                colorFilter = if (tint) ColorFilter.tint(contentColor) else null,
                contentScale = ContentScale.Fit,
            )
        }
        return
    }
    
    // If download failed and we have a fallback URL, try it
    if (downloadFailed && fallbackUrl != null && !fallbackFailed) {
        // Try fallback URL and also try to save it
        androidx.compose.runtime.LaunchedEffect(fallbackUrl) {
            val savedUri = iconManager.downloadAndSaveIcon(effectiveKey, fallbackUrl)
            if (savedUri != null) {
                localUri = savedUri
            } else {
                fallbackFailed = true
            }
        }
        
        // Show fallback URL while downloading
        Box(modifier = modifier.size(24.dp)) {
            AsyncImage(
                model = fallbackUrl,
                contentDescription = name,
                modifier = Modifier.padding(padding),
                colorFilter = if (tint) ColorFilter.tint(contentColor) else null,
                contentScale = ContentScale.Fit,
                onError = { fallbackFailed = true }
            )
        }
        return
    }
    
    // If both primary and fallback failed, use the fallback composable
    if (downloadFailed && (fallbackUrl == null || fallbackFailed) && fallback != null) {
        fallback()
        return
    }
    
    // Start download if not already downloading
    if (!isDownloading && !downloadFailed) {
        androidx.compose.runtime.LaunchedEffect(url) {
            isDownloading = true
            val savedUri = iconManager.downloadAndSaveIcon(effectiveKey, url)
            if (savedUri != null) {
                localUri = savedUri
            } else {
                downloadFailed = true
            }
            isDownloading = false
        }
    }
    
    // Show loading state while downloading (use remote URL with Coil cache as temporary display)
    Box(modifier = modifier.size(24.dp)) {
        AsyncImage(
            model = url,
            contentDescription = name,
            modifier = Modifier.padding(padding),
            colorFilter = if (tint) ColorFilter.tint(contentColor) else null,
            contentScale = ContentScale.Fit,
            onError = { downloadFailed = true }
        )
    }
}

private fun String.normalizeLobeHubIconSlug(): String {
    return lowercase()
        .trim()
        .removePrefix(LOBEHUB_ICON_URI_PREFIX)
        .replace(" ", "-")
        .replace("_", "-")
}

private fun String.extractLobeHubIconSlug(): String? {
    if (!startsWith(LOBEHUB_ICON_URI_PREFIX)) {
        return null
    }
    return removePrefix(LOBEHUB_ICON_URI_PREFIX)
        .normalizeLobeHubIconSlug()
        .takeIf { it.isNotBlank() }
}

private fun String.isCatalogIconUrl(): Boolean {
    val lower = this.lowercase()
    return lower.contains("/catalog/icons/") ||
        lower.contains("/catalog/refs/heads/") ||
        lower.contains("raw.githubusercontent.com/cocolalilal/lastchat") ||
        lower.contains("jsdelivr.net/gh/cocolalilal/lastchat") ||
        (lower.contains("catalog") && lower.contains("icons")) ||
        lower.startsWith("icons/") ||
        lower.startsWith("/icons/") ||
        lower.contains("file:///android_asset/icons/")
}

private val ICON_CACHE = mutableMapOf<String, String>()

internal fun computeAIIconByName(name: String): String? {
    val cached = ICON_CACHE[name]
    if (cached != null) return cached.ifEmpty { null }

    val lowerName = name.lowercase()
    val path = when {
        lowerName.contains("comfyui") || lowerName.contains("comfy ui") -> "comfyui.svg"
        lowerName.contains("tavern") -> "tavern.png"
        
        else -> null
    }

    ICON_CACHE[name] = path ?: ""
    return path
}

@Composable
fun SiliconFlowPowerByIcon(modifier: Modifier = Modifier) {
    val darkMode = LocalDarkMode.current
    if (!darkMode) {
        AsyncImage(model = R.drawable.siliconflow_light, contentDescription = null, modifier = modifier)
    } else {
        AsyncImage(model = R.drawable.siliconflow_dark, contentDescription = null, modifier = modifier)
    }
}

/**
 * Search the LobeHub icons API for a matching monochrome slug.
 * Returns the matching slug if found, null otherwise.
 */
suspend fun searchLobeHubIcon(httpClient: PlatformHttpClient, providerName: String): String? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val response = httpClient.execute(
                PlatformHttpRequest(
                    method = "GET",
                    url = "https://unpkg.com/@lobehub/icons-static-png@latest/?meta",
                )
            )
            if (response.statusCode !in 200..299) return@runCatching null

            val root = JsonInstant.parseToJsonElement(response.body.decodeToString()) as? JsonObject
                ?: return@runCatching null
            val files = root["files"] as? JsonArray
                ?: return@runCatching null

            val slugs = files
                .mapNotNull { element ->
                    val file = element as? JsonObject ?: return@mapNotNull null
                    file["path"]
                        ?.let { it.jsonPrimitiveOrNull }
                        ?.contentOrNull
                }
                .mapNotNull { path ->
                    if (path.startsWith("/light/") && path.endsWith(".png")) {
                        path.substringAfterLast('/').removeSuffix(".png").takeIf { it.isNotBlank() }
                    } else null
                }
                .distinct()

            val normalizedName = providerName.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (normalizedName.isBlank()) return@runCatching null

            // Filter for monochrome options (those without "-color" suffix and not ending in "-text")
            val monoSlugs = slugs.filter { !it.endsWith("-color") && !it.endsWith("-text") }

            // 1. Exact match of normalized name and slug (with hyphens/underscores removed)
            val exactMatch = monoSlugs.find { slug ->
                slug.replace("-", "").replace("_", "") == normalizedName
            }
            if (exactMatch != null) return@runCatching exactMatch

            // 2. Fuzzy match where provider name contains slug or vice versa
            val fuzzyMatch = monoSlugs.find { slug ->
                val cleanSlug = slug.replace("-", "").replace("_", "")
                normalizedName.contains(cleanSlug) || cleanSlug.contains(normalizedName)
            }
            fuzzyMatch
        }.getOrNull()
    }
}
