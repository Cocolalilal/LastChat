package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.css
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.hooks.rememberAvatarShape
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.toCssHex
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * UNIFIED Provider Icon - Use this everywhere a provider icon is needed.
 * Takes the whole ProviderSetting object to ensure consistent display.
 * 
 * Priority:
 * 1. Custom icon (user-selected)
 * 2. Local pattern matching
 * 3. LobeHub CDN
 * 4. Text avatar fallback
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
    // Get base URL for provider type detection
    val baseUrl = when (provider) {
        is ProviderSetting.OpenAI -> provider.baseUrl
        is ProviderSetting.Google -> provider.baseUrl
        is ProviderSetting.Claude -> provider.baseUrl
    }
    
    // Derive provider slug from name for LobeHub lookup
    val providerSlug = remember(provider.name) {
        getProviderSlugFromName(provider.name) ?: provider.name.lowercase().replace(" ", "-").replace("_", "-")
    }

    val effectiveContentColor = if (provider.enabled) {
        contentColor
    } else {
        contentColor.copy(alpha = 0.38f)
    }
    
    AutoAIIconWithUrl(
        name = provider.name,
        customIconUri = provider.customIconUri,
        providerSlug = providerSlug,
        providerBaseUrl = baseUrl,
        isGoogleProvider = provider is ProviderSetting.Google,
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
 * 1. Custom icon (user-selected)
 * 2. Direct icon URL from API
 * 3. LobeHub CDN via provider slug
 * 4. Local pattern matching
 * 5. Provider-specific fallback or text avatar
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
    val baseUrl = when (provider) {
        is ProviderSetting.OpenAI -> provider.baseUrl
        is ProviderSetting.Google -> provider.baseUrl
        is ProviderSetting.Claude -> provider.baseUrl
        null -> null
    }
    
    AutoAIIconWithUrl(
        name = model.displayName.ifBlank { model.modelId },
        iconUrl = model.iconUrl,
        providerSlug = model.providerSlug,
        providerBaseUrl = baseUrl,
        isGoogleProvider = provider is ProviderSetting.Google,
        customIconUri = model.customIconUri,
        modifier = modifier,
        loading = loading,
        color = color,
        contentColor = contentColor,
        padding = padding
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
                  fill: ${contentColor.toCssHex()};
                }
            """.trimIndent()
            )
            .build()
    }
    Surface(
        modifier = modifier.size(24.dp),
        shape = rememberAvatarShape(loading),
        color = Color.Transparent,
    ) {
        AsyncImage(
            model = model,
            contentDescription = name,
            modifier = Modifier.padding(padding)
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
        baseUrl?.toHttpUrlOrNull()?.host?.let { host ->
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
private fun getProviderSlugFromName(name: String): String? {
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
 * 2. LobeHub CDN via provider slug (colored → monochrome fallback)
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
) {
    val darkMode = LocalDarkMode.current
    
    // Priority 0: User-selected custom icon (highest priority)
    if (!customIconUri.isNullOrBlank()) {
        // Handle LobeHub icons theme switching if relevant
        val effectiveUri = if (customIconUri.contains("registry.npmmirror.com/@lobehub/icons-static-png")) {
            if (darkMode) {
                customIconUri.replace("/light/", "/dark/")
            } else {
                customIconUri.replace("/dark/", "/light/")
            }
        } else {
            customIconUri
        }

        Surface(
            modifier = modifier,
            shape = rememberAvatarShape(loading),
            color = Color.Transparent,
        ) {
            AsyncImage(
                model = android.net.Uri.parse(effectiveUri),
                contentDescription = name,
                modifier = Modifier.padding(padding),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
        return
    }
    
    // Determine provider type based on base URL
    val isOpenRouterProvider = providerBaseUrl?.contains("openrouter.ai") == true
    val isOpenAIProvider = providerBaseUrl?.contains("api.openai.com") == true
    
    // Priority 1: Direct icon URL from API
    if (!iconUrl.isNullOrBlank()) {
        RemoteIcon(
            url = iconUrl,
            iconKey = me.rerere.rikkahub.utils.IconStorageManager.generateIconKey(providerSlug, name, darkMode),
            name = name,
            modifier = modifier,
            loading = loading,
            color = color,
            padding = padding
        )
        return
    }
    
    // Priority 2: LobeHub CDN via provider slug (for any provider with a slug)
    // Skip CDN for models that already have good local icons
    if (!providerSlug.isNullOrBlank() && !hasGoodLocalIcon(name)) {
        val lobeHubUrls = getLobeHubIconUrls(providerSlug, darkMode)
        RemoteIcon(
            url = lobeHubUrls.coloredUrl,
            iconKey = me.rerere.rikkahub.utils.IconStorageManager.generateIconKey(providerSlug, name, darkMode),
            fallbackUrl = lobeHubUrls.monochromeUrl,
            name = name,
            modifier = modifier,
            loading = loading,
            color = color,
            padding = padding,
            fallback = {
                // If LobeHub icons fail, try local pattern matching
                AutoAIIcon(
                    name = name,
                    modifier = modifier,
                    loading = loading,
                    color = color,
                    contentColor = contentColor,
                    padding = padding
                )
            }
        )
        return
    }
    
    // Priority 3: Local pattern matching
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
    
    // Priority 4: Provider-specific fallbacks
    when {
        isOpenAIProvider -> {
            // OpenAI provider: fallback to OpenAI logo
            AIIcon(
                path = "openai.svg",
                name = name,
                modifier = modifier,
                loading = loading,
                color = color,
                contentColor = contentColor,
                padding = padding,
            )
        }
        isGoogleProvider -> {
            // Google provider: fallback to Google logo
            AIIcon(
                path = "google-color.svg",
                name = name,
                modifier = modifier,
                loading = loading,
                color = color,
                contentColor = contentColor, // This might tint the colored icon if it's not protected, but user said it's fine
                padding = padding,
            )
        }
        else -> {
            // For models: fallback directly to text avatar (no favicon fetching)
            TextAvatar(
                text = name,
                modifier = modifier,
                loading = loading,
                color = color,
                contentColor = contentColor
            )
        }
    }
}

/**
 * Check if a model name has a good local icon available
 * Skip CDN lookup for these models to use local icons which are already excellent
 */
private fun hasGoodLocalIcon(name: String): Boolean {
    val lowerName = name.lowercase()
    // Models/providers with excellent local icons - skip CDN for these
    return lowerName.contains("gemini") ||
           lowerName.contains("claude") ||
           lowerName.contains("gpt") ||
           lowerName.contains("deepseek") ||
           lowerName.contains("qwen") ||
           lowerName.contains("mistral") ||
           lowerName.contains("llama") ||
           lowerName.contains("gemma") ||
           lowerName.contains("grok") ||
           lowerName.contains("openai") ||
           lowerName.contains("google")
}

/**
 * Icon URL pair containing both colored and monochrome versions
 */
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
    val normalizedSlug = providerSlug.lowercase()
        .replace(" ", "-")
        .replace("_", "-")
    
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
    padding: Dp = 4.dp,
    fallback: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    val okHttpClient = remember { org.koin.java.KoinJavaComponent.get<okhttp3.OkHttpClient>(okhttp3.OkHttpClient::class.java) }
    val iconManager = remember(context) { 
        me.rerere.rikkahub.utils.IconStorageManager.getInstance(context, okHttpClient)
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
        Surface(
            modifier = modifier.size(24.dp),
            shape = rememberAvatarShape(loading),
            color = Color.Transparent,
        ) {
            AsyncImage(
                model = localUri,
                contentDescription = name,
                modifier = Modifier.padding(padding)
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
        Surface(
            modifier = modifier.size(24.dp),
            shape = rememberAvatarShape(loading),
            color = Color.Transparent,
        ) {
            AsyncImage(
                model = fallbackUrl,
                contentDescription = name,
                modifier = Modifier.padding(padding),
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
    Surface(
        modifier = modifier.size(24.dp),
        shape = rememberAvatarShape(loading),
        color = Color.Transparent,
    ) {
        AsyncImage(
            model = url,
            contentDescription = name,
            modifier = Modifier.padding(padding),
            onError = { downloadFailed = true }
        )
    }
}

@Preview
@Composable
private fun PreviewAutoAIIcon() {
    Column {
        AutoAIIcon("test")
    }
}

// https://lobehub.com/zh/icons
private fun computeAIIconByName(name: String): String? {
    // 检查缓存
    ICON_CACHE[name]?.let { return it }

    val lowerName = name.lowercase()
    
    // OpenRouter model ID parsing: extract provider and model from "provider/model" format
    val hasSlash = lowerName.contains("/")
    val providerPart = if (hasSlash) lowerName.substringBefore("/") else null
    val modelPart = if (hasSlash) lowerName.substringAfter("/") else null
    
    // First, try to match against model name (this is more specific and should take priority)
    // For OpenRouter: "anthropic/claude-3" should show Claude icon, not Anthropic
    val modelIcon = modelPart?.let { matchModelPattern(it) }
    if (modelIcon != null) {
        ICON_CACHE[name] = modelIcon
        return modelIcon
    }
    
    // Then, try to match against provider
    val providerIcon = providerPart?.let { matchProviderPattern(it) }
    if (providerIcon != null) {
        ICON_CACHE[name] = providerIcon
        return providerIcon
    }
    
    // Finally, try to match the full name
    val fullNameIcon = matchModelPattern(lowerName) ?: matchProviderPattern(lowerName)
    fullNameIcon?.let { ICON_CACHE[name] = it }
    
    return fullNameIcon
}

// Match against provider/company patterns - used for OpenRouter provider prefixes
private fun matchProviderPattern(providerName: String): String? {
    return when {
        // Companies with their own icons
        providerName == "openai" -> "openai.svg"
        providerName == "google" -> "google-color.svg"
        providerName == "anthropic" -> "claude-color.svg" // Anthropic = Claude
        providerName == "meta-llama" || providerName == "meta" -> "meta-color.svg"
        providerName == "mistralai" || providerName == "mistral" -> "mistral-color.svg"
        providerName == "deepseek" -> "deepseek-color.svg"
        providerName == "x-ai" || providerName == "xai" -> "xai.svg"
        providerName == "cohere" -> "cohere-color.svg"
        providerName == "perplexity" -> "perplexity-color.svg"
        providerName == "nvidia" -> "nvidia-color.svg"
        providerName == "qwen" || providerName == "alibaba" -> "qwen-color.svg"
        providerName == "microsoft" -> "openai.svg" // Azure OpenAI
        providerName == "groq" -> "groq.svg"
        providerName == "together" -> "openrouter.svg"
        providerName == "fireworks" -> "openrouter.svg"
        providerName == "openrouter" -> "openrouter.svg"
        
        // Additional OpenRouter providers - use generic or related icons
        providerName == "nousresearch" -> "openrouter.svg"
        providerName == "cognitivecomputations" -> "openrouter.svg"
        providerName == "databricks" -> "openrouter.svg"
        providerName == "liquid" -> "openrouter.svg"
        providerName == "inflection" -> "openrouter.svg"
        providerName == "neversleep" -> "openrouter.svg"
        providerName == "sao10k" -> "openrouter.svg"
        providerName == "ai21" -> "openrouter.svg"
        providerName == "amazon" -> "openrouter.svg"
        providerName == "aetherwiing" -> "openrouter.svg"
        providerName == "thedrummer" -> "openrouter.svg"
        providerName == "undi95" -> "openrouter.svg"
        providerName == "01-ai" -> "openrouter.svg" // Yi models
        providerName == "upstage" -> "openrouter.svg" // Solar
        providerName == "mancer" -> "openrouter.svg"
        providerName == "lynn" -> "openrouter.svg"
        providerName == "pygmalionai" -> "openrouter.svg"
        
        // Fallback patterns using contains for partial matches
        providerName.contains("llama") -> "meta-color.svg"
        providerName.contains("qwen") -> "qwen-color.svg"
        
        else -> null
    }
}

// Match against model name patterns (for model-specific icons)
private fun matchModelPattern(modelName: String): String? {
    return when {
        // Specific model patterns - order matters (more specific first)
        PATTERN_CLAUDE_MODEL.containsMatchIn(modelName) -> "claude-color.svg"
        PATTERN_GPT_MODEL.containsMatchIn(modelName) -> "openai.svg"
        PATTERN_GEMINI_MODEL.containsMatchIn(modelName) -> "gemini-color.svg"
        PATTERN_GEMMA.containsMatchIn(modelName) -> "gemma-color.svg"
        PATTERN_DEEPSEEK_MODEL.containsMatchIn(modelName) -> "deepseek-color.svg"
        PATTERN_GROK_MODEL.containsMatchIn(modelName) -> "grok.svg"
        PATTERN_OLLAMA.containsMatchIn(modelName) -> "ollama.svg" // Must be before PATTERN_LLAMA
        PATTERN_LLAMA.containsMatchIn(modelName) -> "meta-color.svg"
        PATTERN_QWEN_MODEL.containsMatchIn(modelName) -> "qwen-color.svg"
        PATTERN_MISTRAL_MODEL.containsMatchIn(modelName) -> "mistral-color.svg"
        PATTERN_MIXTRAL.containsMatchIn(modelName) -> "mistral-color.svg"
        PATTERN_CODESTRAL.containsMatchIn(modelName) -> "mistral-color.svg"
        PATTERN_PIXTRAL.containsMatchIn(modelName) -> "mistral-color.svg"
        PATTERN_PHI.containsMatchIn(modelName) -> "openai.svg" // Microsoft Phi
        PATTERN_COMMAND.containsMatchIn(modelName) -> "cohere-color.svg"
        PATTERN_INTERNLM_MODEL.containsMatchIn(modelName) -> "internlm-color.svg"
        PATTERN_DOLPHIN.containsMatchIn(modelName) -> "openrouter.svg"
        PATTERN_HERMES.containsMatchIn(modelName) -> "openrouter.svg"
        PATTERN_SOLAR.containsMatchIn(modelName) -> "openrouter.svg"
        PATTERN_YI.containsMatchIn(modelName) -> "openrouter.svg"
        PATTERN_WIZARDLM.containsMatchIn(modelName) -> "openrouter.svg"
        PATTERN_NOVA.containsMatchIn(modelName) -> "openrouter.svg" // Amazon Nova
        PATTERN_TOPPY.containsMatchIn(modelName) -> "openrouter.svg"
        PATTERN_MYTHOMAX.containsMatchIn(modelName) -> "openrouter.svg"
        PATTERN_SONAR.containsMatchIn(modelName) -> "perplexity-color.svg"
        PATTERN_JAMBA.containsMatchIn(modelName) -> "openrouter.svg"
        // Search service patterns
        PATTERN_SEARCH_BING.containsMatchIn(modelName) -> "bing.png"
        PATTERN_SEARCH_TAVILY.containsMatchIn(modelName) -> "tavily.png"
        PATTERN_SEARCH_EXA.containsMatchIn(modelName) -> "exa.png"
        PATTERN_SEARCH_BRAVE.containsMatchIn(modelName) -> "brave.svg"
        PATTERN_SEARCH_LINKUP.containsMatchIn(modelName) -> "linkup.png"
        PATTERN_SEARCH_METASO.containsMatchIn(modelName) -> "metaso.svg"
        PATTERN_SEARCH_FIRECRAWL.containsMatchIn(modelName) -> "firecrawl.svg"
        PATTERN_SEARCH_JINA.containsMatchIn(modelName) -> "jina.svg"
        PATTERN_PERPLEXITY.containsMatchIn(modelName) -> "perplexity-color.svg"
        PATTERN_ZHIPU.containsMatchIn(modelName) -> "zhipu-color.svg"
        else -> null
    }
}

// Also provide legacy matching for non-OpenRouter usage (backwards compat)
private fun matchIconPattern(searchName: String): String? {
    return when {
        PATTERN_OPENAI.containsMatchIn(searchName) -> "openai.svg"
        PATTERN_GEMINI.containsMatchIn(searchName) -> "gemini-color.svg"
        PATTERN_GOOGLE.containsMatchIn(searchName) -> "google-color.svg"
        PATTERN_CLAUDE.containsMatchIn(searchName) -> "claude-color.svg"
        PATTERN_DEEPSEEK.containsMatchIn(searchName) -> "deepseek-color.svg"
        PATTERN_GROK.containsMatchIn(searchName) -> "grok.svg"
        PATTERN_QWEN.containsMatchIn(searchName) -> "qwen-color.svg"
        PATTERN_DOUBAO.containsMatchIn(searchName) -> "doubao-color.svg"
        PATTERN_OPENROUTER.containsMatchIn(searchName) -> "openrouter.svg"
        PATTERN_ZHIPU.containsMatchIn(searchName) -> "zhipu-color.svg"
        PATTERN_MISTRAL.containsMatchIn(searchName) -> "mistral-color.svg"
        PATTERN_META.containsMatchIn(searchName) -> "meta-color.svg"
        PATTERN_HUNYUAN.containsMatchIn(searchName) -> "hunyuan-color.svg"
        PATTERN_GEMMA.containsMatchIn(searchName) -> "gemma-color.svg"
        PATTERN_PERPLEXITY.containsMatchIn(searchName) -> "perplexity-color.svg"
        PATTERN_BYTEDANCE.containsMatchIn(searchName) -> "bytedance-color.svg"
        PATTERN_ALIYUN.containsMatchIn(searchName) -> "alibabacloud-color.svg"
        PATTERN_SILLICON_CLOUD.containsMatchIn(searchName) -> "siliconflow.svg"
        PATTERN_AIHUBMIX.containsMatchIn(searchName) -> "aihubmix-color.svg"
        PATTERN_OLLAMA.containsMatchIn(searchName) -> "ollama.svg"
        PATTERN_GITHUB.containsMatchIn(searchName) -> "github.svg"
        PATTERN_CLOUDFLARE.containsMatchIn(searchName) -> "cloudflare-color.svg"
        PATTERN_MINIMAX.containsMatchIn(searchName) -> "minimax-color.svg"
        PATTERN_XAI.containsMatchIn(searchName) -> "xai.svg"
        PATTERN_JUHENEXT.containsMatchIn(searchName) -> "juhenext.png"
        PATTERN_KIMI.containsMatchIn(searchName) -> "kimi-color.svg"
        PATTERN_MOONSHOT.containsMatchIn(searchName) -> "moonshot.svg"
        PATTERN_302.containsMatchIn(searchName) -> "302ai.svg"
        PATTERN_STEP.containsMatchIn(searchName) -> "stepfun-color.svg"
        PATTERN_INTERN.containsMatchIn(searchName) -> "internlm-color.svg"
        PATTERN_COHERE.containsMatchIn(searchName) -> "cohere-color.svg"
        PATTERN_TAVERN.containsMatchIn(searchName) -> "tavern.png"
        PATTERN_CEREBRAS.containsMatchIn(searchName) -> "cerebras-color.svg"
        PATTERN_NVIDIA.containsMatchIn(searchName) -> "nvidia-color.svg"
        PATTERN_PPIO.containsMatchIn(searchName) -> "ppio-color.svg"
        PATTERN_VERCEL.containsMatchIn(searchName) -> "vercel.svg"
        PATTERN_GROQ.containsMatchIn(searchName) -> "groq.svg"
        PATTERN_TOKENPONY.containsMatchIn(searchName) -> "tokenpony.svg"
        PATTERN_LING.containsMatchIn(searchName) -> "ling.png"
        // Search providers
        PATTERN_SEARCH_LINKUP.containsMatchIn(searchName) -> "linkup.png"
        PATTERN_SEARCH_BING.containsMatchIn(searchName) -> "bing.png"
        PATTERN_SEARCH_TAVILY.containsMatchIn(searchName) -> "tavily.png"
        PATTERN_SEARCH_EXA.containsMatchIn(searchName) -> "exa.png"
        PATTERN_SEARCH_BRAVE.containsMatchIn(searchName) -> "brave.svg"
        PATTERN_SEARCH_METASO.containsMatchIn(searchName) -> "metaso.svg"
        PATTERN_SEARCH_FIRECRAWL.containsMatchIn(searchName) -> "firecrawl.svg"
        PATTERN_SEARCH_JINA.containsMatchIn(searchName) -> "jina.svg"
        else -> null
    }
}

// 静态缓存和正则模式
private val ICON_CACHE = mutableMapOf<String, String>()
private val PATTERN_OPENAI = Regex("(gpt|openai|o\\d)")
private val PATTERN_GEMINI = Regex("(gemini)")
private val PATTERN_GOOGLE = Regex("google")
private val PATTERN_CLAUDE = Regex("claude")
private val PATTERN_DEEPSEEK = Regex("deepseek")
private val PATTERN_GROK = Regex("grok")
private val PATTERN_QWEN = Regex("qwen|qwq|qvq")
private val PATTERN_DOUBAO = Regex("doubao")
private val PATTERN_OPENROUTER = Regex("openrouter")
private val PATTERN_ZHIPU = Regex("zhipu|智谱|glm")
private val PATTERN_MISTRAL = Regex("mistral")
private val PATTERN_META = Regex("meta\\b|(?<!o)llama")
private val PATTERN_HUNYUAN = Regex("hunyuan|tencent")
private val PATTERN_GEMMA = Regex("gemma")
private val PATTERN_PERPLEXITY = Regex("perplexity")
private val PATTERN_BYTEDANCE = Regex("bytedance|火山")
private val PATTERN_ALIYUN = Regex("aliyun|阿里云|百炼")
private val PATTERN_SILLICON_CLOUD = Regex("silicon|硅基")
private val PATTERN_AIHUBMIX = Regex("aihubmix")
private val PATTERN_OLLAMA = Regex("ollama")
private val PATTERN_GITHUB = Regex("github")
private val PATTERN_CLOUDFLARE = Regex("cloudflare")
private val PATTERN_MINIMAX = Regex("minimax")
private val PATTERN_XAI = Regex("xai")
private val PATTERN_JUHENEXT = Regex("juhenext")
private val PATTERN_KIMI = Regex("kimi")
private val PATTERN_MOONSHOT = Regex("moonshot|月之暗面")
private val PATTERN_302 = Regex("302")
private val PATTERN_STEP = Regex("step|阶跃")
private val PATTERN_INTERN = Regex("intern|书生")
private val PATTERN_COHERE = Regex("cohere")
private val PATTERN_TAVERN = Regex("tavern")
private val PATTERN_CEREBRAS = Regex("cerebras")
private val PATTERN_NVIDIA = Regex("nvidia")
private val PATTERN_PPIO = Regex("ppio|派欧")
private val PATTERN_VERCEL = Regex("vercel")
private val PATTERN_GROQ = Regex("groq")
private val PATTERN_TOKENPONY = Regex("tokenpony|小马算力")
private val PATTERN_LING = Regex("ling|ring|百灵")

private val PATTERN_SEARCH_LINKUP = Regex("linkup")
private val PATTERN_SEARCH_BING = Regex("bing")
private val PATTERN_SEARCH_TAVILY = Regex("tavily")
private val PATTERN_SEARCH_EXA = Regex("exa")
private val PATTERN_SEARCH_BRAVE = Regex("brave")
private val PATTERN_SEARCH_METASO = Regex("metaso|秘塔")
private val PATTERN_SEARCH_FIRECRAWL = Regex("firecrawl")
private val PATTERN_SEARCH_JINA = Regex("jina")

// Model family patterns (for matching model names in OpenRouter format)
private val PATTERN_LLAMA = Regex("llama")
private val PATTERN_QWEN_MODEL = Regex("qwen|qwq|qvq")
private val PATTERN_MISTRAL_MODEL = Regex("mistral")
private val PATTERN_PHI = Regex("\\bphi\\b|phi-")
private val PATTERN_COMMAND = Regex("command-")
private val PATTERN_CLAUDE_MODEL = Regex("claude")
private val PATTERN_GPT_MODEL = Regex("gpt-|\\bo\\d")
private val PATTERN_DEEPSEEK_MODEL = Regex("deepseek")
private val PATTERN_GEMINI_MODEL = Regex("gemini")
private val PATTERN_GROK_MODEL = Regex("grok")
private val PATTERN_MIXTRAL = Regex("mixtral")
private val PATTERN_CODESTRAL = Regex("codestral")
private val PATTERN_PIXTRAL = Regex("pixtral")
private val PATTERN_WIZARDLM = Regex("wizardlm|wizard")
private val PATTERN_DOLPHIN = Regex("dolphin")
private val PATTERN_HERMES = Regex("hermes")
private val PATTERN_SOLAR = Regex("solar")
private val PATTERN_YI = Regex("\\byi\\b|yi-")
private val PATTERN_INTERNLM_MODEL = Regex("internlm")
private val PATTERN_NOVA = Regex("\\bnova\\b")
private val PATTERN_TOPPY = Regex("toppy")
private val PATTERN_MYTHOMAX = Regex("mythomax")
private val PATTERN_SONAR = Regex("sonar")
private val PATTERN_JAMBA = Regex("jamba")

@Composable
fun SiliconFlowPowerByIcon(modifier: Modifier = Modifier) {
    val darkMode = LocalDarkMode.current
    if (!darkMode) {
        AsyncImage(model = R.drawable.siliconflow_light, contentDescription = null, modifier = modifier)
    } else {
        AsyncImage(model = R.drawable.siliconflow_dark, contentDescription = null, modifier = modifier)
    }
}

