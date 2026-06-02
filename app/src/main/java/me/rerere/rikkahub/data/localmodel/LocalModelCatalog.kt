package me.rerere.rikkahub.data.localmodel

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import me.rerere.ai.provider.LocalModelAccelerator
import me.rerere.ai.provider.LocalModelDownloadState
import me.rerere.ai.provider.LocalModelRuntime
import me.rerere.ai.provider.LocalModelSamplerDefaults
import me.rerere.ai.provider.LocalModelUpdateInfo
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.data.db.entity.LocalModelInstallEntity
import me.rerere.rikkahub.utils.jsonPrimitiveOrNull
import kotlin.uuid.Uuid

private val curatedIds = setOf(
    "Gemma-4-E2B-it",
    "Gemma-4-E4B-it",
    "Gemma-3n-E2B-it",
    "Gemma-3n-E4B-it",
    "Gemma3-1B-IT",
    "Qwen2.5-1.5B-Instruct",
    "DeepSeek-R1-Distill-Qwen-1.5B",
)

@Serializable
data class LocalModelCatalogEntry(
    val name: String,
    val repoId: String,
    val fileName: String,
    val description: String,
    val sizeBytes: Long,
    val minRamGb: Int,
    val revision: String,
    val supportsImage: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsTools: Boolean = false,
    val speculativeDecoding: Boolean = false,
    val defaults: LocalModelSamplerDefaults = LocalModelSamplerDefaults(),
    val updateRevision: String = "",
    val updateFileName: String = "",
    val updateInfo: String = "",
) {
    val catalogId: String = name
    val downloadUrl: String
        get() = "https://huggingface.co/$repoId/resolve/$revision/$fileName?download=true"
}

