package me.rerere.rikkahub.data.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import me.rerere.ai.context.ContextMapCategory
import me.rerere.ai.context.ContextMapEntry
import me.rerere.ai.context.ContextTokenEstimator
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_IN_WORLD_ARTIFACTS_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_LEARNING_MODE_PROMPT
import me.rerere.rikkahub.data.ai.tools.LocalToolDefinitionPreview
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.ai.tools.toDefinitionPreviews
import me.rerere.rikkahub.data.ai.tools.workspaceToolDefinitionPreviews
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantSearchMode
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.model.InstructionItem
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.LorebookEntry
import me.rerere.rikkahub.data.model.Skill
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import java.io.File
import kotlin.uuid.Uuid

internal val VISUAL_TOOL_NAMES = setOf(
    "search_web",
    "workspace_view_image",
    "workspace_read_file",
    "look_at_screen",
)

internal val VOICE_MEMOS_MEMORY_BRIDGE_PROMPT = """
    ## Voice Memos & Audio Transcripts
    - The user records voice memos and audio notes directly on their device.
    - Transcripts and summaries are synced to `/workspace/transcripts/`.
    - A lightweight catalog index is available at `/workspace/transcripts/index.json` containing memo titles, dates, durations, and brief summaries.
    - When the user asks about their recordings, voice notes, or spoken ideas, inspect `/workspace/transcripts/index.json` and read the relevant transcript markdown file at `/workspace/transcripts/<id>.md`.
""".trimIndent()

internal val TMDB_MEDIA_PROMPT = """
    ## Entertainment & TMDB Media Tools
    When discussing, searching, or recommending movies, TV shows, or anime:
    - `search_media(query, type)`: Find shows or movies. Parameters: `query` (title/actor name), `type` ('multi', 'tv', 'movie', default 'multi').
    - `get_media_info(id, type)`: Full details (synopsis, seasons, episodes, cast, rating). Parameters: `id` (numeric TMDB id), `type` ('tv' or 'movie', default 'tv').
    - `get_media_art(id, type)`: High-res posters and 16:9 cinematic backdrops. Parameters: `id`, `type` (default 'tv').
    - `get_media_recommendations(id, type)`: Algorithmic recommendations. Parameters: `id`, `type` (default 'tv').
    - `manage_watchlist(action, id, type)`: View or modify watchlist. Parameters: `action` ('get' (default), 'add', 'remove'), `id` (numeric id), `type` ('tv' or 'movie').

    When recommending titles or presenting multiple shows to the user, ALWAYS present them as a native media showcase using the ```media_deck``` code fence:
    ```media_deck
    {
      "title": "Companion Recommendations",
      "items": [
        {
          "tmdb_id": 37854,
          "media_type": "tv",
          "title": "One Piece",
          "year": "1999",
          "rating": "8.7",
          "progress": "Ep 594 / 1122",
          "poster": "https://image.tmdb.org/t/p/w500/<hash>.jpg",
          "badges": ["Anime", "Action"],
          "hook": "Companion's personalized commentary explaining why you picked this."
        }
      ]
    }
    ```
    You can recommend any number of items (1, 2, 3, 5, etc.). Always keep the deck mobile-friendly and include real poster URLs returned by the media tools.
""".trimIndent()

internal val VISUAL_TOOL_PRIVACY_PROMPT = """
    ## Tool Visual Inspection & User Privacy Guidelines
    - Tool-inspected images (from web search, workspace files, screenshots, etc.) are private behind-the-scenes visual inspection data delivered for your eyes only. The user CANNOT see them.
    - The user DID NOT upload, provide, or see these tool images. Do NOT refer to them as "the images you sent", "the images you uploaded", or "the images you provided".
    - If the user asks for images, or if an image from search or workspace is useful or relevant to show, you MUST explicitly output the markdown image link `![title](url)` in your final response to the user. Otherwise, the user will see nothing.
""".trimIndent()

