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
    fun `parses raw url into resolve url`() {
        val spec = ModelInstall.parseImportUrl(
            "https://huggingface.co/org/repo/raw/main/model.litertlm"
        )
        assertEquals("https://huggingface.co/org/repo/resolve/main/model.litertlm", spec?.downloadUrl)
        assertEquals("model", spec?.name)
    }

    @Test
    fun `parses short hf-dot-co domain`() {
        val spec = ModelInstall.parseImportUrl(
            "https://hf.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm"
        )
        assertEquals("litert-community/Gemma3-1B-IT", spec?.hfRepo)
        assertEquals(
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.litertlm",
            spec?.downloadUrl,
        )
    }

    @Test
    fun `parses url without scheme and with www`() {
        val spec = ModelInstall.parseImportUrl(
            "www.huggingface.co/org/repo/resolve/main/model.litertlm"
        )
        assertEquals("https://huggingface.co/org/repo/resolve/main/model.litertlm", spec?.downloadUrl)
    }

    @Test
    fun `parses http url upgrading to https`() {
        val spec = ModelInstall.parseImportUrl(
            "http://hf.co/org/repo/blob/main/model.litertlm"
        )
        assertEquals("https://huggingface.co/org/repo/resolve/main/model.litertlm", spec?.downloadUrl)
    }

    @Test
    fun `parses url with query parameters and fragments`() {
        val spec = ModelInstall.parseImportUrl(
            "https://huggingface.co/org/repo/resolve/main/model.litertlm?download=true#description"
        )
        assertEquals("https://huggingface.co/org/repo/resolve/main/model.litertlm", spec?.downloadUrl)
        assertEquals("model.litertlm", spec?.modelFile)
    }

    @Test
    fun `parses url wrapped in angle brackets and whitespace`() {
        val spec = ModelInstall.parseImportUrl(
            "  <https://huggingface.co/org/repo/resolve/main/model.litertlm>  "
        )
        assertEquals("https://huggingface.co/org/repo/resolve/main/model.litertlm", spec?.downloadUrl)
    }

    @Test
    fun `parses nested directory path within repo`() {
        val spec = ModelInstall.parseImportUrl(
            "https://huggingface.co/org/repo/resolve/main/sub/dir/model.litertlm"
        )
        assertEquals("org/repo", spec?.hfRepo)
        assertEquals("main", spec?.commitHash)
        assertEquals("model.litertlm", spec?.modelFile)
        assertEquals("model", spec?.name)
        assertEquals("org/repo/sub/dir/model.litertlm", spec?.id)
        assertEquals(
            "https://huggingface.co/org/repo/resolve/main/sub/dir/model.litertlm",
            spec?.downloadUrl,
        )
    }

    @Test
    fun `parses shorthand repo and file path`() {
        val specWithResolve = ModelInstall.parseImportUrl("org/repo/resolve/v1.0/model.litertlm")
        assertEquals("org/repo", specWithResolve?.hfRepo)
        assertEquals("v1.0", specWithResolve?.commitHash)
        assertEquals("https://huggingface.co/org/repo/resolve/v1.0/model.litertlm", specWithResolve?.downloadUrl)

        val specDirect = ModelInstall.parseImportUrl("org/repo/model.litertlm")
        assertEquals("org/repo", specDirect?.hfRepo)
        assertEquals("main", specDirect?.commitHash)
        assertEquals("https://huggingface.co/org/repo/resolve/main/model.litertlm", specDirect?.downloadUrl)
    }

    @Test
    fun `parses case-insensitive litertlm extension`() {
        val spec = ModelInstall.parseImportUrl(
            "https://huggingface.co/org/repo/resolve/main/model.LITERTLM"
        )
        assertEquals("model", spec?.name)
        assertEquals("model.LITERTLM", spec?.modelFile)
    }

    @Test
    fun `handles url-encoded spaces in file name`() {
        val spec = ModelInstall.parseImportUrl(
            "https://huggingface.co/org/repo/resolve/main/my%20model.litertlm"
        )
        assertEquals("my model", spec?.name)
        assertEquals("my model.litertlm", spec?.modelFile)
        assertEquals("https://huggingface.co/org/repo/resolve/main/my%20model.litertlm", spec?.downloadUrl)
    }

    @Test
    fun `rejects non-huggingface url`() {
        assertNull(ModelInstall.parseImportUrl("https://example.com/x/resolve/main/model.litertlm"))
        assertNull(ModelInstall.parseImportUrl("example.com/x/resolve/main/model.litertlm"))
    }

    @Test
    fun `rejects non-litertlm files including gguf`() {
        assertNull(ModelInstall.parseImportUrl("https://huggingface.co/org/repo/resolve/main/model.task"))
        assertNull(ModelInstall.parseImportUrl("https://huggingface.co/org/repo/resolve/main/model.gguf"))
        assertNull(ModelInstall.parseImportUrl("https://huggingface.co/org/repo/blob/main/model.safetensors"))
        assertNull(ModelInstall.parseImportUrl("https://huggingface.co/org/repo/resolve/main/README.md"))
    }

    @Test
    fun `rejects incomplete or empty urls`() {
        assertNull(ModelInstall.parseImportUrl(""))
        assertNull(ModelInstall.parseImportUrl("   "))
        assertNull(ModelInstall.parseImportUrl("https://huggingface.co/"))
        assertNull(ModelInstall.parseImportUrl("https://huggingface.co/org/repo"))
        assertNull(ModelInstall.parseImportUrl("https://huggingface.co/org/repo/resolve/main"))
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
    fun `accelerator resolves independently of vision support`() {
        assertEquals(
            LocalAccelerator.GPU,
            AcceleratorProbe.resolve(model(supportsImage = false)).effective,
        )
        assertEquals(
            LocalAccelerator.GPU,
            AcceleratorProbe.resolve(model()).effective,
        )
    }
}

