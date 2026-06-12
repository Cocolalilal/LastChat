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
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.configureClientWithProxy
import me.rerere.ai.util.configureReferHeaders
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomBody
import me.rerere.ai.util.toHeaders
import me.rerere.common.http.await
import me.rerere.common.http.getByKey
import me.rerere.common.http.jsonPrimitiveOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal

class OpenAIProvider(
    private val client: OkHttpClient
) : Provider<ProviderSetting.OpenAI> {
    private val keyRoulette = KeyRoulette.default()

    private val chatCompletionsAPI = ChatCompletionsAPI(client = client, keyRoulette = keyRoulette)
    private val responseAPI = ResponseAPI(client = client)


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
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .configureReferHeaders(providerSetting.baseUrl)
            .get()
            .build()

        val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
        if (!response.isSuccessful) {
            // Don't fail completely if embedding endpoint fails, just return empty
            if (forceEmbeddingType) {
                return emptyList()
            }
            error("Failed to get models: ${response.code} ${response.body?.string()}")
        }

        val bodyStr = response.body?.string() ?: ""
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
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $key")
            .configureReferHeaders(providerSetting.baseUrl)
            .get()
            .build()
        val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
        if (!response.isSuccessful) {
            error("Failed to get balance: ${response.code} ${response.body?.string()}")
        }

        val bodyStr = response.body.string()
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

        val request = Request.Builder()
            .url("${providerSetting.baseUrl}/images/generations")
            .headers(params.customHeaders.toHeaders())
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .configureReferHeaders(providerSetting.baseUrl)
            .build()

        val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
        if (!response.isSuccessful) {
            error("Failed to generate image: ${response.code} ${response.body?.string()}")
        }

        val bodyStr = response.body?.string() ?: ""
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

        val request = Request.Builder()
            .url("${providerSetting.baseUrl}/embeddings")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .configureReferHeaders(providerSetting.baseUrl)
            .build()

        val response =
            client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
        if (!response.isSuccessful) {
            error("Failed to create embedding: ${response.code} ${response.body?.string()}")
        }

        val bodyStr = response.body?.string() ?: ""
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
