package me.rerere.rikkahub.data.ai.models

import me.rerere.ai.provider.OpenAICompatibilityMode
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import kotlin.uuid.Uuid

fun mergeCatalogIntoSettings(
    settings: Settings,
    snapshot: ModelCatalogSnapshot,
    resolver: ModelMetadataResolver,
): Settings {
    val catalogProvidersById = snapshot.providers
        .mapNotNull { provider -> provider.uuidOrNull()?.let { it to provider } }
        .toMap()

    val normalizedExisting = settings.providers.map { provider ->
        val catalogProvider = catalogProvidersById[provider.id]
        val withCatalogDefaults = if (catalogProvider != null) {
            provider
                .withCatalogProviderDefaults(catalogProvider)
        } else {
            provider
        }
        resolver.applyToProvider(withCatalogDefaults)
    }

    val existingProviderIds = normalizedExisting.map { it.id }.toSet()
    val missingCatalogProviders = snapshot.providers
        .filter { it.builtIn || it.preset }
        .mapNotNull { catalogProvider ->
            val id = catalogProvider.uuidOrNull() ?: return@mapNotNull null
            if (id in existingProviderIds) return@mapNotNull null
            catalogProvider.toProviderSetting()
        }

    return settings.copy(
        providers = normalizedExisting + missingCatalogProviders.map(resolver::applyToProvider),
    )
}

private fun ProviderSetting.withCatalogProviderDefaults(
    catalogProvider: CatalogProvider,
): ProviderSetting {
    val catalogIcon = catalogProvider.icon?.toCatalogIconUrl()
    return when (this) {
        is ProviderSetting.OpenAI -> copy(
            customIconUri = customIconUri ?: catalogIcon,
            reasoningBehavior = reasoningBehavior
                ?: catalogProvider.reasoningBehavior?.toReasoningRequestBehavior(),
            streamOptionsMode = streamOptionsMode.catalogDefault(catalogProvider.streamOptionsMode),
            imageResponseModalitiesMode = imageResponseModalitiesMode.catalogDefault(catalogProvider.imageResponseModalitiesMode),
            reasoningContentReplayMode = reasoningContentReplayMode.catalogDefault(catalogProvider.reasoningContentReplayMode),
        )

        is ProviderSetting.Google -> copy(customIconUri = customIconUri ?: catalogIcon)

        is ProviderSetting.Claude -> copy(customIconUri = customIconUri ?: catalogIcon)
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

private fun CatalogProvider.uuidOrNull(): Uuid? {
    return runCatching { Uuid.parse(id) }.getOrNull()
}
