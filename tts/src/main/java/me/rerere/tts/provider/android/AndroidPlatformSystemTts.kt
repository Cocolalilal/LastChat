package me.rerere.tts.provider.android

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import me.rerere.tts.provider.PlatformSystemTts
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.uuid.Uuid

class AndroidPlatformSystemTts(
    private val context: Context,
) : PlatformSystemTts {
    private val engine = AtomicReference<TextToSpeech?>(null)
    private val pendingDone = AtomicReference<(() -> Unit)?>(null)

    override val available: Boolean = true

    override fun speak(text: String, speechRate: Float, pitch: Float, onDone: () -> Unit) {
        if (text.isBlank()) {
            onDone()
            return
        }
        pendingDone.getAndSet(onDone)?.invoke()
        val existing = engine.get()
        if (existing != null) {
            speakWith(existing, text, speechRate, pitch)
            return
        }
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                pendingDone.getAndSet(null)?.invoke()
                return@TextToSpeech
            }
            engine.set(tts)
            speakWith(tts, text, speechRate, pitch)
        }
    }

    override fun stop() {
        engine.get()?.stop()
        pendingDone.getAndSet(null)?.invoke()
    }

    private fun speakWith(tts: TextToSpeech, text: String, speechRate: Float, pitch: Float) {
        tts.language = Locale.getDefault()
        tts.setSpeechRate(speechRate.coerceIn(0.1f, 3.0f))
        tts.setPitch(pitch.coerceIn(0.5f, 2.0f))
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                pendingDone.getAndSet(null)?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                pendingDone.getAndSet(null)?.invoke()
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, Uuid.random().toString())
    }
}
