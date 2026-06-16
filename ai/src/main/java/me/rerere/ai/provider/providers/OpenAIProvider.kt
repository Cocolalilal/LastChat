package me.rerere.ai.provider.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.openai.ChatCompletionsAPI
import me.rerere.ai.provider.providers.openai.ResponseAPI
import me.rerere.ai.registry.ModelIdNormalizer
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.ProviderProxy
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomBody
import me.rerere.common.http.getByKey
import me.rerere.common.http.jsonPrimitiveOrNull
import me.rerere.common.http.urlHostOrNull
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpProxy
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformMediaEncoder

class OpenAIProvider(
    private val platformHttpClient: PlatformHttpClient,
    private val platformMediaEncoder: PlatformMediaEncoder,
) : Provider<ProviderSetting.OpenAI> {
    private val keyRoulette = KeyRoulette.default()

    private val chatCompletionsAPI = ChatCompletionsAPI(
        httpClient = platformHttpClient,
        keyRoulette = keyRoulette,
        mediaEncoder = platformMediaEncoder,
    )
    private val responseAPI = ResponseAPI(
        httpClient = platformHttpClient,
        mediaEncoder = platformMediaEncoder,
    )


    override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> =
        withContext(Dispatchers.IO) {
            val key = keyRoulette.next(providerSetting.apiKey)
            
            // Fetch regular models
            val regularModels = fetchModelsFromUrl(
                url = "${providerSetting.baseUrl}/models",
                key = key,
                providerSetting = providerSetting
            )
            
            // For OpenRouter, also fetch embedding models using output_modalities filter
            // OpenRouter's /models endpoint doesn't return embedding models by default
            val isOpenRouter = providerSetting.baseUrl.contains("openrouter.ai", ignoreCase = true)
            val embeddingModels = if (isOpenRouter) {
                fetchModelsFromUrl(
                    url = "${providerSetting.baseUrl}/models?output_modalities=embeddings",
                    key = key,
                    providerSetting = providerSetting,
                    forceEmbeddingType = true
                )
            } else {
                emptyList()
            }
            
            // Combine and deduplicate by model ID
            val allModels = (regularModels + embeddingModels)
                .distinctBy { it.modelId }
            
            allModels
        }
    
    private suspend fun fetchModelsFromUrl(
        url: String,
        key: String,
        providerSetting: ProviderSetting.OpenAI,
        forceEmbeddingType: Boolean = false
    ): List<Model> {
        val response = platformHttpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = url,
                headers = mapOf("Authorization" to "Bearer $key")
                    .withReferHeaders(providerSetting.baseUrl),
                proxy = providerSetting.proxy.toPlatformProxy()
            )
        )
        if (response.statusCode !in 200..299) {
            // Don't fail completely if embedding endpoint fails, just return empty
            if (forceEmbeddingType) {
                return emptyList()
            }
            error("Failed to get models: ${response.statusCode} ${response.body.decodeToString()}")
        }

        val bodyStr = response.body.decodeToString()
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
        val data = bodyJson["data"]?.jsonArray ?: return emptyList()

        return data.mapNotNull { modelJson ->
            val modelObj = modelJson.jsonObject
            val id = modelObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

            // Check if model is embedding type via:
            // 1. Model ID contains "embed"
            // 2. architecture.modality contains "embedding" (OpenRouter format)
            // 3. architecture.output_modalities contains "embedding" (OpenRouter array format)
            // 4. Forced by forceEmbeddingType parameter (for OpenRouter embedding endpoint)
            val architecture = modelObj["architecture"]?.jsonObject
            val modality = architecture?.get("modality")?.jsonPrimitive?.contentOrNull
            val inputModalitiesRaw = architecture?.get("input_modalities")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
            val outputModalitiesRaw = architecture?.get("output_modalities")?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                ?: emptyList()
            val inputModalities = inputModalitiesRaw.toModalities().ifEmpty { listOf(Modality.TEXT) }
            val outputModalities = outputModalitiesRaw.toModalities().ifEmpty { listOf(Modality.TEXT) }
            val supportedParameters = modelObj["supported_parameters"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.lowercase() }
                ?: emptyList()
            
            val isEmbedding = forceEmbeddingType ||
                id.contains("embed", ignoreCase = true) ||
                modality?.contains("embedding", ignoreCase = true) == true ||
                outputModalitiesRaw.any { it.contains("embedding", ignoreCase = true) }

            val isImageModel = !isEmbedding &&
                outputModalities.contains(Modality.IMAGE) &&
                !outputModalities.contains(Modality.TEXT)
            
            val canonicalSlug = modelObj["canonical_slug"]?.jsonPrimitive?.contentOrNull
            val displayName = modelObj["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: id
            val abilities = buildList {
                if (supportedParameters.any { it == "tools" || it == "tool_choice" }) {
                    add(ModelAbility.TOOL)
                }
                if (supportedParameters.any { it in setOf("reasoning", "include_reasoning", "reasoning_effort", "thinking") }) {
                    add(ModelAbility.REASONING)
                }
            }
            
            Model(
                modelId = id,
                displayName = displayName,
                canonicalModelId = ModelIdNormalizer.canonicalize(id, canonicalSlug),
                type = when {
                    isEmbedding -> ModelType.EMBEDDING
                    isImageModel -> ModelType.IMAGE
                    else -> ModelType.CHAT
                },
                inputModalities = inputModalities,
                outputModalities = if (isEmbedding) listOf(Modality.TEXT) else outputModalities,
                abilities = abilities,
            )
        }
    }

    override suspend fun getBalance(providerSetting: ProviderSetting.OpenAI): String = withContext(Dispatchers.IO) {
        val key = keyRoulette.next(providerSetting.apiKey)
        val url = if (providerSetting.balanceOption.apiPath.startsWith("http")) {
            providerSetting.balanceOption.apiPath
        } else {
            "${providerSetting.baseUrl}${providerSetting.balanceOption.apiPath}"
        }
        val response = platformHttpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = url,
                headers = mapOf("Authorization" to "Bearer $key")
                    .withReferHeaders(providerSetting.baseUrl),
                proxy = providerSetting.proxy.toPlatformProxy()
            )
        )
        if (response.statusCode !in 200..299) {
            error("Failed to get balance: ${response.statusCode} ${response.body.decodeToString()}")
        }

        val bodyStr = response.body.decodeToString()
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
        val value = bodyJson.getByKey(providerSetting.balanceOption.resultPath)
        val digitalValue = value.toFloatOrNull()
        if(digitalValue != null) {
            "%.2f".format(digitalValue)
        } else {
            value
        }
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> = if (providerSetting.useResponseApi) {
        responseAPI.streamText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    } else {
        chatCompletionsAPI.streamText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.OpenAI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk = if (providerSetting.useResponseApi) {
        responseAPI.generateText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    } else {
        chatCompletionsAPI.generateText(
            providerSetting = providerSetting,
            messages = messages,
            params = params
        )
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        require(providerSetting is ProviderSetting.OpenAI) {
            "Expected OpenAI provider setting"
        }

        val key = keyRoulette.next(providerSetting.apiKey)

        val requestBody = json.encodeToString(
            buildJsonObject {
                put("model", params.model.modelId)
                put("prompt", params.prompt)
                // DALL-E 3 only supports n=1, DALL-E 2 supports up to 10
                val isDalle3 = params.model.modelId.contains("dall-e-3", ignoreCase = true)
                put("n", if (isDalle3) 1 else params.numOfImages.coerceIn(1, 10))
                put("response_format", "b64_json")
                // DALL-E 3: 1024x1024, 1792x1024, 1024x1792
                // DALL-E 2: 256x256, 512x512, 1024x1024
                put(
                    "size", when {
                        isDalle3 -> when (params.aspectRatio) {
                            ImageAspectRatio.SQUARE -> "1024x1024"
                            ImageAspectRatio.LANDSCAPE -> "1792x1024"
                            ImageAspectRatio.PORTRAIT -> "1024x1792"
                        }
                        else -> "1024x1024" // DALL-E 2 only supports square
                    }
                )
            }.mergeCustomBody(params.customBody)
        )

        val response = platformHttpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/images/generations",
                headers = params.customHeaders.toHeaderMap()
                    .withReferHeaders(providerSetting.baseUrl)
                    .withAuthAndJson(key),
                body = requestBody.encodeToByteArray(),
                mediaType = "application/json",
                proxy = providerSetting.proxy.toPlatformProxy()
            )
        )
        if (response.statusCode !in 200..299) {
            error("Failed to generate image: ${response.statusCode} ${response.body.decodeToString()}")
        }

        val bodyStr = response.body.decodeToString()
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
        val data = bodyJson["data"]?.jsonArray ?: error("No data in response")

        val items = data.map { imageJson ->
            val imageObj = imageJson.jsonObject
            val b64Json = imageObj["b64_json"]?.jsonPrimitive?.contentOrNull
                ?: error("No b64_json in response")

            ImageGenerationItem(
                data = b64Json,
                mimeType = "image/png"
            )
        }

        ImageGenerationResult(items = items)
    }
    override suspend fun createEmbedding(
        providerSetting: ProviderSetting.OpenAI,
        input: List<String>,
        model: Model
    ): List<List<Float>> = withContext(Dispatchers.IO) {
        val key = keyRoulette.next(providerSetting.apiKey)
        val requestBody = json.encodeToString(
            buildJsonObject {
                put("model", model.modelId)
                put(
                    "input",
                    kotlinx.serialization.json.JsonArray(input.map { kotlinx.serialization.json.JsonPrimitive(it) })
                )
            }
        )

        val response = platformHttpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = "${providerSetting.baseUrl}/embeddings",
                headers = mapOf<String, String>()
                    .withReferHeaders(providerSetting.baseUrl)
                    .withAuthAndJson(key),
                body = requestBody.encodeToByteArray(),
                mediaType = "application/json",
                proxy = providerSetting.proxy.toPlatformProxy()
            )
        )
        if (response.statusCode !in 200..299) {
            error("Failed to create embedding: ${response.statusCode} ${response.body.decodeToString()}")
        }

        val bodyStr = response.body.decodeToString()
        val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
        val data = bodyJson["data"]?.jsonArray ?: error("No data in response")

        data.map { item ->
            item.jsonObject["embedding"]?.jsonArray?.map { it.jsonPrimitive.content.toFloat() }
                ?: error("No embedding in response")
        }
    }
}