val BUNDLED_LOCAL_MODEL_CATALOG = listOf(
    LocalModelCatalogEntry(
        name = "Gemma-4-E2B-it",
        repoId = "litert-community/gemma-4-E2B-it-litert-lm",
        fileName = "gemma-4-E2B-it.litertlm",
        description = "Gemma 4 E2B LiteRT-LM chat model with multimodal input and 32K context.",
        sizeBytes = 2588147712,
        minRamGb = 8,
        revision = "6e5c4f1e395deb959c494953478fa5cec4b8008f",
        supportsImage = true,
        supportsAudio = true,
        supportsReasoning = true,
        supportsTools = true,
        speculativeDecoding = true,
        defaults = LocalModelSamplerDefaults(
            topK = 64,
            topP = 0.95f,
            temperature = 1.0f,
            maxTokens = 4000,
            maxContextLength = 32000,
            accelerators = listOf(LocalModelAccelerator.GPU, LocalModelAccelerator.CPU),
            visionAccelerator = LocalModelAccelerator.GPU,
            speculativeDecoding = true,
        ),
        updateRevision = "7fa1d78473894f7e736a21d920c3aa80f950c0db",
        updateFileName = "gemma-4-E2B-it.litertlm",
        updateInfo = "Updated model uses Gemma 4 built-in Multi Token Prediction for faster decode on select workloads.",
    ),
    LocalModelCatalogEntry(
        name = "Gemma-4-E4B-it",
        repoId = "litert-community/gemma-4-E4B-it-litert-lm",
        fileName = "gemma-4-E4B-it.litertlm",
        description = "Gemma 4 E4B LiteRT-LM chat model with multimodal input and 32K context.",
        sizeBytes = 3659530240,
        minRamGb = 12,
        revision = "28299f30ee4d43294517a4ac93abd6163412f07f",
        supportsImage = true,
        supportsAudio = true,
        supportsReasoning = true,
        supportsTools = true,
        speculativeDecoding = true,
        defaults = LocalModelSamplerDefaults(
            topK = 64,
            topP = 0.95f,
            temperature = 1.0f,
            maxTokens = 4000,
            maxContextLength = 32000,
            accelerators = listOf(LocalModelAccelerator.GPU, LocalModelAccelerator.CPU),
            visionAccelerator = LocalModelAccelerator.GPU,
            speculativeDecoding = true,
        ),
        updateRevision = "9695417f248178c63a9f318c6e0c56cb917cb837",
        updateFileName = "gemma-4-E4B-it.litertlm",
        updateInfo = "Updated model uses Gemma 4 built-in Multi Token Prediction for faster decode on select workloads.",
    ),
    LocalModelCatalogEntry(
        name = "Gemma-3n-E2B-it",
        repoId = "google/gemma-3n-E2B-it-litert-lm",
        fileName = "gemma-3n-E2B-it-int4.litertlm",
        description = "Gemma 3n E2B LiteRT-LM model with text, vision, and audio input.",
        sizeBytes = 3655827456,
        minRamGb = 8,
        revision = "ba9ca88da013b537b6ed38108be609b8db1c3a16",
        supportsImage = true,
        supportsAudio = true,
        defaults = LocalModelSamplerDefaults(
            topK = 64,
            topP = 0.95f,
            temperature = 1.0f,
            maxTokens = 4096,
            maxContextLength = 4096,
            accelerators = listOf(LocalModelAccelerator.CPU, LocalModelAccelerator.GPU),
        ),
    ),
    LocalModelCatalogEntry(
        name = "Gemma-3n-E4B-it",
        repoId = "google/gemma-3n-E4B-it-litert-lm",
        fileName = "gemma-3n-E4B-it-int4.litertlm",
        description = "Gemma 3n E4B LiteRT-LM model with text, vision, and audio input.",
        sizeBytes = 4919541760,
        minRamGb = 12,
        revision = "297ed75955702dec3503e00c2c2ecbbf475300bc",
        supportsImage = true,
        supportsAudio = true,
        defaults = LocalModelSamplerDefaults(
            topK = 64,
            topP = 0.95f,
            temperature = 1.0f,
            maxTokens = 4096,
            maxContextLength = 4096,
            accelerators = listOf(LocalModelAccelerator.CPU, LocalModelAccelerator.GPU),
        ),
    ),
    LocalModelCatalogEntry(
        name = "Gemma3-1B-IT",
        repoId = "litert-community/Gemma3-1B-IT",
        fileName = "gemma3-1b-it-int4.litertlm",
        description = "Small Gemma 3 1B instruct model with 4-bit quantization.",
        sizeBytes = 584417280,
        minRamGb = 6,
        revision = "42d538a932e8d5b12e6b3b455f5572560bd60b2c",
        defaults = LocalModelSamplerDefaults(
            topK = 64,
            topP = 0.95f,
            temperature = 1.0f,
            maxTokens = 1024,
            maxContextLength = 1024,
            accelerators = listOf(LocalModelAccelerator.GPU, LocalModelAccelerator.CPU),
        ),
    ),
    LocalModelCatalogEntry(
        name = "Qwen2.5-1.5B-Instruct",
        repoId = "litert-community/Qwen2.5-1.5B-Instruct",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        description = "Qwen2.5 1.5B instruct model packaged for LiteRT-LM.",
        sizeBytes = 1597931520,
        minRamGb = 6,
        revision = "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
        defaults = LocalModelSamplerDefaults(
            topK = 20,
            topP = 0.8f,
            temperature = 0.7f,
            maxTokens = 4096,
            maxContextLength = 4096,
            accelerators = listOf(LocalModelAccelerator.GPU, LocalModelAccelerator.CPU),
        ),
    ),
    LocalModelCatalogEntry(
        name = "DeepSeek-R1-Distill-Qwen-1.5B",
        repoId = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
        fileName = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
        description = "DeepSeek R1 distilled Qwen 1.5B reasoning model packaged for LiteRT-LM.",
        sizeBytes = 1833451520,
        minRamGb = 6,
        revision = "e34bb88632342d1f9640bad579a45134eb1cf988",
        supportsReasoning = true,
        defaults = LocalModelSamplerDefaults(
            topK = 64,
            topP = 0.95f,
            temperature = 1.0f,
            maxTokens = 4096,
            maxContextLength = 4096,
            accelerators = listOf(LocalModelAccelerator.GPU, LocalModelAccelerator.CPU),
        ),
    ),
)

