package me.rerere.ai.provider

import me.rerere.ai.provider.providers.ClaudeProvider
import me.rerere.ai.provider.providers.ComfyUIProvider
import me.rerere.ai.provider.providers.GoogleProvider
import me.rerere.ai.provider.providers.OpenAIProvider
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformJwtSigner
import me.rerere.common.platform.PlatformMediaEncoder

/**
 * Provider管理器，负责注册和获取Provider实例
 */
class ProviderManager(
    platformHttpClient: PlatformHttpClient,
    platformMediaEncoder: PlatformMediaEncoder,
    platformJwtSigner: PlatformJwtSigner,
) {
    // 存储已注册的Provider实例
    private val providers = mutableMapOf<String, Provider<*>>()

    init {
        // 注册默认Provider
        registerProvider("openai", OpenAIProvider(platformHttpClient, platformMediaEncoder))
        registerProvider("google", GoogleProvider(platformHttpClient, platformMediaEncoder, platformJwtSigner))
        registerProvider("claude", ClaudeProvider(platformHttpClient, platformMediaEncoder))
        registerProvider("comfyui", ComfyUIProvider(platformHttpClient))
    }

    /**
     * 注册Provider实例
     *
     * @param name Provider名称
     * @param provider Provider实例
     */
    fun registerProvider(name: String, provider: Provider<*>) {
        providers[name] = provider
    }

    /**
     * 获取Provider实例
     *
     * @param name Provider名称
     * @return Provider实例，如果不存在则返回null
     */
    fun getProvider(name: String): Provider<*> {
        return providers[name] ?: throw IllegalArgumentException("Provider not found: $name")
    }

    /**
     * 根据ProviderSetting获取对应的Provider实例
     *
     * @param setting Provider设置
     * @return Provider实例，如果不存在则返回null
     */
    fun <T : ProviderSetting> getProviderByType(setting: T): Provider<T> {
        @Suppress("UNCHECKED_CAST")
        return when (setting) {
            is ProviderSetting.OpenAI -> getProvider("openai")
            is ProviderSetting.Google -> getProvider("google")
            is ProviderSetting.Claude -> getProvider("claude")
            is ProviderSetting.ComfyUI -> getProvider("comfyui")
        } as Provider<T>
    }
}
