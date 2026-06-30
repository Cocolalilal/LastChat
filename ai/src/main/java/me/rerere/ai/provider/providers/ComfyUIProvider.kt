package me.rerere.ai.provider.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderProxy
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.withDefaultSafetensorsExtension
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.ai.ui.ImageGenerationItem
import me.rerere.ai.ui.ImageGenerationResult
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.common.http.urlEncode
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpProxy
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalEncodingApi::class)
class ComfyUIProvider(
    private val httpClient: PlatformHttpClient
) : Provider<ProviderSetting.ComfyUI> {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override suspend fun listModels(providerSetting: ProviderSetting.ComfyUI): List<Model> {
        return providerSetting.models
    }

    override suspend fun generateText(
        providerSetting: ProviderSetting.ComfyUI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): MessageChunk {
        throw UnsupportedOperationException("ComfyUI only supports image generation")
    }

    override suspend fun streamText(
        providerSetting: ProviderSetting.ComfyUI,
        messages: List<UIMessage>,
        params: TextGenerationParams
    ): Flow<MessageChunk> {
        throw UnsupportedOperationException("ComfyUI only supports image generation")
    }

    override suspend fun generateImage(
        providerSetting: ProviderSetting,
        params: ImageGenerationParams
    ): ImageGenerationResult = withContext(Dispatchers.IO) {
        require(providerSetting is ProviderSetting.ComfyUI) {
            "Expected ComfyUI provider setting"
        }

        val workflow = parseWorkflow(providerSetting.workflowJson)
        val requestedCount = params.numOfImages.coerceIn(1, 4)
        val items = mutableListOf<ImageGenerationItem>()

        repeat(requestedCount) {
            val promptId = queuePrompt(
                providerSetting = providerSetting,
                workflow = workflow.prepareForGeneration(providerSetting, params),
                customHeaders = params.customHeaders,
            )
            items += waitForImages(
                providerSetting = providerSetting,
                promptId = promptId,
                customHeaders = params.customHeaders,
            )
        }

        if (items.isEmpty()) {
            error("ComfyUI completed without returning any images")
        }

        ImageGenerationResult(items = items.take(requestedCount))
    }

    private fun parseWorkflow(workflowJson: String): JsonObject {
        val workflow = workflowJson.trim()
        if (workflow.isBlank()) {
            error("Import a ComfyUI API workflow JSON before generating images")
        }
        val parsed = runCatching { json.parseToJsonElement(workflow) }.getOrElse {
            error("Invalid ComfyUI workflow JSON: ${it.message}")
        }
        return parsed as? JsonObject ?: error("ComfyUI workflow must be a JSON object")
    }

    private fun JsonObject.prepareForGeneration(
        providerSetting: ProviderSetting.ComfyUI,
        params: ImageGenerationParams,
    ): JsonObject {
        var updated = this
        val promptNodeId = providerSetting.promptNodeId.ifBlank {
            findTextPromptNodeId() ?: error("Set a prompt node id for this ComfyUI workflow")
        }
        val modelNodeId = providerSetting.modelNodeId.ifBlank {
            findModelNodeId() ?: error("Set a model node id for this ComfyUI workflow")
        }

        updated = updated.setNodeInput(
            nodeId = promptNodeId,
            inputName = providerSetting.promptInputName.ifBlank { "text" },
            value = JsonPrimitive(params.prompt),
        )
        updated = updated.setNodeInput(
            nodeId = modelNodeId,
            inputName = providerSetting.modelInputName.ifBlank { "ckpt_name" },
            value = JsonPrimitive(params.model.modelId.withDefaultSafetensorsExtension()),
        )
        updated = updated.applyAspectRatio(params.aspectRatio)
        updated = params.customBody.fold(updated) { current, body ->
            current.applyCustomBody(body)
        }

        return updated
    }

    private suspend fun queuePrompt(
        providerSetting: ProviderSetting.ComfyUI,
        workflow: JsonObject,
        customHeaders: List<CustomHeader>,
    ): String {
        val body = json.encodeToString(
            JsonObject(
                mapOf(
                    "client_id" to JsonPrimitive(Uuid.random().toString()),
                    "prompt" to workflow,
                )
            )
        )
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "POST",
                url = providerSetting.endpoint("prompt"),
                headers = customHeaders.toHeaderMap() + ("Content-Type" to "application/json"),
                body = body.encodeToByteArray(),
                mediaType = "application/json",
                proxy = providerSetting.proxy.toPlatformProxy()
            )
        )
        val responseBody = response.body.decodeToString()
        if (response.statusCode !in 200..299) {
            error("ComfyUI prompt failed: ${response.statusCode} $responseBody")
        }
        val responseJson = json.parseToJsonElement(responseBody) as? JsonObject
            ?: error("Invalid ComfyUI prompt response")
        return responseJson["prompt_id"]?.jsonPrimitive?.contentOrNull
            ?: error("ComfyUI response did not include prompt_id")
    }

    private suspend fun waitForImages(
        providerSetting: ProviderSetting.ComfyUI,
        promptId: String,
        customHeaders: List<CustomHeader>,
    ): List<ImageGenerationItem> {
        repeat(HISTORY_POLL_ATTEMPTS) {
            val images = fetchHistoryImages(providerSetting, promptId, customHeaders)
            if (images.isNotEmpty()) {
                return images
            }
            delay(HISTORY_POLL_DELAY_MS)
        }
        error("Timed out waiting for ComfyUI image output")
    }

    private suspend fun fetchHistoryImages(
        providerSetting: ProviderSetting.ComfyUI,
        promptId: String,
        customHeaders: List<CustomHeader>,
    ): List<ImageGenerationItem> {
        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = providerSetting.endpoint("history/$promptId"),
                headers = customHeaders.toHeaderMap(),
                proxy = providerSetting.proxy.toPlatformProxy()
            )
        )
        val responseBody = response.body.decodeToString()
        if (response.statusCode !in 200..299) {
            error("ComfyUI history failed: ${response.statusCode} $responseBody")
        }
        val history = json.parseToJsonElement(responseBody) as? JsonObject ?: return emptyList()
        val promptHistory = history[promptId] as? JsonObject ?: return emptyList()
        val outputs = promptHistory["outputs"] as? JsonObject ?: return emptyList()
        val imageRefs = outputs.values.flatMap { output ->
            val outputObject = output as? JsonObject ?: return@flatMap emptyList()
            val images = outputObject["images"] as? JsonArray ?: return@flatMap emptyList()
            images.mapNotNull { it as? JsonObject }
        }

        return imageRefs.mapNotNull { imageObject ->
            fetchImage(providerSetting, imageObject, customHeaders)
        }
    }

    private suspend fun fetchImage(
        providerSetting: ProviderSetting.ComfyUI,
        imageObject: JsonObject,
        customHeaders: List<CustomHeader>,
    ): ImageGenerationItem? {
        val filename = imageObject["filename"]?.jsonPrimitive?.contentOrNull ?: return null
        val subfolder = imageObject["subfolder"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val type = imageObject["type"]?.jsonPrimitive?.contentOrNull ?: "output"
        val url = providerSetting.endpoint("view").withQueryParameters(
            "filename" to filename,
            "subfolder" to subfolder,
            "type" to type
        )

        val response = httpClient.execute(
            PlatformHttpRequest(
                method = "GET",
                url = url,
                headers = customHeaders.toHeaderMap(),
                proxy = providerSetting.proxy.toPlatformProxy()
            )
        )
        if (response.statusCode !in 200..299) {
            error("ComfyUI image download failed: ${response.statusCode} ${response.body.decodeToString()}")
        }
        val mime = response.header("Content-Type")?.substringBefore(";") ?: "image/png"
        return ImageGenerationItem(
            data = Base64.Default.encode(response.body),
            mimeType = mime,
        )
    }

    private fun ProviderSetting.ComfyUI.endpoint(path: String): String {
        return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
    }

    private fun List<CustomHeader>.toHeaderMap(): Map<String, String> {
        return associate { it.name to it.value }
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

    private fun String.withQueryParameters(vararg params: Pair<String, String>): String {
        return buildString {
            append(this@withQueryParameters)
            params.forEachIndexed { index, (name, value) ->
                append(if (index == 0) '?' else '&')
                append(name.urlEncode())
                append('=')
                append(value.urlEncode())
            }
        }
    }

    private fun PlatformHttpResponse.header(name: String): String? {
        return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.firstOrNull()
    }

    private fun JsonObject.findModelNodeId(): String? {
        return findNodeIdWithInput(
            inputNames = listOf("ckpt_name", "model_name"),
            preferredClassNames = listOf("CheckpointLoader", "CheckpointLoaderSimple", "UNETLoader"),
        )
    }

    private fun JsonObject.findTextPromptNodeId(): String? {
        return findNodeIdWithInput(
            inputNames = listOf("text", "prompt", "positive"),
            preferredClassNames = listOf("CLIPTextEncode", "TextEncode"),
            avoidTitleTokens = listOf("negative"),
        )
    }

    private fun JsonObject.findNodeIdWithInput(
        inputNames: List<String>,
        preferredClassNames: List<String>,
        avoidTitleTokens: List<String> = emptyList(),
    ): String? {
        val candidates = entries.mapNotNull { (nodeId, element) ->
            val node = element as? JsonObject ?: return@mapNotNull null
            val inputs = node["inputs"] as? JsonObject ?: return@mapNotNull null
            if (inputNames.none { inputs.containsKey(it) }) return@mapNotNull null
            nodeId to node
        }.filterNot { (_, node) ->
            val title = node.nodeTitle().lowercase()
            avoidTitleTokens.any { title.contains(it) }
        }

        return candidates.firstOrNull { (_, node) ->
            val classType = node["class_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            preferredClassNames.any { classType.contains(it, ignoreCase = true) }
        }?.first ?: candidates.firstOrNull()?.first
    }

    private fun JsonObject.nodeTitle(): String {
        val meta = this["_meta"] as? JsonObject
        return meta?.get("title")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun JsonObject.setNodeInput(
        nodeId: String,
        inputName: String,
        value: JsonElement,
    ): JsonObject {
        val node = this[nodeId] as? JsonObject ?: error("ComfyUI workflow node '$nodeId' was not found")
        val inputs = node["inputs"] as? JsonObject ?: error("ComfyUI workflow node '$nodeId' has no inputs")
        val updatedInputs = JsonObject(inputs.toMutableMap().apply { put(inputName, value) })
        val updatedNode = JsonObject(node.toMutableMap().apply { put("inputs", updatedInputs) })
        return JsonObject(toMutableMap().apply { put(nodeId, updatedNode) })
    }

    private fun JsonObject.applyAspectRatio(aspectRatio: ImageAspectRatio): JsonObject {
        val (width, height) = when (aspectRatio) {
            ImageAspectRatio.SQUARE -> 1024 to 1024
            ImageAspectRatio.LANDSCAPE -> 1216 to 832
            ImageAspectRatio.PORTRAIT -> 832 to 1216
        }
        val targetNodeId = entries.firstOrNull { (_, element) ->
            val inputs = (element as? JsonObject)?.get("inputs") as? JsonObject
            inputs?.containsKey("width") == true && inputs.containsKey("height")
        }?.key ?: return this

        return setNodeInput(targetNodeId, "width", JsonPrimitive(width))
            .setNodeInput(targetNodeId, "height", JsonPrimitive(height))
    }

    private fun JsonObject.applyCustomBody(body: CustomBody): JsonObject {
        val key = body.key.trim()
        if (key.isBlank()) return this
        val path = key.split('.').filter { it.isNotBlank() }
        if (path.size >= 3 && path[1] == "inputs") {
            return setNodeInput(path[0], path.drop(2).joinToString("."), body.value)
        }
        if (path.size == 2) {
            return setNodeInput(path[0], path[1], body.value)
        }
        if (path.size == 1) {
            return setAllInputsNamed(path[0], body.value)
        }
        return this
    }

    private fun JsonObject.setAllInputsNamed(inputName: String, value: JsonElement): JsonObject {
        var updated = this
        entries.forEach { (nodeId, element) ->
            val inputs = (element as? JsonObject)?.get("inputs") as? JsonObject
            if (inputs?.containsKey(inputName) == true) {
                updated = updated.setNodeInput(nodeId, inputName, coerceLikeExisting(inputs[inputName], value))
            }
        }
        return updated
    }

    private fun coerceLikeExisting(existing: JsonElement?, replacement: JsonElement): JsonElement {
        if (existing !is JsonPrimitive || replacement !is JsonPrimitive) return replacement
        if (!existing.isString) {
            replacement.intOrNull?.let { return JsonPrimitive(it) }
            replacement.contentOrNull?.toFloatOrNull()?.let { return JsonPrimitive(it) }
        }
        return replacement
    }

    private companion object {
        const val HISTORY_POLL_ATTEMPTS = 240
        const val HISTORY_POLL_DELAY_MS = 500L
    }
}
