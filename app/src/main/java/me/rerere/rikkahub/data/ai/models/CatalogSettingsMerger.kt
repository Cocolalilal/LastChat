package me.rerere.rikkahub.data.ai.models

import me.rerere.ai.provider.OpenAICompatibilityMode
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.common.http.urlPartsOrNull
import kotlin.uuid.Uuid

fun mergeCatalogIntoSettings(
    settings: Settings,
    snapshot: ModelCatalogSnapshot,
    resolver: ModelMetadataResolver,
    includeMissingCatalogProviders: Boolean = false,
): Settings {
    val catalogProvidersById = snapshot.providers
        .mapNotNull { provider -> provider.uuidOrNull()?.let { it to provider } }
        .toMap()
    val catalogProvidersByBaseUrl = snapshot.providers
        .groupBy { provider -> provider.matchType to provider.baseUrl.normalizedCatalogUrlKey() }
    val catalogProvidersByName = snapshot.providers
        .groupBy { provider -> provider.matchType to provider.name.normalizedCatalogNameKey() }
    val matchedCatalogProviderIds = mutableSetOf<String>()

    val normalizedExisting = settings.providers.map { provider ->
        val catalogProvider = catalogProvidersById[provider.id]
            ?: catalogProvidersByBaseUrl[provider.matchType to provider.baseUrlForCatalogMatch().normalizedCatalogUrlKey()]
                ?.singleOrNull()
            ?: catalogProvidersByName[provider.matchType to provider.name.normalizedCatalogNameKey()]
                ?.singleOrNull()
        val withCatalogDefaults = if (catalogProvider != null) {
            matchedCatalogProviderIds += catalogProvider.id
            provider
                .withCatalogProviderDefaults(catalogProvider)
        } else {
            provider
        }
        resolver.applyToProvider(withCatalogDefaults)
    }

    val existingProviderIds = normalizedExisting.map { it.id }.toSet()
    val missingCatalogProviders = if (includeMissingCatalogProviders) {
        snapshot.providers
            .filter { it.builtIn || it.preset }
            .mapNotNull { catalogProvider ->
                val id = catalogProvider.uuidOrNull() ?: return@mapNotNull null
                if (id in existingProviderIds) return@mapNotNull null
                if (catalogProvider.id in matchedCatalogProviderIds) return@mapNotNull null
                catalogProvider.toProviderSetting()
            }
    } else {
        emptyList()
    }

    return settings.copy(
        providers = normalizedExisting + missingCatalogProviders.map(resolver::applyToProvider),
    )
}

private fun ProviderSetting.withCatalogProviderDefaults(
    catalogProvider: CatalogProvider,
): ProviderSetting {
    val catalogIcon = catalogProvider.icon?.toCatalogIconUrl()
    val resolvedIcon = customIconUri.catalogIconDefault(catalogIcon)
    return when (this) {
        is ProviderSetting.OpenAI -> copy(
            customIconUri = resolvedIcon,
            reasoningBehavior = reasoningBehavior
                ?: catalogProvider.reasoningBehavior?.toReasoningRequestBehavior(),
            streamOptionsMode = streamOptionsMode.catalogDefault(catalogProvider.streamOptionsMode),
            imageResponseModalitiesMode = imageResponseModalitiesMode.catalogDefault(catalogProvider.imageResponseModalitiesMode),
            reasoningContentReplayMode = reasoningContentReplayMode.catalogDefault(catalogProvider.reasoningContentReplayMode),
            promptCacheMode = promptCacheMode.catalogDefault(catalogProvider.promptCacheMode),
        )

        is ProviderSetting.Google -> copy(customIconUri = resolvedIcon)

        is ProviderSetting.Claude -> copy(customIconUri = resolvedIcon)

        is ProviderSetting.ComfyUI -> copy(customIconUri = resolvedIcon)
    }
}