private fun List<String>.toModalities(): List<Modality> {
    val modalities = linkedSetOf<Modality>()
    forEach { raw ->
        when (raw.lowercase()) {
            "text" -> modalities += Modality.TEXT
            "image" -> modalities += Modality.IMAGE
        }
    }
    return modalities.toList()
}

private fun List<CustomHeader>.toHeaderMap(): Map<String, String> {
    return filter { it.name.isNotBlank() }.associate { it.name to it.value }
}

private fun Map<String, String>.withAuthAndJson(key: String): Map<String, String> {
    return this + mapOf(
        "Authorization" to "Bearer $key",
        "Content-Type" to "application/json"
    )
}

private fun Map<String, String>.withReferHeaders(baseUrl: String): Map<String, String> {
    return when (baseUrl.urlHostOrNull()) {
        "aihubmix.com" -> this + ("APP-Code" to "DKHA9468")
        "openrouter.ai" -> this + mapOf(
            "X-Title" to "LastChat",
            "HTTP-Referer" to "https://github.com/Cocolalilal/LastChat"
        )
        else -> this
    }
}

private fun ProviderProxy.toPlatformProxy(): PlatformHttpProxy? {
    return when (this) {
        ProviderProxy.None -> null
        is ProviderProxy.Http -> PlatformHttpProxy(
            host = address,
            port = port,
            username = username,
            password = password
        )
    }
}
