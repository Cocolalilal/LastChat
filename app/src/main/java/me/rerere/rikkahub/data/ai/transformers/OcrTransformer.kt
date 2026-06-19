package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import me.rerere.common.cache.LruCache
import me.rerere.common.platform.android.cache.SingleFileCacheStore
import me.rerere.rikkahub.data.ai.buildOcrGenerationParams
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.repository.ChatAttachmentRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import kotlin.time.Duration.Companion.days

private const val TAG = "OcrTransformer"

enum class OcrStatus {
    SUCCESS,
    CACHE_HIT,
    UNAVAILABLE,
    FAILED,
}

data class OcrExecutionResult(
    val promptText: String?,
    val status: OcrStatus,
)

internal fun OcrExecutionResult.consumesImageInput(): Boolean {
    if (promptText == null) return false
    return status == OcrStatus.SUCCESS || status == OcrStatus.CACHE_HIT
}

internal fun buildImageOcrPrompt(content: String): String {
    return """
        <image_file_ocr>
           $content
        </image_file_ocr>
        * The image_file_ocr tag contains the text description of an uploaded image. For models without direct image input, use this as the image content for this turn.
    """.trimIndent()
}

internal fun isOcrConfigured(settings: Settings): Boolean {
    val model = settings.findModelById(settings.ocrModelId) ?: return false
    return model.findProvider(settings.providers) != null
}

internal fun shouldSilentlyPreloadImageForOcrFallback(
    model: Model,
    settings: Settings,
): Boolean {
    return !model.inputModalities.contains(Modality.IMAGE) && isOcrConfigured(settings)
}

object OcrTransformer : InputMessageTransformer, KoinComponent {
    private val cache by lazy {
        val context = get<Context>()
        val json = Json { allowStructuredMapKeys = true }
        val store = SingleFileCacheStore(
            file = File(context.cacheDir, "ocr_cache.json"),
            keySerializer = String.serializer(),
            valueSerializer = String.serializer(),
            json = json
        )
        LruCache(
            capacity = 64,
            store = store,
            deleteOnEvict = true,
            preloadFromStore = true,
            expireAfterWriteMillis = 3.days.inWholeMilliseconds,
        )
    }

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (ctx.model.inputModalities.contains(Modality.IMAGE)) {
            // 如果模型支持图片输入，直接返回原始消息
            return messages
        }

        // 如果模型不支持图片输入，进行OCR转换
        return withContext(Dispatchers.IO) {
            messages.map { message ->
                message.copy(
                    parts = message.parts.map { part ->
                        when {
                            part is UIMessagePart.Image && part.url.startsWith("file:") -> {
                                val fileName = resolveFileName(part)
                                val ocrText = get<ChatAttachmentRepository>().resolveAttachmentOcrText(
                                    part = part,
                                    ensureAvailable = true,
                                    onBeforeProviderCall = {
                                        ctx.upsertProgressAnnotation(
                                            annotation = UIMessageAnnotation.OcrActivity(
                                                source = UIMessageAnnotation.OcrActivity.Source.IMAGE,
                                                fileName = fileName,
                                            ),
                                            matches = { annotation ->
                                                annotation is UIMessageAnnotation.OcrActivity &&
                                                    annotation.source == UIMessageAnnotation.OcrActivity.Source.IMAGE &&
                                                    annotation.fileName == fileName
                                            }
                                        )
                                    },
                                )
                                val ocrResult = OcrExecutionResult(
                                    promptText = ocrText,
                                    status = if (ocrText.isNullOrBlank()) OcrStatus.FAILED else OcrStatus.CACHE_HIT,
                                )
                                if (ocrResult.consumesImageInput()) {
                                    ctx.recordGenerationAnnotation(
                                        UIMessageAnnotation.OcrActivity(
                                            source = UIMessageAnnotation.OcrActivity.Source.IMAGE,
                                            fileName = fileName,
                                        )
                                    )
                                    UIMessagePart.Text(ocrResult.promptText!!)
                                } else {
                                    part
                                }
                            }

                            else -> part
                        }
                    }
                )
            }
        }
    }

    suspend fun performOcr(part: UIMessagePart.Image): String {
        return performOcrWithMetadata(part).promptText.orEmpty()
    }

    suspend fun performOcrWithMetadata(
        part: UIMessagePart.Image,
        onBeforeProviderCall: (suspend () -> Unit)? = null,
    ): OcrExecutionResult {
        try {
            cache.get(part.url)?.let { cachedResult ->
                Log.i(TAG, "performOcr: Using cached result for ${part.url}")
                return OcrExecutionResult(
                    promptText = cachedResult,
                    status = OcrStatus.CACHE_HIT,
                )
            }

            val settings = get<SettingsStore>().settingsFlow.value
            val model = settings.findModelById(settings.ocrModelId) ?: return OcrExecutionResult(
                promptText = null,
                status = OcrStatus.UNAVAILABLE,
            )
            val providerSetting = model.findProvider(settings.providers) ?: return OcrExecutionResult(
                promptText = null,
                status = OcrStatus.UNAVAILABLE,
            )
            val provider = get<ProviderManager>().getProviderByType(providerSetting)
            onBeforeProviderCall?.invoke()
            val result = provider.generateText(
                providerSetting = providerSetting,
                messages = listOf(
                    UIMessage.system(settings.ocrPrompt),
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Image(part.url))
                    )
                ),
                params = settings.buildOcrGenerationParams(model),
            )
            val content = result.choices[0].message?.toText()?.trim().orEmpty()
            if (content.isBlank()) {
                Log.w(TAG, "performOcr: OCR returned blank content for ${part.url}")
                return OcrExecutionResult(
                    promptText = null,
                    status = OcrStatus.FAILED,
                )
            }
            Log.i(TAG, "performOcr: $content")
            val ocrResult = buildImageOcrPrompt(content)

            cache.put(part.url, ocrResult)
            return OcrExecutionResult(
                promptText = ocrResult,
                status = OcrStatus.SUCCESS,
            )
        } catch (throwable: Throwable) {
            Log.w(TAG, "performOcr: OCR failed for ${part.url}", throwable)
            return OcrExecutionResult(
                promptText = null,
                status = OcrStatus.FAILED,
            )
        }
    }

    private fun resolveFileName(part: UIMessagePart.Image): String? {
        return runCatching {
            Uri.parse(part.url).lastPathSegment
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
