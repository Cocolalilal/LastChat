package me.rerere.ai.generation

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.prompt.LorebookActivationKind
import me.rerere.rikkahub.data.prompt.PortableLorebook
import me.rerere.rikkahub.data.prompt.PortableLorebookEntry
import me.rerere.rikkahub.data.prompt.PortableSkill
import me.rerere.rikkahub.data.prompt.PromptInjectionPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PortableGenerationPrepareTest {
    private val chatModel = Model(
        modelId = "test-chat",
        displayName = "Test Chat",
        type = ModelType.CHAT,
        abilities = listOf(ModelAbility.TOOL),
        inputModalities = listOf(Modality.TEXT),
        outputModalities = listOf(Modality.TEXT),
    )

    @Test
    fun lorebookAndSkillInjectIntoSharedSystemPrompt() = runBlocking {
        val result = PortableGenerationPrepare.prepare(
            PortablePrepareRequest(
                messages = listOf(UIMessage.user("We visited the castle")),
                model = chatModel,
                tools = emptyList(),
                assistant = PortablePrepareAssistant(
                    id = "asst",
                    systemPrompt = "You are LastChat.",
                    smartContextManagement = false,
                    enabledLorebookIds = setOf("book"),
                    enabledSkillIds = setOf("guide"),
                ),
                skills = listOf(
                    PortableSkill(
                        id = "guide",
                        name = "Guide",
                        instructions = "Speak like a tour guide.",
                        injectionPosition = PromptInjectionPosition.AFTER_SYSTEM,
                    ),
                ),
                lorebooks = listOf(
                    PortableLorebook(
                        id = "book",
                        name = "Places",
                        entries = listOf(
                            PortableLorebookEntry(
                                id = "glass",
                                name = "Glass castle",
                                prompt = "The castle is made of glass.",
                                activationType = LorebookActivationKind.ALWAYS,
                            ),
                        ),
                    ),
                ),
            ),
        )
        val system = result.providerMessages.first { it.role == MessageRole.SYSTEM }.toText()
        assertTrue(system.contains("You are LastChat."))
        assertTrue(system.contains("Speak like a tour guide."))
        assertTrue(system.contains("The castle is made of glass."))
        assertEquals("Always Active", result.usedLorebookEntries.single().activationReason)
        assertEquals("Enabled for assistant", result.usedModes.single().activationReason)
    }

    @Test
    fun topOfChatSkillBecomesInContextUserMessage() = runBlocking {
        val result = PortableGenerationPrepare.prepare(
            PortablePrepareRequest(
                messages = listOf(
                    UIMessage.user("older"),
                    UIMessage.assistant("reply"),
                    UIMessage.user("latest"),
                ),
                model = chatModel,
                tools = emptyList(),
                assistant = PortablePrepareAssistant(
                    id = "asst",
                    systemPrompt = "Base",
                    smartContextManagement = false,
                    enabledSkillIds = setOf("top"),
                ),
                skills = listOf(
                    PortableSkill(
                        id = "top",
                        name = "Top",
                        instructions = "top-body",
                        injectionPosition = PromptInjectionPosition.TOP_OF_CHAT,
                    ),
                ),
            ),
        )
        val injected = result.providerMessages.first { it.toText().contains("top-body") }
        assertEquals(MessageRole.USER, injected.role)
        assertTrue(injected.toText().contains("[Skill: Top]"))
    }

    @Test
    fun documentTransformerUsesPortableDocumentText() = runBlocking {
        val document = UIMessagePart.Document(
            url = "memory:notes.txt",
            fileName = "notes.txt",
            mime = "text/plain",
        )
        val result = PortableGenerationPrepare.prepare(
            PortablePrepareRequest(
                messages = listOf(UIMessage(role = MessageRole.USER, parts = listOf(document))),
                model = chatModel,
                tools = emptyList(),
                assistant = PortablePrepareAssistant(
                    id = "asst",
                    smartContextManagement = false,
                ),
                transformers = defaultPortableInputTransformers(),
                transformerContext = PortableTransformerContext(
                    model = chatModel,
                    documentRuntime = bytesDocumentRuntime(
                        readBytes = { url ->
                            if (url == "memory:notes.txt") "Hello from the file".encodeToByteArray() else null
                        },
                    ),
                ),
            ),
        )
        val text = result.providerMessages.joinToString("\n") { it.toText() }
        assertTrue(text.contains("## user sent a file: notes.txt"))
        assertTrue(text.contains("Hello from the file"))
    }

    @Test
    fun ocrTransformerRewritesImagesWhenModelHasNoVision() = runBlocking {
        val result = PortableGenerationPrepare.prepare(
            PortablePrepareRequest(
                messages = listOf(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(UIMessagePart.Image(url = "file://shot.png")),
                    ),
                ),
                model = chatModel,
                tools = emptyList(),
                assistant = PortablePrepareAssistant(id = "asst", smartContextManagement = false),
                transformers = defaultPortableInputTransformers(),
                transformerContext = PortableTransformerContext(
                    model = chatModel,
                    ocrRuntime = PortableOcrRuntime { "Printed sign: CLOSED" },
                ),
            ),
        )
        val text = result.providerMessages.joinToString("\n") { it.toText() }
        assertTrue(text.contains("<image_file_ocr>"))
        assertTrue(text.contains("Printed sign: CLOSED"))
        assertFalse(
            result.providerMessages.any { message ->
                message.parts.any { it is UIMessagePart.Image }
            },
        )
    }

    @Test
    fun placeholderTransformerSubstitutesHostValues() {
        val replaced = PortablePlaceholderTransformer.replacePlaceholders(
            "Hello {{char}} from {model}",
            mapOf("char" to "Aria", "model" to "gpt"),
        )
        assertEquals("Hello Aria from gpt", replaced)
    }
}

