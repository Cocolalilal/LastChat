package me.rerere.locallm

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalModelContextTest {
    @Test
    fun effectiveContext_usesKnownModelDefault() {
        val model = installedModel(
            defaultConfig = LocalModelDefaultConfig(maxContextLength = 16_384, maxTokens = 2_048),
        )

        assertEquals(16_384, model.effectiveRuntimeContextLength(totalRamGb = 12))
    }

    @Test
    fun effectiveContext_respectsUserModelAndDeviceCeilings() {
        val model = installedModel(
            defaultConfig = LocalModelDefaultConfig(maxContextLength = 32_768, maxTokens = 4_096),
            config = LocalModelConfig(contextLength = 24_000),
        )

        assertEquals(8_192, model.effectiveRuntimeContextLength(totalRamGb = 8))
        assertEquals(24_000, model.effectiveRuntimeContextLength(totalRamGb = 16))
    }

    private fun installedModel(
        defaultConfig: LocalModelDefaultConfig,
        config: LocalModelConfig = LocalModelConfig(),
    ) = InstalledLocalModel(
        id = "local-test",
        displayName = "Local Test",
        filePath = "local-test.litertlm",
        commitHash = "test",
        sizeInBytes = 1,
        defaultConfig = defaultConfig,
        config = config,
    )
}
