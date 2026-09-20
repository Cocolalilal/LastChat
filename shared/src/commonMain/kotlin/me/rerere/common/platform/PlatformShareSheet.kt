package me.rerere.common.platform

/** Native share-sheet / activity-view contract used by conversation and message export. */
interface PlatformShareSheet {
    val available: Boolean get() = true

    fun shareText(title: String, text: String)
}

class UnavailableShareSheet(
    override val available: Boolean = false,
) : PlatformShareSheet {
    override fun shareText(title: String, text: String) = Unit
}
