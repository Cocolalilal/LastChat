package me.rerere.rikkahub.ui.components.avatar

/**
 * The six face states the avatar can show. Pure (no Android): the chat → face
 * mapping lives in [blobLifecycleFromChat] (see BlobLifecycleChat.kt).
 */
enum class BlobLifecycle {
    Idle,
    Thinking,
    Working,
    Waiting,
    Blocked,
    Done;

    internal fun isActiveWork(): Boolean = when (this) {
        Thinking, Working, Waiting, Blocked -> true
        Idle, Done -> false
    }
}