internal val MEMORY_SEARCH_TOOL_SYSTEM_PROMPT = """
    ## Memory search tool
    You may call `$MEMORY_SEARCH_TOOL_NAME` when you are deliberately trying to remember something from core memories or older chats.
    - Use it for genuine recall, not on every turn.
    - It searches only this character's memories and actually used chat timeline, not other characters or discarded reply versions.
    - You can pass `time_range` when the user asks about a rough time span, like "last day", "last 2 days", "yesterday", "last week", "this month", "last month", or "4 months ago".
    - Preserve the user's recall terms, including odd meta words; the memory subagent will expand the query internally across several variants.
    - If the returned summary says no clear memory was found and the user keeps pressing, you may try one more narrower query.
    - Treat returned memories as approximate, human-like recollections.
    - Time labels are fuzzy on purpose; do not expose exact timestamps unless the user asks.
    - If confidence is low or results disagree, answer with natural uncertainty.
""".trimIndent()

internal val SEARCH_WEB_USAGE_SYSTEM_PROMPT = """
    ## tool: search_web

    ### usage
    - You can use the search_web tool to search the internet for the latest news, weather, sports, or to confirm some facts.
    - You can perform multiple search if needed
    - Generate keywords based on the user's question. For live data, prefer a `topic` of `weather`, `news`, `sports`, or `images` when it is obvious.
    - Today is {{cur_date}}
    - Trust structured weather `answer` fields over encyclopedia snippets.
    - For news, prefer items with recent `publishedAt` dates. Do not treat Wikipedia as live news.
    - Search results are private behind-the-scenes data for your eyes only. The user CANNOT see them and did NOT provide or upload them.
    - When the result includes an `images` array, if the user asks for images or if an image is useful, put 1–4 relevant `markdown_image` values (e.g. `![title](url)`) each on their own line in your reply so they render inline for the user. If you do not explicitly embed `![title](url)`, the user will see nothing.
""".trimIndent()

internal val SCRAPE_WEB_SYSTEM_PROMPT = """
    ## tool: scrape_web

    ### usage
    - You can use the scrape_web tool to scrape url for detailed content.
    - You can perform multiple scrape if needed.
    - For common problems, try not to use this tool unless the user requests it.
""".trimIndent()

internal fun formatSkillPromptText(skillsRoot: File?, skill: Skill): String = buildString {
    if (skill.name.isNotBlank()) {
        append("[Skill: ${skill.name}]")
        appendLine()
    }
    append(skill.instructions)
    if (skillsRoot != null) {
        val resources = File(skillsRoot, skill.workspaceDirectory().removePrefix("/skills/"))
            .takeIf { it.isDirectory }
            ?.walkTopDown()
            ?.filter { it.isFile && it.name != "SKILL.md" }
            ?.map { it.relativeTo(skillsRoot).invariantSeparatorsPath }
            ?.take(64)
            ?.toList()
            .orEmpty()
        if (resources.isNotEmpty()) {
            appendLine()
            appendLine("Skill package: ${skill.workspaceDirectory()}")
            appendLine("Bundled resources (load only when needed):")
            resources.forEach { appendLine("- /skills/$it") }
        }
    }
}

internal fun formatSkillChatMessageText(skill: Skill): String =
    "<system>\n[Skill: ${skill.name}]\n${skill.instructions}\nSkill directory: ${skill.workspaceDirectory()}\n</system>"

internal fun formatSkillsAccountingText(
    skillsRoot: File?,
    skills: List<Skill>,
): String = buildString {
    skills.forEach { skill ->
        val text = when (skill.injectionPosition) {
            InjectionPosition.BEFORE_SYSTEM,
            InjectionPosition.AFTER_SYSTEM -> formatSkillPromptText(skillsRoot, skill)
            else -> formatSkillChatMessageText(skill)
        }
        appendLine(text)
    }
}

internal fun resolveActiveInstructions(
    settings: Settings,
    assistant: Assistant,
    conversationEnabledInstructionIds: Set<Uuid>? = null,
): List<InstructionItem> {
    val activeInstructionIds = conversationEnabledInstructionIds ?: assistant.enabledInstructionIds
    return settings.instructions.filter { inst ->
        if (conversationEnabledInstructionIds != null) {
            activeInstructionIds.contains(inst.id)
        } else if (assistant.enabledInstructionIds.isNotEmpty()) {
            activeInstructionIds.contains(inst.id)
        } else {
            inst.enabled
        }
    }
}

