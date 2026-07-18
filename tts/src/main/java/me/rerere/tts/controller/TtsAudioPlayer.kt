package me.rerere.tts.controller

import kotlinx.coroutines.flow.StateFlow
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.TTSResponse

/** Platform playback boundary used by the source-shared TTS queue controller. */
interface TtsAudioPlayer {
    val playbackState: StateFlow<PlaybackState>

    fun pause()
    fun resume()
    fun stop()
    fun clear()
    fun release()
    fun seekBy(ms: Long)
    fun setSpeed(speed: Float)
    suspend fun play(response: TTSResponse)
}