fun parseGalleryAllowlist(json: String): List<LocalModelCatalogEntry> {
    val root = runCatching { me.rerere.rikkahub.utils.JsonInstant.parseToJsonElement(json) }.getOrNull() as? JsonObject
        ?: return emptyList()
    val models = root["models"] as? JsonArray ?: return emptyList()
    return models.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val name = obj.string("name")?.takeIf { it in curatedIds } ?: return@mapNotNull null
        val defaultConfig = obj["defaultConfig"] as? JsonObject ?: JsonObject(emptyMap())
        val capabilities = (obj["capabilities"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.content }
            ?.toSet()
            ?: emptySet()
        val updateFile = (obj["updatableModelFiles"] as? JsonArray)
            ?.firstOrNull() as? JsonObject
        LocalModelCatalogEntry(
            name = name,
            repoId = obj.string("modelId") ?: return@mapNotNull null,
            fileName = obj.string("modelFile") ?: return@mapNotNull null,
            description = obj.string("description").orEmpty(),
            sizeBytes = obj.long("sizeInBytes") ?: 0L,
            minRamGb = obj.int("minDeviceMemoryInGb") ?: 0,
            revision = obj.string("commitHash") ?: return@mapNotNull null,
            supportsImage = obj.boolean("llmSupportImage"),
            supportsAudio = obj.boolean("llmSupportAudio"),
            supportsReasoning = "llm_thinking" in capabilities || name.contains("DeepSeek", ignoreCase = true),
            supportsTools = name.startsWith("Gemma-4"),
            speculativeDecoding = "speculative_decoding" in capabilities,
            defaults = LocalModelSamplerDefaults(
                topK = defaultConfig.int("topK") ?: 64,
                topP = defaultConfig.float("topP") ?: 0.95f,
                temperature = defaultConfig.float("temperature") ?: 1.0f,
                maxTokens = defaultConfig.int("maxTokens") ?: 1024,
                maxContextLength = defaultConfig.int("maxContextLength") ?: defaultConfig.int("maxTokens"),
                accelerators = parseAccelerators(defaultConfig.string("accelerators")),
                visionAccelerator = parseAccelerator(defaultConfig.string("visionAccelerator")),
                speculativeDecoding = "speculative_decoding" in capabilities,
            ),
            updateRevision = updateFile?.string("commitHash").orEmpty(),
            updateFileName = updateFile?.string("fileName").orEmpty(),
            updateInfo = obj.string("updateInfo").orEmpty(),
        )
    }.sortedBy { curatedIds.indexOfCompat(it.name) }
}

fun recommendedLocalModel(catalog: List<LocalModelCatalogEntry>, context: Context): LocalModelCatalogEntry? {
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return catalog.firstOrNull()
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val totalRamGb = (memoryInfo.totalMem / 1_073_741_824L).coerceAtLeast(1L).toInt()
    val supported = catalog.filter { it.minRamGb <= totalRamGb }.ifEmpty { catalog }
    return supported.firstOrNull { it.name == "Gemma-4-E2B-it" && supportsGpuByDefault() }
        ?: supported.firstOrNull { it.name == "Gemma3-1B-IT" }
        ?: supported.minByOrNull { it.sizeBytes }
}

fun LocalModelCatalogEntry.toInstallEntity(
    status: LocalModelDownloadState,
    localPath: String = "",
    selectedAccelerator: LocalModelAccelerator = defaultAccelerator(),
    progressPercent: Int = 0,
    bytesDownloaded: Long = 0L,
    bytesTotal: Long = sizeBytes,
    error: String = "",
): LocalModelInstallEntity = LocalModelInstallEntity(
    catalogId = catalogId,
    repoId = repoId,
    revision = revision,
    modelId = name,
    displayName = name,
    fileName = fileName,
    localPath = localPath,
    status = status.name,
    progressPercent = progressPercent,
    bytesDownloaded = bytesDownloaded,
    bytesTotal = bytesTotal,
    sizeBytes = sizeBytes,
    minRamGb = minRamGb,
    description = description,
    topK = defaults.topK,
    topP = defaults.topP,
    temperature = defaults.temperature,
    maxTokens = defaults.maxTokens,
    contextWindowTokens = defaults.maxContextLength,
    acceleratorsCsv = defaults.accelerators.joinToString(",") { it.name.lowercase() },
    selectedAccelerator = selectedAccelerator.name.lowercase(),
    visionAccelerator = defaults.visionAccelerator?.name?.lowercase().orEmpty(),
    supportsImage = supportsImage,
    supportsAudio = supportsAudio,
    supportsReasoning = supportsReasoning,
    supportsTools = supportsTools,
    speculativeDecoding = speculativeDecoding,
    updateRevision = updateRevision,
    updateFileName = updateFileName,
    updateInfo = updateInfo,
    imported = false,
    lastError = error,
    updatedAt = System.currentTimeMillis(),
)

