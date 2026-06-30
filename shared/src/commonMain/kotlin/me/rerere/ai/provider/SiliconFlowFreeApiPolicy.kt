package me.rerere.ai.provider

object SiliconFlowFreeApiPolicy {
    private const val HOST = "api.siliconflow.cn"
    private val supportedPaths = setOf(
        "/v1/chat/completions",
        "/v1/models"
    )
    private val emptyBearerTokens = setOf("Bearer", "Bearer sk-")

    fun authorizationOverride(
        host: String,
        path: String,
        authorizationHeader: String?,
        model: String?,
        freeModels: Iterable<String>,
        apiKey: String,
    ): String? = if (
        shouldUseFreeApiKey(
            host = host,
            path = path,
            authorizationHeader = authorizationHeader,
            model = model,
            freeModels = freeModels,
        )
    ) {
        "Bearer $apiKey"
    } else {
        null
    }

    fun shouldUseFreeApiKey(
        host: String,
        path: String,
        authorizationHeader: String?,
        model: String?,
        freeModels: Iterable<String>,
    ): Boolean {
        if (host != HOST) return false
        if (path !in supportedPaths) return false
        if (authorizationHeader?.trim() !in emptyBearerTokens) return false

        val normalizedFreeModels = freeModels
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        return model.isNullOrEmpty() || model in normalizedFreeModels
    }
}
