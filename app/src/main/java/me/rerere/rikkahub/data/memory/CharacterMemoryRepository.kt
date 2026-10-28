package me.rerere.rikkahub.data.memory

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.currentVersionMessages
import me.rerere.rikkahub.data.ai.rag.EmbeddingService
import me.rerere.rikkahub.data.ai.rag.VectorEngine
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

fun Assistant.memoryMode(): MemoryExperience = if (memoryExperience == "ADVANCED" ||
    (memoryExperience == null && enableMemoryConsolidation)) MemoryExperience.ADVANCED else MemoryExperience.BASIC

/** Host-owned scope, optimistic writes and source eligibility. No network operation runs under SQLite's lock. */
class CharacterMemoryRepository(
    val database: AppDatabase,
    private val embeddings: MemoryEmbeddingClient,
    private val ownerLookup: (String) -> Assistant?,
) {
    val dao get() = database.characterMemoryDao()
    private val json get() = JsonInstant
    fun owner(owner: String): Assistant = ownerLookup(owner)
        ?: error("Character no longer exists.")
    fun configuration(owner: String): String = owner(owner).let { assistant ->
        memoryHash("${assistant.enableMemory}|${assistant.memoryMode()}|${assistant.memoryContext}|${assistant.memoryDreaming}|${assistant.memoryProcessingModelId}")
    }
    private inline fun <reified T> decode(row: MemoryRecord): T = json.decodeFromString(row.payload)
    internal suspend inline fun <reified T> put(owner: String, kind: String, id: String, value: T) {
        val payload = JsonInstant.encodeToString(value)
        require(payload.toByteArray().size <= 512_000) { "This memory is too large. Split it into smaller notes." }
        dao.put(MemoryRecord(owner, kind, id, payload))
    }
    suspend fun metadata(owner: String): MemoryMetadata = dao.get(owner, "meta", "meta")?.let { decode(it) } ?: MemoryMetadata()

    suspend fun initialise(owner: String) = withContext(Dispatchers.IO) {
        owner(owner)
        database.withTransaction {
            val initial = metadata(owner)
            if (initial.migrated) return@withTransaction
            val exclusions = suppressions(owner)
            database.memoryDao().getMemoriesOfAssistant(owner).filter { it.type == 0 }.forEach { old ->
                val id = "legacy-${old.id}"
                if (dao.get(owner, "suppression", id) != null || exclusions.any { it.textHash == memoryHash(old.content) }) return@forEach
                put(owner, "note", id, MemoryNote(id, "Saved memory", paragraphs = listOf(
                    MemoryParagraph(id, old.content, listOf(MemoryEvidence(origin = "imported note"))),
                ), updatedAt = old.createdAt))
            }
            // Old episode text is never used to teach the new engine.
            database.openHelper.writableDatabase.execSQL("DELETE FROM embedding_cache WHERE memory_id IN (SELECT id FROM MemoryEntity WHERE assistant_id = ?) OR memory_id IN (SELECT id FROM ChatEpisodeEntity WHERE assistant_id = ?)", arrayOf(owner, owner))
            database.openHelper.writableDatabase.execSQL("DELETE FROM MemoryEntity WHERE assistant_id = ?", arrayOf(owner))
            database.openHelper.writableDatabase.execSQL("DELETE FROM ChatEpisodeEntity WHERE assistant_id = ?", arrayOf(owner))
            database.openHelper.writableDatabase.execSQL("DELETE FROM memory_projection WHERE assistant_id = ?", arrayOf(owner))
            for ((table, fts) in listOf("memory_claim" to "memory_claim_fts", "memory_episode_v3" to "memory_episode_v3_fts", "memory_source_v3" to "memory_source_v3_fts")) {
                database.openHelper.writableDatabase.execSQL("DELETE FROM $fts WHERE rowid IN (SELECT id FROM $table WHERE assistant_id = ?)", arrayOf(owner))
                database.openHelper.writableDatabase.execSQL("DELETE FROM $table WHERE assistant_id = ?", arrayOf(owner))
            }
            database.openHelper.writableDatabase.execSQL("DELETE FROM memory_ingest_state WHERE assistant_id = ?", arrayOf(owner))
            put(owner, "meta", "meta", initial.copy(migrated = true))
        }
    }

    /** Called only by the character-package importer with its freshly allocated owner UUID. */
    suspend fun importPackage(newOwner: String, bundle: CharacterMemoryPackage) = database.withTransaction {
        require(runCatching { Uuid.parse(newOwner) }.isSuccess && dao.all(newOwner).isEmpty())
        require(bundle.version == 1 && bundle.rememberingRules.length <= 8000)
        require(bundle.notes.size <= 10000 && bundle.facts.size <= 100000 && bundle.nodes.size <= 20000)
        bundle.notes.forEach { note ->
            require(note.title.length in 1..160 && note.body.length <= 48000 && note.category in setOf("You", "Topics", "People"))
            put(newOwner,"note",note.id,note.copy(paragraphs=note.paragraphs.map { p -> p.copy(
                evidence=listOf(MemoryEvidence(origin="imported note")),authored=false) }))
        }
        bundle.facts.forEach { fact ->
            val imported=fact.copy(evidence=listOf(MemoryEvidence(origin="imported note")),authored=false)
            validateMemoryFact(imported)
            put(newOwner,"fact",fact.id,imported)
        }
        bundle.nodes.forEach { node ->
            require(node.name.length in 1..160 && node.id.length in 1..100)
            put(newOwner,"node",node.id,node)
        }
        put(newOwner,"meta","meta",MemoryMetadata(migrated=true,activeAdvanced=bundle.facts.isNotEmpty(),rules=bundle.rememberingRules))
    }

    fun observe(owner: String): Flow<CharacterMemory> = combine(dao.observe(owner), database.conversationDao().getConversationsOfAssistant(owner)) {
        rows, _ -> snapshot(owner, rows)
    }.catch { error -> emit(CharacterMemory(metadata = MemoryMetadata(error = "Memory needs repair: ${error.message}. Stored records have been preserved."))) }.flowOn(Dispatchers.IO)
    suspend fun view(owner: String): CharacterMemory = withContext(Dispatchers.IO) { snapshot(owner, dao.all(owner)) }

    suspend fun candidateView(owner: String): CharacterMemory = snapshot(owner, dao.all(owner).filter {
        it.kind.startsWith("candidate-") || it.kind in setOf("meta", "note")
    }.map { if (it.kind.startsWith("candidate-")) it.copy(kind = it.kind.removePrefix("candidate-")) else it })

    suspend fun startPreparation(owner: String) = database.withTransaction {
        owner(owner)
        val meta = metadata(owner)
        if (meta.preparing || meta.activeAdvanced) return@withTransaction
        listOf("candidate-fact", "candidate-node", "candidate-note", "candidate-receipt").forEach { dao.clearKind(owner, it) }
        put(owner, "meta", "meta", meta.copy(preparing = true, preparationId = Uuid.random().toString(),
            paused = false, cancelled = false, error = null, inFlight = null, policyRevision = meta.policyRevision + 1))
    }

    suspend fun cancelPreparation(owner: String) = database.withTransaction {
        val meta = metadata(owner)
        listOf("candidate-fact", "candidate-node", "candidate-note", "candidate-receipt").forEach { dao.clearKind(owner, it) }
        put(owner, "meta", "meta", meta.copy(preparing = false, preparationId = null, cancelled = true,
            progress = "", inFlight = null, error = null, policyRevision = meta.policyRevision + 1))
    }

    /** Re-read sources inside the activation transaction, so a chat arriving mid-scan is not skipped. */
    private suspend fun preparationCaughtUp(owner: String): Boolean {
        val context = owner(owner).memoryContext
        for (note in view(owner).notes.filter { !it.sourceOnly && it.context == context }) {
            for (paragraph in note.paragraphs) {
                val id = "note:${note.id}:${paragraph.id}:${memoryHash(paragraph.text)}"
                if (dao.get(owner, "candidate-receipt", id) == null) return false
            }
        }
        val conversations = database.openHelper.readableDatabase.query(
            "SELECT nodes FROM ConversationEntity WHERE assistant_id = ?", arrayOf(owner))
        conversations.use { cursor ->
            while (cursor.moveToNext()) {
                val messages = json.decodeFromString<List<MessageNode>>(cursor.getString(0)).currentVersionMessages()
                for (message in messages.filter { it.role == MessageRole.USER && it.toText().isNotBlank() &&
                    (it.memoryContext == null || it.memoryContext == context) }) {
                    if (dao.get(owner, "candidate-receipt", "${message.id}:${memoryHash(message.toText())}") == null) return false
                }
            }
        }
        return true
    }

    suspend fun materializeBasic(owner: String) = database.withTransaction {
        val state=view(owner)
        val current=state.facts.filter { isCurrentMemory(it,System.currentTimeMillis()) && it.context==owner(owner).memoryContext }
        if(current.isEmpty())return@withTransaction
        val originals=state.notes.filter { it.context==owner(owner).memoryContext && !it.id.startsWith("projection-") }
        originals.forEach { put(owner,"note",it.id,it.copy(sourceOnly=false)) }
        state.notes.filter { it.id.startsWith("projection-") && it.context==owner(owner).memoryContext }.forEach { dao.delete(owner,"note",it.id) }
        val remaining=current.filter { fact -> originals.none { note -> note.paragraphs.any { sameMemoryText(it.text,fact.text) } } }
        remaining.groupBy{it.topic}.forEach{(topic,facts)->
            val id="projection-${memoryHash(owner(owner).memoryContext+topic).take(24)}"
            put(owner,"note",id,MemoryNote(id,topic,if(topic=="Overview")"You" else "Topics",paragraphs=facts.map{f->
                MemoryParagraph(f.id,f.text,f.evidence,f.authored,f.validUntil,originFact=f.id)
            },revision=(state.notes.firstOrNull{it.id==id}?.revision?:0)+1,context=owner(owner).memoryContext))
        }
    }

    suspend fun applyCandidate(owner: String, draft: MemoryDraft) = database.withTransaction {
        val meta = metadata(owner)
        check(meta.preparing && !meta.paused && !meta.cancelled && meta.policyRevision == draft.policyRevision)
        check(draft.configuration.isEmpty() || configuration(owner) == draft.configuration) { "Memory mode or context changed. Retry." }
        validateChanges(owner, draft.changes, "candidate-")
        draft.changes.forEach { change ->
            require(!change.delete)
            when (change.kind) {
                "fact" -> put(owner, "candidate-fact", change.id, requireNotNull(change.fact).copy(revision = (change.expectedRevision ?: 0) + 1))
                "node" -> put(owner, "candidate-node", change.id, requireNotNull(change.node).copy(revision = (change.expectedRevision ?: 0) + 1))
                "note" -> put(owner, "candidate-note", change.id, requireNotNull(change.note).copy(revision = (change.expectedRevision ?: 0) + 1))
            }
        }
    }

    suspend fun activateCandidate(owner: String, preparationId: String?, requireCatchUp: Boolean = false): Boolean = database.withTransaction {
        val meta = metadata(owner)
        check(meta.preparing && meta.preparationId == preparationId && !meta.paused && !meta.cancelled)
        if (requireCatchUp && !preparationCaughtUp(owner)) return@withTransaction false
        val candidate = candidateView(owner)
        // A deleted/edited source cannot be activated simply because extraction completed earlier.
        val facts = candidate.facts.filter { fact -> fact.authored || fact.evidence.any { evidenceValid(owner, it) } }
        val originalView=view(owner)
        val preserved = originalView.facts.filter { it.authored || it.status != "active" || it.context != owner(owner).memoryContext }
        dao.clearKind(owner, "fact")
        dao.clearKind(owner, "vector")
        facts.forEach { put(owner, "fact", it.id, it) }
        preserved.forEach { put(owner, "fact", it.id, it) }
        candidate.nodes.forEach { put(owner, "node", it.id, it) }
        originalView.nodes.forEach { put(owner,"node",it.id,it) }
        dao.all(owner).filter { it.kind == "candidate-receipt" }.forEach { dao.put(it.copy(kind = "receipt")) }
        listOf("candidate-fact", "candidate-node", "candidate-note", "candidate-receipt").forEach { dao.clearKind(owner, it) }
        val summary = (facts + preserved).distinctBy { it.id }.filter { isCurrentMemory(it, System.currentTimeMillis()) && it.kind in setOf("fact", "habit") }
            .groupBy { it.topic }.map { (topic, items) -> MemorySummarySection(topic, items.joinToString(" ") { it.text }, items.map { it.id }) }
        put(owner, "meta", "meta", meta.copy(activeAdvanced = true, preparing = false, preparationId = null,
            progress = "", summary = summary, revision = meta.revision + 1, updatedAt = System.currentTimeMillis()))
        true
    }

    private suspend fun snapshot(owner: String, rows: List<MemoryRecord>): CharacterMemory {
        val evidence = rows.filter { it.kind == "note" }.flatMap { decode<MemoryNote>(it).paragraphs.flatMap { p -> p.evidence } } +
            rows.filter { it.kind == "fact" }.flatMap { decode<MemoryFact>(it).evidence }
        val reader = EvidenceReader(owner)
        val eligible = evidence.distinct().associateWith { reader.valid(it) }
        val metadata = rows.firstOrNull { it.kind == "meta" }?.let { decode<MemoryMetadata>(it) } ?: MemoryMetadata()
        val notes = rows.filter { it.kind == "note" }.map { decode<MemoryNote>(it) }.map { n ->
            n.copy(paragraphs = n.paragraphs.filter { p -> (p.validUntil == null || p.validUntil > System.currentTimeMillis()) && (p.authored || p.evidence.any { eligible[it] == true }) })
        }.filter { it.paragraphs.isNotEmpty() }
        val facts = rows.filter { it.kind == "fact" }.map { decode<MemoryFact>(it) }
            .filter { f -> f.authored || f.evidence.any { eligible[it] == true } }
        // A presentation is never allowed to preserve a paragraph whose underlying evidence was removed.
        val validSummary = metadata.summary.filter { section -> section.factIds.isNotEmpty() && section.factIds.all { id ->
            facts.any { it.id == id && it.context == owner(owner).memoryContext && isCurrentMemory(it, System.currentTimeMillis()) && it.kind !in setOf("plan", "temporary") }
        } }
        return CharacterMemory(metadata.copy(summary = validSummary), notes, facts,
            rows.filter { it.kind == "node" }.map { decode(it) },
            rows.filter { it.kind == "draft" }.map { decode(it) },
            rows.filter { it.kind == "history" }.map { decode<MemoryCommit>(it) }.sortedByDescending { it.at })
    }

    suspend fun evidenceValid(owner: String, evidence: MemoryEvidence, depth: Int = 0): Boolean =
        EvidenceReader(owner).valid(evidence, depth)

    /** Cache parsed sources only for one operation; source edits cannot leave a persistent stale cache. */
    private inner class EvidenceReader(private val owner: String) {
        private val notes = mutableMapOf<String, MemoryNote?>()
        private val chats = mutableMapOf<String, Map<String, Pair<String, String>>>()
        suspend fun valid(evidence: MemoryEvidence, depth: Int = 0): Boolean {
            if (depth > 4) return false
            if (evidence.origin == "saved note") {
                val note = if (notes.containsKey(evidence.messageId)) notes[evidence.messageId] else
                    dao.get(owner, "note", evidence.messageId)?.let { decode<MemoryNote>(it) }.also { notes[evidence.messageId] = it }
                note ?: return false
                if (evidence.paragraphId != null) {
                    val paragraph = note.paragraphs.firstOrNull { it.id == evidence.paragraphId } ?: return false
                    return evidence.quote.isNotBlank() && memoryHash(paragraph.text) == evidence.revision && paragraph.text.contains(evidence.quote) &&
                        (paragraph.authored || paragraph.evidence.any { valid(it, depth + 1) })
                }
                val safeBody = note.paragraphs.filter { it.authored || it.evidence.any { source -> valid(source, depth + 1) } }
                    .joinToString("\n\n") { it.text }
                return note.revision.toString() == evidence.revision && evidence.quote.isNotBlank() && safeBody.contains(evidence.quote)
            }
            if (evidence.origin != "chat") return evidence.origin in setOf("imported note", "user edit", "memory request")
            val messages = chats[evidence.conversationId] ?: run {
                val source = database.conversationDao().getConversationById(evidence.conversationId)
                val selected = if (source?.assistantId != owner) emptyMap() else
                    json.decodeFromString<List<MessageNode>>(source.nodes).currentVersionMessages()
                        .filter { it.role == MessageRole.USER }.associate { message ->
                            val text = message.toText()
                            message.id.toString() to (memoryHash(text) to text)
                        }
                chats[evidence.conversationId] = selected
                selected
            }
            val (revision, text) = messages[evidence.messageId] ?: return false
            return revision == evidence.revision && evidence.quote.isNotBlank() && text.contains(evidence.quote)
        }
    }

    suspend fun configure(owner: String, transform: (MemoryMetadata) -> MemoryMetadata) = database.withTransaction {
        owner(owner)
        val old = metadata(owner)
        val next = transform(old)
        require(next.rules.length <= 8000)
        put(owner, "meta", "meta", next.copy(policyRevision = old.policyRevision + 1, revision = old.revision + 1))
    }

    suspend fun saveNote(owner: String, note: MemoryNote, expectedRevision: Long?) = apply(owner,
        MemoryDraft(Uuid.random().toString(), "Edit ${note.title}", listOf(MemoryChange("note", note.id, expectedRevision, note = note)), metadata(owner).policyRevision))

    suspend fun stage(owner: String, draft: MemoryDraft) = database.withTransaction {
        owner(owner)
        check(metadata(owner).policyRevision == draft.policyRevision) { "Memory settings changed. Try again." }
        validateChanges(owner, draft.changes)
        put(owner, "draft", draft.id, draft)
    }

    private suspend fun validateChanges(owner: String, changes: List<MemoryChange>, prefix: String = "") {
        require(changes.size <= 64) { "Split this edit into smaller requests." }
        require(changes.map { it.kind to it.id }.distinct().size == changes.size) { "Duplicate change." }
        changes.forEach { c ->
            require(c.kind in setOf("note", "fact", "node") && c.id.length in 1..100)
            val existing = dao.get(owner, prefix + c.kind, c.id)
            val revision = existing?.let { when (c.kind) {
                "note" -> decode<MemoryNote>(it).revision
                "fact" -> decode<MemoryFact>(it).revision
                else -> decode<MemoryNode>(it).revision
            } }
            check(revision == c.expectedRevision) { "This memory changed. Refresh the preview before applying." }
            require(existing != null || !c.delete) { "Memory no longer exists." }
            if (!c.delete) when (c.kind) {
                "note" -> {
                    val note = requireNotNull(c.note)
                    require(note.id == c.id && note.title.isNotBlank() && note.title.length <= 160 && note.body.length <= 48_000)
                    require(note.category in setOf("You", "Topics", "People"))
                    note.paragraphs.forEach { p ->
                        require(p.text.isNotBlank())
                        require(p.authored || (p.evidence.isNotEmpty() && p.evidence.all { evidenceValid(owner, it) })) { "The source changed. Refresh this edit." }
                    }
                }
                "fact" -> {
                    val fact = requireNotNull(c.fact)
                    require(fact.id == c.id)
                    validateMemoryFact(fact)
                    for (nodeId in listOfNotNull(fact.subject, fact.objectId)) {
                        if (nodeId in setOf("user", "assistant")) continue
                        val node = changes.firstOrNull { it.kind == "node" && it.id == nodeId && !it.delete }?.node
                            ?: dao.get(owner, prefix + "node", nodeId)?.let { decode<MemoryNode>(it) }
                        require(node != null && node.context == fact.context) { "The connected entity is missing or belongs to another context." }
                    }
                    require(fact.authored || fact.evidence.all { evidenceValid(owner, it) }) { "The source changed. Refresh this edit." }
                }
                "node" -> require(c.id !in setOf("user","assistant") && c.node?.let {
                    it.id == c.id && it.name.isNotBlank() && it.name.length <= 160 && it.type in setOf("Person","Place","Thing","Concept")
                } == true)
            }
        }
    }

    suspend fun apply(owner: String, draft: MemoryDraft): String = database.withTransaction {
        owner(owner)
        val meta = metadata(owner)
        check(draft.configuration.isEmpty() || configuration(owner) == draft.configuration) { "Memory mode or context changed. Retry." }
        check(meta.policyRevision == draft.policyRevision) { "Memory settings changed. Try again." }
        validateChanges(owner, draft.changes)
        val before = draft.changes.mapNotNull { c -> dao.get(owner, c.kind, c.id)?.let { old -> when (c.kind) {
            "note" -> MemoryChange("note", c.id, note = decode(old))
            "fact" -> MemoryChange("fact", c.id, fact = decode(old))
            else -> MemoryChange("node", c.id, node = decode(old))
        } } }
        val applied = draft.changes.map { c ->
            if (c.delete) {
                val old = before.first { it.kind == c.kind && it.id == c.id }
                val evidence = old.fact?.evidence ?: old.note?.paragraphs?.flatMap { it.evidence }.orEmpty()
                val text = old.fact?.text ?: old.note?.body.orEmpty()
                put(owner, "suppression", c.id, makeSuppression(owner, evidence, text))
                val derived = if(old.note!=null)dao.all(owner).filter{it.kind=="fact"}.map{decode<MemoryFact>(it)}
                    .filter{fact->fact.evidence.any{it.origin=="saved note"&&it.messageId==c.id}}.map{it.id} else emptyList()
                val related=old.note?.paragraphs?.mapNotNull{it.originFact}.orEmpty()+listOfNotNull(old.fact?.id)+derived
                related.distinct().forEach { factId ->
                    dao.get(owner,"fact",factId)?.let { row ->
                        val fact=decode<MemoryFact>(row)
                        put(owner,"suppression",factId,makeSuppression(owner,fact.evidence,fact.text))
                        scrubNoteCopies(owner,fact)
                        dao.delete(owner,"fact",factId)
                    }
                }
                dao.delete(owner, c.kind, c.id)
                // History and unfinished previews must not become recovery copies of forgotten content.
                dao.clearKind(owner, "history")
                dao.clearKind(owner, "draft")
                dao.clearKind(owner, "vector")
                listOf("candidate-fact", "candidate-node", "candidate-note", "candidate-receipt").forEach { dao.clearKind(owner, it) }
                if (c.kind == "node") {
                    dao.all(owner).filter { it.kind == "fact" }.map { decode<MemoryFact>(it) }
                        .filter { it.subject == c.id || it.objectId == c.id }.forEach { f ->
                            put(owner, "suppression", f.id, makeSuppression(owner, f.evidence, f.text))
                            scrubNoteCopies(owner,f)
                            dao.delete(owner, "fact", f.id)
                        }
                }
                c
            } else when (c.kind) {
                "note" -> c.copy(note = requireNotNull(c.note).copy(revision = (c.expectedRevision ?: 0) + 1, updatedAt = System.currentTimeMillis()))
                    .also { changed ->
                        val oldNote=before.firstOrNull{it.kind=="note"&&it.id==c.id}?.note
                        val retained=changed.note?.paragraphs?.mapNotNull{it.originFact}.orEmpty().toSet()
                        oldNote?.paragraphs?.mapNotNull{it.originFact}?.filter{it !in retained}?.forEach{factId->
                            dao.get(owner,"fact",factId)?.let{record->
                                val fact=decode<MemoryFact>(record)
                                put(owner,"fact",factId,fact.copy(status="historical",revision=fact.revision+1))
                                put(owner,"correction",factId,makeSuppression(owner,fact.evidence,fact.text))
                            }
                        }
                        retained.forEach{factId->
                            if(dao.get(owner,"correction",factId)!=null){
                                dao.get(owner,"fact",factId)?.let{record->val fact=decode<MemoryFact>(record);put(owner,"fact",factId,fact.copy(status="active",revision=fact.revision+1))}
                                dao.delete(owner,"correction",factId)
                            }
                        }
                        put(owner, "note", c.id, changed.note)
                    }
                "fact" -> c.copy(fact = requireNotNull(c.fact).copy(revision = (c.expectedRevision ?: 0) + 1))
                    .also { put(owner, "fact", c.id, it.fact); dao.delete(owner, "vector", c.id) }
                else -> c.copy(node = requireNotNull(c.node).copy(revision = (c.expectedRevision ?: 0) + 1)).also { put(owner, "node", c.id, it.node) }
            }
        }
        val commit = MemoryCommit(Uuid.random().toString(), draft.request, System.currentTimeMillis(), before, applied)
        // Ordinary edits have a bounded version history. Erasures have no hidden permanent undo copy.
        if (draft.changes.none { it.delete }) {
            put(owner, "history", commit.id, commit)
            val history = dao.all(owner).filter { it.kind == "history" }.map { decode<MemoryCommit>(it) }.sortedByDescending { it.at }
            history.drop(30).forEach { dao.delete(owner, "history", it.id) }
        }
        val erasing = draft.changes.any { it.delete }
        put(owner, "meta", "meta", meta.copy(revision = meta.revision + 1, updatedAt = System.currentTimeMillis(),
            policyRevision = meta.policyRevision + if (erasing) 1 else 0,
            preparing = if (erasing) false else meta.preparing,
            activeAdvanced = if (owner(owner).memoryMode()==MemoryExperience.BASIC && draft.changes.any{it.kind=="note"}) false else meta.activeAdvanced,
            summary = if (erasing) emptyList() else draft.summary))
        dao.delete(owner, "draft", draft.id)
        commit.id
    }

    suspend fun undo(owner: String, commitId: String) {
        val commit = dao.get(owner, "history", commitId)?.let { decode<MemoryCommit>(it) } ?: error("This change can no longer be undone.")
        val inverse = commit.after.map { c ->
            val previous = commit.before.firstOrNull { it.kind == c.kind && it.id == c.id }
            val expected = c.note?.revision ?: c.fact?.revision ?: c.node?.revision ?: 1L
            previous?.copy(expectedRevision = expected) ?: MemoryChange(c.kind, c.id, expectedRevision = expected, delete = true)
        }
        apply(owner, MemoryDraft(Uuid.random().toString(), "Undo ${commit.label}", inverse, metadata(owner).policyRevision))
    }

    suspend fun suppressions(owner: String): List<MemorySuppression> = dao.all(owner).filter { it.kind == "suppression" }.map { decode(it) }
    private suspend fun scrubNoteCopies(owner:String,fact:MemoryFact) {
        dao.all(owner).filter{it.kind=="note"}.map{decode<MemoryNote>(it)}.forEach{note->
            val paragraphs=note.paragraphs.mapNotNull{p->
                if(p.originFact==fact.id)return@mapNotNull null
                var text=p.text
                fact.evidence.filter{it.origin=="saved note"&&it.messageId==note.id}.forEach{e->if(e.quote.isNotBlank())text=text.replace(e.quote,"")}
                if(text.isBlank())null else p.copy(text=text.trim())
            }
            if(paragraphs!=note.paragraphs){
                if(paragraphs.isEmpty())dao.delete(owner,"note",note.id)
                else put(owner,"note",note.id,note.copy(paragraphs=paragraphs,revision=note.revision+1))
            }
        }
    }
    private suspend fun makeSuppression(owner: String, evidence: List<MemoryEvidence>, text: String): MemorySuppression {
        val exclusions = evidence.filter { it.origin in setOf("chat", "saved note") && it.quote.isNotBlank() }.flatMap { e ->
            val source = if(e.origin=="saved note") {
                dao.get(owner,"note",e.messageId)?.let { record ->
                    val note=decode<MemoryNote>(record)
                    if(e.paragraphId!=null)note.paragraphs.firstOrNull{it.id==e.paragraphId}?.text else note.body
                }.orEmpty()
            } else {
                val row = database.conversationDao().getConversationById(e.conversationId)
                row?.takeIf { it.assistantId == owner }?.let { json.decodeFromString<List<MessageNode>>(it.nodes).currentVersionMessages() }
                    ?.firstOrNull { it.id.toString() == e.messageId }?.toText().orEmpty()
            }
            val result = mutableListOf<MemoryExclusion>()
            var index = source.indexOf(e.quote)
            while (index >= 0) {
                result += MemoryExclusion(if(e.origin=="saved note")"note:${e.messageId}" else e.conversationId,
                    if(e.origin=="saved note")e.paragraphId?:"*" else e.messageId, e.revision, index, index + e.quote.length, memoryHash(e.quote))
                index = source.indexOf(e.quote, index + e.quote.length)
            }
            result
        }
        return MemorySuppression(exclusions, memoryHash(text))
    }
    suspend fun redact(owner: String, evidence: MemoryEvidence, text: String): String {
        val exclusions = (suppressions(owner)+dao.all(owner).filter{it.kind=="correction"}.map{decode<MemorySuppression>(it)}).flatMap { it.exclusions }.filter {
            it.messageId == evidence.messageId && it.conversationId == evidence.conversationId
        }
        if (exclusions.isEmpty()) return text
        // Unknown remapping is withheld before inference, never sent to a model for redaction.
        if (exclusions.any { it.revision != evidence.revision }) return "[Source withheld after a privacy exclusion and subsequent edit]"
        val full = database.conversationDao().getConversationById(evidence.conversationId)?.let {
            json.decodeFromString<List<MessageNode>>(it.nodes).currentVersionMessages().firstOrNull { m -> m.id.toString() == evidence.messageId }?.toText()
        } ?: return ""
        var result = text
        for (old in exclusions) {
            val fragment = full.substring(old.start.coerceIn(0, full.length), old.end.coerceIn(0, full.length))
            if (memoryHash(fragment) != old.fragmentHash) return "[Source withheld after a privacy exclusion]"
            if (fragment.isNotBlank()) result = result.replace(fragment, "[forgotten]")
        }
        return result
    }

    suspend fun context(owner: String, query: String, tools: Boolean): List<AssistantMemory> = withContext(Dispatchers.IO) {
        initialise(owner)
        val assistant = owner(owner)
        if (!assistant.enableMemory) return@withContext emptyList()
        val view = view(owner)
        if (assistant.memoryMode() == MemoryExperience.BASIC) {
            val you = view.notes.filter { it.category == "You" && !it.sourceOnly && it.context == assistant.memoryContext }
            val others = view.notes.filter { it.category != "You" && !it.sourceOnly && it.context == assistant.memoryContext }
            val relevant = if (tools) emptyList() else others.filter { lexical(query, it.title + " " + it.aliases.joinToString(" ") + " " + it.body) > 0 }.take(4)
            return@withContext (you + relevant).map { it.asContext() } + if (tools) others.map { note ->
                AssistantMemory(id = note.id.hashCode(), content = "Note ${note.id}: ${note.title}. ${note.description} Aliases: ${note.aliases.joinToString()}", timestamp = note.updatedAt)
            } else emptyList()
        }
        val facts = view.facts.filter { isCurrentMemory(it, System.currentTimeMillis()) && it.context == assistant.memoryContext }
        val scores = facts.associate { it.id to lexical(query, it.text) }.toMutableMap()
        val namedNodes = view.nodes.filter { node ->
            (listOf(node.name) + node.aliases).any { name -> name.isNotBlank() && lexical(name, query) >= 1.0 }
        }.map { it.id }.toSet()
        facts.filter { it.subject in namedNodes || it.objectId in namedNodes }.forEach { scores[it.id] = scores.getValue(it.id) + 2.0 }
        if (query.isNotBlank()) {
            try {
                val q = embeddings.embedWithModelId(query, owner)
                val vectors = dao.all(owner).filter { it.kind == "vector" }.associate { it.id to decode<MemoryVector>(it) }
                facts.forEach { fact -> vectors[fact.id]?.takeIf { it.recipe == q.modelId && it.revision == fact.revision }?.let { v ->
                    scores[fact.id] = scores.getValue(fact.id) + VectorEngine.cosineSimilarity(q.embeddings.first().toFloatArray(), v.values.toFloatArray()).coerceAtLeast(0f).toDouble()
                } }
            } catch (error: Exception) { if (error is kotlinx.coroutines.CancellationException) throw error }
        }
        val fallbackNotes = if (!view.metadata.activeAdvanced) view.notes.filter { !it.sourceOnly && it.context == assistant.memoryContext && (it.category == "You" || lexical(query,it.body) > 0) }.take(5).map { it.asContext() } else emptyList()
        fallbackNotes + facts.filter { scores.getValue(it.id) > 0 || it.pinned }.sortedByDescending { scores.getValue(it.id) + if (it.pinned) 0.2 else 0.0 }
            .take(12).map { f -> AssistantMemory(id = f.id.hashCode(), content = "${f.text} [memory ${f.id}; ${f.kind}; reported ${f.occurredAt ?: "time unknown"}]", timestamp = f.recordedAt) }
    }

    private fun MemoryNote.asContext() = AssistantMemory(id = id.hashCode(), content = "Note $id · $title\n$body", timestamp = updatedAt)
    internal fun lexical(query: String, text: String): Double {
        val stop = setOf("what", "when", "where", "who", "how", "is", "are", "was", "the", "a", "an", "do", "did", "you", "i", "my", "me", "about", "of", "and", "to")
        val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.isNotBlank() && it !in stop }.distinct()
        if (words.isEmpty()) return 0.0
        val lower = text.lowercase()
        return words.count { term ->
            if(term.all { it.code < 128 }) Regex("(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])").containsMatchIn(lower)
            else lower.contains(term)
        }.toDouble() / words.size
    }

    suspend fun index(owner: String) {
        val view = view(owner)
        for (fact in view.facts) {
            val old = dao.get(owner, "vector", fact.id)?.let { decode<MemoryVector>(it) }
            if (old?.revision == fact.revision && old.recipe == embeddings.getEmbeddingModelId(owner)) continue
            val vector = embeddings.embedWithModelId(fact.text, owner)
            database.withTransaction {
                val current = dao.get(owner, "fact", fact.id)?.let { decode<MemoryFact>(it) }
                if (current?.revision == fact.revision && metadata(owner).paused.not()) {
                    put(owner, "vector", fact.id, MemoryVector(fact.revision, vector.modelId, vector.embeddings.first()))
                }
            }
        }
    }
}
