package me.rerere.locallm

import me.rerere.common.runtime.local.InstalledLocalModel
import me.rerere.common.runtime.local.LocalModelMetadata

typealias LocalModelKind = me.rerere.common.runtime.local.LocalModelKind
typealias LocalAccelerator = me.rerere.common.runtime.local.LocalAccelerator
typealias LocalModelConfig = me.rerere.common.runtime.local.LocalModelConfig
typealias LocalModelRuntimeFlags = me.rerere.common.runtime.local.LocalModelRuntimeFlags
typealias LocalModelDefaultConfig = me.rerere.common.runtime.local.LocalModelDefaultConfig
typealias LocalModelMetadata = me.rerere.common.runtime.local.LocalModelMetadata
typealias LocalModelCatalog = me.rerere.common.runtime.local.LocalModelCatalog
typealias InstalledLocalModel = me.rerere.common.runtime.local.InstalledLocalModel

fun InstalledLocalModel.effectiveRuntimeContextLength(totalRamGb: Int): Int {
    val outputTokens = config.maxTokens ?: defaultConfig.maxTokens
    val modelCeiling = defaultConfig.maxContextLength ?: defaultConfig.effectiveContextLength
    val requestedContext = config.contextLength ?: defaultConfig.effectiveContextLength
    val withinModel = maxOf(requestedContext, outputTokens).coerceAtMost(modelCeiling)
    return minOf(withinModel, MemoryGuard.safeContextTokenCap(totalRamGb)).coerceAtLeast(512)
}

internal fun InstalledLocalModel.withCatalogMetadata(
    meta: LocalModelMetadata,
    tokenizerPath: String? = this.tokenizerPath,
): InstalledLocalModel = copy(
    displayName = displayName.ifBlank { meta.name },
    kind = meta.kind,
    tokenizerPath = if (meta.kind == me.rerere.common.runtime.local.LocalModelKind.EMBEDDING) tokenizerPath else null,
    minDeviceMemoryGb = meta.minDeviceMemoryInGb,
    supportsImage = meta.supportsImage,
    supportsAudio = meta.supportsAudio,
    supportsThinking = meta.supportsThinking,
    supportsSpeculativeDecoding = meta.supportsSpeculativeDecoding,
    embeddingDimension = meta.embeddingDimension,
    defaultConfig = meta.defaultConfig,
)
