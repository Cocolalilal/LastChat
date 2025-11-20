package me.rerere.ai.mnn

import android.text.TextUtils
import me.rerere.ai.mnn.model.ChatDataItem

class ChatService {
    private val transformerSessionMap: MutableMap<String, ChatSession> = HashMap()

    /**
     * Unified method to create a session for any model type
     * @param modelId The model ID
     * @param modelName The model name (used for type detection)
     * @param sessionIdParam Optional session ID, will generate new one if null/empty
     * @param configPath Configuration file path for LLM models, or diffusion directory for diffusion models
     * @param useNewConfig If true, ignore existing config and use provided configPath. If false, may reuse existing session config
     */
    @Synchronized
    fun createSession(
        modelId: String,
        modelName: String,
        sessionIdParam: String?,
        historyList: List<ChatDataItem>?,
        configPath: String?,
        useNewConfig: Boolean = false
    ): ChatSession {
        val sessionId = if (TextUtils.isEmpty(sessionIdParam)) {
            System.currentTimeMillis().toString()
        } else {
            sessionIdParam!!
        }

        val session = LlmSession(modelId, sessionId, configPath!!, historyList)
        session.supportOmni = modelName.lowercase().contains("omni")

        // Store in appropriate map
        transformerSessionMap[sessionId] = session

        return session
    }

    @Synchronized
    fun createLlmSession(
        modelId: String?,
        modelDir: String?,
        sessionIdParam: String?,
        chatDataItemList: List<ChatDataItem>?,
        supportOmni: Boolean
    ): LlmSession {
        var sessionId: String = if (TextUtils.isEmpty(sessionIdParam)) {
            System.currentTimeMillis().toString()
        } else {
            sessionIdParam!!
        }
        val session = LlmSession(modelId!!, sessionId, modelDir!!, chatDataItemList)
        session.supportOmni = supportOmni
        transformerSessionMap[sessionId] = session
        return session
    }

    @Synchronized
    fun getSession(sessionId: String): ChatSession? {
        return transformerSessionMap[sessionId]
    }

    @Synchronized
    fun removeSession(sessionId: String) {
        transformerSessionMap.remove(sessionId)
    }

    companion object {
        private var instance: ChatService? = null

        @JvmStatic
        @Synchronized
        fun provide(): ChatService {
            if (instance == null) {
                instance = ChatService()
            }
            return instance!!
        }
    }
}
