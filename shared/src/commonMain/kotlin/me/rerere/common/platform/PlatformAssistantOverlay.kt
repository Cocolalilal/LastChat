package me.rerere.common.platform

/**
 * Digital-assistant overlay contract. Android hosts a translucent activity;
 * iOS presents the same chat composer as a Compose overlay in-process.
 */
interface PlatformAssistantOverlay {
    val available: Boolean get() = true

    fun present()

    fun dismiss()
}

class UnavailableAssistantOverlay(
    override val available: Boolean = false,
) : PlatformAssistantOverlay {
    override fun present() = Unit

    override fun dismiss() = Unit
}
