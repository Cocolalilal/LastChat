package me.rerere.ai.generation

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.contextCapacityTokens
import me.rerere.ai.workspace.createPortableWorkspaceTools
import me.rerere.common.runtime.OnDeviceWorkspaceRuntime
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.prompt.PromptInjectionEngine

const val SKILL_MANAGEMENT_TOOL_NAME = "manage_skills"
const val SEARCH_WEB_TOOL_NAME = "search_web"
const val CREATE_MEMORY_TOOL_NAME = "create_memory"
const val EDIT_MEMORY_TOOL_NAME = "edit_memory"
const val DELETE_MEMORY_TOOL_NAME = "delete_memory"
const val SEARCH_MEMORY_TOOL_NAME = "search_memory"

data class PortableToolAssemblyOptions(
    val model: Model,
    val includeSearch: Boolean = false,
    val includeMemory: Boolean = false,
    val includeMemorySearch: Boolean = false,
    val includeWorkspace: Boolean = false,
    val includeSkills: Boolean = false,
    val automaticSkillInvocation: Boolean = true,
)

data class PortableMemoryToolRuntime(
    val onCreate: suspend (String) -> JsonElement,
    val onUpdate: suspend (Int, String) -> JsonElement,
    val onDelete: suspend (Int) -> JsonElement,
    val onSearch: (suspend (String, Int, String?) -> JsonElement)? = null,
)

data class PortableSkillToolBinding(
    val skills: List<PortableSkill>,
    val assistantId: String,
    val assistantDefaultSkillIds: Set<String>,
    val conversationSkillIds: Set<String>,
    val turnScopedSkillIds: Set<String>,
    val onUpdateTurnScopedSkillIds: suspend (Set<String>) -> Unit,
)

data class PortableMcpToolBinding(
    val name: String,
    val description: String,
    val inputSchema: InputSchema? = null,
    val execute: suspend (JsonObject) -> JsonElement,
)

data class PortableToolRuntimes(
    val searchTool: Tool? = null,
    val localTools: List<Tool> = emptyList(),
    val workspaceRuntime: OnDeviceWorkspaceRuntime? = null,
    val workspaceApprovals: Map<String, Boolean> = emptyMap(),
    val workspaceCwd: String? = null,
    val mcpTools: List<PortableMcpToolBinding> = emptyList(),
    val memory: PortableMemoryToolRuntime? = null,
    val skills: PortableSkillToolBinding? = null,
    val extraTools: List<Tool> = emptyList(),
)

/**
 * Single tool-list builder used by Android ChatService and iOS IosAppController.
 * Hosts inject platform runtimes (PRoot, MCP transport, JS, TTS) and must not
 * each hand-roll the list order or uniqueness rules.
 */
fun assemblePortableTools(
    options: PortableToolAssemblyOptions,
    runtimes: PortableToolRuntimes,
): List<Tool> {
    if (ModelAbility.TOOL !in options.model.abilities) return emptyList()
    return buildList {
        if (options.includeSearch) {
            runtimes.searchTool?.let(::add)
        }
        if (options.includeMemory) {
            runtimes.memory?.let { memory ->
                addAll(createPortableMemoryTools(memory, options.includeMemorySearch))
            }
        }
        addAll(runtimes.localTools)
        if (options.includeSkills) {
            runtimes.skills?.let { binding ->
                createPortableManageSkillsTool(
                    binding = binding,
                    automaticInvocationEnabled = options.automaticSkillInvocation,
                )?.let(::add)
            }
        }
        if (options.includeWorkspace) {
            runtimes.workspaceRuntime?.let { runtime ->
                addAll(
                    createPortableWorkspaceTools(
                        runtime = runtime,
                        approvalOverrides = runtimes.workspaceApprovals,
                        cwd = runtimes.workspaceCwd,
                    ),
                )
            }
        }
        val mcpLimit = (
            (options.model.contextCapacityTokens?.toLong() ?: 16_000L) * 2L
            ).coerceIn(2_000L, 32_000L).toInt()
        runtimes.mcpTools.forEach { binding ->
            add(
                Tool(
                    name = binding.name,
                    description = binding.description,
                    parameters = { binding.inputSchema },
                    execute = { arguments ->
                        val payload = arguments as? JsonObject ?: arguments.jsonObject
                        truncateLargeJsonText(binding.execute(payload), mcpLimit)
                    },
                ),
            )
        }
        addAll(runtimes.extraTools)
    }.withUniqueToolNames()
}