internal fun formatCustomInstructionsText(instructions: List<InstructionItem>): String {
    if (instructions.isEmpty()) return ""
    return buildString {
        appendLine("## Custom Instructions")
        instructions.forEach { inst ->
            if (inst.name.isNotBlank()) {
                appendLine("[Instruction: ${inst.name}]")
            }
            appendLine(inst.content)
            appendLine()
        }
    }
}

internal data class NamedPromptSegment(
    val id: String,
    val label: String,
    val subtitle: String? = null,
    val text: String,
)

internal data class PromptInjectionBundle(
    val systemPromptText: String,
    val customInstructionsText: String,
    val activeInstructions: List<InstructionItem>,
    val artifactsPromptText: String?,
    val voiceMemosPromptText: String?,
    val tmdbPromptText: String?,
    val visualPrivacyPromptText: String?,
    val timeAwarenessPromptText: String?,
    val featurePromptsText: String,
    val featurePromptSegments: List<NamedPromptSegment>,
)

internal fun buildPromptInjectionBundle(
    assistant: Assistant,
    settings: Settings,
    model: Model,
    rawMessages: List<UIMessage>,
    retainedMessages: List<UIMessage>,
    hasVisualTool: Boolean,
    hasVisualToolResults: Boolean = rawMessages.any { msg ->
        msg.role == MessageRole.TOOL &&
            msg.parts.any { it is UIMessagePart.ToolResult && it.inspectedImages.isNotEmpty() }
    },
    conversationEnabledInstructionIds: Set<Uuid>? = null,
    allowGlobalPromptInjections: Boolean = true,
): PromptInjectionBundle {
    val systemPromptText = buildString {
        append(assistant.systemPrompt)
        if (allowGlobalPromptInjections && assistant.learningMode) {
            appendLine()
            append(settings.learningModePrompt.ifEmpty { DEFAULT_LEARNING_MODE_PROMPT })
            appendLine()
        }
    }

    val activeInstructions = if (allowGlobalPromptInjections) {
        resolveActiveInstructions(
            settings = settings,
            assistant = assistant,
            conversationEnabledInstructionIds = conversationEnabledInstructionIds,
        )
    } else {
        emptyList()
    }
    val customInstructionsText = formatCustomInstructionsText(activeInstructions)

    val artifactsPrompt = if (allowGlobalPromptInjections && settings.displaySetting.enableInWorldArtifacts) {
        settings.displaySetting.inWorldArtifactsPrompt.ifBlank { DEFAULT_IN_WORLD_ARTIFACTS_PROMPT }
    } else {
        null
    }
    val voiceMemosPrompt = if (allowGlobalPromptInjections && settings.enableVoiceMemosMemoryBridge) {
        VOICE_MEMOS_MEMORY_BRIDGE_PROMPT
    } else {
        null
    }
    val tmdbPrompt = if (allowGlobalPromptInjections && settings.tmdbSetting.enabled) {
        TMDB_MEDIA_PROMPT
    } else {
        null
    }
    val visualPrivacyPrompt = if (
        allowGlobalPromptInjections &&
        model.inputModalities.contains(Modality.IMAGE) &&
        (hasVisualTool || hasVisualToolResults)
    ) {
        VISUAL_TOOL_PRIVACY_PROMPT
    } else {
        null
    }
    val timeAwarenessPrompt = if (allowGlobalPromptInjections) {
        buildTimeAwarenessBlock(
            enabled = assistant.enableTimeAwareness,
            fullMessages = rawMessages,
            retainedMessages = retainedMessages,
        )
    } else {
        null
    }

    val segments = buildList {
        artifactsPrompt?.let {
            add(
                NamedPromptSegment(
                    id = "feature:artifacts",
                    label = "In-World Visual Artifacts",
                    subtitle = "Interactive HTML/SVG artifact rendering guidelines",
                    text = it,
                )
            )
        }
        tmdbPrompt?.let {
            add(
                NamedPromptSegment(
                    id = "feature:tmdb",
                    label = "TMDB Media Tools & Deck",
                    subtitle = "Entertainment search & media_deck instructions",
                    text = it,
                )
            )
        }
        voiceMemosPrompt?.let {
            add(
                NamedPromptSegment(
                    id = "feature:voice_memos",
                    label = "Voice Memos Memory Bridge",
                    subtitle = "/workspace/transcripts/ index access instructions",
                    text = it,
                )
            )
        }
        timeAwarenessPrompt?.let {
            add(
                NamedPromptSegment(
                    id = "feature:time_awareness",
                    label = "Time Awareness",
                    subtitle = "Current timestamp & session timeline cues",
                    text = it,
                )
            )
        }
        visualPrivacyPrompt?.let {
            add(
                NamedPromptSegment(
                    id = "feature:visual_privacy",
                    label = "Visual Tool Privacy",
                    subtitle = "Guidelines for tool-inspected images",
                    text = it,
                )
            )
        }
    }

    val featurePromptsText = segments.joinToString("\n") { it.text }

    return PromptInjectionBundle(
        systemPromptText = systemPromptText,
        customInstructionsText = customInstructionsText,
        activeInstructions = activeInstructions,
        artifactsPromptText = artifactsPrompt,
        voiceMemosPromptText = voiceMemosPrompt,
        tmdbPromptText = tmdbPrompt,
        visualPrivacyPromptText = visualPrivacyPrompt,
        timeAwarenessPromptText = timeAwarenessPrompt,
        featurePromptsText = featurePromptsText,
        featurePromptSegments = segments,
    )
}

