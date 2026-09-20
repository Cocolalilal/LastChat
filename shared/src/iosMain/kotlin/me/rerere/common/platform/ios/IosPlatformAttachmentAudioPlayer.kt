package me.rerere.common.platform.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.common.platform.PlatformAttachmentAudioPlayer
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
class IosPlatformAttachmentAudioPlayer : PlatformAttachmentAudioPlayer {
    private val mutablePlayingUrl = MutableStateFlow<String?>(null)
    private var player: AVAudioPlayer? = null

    override val playingUrl: StateFlow<String?> = mutablePlayingUrl.asStateFlow()

    override fun toggle(url: String) {
        if (url.isBlank()) return
        if (mutablePlayingUrl.value == url && player?.playing == true) {
            stop()
            return
        }
        stop()
        val fileUrl = NSURL.URLWithString(url) ?: return
        val session = platform.AVFAudio.AVAudioSession.sharedInstance()
        session.setCategory(platform.AVFAudio.AVAudioSessionCategoryPlayback, error = null)
        val next = AVAudioPlayer(contentsOfURL = fileUrl, error = null)
        if (!next.prepareToPlay() || !next.play()) {
            next.stop()
            return
        }
        player = next
        mutablePlayingUrl.value = url
    }

    override fun stop() {
        player?.stop()
        player = null
        mutablePlayingUrl.value = null
    }
}
