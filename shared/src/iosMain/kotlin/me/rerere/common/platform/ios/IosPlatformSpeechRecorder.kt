package me.rerere.common.platform.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformSpeechRecorder
import platform.AVFAudio.AVAudioQualityHigh
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.posix.memcpy
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)
class IosPlatformSpeechRecorder : PlatformSpeechRecorder {
    private val recording = AtomicBoolean(false)
    private var recorder: AVAudioRecorder? = null
    private var outputUrl: NSURL? = null

    override val isRecording: Boolean
        get() = recording.load()

    override suspend fun start(): Boolean = withContext(Dispatchers.Main) {
        if (recording.load()) return@withContext true
        val session = AVAudioSession.sharedInstance()
        val granted = session.requestRecordPermission()
        if (!granted) return@withContext false
        session.setCategory(AVAudioSessionCategoryRecord, error = null)
        val url = recordingUrl()
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatLinearPCM,
            AVSampleRateKey to 16_000,
            AVNumberOfChannelsKey to 1,
            AVLinearPCMBitDepthKey to 16,
            AVLinearPCMIsFloatKey to false,
            AVEncoderAudioQualityKey to AVAudioQualityHigh,
        )
        val created = AVAudioRecorder(uRL = url, settings = settings, error = null)
        if (!created.prepareToRecord() || !created.record()) {
            return@withContext false
        }
        recorder = created
        outputUrl = url
        recording.store(true)
        true
    }

    override suspend fun stop(): ByteArray? = withContext(Dispatchers.Main) {
        if (!recording.load()) return@withContext null
        recorder?.stop()
        recorder = null
        recording.store(false)
        val url = outputUrl ?: return@withContext null
        val data = NSData.dataWithContentsOfURL(url) ?: return@withContext null
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), data.bytes, data.length) }
        }
        runCatching { NSFileManager.defaultManager.removeItemAtURL(url, error = null) }
        if (bytes.size >= 12 && bytes.decodeToString(0, 4) == "RIFF") bytes else wrapPcmAsWav(bytes)
    }

    private fun recordingUrl(): NSURL {
        return NSURL.fileURLWithPath(NSTemporaryDirectory() + "lastchat-stt.wav")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun wrapPcmAsWav(pcm: ByteArray, sampleRate: Int = 16_000): ByteArray {
    val header = ByteArray(44)
    fun u32(offset: Int, value: Int) {
        header[offset] = (value and 0xff).toByte()
        header[offset + 1] = ((value shr 8) and 0xff).toByte()
        header[offset + 2] = ((value shr 16) and 0xff).toByte()
        header[offset + 3] = ((value shr 24) and 0xff).toByte()
    }
    fun u16(offset: Int, value: Int) {
        header[offset] = (value and 0xff).toByte()
        header[offset + 1] = ((value shr 8) and 0xff).toByte()
    }
    "RIFF".encodeToByteArray().copyInto(header, 0)
    u32(4, 36 + pcm.size)
    "WAVE".encodeToByteArray().copyInto(header, 8)
    "fmt ".encodeToByteArray().copyInto(header, 12)
    u32(16, 16)
    u16(20, 1)
    u16(22, 1)
    u32(24, sampleRate)
    u32(28, sampleRate * 2)
    u16(32, 2)
    u16(34, 16)
    "data".encodeToByteArray().copyInto(header, 36)
    u32(40, pcm.size)
    return header + pcm
}

private suspend fun AVAudioSession.requestRecordPermission(): Boolean =
    suspendCancellableCoroutine { continuation ->
        requestRecordPermission { granted ->
            if (continuation.isActive) continuation.resume(granted)
        }
    }
