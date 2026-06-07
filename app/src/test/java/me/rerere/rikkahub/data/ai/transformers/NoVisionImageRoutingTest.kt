package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class NoVisionImageRoutingTest {
    @Test
    fun `consumesImageInput treats success and cache hit as handled`() {
        assertTrue(
            OcrExecutionResult(
                promptText = "<image_file_ocr>desc</image_file_ocr>",
                status = OcrStatus.SUCCESS,
            ).consumesImageInput()
        )
        assertTrue(
            OcrExecutionResult(
                promptText = "<image_file_ocr>desc</image_file_ocr>",
                status = OcrStatus.CACHE_HIT,
            ).consumesImageInput()
        )
        assertFalse(
            OcrExecutionResult(
                promptText = null,
                status = OcrStatus.UNAVAILABLE,
            ).consumesImageInput()
        )
        assertFalse(
            OcrExecutionResult(
                promptText = null,
                status = OcrStatus.FAILED,
            ).consumesImageInput()
        )
    }

    @Test
    fun `shouldSilentlyPreloadImageForPython only when model lacks vision and OCR is configured`() {
        val ocrModel = Model(id = Uuid.random())
        val configuredSettings = Settings.dummy().copy(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(ocrModel))),
            ocrModelId = ocrModel.id,
        )
        val noVisionModel = Model(inputModalities = listOf(Modality.TEXT))
        val visionModel = Model(inputModalities = listOf(Modality.TEXT, Modality.IMAGE))

        assertTrue(shouldSilentlyPreloadImageForPython(noVisionModel, configuredSettings))
        assertFalse(shouldSilentlyPreloadImageForPython(visionModel, configuredSettings))
        assertFalse(
            shouldSilentlyPreloadImageForPython(
                noVisionModel,
                configuredSettings.copy(ocrModelId = Uuid.random())
            )
        )
    }

    @Test
    fun `buildResidualImageFallbackText only mentions python when enabled`() {
        val pythonEnabled = UnsupportedFileTransformer.buildResidualImageFallbackText(
            fileName = "photo.png",
            sourceUrl = "file:///tmp/photo.png",
            pythonEnabled = true,
        )
        val pythonDisabled = UnsupportedFileTransformer.buildResidualImageFallbackText(
            fileName = "photo.png",
            sourceUrl = "file:///tmp/photo.png",
            pythonEnabled = false,
        )

        assertTrue(pythonEnabled.contains("Python can use the original image"))
        assertTrue(pythonEnabled.contains("list_sandbox_files"))
        assertFalse(pythonDisabled.contains("Python can use the original image"))
        assertTrue(pythonDisabled.contains("Do not infer image contents"))
    }

    @Test
    fun `buildResidualImageFallbackText prefers linux when enabled`() {
        val linuxEnabled = UnsupportedFileTransformer.buildResidualImageFallbackText(
            fileName = "photo.png",
            sourceUrl = "file:///tmp/photo.png",
            pythonEnabled = true,
            linuxEnabled = true,
        )

        assertTrue(linuxEnabled.contains("Linux can use the original image"))
        assertTrue(linuxEnabled.contains("preloaded workspace files"))
        assertFalse(linuxEnabled.contains("Python can use the original image"))
    }
}
