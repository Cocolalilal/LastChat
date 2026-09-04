package me.rerere.lastchat.ios

expect object IosZipArchive {
    fun decompressRawDeflate(compressed: ByteArray, expectedUncompressedSize: Int): ByteArray?
    fun extractEntries(zipBytes: ByteArray): Map<String, ByteArray>
    fun createStoredZip(entries: Map<String, ByteArray>): ByteArray
}
