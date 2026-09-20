package me.rerere.ai.provider.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.rerere.ai.provider.ImageGenerationInput
import me.rerere.ai.provider.ImageGenerationParams
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.ui.ImageAspectRatio
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class ComfyUIImageToImageTest {
    @Test
    fun uploadsInputImageAndInjectsLoadImageNode() = runBlocking {
        val png = Base64.Default.encode(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        val client = RecordingComfyClient()
        val provider = ComfyUIProvider(client)
        val workflow = """
            {
              "1": {"class_type":"CheckpointLoaderSimple","inputs":{"ckpt_name":"model.safetensors"}},
              "2": {"class_type":"CLIPTextEncode","inputs":{"text":"old"}},
              "3": {"class_type":"LoadImage","inputs":{"image":"placeholder.png"}},
              "4": {"class_type":"EmptyLatentImage","inputs":{"width":512,"height":512}}
            }
        """.trimIndent()
        val result = provider.generateImage(
            providerSetting = ProviderSetting.ComfyUI(
                workflowJson = workflow,
                promptNodeId = "2",
                modelNodeId = "1",
            ),
            params = ImageGenerationParams(
                model = Model(modelId = "dream.safetensors", displayName = "Dream"),
                prompt = "a castle",
                aspectRatio = ImageAspectRatio.SQUARE,
                inputImages = listOf(
                    ImageGenerationInput(data = png, mimeType = "image/png", fileName = "ref.png"),
                ),
            ),
        )
        assertEquals(1, result.items.size)
        assertTrue(client.urls.any { it.contains("/upload/image") })
        val promptBody = client.bodies.first { it.contains("\"prompt\"") }
        assertTrue(promptBody.contains("in.png") || promptBody.contains("ref.png"))
        assertTrue(promptBody.contains("a castle"))
    }
}

private class RecordingComfyClient : PlatformHttpClient {
    val urls = mutableListOf<String>()
    val bodies = mutableListOf<String>()

    override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
        urls += request.url
        bodies += request.body?.decodeToString().orEmpty()
        val body = when {
            request.url.contains("upload/image") -> """{"name":"in.png","subfolder":"","type":"input"}"""
            request.url.endsWith("/prompt") -> """{"prompt_id":"p1"}"""
            request.url.contains("/history/") -> """
                {"p1":{"outputs":{"9":{"images":[{"filename":"out.png","subfolder":"","type":"output"}]}}}}
            """.trimIndent()
            request.url.contains("/view") -> "PNG"
            else -> "{}"
        }
        return PlatformHttpResponse(statusCode = 200, body = body.encodeToByteArray())
    }

    override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
}
