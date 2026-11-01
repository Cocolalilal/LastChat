package me.rerere.rikkahub.ui.components.ai

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ModelListBackendFilterTest {

    @Test
    fun backendChatModelIsHiddenWhenAllowBackendModelsIsFalse() {
        val backendModel = Model(
            id = Uuid.random(),
            modelId = "test-backend-chat",
            displayName = "Backend Chat Model",
            type = ModelType.CHAT,
            backend = true,
        )

        val selectable = isModelSelectable(
            model = backendModel,
            targetType = ModelType.CHAT,
            allowBackendModels = false,
        )

        assertFalse("Backend chat model must be hidden from chat picker", selectable)
    }

    @Test
    fun normalChatModelIsVisibleWhenAllowBackendModelsIsFalse() {
        val normalModel = Model(
            id = Uuid.random(),
            modelId = "test-chat",
            displayName = "Regular Chat Model",
            type = ModelType.CHAT,
            backend = false,
        )

        val selectable = isModelSelectable(
            model = normalModel,
            targetType = ModelType.CHAT,
            allowBackendModels = false,
        )

        assertTrue("Regular chat model must be visible in chat picker", selectable)
    }

    @Test
    fun backendChatModelIsVisibleWhenAllowBackendModelsIsTrue() {
        val backendModel = Model(
            id = Uuid.random(),
            modelId = "test-backend-chat",
            displayName = "Backend Chat Model",
            type = ModelType.CHAT,
            backend = true,
        )

        val selectable = isModelSelectable(
            model = backendModel,
            targetType = ModelType.CHAT,
            allowBackendModels = true,
        )

        assertTrue("Backend chat model must be selectable when allowed", selectable)
    }

    @Test
    fun nonChatModelsAreNotHiddenByBackendFlag() {
        val types = listOf(ModelType.EMBEDDING, ModelType.IMAGE, ModelType.STT)
        for (type in types) {
            val model = Model(
                id = Uuid.random(),
                modelId = "test-$type",
                type = type,
                backend = true,
            )

            val selectable = isModelSelectable(
                model = model,
                targetType = type,
                allowBackendModels = false,
            )

            assertTrue("Non-chat model of type $type should not be hidden by backend flag", selectable)
        }
    }

    @Test
    fun customModelFilterIsRespectedTogetherWithBackendRule() {
        val model1 = Model(
            id = Uuid.random(),
            modelId = "allowed",
            type = ModelType.CHAT,
            backend = false,
        )
        val model2 = Model(
            id = Uuid.random(),
            modelId = "disallowed-by-filter",
            type = ModelType.CHAT,
            backend = false,
        )

        val filter: (Model) -> Boolean = { it.modelId == "allowed" }

        assertTrue(isModelSelectable(model1, ModelType.CHAT, allowBackendModels = false, modelFilter = filter))
        assertFalse(isModelSelectable(model2, ModelType.CHAT, allowBackendModels = false, modelFilter = filter))
    }

    @Test
    fun providerWithOnlyBackendChatModelsHasNoVisibleModelsInChatPicker() {
        val backendOnlyProvider = ProviderSetting.OpenAI(
            name = "BackendOnlyProvider",
            enabled = true,
            models = listOf(
                Model(
                    id = Uuid.random(),
                    modelId = "backend-1",
                    type = ModelType.CHAT,
                    backend = true,
                ),
                Model(
                    id = Uuid.random(),
                    modelId = "backend-2",
                    type = ModelType.CHAT,
                    backend = true,
                ),
            ),
        )

        val visibleModels = backendOnlyProvider.models.filter {
            it.type == ModelType.CHAT && isModelSelectable(it, ModelType.CHAT, allowBackendModels = false)
        }

        assertTrue("Provider with only backend models should have no visible chat models", visibleModels.isEmpty())
    }
}
