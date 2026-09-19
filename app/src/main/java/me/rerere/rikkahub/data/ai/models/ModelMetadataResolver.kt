package me.rerere.rikkahub.data.ai.models

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelDisplayNameGenerator
import me.rerere.ai.registry.ModelIdNormalizer

data class ModelResolutionOptions(
    val preserveDisplayName: Boolean = false,
    // These options make persisted user choices authoritative. In particular, empty lists,
    // CHAT, and null are valid explicit choices rather than signals to reapply catalog defaults.
    val preserveExistingCapabilities: Boolean = false,
    val preserveExistingType: Boolean = false,
    val preserveExistingConfiguration: Boolean = false,
)

class ModelMetadataResolver(
    private val snapshotProvider: () -> ModelCatalogSnapshot?,
) {
    fun applyToModel(
        model: Model,
        providerHint: ProviderSetting? = null,
        options: ModelResolutionOptions = ModelResolutionOptions(),
    ): Model {
        if (model.modelId.isBlank()) return model

        val catalogEntry = resolveCatalogEntry(model = model, providerHint = providerHint)
        val canonicalModelId = model.canonicalModelId
            ?.takeIf { it.isNotBlank() }
            ?.let { ModelIdNormalizer.canonicalize(model.modelId, it) }
            ?: catalogEntry?.canonicalModelId
            ?: ModelIdNormalizer.canonicalize(model.modelId)

        val displayName = if (options.preserveDisplayName) {
            model.displayName
        } else {
            ModelDisplayNameGenerator.generate(model.modelId, canonicalModelId)
        }

        val resolvedType = resolveType(model, catalogEntry, options)
        val inputModalities = resolveInputModalities(model, catalogEntry, resolvedType, options)
        val outputModalities = resolveOutputModalities(model, catalogEntry, resolvedType, options)
        val abilities = resolveAbilities(model, catalogEntry, options)

        return model.copy(
            displayName = displayName,
            canonicalModelId = canonicalModelId,
            type = resolvedType,
            inputModalities = inputModalities,
            outputModalities = outputModalities,
            abilities = abilities,
            imageGenerationMethod = if (options.preserveExistingConfiguration) {
                model.imageGenerationMethod
            } else {
                catalogEntry?.imageGenerationMethod ?: model.imageGenerationMethod
            },
            iconUrl = catalogEntry?.iconUrl,
            customIconUri = model.customIconUri.preserveUserModelIcon(),
            reasoningBehavior = if (options.preserveExistingConfiguration) {
                model.reasoningBehavior
            } else {
                catalogEntry?.reasoningBehavior ?: model.reasoningBehavior
            },
            reasoningConfig = if (options.preserveExistingConfiguration) {
                model.reasoningConfig
            } else {
                catalogEntry?.reasoningConfig ?: model.reasoningConfig
            },
            providerSlug = catalogEntry?.providerSlug?.toIconProviderSlug(),
            contextWindowTokens = model.contextWindowTokens ?: catalogEntry?.contextWindowTokens,
            maxImagesInContext = model.maxImagesInContext ?: catalogEntry?.maxImagesInContext,
        )
    }

    fun applyToProvider(
        provider: ProviderSetting,
        options: ModelResolutionOptions = ModelResolutionOptions(
            preserveDisplayName = true,
            preserveExistingCapabilities = true,
            preserveExistingType = true,
            preserveExistingConfiguration = true,
        ),
    ): ProviderSetting {
        return provider.copyProvider(
            models = applyToModels(provider.models, providerHint = provider, options = options),
        )
    }

    fun applyToModels(
        models: List<Model>,
        providerHint: ProviderSetting? = null,
        options: ModelResolutionOptions = ModelResolutionOptions(),
    ): List<Model> {
        val resolvedModels = models.map { model ->
            applyToModel(model, providerHint = providerHint, options = options)
        }
        if (options.preserveDisplayName) return resolvedModels

        val displayNames = ModelDisplayNameGenerator.generateBatch(
            resolvedModels.map { model -> model.modelId to model.canonicalModelId },
        )
        return resolvedModels.mapIndexed { index, model ->
            model.copy(displayName = displayNames[index])
        }
    }

    fun estimateCostUsd(
        model: Model,
        promptTokens: Int,
        completionTokens: Int,
    ): Double? {
        val catalogEntry = resolveCatalogEntry(model = model) ?: return null
        val inputCost = catalogEntry.inputCostPerToken ?: return null
        val outputCost = catalogEntry.outputCostPerToken ?: return null
        return (promptTokens * inputCost) + (completionTokens * outputCost)
    }

    private fun resolveCatalogEntry(
        model: Model,
        providerHint: ProviderSetting? = null,
    ): ModelCatalogEntry? {
        val snapshot = snapshotProvider() ?: return null

        snapshot.resolveModelEntry(
            modelId = model.modelId,
            canonicalHint = model.canonicalModelId,
            providerHint = providerHint,
            providerSlugHint = model.providerSlug,
        )?.let { return it }

        snapshot.exactEntries[model.modelId.lowercase()]?.let { return it }

        val storedCanonicalKey = model.canonicalModelId
            ?.takeIf { it.isNotBlank() }
            ?.let { ModelIdNormalizer.canonicalize(model.modelId, it) }
        if (storedCanonicalKey != null) {
            snapshot.exactEntries[storedCanonicalKey]?.let { return it }
            snapshot.canonicalEntries[storedCanonicalKey]
                ?.let { selectCatalogCandidate(it, model, providerHint) }
                ?.let { return it }
        }

        val canonicalModelId = ModelIdNormalizer.canonicalize(
            modelId = model.modelId,
            canonicalHint = model.canonicalModelId,
        )
        snapshot.exactEntries[canonicalModelId]?.let { return it }
        val candidates = snapshot.canonicalEntries[canonicalModelId]
        return candidates
            ?.let { selectCatalogCandidate(it, model, providerHint) }
            ?: snapshot.inferFamilyEntry(
                modelId = model.modelId,
                canonicalHint = model.canonicalModelId,
            )
    }

    private fun selectCatalogCandidate(
        candidates: List<ModelCatalogEntry>,
        model: Model,
        providerHint: ProviderSetting?,
    ): ModelCatalogEntry? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.single()

        val slugMatches = candidates.filter { candidate ->
            candidate.matchesProviderSlug(model.providerSlug)
        }
        if (slugMatches.size == 1) {
            return slugMatches.single()
        }

        val providerMatches = candidates.filter { candidate ->
            candidate.matchesProviderHint(providerHint)
        }
        return providerMatches.singleOrNull()
    }

    private fun resolveType(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        options: ModelResolutionOptions,
    ): ModelType {
        if (options.preserveExistingType) {
            return model.type
        }

        return catalogEntry?.mode.toModelTypeOrNull() ?: model.type
    }

    private fun resolveInputModalities(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        resolvedType: ModelType,
        options: ModelResolutionOptions,
    ): List<Modality> {
        if (options.preserveExistingCapabilities) {
            return model.inputModalities
        }

        if (catalogEntry == null) {
            return model.inputModalities
        }

        val inputs = linkedSetOf<Modality>()
        inputs += model.inputModalities
        if (catalogEntry.supportsVision || catalogEntry.supportedModalities.contains(Modality.IMAGE)) {
            inputs += Modality.IMAGE
        }

        return when (resolvedType) {
            ModelType.CHAT -> catalogEntry.inputModalities.takeIf { it.isNotEmpty() } ?: inputs.toList()
            ModelType.IMAGE -> catalogEntry.inputModalities.takeIf { it.isNotEmpty() } ?: inputs.toList()
            ModelType.EMBEDDING -> listOf(Modality.TEXT)
            ModelType.STT -> listOf(Modality.AUDIO)
        }
    }

    private fun resolveOutputModalities(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        resolvedType: ModelType,
        options: ModelResolutionOptions,
    ): List<Modality> {
        if (options.preserveExistingCapabilities) {
            return model.outputModalities
        }
        if (catalogEntry == null) {
            return model.outputModalities
        }

        return when (resolvedType) {
            ModelType.CHAT -> catalogEntry.outputModalities.takeIf { it.isNotEmpty() }
                ?: model.outputModalities

            ModelType.IMAGE -> catalogEntry.outputModalities.takeIf { it.isNotEmpty() } ?: buildList {
                if (catalogEntry.supportedModalities.contains(Modality.TEXT)) {
                    add(Modality.TEXT)
                }
                add(Modality.IMAGE)
            }.distinct()

            ModelType.EMBEDDING -> listOf(Modality.TEXT)
            ModelType.STT -> listOf(Modality.TEXT)
        }
    }

    private fun resolveAbilities(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        options: ModelResolutionOptions,
    ): List<ModelAbility> {
        if (options.preserveExistingCapabilities) {
            return model.abilities
        }
        if (catalogEntry == null) {
            return model.abilities
        }

        val abilities = linkedSetOf<ModelAbility>()
        if (catalogEntry.supportsFunctionCalling) {
            abilities += ModelAbility.TOOL
        }
        if (catalogEntry.supportsReasoning) {
            abilities += ModelAbility.REASONING
        }
        return ModelAbility.entries.filter { it in abilities }
    }
}

