package me.rerere.locallm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelInstallParseTest {
    @Test
    fun `parses resolve url`() {
        val spec = ModelInstall.parseImportUrl(
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm"
        )
        assertEquals("litert-community/Gemma3-1B-IT", spec?.hfRepo)
        assertEquals("main", spec?.commitHash)
        assertEquals("gemma3-1b-it-int4.litertlm", spec?.modelFile)
        assertEquals(
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
            spec?.downloadUrl,
        )
    }

    @Test
    fun `parses blob url into resolve url`() {
        val spec = ModelInstall.parseImportUrl(
            "https://huggingface.co/org/repo/blob/abc123/model.litertlm"
        )
        assertEquals("https://huggingface.co/org/repo/resolve/abc123/model.litertlm", spec?.downloadUrl)
    }

    @Test
    fun `rejects non-huggingface url`() {
        assertNull(ModelInstall.parseImportUrl("https://example.com/x/resolve/main/model.litertlm"))
    }

    @Test
    fun `rejects non-litertlm file`() {
        assertNull(ModelInstall.parseImportUrl("https://huggingface.co/org/repo/resolve/main/model.task"))
    }
}

class AcceleratorProbeTest {
    private fun model(
        accelerators: List<String> = listOf("gpu", "cpu"),
        accelerator: LocalAccelerator = LocalAccelerator.AUTO,
        gpuCrashed: Boolean = false,
        supportsImage: Boolean = false,
        visionAccelerator: String? = null,
    ) = InstalledLocalModel(
        id = "m",
        displayName = "m",
        filePath = "/tmp/m.litertlm",
        commitHash = "c",
        sizeInBytes = 1,
        supportsImage = supportsImage,
        defaultConfig = LocalModelDefaultConfig(accelerators = accelerators, visionAccelerator = visionAccelerator),
        config = LocalModelConfig(accelerator = accelerator),
        runtimeFlags = LocalModelRuntimeFlags(gpuCrashed = gpuCrashed),
    )

    @Test
    fun `auto prefers gpu when advertised and not crashed`() {
        assertTrue(AcceleratorProbe.resolve(model()).usingGpu)
    }

    @Test
    fun `auto falls back to cpu when gpu crashed`() {
        assertFalse(AcceleratorProbe.resolve(model(gpuCrashed = true)).usingGpu)
    }

    @Test
    fun `forced cpu ignores gpu preference`() {
        assertFalse(AcceleratorProbe.resolve(model(accelerator = LocalAccelerator.CPU)).usingGpu)
    }

    @Test
    fun `forced gpu blocked when crashed`() {
        assertFalse(AcceleratorProbe.resolve(model(accelerator = LocalAccelerator.GPU, gpuCrashed = true)).usingGpu)
    }

    @Test
    fun `vision backend only when image supported`() {
        assertNull(AcceleratorProbe.resolve(model(supportsImage = false)).vision)
        assertEquals(
            LocalAccelerator.GPU,
            AcceleratorProbe.resolve(model()).effective,
        )
    }
}
