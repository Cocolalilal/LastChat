package me.rerere.common.crypto

/** Extracts the PKCS#1 payload used by Apple Security from a PKCS#8 RSA key. */
internal object Pkcs8RsaPrivateKey {
    fun extract(pkcs8: ByteArray): ByteArray {
        val root = DerReader(pkcs8).read(tag = 0x30)
        val sequence = DerReader(root)
        sequence.read(tag = 0x02) // version
        sequence.read(tag = 0x30) // rsaEncryption algorithm identifier
        return sequence.read(tag = 0x04) // wrapped PKCS#1 RSAPrivateKey
    }
}

private class DerReader(private val bytes: ByteArray) {
    private var offset = 0

    fun read(tag: Int): ByteArray {
        require(readByte() == tag) { "Unexpected DER tag" }
        val firstLength = readByte()
        val length = if (firstLength and 0x80 == 0) {
            firstLength
        } else {
            val count = firstLength and 0x7F
            require(count in 1..4) { "Unsupported DER length" }
            var value = 0
            repeat(count) { value = (value shl 8) or readByte() }
            value
        }
        require(length >= 0 && offset + length <= bytes.size) { "Invalid DER length" }
        return bytes.copyOfRange(offset, offset + length).also { offset += length }
    }

    private fun readByte(): Int {
        require(offset < bytes.size) { "Unexpected end of DER data" }
        return bytes[offset++].toInt() and 0xFF
    }
}
