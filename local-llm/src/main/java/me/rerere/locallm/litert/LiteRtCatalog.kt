package me.rerere.locallm.litert

/**
 * A single curated entry the Settings -> Local LiteRT picker shows.
 *
 * These entries intentionally mirror Google AI Edge Gallery's current root
 * model_allowlist.json: same repo, filename, version, size, and broad capability.
 */
data class LiteRtCatalogEntry(
    val displayName: String,
    val modelId: String,
    val modelFile: String,
    val description: String,
    val sizeBytes: Long,
    val minDeviceMemoryGb: Int,
    val version: String = "main",
    val recommended: Boolean = false,
    val tags: List<String> = emptyList(),
) {
    fun resolveUrl(): String = "https://huggingface.co/$modelId/resolve/$version/$modelFile?download=true"
    fun config(): LiteRtModelConfig = LiteRtModelDefaults.forModelFile(modelFile)
}

object LiteRtCatalog {
    val ENTRIES: List<LiteRtCatalogEntry> = listOf(
        LiteRtCatalogEntry(
            displayName = "Qwen2.5-1.5B-Instruct q8",
            modelId = "litert-community/Qwen2.5-1.5B-Instruct",
            modelFile = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            description = "A Qwen2.5 1.5B Instruct task package with 8-bit quantization. Gallery runs it CPU-first on phones with about 2.5 GB peak runtime memory.",
            sizeBytes = 1625493432L,
            minDeviceMemoryGb = 6,
            version = "20250514",
            recommended = true,
            tags = listOf("cpu-first"),
        ),
        LiteRtCatalogEntry(
            displayName = "Gemma3-1B-IT q4",
            modelId = "litert-community/Gemma3-1B-IT",
            modelFile = "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
            description = "A compact Gemma 3 1B Instruct task package with 4-bit quantization. Good for a first local install on tighter devices.",
            sizeBytes = 554661246L,
            minDeviceMemoryGb = 4,
            version = "20250514",
            recommended = false,
            tags = listOf("small"),
        ),
        LiteRtCatalogEntry(
            displayName = "Gemma-3n-E2B-it-int4",
            modelId = "google/gemma-3n-E2B-it-litert-preview",
            modelFile = "gemma-3n-E2B-it-int4.task",
            description = "Preview Gemma 3n E2B task package from Google AI Edge Gallery. Supports text and image input with a 4096-token runtime cap.",
            sizeBytes = 3136226711L,
            minDeviceMemoryGb = 8,
            version = "20250520",
            recommended = false,
            tags = listOf("multimodal"),
        ),
        LiteRtCatalogEntry(
            displayName = "Gemma-3n-E4B-it-int4",
            modelId = "google/gemma-3n-E4B-it-litert-preview",
            modelFile = "gemma-3n-E4B-it-int4.task",
            description = "Preview Gemma 3n E4B task package from Google AI Edge Gallery. Larger multimodal model with a higher peak memory requirement.",
            sizeBytes = 4405655031L,
            minDeviceMemoryGb = 8,
            version = "20250520",
            recommended = false,
            tags = listOf("multimodal"),
        ),
    )

    fun findByModelFile(modelFile: String): LiteRtCatalogEntry? =
        ENTRIES.firstOrNull { it.modelFile == modelFile }
}