internal fun memoryToolDefinitionPreviews(
    includeSummaryTools: Boolean,
    includeSearchTool: Boolean,
): List<LocalToolDefinitionPreview> = buildList {
    add(
        LocalToolDefinitionPreview(
            name = "create_memory",
            description = "Create a new memory record.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("content", buildJsonObject {
                        put("type", "string")
                        put("description", "Content of the memory.")
                    })
                },
                required = listOf("content"),
            ),
        )
    )
    add(
        LocalToolDefinitionPreview(
            name = "edit_memory",
            description = "Update an existing memory record.",
            schema = InputSchema.Obj(
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
            ),
        )
    )
    add(
        LocalToolDefinitionPreview(
            name = "delete_memory",
            description = "Delete a memory record.",
            schema = InputSchema.Obj(
                properties = buildJsonObject {
                    put("id", buildJsonObject {
                        put("type", "integer")
                        put("description", "ID of the memory to delete.")
                    })
                },
                required = listOf("id"),
            ),
        )
    )
    if (includeSummaryTools) {
        add(
            LocalToolDefinitionPreview(
                name = "get_memories",
                description = "Fetch exact verbatim raw memory records by their numeric [#ID] pointers from the Memory Summary index. Use this when exact wording, timestamps, or full details are needed. Batch multiple IDs into one call.",
                schema = InputSchema.Obj(
                    properties = buildJsonObject {
                        putJsonObject("ids") {
                            put("type", "array")
                            put("description", "List of positive integer memory IDs (max 25), e.g., [79, 182].")
                            putJsonObject("items") { put("type", "integer") }
                        }
                    },
                    required = listOf("ids"),
                ),
            )
        )
        add(
            LocalToolDefinitionPreview(
                name = "manage_memory_summary",
                description = "Inspect or surgically update the user's structured Memory Summary. Prefer 'update_section' with find_text/replace_text or targeted content over 'replace_summary'.",
                schema = InputSchema.Obj(
                    properties = buildJsonObject {
                        putJsonObject("action") {
                            put("type", "string")
                            putJsonArray("enum") {
                                add(JsonPrimitive("read"))
                                add(JsonPrimitive("list_sections"))
                                add(JsonPrimitive("update_section"))
                                add(JsonPrimitive("add_section"))
                                add(JsonPrimitive("delete_section"))
                                add(JsonPrimitive("replace_summary"))
                            }
                            put("description", "Operation to execute: 'read' (full or by section_title), 'list_sections' (overview index), 'update_section', 'add_section', 'delete_section', or 'replace_summary'.")
                        }
                        putJsonObject("section_title") {
                            put("type", "string")
                            put("description", "Target section title for read (to fetch one section only), update_section, add_section, or delete_section.")
                        }
                        putJsonObject("new_title") {
                            put("type", "string")
                            put("description", "Optional renamed title when using update_section.")
                        }
                        putJsonObject("icon") {
                            put("type", "string")
                            put("description", "Optional icon name: star, heart, code, target, devices, sparkles, profile, book, music.")
                        }
                        putJsonObject("find_text") {
                            put("type", "string")
                            put("description", "Exact substring or bullet line inside section_title to replace (surgical block patch).")
                        }
                        putJsonObject("replace_text") {
                            put("type", "string")
                            put("description", "Replacement text for find_text (or appended line if find_text is omitted and append_line is true).")
                        }
                        putJsonObject("append_line") {
                            put("type", "boolean")
                            put("description", "If true with replace_text, appends a new bullet to section_title without rewriting existing lines.")
                        }
                        putJsonObject("content") {
                            put("type", "string")
                            put("description", "Full section markdown content (preserve existing [#ID] pointers) for update_section or add_section.")
                        }
                        putJsonObject("full_summary_markdown") {
                            put("type", "string")
                            put("description", "Complete replacement markdown (with ## Section headings and [#ID] citations). Only used for replace_summary.")
                        }
                        putJsonObject("confirm_full_replace") {
                            put("type", "boolean")
                            put("description", "Must be true when action is replace_summary.")
                        }
                    },
                    required = listOf("action"),
                ),
            )
        )
    }
    if (includeSearchTool) {
        add(
            LocalToolDefinitionPreview(
                name = MEMORY_SEARCH_TOOL_NAME,
                description = "Search this character's core memories and actually used past chat messages for a remembered topic, scene, person, feeling, or detail. The memory subagent expands the query internally and searches multiple variants.",
                schema = InputSchema.Obj(
                    properties = buildJsonObject {
                        put("query", buildJsonObject {
                            put("type", "string")
                            put("description", "The remembered topic, keyword, person, preference, event, or question to search for.")
                        })
                        put("limit", buildJsonObject {
                            put("type", "integer")
                            put("description", "Maximum number of memory results to return. Defaults to 5.")
                        })
                        put("time_range", buildJsonObject {
                            put("type", "string")
                            put("description", "Optional rough time span to filter recall, such as last week, this month, last month, 4 months ago, or yesterday.")
                        })
                    },
                    required = listOf("query"),
                ),
            )
        )
    }
}

