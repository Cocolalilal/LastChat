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
    fun `shouldSilentlyPreloadImageForOcrFallback only when model lacks vision and OCR is configured`() {
        val ocrModel = Model(id = Uuid.random())
        val configuredSettings = Settings.dummy().copy(
            providers = listOf(ProviderSetting.OpenAI(models = listOf(ocrModel))),
            ocrModelId = ocrModel.id,
        )
        val noVisionModel = Model(inputModalities = listOf(Modality.TEXT))
        val visionModel = Model(inputModalities = listOf(Modality.TEXT, Modality.IMAGE))

        assertTrue(shouldSilentlyPreloadImageForOcrFallback(noVisionModel, configuredSettings))
        assertFalse(shouldSilentlyPreloadImageForOcrFallback(visionModel, configuredSettings))
        assertFalse(
            shouldSilentlyPreloadImageForOcrFallback(
                noVisionModel,
                configuredSettings.copy(ocrModelId = Uuid.random())
            )
        )
    }

    @Test
    fun `buildResidualImageFallbackText only mentions workspace tools when enabled`() {
        val workspaceEnabled = UnsupportedFileTransformer.buildResidualImageFallbackText(
            fileName = "photo.png",
            sourceUrl = "file:///tmp/photo.png",
            workspaceEnabled = true,
        )
        val workspaceDisabled = UnsupportedFileTransformer.buildResidualImageFallbackText(
            fileName = "photo.png",
            sourceUrl = "file:///tmp/photo.png",
            workspaceEnabled = false,
        )

        assertTrue(workspaceEnabled.contains("bound Linux workspace tools"))
        assertTrue(workspaceEnabled.contains("URL: file:///tmp/photo.png"))
        assertFalse(workspaceDisabled.contains("bound Linux workspace tools"))
        assertTrue(workspaceDisabled.contains("Do not infer image contents"))
    }

}
