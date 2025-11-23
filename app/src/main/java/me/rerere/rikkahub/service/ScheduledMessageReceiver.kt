package me.rerere.rikkahub.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import me.rerere.common.android.Logging

class ScheduledMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val assistantId = intent.getStringExtra("assistantId") ?: return
        val conversationId = intent.getStringExtra("conversationId") ?: return
        val reason = intent.getStringExtra("reason") ?: "No reason provided"

        Logging.log("ScheduledMessageReceiver", "Received alarm for assistant $assistantId, conversation $conversationId")

        val workRequest = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
            .setInputData(
                workDataOf(
                    "assistantId" to assistantId,
                    "conversationId" to conversationId,
                    "reason" to reason
                )
            )
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
