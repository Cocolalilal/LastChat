package me.rerere.tts.provider.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class LocalTtsEngine(
    val packageName: String,
    val label: String,
)

data class LocalTtsVoice(
    val name: String,
    val localeTag: String,
    val quality: Int,
    val latency: Int,
    val requiresNetwork: Boolean,
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

suspend fun discoverLocalTtsVoices(
    context: Context,
    enginePackageName: String?,
): List<LocalTtsVoice> = withContext(Dispatchers.Main.immediate) {
    suspendCancellableCoroutine { continuation ->
        var tts: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            val ttsInstance = tts
            if (status != TextToSpeech.SUCCESS || ttsInstance == null) {
                if (continuation.isActive) continuation.resume(emptyList())
                ttsInstance?.shutdown()
                return@OnInitListener
            }

            val voices = try {
                ttsInstance.voices.orEmpty()
                    .map { voice ->
                        LocalTtsVoice(
                            name = voice.name,
                            localeTag = voice.locale?.toLanguageTag().orEmpty(),
                            quality = voice.quality,
                            latency = voice.latency,
                            requiresNetwork = voice.isNetworkConnectionRequired,
                        )
                    }
                    .distinctBy { it.name }
                    .sortedWith(
                        compareBy<LocalTtsVoice>(
                            { it.requiresNetwork },
                            { it.localeTag != java.util.Locale.getDefault().toLanguageTag() },
                        ).thenBy(String.CASE_INSENSITIVE_ORDER) { it.localeTag }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    )
            } catch (e: Exception) {
                emptyList()
            }

            if (continuation.isActive) continuation.resume(voices)
            ttsInstance.shutdown()
        }

        val engine = enginePackageName?.takeIf { it.isNotBlank() }
        tts = if (engine == null) {
            TextToSpeech(context, listener)
        } else {
            TextToSpeech(context, listener, engine)
        }

        continuation.invokeOnCancellation {
            tts?.shutdown()
        }
    }
}
