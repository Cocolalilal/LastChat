package me.rerere.lastchat.ios

internal expect object IosNativeFiles {
    val webUiBundled: Boolean

    fun loadWebAsset(relativePath: String): ByteArray?

    fun readUrl(url: String): ByteArray?

    fun loadBundledCatalog(fileName: String): String?
}
