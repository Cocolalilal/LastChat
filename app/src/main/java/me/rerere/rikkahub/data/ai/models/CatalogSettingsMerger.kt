package me.rerere.rikkahub.data.ai.models

import me.rerere.rikkahub.data.datastore.Settings

fun mergeCatalogIntoSettings(
    settings: Settings,
    snapshot: ModelCatalogSnapshot,
    resolver: ModelMetadataResolver,
    includeMissingCatalogProviders: Boolean = false,
): Settings {
    return settings.copy(
        providers = mergeCatalogIntoProviders(
            providers = settings.providers,
            snapshot = snapshot,
            resolver = resolver,
            includeMissingCatalogProviders = includeMissingCatalogProviders,
        ),
    )
}