class InstalledLocalModelMetadataTest {
    @Test
    fun `catalog metadata restores embedding kind for older installed records`() {
        val installed = InstalledLocalModel(
            id = "EmbeddingGemma-300M",
            displayName = "EmbeddingGemma 300M",
            filePath = "/tmp/embedding.tflite",
            commitHash = "old",
            sizeInBytes = 1,
        )
        val meta = LocalModelMetadata(
            id = "EmbeddingGemma-300M",
            name = "EmbeddingGemma 300M",
            kind = LocalModelKind.EMBEDDING,
            hfRepo = "litert-community/embeddinggemma-300m",
            modelFile = "embedding.tflite",
            tokenizerFile = "sentencepiece.model",
            commitHash = "new",
            sizeInBytes = 1,
            minDeviceMemoryInGb = 4,
            embeddingDimension = 768,
        )

        val reconciled = installed.withCatalogMetadata(meta, "/tmp/sentencepiece.model")

        assertTrue(reconciled.isEmbedding)
        assertEquals("/tmp/sentencepiece.model", reconciled.tokenizerPath)
        assertEquals(768, reconciled.embeddingDimension)
        assertEquals("old", reconciled.commitHash)
    }

    @Test
    fun `catalog metadata preserves user config and runtime flags`() {
        val installed = InstalledLocalModel(
            id = "Gemma3-1B-IT",
            displayName = "My Gemma",
            filePath = "/tmp/model.litertlm",
            commitHash = "old",
            sizeInBytes = 1,
            config = LocalModelConfig(temperature = 0.2f, accelerator = LocalAccelerator.CPU),
            runtimeFlags = LocalModelRuntimeFlags(gpuCrashed = true),
            customIconUri = "content://icon",
        )
        val meta = LocalModelMetadata(
            id = "Gemma3-1B-IT",
            name = "Gemma 3 1B",
            hfRepo = "litert-community/Gemma3-1B-IT",
            modelFile = "model.litertlm",
            commitHash = "new",
            sizeInBytes = 1,
            minDeviceMemoryInGb = 6,
            supportsThinking = true,
        )

        val reconciled = installed.withCatalogMetadata(meta)

        assertEquals("My Gemma", reconciled.displayName)
        assertEquals(installed.config, reconciled.config)
        assertEquals(installed.runtimeFlags, reconciled.runtimeFlags)
        assertEquals("content://icon", reconciled.customIconUri)
        assertTrue(reconciled.supportsThinking)
    }
}

class MemoryGuardPolicyTest {
    @Test
    fun `context ceiling grows conservatively with device memory`() {
        assertEquals(4_096, MemoryGuard.safeContextTokenCap(4))
        assertEquals(4_096, MemoryGuard.safeContextTokenCap(6))
        assertEquals(8_192, MemoryGuard.safeContextTokenCap(8))
        assertEquals(16_384, MemoryGuard.safeContextTokenCap(12))
        assertEquals(32_768, MemoryGuard.safeContextTokenCap(16))
    }

    @Test
    fun `allowlist memory shortfall is advisory like Edge Gallery`() {
        val result = MemoryGuard.evaluate(
            totalRamGb = 8,
            modelSizeBytes = 3_659_530_240L,
            minDeviceMemoryGb = 12,
        )

        assertTrue(result is MemoryCheck.Advisory)
    }

    @Test
    fun `compatible device is allowed without an available memory estimate`() {
        val result = MemoryGuard.evaluate(
            totalRamGb = 8,
            modelSizeBytes = 2_588_147_712L,
            minDeviceMemoryGb = 8,
        )

        assertEquals(MemoryCheck.Ok, result)
    }
}
