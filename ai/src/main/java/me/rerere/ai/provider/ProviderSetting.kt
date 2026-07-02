package me.rerere.ai.provider

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.uuid.Uuid

@Serializable
sealed class ProviderProxy {
    @Serializable
    @SerialName("none")
    object None : ProviderProxy()

    @Serializable
    @SerialName("http")
    data class Http(
        val address: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
    ) : ProviderProxy()
}

@Serializable
data class BalanceOption(
    val enabled: Boolean = false, // 是否开启余额获取功能
    val apiPath: String = "/credits", // 余额获取API路径
    val resultPath: String = "data.total_usage", // 余额获取JSON路径
)

@Serializable
sealed class ProviderSetting {
    abstract val id: Uuid
    abstract val enabled: Boolean
    abstract val name: String
    abstract val models: List<Model>
    abstract val proxy: ProviderProxy
    abstract val balanceOption: BalanceOption
    abstract val tags: List<Uuid>
    abstract val customIconUri: String?

    abstract val builtIn: Boolean

    abstract fun addModel(model: Model): ProviderSetting
    abstract fun editModel(model: Model): ProviderSetting
    abstract fun delModel(model: Model): ProviderSetting
    abstract fun moveModel(from: Int, to: Int): ProviderSetting
    abstract fun copyProvider(
        id: Uuid = this.id,
        enabled: Boolean = this.enabled,
        name: String = this.name,
        models: List<Model> = this.models,
        proxy: ProviderProxy = this.proxy,
        balanceOption: BalanceOption = this.balanceOption,
        tags: List<Uuid> = this.tags,
        customIconUri: String? = this.customIconUri,
        builtIn: Boolean = this.builtIn,
    ): ProviderSetting

    @Serializable
    @SerialName("openai")
    data class OpenAI(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "OpenAI",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        override var tags: List<Uuid> = emptyList(),
        override val customIconUri: String? = null,
        @Transient override val builtIn: Boolean = false,
        var apiKey: String = "",
        var baseUrl: String = "https://api.openai.com/v1",
        var chatCompletionsPath: String = "/chat/completions",
        var useResponseApi: Boolean = false,
        val reasoningBehavior: ReasoningRequestBehavior? = null,
        val streamOptionsMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
        val imageResponseModalitiesMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
        val reasoningContentReplayMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
        val promptCacheMode: OpenAICompatibilityMode = OpenAICompatibilityMode.AUTO,
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveModel(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            tags: List<Uuid>,
            customIconUri: String?,
            builtIn: Boolean,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customIconUri = customIconUri,
                builtIn = builtIn,
                proxy = proxy,
                balanceOption = balanceOption,
                tags = tags,
            )
        }
    }

    @Serializable
    @SerialName("google")
    data class Google(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "Google",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        override var tags: List<Uuid> = emptyList(),
        override val customIconUri: String? = null,
        @Transient override val builtIn: Boolean = false,
        var apiKey: String = "",
        var baseUrl: String = "https://generativelanguage.googleapis.com/v1beta", // only for google AI
        var vertexAI: Boolean = false,
        var privateKey: String = "", // only for vertex AI
        var serviceAccountEmail: String = "", // only for vertex AI
        var location: String = "us-central1", // only for vertex AI
        var projectId: String = "", // only for vertex AI
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveModel(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            tags: List<Uuid>,
            customIconUri: String?,
            builtIn: Boolean,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customIconUri = customIconUri,
                builtIn = builtIn,
                proxy = proxy,
                balanceOption = balanceOption,
                tags = tags
            )
        }
    }

    @Serializable
    @SerialName("claude")
    data class Claude(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "Claude",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        override var tags: List<Uuid> = emptyList(),
        override val customIconUri: String? = null,
        @Transient override val builtIn: Boolean = false,
        var apiKey: String = "",
        var baseUrl: String = "https://api.anthropic.com/v1",
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model)
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.copy() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveModel(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            tags: List<Uuid>,
            customIconUri: String?,
            builtIn: Boolean,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models,
                customIconUri = customIconUri,
                proxy = proxy,
                balanceOption = balanceOption,
                tags = tags,
                builtIn = builtIn,
            )
        }
    }
    companion object {
        val Types by lazy {
            listOf(
                OpenAI::class,
                Google::class,
                Claude::class,
            )
        }
    }

    @Serializable
    @SerialName("comfyui")
    data class ComfyUI(
        override var id: Uuid = Uuid.random(),
        override var enabled: Boolean = true,
        override var name: String = "ComfyUI",
        override var models: List<Model> = emptyList(),
        override var proxy: ProviderProxy = ProviderProxy.None,
        override val balanceOption: BalanceOption = BalanceOption(),
        override var tags: List<Uuid> = emptyList(),
        override val customIconUri: String? = null,
        @Transient override val builtIn: Boolean = false,
        var baseUrl: String = "http://127.0.0.1:8188",
        var workflowJson: String = "",
        var promptNodeId: String = "",
        var promptInputName: String = "text",
        var modelNodeId: String = "",
        var modelInputName: String = "ckpt_name",
    ) : ProviderSetting() {
        override fun addModel(model: Model): ProviderSetting {
            return copy(models = models + model.withComfyDefaults())
        }

        override fun editModel(model: Model): ProviderSetting {
            return copy(models = models.map { if (it.id == model.id) model.withComfyDefaults() else it })
        }

        override fun delModel(model: Model): ProviderSetting {
            return copy(models = models.filter { it.id != model.id })
        }

        override fun moveModel(
            from: Int,
            to: Int
        ): ProviderSetting {
            return copy(models = models.toMutableList().apply {
                val model = removeAt(from)
                add(to, model)
            })
        }

        override fun copyProvider(
            id: Uuid,
            enabled: Boolean,
            name: String,
            models: List<Model>,
            proxy: ProviderProxy,
            balanceOption: BalanceOption,
            tags: List<Uuid>,
            customIconUri: String?,
            builtIn: Boolean,
        ): ProviderSetting {
            return this.copy(
                id = id,
                enabled = enabled,
                name = name,
                models = models.map { it.withComfyDefaults() },
                proxy = proxy,
                balanceOption = balanceOption,
                tags = tags,
                customIconUri = customIconUri,
                builtIn = builtIn,
            )
        }
    }
}

private val MODEL_FILENAME_EXTENSIONS = setOf("safetensors", "ckpt", "pt", "pth", "bin")

fun Model.withComfyDefaults(): Model {
    val normalizedId = modelId.withDefaultSafetensorsExtension()
    return copy(
        modelId = normalizedId,
        displayName = displayName.ifBlank { normalizedId },
        type = ModelType.IMAGE,
        inputModalities = listOf(Modality.TEXT),
        outputModalities = listOf(Modality.IMAGE),
        imageGenerationMethod = ImageGenerationMethod.DIFFUSION,
    )
}

fun String.withDefaultSafetensorsExtension(): String {
    val trimmed = trim()
    if (trimmed.isBlank()) return trimmed
    val extension = trimmed.substringAfterLast('.', missingDelimiterValue = "")
    return if (extension.lowercase() in MODEL_FILENAME_EXTENSIONS) trimmed else "$trimmed.safetensors"
}