class PortableToolAssemblerTest {
    private val toolModel = Model(
        modelId = "tool-model",
        displayName = "Tool Model",
        type = ModelType.CHAT,
        abilities = listOf(ModelAbility.TOOL),
    )

    @Test
    fun assemblerBuildsMemorySkillsAndMcpFromRuntimes() = runBlocking {
        val created = mutableListOf<String>()
        val turnSkills = mutableSetOf<String>()
        val tools = assemblePortableTools(
            options = PortableToolAssemblyOptions(
                model = toolModel,
                includeSearch = true,
                includeMemory = true,
                includeSkills = true,
            ),
            runtimes = PortableToolRuntimes(
                searchTool = Tool(
                    name = SEARCH_WEB_TOOL_NAME,
                    description = "Search",
                    execute = { JsonPrimitive("ok") },
                ),
                memory = PortableMemoryToolRuntime(
                    onCreate = { content ->
                        created += content
                        buildJsonObject { put("id", 1); put("content", content) }
                    },
                    onUpdate = { id, content ->
                        buildJsonObject { put("id", id); put("content", content) }
                    },
                    onDelete = { id -> buildJsonObject { put("deleted", id) } },
                ),
                skills = PortableSkillToolBinding(
                    skills = listOf(
                        PortableSkill(
                            id = "research",
                            name = "Research",
                            description = "Look things up",
                            instructions = "search first",
                        ),
                    ),
                    assistantId = "asst",
                    assistantDefaultSkillIds = emptySet(),
                    conversationSkillIds = emptySet(),
                    turnScopedSkillIds = emptySet(),
                    onUpdateTurnScopedSkillIds = { turnSkills.clear(); turnSkills.addAll(it) },
                ),
                mcpTools = listOf(
                    PortableMcpToolBinding(
                        name = "mcp_ping",
                        description = "Ping",
                        execute = { buildJsonObject { put("pong", true) } },
                    ),
                ),
            ),
        )
        assertEquals(
            listOf("search_web", "create_memory", "edit_memory", "delete_memory", "manage_skills", "mcp_ping"),
            tools.map { it.name },
        )
        val skillTool = tools.first { it.name == SKILL_MANAGEMENT_TOOL_NAME }
        val outcome = skillTool.execute(
            buildJsonObject {
                put("skills", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("Research"))))
            },
        ).jsonObject
        assertEquals("research", outcome["activated"]?.jsonArray?.first()?.jsonObject?.get("id")?.jsonPrimitive?.content)
        assertEquals(setOf("research"), turnSkills)

        val createdJson = tools.first { it.name == CREATE_MEMORY_TOOL_NAME }
            .execute(buildJsonObject { put("content", "User likes tea") })
            .jsonObject
        assertEquals("User likes tea", createdJson["content"]?.jsonPrimitive?.contentOrNull)
        assertEquals(listOf("User likes tea"), created)
    }

    @Test
    fun uniqueNamesKeepStableSuffixes() {
        val first = Tool(name = "search", description = "one", execute = { JsonPrimitive("one") })
        val second = Tool(name = "search", description = "two", execute = { JsonPrimitive("two") })
        val third = Tool(name = "search", description = "three", execute = { JsonPrimitive("three") })
        val unique = listOf(first, second, third).withUniqueToolNames()
        assertEquals(listOf("search", "search__2", "search__3"), unique.map { it.name })
    }

    @Test
    fun manageSkillsOmitsWhenAutomaticInvocationDisabled() {
        val tool = createPortableManageSkillsTool(
            binding = PortableSkillToolBinding(
                skills = listOf(
                    PortableSkill(id = "a", name = "A", instructions = "do a", description = "A"),
                ),
                assistantId = "asst",
                assistantDefaultSkillIds = emptySet(),
                conversationSkillIds = emptySet(),
                turnScopedSkillIds = emptySet(),
                onUpdateTurnScopedSkillIds = {},
            ),
            automaticInvocationEnabled = false,
        )
        assertEquals(null, tool)
    }

    @Test
    fun noToolsWhenModelLacksToolAbility() {
        val model = Model(
            modelId = "plain",
            displayName = "Plain",
            type = ModelType.CHAT,
        )
        val tools = assemblePortableTools(
            options = PortableToolAssemblyOptions(model = model, includeMemory = true),
            runtimes = PortableToolRuntimes(
                memory = PortableMemoryToolRuntime(
                    onCreate = { JsonPrimitive(it) },
                    onUpdate = { _, content -> JsonPrimitive(content) },
                    onDelete = { JsonPrimitive(it) },
                ),
            ),
        )
        assertTrue(tools.isEmpty())
    }
}
