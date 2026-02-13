package me.rerere.rikkahub.data.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.core.merge
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.registry.ModelRegistry
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.ai.ui.limitContext
import me.rerere.ai.ui.truncate
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.ai.transformers.InputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.MessageTransformer
import me.rerere.rikkahub.data.ai.transformers.OutputMessageTransformer
import me.rerere.rikkahub.data.ai.transformers.onGenerationFinish
import me.rerere.rikkahub.data.ai.transformers.transforms
import me.rerere.rikkahub.data.ai.transformers.visualTransforms
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookActivationType
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.data.model.Mode
import me.rerere.rikkahub.data.model.ModeAttachmentType
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.applyPlaceholders
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "GenerationHandler"

/**
 * Result of building messages, includes both the messages and info about activated context sources.
 */
data class BuildMessagesResult(
    val messages: List<UIMessage>,
    val activatedLorebookEntries: List<me.rerere.ai.ui.UsedLorebookEntry>,
    val usedModes: List<me.rerere.ai.ui.UsedMode> = emptyList(),
    val usedMemories: List<me.rerere.ai.ui.UsedMemory> = emptyList()
)

@Serializable
sealed interface GenerationChunk {
    data class Messages(
        val messages: List<UIMessage>
    ) : GenerationChunk
}

class GenerationHandler(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val json: Json,
    private val memoryRepo: MemoryRepository,
    private val conversationRepo: ConversationRepository,
    private val aiLoggingManager: AILoggingManager,
    private val embeddingService: me.rerere.rikkahub.data.ai.rag.EmbeddingService,
) {
    fun generateText(
        settings: Settings,
        model: Model,
        messages: List<UIMessage>,
        inputTransformers: List<InputMessageTransformer> = emptyList(),
        outputTransformers: List<OutputMessageTransformer> = emptyList(),
        assistant: Assistant,
        memories: List<AssistantMemory>? = null,
        tools: List<Tool> = emptyList(),
        truncateIndex: Int = -1,
        maxSteps: Int = 256,
        enabledModeIds: Set<Uuid> = emptySet(),
    ): Flow<GenerationChunk> = flow {
        val provider = model.findProvider(settings.providers) ?: error("Provider not found")
        val providerImpl = providerManager.getProviderByType(provider)

        var messages: List<UIMessage> = messages

        for (stepIndex in 0 until maxSteps) {
            Log.i(TAG, "streamText: start step #$stepIndex (${model.id})")

            val toolsInternal = buildList {
                Log.i(TAG, "generateInternal: build tools($assistant)")
                // Add memory tools if memory is enabled for this assistant
                if (assistant.enableMemory) {
                    buildMemoryTools(
                        onCreation = { content ->
                            memoryRepo.addMemory(assistant.id.toString(), content)
                        },
                        onUpdate = { id, content ->
                            memoryRepo.updateContent(id, content)
                        },
                        onDelete = { id ->
                            memoryRepo.deleteMemory(id)
                        }
                    ).let(this::addAll)
                }
                addAll(tools)
            }

            generateInternal(
                assistant = assistant,
                settings = settings,
                messages = messages,
                onUpdateMessages = {
                    messages = it.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant
                    )
                    emit(
                        GenerationChunk.Messages(
                            messages.visualTransforms(
                                transformers = outputTransformers,
                                context = context,
                                model = model,
                                assistant = assistant
                            )
                        )
                    )
                },
                transformers = inputTransformers,
                model = model,
                providerImpl = providerImpl,
                provider = provider,
                tools = toolsInternal,
                memories = memories ?: emptyList(),
                truncateIndex = truncateIndex,
                stream = assistant.streamOutput,
                enabledModeIds = enabledModeIds
            )
            messages = messages.visualTransforms(
                transformers = outputTransformers,
                context = context,
                model = model,
                assistant = assistant
            )
            messages = messages.onGenerationFinish(
                transformers = outputTransformers,
                context = context,
                model = model,
                assistant = assistant
            )
            emit(GenerationChunk.Messages(messages))

            val toolCalls = messages.last().getToolCalls()
            if (toolCalls.isEmpty()) {
                // no tool calls, break
                break
            }
            // handle tool calls
            val results = arrayListOf<UIMessagePart.ToolResult>()
            toolCalls.forEach { toolCall ->
                runCatching {
                    val tool = toolsInternal.find { tool -> tool.name == toolCall.toolName }
                        ?: error("Tool ${toolCall.toolName} not found")
                    val args = runCatching {
                        json.parseToJsonElement(toolCall.arguments.ifBlank { "{}" })
                    }.getOrElse {
                        // Handle malformed JSON from model (e.g., multiple objects concatenated)
                        Log.w(TAG, "Failed to parse tool arguments, attempting sanitization: ${it.message}")
                        val sanitized = sanitizeToolCallArguments(toolCall.arguments)
                        json.parseToJsonElement(sanitized)
                    }
                    Log.i(TAG, "generateText: executing tool ${tool.name} with args: $args")
                    val result = tool.execute(args)
                    results += UIMessagePart.ToolResult(
                        toolName = toolCall.toolName,
                        toolCallId = toolCall.toolCallId,
                        content = result,
                        arguments = args,
                        metadata = toolCall.metadata
                    )
                }.onFailure {
                    it.printStackTrace()
                    results += UIMessagePart.ToolResult(
                        toolName = toolCall.toolName,
                        toolCallId = toolCall.toolCallId,
                        metadata = toolCall.metadata,
                        content = buildJsonObject {
                            put(
                                "error",
                                JsonPrimitive(buildString {
                                    append("[${it.javaClass.name}] ${it.message}")
                                    append("\n${it.stackTraceToString()}")
                                })
                            )
                        },
                        arguments = runCatching {
                            json.parseToJsonElement(toolCall.arguments)
                        }.getOrElse { JsonObject(emptyMap()) }
                    )
                }
            }
            messages = messages + UIMessage(
                role = MessageRole.TOOL,
                parts = results
            )
            emit(
                GenerationChunk.Messages(
                    messages.transforms(
                        transformers = outputTransformers,
                        context = context,
                        model = model,
                        assistant = assistant
                    )
                )
            )
        }

    }.flowOn(Dispatchers.IO)

    suspend fun buildMessages(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        model: Model,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        truncateIndex: Int,
        enabledModeIds: Set<Uuid> = emptySet(),
    ): BuildMessagesResult {
        // Token estimator (rough estimate: 4 chars per token)
        fun estimateTokens(text: String) = text.length / 4
        fun estimateTokens(message: UIMessage) = estimateTokens(message.toText())

        val maxTokens = assistant.maxTokenUsage
        var currentTokens = 0

        // Cosine similarity for RAG matching
        fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
            if (a.size != b.size) return 0f
            var dotProduct = 0f
            var normA = 0f
            var normB = 0f
            for (i in a.indices) {
                dotProduct += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denominator = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
            return if (denominator == 0f) 0f else dotProduct / denominator
        }
        
        // Helper to check if and why lorebook entry activated
        fun getLorebookEntryActivationReason(entry: LorebookEntry, recentMessages: List<String>, queryEmbedding: List<Float>? = null): String? {
            if (!entry.enabled) return null
            return when (entry.activationType) {
                LorebookActivationType.ALWAYS -> "Always Active"
                LorebookActivationType.KEYWORDS -> {
                    val searchText = recentMessages.joinToString(" ")
                    val matchingKeyword = entry.keywords.firstOrNull { keyword ->
                        if (entry.useRegex) {
                            try {
                                val regex = if (entry.caseSensitive) {
                                    Regex(keyword)
                                } else {
                                    Regex(keyword, RegexOption.IGNORE_CASE)
                                }
                                regex.containsMatchIn(searchText)
                            } catch (e: Exception) {
                                Log.w(TAG, "Invalid regex in lorebook entry: $keyword", e)
                                false
                            }
                        } else {
                            if (entry.caseSensitive) {
                                searchText.contains(keyword)
                            } else {
                                searchText.contains(keyword, ignoreCase = true)
                            }
                        }
                    }
                    if (matchingKeyword != null) "Keyword: $matchingKeyword" else null
                }
                LorebookActivationType.RAG -> {
                    // RAG activation uses embedding similarity
                    if (entry.embedding == null || entry.embedding.isEmpty()) {
                        Log.d(TAG, "RAG entry '${entry.name}' has no embedding, skipping")
                        null
                    } else if (queryEmbedding == null) {
                        Log.d(TAG, "No query embedding available for RAG matching")
                        null
                    } else {
                        // Compute cosine similarity
                        val similarity = cosineSimilarity(entry.embedding, queryEmbedding)
                        val threshold = 0.7f // Similarity threshold for activation
                        val activated = similarity >= threshold
                        if (activated) {
                            val scoreStr = try {
                                "%.2f".format(similarity)
                            } catch (e: Exception) {
                                similarity.toString().take(4)
                            }
                            Log.d(TAG, "RAG entry '${entry.name}' activated with similarity $similarity")
                            "RAG Match ($scoreStr)"
                        } else null
                    }
                }
            }
        }

        // Get recent message text for lorebook keyword scanning
        val recentMessagesForScan = messages.takeLast(10).map { it.toText() }

        // Collect enabled modes - use per-conversation enabledModeIds if provided, otherwise fall back to defaultEnabled
        val enabledModes = if (enabledModeIds.isNotEmpty()) {
            settings.modes.filter { enabledModeIds.contains(it.id) }
        } else {
            settings.modes.filter { it.defaultEnabled }
        }
        
        // Build UsedMode list for UI display
        val usedModes = enabledModes.mapIndexed { index, mode ->
            val reason = if (enabledModeIds.contains(mode.id)) {
                "Activated by user"
            } else {
                "Default enabled"
            }
            me.rerere.ai.ui.UsedMode(
                modeId = mode.id.toString(),
                modeName = mode.name,
                modeIcon = mode.icon,
                priority = enabledModes.size - index,  // Higher priority for earlier modes
                activationReason = reason
            )
        }

        // Check if any lorebook entries use RAG activation
        val lorebooksForAssistant = settings.lorebooks
            .filter { it.enabled && assistant.enabledLorebookIds.contains(it.id) }
        val hasRagEntries = lorebooksForAssistant.any { lorebook ->
            lorebook.entries.any { it.activationType == LorebookActivationType.RAG && it.enabled }
        }
        
        // Compute query embedding only if there are RAG entries
        val queryEmbedding: List<Float>? = if (hasRagEntries) {
            try {
                val queryText = recentMessagesForScan.takeLast(3).joinToString("\n")
                if (queryText.isNotBlank()) {
                    embeddingService.embed(queryText)
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to compute query embedding for RAG", e)
                null
            }
        } else null

        // Collect activated lorebook entries from enabled lorebooks assigned to this assistant
        // Also track UsedLorebookEntry info for the UI display
        // Collect activated lorebook entries from enabled lorebooks assigned to this assistant
        // Also track UsedLorebookEntry info for the UI display
        data class ActivatedEntryWithLorebook(val lorebook: Lorebook, val entry: LorebookEntry, val entryIndex: Int, val reason: String)
        val activatedEntriesWithLorebook = lorebooksForAssistant
            .flatMap { lorebook -> 
                lorebook.entries.mapIndexedNotNull { index, entry ->
                    val reason = getLorebookEntryActivationReason(entry, recentMessagesForScan, queryEmbedding)
                    if (reason != null) {
                        ActivatedEntryWithLorebook(lorebook, entry, index, reason)
                    } else null
                }
            }
        val activatedEntries = activatedEntriesWithLorebook.map { it.entry }
        
        // Build UsedLorebookEntry list for UI display
        val usedLorebookEntries = activatedEntriesWithLorebook.mapIndexed { priority, activated ->
            // Serialize cover Avatar to JSON string for UI display
            val coverJson = activated.lorebook.cover?.let { cover ->
                try {
                    json.encodeToString(me.rerere.rikkahub.data.model.Avatar.serializer(), cover)
                } catch (e: Exception) {
                    null
                }
            }
            me.rerere.ai.ui.UsedLorebookEntry(
                lorebookId = activated.lorebook.id.toString(),
                lorebookName = activated.lorebook.name,
                lorebookCover = coverJson,
                entryId = activated.entry.id.toString(),
                entryName = activated.entry.name,
                entryIndex = activated.entryIndex,
                priority = activatedEntriesWithLorebook.size - priority, // Higher priority for first entries
                activationReason = activated.reason
            )
        }

        // Group injections by position
        val beforeSystemModes = enabledModes.filter { it.injectionPosition == InjectionPosition.BEFORE_SYSTEM }
        val afterSystemModes = enabledModes.filter { it.injectionPosition == InjectionPosition.AFTER_SYSTEM }
        val beforeSystemEntries = activatedEntries.filter { it.injectionPosition == InjectionPosition.BEFORE_SYSTEM }
        val afterSystemEntries = activatedEntries.filter { it.injectionPosition == InjectionPosition.AFTER_SYSTEM }

        // 1. Base System Prompt (BEFORE_SYSTEM modes/entries + System + Learning + AFTER_SYSTEM modes/entries + Tools)
        val baseSystemPromptBuilder = StringBuilder()
        
        // BEFORE_SYSTEM injections
        beforeSystemModes.forEach { mode ->
            baseSystemPromptBuilder.append(mode.prompt)
            baseSystemPromptBuilder.appendLine()
        }
        beforeSystemEntries.forEach { entry ->
            baseSystemPromptBuilder.append(entry.prompt)
            baseSystemPromptBuilder.appendLine()
        }
        
        // Original system prompt  
        if (assistant.systemPrompt.isNotBlank()) {
            baseSystemPromptBuilder.append(assistant.systemPrompt)
        }
        
        // Learning mode (legacy - still supported)
        if (assistant.learningMode) {
            baseSystemPromptBuilder.appendLine()
            baseSystemPromptBuilder.append(settings.learningModePrompt.ifEmpty { DEFAULT_LEARNING_MODE_PROMPT })
            baseSystemPromptBuilder.appendLine()
        }
        
        // AFTER_SYSTEM injections
        afterSystemModes.forEach { mode ->
            baseSystemPromptBuilder.appendLine()
            baseSystemPromptBuilder.append(mode.prompt)
        }
        afterSystemEntries.forEach { entry ->
            baseSystemPromptBuilder.appendLine()
            baseSystemPromptBuilder.append(entry.prompt)
        }
        
        // Tool prompts
        tools.forEach { tool ->
            baseSystemPromptBuilder.appendLine()
            baseSystemPromptBuilder.append(tool.systemPrompt(model, messages))
        }
        val baseSystemPrompt = baseSystemPromptBuilder.toString()
        currentTokens += estimateTokens(baseSystemPrompt)

        // 2. Prepare Candidates
        // Apply message history limit if configured
        val historyLimitedMessages = assistant.maxHistoryMessages?.let { limit ->
            if (limit > 0) messages.limitContext(limit) else messages
        } ?: messages
        
        // Prune search results if configured
        val searchPrunedMessages = assistant.maxSearchResultsRetained?.let { maxSearches ->
            if (maxSearches > 0) {
                // Find all messages that contain search tool results
                val searchResultIndices = historyLimitedMessages.mapIndexedNotNull { index, msg ->
                    val hasSearchResult = msg.parts.any { part ->
                        part is UIMessagePart.ToolResult && part.toolName == "search_web"
                    }
                    if (hasSearchResult) index else null
                }
                
                // Keep only the last N search results
                val indicesToPrune = searchResultIndices.dropLast(maxSearches).toSet()
                
                if (indicesToPrune.isNotEmpty()) {
                    historyLimitedMessages.mapIndexed { index, msg ->
                        if (index in indicesToPrune) {
                            // Replace search result content with a minimal placeholder
                            msg.copy(parts = msg.parts.map { part ->
                                if (part is UIMessagePart.ToolResult && part.toolName == "search_web") {
                                    part.copy(content = kotlinx.serialization.json.buildJsonObject {
                                        put("note", kotlinx.serialization.json.JsonPrimitive("Earlier search results pruned to save context"))
                                    })
                                } else part
                            })
                        } else msg
                    }
                } else historyLimitedMessages
            } else historyLimitedMessages
        } ?: historyLimitedMessages
        
        // Chat History (reverse order to prioritize recent)
        val chatHistoryCandidates = searchPrunedMessages.truncate(truncateIndex).reversed()
        
        // Memories (Prepare effective memories including recent chats if enabled)
        val effectiveMemoriesCandidates = if (assistant.enableMemory) {
            val recentChatMemories = if (assistant.enableRecentChatsReference && messages.size <= 2) {
                val today = java.time.LocalDate.now()
                val recentConversations = conversationRepo.getRecentConversations(
                    assistantId = assistant.id,
                    limit = 3,
                ).filter { 
                    java.time.LocalDateTime.ofInstant(it.updateAt, java.time.ZoneId.systemDefault()).toLocalDate() == today 
                }
                recentConversations.map { conversation ->
                    AssistantMemory(
                        id = -1,
                        content = "Participated in conversation: ${conversation.title}",
                        type = 1,
                        timestamp = conversation.updateAt.toEpochMilli()
                    )
                }
            } else {
                emptyList()
            }
            (memories + recentChatMemories).distinctBy { it.content } // Avoid duplicates
        } else {
            emptyList()
        }

        // 3. Allocation Logic
        val selectedMessages = mutableListOf<UIMessage>()
        val selectedMemories = mutableListOf<AssistantMemory>()
        
        val remainingTokens = maxTokens - currentTokens
        if (remainingTokens <= 0) {
            // Edge case: System prompt too large. Just return minimums.
            Log.w(TAG, "buildMessages: System prompt exceeds max tokens!")
        }

        // Minimums
        val minChatHistory = 2.coerceAtMost(chatHistoryCandidates.size)
        val minMemories = if (assistant.enableMemory) 2.coerceAtMost(effectiveMemoriesCandidates.size) else 0

        // Add minimums first
        var usedTokens = 0
        
        // Add min chat history
        chatHistoryCandidates.take(minChatHistory).forEach {
            selectedMessages.add(it)
            usedTokens += estimateTokens(it)
        }
        
        // Add min memories
        effectiveMemoriesCandidates.take(minMemories).forEach {
            selectedMemories.add(it)
            usedTokens += estimateTokens(it.content)
        }

        // Distribute remaining tokens
        var availableTokens = remainingTokens - usedTokens
        if (availableTokens > 0) {
            val remainingChatHistory = chatHistoryCandidates.drop(minChatHistory)
            val remainingMemories = effectiveMemoriesCandidates.drop(minMemories)
            
            when (assistant.contextPriority) {
                me.rerere.rikkahub.data.model.ContextPriority.CHAT_HISTORY -> {
                    // Prioritize Chat History
                    for (msg in remainingChatHistory) {
                        val cost = estimateTokens(msg)
                        if (availableTokens >= cost) {
                            selectedMessages.add(msg)
                            availableTokens -= cost
                        } else break
                    }
                    for (mem in remainingMemories) {
                        val cost = estimateTokens(mem.content)
                        if (availableTokens >= cost) {
                            selectedMemories.add(mem)
                            availableTokens -= cost
                        }
                    }
                }
                me.rerere.rikkahub.data.model.ContextPriority.MEMORIES -> {
                    // Prioritize Memories
                    for (mem in remainingMemories) {
                        val cost = estimateTokens(mem.content)
                        if (availableTokens >= cost) {
                            selectedMemories.add(mem)
                            availableTokens -= cost
                        }
                    }
                    for (msg in remainingChatHistory) {
                        val cost = estimateTokens(msg)
                        if (availableTokens >= cost) {
                            selectedMessages.add(msg)
                            availableTokens -= cost
                        } else break
                    }
                }
                me.rerere.rikkahub.data.model.ContextPriority.BALANCED -> {
                    // Balanced (e.g. 50/50 split of remaining, or round-robin)
                    // Simple round-robin approach
                    var msgIndex = 0
                    var memIndex = 0
                    var addedSomething = true
                    while (addedSomething && availableTokens > 0) {
                        addedSomething = false
                        // Try add message
                        if (msgIndex < remainingChatHistory.size) {
                            val msg = remainingChatHistory[msgIndex]
                            val cost = estimateTokens(msg)
                            if (availableTokens >= cost) {
                                selectedMessages.add(msg)
                                availableTokens -= cost
                                msgIndex++
                                addedSomething = true
                            }
                        }
                        // Try add memory
                        if (memIndex < remainingMemories.size) {
                            val mem = remainingMemories[memIndex]
                            val cost = estimateTokens(mem.content)
                            if (availableTokens >= cost) {
                                selectedMemories.add(mem)
                                availableTokens -= cost
                                memIndex++
                                addedSomething = true
                            }
                        }
                    }
                }
            }
        }

        // 4. Construct Final List
        // Collect all attachments from enabled modes
        val modeAttachmentParts = enabledModes.flatMap { mode ->
            mode.attachments.map { attachment ->
                when (attachment.type) {
                    ModeAttachmentType.IMAGE -> UIMessagePart.Image(url = attachment.url)
                    ModeAttachmentType.VIDEO -> UIMessagePart.Video(url = attachment.url)
                    ModeAttachmentType.AUDIO -> UIMessagePart.Audio(url = attachment.url)
                    ModeAttachmentType.DOCUMENT -> UIMessagePart.Document(
                        url = attachment.url,
                        fileName = attachment.fileName,
                        mime = attachment.mime
                    )
                }
            }
        }
        
        // Collect attachments from activated lorebook entries
        val lorebookAttachmentParts = activatedEntries.flatMap { entry ->
            entry.attachments.map { attachment ->
                when (attachment.type) {
                    ModeAttachmentType.IMAGE -> UIMessagePart.Image(url = attachment.url)
                    ModeAttachmentType.VIDEO -> UIMessagePart.Video(url = attachment.url)
                    ModeAttachmentType.AUDIO -> UIMessagePart.Audio(url = attachment.url)
                    ModeAttachmentType.DOCUMENT -> UIMessagePart.Document(
                        url = attachment.url,
                        fileName = attachment.fileName,
                        mime = attachment.mime
                    )
                }
            }
        }
        
        // Combine all context attachments
        val allContextAttachments = modeAttachmentParts + lorebookAttachmentParts
        
        val builtMessages = buildList {
            val finalSystemPrompt = buildString {
                append(baseSystemPrompt)
                if (selectedMemories.isNotEmpty()) {
                    appendLine()
                    append(buildMemoryPrompt(model, selectedMemories))
                }
            }
            if (finalSystemPrompt.isNotBlank()) {
                add(UIMessage.system(finalSystemPrompt))
            }
            
            // Add mode and lorebook attachments as a user message if there are any
            if (allContextAttachments.isNotEmpty()) {
                add(UIMessage(
                    role = me.rerere.ai.core.MessageRole.USER,
                    parts = allContextAttachments
                ))
            }
            
            // Restore chat history order
            addAll(selectedMessages.sortedBy { messages.indexOf(it) })
        }
        // Build UsedMemory list for UI display
        val usedMemories = selectedMemories.mapIndexed { index, memory ->
            val reason = when {
                memory.id == -1 -> "Recent episode boost"  // Recent chat reference
                assistant.useRagMemoryRetrieval -> "Contextually relevant"  // RAG mode
                else -> "Always included"  // Basic mode
            }
            me.rerere.ai.ui.UsedMemory(
                memoryId = memory.id,
                memoryContent = memory.content.take(50) + if (memory.content.length > 50) "..." else "",
                memoryType = memory.type,
                priority = selectedMemories.size - index,  // Higher priority for earlier memories
                activationReason = reason
            )
        }
        
        return BuildMessagesResult(
            messages = builtMessages,
            activatedLorebookEntries = usedLorebookEntries,
            usedModes = usedModes,
            usedMemories = usedMemories
        )
    }

    private suspend fun generateInternal(
        assistant: Assistant,
        settings: Settings,
        messages: List<UIMessage>,
        onUpdateMessages: suspend (List<UIMessage>) -> Unit,
        transformers: List<MessageTransformer>,
        model: Model,
        providerImpl: Provider<ProviderSetting>,
        provider: ProviderSetting,
        tools: List<Tool>,
        memories: List<AssistantMemory>,
        truncateIndex: Int,
        stream: Boolean,
        enabledModeIds: Set<Uuid> = emptySet()
    ) {
        val buildResult = buildMessages(
            assistant = assistant,
            settings = settings,
            messages = messages,
            model = model,
            tools = tools,
            memories = memories,
            truncateIndex = truncateIndex,
            enabledModeIds = enabledModeIds
        )
        val internalMessages = buildResult.messages.transforms(transformers, context, model, assistant)
        val usedLorebookEntries = buildResult.activatedLorebookEntries
        val usedModes = buildResult.usedModes
        val usedMemories = buildResult.usedMemories
        val hasContextSources = usedLorebookEntries.isNotEmpty() || usedModes.isNotEmpty() || usedMemories.isNotEmpty()

        var messages: List<UIMessage> = messages
        val params = TextGenerationParams(
            model = model,
            temperature = assistant.temperature,
            topP = assistant.topP,
            maxTokens = assistant.maxTokens,
            tools = tools,
            thinkingBudget = assistant.thinkingBudget,
            customHeaders = buildList {
                addAll(assistant.customHeaders)
                addAll(model.customHeaders)
            },
            customBody = buildList {
                addAll(assistant.customBodies)
                addAll(model.customBodies)
            }
        )
        if (stream) {
            aiLoggingManager.addLog(AILogging.Generation(
                params = params,
                messages = messages,
                providerSetting = provider,
                stream = true
            ))
            providerImpl.streamText(
                providerSetting = provider,
                messages = internalMessages,
                params = params
            ).collect {
                messages = messages.handleMessageChunk(chunk = it, model = model)
                it.usage?.let { usage ->
                    messages = messages.mapIndexed { index, message ->
                        if (index == messages.lastIndex) {
                            message.copy(usage = message.usage.merge(usage))
                        } else {
                            message
                        }
                    }
                }
                onUpdateMessages(messages)
            }
            // Attach all context sources to the last assistant message after streaming completes
            if (hasContextSources) {
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex && message.role == me.rerere.ai.core.MessageRole.ASSISTANT) {
                        message.copy(
                            usedLorebookEntries = usedLorebookEntries.ifEmpty { null },
                            usedModes = usedModes.ifEmpty { null },
                            usedMemories = usedMemories.ifEmpty { null }
                        )
                    } else {
                        message
                    }
                }
                onUpdateMessages(messages)
            }
        } else {
            aiLoggingManager.addLog(AILogging.Generation(
                params = params,
                messages = messages,
                providerSetting = provider,
                stream = false
            ))
            val chunk = providerImpl.generateText(
                providerSetting = provider,
                messages = internalMessages,
                params = params,
            )
            messages = messages.handleMessageChunk(chunk = chunk, model = model)
            chunk.usage?.let { usage ->
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex) {
                        message.copy(
                            usage = message.usage.merge(usage)
                        )
                    } else {
                        message
                    }
                }
            }
            // Attach all context sources to the last assistant message
            if (hasContextSources) {
                messages = messages.mapIndexed { index, message ->
                    if (index == messages.lastIndex && message.role == me.rerere.ai.core.MessageRole.ASSISTANT) {
                        message.copy(
                            usedLorebookEntries = usedLorebookEntries.ifEmpty { null },
                            usedModes = usedModes.ifEmpty { null },
                            usedMemories = usedMemories.ifEmpty { null }
                        )
                    } else {
                        message
                    }
                }
            }
            onUpdateMessages(messages)
        }
    }

    private fun buildMemoryTools(
        onCreation: suspend (String) -> AssistantMemory,
        onUpdate: suspend (Int, String) -> AssistantMemory,
        onDelete: suspend (Int) -> Unit
    ) = listOf(
        Tool(
            name = "create_memory",
            description = "Create a new memory record.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "Content of the memory.")
                        })
                    },
                    required = listOf("content")
                )
            },
            execute = {
                val params = it.jsonObject
                val content =
                    params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
                json.encodeToJsonElement(AssistantMemory.serializer(), onCreation(content))
            }
        ),
        Tool(
            name = "edit_memory",
            description = "Update an existing memory record.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "ID of the memory to update.")
                        })
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "New content for the memory.")
                        })
                    },
                    required = listOf("id", "content"),
                )
            },
            execute = {
                val params = it.jsonObject
                val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                val content =
                    params["content"]?.jsonPrimitive?.contentOrNull ?: error("content is required")
                val before = memoryRepo.getMemoryById(id)
                val updated = onUpdate(id, content)
                buildJsonObject {
                    put("id", JsonPrimitive(updated.id))
                    put("content", JsonPrimitive(updated.content))
                    put("type", JsonPrimitive(updated.type))
                    put("hasEmbedding", JsonPrimitive(updated.hasEmbedding))
                    updated.embeddingModelId?.let { put("embeddingModelId", JsonPrimitive(it)) }
                    put("timestamp", JsonPrimitive(updated.timestamp))
                    updated.significance?.let { put("significance", JsonPrimitive(it)) }
                    before?.let { previous ->
                        put("before_content", JsonPrimitive(previous.content))
                        put("before_timestamp", JsonPrimitive(previous.timestamp))
                    }
                }
            }
        ),
        Tool(
            name = "delete_memory",
            description = "Delete a memory record.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "ID of the memory to delete.")
                        })
                    },
                    required = listOf("id")
                )
            },
            execute = {
                val params = it.jsonObject
                val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                val before = memoryRepo.getMemoryById(id)
                onDelete(id)
                buildJsonObject {
                    put("deleted", JsonPrimitive(true))
                    before?.let { memory ->
                        put("id", JsonPrimitive(memory.id))
                        put("content", JsonPrimitive(memory.content))
                        put("type", JsonPrimitive(memory.type))
                        put("hasEmbedding", JsonPrimitive(memory.hasEmbedding))
                        memory.embeddingModelId?.let { put("embeddingModelId", JsonPrimitive(it)) }
                        put("timestamp", JsonPrimitive(memory.timestamp))
                        memory.significance?.let { put("significance", JsonPrimitive(it)) }
                    }
                }
            }
        )
    )

    private suspend fun buildMemoryPrompt(model: Model, memories: List<AssistantMemory>): String {
        Log.d(TAG, "buildMemoryPrompt: Injecting ${memories.size} memories into prompt")
        if (memories.isEmpty()) {
            Log.w(TAG, "buildMemoryPrompt: WARNING - No memories to inject!")
            return ""
        }

        val coreMemories = memories.filter { it.type == 0 } // CORE
        val episodicMemories = memories.filter { it.type == 1 } // EPISODIC
        
        return buildString {
            append("## Memories\n")
            append("These are memories that you can reference in the future conversations.\n")
            
            if (coreMemories.isNotEmpty()) {
                append("### Core Memories\n")
                coreMemories.forEach { memory ->
                    append("- [ID: ${memory.id}] ${memory.content}\n")
                }
            }

            if (episodicMemories.isNotEmpty()) {
                append("### Episodic Memories\n")
                
                val now = java.time.LocalDate.now()
                val yesterday = now.minusDays(1)
                val lastWeek = now.minusWeeks(1)
                
                val groupedEpisodes = episodicMemories.groupBy { memory ->
                    val date = java.time.Instant.ofEpochMilli(memory.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    
                    when {
                        date.isEqual(now) -> "Today"
                        date.isEqual(yesterday) -> "Yesterday"
                        date.isAfter(lastWeek) -> "This Week"
                        else -> "Older"
                    }
                }
                
                // Order: Today -> Yesterday -> This Week -> Older
                listOf("Today", "Yesterday", "This Week", "Older").forEach { group ->
                    val memoriesInGroup = groupedEpisodes[group]
                    if (!memoriesInGroup.isNullOrEmpty()) {
                        append("#### $group\n")
                        memoriesInGroup.sortedByDescending { it.timestamp }.forEach { memory ->
                            append("- ${memory.content}\n")
                        }
                    }
                }
            }
            
            if (model.abilities.contains(ModelAbility.TOOL)) {
                append(
                    """
                        
                        ## Memory Tool
                        You are a stateless large language model; you **cannot store memories** internally. To remember information, you must use **memory tools**.
                        Memory tools allow you (the assistant) to store multiple pieces of information (records) to recall details across conversations.
                        You can use the `create_memory`, `edit_memory`, and `delete_memory` tools to create, update, or delete memories.
                        - If there is no relevant information in memory, call `create_memory` to create a new record.
                        - If a relevant record already exists, call `edit_memory` to update it.
                        - If a memory is outdated or no longer useful, call `delete_memory` to remove it.
                        **Note:** You can only edit or delete **Core Memories** (which have an ID). Episodic Memories are read-only context.
                        
                        **Do not store sensitive information.** Sensitive information includes: ethnicity, religious beliefs, sexual orientation, political views, sexual life, criminal records, etc.
                        During chats, act like a personal secretary and **proactively** record user-related information, including but not limited to:
                        - Name/Nickname
                        - Age/Gender/Hobbies
                        - Plans/To-do items
                    """.trimIndent()
                )
            }
        }
    }

    fun translateText(
        settings: Settings,
        sourceText: String,
        targetLanguage: Locale,
        modelIdOverride: Uuid? = null,
        onStreamUpdate: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val modelId = modelIdOverride ?: settings.translateModeId
        val model = settings.providers.findModelById(modelId)
            ?: error("Translation model not found")
        val provider = model.findProvider(settings.providers)
            ?: error("Translation provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        if (!ModelRegistry.QWEN_MT.match(model.modelId)) {
            // Use regular translation with prompt
            val prompt = settings.translatePrompt.applyPlaceholders(
                "source_text" to sourceText,
                "target_lang" to targetLanguage.toString(),
            )

            var messages = listOf(UIMessage.user(prompt))
            var translatedText = ""

            providerHandler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                ),
            ).collect { chunk ->
                messages = messages.handleMessageChunk(chunk)
                translatedText = messages.lastOrNull()?.toContentText() ?: ""

                if (translatedText.isNotBlank()) {
                    onStreamUpdate?.invoke(translatedText)
                    emit(translatedText)
                }
            }
        } else {
            // Use Qwen MT model with special translation options
            val messages = listOf(UIMessage.user(sourceText))
            val chunk = providerHandler.generateText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.3f,
                    topP = 0.95f,
                    customBody = listOf(
                        CustomBody(
                            key = "translation_options",
                            value = buildJsonObject {
                                put("source_lang", JsonPrimitive("auto"))
                                put(
                                    "target_lang",
                                    JsonPrimitive(targetLanguage.getDisplayLanguage(Locale.ENGLISH))
                                )
                            }
                        )
                    )
                ),
            )
            val translatedText = chunk.choices.firstOrNull()?.message?.toContentText() ?: ""

            if (translatedText.isNotBlank()) {
                onStreamUpdate?.invoke(translatedText)
                emit(translatedText)
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Attempts to sanitize malformed JSON from streamed tool call arguments.
     * Handles cases where the model outputs content after a valid JSON object.
     */
    private fun sanitizeToolCallArguments(arguments: String): String {
        if (arguments.isBlank()) return "{}"
        val trimmed = arguments.trim()
        
        // Find the first complete JSON object
        var braceCount = 0
        var inString = false
        var escape = false
        
        for ((index, char) in trimmed.withIndex()) {
            if (escape) {
                escape = false
                continue
            }
            when (char) {
                '\\' -> if (inString) escape = true
                '"' -> inString = !inString
                '{' -> if (!inString) braceCount++
                '}' -> if (!inString) {
                    braceCount--
                    if (braceCount == 0) {
                        // Found complete object, return it
                        return trimmed.substring(0, index + 1)
                    }
                }
            }
        }
        // Couldn't find complete object, return empty
        Log.w(TAG, "Could not extract valid JSON object from: $trimmed")
        return "{}"
    }
}
