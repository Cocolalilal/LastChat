package me.rerere.common.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Plays a local or remote audio attachment inline. */
interface PlatformAttachmentAudioPlayer {
    val playingUrl: StateFlow<String?>

    fun toggle(url: String)

    fun stop()
}

class UnavailableAttachmentAudioPlayer : PlatformAttachmentAudioPlayer {
    override val playingUrl: StateFlow<String?> = MutableStateFlow(null)

    override fun toggle(url: String) = Unit

    override fun stop() = Unit
}
