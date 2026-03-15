package me.rerere.rikkahub.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import me.rerere.common.android.Logging

class ScheduledMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val assistantId = intent.getStringExtra("assistantId") ?: return
        val conversationId = intent.getStringExtra("conversationId") ?: return
        val reason = intent.getStringExtra("reason") ?: "No reason provided"
        val now = System.currentTimeMillis()

        Logging.log("ScheduledMessageReceiver", "Received legacy scheduled-message broadcast for assistant $assistantId, conversation $conversationId")

        val uniqueWorkName = ScheduledMessageWorkSpec.buildUniqueWorkName(
            assistantId = assistantId,
            conversationId = conversationId,
            reason = reason,
            scheduledAtMillis = now
        )
        val workRequest = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30,
                java.util.concurrent.TimeUnit.SECONDS
            )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                ScheduledMessageWorkSpec.buildInputData(
                    assistantId = assistantId,
                    conversationId = conversationId,
                    reason = reason,
                    createdAtMillis = now,
                    scheduledAtMillis = now
                )
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest
        )
    }
}
