package me.rerere.rikkahub.data.memory

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal fun memoryForegroundInfo(context:Context,owner:String,name:String,phase:String,taskId:String?=null):ForegroundInfo {
    val manager=context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel("memory-work","Memory processing",NotificationManager.IMPORTANCE_LOW))
    val id=12000+(owner.hashCode() and 0x7fffffff)%1000000
    val open=PendingIntent.getActivity(context,id,Intent(context,RouteActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val control=PendingIntent.getBroadcast(context,id,Intent(context,MemoryWorkActionReceiver::class.java)
        .putExtra("owner",owner).putExtra("task",taskId),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification=NotificationCompat.Builder(context,"memory-work")
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("${name.ifBlank{"Character"}} · Memory")
        .setContentText(phase).setContentIntent(open).setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS).setProgress(0,0,true)
        .addAction(0,if(taskId==null)"Pause" else "Cancel",control).build()
    return ForegroundInfo(id,notification,if(Build.VERSION.SDK_INT>=29)ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
}

class MemoryWorkActionReceiver:BroadcastReceiver(),KoinComponent {
    private val repository:CharacterMemoryRepository by inject()
    private val controller:MemoryTaskController by inject()
    private val scope:AppScope by inject()
    override fun onReceive(context:Context,intent:Intent) {
        val owner=intent.getStringExtra("owner")?:return
        val pending=goAsync()
        scope.launch(Dispatchers.IO) {
            try {
                val task=intent.getStringExtra("task")
                if(task!=null)controller.cancel(owner,task)
                else {
                    repository.configure(owner){it.copy(paused=true,inFlight=null,inFlightWorker=null)}
                    WorkManager.getInstance(context).cancelUniqueWork("character-memory:$owner")
                }
            } finally { pending.finish() }
        }
    }
}
