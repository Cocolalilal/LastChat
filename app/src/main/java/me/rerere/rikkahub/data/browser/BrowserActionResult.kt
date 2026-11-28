package me.rerere.rikkahub.data.browser

/**
 * Result of a browser action execution.
 */
data class BrowserActionResult(
    val text: String,
    val success: Boolean = true,
    /** Base64-encoded JPEG for the vision API. */
    val base64Image: String? = null,
    /** Local file path where the screenshot or auto-snapshot was saved to disk. */
    val imageFilePath: String? = null,
    /** Raw downloaded file data (for fetch action). */
    val fetchedFileData: ByteArray? = null,
    /** Determined filename for the fetched file. */
    val fetchedFileName: String? = null,
    /** The page URL at the time this action was executed. */
    val pageURL: String? = null,
    /** The verified tab id this action ran on. */
    val tabId: Int? = null,
    /** True when the result triggered an automatic hand-over to the user. */
    val handoffRequested: Boolean = false,
) {
    companion object {
        fun error(message: String) = BrowserActionResult(
            text = "Error: $message",
            success = false,
        )
    }
}
