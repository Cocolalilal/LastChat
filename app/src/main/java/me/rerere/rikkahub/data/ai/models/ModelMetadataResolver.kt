package me.rerere.rikkahub.data.ai.models

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelDisplayNameGenerator
import me.rerere.ai.registry.ModelIdNormalizer
import me.rerere.ai.registry.ModelRegistry

data class ModelResolutionOptions(
    val preserveDisplayName: Boolean = false,
    val preserveExistingCapabilities: Boolean = false,
    val preserveExistingType: Boolean = false,
)

class ModelMetadataResolver(
    private val snapshotProvider: () -> ModelCatalogSnapshot?,
) {
    fun applyToModel(
        model: Model,
        options: ModelResolutionOptions = ModelResolutionOptions(),
    ): Model {
        if (model.modelId.isBlank()) return model

        val catalogEntry = resolveCatalogEntry(model)
        val canonicalModelId = model.canonicalModelId
            ?.takeIf { it.isNotBlank() }
            ?.let { ModelIdNormalizer.canonicalize(model.modelId, it) }
            ?: catalogEntry?.canonicalModelId
            ?: ModelIdNormalizer.canonicalize(model.modelId)

        val displayName = if (
            options.preserveDisplayName &&
            model.displayName.isNotBlank() &&
            model.displayName != model.modelId
        ) {
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
            providerSlug = model.providerSlug ?: model.modelId.substringBefore("/").takeIf { model.modelId.contains("/") },
        )
    }

    fun applyToProvider(
        provider: ProviderSetting,
        options: ModelResolutionOptions = ModelResolutionOptions(
            preserveDisplayName = true,
            preserveExistingCapabilities = true,
            preserveExistingType = true,
        ),
    ): ProviderSetting {
        return provider.copyProvider(
            models = provider.models.map { applyToModel(it, options) }
        )
    }

    fun estimateCostUsd(
        model: Model,
        promptTokens: Int,
        completionTokens: Int,
    ): Double? {
        val catalogEntry = resolveCatalogEntry(model) ?: return null
        val inputCost = catalogEntry.inputCostPerToken ?: return null
        val outputCost = catalogEntry.outputCostPerToken ?: return null
        return (promptTokens * inputCost) + (completionTokens * outputCost)
    }

    private fun resolveCatalogEntry(model: Model): ModelCatalogEntry? {
        val snapshot = snapshotProvider() ?: return null
        val canonicalModelId = ModelIdNormalizer.canonicalize(
            modelId = model.modelId,
            canonicalHint = model.canonicalModelId,
        )
        return snapshot.exactEntries[model.modelId.lowercase()]
            ?: snapshot.exactEntries[(model.canonicalModelId ?: "").lowercase()]
            ?: snapshot.canonicalEntries[canonicalModelId]
    }

    private fun resolveType(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        options: ModelResolutionOptions,
    ): ModelType {
        if (options.preserveExistingType && model.type != ModelType.CHAT) {
            return model.type
        }

        if (model.type != ModelType.CHAT) {
            return model.type
        }

        val catalogType = catalogEntry?.mode.toModelTypeOrNull()
        return catalogType ?: model.type
    }

    private fun resolveInputModalities(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        resolvedType: ModelType,
        options: ModelResolutionOptions,
    ): List<Modality> {
        val hasImageInput = linkedSetOf<Modality>().apply {
            if (options.preserveExistingCapabilities && model.inputModalities.contains(Modality.IMAGE)) {
                add(Modality.IMAGE)
            }
            if (catalogEntry?.supportsVision == true) add(Modality.IMAGE)
            if (ModelRegistry.regexInputModalities(model.modelId).contains(Modality.IMAGE)) add(Modality.IMAGE)
        }

        return when (resolvedType) {
            ModelType.CHAT -> listOf(Modality.TEXT) + hasImageInput
            ModelType.IMAGE -> listOf(Modality.TEXT) + hasImageInput
            ModelType.EMBEDDING -> listOf(Modality.TEXT)
        }
    }

    private fun resolveOutputModalities(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        resolvedType: ModelType,
        options: ModelResolutionOptions,
    ): List<Modality> {
        val imageOutput = linkedSetOf<Modality>().apply {
            if (options.preserveExistingCapabilities && model.outputModalities.contains(Modality.IMAGE)) {
                add(Modality.IMAGE)
            }
            if (ModelRegistry.regexOutputModalities(model.modelId).contains(Modality.IMAGE)) add(Modality.IMAGE)
        }

        return when (resolvedType) {
            ModelType.CHAT -> listOf(Modality.TEXT) + imageOutput
            ModelType.IMAGE -> {
                val outputs = linkedSetOf(Modality.IMAGE)
                if (options.preserveExistingCapabilities && model.outputModalities.contains(Modality.TEXT)) {
                    outputs += Modality.TEXT
                }
                outputs.toList()
            }

            ModelType.EMBEDDING -> listOf(Modality.TEXT)
        }
    }

    private fun resolveAbilities(
        model: Model,
        catalogEntry: ModelCatalogEntry?,
        options: ModelResolutionOptions,
    ): List<ModelAbility> {
        val abilities = linkedSetOf<ModelAbility>().apply {
            if (options.preserveExistingCapabilities && model.abilities.contains(ModelAbility.TOOL)) {
                add(ModelAbility.TOOL)
            }
            if (catalogEntry?.supportsFunctionCalling == true) add(ModelAbility.TOOL)
            if (ModelRegistry.regexAbilities(model.modelId).contains(ModelAbility.TOOL)) add(ModelAbility.TOOL)

            if (options.preserveExistingCapabilities && model.abilities.contains(ModelAbility.REASONING)) {
                add(ModelAbility.REASONING)
            }
            if (catalogEntry?.supportsReasoning == true) add(ModelAbility.REASONING)
            if (ModelRegistry.regexAbilities(model.modelId).contains(ModelAbility.REASONING)) add(ModelAbility.REASONING)
        }

        return ModelAbility.entries.filter { it in abilities }
    }
}

private fun String?.toModelTypeOrNull(): ModelType? {
    return when (this?.lowercase()) {
        "embedding" -> ModelType.EMBEDDING
        "image_generation", "image" -> ModelType.IMAGE
        else -> null
    }
}