private fun String?.toModelTypeOrNull(): ModelType? {
    return when (this?.lowercase()) {
        "embedding" -> ModelType.EMBEDDING
        "image_generation", "image" -> ModelType.IMAGE
        "chat" -> ModelType.CHAT
        "stt" -> ModelType.STT
        else -> null
    }
}

private fun ModelCatalogEntry.matchesProviderSlug(providerSlug: String?): Boolean {
    val normalizedSlug = providerSlug?.normalizeProviderToken() ?: return false
    val keyProvider = key.substringBefore("/").takeIf { key.contains("/") }?.normalizeProviderToken()
    val providerToken = this.providerSlug?.normalizeProviderToken()
    return keyProvider == normalizedSlug || providerToken == normalizedSlug
}

private fun ModelCatalogEntry.matchesProviderHint(providerHint: ProviderSetting?): Boolean {
    val allowedProviders = when (providerHint) {
        is ProviderSetting.Claude -> setOf("anthropic")
        is ProviderSetting.Google -> {
            if (providerHint.vertexAI) {
                setOf("vertex-ai", "vertex-ai-language-models")
            } else {
                setOf("gemini", "google-ai-studio")
            }
        }

        is ProviderSetting.OpenAI -> {
            if (providerHint.baseUrl.contains("api.openai.com", ignoreCase = true)) {
                setOf("openai")
            } else {
                emptySet()
            }
        }

        is ProviderSetting.ComfyUI -> emptySet()

        is ProviderSetting.LiteRtLocal -> emptySet()

        null -> emptySet()
    }
    if (allowedProviders.isEmpty()) return false

    val keyProvider = key.substringBefore("/").takeIf { key.contains("/") }?.normalizeProviderToken()
    val providerToken = providerSlug?.normalizeProviderToken()
    return allowedProviders.any { candidate ->
        candidate == keyProvider || candidate == providerToken
    }
}

private fun String.toIconProviderSlug(): String {
    return when (normalizeProviderToken()) {
        "gemini",
        "google-ai-studio",
        "vertex-ai",
        "vertex-ai-language-models" -> "google"
        "azure-openai",
        "azure-ai",
        "azure" -> "azure"
        "bedrock-converse" -> "bedrock"
        else -> normalizeProviderToken()
    }
}

private fun String.normalizeProviderToken(): String {
    return lowercase()
        .replace('_', '-')
        .replace('.', '-')
}

private fun String?.preserveUserModelIcon(): String? {
    if (isNullOrBlank()) return null
    return if (isCatalogManagedIconUri()) {
        null
    } else {
        this
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
