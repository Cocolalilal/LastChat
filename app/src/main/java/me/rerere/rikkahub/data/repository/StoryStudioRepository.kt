package me.rerere.rikkahub.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.ProviderManager
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getAssistantById
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.extractJsonPayload
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlin.uuid.Uuid

private const val TAG = "StoryStudioRepository"

class StoryStudioRepository(
    private val context: Context,
    private val workspaceDao: WorkspaceDAO,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val secretKeyManager: SecretKeyManager,
    private val memoryRepository: MemoryRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _projectsFlow = MutableStateFlow<List<StoryProject>>(emptyList())
    val projectsFlow: StateFlow<List<StoryProject>> = _projectsFlow.asStateFlow()

    init {
        refresh()
    }

    fun isProjectGenerating(projectId: String): Boolean {
        return activeJobs.containsKey(projectId)
    }

    fun cancelGeneration(projectId: String) {
        val job = activeJobs.remove(projectId)
        job?.cancel()
        scope.launch {
            val project = _projectsFlow.value.firstOrNull { it.id == projectId } ?: return@launch
            val updatedBeats = project.bible?.beats?.map { beat ->
                if (beat.status == BeatStatus.Writing) {
                    beat.copy(status = BeatStatus.Queued)
                } else beat
            }
            val updatedProject = project.copy(
                status = if (project.bible == null) StoryStatus.Outlining else StoryStatus.Paused,
                bible = project.bible?.copy(beats = updatedBeats ?: emptyList()),
                lastError = "Generation cancelled by user",
                updatedAt = System.currentTimeMillis()
            )
            saveProject(updatedProject)
        }
    }

    fun getStoriesDir(): File {
        val dir = File(context.filesDir, "stories")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun refresh() {
        scope.launch {
            _projectsFlow.value = loadProjects()
        }
    }

    private suspend fun loadProjects(): List<StoryProject> = withContext(Dispatchers.IO) {
        val dir = getStoriesDir()
        val files = dir.listFiles { f -> f.isDirectory } ?: return@withContext emptyList()

        files.mapNotNull { projectDir ->
            val jsonFile = File(projectDir, "project.json")
            if (jsonFile.exists()) {
                runCatching {
                    JsonInstant.decodeFromString<StoryProject>(jsonFile.readText())
                }.getOrNull()
            } else null
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun createProject(
        title: String,
        premise: String,
        genre: String,
        tone: String,
        architectModelId: Uuid?,
        proseModelId: Uuid?,
    ): StoryProject = withContext(Dispatchers.IO) {
        val id = Uuid.random().toString()
        val project = StoryProject(
            id = id,
            title = title.ifBlank { "Untitled Story" },
            premise = premise,
            genre = genre,
            tone = tone,
            status = StoryStatus.Outlining,
            currentBeatIndex = 0,
            architectModelId = architectModelId,
            proseModelId = proseModelId,
        )
        saveProject(project)
        refresh()
        project
    }

    suspend fun saveProject(project: StoryProject) = withContext(Dispatchers.IO) {
        val dir = File(getStoriesDir(), project.id)
        if (!dir.exists()) dir.mkdirs()

        val jsonFile = File(dir, "project.json")
        jsonFile.writeText(JsonInstant.encodeToString(project))

        // Write manuscript markdown
        val mdFile = File(dir, "manuscript.md")
        val md = buildString {
            appendLine("# ${project.title}")
            appendLine("Genre: ${project.genre} • Tone: ${project.tone}")
            appendLine()
            project.bible?.beats?.forEach { beat ->
                appendLine("## Beat ${beat.index + 1}: ${beat.title}")
                if (beat.content.isNotBlank()) {
                    appendLine(beat.content)
                    appendLine()
                }
            }
        }
        mdFile.writeText(md)

        syncToWorkspace(project)
        refresh()
    }

    suspend fun deleteProject(id: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(getStoriesDir(), id)
        val success = dir.deleteRecursively()

        runCatching {
            val wsStoriesDir = getWorkspaceStoriesDir()
            if (wsStoriesDir != null) {
                File(wsStoriesDir, id).deleteRecursively()
                updateWorkspaceIndex(wsStoriesDir)
            }
        }

        refresh()
        success
    }

    suspend fun generateStoryBible(projectId: String): Result<StoryProject> = withContext(Dispatchers.IO) {
        val project = _projectsFlow.value.firstOrNull { it.id == projectId }
            ?: return@withContext Result.failure(IllegalStateException("Story $projectId not found"))

        // Set status to Outlining and clear old error
        saveProject(project.copy(status = StoryStatus.Outlining, lastError = null))

        val currentJob = coroutineContext[Job]
        if (currentJob != null) {
            activeJobs[projectId] = currentJob
        }

        try {
            val result = runCatching {
                val settings = settingsStore.settingsFlow.value
                val modelId = project.architectModelId ?: settings.chatModelId
                val model = settings.findModelById(modelId)
                    ?: throw IllegalStateException("No valid architect model found. Please select a model.")
                val rawProv = model.findProvider(settings.providers)
                    ?: throw IllegalStateException("Provider not found for architect model ${model.displayName}")
                val provider = secretKeyManager.populateProviderSecrets(rawProv)
                val handler = providerManager.getProviderByType(provider)

                val prompt = """
                    You are a master story architect and worldbuilder.
                    Create a rich Story Bible and Milestone Beat Outline for the following story idea.
                    
                    Title: ${project.title}
                    Genre: ${project.genre.ifBlank { "Original Fiction" }}
                    Tone: ${project.tone.ifBlank { "Engaging" }}
                    Premise: ${project.premise}
                    
                    Guidelines:
                    - Keep characters concise (up to 4 characters, 1-2 sentences description & motive each).
                    - Keep world rules to 2-3 concise points.
                    - Output exactly 4 milestone beats.
                    - Be direct so the full JSON is completed without truncation.
                    
                    Respond ONLY with a valid JSON object matching this exact schema:
                    {
                      "style_guidelines": "Core narrative voice and style rules",
                      "characters": [
                        { "name": "Character Name", "role": "Protagonist/Antagonist/Ally", "description": "Visual and personality details", "motive": "What they desire or fear" }
                      ],
                      "world_rules": [
                        "Rule or key lore detail 1",
                        "Rule or key lore detail 2"
                      ],
                      "beats": [
                        { "index": 0, "title": "Milestone Title", "objective": "What happens in this milestone scene", "target_words": 1000 },
                        { "index": 1, "title": "Milestone Title", "objective": "What happens in this milestone scene", "target_words": 1200 },
                        { "index": 2, "title": "Milestone Title", "objective": "What happens in this milestone scene", "target_words": 1000 },
                        { "index": 3, "title": "Milestone Title", "objective": "What happens in this milestone scene", "target_words": 1200 }
                      ]
                    }
                """.trimIndent()

                val resp = handler.generateText(
                    providerSetting = provider,
                    messages = listOf(UIMessage.user(prompt)),
                    params = TextGenerationParams(
                        model = model,
                        temperature = 0.5f,
                        maxTokens = 4000
                    )
                )

                val text = resp.choices.firstOrNull()?.message?.toContentText().orEmpty()
                if (text.isBlank()) {
                    throw IllegalStateException("Architect model returned empty response")
                }

                val cleanJson = extractJsonPayload(text)
                val json = JSONObject(cleanJson)

                val style = json.optString("style_guidelines", "Visceral narrative with high momentum.")
                val charsArray = json.optJSONArray("characters") ?: JSONArray()
                val characters = (0 until charsArray.length()).map { i ->
                    val c = charsArray.getJSONObject(i)
                    StoryCharacter(
                        name = c.optString("name", "Character ${i + 1}"),
                        role = c.optString("role", "Protagonist"),
                        description = c.optString("description", ""),
                        motive = c.optString("motive", "")
                    )
                }

                val rulesArray = json.optJSONArray("world_rules") ?: JSONArray()
                val rules = (0 until rulesArray.length()).map { rulesArray.getString(it) }

                val beatsArray = json.optJSONArray("beats") ?: JSONArray()
                val beats = if (beatsArray.length() > 0) {
                    (0 until beatsArray.length()).map { i ->
                        val b = beatsArray.getJSONObject(i)
                        StoryBeat(
                            index = i,
                            title = b.optString("title", "Beat ${i + 1}"),
                            objective = b.optString("objective", "Advance the plot"),
                            targetWords = b.optInt("target_words", 1000),
                            status = if (i == 0) BeatStatus.Writing else BeatStatus.Queued
                        )
                    }
                } else {
                    listOf(
                        StoryBeat(0, "The Inciting Incident", "Introduction to the characters and setup for: ${project.premise.take(120)}", 1000, status = BeatStatus.Writing),
                        StoryBeat(1, "Rising Stakes", "Complications arise and loyalties are tested.", 1200, status = BeatStatus.Queued),
                        StoryBeat(2, "The Turning Point", "A major turning point or crisis shifts the balance.", 1200, status = BeatStatus.Queued),
                        StoryBeat(3, "The Climax & Resolution", "The climax of the story premise and final aftermath.", 1000, status = BeatStatus.Queued),
                    )
                }

                val bible = StoryBible(
                    title = project.title,
                    premise = project.premise,
                    genre = project.genre.ifBlank { "Fiction" },
                    tone = project.tone.ifBlank { "Engaging" },
                    styleGuidelines = style,
                    characters = characters,
                    worldRules = rules,
                    beats = beats,
                )

                val updatedProject = project.copy(
                    status = StoryStatus.Writing,
                    bible = bible,
                    lastError = null,
                    updatedAt = System.currentTimeMillis()
                )
                saveProject(updatedProject)
                updatedProject
            }

            result.onFailure { err ->
                val failedProject = project.copy(
                    lastError = err.message ?: "Failed to generate Story Bible",
                    updatedAt = System.currentTimeMillis()
                )
                saveProject(failedProject)
            }

            result
        } finally {
            activeJobs.remove(projectId)
        }
    }

    suspend fun generateBeatProse(
        projectId: String,
        beatIndex: Int,
        steerNote: String? = null,
        onChunk: (String) -> Unit = {}
    ): Result<StoryBeat> = withContext(Dispatchers.IO) {
        val project = _projectsFlow.value.firstOrNull { it.id == projectId }
            ?: return@withContext Result.failure(IllegalStateException("Story not found"))
        val bible = project.bible
            ?: return@withContext Result.failure(IllegalStateException("Story Bible not generated yet"))
        val beat = bible.beats.getOrNull(beatIndex)
            ?: return@withContext Result.failure(IllegalStateException("Beat $beatIndex not found"))

        // Set beat to Writing and clear error
        val writingBeat = beat.copy(status = BeatStatus.Writing, errorMessage = null)
        val writingBeats = bible.beats.map { if (it.index == beatIndex) writingBeat else it }
        saveProject(project.copy(bible = bible.copy(beats = writingBeats), lastError = null))

        val currentJob = coroutineContext[Job]
        if (currentJob != null) {
            activeJobs[projectId] = currentJob
        }

        try {
            val result = runCatching {
                val settings = settingsStore.settingsFlow.value
                val modelId = project.proseModelId ?: settings.chatModelId
                val model = settings.findModelById(modelId)
                    ?: throw IllegalStateException("No valid prose model found")
                val rawProv = model.findProvider(settings.providers)
                    ?: throw IllegalStateException("Provider not found for prose model ${model.displayName}")
                val provider = secretKeyManager.populateProviderSecrets(rawProv)
                val handler = providerManager.getProviderByType(provider)

                // Context: Previous beats summary
                val previousBeatsSummary = bible.beats.take(beatIndex).mapIndexed { i, b ->
                    "Beat ${i + 1} (${b.title}): ${b.summary.ifBlank { b.objective }}"
                }.joinToString("\n")

                // Context: Last 500 words of preceding beat
                val lastWords = if (beatIndex > 0) {
                    val prevContent = bible.beats[beatIndex - 1].content
                    prevContent.split("\\s+".toRegex()).takeLast(400).joinToString(" ")
                } else ""

                val charactersContext = bible.characters.joinToString("\n") {
                    "- ${it.name} (${it.role}): ${it.description}. Motive: ${it.motive}"
                }

                val prompt = buildString {
                    appendLine("You are the novelist writing Beat ${beatIndex + 1}: \"${beat.title}\".")
                    appendLine("Genre: ${project.genre} • Tone: ${project.tone}")
                    appendLine("Style Guidelines: ${bible.styleGuidelines}")
                    appendLine()
                    appendLine("### Characters")
                    appendLine(charactersContext)
                    appendLine()
                    if (bible.worldRules.isNotEmpty()) {
                        appendLine("### Setting Rules")
                        bible.worldRules.forEach { appendLine("- $it") }
                        appendLine()
                    }
                    if (previousBeatsSummary.isNotBlank()) {
                        appendLine("### Narrative So Far")
                        appendLine(previousBeatsSummary)
                        appendLine()
                    }
                    if (lastWords.isNotBlank()) {
                        appendLine("### Previous Text Excerpt (Resume directly from here)")
                        appendLine("...$lastWords")
                        appendLine()
                    }
                    appendLine("### Current Beat Objective")
                    appendLine(beat.objective)
                    appendLine("Target word count: approx ${beat.targetWords} words.")
                    if (!steerNote.isNullOrBlank()) {
                        appendLine("Director Note: $steerNote")
                    }
                    appendLine()
                    appendLine("Write rich, visceral, fully realized prose. Do NOT skip or rush ahead. Write the scene in full detail.")
                }

                val proseBuilder = StringBuilder()
                handler.streamText(
                    providerSetting = provider,
                    messages = listOf(UIMessage.user(prompt)),
                    params = TextGenerationParams(
                        model = model,
                        temperature = 0.7f,
                        maxTokens = 3500
                    )
                ).collect { chunk ->
                    val delta = chunk.choices.firstOrNull()?.delta?.toContentText().orEmpty()
                    if (delta.isNotEmpty()) {
                        proseBuilder.append(delta)
                        onChunk(delta)
                    }
                }

                val fullText = proseBuilder.toString()
                if (fullText.isBlank()) {
                    throw IllegalStateException("Model returned empty prose for beat ${beatIndex + 1}")
                }
                val wordCount = fullText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size

                val updatedBeat = beat.copy(
                    content = fullText,
                    wordsWritten = wordCount,
                    status = BeatStatus.Done,
                    errorMessage = null,
                    summary = "Completed scene: ${beat.title}. (${wordCount} words)"
                )

                val updatedBeats = bible.beats.map { if (it.index == beatIndex) updatedBeat else it }
                val nextIndex = (beatIndex + 1).coerceAtMost(bible.beats.size)
                val isAllDone = nextIndex >= bible.beats.size

                val updatedBible = bible.copy(beats = updatedBeats)
                val updatedProject = project.copy(
                    bible = updatedBible,
                    currentBeatIndex = nextIndex,
                    totalWords = updatedBeats.sumOf { it.wordsWritten },
                    status = if (isAllDone) StoryStatus.Completed else StoryStatus.Writing,
                    lastError = null,
                    updatedAt = System.currentTimeMillis()
                )

                saveProject(updatedProject)
                updatedBeat
            }

            result.onFailure { err ->
                val failedBeat = beat.copy(
                    status = BeatStatus.Failed,
                    errorMessage = err.message ?: "Beat generation failed"
                )
                val updatedBeats = bible.beats.map { if (it.index == beatIndex) failedBeat else it }
                val failedProject = project.copy(
                    bible = bible.copy(beats = updatedBeats),
                    lastError = "Beat ${beatIndex + 1} failed: ${err.message}"
                )
                saveProject(failedProject)
            }

            result
        } finally {
            activeJobs.remove(projectId)
        }
    }

    suspend fun sendDirectorMessage(
        projectId: String,
        userMessage: String,
        onChunk: (String) -> Unit = {}
    ): Result<StoryDirectorMessage> = withContext(Dispatchers.IO) {
        val project = _projectsFlow.value.firstOrNull { it.id == projectId }
            ?: return@withContext Result.failure(IllegalStateException("Story $projectId not found"))

        val settings = settingsStore.settingsFlow.value
        val assistant = settings.getAssistantById(settings.assistantId)
            ?: settings.assistants.firstOrNull()
            ?: return@withContext Result.failure(IllegalStateException("No active assistant found"))

        val modelId = assistant.chatModelId ?: settings.chatModelId
        val model = settings.findModelById(modelId)
            ?: return@withContext Result.failure(IllegalStateException("No valid chat model found"))
        val rawProv = model.findProvider(settings.providers)
            ?: return@withContext Result.failure(IllegalStateException("Provider not found for model ${model.displayName}"))
        val provider = secretKeyManager.populateProviderSecrets(rawProv)
        val handler = providerManager.getProviderByType(provider)

        // Add user message to history
        val userMsg = StoryDirectorMessage(role = "user", content = userMessage)
        val updatedHistory = project.directorChat + userMsg
        saveProject(project.copy(directorChat = updatedHistory))

        val memories = runCatching {
            memoryRepository.getCombinedMemoriesOfAssistant(assistant.id.toString())
        }.getOrDefault(emptyList()).map { it.content }

        val systemPrompt = buildString {
            appendLine("You are ${assistant.name}, the user's creative partner, editor, and story director.")
            if (assistant.systemPrompt.isNotBlank()) {
                appendLine(assistant.systemPrompt)
            }
            if (memories.isNotEmpty()) {
                appendLine("\n### Your Memories & Shared Context with the User:")
                memories.take(15).forEach { appendLine("- $it") }
            }
            appendLine("\n### Current Story Overview:")
            appendLine("Title: ${project.title}")
            appendLine("Genre: ${project.genre} • Tone: ${project.tone}")
            appendLine("Premise: ${project.premise}")
            if (project.bible != null) {
                appendLine("Style Guidelines: ${project.bible.styleGuidelines}")
                appendLine("Characters: " + project.bible.characters.joinToString { "${it.name} (${it.role})" })
                appendLine("Beats: " + project.bible.beats.joinToString { "Beat ${it.index + 1}: ${it.title}" })
            }
            appendLine("\n### Story Director Instructions:")
            appendLine("Chat with the user in your authentic voice, personality, and humor. Help brainstorm, critique, suggest plot twists, or steer scenes.")
            appendLine("If the user asks you to modify, add, or rewrite the Story Bible (premise, characters, world rules, beats, or style), fulfill the request in conversation, and ALSO include an updated Story Bible JSON block fenced as ```updated_bible ... ``` at the end.")
            appendLine("The app will automatically detect the ```updated_bible ... ``` block and update the Story Bible in real-time!")
        }

        val messages = mutableListOf<UIMessage>()
        messages.add(UIMessage.system(systemPrompt))
        updatedHistory.takeLast(10).forEach { msg ->
            if (msg.role == "user") {
                messages.add(UIMessage.user(msg.content))
            } else {
                messages.add(UIMessage.assistant(msg.content))
            }
        }

        val responseBuilder = StringBuilder()
        val currentJob = coroutineContext[Job]
        if (currentJob != null) activeJobs[projectId] = currentJob

        try {
            handler.streamText(
                providerSetting = provider,
                messages = messages,
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.7f,
                    maxTokens = 3000
                )
            ).collect { chunk ->
                val delta = chunk.choices.firstOrNull()?.delta?.toContentText().orEmpty()
                if (delta.isNotEmpty()) {
                    responseBuilder.append(delta)
                    onChunk(delta)
                }
            }

            val fullText = responseBuilder.toString()
            val assistantMsg = StoryDirectorMessage(role = "assistant", content = fullText)

            // Check if updated_bible block was emitted
            var newBible: StoryBible? = project.bible
            val bibleBlock = Regex("(?s)```(?:updated_bible|json)?\\s*(\\{.*?\\})\\s*```").find(fullText)
            if (bibleBlock != null) {
                val candidateJson = extractJsonPayload(bibleBlock.groupValues[1])
                val json = runCatching { JSONObject(candidateJson) }.getOrNull()
                if (json != null && (json.has("characters") || json.has("beats") || json.has("style_guidelines"))) {
                    val charsArray = json.optJSONArray("characters")
                    val characters = if (charsArray != null) {
                        (0 until charsArray.length()).map { i ->
                            val c = charsArray.getJSONObject(i)
                            StoryCharacter(
                                name = c.optString("name", "Character ${i + 1}"),
                                role = c.optString("role", "Protagonist"),
                                description = c.optString("description", ""),
                                motive = c.optString("motive", "")
                            )
                        }
                    } else project.bible?.characters ?: emptyList()

                    val rulesArray = json.optJSONArray("world_rules")
                    val rules = if (rulesArray != null) {
                        (0 until rulesArray.length()).map { rulesArray.getString(it) }
                    } else project.bible?.worldRules ?: emptyList()

                    val beatsArray = json.optJSONArray("beats")
                    val beats = if (beatsArray != null && beatsArray.length() > 0) {
                        (0 until beatsArray.length()).map { i ->
                            val b = beatsArray.getJSONObject(i)
                            StoryBeat(
                                index = i,
                                title = b.optString("title", "Beat ${i + 1}"),
                                objective = b.optString("objective", "Advance the plot"),
                                targetWords = b.optInt("target_words", 1000),
                                status = if (i == 0) BeatStatus.Writing else BeatStatus.Queued
                            )
                        }
                    } else project.bible?.beats ?: emptyList()

                    newBible = StoryBible(
                        title = project.title,
                        premise = json.optString("premise", project.premise),
                        genre = json.optString("genre", project.genre),
                        tone = json.optString("tone", project.tone),
                        styleGuidelines = json.optString("style_guidelines", project.bible?.styleGuidelines ?: "Visceral narrative."),
                        characters = characters,
                        worldRules = rules,
                        beats = beats,
                    )
                }
            }

            val finalProject = project.copy(
                bible = newBible,
                directorChat = updatedHistory + assistantMsg,
                updatedAt = System.currentTimeMillis()
            )
            saveProject(finalProject)
            Result.success(assistantMsg)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            activeJobs.remove(projectId)
        }
    }

    private suspend fun syncToWorkspace(project: StoryProject) = withContext(Dispatchers.IO) {
        runCatching {
            val wsStoriesDir = getWorkspaceStoriesDir() ?: return@runCatching
            val projectDir = File(wsStoriesDir, project.id)
            if (!projectDir.exists()) projectDir.mkdirs()

            // 1. JSON
            File(projectDir, "project.json").writeText(JsonInstant.encodeToString(project))

            // 2. Full Manuscript Markdown
            val mdFile = File(projectDir, "manuscript.md")
            val md = buildString {
                appendLine("# ${project.title}")
                appendLine("Genre: ${project.genre} • Tone: ${project.tone} • Total Words: ${project.totalWords}")
                appendLine()
                appendLine("## Premise")
                appendLine(project.premise)
                appendLine()
                project.bible?.beats?.forEach { beat ->
                    appendLine("## Beat ${beat.index + 1}: ${beat.title}")
                    if (beat.content.isNotBlank()) {
                        appendLine(beat.content)
                        appendLine()
                    }
                }
            }
            mdFile.writeText(md)

            updateWorkspaceIndex(wsStoriesDir)
        }.onFailure { Log.e(TAG, "Failed to sync story to workspace", it) }
    }

    private suspend fun getWorkspaceStoriesDir(): File? = withContext(Dispatchers.IO) {
        val workspace = workspaceDao.getAll().firstOrNull() ?: return@withContext null
        val dir = File(context.filesDir, "workspaces/${workspace.root}/files/stories")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private fun updateWorkspaceIndex(storiesDir: File) {
        runCatching {
            val projectDirs = storiesDir.listFiles { f -> f.isDirectory } ?: return
            val catalog = projectDirs.mapNotNull { dir ->
                val f = File(dir, "project.json")
                if (f.exists()) {
                    runCatching {
                        val p = JsonInstant.decodeFromString<StoryProject>(f.readText())
                        mapOf(
                            "id" to p.id,
                            "title" to p.title,
                            "genre" to p.genre,
                            "status" to p.status.name,
                            "totalWords" to p.totalWords,
                            "path" to "/workspace/stories/${p.id}/manuscript.md"
                        )
                    }.getOrNull()
                } else null
            }
            File(storiesDir, "index.json").writeText(JsonInstant.encodeToString(catalog))
        }
    }
}