fun createPortableMemoryTools(
    runtime: PortableMemoryToolRuntime,
    includeSearch: Boolean = false,
): List<Tool> = buildList {
    add(
        Tool(
            name = CREATE_MEMORY_TOOL_NAME,
            description = "Create a new memory record.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("content", buildJsonObject {
                            put("type", "string")
                            put("description", "Content of the memory.")
                        })
                    },
                    required = listOf("content"),
                )
            },
            execute = { arguments ->
                val content = arguments.jsonObject["content"]?.jsonPrimitive?.contentOrNull
                    ?: error("content is required")
                runtime.onCreate(content)
            },
        ),
    )
    add(
        Tool(
            name = EDIT_MEMORY_TOOL_NAME,
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
            execute = { arguments ->
                val params = arguments.jsonObject
                val id = params["id"]?.jsonPrimitive?.intOrNull ?: error("id is required")
                val content = params["content"]?.jsonPrimitive?.contentOrNull
                    ?: error("content is required")
                runtime.onUpdate(id, content)
            },
        ),
    )
    add(
        Tool(
            name = DELETE_MEMORY_TOOL_NAME,
            description = "Delete an outdated memory record.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("id", buildJsonObject {
                            put("type", "integer")
                            put("description", "ID of the memory to delete.")
                        })
                    },
                    required = listOf("id"),
                )
            },
            execute = { arguments ->
                val id = arguments.jsonObject["id"]?.jsonPrimitive?.intOrNull
                    ?: error("id is required")
                runtime.onDelete(id)
            },
        ),
    )
    if (includeSearch) {
        val search = runtime.onSearch
        if (search != null) {
            add(
                Tool(
                    name = SEARCH_MEMORY_TOOL_NAME,
                    description = "Search stored memories and past chats.",
                    parameters = {
                        InputSchema.Obj(
                            properties = buildJsonObject {
                                put("query", buildJsonObject {
                                    put("type", "string")
                                    put("description", "Search query")
                                })
                                put("limit", buildJsonObject {
                                    put("type", "integer")
                                    put("description", "Maximum results to return")
                                })
                                put("time_range", buildJsonObject {
                                    put("type", "string")
                                    put("description", "Optional time range filter")
                                })
                            },
                            required = listOf("query"),
                        )
                    },
                    execute = { arguments ->
                        val params = arguments.jsonObject
                        val query = params["query"]?.jsonPrimitive?.contentOrNull
                            ?: error("query is required")
                        val limit = params["limit"]?.jsonPrimitive?.intOrNull ?: 10
                        val timeRange = params["time_range"]?.jsonPrimitive?.contentOrNull
                        search(query, limit, timeRange)
                    },
                ),
            )
        }
    }
}

