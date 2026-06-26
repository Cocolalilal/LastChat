package me.rerere.rikkahub.ui.hooks

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.asr.ASRController
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.Model
import me.rerere.asr.ASRState
import me.rerere.asr.providers.OpenAICompatibleASRController
import me.rerere.rikkahub.data.datastore.SettingsStore
import okhttp3.OkHttpClient
import org.koin.compose.koinInject
import me.rerere.ai.provider.ModelType

@Composable
fun rememberCustomSttState(): CustomSttState {
    val context = LocalContext.current
    val settingsStore = koinInject<SettingsStore>()
    val httpClient = koinInject<OkHttpClient>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    val sttState = remember {
        CustomSttStateImpl(context.applicationContext, httpClient)
    }

    val sttModelId = settings.sttModelId
    val provider = remember(sttModelId, settings.providers) {
        if (sttModelId != null) {
            settings.providers.firstOrNull { it.models.any { model -> model.id == sttModelId } }
        } else null
    }
    val model = remember(sttModelId, provider) {
        provider?.models?.firstOrNull { it.id == sttModelId }
    }

    DisposableEffect(provider, model) {
        sttState.updateProvider(provider, model)
        onDispose { }
    }

    DisposableEffect(sttState) {
        onDispose {
            sttState.cleanup()
        }
    }

    return sttState
}

interface CustomSttState {
    val state: StateFlow<ASRState>
    fun start(onTranscriptChange: (String) -> Unit)
    fun stop()
    fun clearError()
    fun cleanup()
}

private class CustomSttStateImpl(
    private val context: Context,
    private val httpClient: OkHttpClient,
) : CustomSttState {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var controller: ASRController? = null
    private var controllerJob: kotlinx.coroutines.Job? = null
    
    private val _state = MutableStateFlow(ASRState())
    override val state: StateFlow<ASRState> = _state.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAcceptsDelayedFocusGain(false)
        .build()

    fun updateProvider(provider: ProviderSetting?, model: Model?) {
        controllerJob?.cancel()
        controller?.dispose()
        
        val newController = if (provider != null && model != null) {
            createController(provider, model)
        } else null
        controller = newController
        
        if (newController == null) {
            _state.value = ASRState()
        } else {
            controllerJob = scope.launch {
                newController.state.collect { 
                    _state.value = it 
                }
            }
        }
    }

    override fun start(onTranscriptChange: (String) -> Unit) {
        val controller = controller
        if (controller != null && controller.needsAudioFocus) {
            val result = audioManager.requestAudioFocus(audioFocusRequest)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        }
        controller?.start(onTranscriptChange)
    }

    override fun stop() {
        controller?.stop()
        if (controller?.needsAudioFocus == true) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
        }
    }

    override fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    override fun cleanup() {
        controllerJob?.cancel()
        controller?.dispose()
        controller = null
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        scope.cancel()
    }

    private fun createController(provider: ProviderSetting, model: Model): ASRController? {
        if (model.type == ModelType.STT) {
            return when (provider) {
                is ProviderSetting.OpenAI -> OpenAICompatibleASRController(context, httpClient, provider, model)
                else -> null
            }
        }
        return null
    }
}
