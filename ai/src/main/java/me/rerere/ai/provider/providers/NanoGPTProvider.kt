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
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.openai.ChatCompletionsAPI
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.KeyRoulette
import me.rerere.ai.util.configureClientWithProxy
import me.rerere.ai.util.json
import me.rerere.ai.util.mergeCustomBody
import me.rerere.ai.util.toHeaders
import me.rerere.common.http.await
import me.rerere.common.http.getByKey
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * NanoGPT API Provider
 * Supports chat completions, image generation, balance checking, and embeddings
 */
class NanoGPTProvider(
    private val client: OkHttpClient
) : Provider<ProviderSetting.NanoGPT> {
    private val keyRoulette = KeyRoulette.default()
    
    // Reuse OpenAI chat completions API since NanoGPT is OpenAI-compatible
    private val chatCompletionsAPI = ChatCompletionsAPI(client = client, keyRoulette = keyRoulette)

    override suspend fun listModels(providerSetting: ProviderSetting.NanoGPT): List<Model> =
        withContext(Dispatchers.IO) {
            val key = keyRoulette.next(providerSetting.apiKey)
            
            val request = Request.Builder()
                .url("${providerSetting.baseUrl}/v1/models")
                .addHeader("Authorization", "Bearer $key")
                .get()
                .build()

            val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
            if (!response.isSuccessful) {
                error("Failed to get models: ${response.code} ${response.body?.string()}")
            }

            val bodyStr = response.body?.string() ?: ""
            val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
            val data = bodyJson["data"]?.jsonArray ?: return@withContext emptyList()

            data.mapNotNull { modelJson ->
                val modelObj = modelJson.jsonObject
                val id = modelObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                
                // Check if model is embedding type
                val isEmbedding = id.contains("embed", ignoreCase = true)
                
                // Check if model is image generation type
                val isImage = id.contains("flux", ignoreCase = true) ||
                    id.contains("hidream", ignoreCase = true) ||
                    id.contains("recraft", ignoreCase = true) ||
                    id.contains("dalle", ignoreCase = true) ||
                    id.contains("sdxl", ignoreCase = true)

                Model(
                    modelId = id,
                    displayName = modelObj["name"]?.jsonPrimitive?.contentOrNull ?: id,
                    type = when {
                        isEmbedding -> me.rerere.ai.provider.ModelType.EMBEDDING
                        isImage -> me.rerere.ai.provider.ModelType.IMAGE
                        else -> me.rerere.ai.provider.ModelType.CHAT
                    },
                    outputModalities = listOf(me.rerere.ai.provider.Modality.TEXT),
                )
            }
        }

    override suspend fun getBalance(providerSetting: ProviderSetting.NanoGPT): String = 
        withContext(Dispatchers.IO) {
            val key = keyRoulette.next(providerSetting.apiKey)
            val url = if (providerSetting.balanceOption.apiPath.startsWith("http")) {
                providerSetting.balanceOption.apiPath
            } else {
                "${providerSetting.baseUrl}${providerSetting.balanceOption.apiPath}"
            }
            
            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", key)
                .addHeader("Content-Type", "application/json")
                .post("{}".toRequestBody("application/json".toMediaType()))
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
        providerSetting: ProviderSetting.NanoGPT,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> {
        // Convert NanoGPT settings to OpenAI settings for API compatibility
        val openAISettings = ProviderSetting.OpenAI(
            id = providerSetting.id,
            enabled = providerSetting.enabled,
            name = providerSetting.name,
            models = providerSetting.models,
            proxy = providerSetting.proxy,
            apiKey = providerSetting.apiKey,
            baseUrl = providerSetting.baseUrl,
            chatCompletionsPath = "/v1/chat/completions"
        )
        
        return chatCompletionsAPI.streamText(
            providerSetting = openAISettings,
            messages = messages,
            params = params
        )
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.NanoGPT,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        // Convert NanoGPT settings to OpenAI settings for API compatibility
        val openAISettings = ProviderSetting.OpenAI(
            id = providerSetting.id,
            enabled = providerSetting.enabled,
            name = providerSetting.name,
            models = providerSetting.models,
            proxy = providerSetting.proxy,
            apiKey = providerSetting.apiKey,
            baseUrl = providerSetting.baseUrl,
            chatCompletionsPath = "/v1/chat/completions"
        )
        
        return chatCompletionsAPI.generateText(
            providerSetting = openAISettings,
            messages = messages,
            params = params
        )
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        require(providerSetting is ProviderSetting.NanoGPT) {
            "Expected NanoGPT provider setting"
        }

        val key = keyRoulette.next(providerSetting.apiKey)

        val requestBody = json.encodeToString(
            buildJsonObject {
                put("model", params.model.modelId)
                put("prompt", params.prompt)
                put("n", params.numOfImages)
                put("response_format", "b64_json")
                put(
                    "size", when (params.aspectRatio) {
                        ImageAspectRatio.SQUARE -> "1024x1024"
                        ImageAspectRatio.LANDSCAPE -> "1536x1024"
                        ImageAspectRatio.PORTRAIT -> "1024x1536"
                    }
                )
            }.mergeCustomBody(params.customBody)
        )

        val request = Request.Builder()
            .url("${providerSetting.baseUrl}/v1/images/generations")
            .headers(params.customHeaders.toHeaders())
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
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
        providerSetting: ProviderSetting.NanoGPT,
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
            .url("${providerSetting.baseUrl}/v1/embeddings")
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.configureClientWithProxy(providerSetting.proxy).newCall(request).await()
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
