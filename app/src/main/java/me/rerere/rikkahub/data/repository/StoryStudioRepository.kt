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
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.utils.JsonInstant
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.uuid.Uuid

private const val TAG = "StoryStudioRepository"

class StoryStudioRepository(
    private val context: Context,
    private val workspaceDao: WorkspaceDAO,
    private val settingsStore: SettingsStore,
    private val providerManager: ProviderManager,
    private val secretKeyManager: SecretKeyManager,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _projectsFlow = MutableStateFlow<List<StoryProject>>(emptyList())
    val projectsFlow: StateFlow<List<StoryProject>> = _projectsFlow.asStateFlow()

    init {
        refresh()
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

        runCatching {
            val settings = settingsStore.settingsFlow.value
            val modelId = project.architectModelId ?: settings.chatModelId
            val model = settings.findModelById(modelId)
                ?: return@withContext Result.failure(IllegalStateException("No valid architect model found"))
            val rawProv = model.findProvider(settings.providers)
                ?: return@withContext Result.failure(IllegalStateException("Provider not found for architect model"))
            val provider = secretKeyManager.populateProviderSecrets(rawProv)
            val handler = providerManager.getProviderByType(provider)

            val prompt = """
                You are a master story architect and worldbuilder.
                Create a rich Story Bible and Milestone Beat Outline for the following story idea.
                
                Title: ${project.title}
                Genre: ${project.genre}
                Tone: ${project.tone}
                Premise: ${project.premise}
                
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
                    { "index": 0, "title": "Beat Title", "objective": "What happens in this milestone scene", "target_words": 1000 },
                    { "index": 1, "title": "Beat Title", "objective": "What happens in this milestone scene", "target_words": 1200 },
                    { "index": 2, "title": "Beat Title", "objective": "What happens in this milestone scene", "target_words": 1000 },
                    { "index": 3, "title": "Beat Title", "objective": "What happens in this milestone scene", "target_words": 1200 }
                  ]
                }
            """.trimIndent()

            val resp = handler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(
                    model = model,
                    temperature = 0.5f,
                    maxTokens = 2000
                )
            )

            val text = resp.choices.firstOrNull()?.message?.toContentText().orEmpty()
            val cleanJson = text.substringAfter("{").substringBeforeLast("}")
            val json = JSONObject("{$cleanJson}")

            val style = json.optString("style_guidelines", "Visceral narrative with high momentum.")
            val charsArray = json.optJSONArray("characters") ?: JSONArray()
            val characters = (0 until charsArray.length()).map { i ->
                val c = charsArray.getJSONObject(i)
                StoryCharacter(
                    name = c.optString("name"),
                    role = c.optString("role"),
                    description = c.optString("description"),
                    motive = c.optString("motive")
                )
            }

            val rulesArray = json.optJSONArray("world_rules") ?: JSONArray()
            val rules = (0 until rulesArray.length()).map { rulesArray.getString(it) }

            val beatsArray = json.optJSONArray("beats") ?: JSONArray()
            val beats = (0 until beatsArray.length()).map { i ->
                val b = beatsArray.getJSONObject(i)
                StoryBeat(
                    index = i,
                    title = b.optString("title"),
                    objective = b.optString("objective"),
                    targetWords = b.optInt("target_words", 1000),
                    status = if (i == 0) BeatStatus.Writing else BeatStatus.Queued
                )
            }

            val bible = StoryBible(
                title = project.title,
                premise = project.premise,
                genre = project.genre,
                tone = project.tone,
                styleGuidelines = style,
                characters = characters,
                worldRules = rules,
                beats = beats,
            )

            val updatedProject = project.copy(
                status = StoryStatus.Writing,
                bible = bible,
                updatedAt = System.currentTimeMillis()
            )
            saveProject(updatedProject)
            updatedProject
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

        runCatching {
            val settings = settingsStore.settingsFlow.value
            val modelId = project.proseModelId ?: settings.chatModelId
            val model = settings.findModelById(modelId)
                ?: return@withContext Result.failure(IllegalStateException("No valid prose model found"))
            val rawProv = model.findProvider(settings.providers)
                ?: return@withContext Result.failure(IllegalStateException("Provider not found for prose model"))
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
                    maxTokens = 3000
                )
            ).collect { chunk ->
                val delta = chunk.choices.firstOrNull()?.delta?.toContentText().orEmpty()
                if (delta.isNotBlank()) {
                    proseBuilder.append(delta)
                    onChunk(delta)
                }
            }

            val fullText = proseBuilder.toString()
            val wordCount = fullText.split("\\s+".toRegex()).filter { it.isNotBlank() }.size

            val updatedBeat = beat.copy(
                content = fullText,
                wordsWritten = wordCount,
                status = BeatStatus.Done,
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
                updatedAt = System.currentTimeMillis()
            )

            saveProject(updatedProject)
            updatedBeat
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
