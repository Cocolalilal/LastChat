package me.rerere.common.platform

/** Opens a local or remote attachment with the platform's native viewer. */
fun interface PlatformAttachmentOpener {
    /** Returns true when the platform accepted the request for presentation. */
    fun open(url: String): Boolean
}