internal data class PreRequestToolAccounting(
    val toolDefinitionText: String,
    val toolDefinitionTokens: Int,
    val embeddedToolText: String,
    val toolNames: Set<String>,
    val localAndSearchTokens: Int,
    val mcpTokens: Int,
    val workspaceTokens: Int,
    val localAndSearchCount: Int,
    val mcpCount: Int,
    val workspaceCount: Int,
)

internal fun computePreRequestToolAccounting(
    model: Model,
    assistant: Assistant,
    settings: Settings,
    includeMemoryTools: Boolean,
    availableSkills: List<Skill>,
    assistantDefaultSkillIds: Set<Uuid>,
    conversationSkillIds: Set<Uuid>,
    workspaceReady: Boolean,
    includePythonTools: Boolean = true,
): PreRequestToolAccounting {
    if (ModelAbility.TOOL !in model.abilities) {
        return PreRequestToolAccounting(
            toolDefinitionText = "",
            toolDefinitionTokens = 0,
            embeddedToolText = "",
            toolNames = emptySet(),
            localAndSearchTokens = 0,
            mcpTokens = 0,
            workspaceTokens = 0,
            localAndSearchCount = 0,
            mcpCount = 0,
            workspaceCount = 0,
        )
    }

    val seenNames = HashSet<String>()
    fun takeUnique(name: String): Boolean = seenNames.add(name)

    val localAndSearchPreviews = mutableListOf<LocalToolDefinitionPreview>()
    val embeddedPromptParts = mutableListOf<String>()

    // 1. Search tools
    val useBuiltInSearch = shouldUseBuiltInSearch(model, assistant)
    val searchMode = assistant.searchMode
    if (searchMode is AssistantSearchMode.Provider && !useBuiltInSearch) {
        val options = settings.searchServices.getOrElse(searchMode.index) { SearchServiceOptions.DEFAULT }
        val service = SearchService.getService(options)
        val baseSchema = service.parameters
        val searchSchema = if (baseSchema is InputSchema.Obj && model.inputModalities.contains(Modality.IMAGE)) {
            val props = (baseSchema.properties as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            if (!props.containsKey("include_images")) {
                props["include_images"] = buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to include relevant image results (URLs and titles) for the search query.")
                }
            }
            if (!props.containsKey("topic")) {
                props["topic"] = buildJsonObject {
                    put("type", "string")
                    put("description", "Optional intent hint: weather, news, sports, images, or general.")
                }
            }
            InputSchema.Obj(properties = JsonObject(props), required = baseSchema.required)
        } else {
            baseSchema
        }
        if (takeUnique("search_web")) {
            localAndSearchPreviews += LocalToolDefinitionPreview(
                name = "search_web",
                description = "Search the web for current information (news, weather, sports, facts, and images). Returns titles, URLs, snippets, dates, and optional inline images.",
                schema = searchSchema,
            )
            embeddedPromptParts += SEARCH_WEB_USAGE_SYSTEM_PROMPT
        }
        if (service.scrapingParameters != null && takeUnique("scrape_web")) {
            localAndSearchPreviews += LocalToolDefinitionPreview(
                name = "scrape_web",
                description = "scrape web for content",
                schema = service.scrapingParameters,
            )
            embeddedPromptParts += SCRAPE_WEB_SYSTEM_PROMPT
        }
    }

    // 2. Local tools + auto-injected TMDB
    val effectiveLocalTools = if (
        settings.tmdbSetting.enabled &&
        LocalToolOption.Tmdb !in assistant.localTools
    ) {
        assistant.localTools + LocalToolOption.Tmdb
    } else {
        assistant.localTools
    }
    effectiveLocalTools.flatMap { it.toDefinitionPreviews() }.forEach { preview ->
        if (takeUnique(preview.name)) {
            localAndSearchPreviews += preview
        }
    }

    // 3. Memory tools & Memory Summary tools
    if (includeMemoryTools) {
        val includeSearch = shouldRegisterMemorySearchTool(assistant)
        memoryToolDefinitionPreviews(
            includeSummaryTools = assistant.enableMemorySummary,
            includeSearchTool = includeSearch,
        ).forEach { preview ->
            if (takeUnique(preview.name)) {
                localAndSearchPreviews += preview
            }
        }
        if (includeSearch) {
            embeddedPromptParts += MEMORY_SEARCH_TOOL_SYSTEM_PROMPT
        }
    }

    // 4. Skill management tool
    val skillTool = createSkillManagementTool(
        state = buildSkillToolState(
            skills = availableSkills,
            assistantId = assistant.id,
            assistantDefaultSkillIds = assistantDefaultSkillIds,
            conversationSkillIds = conversationSkillIds,
            turnScopedSkillIds = emptySet(),
        ),
        currentTurnScopedSkillIds = emptySet(),
        automaticInvocationEnabled = assistant.enableAutomaticSkillInvocation,
        onUpdateTurnScopedSkillIds = {},
    )
    if (skillTool != null && takeUnique(skillTool.name)) {
        localAndSearchPreviews += LocalToolDefinitionPreview(
            name = skillTool.name,
            description = skillTool.description,
            schema = skillTool.parameters(),
        )
    }

    // 5. Workspace & Python extension tools
    val workspacePreviews = if (workspaceReady) {
        workspaceToolDefinitionPreviews(includePythonTools = includePythonTools)
            .filter { takeUnique(it.name) }
    } else {
        emptyList()
    }

    // 6. MCP server tools
    val activeMcpTools = settings.mcpServers
        .filter { server -> server.commonOptions.enable && server.id in assistant.mcpServers }
        .flatMap { server -> server.commonOptions.tools.filter { tool -> tool.enable } }
        .filter { tool -> takeUnique(tool.name) }

    val definitionText = buildString {
        localAndSearchPreviews.forEach { tool ->
            appendLine(ContextTokenEstimator.toolDefinitionText(tool.name, tool.description, tool.schema))
        }
        workspacePreviews.forEach { tool ->
            appendLine(ContextTokenEstimator.toolDefinitionText(tool.name, tool.description, tool.schema))
        }
        activeMcpTools.forEach { tool ->
            appendLine(ContextTokenEstimator.toolDefinitionText(tool.name, tool.description.orEmpty(), tool.inputSchema))
        }
    }

    val embeddedToolText = embeddedPromptParts.joinToString("\n")
    val embeddedPromptTokens = ContextTokenEstimator.textTokens(embeddedToolText, model)

    val localAndSearchDefTokens = localAndSearchPreviews.sumOf { tool ->
        ContextTokenEstimator.toolDefinitionTokens(tool.name, tool.description, tool.schema, model).toLong()
    }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val workspaceDefTokens = workspacePreviews.sumOf { tool ->
        ContextTokenEstimator.toolDefinitionTokens(tool.name, tool.description, tool.schema, model).toLong()
    }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val mcpDefTokens = activeMcpTools.sumOf { tool ->
        ContextTokenEstimator.toolDefinitionTokens(tool.name, tool.description.orEmpty(), tool.inputSchema, model).toLong()
    }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    val totalDefTokens = (localAndSearchDefTokens.toLong() + workspaceDefTokens + mcpDefTokens)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

    return PreRequestToolAccounting(
        toolDefinitionText = definitionText,
        toolDefinitionTokens = totalDefTokens,
        embeddedToolText = embeddedToolText,
        toolNames = seenNames,
        localAndSearchTokens = localAndSearchDefTokens + embeddedPromptTokens,
        mcpTokens = mcpDefTokens,
        workspaceTokens = workspaceDefTokens,
        localAndSearchCount = localAndSearchPreviews.size,
        mcpCount = activeMcpTools.size,
        workspaceCount = workspacePreviews.size,
    )
}