private fun CatalogProvider.toProviderSetting(): ProviderSetting? {
    val parsedId = uuidOrNull() ?: return null
    val iconUri = icon?.toCatalogIconUrl()
    return when (type) {
        CatalogProviderType.OPENAI -> ProviderSetting.OpenAI(
            id = parsedId,
            name = name,
            balanceOption = balanceOption,
            customIconUri = iconUri,
            builtIn = builtIn,
            baseUrl = baseUrl,
            chatCompletionsPath = chatCompletionsPath,
            useResponseApi = useResponseApi,
            reasoningBehavior = reasoningBehavior?.toReasoningRequestBehavior(),
            streamOptionsMode = streamOptionsMode,
            imageResponseModalitiesMode = imageResponseModalitiesMode,
            reasoningContentReplayMode = reasoningContentReplayMode,
            promptCacheMode = promptCacheMode,
        )

        CatalogProviderType.GOOGLE -> ProviderSetting.Google(
            id = parsedId,
            name = name,
            balanceOption = balanceOption,
            customIconUri = iconUri,
            builtIn = builtIn,
            baseUrl = baseUrl,
        )

        CatalogProviderType.CLAUDE -> ProviderSetting.Claude(
            id = parsedId,
            name = name,
            balanceOption = balanceOption,
            customIconUri = iconUri,
            builtIn = builtIn,
            baseUrl = baseUrl,
        )
    }
}

private fun OpenAICompatibilityMode.catalogDefault(catalogValue: OpenAICompatibilityMode): OpenAICompatibilityMode {
    return if (this == OpenAICompatibilityMode.AUTO) catalogValue else this
}

private val CatalogProvider.matchType: CatalogProviderType
    get() = type

private val ProviderSetting.matchType: CatalogProviderType
    get() = when (this) {
        is ProviderSetting.OpenAI -> CatalogProviderType.OPENAI
        is ProviderSetting.Google -> CatalogProviderType.GOOGLE
        is ProviderSetting.Claude -> CatalogProviderType.CLAUDE
        is ProviderSetting.ComfyUI -> CatalogProviderType.OPENAI
    }

private fun ProviderSetting.baseUrlForCatalogMatch(): String {
    return when (this) {
        is ProviderSetting.OpenAI -> baseUrl
        is ProviderSetting.Google -> baseUrl
        is ProviderSetting.Claude -> baseUrl
        is ProviderSetting.ComfyUI -> baseUrl
    }
}

private fun String.normalizedCatalogUrlKey(): String {
    val url = trim().trimEnd('/').urlPartsOrNull()
    return if (url != null) {
        val path = url.encodedPath.trimEnd('/').takeUnless { it == "/" }.orEmpty()
        "${url.scheme}://${url.host}$path"
    } else {
        trim().lowercase().trimEnd('/')
    }
}

private fun String.normalizedCatalogNameKey(): String {
    return trim().lowercase().replace(Regex("\\s+"), " ")
}

private fun String?.catalogIconDefault(catalogIcon: String?): String? {
    if (catalogIcon == null) return this
    return when {
        isNullOrBlank() -> catalogIcon
        isCatalogManagedIconUri() -> catalogIcon
        else -> this
    }
}

private fun String.isCatalogManagedIconUri(): Boolean {
    val lower = lowercase()
    return lower.contains("/catalog/icons/") ||
        lower.contains("/catalog/refs/heads/") ||
        lower.contains("raw.githubusercontent.com/cocolalilal/lastchat") ||
        lower.contains("jsdelivr.net/gh/cocolalilal/lastchat") ||
        (lower.contains("catalog") && lower.contains("icons")) ||
        lower.startsWith("icons/") ||
        lower.startsWith("/icons/") ||
        lower.contains("file:///android_asset/icons/") ||
        lower.contains("file:///android_asset/catalog/icons/")
}

private fun CatalogProvider.uuidOrNull(): Uuid? {
    return runCatching { Uuid.parse(id) }.getOrNull()
}
