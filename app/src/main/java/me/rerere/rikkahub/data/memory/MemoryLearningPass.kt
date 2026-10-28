package me.rerere.rikkahub.data.memory

import androidx.room.withTransaction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.currentVersionMessages
import me.rerere.rikkahub.utils.JsonInstant

@Serializable data class MemoryReceipt(val revision:String,val at:Long,val warning:String?=null)

/** A bounded pass checkpoints each source. A rejected item cannot poison every later source. */
class MemoryLearningPass(
    private val repository:CharacterMemoryRepository,
    private val processor:MemoryProcessor,
    private val owner:String,
    private val workerId:String,
    private val foreground:suspend(String)->Unit,
) {
    private var calls=0
    private lateinit var configuration:String
    private var policyRevision=0L
    private var staged=false
    private var receiptKind="receipt"

    private suspend fun canContinue():Boolean {
        currentCoroutineContext().ensureActive()
        val meta=repository.metadata(owner)
        return !meta.paused&&!meta.cancelled&&meta.policyRevision==policyRevision&&repository.configuration(owner)==configuration
    }
    private suspend fun shouldYield()=calls>=8 || repository.tasks(owner).any{it.active}

    private suspend fun process(key:String,input:MemoryInput,progress:String,prior:String=""):Boolean {
        if(repository.dao.get(owner,receiptKind,key)!=null)return true
        if(!canContinue())return false
        val before=repository.metadata(owner)
        val permitted=repository.database.withTransaction {
            val latest=repository.metadata(owner)
            if(!canContinue()||latest.policyRevision!=before.policyRevision)return@withTransaction false
            repository.put(owner,"meta","meta",latest.copy(progress=progress,inFlight=key,inFlightWorker=workerId,error=null))
            true
        }
        if(!permitted)return false
        val draft=try {
            processor.extract(owner,listOf(input),staged,prior)
        } catch(error:MemoryOutputException) {
            repository.database.withTransaction {
                if(canContinue()) {
                    val receipt=MemoryReceipt(input.evidence.revision,System.currentTimeMillis(),memoryWorkError(error))
                    repository.put(owner,receiptKind,key,receipt)
                    repository.put(owner,"rejected",key,receipt)
                    val latest=repository.metadata(owner)
                    if(latest.inFlightWorker==workerId)repository.put(owner,"meta","meta",latest.copy(inFlight=null,inFlightWorker=null))
                }
            }
            calls++
            return canContinue()
        }
        repository.database.withTransaction {
            val latest=repository.metadata(owner)
            if(!canContinue()||latest.policyRevision!=before.policyRevision)return@withTransaction
            try {
                if(draft.changes.isNotEmpty()) {
                    if(staged)repository.applyCandidate(owner,draft) else repository.apply(owner,draft)
                }
                val warning=draft.reviewNote?.takeIf{it.isNotBlank()}
                val receipt=MemoryReceipt(input.evidence.revision,System.currentTimeMillis(),warning)
                repository.put(owner,receiptKind,key,receipt)
                if(warning!=null)repository.put(owner,"rejected",key,receipt)
            } catch(error:IllegalArgumentException) {
                // Source edits and malformed individual proposals are recoverable; retain originals.
                val receipt=MemoryReceipt(input.evidence.revision,System.currentTimeMillis(),memoryWorkError(error))
                repository.put(owner,receiptKind,key,receipt)
                repository.put(owner,"rejected",key,receipt)
            }
            val current=repository.metadata(owner)
            if(current.inFlightWorker==workerId)repository.put(owner,"meta","meta",current.copy(inFlight=null,inFlightWorker=null))
        }
        calls++
        return canContinue()
    }

    /** true means more committed-source work remains and a continuation must be scheduled. */
    suspend fun run():Boolean {
        val assistant=repository.owner(owner)
        configuration=repository.configuration(owner)
        val meta=repository.metadata(owner)
        policyRevision=meta.policyRevision
        staged=meta.preparing&&assistant.memoryMode()==MemoryExperience.ADVANCED
        receiptKind=if(staged)"candidate-receipt" else "receipt"
        val preparation=meta.preparationId
        val explicit=meta.pendingRefresh
        if(assistant.memoryMode()==MemoryExperience.BASIC&&!assistant.memoryDreaming&&!explicit)return false
        if(assistant.memoryMode()==MemoryExperience.ADVANCED&&!meta.activeAdvanced&&!staged)return false
        if(!canContinue())return false
        foreground(if(staged)"Preparing memory" else "Organizing memory")
        if(staged || assistant.memoryMode()==MemoryExperience.BASIC&&explicit) {
            val notes=repository.view(owner).notes.filter { it.context==assistant.memoryContext &&
                if(staged)!it.sourceOnly else it.id.startsWith("legacy-") }
            for((noteIndex,note) in notes.withIndex())for(paragraph in note.paragraphs) {
                val revision=memoryHash(paragraph.text)
                val parent="note:${note.id}:${paragraph.id}:$revision"
                if(repository.dao.get(owner,receiptKind,parent)!=null)continue
                val source=MemoryEvidence(messageId=note.id,revision=revision,origin="saved note",paragraphId=paragraph.id)
                for((i,text) in paragraph.text.chunked(6000).withIndex()) {
                    if(!canContinue())return false
                    if(!process("$parent:$i",MemoryInput("source",text,source.copy(quote=text)),"${noteIndex+1}/${notes.size} saved notes"))return false
                    if(shouldYield())return true
                }
                repository.put(owner,receiptKind,parent,MemoryReceipt(revision,System.currentTimeMillis()))
            }
        }
        // Fetch only IDs first, then one conversation at a time rather than loading all chat blobs.
        val ids=repository.database.openHelper.readableDatabase.query(
            "SELECT id FROM ConversationEntity WHERE assistant_id = ? ORDER BY create_at,id",arrayOf(owner)).use{c->buildList{while(c.moveToNext())add(c.getString(0))}}
        for((chatIndex,id) in ids.withIndex()) {
            if(!canContinue())return false
            val conversation=repository.database.conversationDao().getConversationById(id)?:continue
            val messages=JsonInstant.decodeFromString<List<MessageNode>>(conversation.nodes).currentVersionMessages()
                .filter{it.role==MessageRole.USER&&it.toText().isNotBlank()&&(it.memoryContext==null||it.memoryContext==assistant.memoryContext)}
            for((messageIndex,message) in messages.withIndex()) {
                if(assistant.memoryMode()==MemoryExperience.BASIC&&!explicit&&meta.learningEnabledAt>0 &&
                    (message.sentAtEpochMillis?:message.createdAt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds())<meta.learningEnabledAt)continue
                val revision=memoryHash(message.toText())
                val parent="${message.id}:$revision"
                if(repository.dao.get(owner,receiptKind,parent)!=null)continue
                val evidence=MemoryEvidence(id,message.id.toString(),revision)
                val safe=repository.redact(owner,evidence,message.toText())
                val prior=messages.take(messageIndex).takeLast(3).map{p->repository.redact(owner,MemoryEvidence(id,p.id.toString(),memoryHash(p.toText())),p.toText()).takeLast(1500)}.joinToString("\n")
                val time=message.sentAtEpochMillis?.let{java.time.Instant.ofEpochMilli(it).toString()+" zone "+message.sentAtZoneId}?:message.createdAt.toString()+" (offset unknown)"
                for((i,text) in safe.chunked(6000).withIndex()) {
                    if(!canContinue())return false
                    if(!process("$parent:$i",MemoryInput("source",text,evidence.copy(quote=text),time),"${chatIndex+1}/${ids.size} chats",prior))return false
                    if(shouldYield())return true
                }
                repository.put(owner,receiptKind,parent,MemoryReceipt(revision,System.currentTimeMillis()))
            }
        }
        if(!canContinue())return false
        if(staged&&!repository.activateCandidate(owner,preparation,requireCatchUp=true))return true
        if(assistant.memoryMode()==MemoryExperience.ADVANCED) {
            foreground("Preparing recall")
            repository.index(owner)
            if(!canContinue())return false
            if(calls>0||staged||explicit) {
                repository.put(owner,"meta","meta",repository.metadata(owner).copy(progress="Updating overview",inFlight="summary",inFlightWorker=workerId))
                try { processor.refreshSummary(owner) }
                catch(error:MemoryOutputException) {
                    // Structured facts are already safe; a prose formatting failure must not stop recall.
                    val current=repository.view(owner)
                    repository.put(owner,"meta","meta",current.metadata.copy(summary=compactMemoryOverview(current.facts.filter{it.context==assistant.memoryContext})))
                }
            }
        }
        repository.database.withTransaction {
            val current=repository.metadata(owner)
            if(canContinue()) {
                val skipped=repository.dao.all(owner).count{it.kind=="rejected"}
                repository.put(owner,"meta","meta",current.copy(progress="",pendingRefresh=false,inFlight=null,inFlightWorker=null,retryCount=0,
                    updatedAt=System.currentTimeMillis(),error=if(skipped>0)"$skipped parts couldn't be organized. Your saved details are retained. Retry will check the affected parts." else null))
            }
        }
        return false
    }
}
