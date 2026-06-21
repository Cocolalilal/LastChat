package me.rerere.asr.android

import android.content.Context
import android.content.Intent
import android.speech.RecognitionService

data class LocalSpeechRecognitionService(
    val label: String,
    val packageName: String,
    val className: String,
)

fun discoverLocalSpeechRecognitionServices(context: Context): List<LocalSpeechRecognitionService> {
    val packageManager = context.packageManager
    val intent = Intent(RecognitionService.SERVICE_INTERFACE)
    return packageManager
        .queryIntentServices(intent, 0)
        .mapNotNull { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
            LocalSpeechRecognitionService(
                label = resolveInfo.loadLabel(packageManager)?.toString().orEmpty()
                    .ifBlank { serviceInfo.packageName },
                packageName = serviceInfo.packageName,
                className = serviceInfo.name,
            )
        }
        .distinctBy { "${it.packageName}/${it.className}" }
        .sortedBy { it.label.lowercase() }
}
