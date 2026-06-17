package me.rerere.ai.provider

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SiliconFlowFreeApiPolicyTest {
    @Test
    fun usesFreeKeyForEmptyBearerAndFreeModel() {
        assertTrue(
            SiliconFlowFreeApiPolicy.shouldUseFreeApiKey(
                host = "api.siliconflow.cn",
                path = "/v1/chat/completions",
                authorizationHeader = "Bearer",
                model = "Qwen/Qwen3-Free",
                freeModels = listOf("Qwen/Qwen3-Free"),
            )
        )
    }

    @Test
    fun usesFreeKeyWhenModelIsMissingOnSupportedPath() {
        assertTrue(
            SiliconFlowFreeApiPolicy.shouldUseFreeApiKey(
                host = "api.siliconflow.cn",
                path = "/v1/models",
                authorizationHeader = " Bearer sk- ",
                model = null,
                freeModels = emptyList(),
            )
        )
    }

    @Test
    fun rejectsConfiguredAuthAndPaidModels() {
        assertFalse(
            SiliconFlowFreeApiPolicy.shouldUseFreeApiKey(
                host = "api.siliconflow.cn",
                path = "/v1/chat/completions",
                authorizationHeader = "Bearer real-key",
                model = "Qwen/Paid",
                freeModels = listOf("Qwen/Free"),
            )
        )
        assertFalse(
            SiliconFlowFreeApiPolicy.shouldUseFreeApiKey(
                host = "api.siliconflow.cn",
                path = "/v1/chat/completions",
                authorizationHeader = "Bearer",
                model = "Qwen/Paid",
                freeModels = listOf("Qwen/Free"),
            )
        )
    }

    @Test
    fun rejectsOtherHostsAndPaths() {
        assertFalse(
            SiliconFlowFreeApiPolicy.shouldUseFreeApiKey(
                host = "example.com",
                path = "/v1/chat/completions",
                authorizationHeader = "Bearer",
                model = null,
                freeModels = emptyList(),
            )
        )
        assertFalse(
            SiliconFlowFreeApiPolicy.shouldUseFreeApiKey(
                host = "api.siliconflow.cn",
                path = "/v1/embeddings",
                authorizationHeader = "Bearer",
                model = null,
                freeModels = emptyList(),
            )
        )
    }

    @Test
    fun buildsAuthorizationHeaderWhenOverrideApplies() {
        assertEquals(
            "Bearer fallback-key",
            SiliconFlowFreeApiPolicy.authorizationOverride(
                host = "api.siliconflow.cn",
                path = "/v1/models",
                authorizationHeader = "Bearer sk-",
                model = "",
                freeModels = emptyList(),
                apiKey = "fallback-key",
            )
        )
        assertNull(
            SiliconFlowFreeApiPolicy.authorizationOverride(
                host = "api.siliconflow.cn",
                path = "/v1/models",
                authorizationHeader = "Bearer configured",
                model = "",
                freeModels = emptyList(),
                apiKey = "fallback-key",
            )
        )
    }
}
