package me.rerere.rikkahub.data.memory

import android.content.Context
import androidx.room.withTransaction
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.SettingsStore
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/** Durable foreground work; navigation and activity scopes do not own generation. */
class MemoryLearningWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params),KoinComponent {
    private val repository:CharacterMemoryRepository by inject()
    private val processor:MemoryProcessor by inject()
    private val settings:SettingsStore by inject()
    private val taskRunner:MemoryTaskRunner by inject()

    override suspend fun getForegroundInfo():ForegroundInfo {
        val owner=inputData.getString("owner").orEmpty()
        return memoryForegroundInfo(applicationContext,owner,runCatching{repository.owner(owner).name}.getOrDefault("Character"),"Updating memory")
    }
    override suspend fun doWork():Result=withContext(Dispatchers.IO) {
        settings.settingsFlow.first{!it.init}
        val owner=inputData.getString("owner")?:return@withContext Result.failure()
        if(runCatching{repository.owner(owner).enableMemory}.getOrDefault(false).not())return@withContext Result.success()
        repository.initialise(owner)
        val workerId=id.toString()
        suspend fun foreground(phase:String,task:String?=null) {
            setForeground(memoryForegroundInfo(applicationContext,owner,repository.owner(owner).name,phase,task))
        }
        try {
            // Older interrupted markers no longer become a permanent pause or a billing claim.
            repository.database.withTransaction {
                val meta=repository.metadata(owner)
                repository.put(owner,"meta","meta",meta.copy(inFlight=null,inFlightWorker=null,
                    pendingRefresh=meta.pendingRefresh||inputData.getBoolean("refresh",false)))
            }
            var edited=0
            while(edited<4) {
                val next=repository.tasks(owner).firstOrNull{it.active}?:break
                taskRunner.runOne(owner,workerId){phase->foreground(phase,next.id)}
                edited++
            }
            if(repository.tasks(owner).any{it.active}) {
                enqueue(applicationContext,owner)
                return@withContext Result.success()
            }
            val more=MemoryLearningPass(repository,processor,owner,workerId){foreground(it)}.run()
            if(more)enqueue(applicationContext,owner,5)
            Result.success()
        } catch(error:CancellationException) {
            throw error
        } catch(error:Exception) {
            val transient=error is java.io.IOException || error.message?.let{it.contains("429")||it.contains("503")||it.contains("502")||it.contains("foreground",true)}==true
            repository.database.withTransaction {
                val meta=repository.metadata(owner)
                repository.put(owner,"meta","meta",meta.copy(inFlight=null,inFlightWorker=null,retryCount=meta.retryCount+1,
                    progress=if(transient&&runAttemptCount<2)"Waiting to retry" else "",error=if(transient&&runAttemptCount<2)null else memoryWorkError(error)))
            }
            if(transient&&runAttemptCount<2)Result.retry() else Result.failure()
        } finally {
            withContext(NonCancellable+Dispatchers.IO) {
                repository.database.withTransaction {
                    val meta=repository.metadata(owner)
                    if(meta.inFlightWorker==workerId)repository.put(owner,"meta","meta",meta.copy(inFlight=null,inFlightWorker=null))
                }
            }
        }
    }
    companion object {
        fun enqueue(context:Context,owner:String,delaySeconds:Long=0,refresh:Boolean=false) {
            WorkManager.getInstance(context).enqueueUniqueWork("character-memory:$owner",ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<MemoryLearningWorker>().setInputData(workDataOf("owner" to owner,"refresh" to refresh))
                    .setInitialDelay(delaySeconds,TimeUnit.SECONDS).setBackoffCriteria(BackoffPolicy.LINEAR,30,TimeUnit.SECONDS).build())
        }
        fun cancel(context:Context,owner:String){WorkManager.getInstance(context).cancelUniqueWork("character-memory:$owner")}
    }
}
