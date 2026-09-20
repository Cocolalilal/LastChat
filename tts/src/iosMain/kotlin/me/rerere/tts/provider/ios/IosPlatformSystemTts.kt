package me.rerere.tts.provider.ios

import me.rerere.tts.provider.PlatformSystemTts
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.AVSpeechUtteranceDefaultSpeechRate

/** AVSpeech implementation of Android's system TTS branch. */
class IosPlatformSystemTts : PlatformSystemTts {
    private val synthesizer = AVSpeechSynthesizer()
    private var onDone: (() -> Unit)? = null

    override val available: Boolean = true

    override fun speak(text: String, speechRate: Float, pitch: Float, onDone: () -> Unit) {
        if (text.isBlank()) {
            onDone()
            return
        }
        stop()
        this.onDone = onDone
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.rate = (AVSpeechUtteranceDefaultSpeechRate * speechRate).coerceIn(0.1f, 1.0f)
        utterance.pitchMultiplier = pitch.coerceIn(0.5f, 2.0f)
        synthesizer.speakUtterance(utterance)
    }

    override fun stop() {
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        val done = onDone
        onDone = null
        done?.invoke()
    }
}
