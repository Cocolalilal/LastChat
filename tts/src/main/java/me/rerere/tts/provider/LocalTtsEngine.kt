package me.rerere.tts.provider

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalTtsEngine(
    val packageName: String,
    val label: String,
)

suspend fun discoverLocalTtsEngines(context: Context): List<LocalTtsEngine> = withContext(Dispatchers.IO) {
    val packageManager = context.packageManager
    val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
    val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.queryIntentServices(
            intent,
            PackageManager.ResolveInfoFlags.of(0L)
        )
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentServices(intent, 0)
    }

    services
        .mapNotNull { resolveInfo ->
            val serviceInfo = resolveInfo.serviceInfo ?: return@mapNotNull null
            val packageName = serviceInfo.packageName.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val label = resolveInfo.loadLabel(packageManager)?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: serviceInfo.applicationInfo?.loadLabel(packageManager)?.toString()
                    ?.takeIf { it.isNotBlank() }
                ?: packageName

            LocalTtsEngine(
                packageName = packageName,
                label = label,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}