fun createPortableManageSkillsTool(
    binding: PortableSkillToolBinding,
    automaticInvocationEnabled: Boolean = true,
): Tool? {
    if (!automaticInvocationEnabled) return null
    val state = PromptInjectionEngine.skillToolState(
        skills = binding.skills,
        assistantId = binding.assistantId,
        assistantDefaultSkillIds = binding.assistantDefaultSkillIds,
        conversationSkillIds = binding.conversationSkillIds,
        turnScopedSkillIds = binding.turnScopedSkillIds,
    )
    if (state.availableSkills.isEmpty()) return null
    val availableSummary = state.availableSkills.joinToString("; ") { skill ->
        val label = skill.name.ifBlank { skill.id }
        val description = skill.description.ifBlank { "No description provided." }
            .replace('\n', ' ')
            .take(120)
        val compatibility = skill.compatibility?.takeIf { it.isNotBlank() }
            ?.let { " (Requires: ${it.replace('\n', ' ').take(64)})" }
            .orEmpty()
        "$label: $description$compatibility"
    }
    return Tool(
        name = SKILL_MANAGEMENT_TOOL_NAME,
        description = "Activate relevant skills for this turn; their full instructions load on the next step. Available: $availableSummary",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    put("skills", buildJsonObject {
                        put("type", "array")
                        put("description", "Skills to activate for this turn, referenced by exact id or exact skill name.")
                        put("items", buildJsonObject { put("type", "string") })
                    })
                },
                required = listOf("skills"),
            )
        },
        execute = { args ->
            val objectValue = args as? JsonObject ?: JsonObject(emptyMap())
            val listed = (objectValue["skills"] as? JsonArray)?.mapNotNull { item ->
                (item as? JsonPrimitive)?.contentOrNull
            }.orEmpty()
            val single = (objectValue["skill"] as? JsonPrimitive)?.contentOrNull
            val targets = PromptInjectionEngine.parseSkillTargets(listed, single)
            require(targets.isNotEmpty()) { "Provide at least one skill target in `skills` or `skill`." }
            val outcome = PromptInjectionEngine.activateSkillsForTurn(
                targets = targets,
                availableSkills = state.availableSkills,
                activeSkills = state.activeSkills,
                currentTurnScopedSkillIds = binding.turnScopedSkillIds,
            )
            binding.onUpdateTurnScopedSkillIds(outcome.updatedTurnScopedSkillIds)
            buildJsonObject {
                put("updated", JsonPrimitive(outcome.activatedSkills.isNotEmpty()))
                put(
                    "activated",
                    JsonArray(
                        outcome.activatedSkills.map { skill ->
                            buildJsonObject {
                                put("id", skill.id)
                                put("name", skill.name)
                            }
                        },
                    ),
                )
                put(
                    "already_active",
                    JsonArray(
                        outcome.alreadyActiveSkills.map { skill ->
                            buildJsonObject {
                                put("id", skill.id)
                                put("name", skill.name)
                            }
                        },
                    ),
                )
                put("unmatched", JsonArray(outcome.unmatchedTargets.map(::JsonPrimitive)))
            }
        },
    )
}

fun List<Tool>.withUniqueToolNames(): List<Tool> {
    val occurrences = mutableMapOf<String, Int>()
    return map { tool ->
        val occurrence = (occurrences[tool.name] ?: 0) + 1
        occurrences[tool.name] = occurrence
        if (occurrence == 1) {
            tool
        } else {
            val suffix = "__$occurrence"
            tool.copy(name = tool.name.take((64 - suffix.length).coerceAtLeast(1)) + suffix)
        }
    }
}

fun truncateLargeJsonText(
    element: JsonElement,
    maxLength: Int = 32_000,
): JsonElement {
    var remaining = maxLength.coerceAtLeast(256)
    fun compact(node: JsonElement): JsonElement = when (node) {
        is JsonPrimitive -> {
            val content = node.content
            if (remaining <= 0) {
                JsonPrimitive("[Additional MCP result content omitted]")
            } else if (!node.isString || content.length <= remaining) {
                remaining = (remaining - content.length).coerceAtLeast(0)
                node
            } else {
                val kept = remaining
                remaining = 0
                JsonPrimitive(content.take(kept) + "... [MCP result compacted]")
            }
        }
        is JsonObject -> JsonObject(node.mapValues { compact(it.value) })
        is JsonArray -> JsonArray(node.map(::compact))
    }
    return compact(element)
}