internal fun buildContextMapEntries(
    model: Model,
    assistant: Assistant,
    bundle: PromptInjectionBundle,
    memorySummaryTokens: Int,
    memoryTokens: Int,
    memoryCount: Int,
    summaryTokens: Int,
    enabledSkills: List<Skill>,
    skillsRoot: File?,
    activatedLorebookEntries: List<Pair<Lorebook, LorebookEntry>>,
    toolEntries: List<ContextMapEntry>,
    toolCallTokens: Int,
    conversationTokens: Int,
    conversationMessageCount: Int,
    mediaTokens: Int,
    imageCount: Int,
    isMemoryEstimated: Boolean = false,
): List<ContextMapEntry> = buildList {
    if (assistant.systemPrompt.isNotBlank()) {
        add(
            ContextMapEntry(
                category = ContextMapCategory.SYSTEM_PROMPT,
                id = "system_prompt",
                label = "System Prompt",
                subtitle = assistant.name.ifBlank { "Assistant instructions" },
                tokens = ContextTokenEstimator.textTokens(assistant.systemPrompt, model),
            )
        )
    }
    if (assistant.learningMode) {
        val learningText = bundle.systemPromptText.removePrefix(assistant.systemPrompt).trim()
        val tokens = ContextTokenEstimator.textTokens(learningText, model)
        if (tokens > 0) {
            add(
                ContextMapEntry(
                    category = ContextMapCategory.SYSTEM_PROMPT,
                    id = "learning_mode",
                    label = "Learning Mode",
                    subtitle = "Socratic teaching prompt",
                    tokens = tokens,
                )
            )
        }
    }

    bundle.activeInstructions.forEach { inst ->
        val instText = buildString {
            if (inst.name.isNotBlank()) appendLine("[Instruction: ${inst.name}]")
            appendLine(inst.content)
        }
        val tokens = ContextTokenEstimator.textTokens(instText, model)
        if (tokens > 0) {
            add(
                ContextMapEntry(
                    category = ContextMapCategory.CUSTOM_INSTRUCTIONS,
                    id = "instruction:${inst.id}",
                    label = inst.name.ifBlank { "Custom Instruction" },
                    subtitle = inst.content.trim().take(72).replace('\n', ' '),
                    tokens = tokens,
                )
            )
        }
    }

    if (memorySummaryTokens > 0) {
        add(
            ContextMapEntry(
                category = ContextMapCategory.MEMORY_SUMMARY,
                id = "memory_summary",
                label = "Memory Summary Profile",
                subtitle = "Hierarchical user profile",
                tokens = memorySummaryTokens,
            )
        )
    }

    if (memoryTokens > 0) {
        add(
            ContextMapEntry(
                category = ContextMapCategory.MEMORIES,
                id = "memories",
                label = "Long-Term Memories",
                subtitle = if (memoryCount > 0) "$memoryCount memories recalled" else "Memory tool context",
                tokens = memoryTokens,
                itemCount = memoryCount.takeIf { it > 0 },
                isEstimated = isMemoryEstimated,
            )
        )
    }

    if (summaryTokens > 0) {
        add(
            ContextMapEntry(
                category = ContextMapCategory.CONVERSATION_SUMMARY,
                id = "conversation_summary",
                label = "Conversation Summary",
                subtitle = "Compressed earlier turns",
                tokens = summaryTokens,
            )
        )
    }

    enabledSkills.forEach { skill ->
        val skillText = when (skill.injectionPosition) {
            InjectionPosition.BEFORE_SYSTEM,
            InjectionPosition.AFTER_SYSTEM -> formatSkillPromptText(skillsRoot, skill)
            else -> formatSkillChatMessageText(skill)
        }
        val tokens = ContextTokenEstimator.textTokens(skillText, model)
        if (tokens > 0) {
            add(
                ContextMapEntry(
                    category = ContextMapCategory.SKILLS_MODES,
                    id = "skill:${skill.id}",
                    label = skill.name.ifBlank { "Unnamed Skill" },
                    subtitle = skill.description.takeIf { it.isNotBlank() }?.take(64),
                    tokens = tokens,
                )
            )
        }
    }

    activatedLorebookEntries
        .groupBy { it.first }
        .forEach { (lorebook, pairs) ->
            val text = pairs.joinToString("\n") { it.second.prompt }
            val tokens = ContextTokenEstimator.textTokens(text, model)
            if (tokens > 0) {
                add(
                    ContextMapEntry(
                        category = ContextMapCategory.LOREBOOKS,
                        id = "lorebook:${lorebook.id}",
                        label = lorebook.name.ifBlank { "Lorebook" },
                        subtitle = "${pairs.size} active ${if (pairs.size == 1) "entry" else "entries"}",
                        tokens = tokens,
                        itemCount = pairs.size,
                    )
                )
            }
        }

    bundle.featurePromptSegments.forEach { segment ->
        val tokens = ContextTokenEstimator.textTokens(segment.text, model)
        if (tokens > 0) {
            add(
                ContextMapEntry(
                    category = ContextMapCategory.FEATURE_PROMPTS,
                    id = segment.id,
                    label = segment.label,
                    subtitle = segment.subtitle,
                    tokens = tokens,
                )
            )
        }
    }

    toolEntries.filterTo(this) { it.tokens > 0 }

    if (toolCallTokens > 0) {
        add(
            ContextMapEntry(
                category = ContextMapCategory.TOOL_CALLS,
                id = "tool_calls",
                label = "Tool Calls & Results",
                subtitle = "In-chat tool invocations & outputs",
                tokens = toolCallTokens,
            )
        )
    }

    if (conversationTokens > 0) {
        add(
            ContextMapEntry(
                category = ContextMapCategory.CONVERSATION,
                id = "conversation",
                label = "Conversation History",
                subtitle = "$conversationMessageCount retained ${if (conversationMessageCount == 1) "message" else "messages"}",
                tokens = conversationTokens,
                itemCount = conversationMessageCount,
            )
        )
    }

    if (mediaTokens > 0) {
        add(
            ContextMapEntry(
                category = ContextMapCategory.MEDIA,
                id = "media",
                label = "Images & Attachments",
                subtitle = if (imageCount > 0) "$imageCount ${if (imageCount == 1) "image" else "images"} in context" else "Active media attachments",
                tokens = mediaTokens,
                itemCount = imageCount.takeIf { it > 0 },
            )
        )
    }
}
