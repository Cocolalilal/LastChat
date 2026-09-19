package me.rerere.tts.controller

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.PlaybackState
import me.rerere.tts.model.PlaybackStatus
import me.rerere.tts.model.TTSResponse
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.create
import platform.darwin.NSObject

/** AVFoundation playback implementation used by the shared TTS queue on iOS. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosTtsAudioPlayer : TtsAudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutablePlaybackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = mutablePlaybackState.asStateFlow()

    private var player: AVAudioPlayer? = null
    private var delegate: PlaybackDelegate? = null
    private var positionJob: Job? = null
    private var speed = 1.0f
    private var totalChunksCount = 0
    private val queuedItems = mutableListOf<QueuedAudioItem>()

    private data class QueuedAudioItem(
        val chunkIndex: Int,
        val response: TTSResponse
    )

    override fun pause() {
        player?.pause()
        stopPositionUpdates()
        mutablePlaybackState.update { it.copy(status = PlaybackStatus.Paused) }
    }

    override fun resume() {
        if (player == null && queuedItems.isNotEmpty()) {
            playNextQueued()
        } else {
            player?.play()
            startPositionUpdates()
            mutablePlaybackState.update { it.copy(status = PlaybackStatus.Playing) }
        }
    }

    override fun stop() {
        player?.stop()
        clear()
        stopPositionUpdates()
        mutablePlaybackState.update { it.copy(status = PlaybackStatus.Idle, positionMs = 0L) }
    }

    override fun clear() {
        player?.delegate = null
        player = null
        delegate = null
        queuedItems.clear()
        totalChunksCount = 0
    }

    override fun release() {
        stop()
        scope.cancel()
    }

    override fun seekBy(ms: Long) {
        val current = player ?: return
        current.currentTime = (current.currentTime + ms / 1_000.0)
            .coerceIn(0.0, current.duration)
        publishPosition(current)
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
        player?.let { current ->
            current.enableRate = true
            current.rate = speed
        }
        mutablePlaybackState.update { it.copy(speed = speed) }
    }

    override fun skipNext() {
        if (queuedItems.isNotEmpty()) {
            player?.stop()
            player?.delegate = null
            player = null
            delegate = null
            playNextQueued()
        }
    }

    override fun setTotalChunks(total: Int) {
        totalChunksCount = total
        mutablePlaybackState.update { it.copy(totalChunks = total) }
    }

    override fun enqueue(chunkIndex: Int, totalChunks: Int, response: TTSResponse) {
        totalChunksCount = totalChunks
        queuedItems.add(QueuedAudioItem(chunkIndex, response))
        mutablePlaybackState.update {
            it.copy(
                totalChunks = totalChunks,
                status = if (player?.isPlaying() == true) PlaybackStatus.Playing else PlaybackStatus.Buffering
            )
        }

        if (player == null || player?.isPlaying() != true) {
            playNextQueued()
        }
    }

    private fun playNextQueued() {
        if (queuedItems.isEmpty()) {
            stopPositionUpdates()
            mutablePlaybackState.update { it.copy(status = PlaybackStatus.Ended) }
            return
        }

        val item = queuedItems.removeAt(0)
        val bytes = if (item.response.format == AudioFormat.PCM) {
            pcmToWavBytes(item.response.audioData, item.response.sampleRate ?: 24_000)
        } else {
            item.response.audioData
        }

        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)

        val current = AVAudioPlayer(data = bytes.toNSData(), error = null)
        val playbackDelegate = PlaybackDelegate(
            onFinished = {
                stopPositionUpdates()
                playNextQueued()
            },
            onError = { error ->
                stopPositionUpdates()
                mutablePlaybackState.update {
                    it.copy(status = PlaybackStatus.Error, errorMessage = error.localizedDescription)
                }
                playNextQueued()
            },
        )
        current.delegate = playbackDelegate
        current.enableRate = true
        current.rate = speed
        player = current
        delegate = playbackDelegate

        val current1Based = item.chunkIndex + 1
        mutablePlaybackState.update {
            it.copy(
                status = PlaybackStatus.Buffering,
                positionMs = 0L,
                durationMs = (current.duration * 1_000).toLong(),
                speed = speed,
                currentChunkIndex = current1Based,
                totalChunks = totalChunksCount.coerceAtLeast(current1Based),
                errorMessage = null,
            )
        }

        current.prepareToPlay()
        if (current.play()) {
            mutablePlaybackState.update { it.copy(status = PlaybackStatus.Playing) }
            startPositionUpdates()
        } else {
            mutablePlaybackState.update {
                it.copy(status = PlaybackStatus.Error, errorMessage = "AVAudioPlayer could not start playback")
            }
            playNextQueued()
        }
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = scope.launch {
            while (isActive) {
                player?.let(::publishPosition)
                delay(100)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun publishPosition(current: AVAudioPlayer) {
        mutablePlaybackState.update {
            it.copy(
                positionMs = (current.currentTime * 1_000).toLong(),
                durationMs = (current.duration * 1_000).toLong(),
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PlaybackDelegate(
    private val onFinished: () -> Unit,
    private val onError: (NSError) -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {
    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        onFinished()
    }

    override fun audioPlayerDecodeErrorDidOccur(player: AVAudioPlayer, error: NSError?) {
        onError(error ?: NSError.errorWithDomain("LastChatTTS", 1, null))
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData = if (isEmpty()) {
    NSData()
} else {
    usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}

