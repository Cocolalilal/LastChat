package me.rerere.rikkahub.data.ai.models

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun testParseCatalog() {
        val file = File("../catalog/lastchat_catalog.json")
        assertTrue("Catalog file should exist at ${file.absolutePath}", file.exists())
        val rawJson = file.readText()
        val snapshot = ModelCatalogParser.parse(rawJson)
        assertNotNull(snapshot)
        assertTrue("Should have parsed providers", snapshot.providers.isNotEmpty())
        assertTrue("Should have parsed model families", snapshot.modelFamilies.isNotEmpty())
        assertTrue("Should have parsed model overrides", snapshot.modelOverrides.isNotEmpty())
        assertTrue("Should have parsed global rules", snapshot.globalRules.isNotEmpty())
    }

    @Test
    fun testSetupModelsResolveThroughFamilyFirstCatalog() {
        val file = File("../catalog/lastchat_catalog.json")
        assertTrue("Catalog file should exist at ${file.absolutePath}", file.exists())
        val rawJson = file.readText()
        val snapshot = ModelCatalogParser.parse(rawJson)

        val missingRefs = snapshot.providers.flatMap { provider ->
            val setupRefs = provider.setupModels + listOfNotNull(
                provider.setupDefaults?.chat,
                provider.setupDefaults?.title,
                provider.setupDefaults?.summarizer,
                provider.setupDefaults?.ocr,
            )
            setupRefs
                .distinct()
                .filter { modelId -> snapshot.resolveModelEntry(modelId) == null }
                .map { modelId -> "${provider.name}: $modelId" }
        }

        assertTrue("Setup model refs should resolve: $missingRefs", missingRefs.isEmpty())
    }

    @Test
    fun testModelCapabilities() {
        val file = File("../catalog/lastchat_catalog.json")
        assertTrue("Catalog file should exist at ${file.absolutePath}", file.exists())
        val rawJson = file.readText()
        val snapshot = ModelCatalogParser.parse(rawJson)
        assertNotNull(snapshot)

        // Helper to infer or find entry
        fun getEntry(modelId: String): ModelCatalogEntry {
            return snapshot.exactEntries[modelId.lowercase()]
                ?: snapshot.inferFamilyEntry(modelId)
                ?: error("Model not found in catalog: $modelId")
        }

        // Gemini 2.0 Flash-Lite: TOOL enabled, REASONING disabled
        val geminiLite = getEntry("gemini-2.0-flash-lite")
        assertTrue("gemini-2.0-flash-lite should support function calling", geminiLite.supportsFunctionCalling)
        assertTrue("gemini-2.0-flash-lite should NOT support reasoning", !geminiLite.supportsReasoning)

        // Llama 4: TOOL and REASONING enabled, supports vision
        val llama4 = getEntry("meta-llama/Llama-4-70B-Instruct")
        assertTrue("Llama 4 should support function calling", llama4.supportsFunctionCalling)
        assertTrue("Llama 4 should support reasoning", llama4.supportsReasoning)
        assertTrue("Llama 4 should support vision", llama4.supportsVision)

        // Qwen QwQ: TOOL and REASONING, NO vision
        val qwq = getEntry("Qwen/QwQ-32B-Preview")
        assertTrue("QwQ should support function calling", qwq.supportsFunctionCalling)
        assertTrue("QwQ should support reasoning", qwq.supportsReasoning)
        assertTrue("QwQ should NOT support vision", !qwq.supportsVision)

        // Qwen QvQ: TOOL, REASONING, and Vision
        val qvq = getEntry("Qwen/QvQ-72B-Preview")
        assertTrue("QvQ should support function calling", qvq.supportsFunctionCalling)
        assertTrue("QvQ should support reasoning", qvq.supportsReasoning)
        assertTrue("QvQ should support vision", qvq.supportsVision)

        // Microsoft Phi-4: TOOL and REASONING
        val phi4 = getEntry("microsoft/phi-4")
        assertTrue("Phi-4 should support function calling", phi4.supportsFunctionCalling)
        assertTrue("Phi-4 should support reasoning", phi4.supportsReasoning)
        assertTrue("Phi-4 should NOT support vision", !phi4.supportsVision)

        // Microsoft Phi-4-vision: TOOL, REASONING, and Vision
        val phi4Vision = getEntry("microsoft/phi-4-multimodal-instruct")
        assertTrue("Phi-4-vision should support function calling", phi4Vision.supportsFunctionCalling)
        assertTrue("Phi-4-vision should support reasoning", phi4Vision.supportsReasoning)
        assertTrue("Phi-4-vision should support vision", phi4Vision.supportsVision)

        // Cohere Command-R: TOOL enabled
        val cohereCommand = getEntry("command-r-plus")
        assertTrue("Cohere Command-R+ should support function calling", cohereCommand.supportsFunctionCalling)

        // Perplexity Sonar Reasoning: REASONING enabled
        val perplexitySonar = getEntry("sonar-reasoning")
        assertTrue("Perplexity Sonar Reasoning should support reasoning", perplexitySonar.supportsReasoning)

        // MiniMax models
        val minimaxText = getEntry("MiniMax-Text-01")
        assertTrue("MiniMax-Text-01 should support function calling", minimaxText.supportsFunctionCalling)
        assertTrue("MiniMax-Text-01 should support reasoning", minimaxText.supportsReasoning)
        assertTrue("MiniMax-Text-01 should NOT support vision", !minimaxText.supportsVision)

        val minimaxVl = getEntry("MiniMax-VL-01")
        assertTrue("MiniMax-VL-01 should support function calling", minimaxVl.supportsFunctionCalling)
        assertTrue("MiniMax-VL-01 should NOT support reasoning", !minimaxVl.supportsReasoning)
        assertTrue("MiniMax-VL-01 should support vision", minimaxVl.supportsVision)

        // Moonshot AI / Kimi
        val kimiK2 = getEntry("kimi-k2")
        assertTrue("Kimi K2 should support function calling", kimiK2.supportsFunctionCalling)
        assertTrue("Kimi K2 should support reasoning", kimiK2.supportsReasoning)

        val kimiK25 = getEntry("kimi-k2.5")
        assertTrue("Kimi K2.5 should support function calling", kimiK25.supportsFunctionCalling)
        assertTrue("Kimi K2.5 should support reasoning", kimiK25.supportsReasoning)
        assertTrue("Kimi K2.5 should support vision", kimiK25.supportsVision)

        val kimiK26 = getEntry("kimi-k2.6")
        assertTrue("Kimi K2.6 should support function calling", kimiK26.supportsFunctionCalling)
        assertTrue("Kimi K2.6 should support reasoning", kimiK26.supportsReasoning)
        assertTrue("Kimi K2.6 should support vision", kimiK26.supportsVision)

        val moonshotV1 = getEntry("moonshot-v1-128k")
        assertTrue("Moonshot V1 should support function calling", moonshotV1.supportsFunctionCalling)
        assertTrue("Moonshot V1 should NOT support reasoning", !moonshotV1.supportsReasoning)

        // Tencent Hunyuan
        val hunyuanPro = getEntry("hunyuan-pro")
        assertTrue("Hunyuan Pro should support function calling", hunyuanPro.supportsFunctionCalling)

        val hunyuanT1 = getEntry("hunyuan-t1")
        assertTrue("Hunyuan T1 should support function calling", hunyuanT1.supportsFunctionCalling)
        assertTrue("Hunyuan T1 should support reasoning", hunyuanT1.supportsReasoning)
        assertTrue("Hunyuan T1 should NOT support vision", !hunyuanT1.supportsVision)

        val hunyuanVision = getEntry("hunyuan-vision")
        assertTrue("Hunyuan Vision should support function calling", hunyuanVision.supportsFunctionCalling)
        assertTrue("Hunyuan Vision should support vision", hunyuanVision.supportsVision)

        // GLM (Zhipu AI)
        val glmPlus = getEntry("glm-4-plus")
        assertTrue("GLM 4 Plus should support function calling", glmPlus.supportsFunctionCalling)
        assertTrue("GLM 4 Plus should NOT support reasoning", !glmPlus.supportsReasoning)

        val glmVision = getEntry("glm-4v-plus")
        assertTrue("GLM 4V Plus should support function calling", glmVision.supportsFunctionCalling)
        assertTrue("GLM 4V Plus should support vision", glmVision.supportsVision)
        assertTrue("GLM 4V Plus should NOT support reasoning", !glmVision.supportsReasoning)

        val glm45 = getEntry("glm-4.5")
        assertTrue("GLM 4.5 should support function calling", glm45.supportsFunctionCalling)
        assertTrue("GLM 4.5 should support reasoning", glm45.supportsReasoning)
        assertTrue("GLM 4.5 should NOT support vision", !glm45.supportsVision)

        val glm5 = getEntry("glm-5")
        assertTrue("GLM 5 should support function calling", glm5.supportsFunctionCalling)
        assertTrue("GLM 5 should support reasoning", glm5.supportsReasoning)
        assertTrue("GLM 5 should NOT support vision", !glm5.supportsVision)

        val glm5vTurbo = getEntry("glm-5v-turbo")
        assertTrue("GLM 5V Turbo should support function calling", glm5vTurbo.supportsFunctionCalling)
        assertTrue("GLM 5V Turbo should support reasoning", glm5vTurbo.supportsReasoning)
        assertTrue("GLM 5V Turbo should support vision", glm5vTurbo.supportsVision)

        val glmZ1 = getEntry("glm-z1-rumination-32b")
        assertTrue("GLM Z1 Rumination should support function calling", glmZ1.supportsFunctionCalling)
        assertTrue("GLM Z1 Rumination should support reasoning", glmZ1.supportsReasoning)
        assertTrue("GLM Z1 Rumination should NOT support vision", !glmZ1.supportsVision)

        val glmZero = getEntry("glm-zero-preview")
        assertTrue("GLM Zero should support function calling", glmZero.supportsFunctionCalling)
        assertTrue("GLM Zero should support reasoning", glmZero.supportsReasoning)

        val glm47 = getEntry("glm-4.7")
        assertTrue("GLM 4.7 should support reasoning", glm47.supportsReasoning)

        val glm47v = getEntry("glm-4.7-vision")
        assertTrue("GLM 4.7 Vision should support reasoning", glm47v.supportsReasoning)
        assertTrue("GLM 4.7 Vision should support vision", glm47v.supportsVision)

        val minimaxNvidia = getEntry("minimaxai/minimax-m2.7")
        assertTrue("minimaxai/minimax-m2.7 should resolve to MiniMax icon", minimaxNvidia.iconUrl == "icons/minimax.svg".toCatalogIconUrl())

        // Qwen
        val qwenVl = getEntry("Qwen/Qwen2.5-VL-7B-Instruct")
        assertTrue("Qwen 2.5 VL should support function calling", qwenVl.supportsFunctionCalling)
        assertTrue("Qwen 2.5 VL should support vision", qwenVl.supportsVision)
        assertTrue("Qwen 2.5 VL should NOT support reasoning", !qwenVl.supportsReasoning)

        val qwenPlus = getEntry("Qwen/Qwen2.5-Plus")
        assertTrue("Qwen 2.5 Plus should support function calling", qwenPlus.supportsFunctionCalling)
        assertTrue("Qwen 2.5 Plus should NOT support reasoning", !qwenPlus.supportsReasoning)
        assertTrue("Qwen 2.5 Plus should NOT support vision", !qwenPlus.supportsVision)

        val qwenMax = getEntry("Qwen/Qwen2.5-Max")
        assertTrue("Qwen 2.5 Max should support function calling", qwenMax.supportsFunctionCalling)
        assertTrue("Qwen 2.5 Max should NOT support reasoning", !qwenMax.supportsReasoning)
        assertTrue("Qwen 2.5 Max should NOT support vision", !qwenMax.supportsVision)

        val qwenCoder = getEntry("Qwen/Qwen2.5-Coder-7B-Instruct")
        assertTrue("Qwen 2.5 Coder should support function calling", qwenCoder.supportsFunctionCalling)
        assertTrue("Qwen 2.5 Coder should NOT support reasoning", !qwenCoder.supportsReasoning)
        assertTrue("Qwen 2.5 Coder should NOT support vision", !qwenCoder.supportsVision)

        val qwen3Vl = getEntry("Qwen/Qwen3-VL-235B-A22B-Thinking")
        assertTrue("Qwen 3 VL Thinking should support function calling", qwen3Vl.supportsFunctionCalling)
        assertTrue("Qwen 3 VL Thinking should support reasoning", qwen3Vl.supportsReasoning)
        assertTrue("Qwen 3 VL Thinking should support vision", qwen3Vl.supportsVision)

        // Baidu ERNIE
        val ernie4 = getEntry("ernie-4.0")
        assertTrue("ERNIE 4.0 should support function calling", ernie4.supportsFunctionCalling)
        assertTrue("ERNIE 4.0 should support reasoning", ernie4.supportsReasoning)

        val ernie45vl = getEntry("ernie-4.5-vl-28b-a3b")
        assertTrue("ERNIE 4.5 VL should support function calling", ernie45vl.supportsFunctionCalling)
        assertTrue("ERNIE 4.5 VL should support reasoning", ernie45vl.supportsReasoning)
        assertTrue("ERNIE 4.5 VL should support vision", ernie45vl.supportsVision)

        // iFlyTek Spark
        val sparkUltra = getEntry("4.0Ultra")
        assertTrue("Spark 4.0 Ultra should support function calling", sparkUltra.supportsFunctionCalling)
        assertTrue("Spark 4.0 Ultra should support reasoning", sparkUltra.supportsReasoning)

        // NVIDIA NIM provider: reasoningBehavior should be parsed correctly
        val nvidiaProvider = snapshot.providers.find { it.id == "d7f8a9e0-cb41-45bd-89d2-e6fdbe4c2d3a" }
        assertNotNull("NVIDIA NIM provider should exist", nvidiaProvider)
        val rb = nvidiaProvider?.reasoningBehavior
        assertNotNull("NVIDIA NIM should have reasoning behavior defined", rb)
        assertTrue("NVIDIA NIM reasoning behavior off should have chat_template_kwargs", rb!!.off.any { it.key == "chat_template_kwargs" })
        val offTemplate = rb.off.single { it.key == "chat_template_kwargs" }.value.jsonObject
        assertEquals("false", offTemplate["enable_thinking"]?.jsonPrimitive?.contentOrNull)
        assertEquals("false", offTemplate["thinking"]?.jsonPrimitive?.contentOrNull)
        val autoTemplate = rb.auto.single { it.key == "chat_template_kwargs" }.value.jsonObject
        assertEquals("true", autoTemplate["enable_thinking"]?.jsonPrimitive?.contentOrNull)
        assertEquals("true", autoTemplate["thinking"]?.jsonPrimitive?.contentOrNull)
        assertTrue("NVIDIA NIM reasoning behavior low should have reasoning_budget", rb.low.any { it.key == "reasoning_budget" })

        // Voyage
        val voyage3 = getEntry("voyage-3")
        assertTrue("voyage-3 should NOT support function calling", !voyage3.supportsFunctionCalling)
        assertTrue("voyage-3 should NOT support reasoning", !voyage3.supportsReasoning)

        // Jina
        val jinaV3 = getEntry("jina-embeddings-v3")
        assertTrue("jina-embeddings-v3 should NOT support function calling", !jinaV3.supportsFunctionCalling)
        assertTrue("jina-embeddings-v3 should NOT support reasoning", !jinaV3.supportsReasoning)

        // Baichuan
        val baichuan4 = getEntry("Baichuan4")
        assertTrue("Baichuan4 should support function calling", baichuan4.supportsFunctionCalling)

        // Solar
        val solarPro = getEntry("solar-pro")
        assertTrue("solar-pro should support function calling", solarPro.supportsFunctionCalling)

        // Yi
        val yiLightning = getEntry("yi-lightning")
        assertTrue("yi-lightning should support function calling", yiLightning.supportsFunctionCalling)
        assertTrue("yi-lightning should support reasoning", yiLightning.supportsReasoning)
        assertTrue("yi-lightning should NOT support vision", !yiLightning.supportsVision)

        val yiLarge = getEntry("yi-large")
        assertTrue("yi-large should support function calling", yiLarge.supportsFunctionCalling)
        assertTrue("yi-large should NOT support reasoning", !yiLarge.supportsReasoning)
        assertTrue("yi-large should NOT support vision", !yiLarge.supportsVision)

        val yiVision = getEntry("yi-vision")
        assertTrue("yi-vision should support function calling", yiVision.supportsFunctionCalling)
        assertTrue("yi-vision should NOT support reasoning", !yiVision.supportsReasoning)
        assertTrue("yi-vision should support vision", yiVision.supportsVision)

        // SenseNova
        val sensechat65 = getEntry("SenseChat-V6.5")
        assertTrue("SenseChat-V6.5 should support function calling", sensechat65.supportsFunctionCalling)
        assertTrue("SenseChat-V6.5 should support reasoning", sensechat65.supportsReasoning)
        assertTrue("SenseChat-V6.5 should support vision", sensechat65.supportsVision)

        val sensechat6 = getEntry("SenseChat-V6")
        assertTrue("SenseChat-V6 should support function calling", sensechat6.supportsFunctionCalling)
        assertTrue("SenseChat-V6 should support reasoning", sensechat6.supportsReasoning)
        assertTrue("SenseChat-V6 should support vision", sensechat6.supportsVision)

        val sensechat5 = getEntry("SenseChat-5")
        assertTrue("SenseChat-5 should support function calling", sensechat5.supportsFunctionCalling)
        assertTrue("SenseChat-5 should NOT support reasoning", !sensechat5.supportsReasoning)
        assertTrue("SenseChat-5 should NOT support vision", !sensechat5.supportsVision)

        // StepFun
        val step35 = getEntry("step-3.5-flash")
        assertTrue("step-3.5-flash should support function calling", step35.supportsFunctionCalling)
        assertTrue("step-3.5-flash should support reasoning", step35.supportsReasoning)

        val step15v = getEntry("step-1.5v-mini")
        assertTrue("step-1.5v-mini should support function calling", step15v.supportsFunctionCalling)
        assertTrue("step-1.5v-mini should support vision", step15v.supportsVision)

        // AI 360 (360 Zhinao)
        val ai360O1 = getEntry("360gpt2-o1")
        assertTrue("360gpt2-o1 should support function calling", ai360O1.supportsFunctionCalling)
        assertTrue("360gpt2-o1 should support reasoning", ai360O1.supportsReasoning)

        val ai360Pro = getEntry("360gpt-pro")
        assertTrue("360gpt-pro should support function calling", ai360Pro.supportsFunctionCalling)
        assertTrue("360gpt-pro should NOT support reasoning", !ai360Pro.supportsReasoning)

        // Verify NVIDIA models
        val nvidiaEmbed = getEntry("nvidia/embed-qa-4")
        assertTrue("nvidia/embed-qa-4 should be an embedding model", nvidiaEmbed.mode == "embedding")
        assertTrue("nvidia/embed-qa-4 should not support reasoning", !nvidiaEmbed.supportsReasoning)

        val nvidiaTranslate = getEntry("nvidia/riva-translate-4b-instruct")
        assertTrue("NVIDIA translate should support function calling", nvidiaTranslate.supportsFunctionCalling)

        // Verify MiniMax reasoning models
        val minimaxM25 = getEntry("minimax/minimax-m2.5")
        assertTrue("MiniMax M2.5 should support function calling", minimaxM25.supportsFunctionCalling)
        assertTrue("MiniMax M2.5 should support reasoning", minimaxM25.supportsReasoning)

        val minimaxM27 = getEntry("minimax/minimax-m2.7")
        assertTrue("MiniMax M2.7 should support function calling", minimaxM27.supportsFunctionCalling)
        assertTrue("MiniMax M2.7 should support reasoning", minimaxM27.supportsReasoning)

        // Verify Google Deep Research models
        val deepResearch = getEntry("google/gemini-deep-research")
        assertTrue("Deep Research should support function calling", deepResearch.supportsFunctionCalling)
        assertTrue("Deep Research should support reasoning", deepResearch.supportsReasoning)
        assertTrue("Deep Research should support vision", deepResearch.supportsVision)
        assertTrue("Deep Research should map to Google icon", deepResearch.iconUrl == "icons/google.svg".toCatalogIconUrl())

        // Verify Nano Banana model mapping
        val banana1 = getEntry("models/nano-banana")
        assertTrue("models/nano-banana should resolve to gemini-2.5-flash-image", banana1.canonicalModelId == "gemini-2.5-flash-image")

        val banana2 = getEntry("google/nano-banana-2")
        assertTrue("google/nano-banana-2 should resolve to gemini-3.1-flash-image", banana2.canonicalModelId == "gemini-3.1-flash-image")

        val bananaPro = getEntry("nano-banana-pro")
        assertTrue("nano-banana-pro should resolve to gemini-3-pro-image", bananaPro.canonicalModelId == "gemini-3-pro-image")

        // Verify AionLabs models
        val aion1 = getEntry("aion-labs/aion-1.0")
        assertTrue("Aion 1.0 should support reasoning", aion1.supportsReasoning)
        assertTrue("Aion 1.0 should support function calling", aion1.supportsFunctionCalling)
        assertTrue("Aion 1.0 should map to AionLabs icon", aion1.iconUrl == "icons/aionlabs.svg".toCatalogIconUrl())

        val aionRp = getEntry("aion-labs/aion-rp-1.0-8b")
        assertTrue("Aion RP should support function calling", aionRp.supportsFunctionCalling)
        assertTrue("Aion RP should NOT support reasoning", !aionRp.supportsReasoning)

        // Verify ByteDance Seed models
        val seed16 = getEntry("bytedance-seed/seed-1.6")
        assertTrue("Seed 1.6 should support reasoning", seed16.supportsReasoning)
        assertTrue("Seed 1.6 should map to ByteDance icon", seed16.iconUrl == "icons/bytedance.svg".toCatalogIconUrl())

        // Verify OpenRouter Routers
        val routerAuto = getEntry("openrouter/auto")
        assertTrue("Auto router should support function calling", routerAuto.supportsFunctionCalling)
        assertTrue("Auto router should support reasoning", routerAuto.supportsReasoning)
        assertTrue("Auto router should map to OpenRouter icon", routerAuto.iconUrl == "icons/openrouter.svg".toCatalogIconUrl())

        // Verify Tencent Hunyuan
        val hunyuanLarge = getEntry("hunyuan-large")
        assertTrue("Hunyuan Large should support function calling", hunyuanLarge.supportsFunctionCalling)

        val hunyuanTurbo = getEntry("hunyuan-turbos")
        assertTrue("Hunyuan TurboS should support function calling", hunyuanTurbo.supportsFunctionCalling)

        // Verify newly added/recent providers
        val expectedProviderIds = setOf(
            "2269a913-9b16-43b8-89c0-fd2b4a5d3c8c", // GitHub Models
            "5a8c27de-c5e0-4434-8efa-932d0613dd9c", // Baichuan AI
            "c1f92a0e-c1d4-45aa-9b24-78fdbe4a3196", // Upstage AI
            "9fb2c4d8-c71b-457f-b88e-df41f23da4c1", // Scaleway
            "4f741ca1-57d6-4444-93ff-18305c43d9b8", // 01.AI
            "a8fd729a-1fb4-45b9-a9c0-fd11c34a2e5e", // SenseTime
            "b42deb05-d6e3-40a7-8f3b-1b9416fcec9f", // Gitee AI
            "c5f8a9e0-cb41-45bd-89d2-e6fdbe4c2d3b", // StepFun
            "e360f2a9-d98c-4f7f-ba0a-25be1c2a129d"  // 360 Zhinao
        )
        val actualProviderIds = snapshot.providers.map { it.id }.toSet()
        expectedProviderIds.forEach { id ->
            assertTrue("Provider $id should exist in catalog", actualProviderIds.contains(id))
        }
    }
}