fun LocalModelInstallEntity.toModel(): Model {
    val state = runCatching { LocalModelDownloadState.valueOf(status) }.getOrDefault(LocalModelDownloadState.DOWNLOADED)
    val accelerators = parseAccelerators(acceleratorsCsv)
    val selected = parseAccelerator(selectedAccelerator) ?: LocalModelAccelerator.AUTO
    val update = updateRevision.takeIf { it.isNotBlank() && it != revision }?.let {
        LocalModelUpdateInfo(
            latestRevision = it,
            latestFileName = updateFileName.ifBlank { fileName },
            message = updateInfo,
            updateAvailable = !imported,
        )
    }
    return Model(
        id = runCatching { Uuid.parse(catalogId.toUuidLike()) }.getOrDefault(Uuid.random()),
        modelId = modelId,
        displayName = displayName,
        type = ModelType.CHAT,
        inputModalities = buildList {
            add(Modality.TEXT)
            if (supportsImage) add(Modality.IMAGE)
        },
        outputModalities = listOf(Modality.TEXT),
        abilities = buildList {
            if (supportsTools) add(me.rerere.ai.provider.ModelAbility.TOOL)
            if (supportsReasoning) add(me.rerere.ai.provider.ModelAbility.REASONING)
        },
        providerSlug = "local",
        contextWindowTokens = contextWindowTokens,
        localRuntime = LocalModelRuntime.LITERT_LM,
        localModelPath = localPath,
        localCatalogId = catalogId,
        localRevision = revision,
        localFileName = fileName,
        localDownloadState = state,
        localUpdateInfo = update,
        localSamplerDefaults = LocalModelSamplerDefaults(
            topK = topK,
            topP = topP,
            temperature = temperature,
            maxTokens = maxTokens,
            maxContextLength = contextWindowTokens,
            accelerators = accelerators,
            visionAccelerator = parseAccelerator(visionAccelerator),
            speculativeDecoding = speculativeDecoding,
        ),
        localAccelerator = selected,
        localImported = imported,
    )
}

fun parseAccelerators(value: String?): List<LocalModelAccelerator> {
    val parsed = value.orEmpty()
        .split(",")
        .mapNotNull { parseAccelerator(it) }
        .distinct()
    return parsed.ifEmpty { listOf(LocalModelAccelerator.CPU) }
}

fun parseAccelerator(value: String?): LocalModelAccelerator? = when (value?.trim()?.lowercase()) {
    "auto" -> LocalModelAccelerator.AUTO
    "cpu" -> LocalModelAccelerator.CPU
    "gpu" -> LocalModelAccelerator.GPU
    "npu" -> LocalModelAccelerator.NPU
    "tpu" -> LocalModelAccelerator.TPU
    else -> null
}

fun LocalModelCatalogEntry.defaultAccelerator(): LocalModelAccelerator {
    val accelerators = defaults.accelerators
    return when {
        Build.MANUFACTURER.equals("google", ignoreCase = true) &&
            Build.DEVICE.startsWith("komodo", ignoreCase = true) &&
            LocalModelAccelerator.CPU in accelerators -> LocalModelAccelerator.CPU
        LocalModelAccelerator.GPU in accelerators -> LocalModelAccelerator.GPU
        LocalModelAccelerator.NPU in accelerators -> LocalModelAccelerator.NPU
        LocalModelAccelerator.TPU in accelerators -> LocalModelAccelerator.TPU
        else -> accelerators.firstOrNull() ?: LocalModelAccelerator.CPU
    }
}

private fun supportsGpuByDefault(): Boolean = true

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitiveOrNull?.content
private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitiveOrNull?.intOrNull
private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitiveOrNull?.longOrNull
private fun JsonObject.float(key: String): Float? = this[key]?.jsonPrimitiveOrNull?.floatOrNull
private fun JsonObject.boolean(key: String): Boolean = this[key]?.jsonPrimitiveOrNull?.content?.toBooleanStrictOrNull() == true

private fun Set<String>.indexOfCompat(name: String): Int {
    val index = toList().indexOf(name)
    return if (index >= 0) index else Int.MAX_VALUE
}

private fun String.toUuidLike(): String {
    val hex = hashCode().toUInt().toString(16).padStart(8, '0')
    return "$hex-0000-4000-8000-000000000000"
}
