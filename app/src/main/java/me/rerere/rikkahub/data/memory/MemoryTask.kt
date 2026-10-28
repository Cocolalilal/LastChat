package me.rerere.rikkahub.data.memory

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import androidx.work.await
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.uuid.Uuid

/** Work belongs to durable storage and WorkManager, never to a screen's composition. */
@Serializable
data class MemoryTask(
    val id: String,
    val request: String,
    val context: String,
    val configuration: String,
    val focusId: String? = null,
    val previousDraftId: String? = null,
    val state: String = "queued",
    val workerId: String? = null,
    val draftId: String? = null,
    val activities: List<MemoryActivity> = emptyList(),
    val error: String? = null,
    val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val active: Boolean get() = state == "queued" || state == "running"
}

class MemoryTaskCancelled : CancellationException("Memory request was cancelled")

class MemoryTaskController(
    private val context: Context,
    private val repository: CharacterMemoryRepository,
    private val appScope: AppScope,
    private val schedule: (String) -> Unit = { MemoryLearningWorker.enqueue(context,it) },
) {
    fun submit(owner: String, request: String, focusId: String? = null, previousDraftId: String? = null): String {
        val id = Uuid.random().toString()
        appScope.launch(Dispatchers.IO) {
            try { enqueue(owner,id,request,focusId,previousDraftId) }
            catch(error:Exception) {
                if(error is CancellationException)throw error
                val assistant=runCatching{repository.owner(owner)}.getOrNull()?:return@launch
                repository.put(owner,"task",id,MemoryTask(id,request.take(8000),assistant.memoryContext,repository.configuration(owner),state="failed",error=memoryWorkError(error)))
            }
        }
        return id
    }

    suspend fun enqueue(owner:String,id:String,request:String,focusId:String?=null,previousDraftId:String?=null) {
        repository.initialise(owner)
        require(request.isNotBlank() && request.length<=8000) { "Keep this request under 8,000 characters." }
        val assistant=repository.owner(owner)
        require(assistant.enableMemory) { "Enable memory before requesting a change." }
        repository.put(owner,"task",id,MemoryTask(id,request,assistant.memoryContext,repository.configuration(owner),focusId,previousDraftId))
        schedule(owner)
    }

    suspend fun cancel(owner:String,id:String) {
        val task=repository.task(owner,id)?:return
        repository.database.withTransaction {
            val current=repository.task(owner,id)?:return@withTransaction
            repository.put(owner,"task",id,current.copy(state="cancelled",error=null,activities=current.activities.map{it.copy(running=false)}))
            repository.dao.all(owner).filter{it.kind=="draft"}.map{JsonInstant.decodeFromString<MemoryDraft>(it.payload)}.filter{it.taskId==id}.forEach{repository.dao.delete(owner,"draft",it.id)}
        }
        task.workerId?.let { kotlinx.coroutines.withContext(Dispatchers.IO) { runCatching { WorkManager.getInstance(context).cancelWorkById(java.util.UUID.fromString(it)).await() } } }
        schedule(owner)
    }

    suspend fun discard(owner:String,draft:MemoryDraft) = repository.database.withTransaction {
        repository.dao.delete(owner,"draft",draft.id)
        draft.taskId?.let{repository.dao.delete(owner,"task",it)}
    }

    fun requestUpdate(owner:String,retryRejected:Boolean=false) {
        appScope.launch(Dispatchers.IO) {
          try {
            repository.initialise(owner)
            repository.database.withTransaction {
                val meta=repository.metadata(owner)
                if(retryRejected)repository.dao.all(owner).filter{it.kind=="rejected"}.forEach { row ->
                    for(kind in listOf("receipt","candidate-receipt")) {
                        repository.dao.delete(owner,kind,row.id)
                        repository.dao.delete(owner,kind,row.id.substringBeforeLast(':'))
                    }
                    repository.dao.delete(owner,"rejected",row.id)
                }
                repository.put(owner,"meta","meta",meta.copy(pendingRefresh=true,paused=false,cancelled=false,error=null,progress="Queued",inFlight=null,inFlightWorker=null))
            }
            val assistant=repository.owner(owner)
            if(assistant.memoryMode()==MemoryExperience.ADVANCED&&!repository.metadata(owner).activeAdvanced)repository.startPreparation(owner)
            schedule(owner)
          } catch(error:Exception) {
            if(error is CancellationException)throw error
            repository.database.withTransaction {
                val meta=repository.metadata(owner)
                repository.put(owner,"meta","meta",meta.copy(progress="",error=memoryWorkError(error)))
            }
          }
        }
    }

    suspend fun retry(owner:String,id:String) {
        repository.database.withTransaction {
            val task=repository.task(owner,id)?:return@withTransaction
            require(task.context==repository.owner(owner).memoryContext) { "Switch back to ${task.context} before retrying this request." }
            repository.put(owner,"task",id,task.copy(state="queued",workerId=null,error=null,attempts=0,configuration=repository.configuration(owner)))
        }
        schedule(owner)
    }

    suspend fun recover() {
        val owners=repository.database.openHelper.readableDatabase.query("SELECT DISTINCT owner FROM character_memory WHERE kind IN ('task','meta')").use { c -> buildList { while(c.moveToNext())add(c.getString(0)) } }
        for(owner in owners) {
            val assistant=runCatching{repository.owner(owner)}.getOrNull()?:continue
            if(!assistant.enableMemory)continue
            repository.initialise(owner)
            val meta=repository.metadata(owner)
            if(repository.tasks(owner).any{it.active} || (!meta.paused&&!meta.cancelled&&meta.error==null&&(meta.preparing||meta.pendingRefresh)))schedule(owner)
        }
    }
}

