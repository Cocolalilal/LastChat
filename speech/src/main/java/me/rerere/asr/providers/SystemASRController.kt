package me.rerere.asr.providers

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.rerere.asr.ASRController
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.ASRState
import me.rerere.asr.ASRStatus
import me.rerere.asr.appendAmplitude
import java.util.Locale

class SystemASRController(
    private val context: Context,
    private val provider: ASRProviderSetting.SystemSTT,
) : ASRController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(
        ASRState(isAvailable = SpeechRecognizer.isRecognitionAvailable(context))
    )
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var onTranscriptChange: ((String) -> Unit)? = null
    private var finalTranscript = ""
    private var partialTranscript = ""

    override fun start(onTranscriptChange: (String) -> Unit) {
        if (state.value.isRecording) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            setError("System speech recognition is not available")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setError("Microphone permission is required")
            return
        }

        this.onTranscriptChange = onTranscriptChange
        finalTranscript = ""
        partialTranscript = ""
        _state.update {
            ASRState(
                status = ASRStatus.Connecting,
                isAvailable = true,
            )
        }

        scope.launch {
            recognizer?.destroy()
            recognizer = createRecognizer().also { speechRecognizer ->
                speechRecognizer.setRecognitionListener(listener())
                speechRecognizer.startListening(createIntent())
            }
        }
    }

    override fun stop() {
        scope.launch {
            _state.update { it.copy(status = ASRStatus.Stopping) }
            recognizer?.stopListening()
        }
    }

    override fun dispose() {
        scope.launch {
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }
        scope.cancel()
    }

    private fun createRecognizer(): SpeechRecognizer {
        val component = provider.componentName()
        return if (component != null) {
            SpeechRecognizer.createSpeechRecognizer(context, component)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }

    private fun createIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, provider.partialResults)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, provider.preferOffline)
            provider.language.trim().takeIf { it.isNotBlank() }?.let { language ->
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            }
        }
    }

    private fun listener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.update { it.copy(status = ASRStatus.Listening, errorMessage = null) }
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _state.update { it.copy(amplitudes = it.amplitudes.appendAmplitude(normalized)) }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            _state.update { it.copy(status = ASRStatus.Stopping) }
        }

        override fun onError(error: Int) {
            setError(systemSpeechError(error))
        }

        override fun onResults(results: Bundle?) {
            finalTranscript = results.bestText()
            partialTranscript = ""
            publishTranscript()
            _state.update { it.copy(status = ASRStatus.Idle, errorMessage = null) }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialTranscript = partialResults.bestText()
            publishTranscript()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun publishTranscript() {
        val transcript = finalTranscript.ifBlank { partialTranscript }.trim()
        _state.update { it.copy(transcript = transcript, errorMessage = null) }
        onTranscriptChange?.invoke(transcript)
    }

    private fun setError(message: String) {
        _state.update {
            it.copy(
                status = ASRStatus.Error,
                isAvailable = SpeechRecognizer.isRecognitionAvailable(context),
                errorMessage = message,
            )
        }
    }
}

private fun ASRProviderSetting.SystemSTT.componentName(): ComponentName? {
    val packageName = servicePackageName?.takeIf { it.isNotBlank() } ?: return null
    val className = serviceClassName?.takeIf { it.isNotBlank() } ?: return null
    return ComponentName(packageName, className)
}

private fun Bundle?.bestText(): String {
    return this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        .orEmpty()
}

private fun systemSpeechError(error: Int): String {
    return when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
        SpeechRecognizer.ERROR_NETWORK -> "Speech recognition network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
        SpeechRecognizer.ERROR_SERVER -> "Speech recognition service error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Speech recognition failed (${String.format(Locale.US, "%d", error)})"
    }
}