internal fun memoryWorkError(error:Throwable):String = when {
    error.message?.contains("context",true)==true && error.message?.contains("exceed",true)==true -> "This request is too large for the selected processing model. Choose a larger context or narrow the request."
    error.message?.contains("provider",true)==true || error.message?.contains("model",true)==true && error.message?.contains("Choose",true)==true -> error.message.orEmpty().take(400)
    error is java.io.IOException -> "The processing provider could not be reached. Your saved memory is unchanged."
    error.message?.contains("No clear change",true)==true -> "I couldn't identify a specific change. Name the detail you want to add, correct or forget."
    error.message == "Check failed." || error.message == "Failed requirement." -> "Memory changed while this update was being prepared. Retry will use its current state."
    else -> error.message?.takeIf{it.isNotBlank()}?.take(500) ?: "Memory could not be updated. Your saved information is unchanged."
}

suspend fun CharacterMemoryRepository.task(owner:String,id:String):MemoryTask? =
    dao.get(owner,"task",id)?.let{JsonInstant.decodeFromString<MemoryTask>(it.payload)}
suspend fun CharacterMemoryRepository.tasks(owner:String):List<MemoryTask> =
    dao.all(owner).filter{it.kind=="task"}.map{JsonInstant.decodeFromString<MemoryTask>(it.payload)}.sortedBy{it.createdAt}

/** Persists every visible state; it can run with no activity, navigation entry or popup alive. */
class MemoryTaskRunner(private val repository:CharacterMemoryRepository,private val processor:MemoryProcessor) {
    suspend fun runOne(owner:String,workerId:String,onForeground:suspend (String)->Unit):Boolean {
        val task=repository.tasks(owner).firstOrNull{it.active}?:return false
        if(task.configuration!=repository.configuration(owner)||task.context!=repository.owner(owner).memoryContext){
            repository.put(owner,"task",task.id,task.copy(state="failed",error="Memory mode or context changed. Retry in the context where you want this edit."))
            return true
        }
        if(task.attempts>=3){
            repository.put(owner,"task",task.id,task.copy(state="failed",error="This request could not finish after being interrupted. Retry when you're ready."))
            return true
        }
        onForeground("Preparing changes")
        repository.put(owner,"task",task.id,task.copy(state="running",workerId=workerId,attempts=task.attempts+1,error=null,activities=emptyList()))
        try {
            kotlinx.coroutines.coroutineScope {
                val events=kotlinx.coroutines.channels.Channel<MemoryActivity>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                val writer=launch(Dispatchers.IO) {
                    for(event in events)repository.database.withTransaction {
                        val current=repository.task(owner,task.id)?:return@withTransaction
                        if(current.state=="running"&&current.workerId==workerId)repository.put(owner,"task",task.id,current.copy(activities=current.activities.filterNot{it.label==event.label}+event))
                    }
                }
                val draft=try {
                    processor.request(owner,task.request,task.focusId,taskId=task.id,commitGuard={
                        val current=repository.task(owner,task.id)
                        if(current?.state!="running"||current.workerId!=workerId)throw MemoryTaskCancelled()
                    },onActivity={events.trySend(it)})
                } finally { events.close() }
                writer.join()
                repository.database.withTransaction {
                    val current=repository.task(owner,task.id)
                    if(current?.state!="running"||current.workerId!=workerId) {
                        repository.dao.delete(owner,"draft",draft.id)
                        throw MemoryTaskCancelled()
                    }
                    repository.put(owner,"task",task.id,current.copy(state="ready",draftId=draft.id,activities=current.activities.map{it.copy(running=false)}))
                    task.previousDraftId?.takeIf{it!=draft.id}?.let { previous ->
                        repository.dao.delete(owner,"draft",previous)
                        repository.tasks(owner).filter{it.id!=task.id&&it.draftId==previous}.forEach{repository.dao.delete(owner,"task",it.id)}
                    }
                }
            }
        } catch(error:CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                repository.database.withTransaction {
                    val current=repository.task(owner,task.id)?:return@withTransaction
                    if(current.state=="running")repository.put(owner,"task",task.id,current.copy(state="queued",workerId=null,activities=current.activities.map{it.copy(running=false)}))
                }
            }
            if(error is MemoryTaskCancelled)return true
            throw error
        } catch(error:Exception) {
            repository.database.withTransaction {
                val current=repository.task(owner,task.id)?:return@withTransaction
                if(current.state=="running")repository.put(owner,"task",task.id,current.copy(state="failed",error=memoryWorkError(error),activities=current.activities.map{it.copy(running=false)}))
            }
        }
        return true
    }
}
