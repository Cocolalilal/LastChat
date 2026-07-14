package me.rerere.common.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class Pkcs8RsaPrivateKeyTest {
    @Test
    fun extractsWrappedPkcs1Payload() {
        val pkcs1 = byteArrayOf(0x30, 0x03, 0x02, 0x01, 0x00)
        val pkcs8 = sequence(
            byteArrayOf(0x02, 0x01, 0x00),
            byteArrayOf(0x30, 0x00),
            tlv(0x04, pkcs1),
        )

        assertContentEquals(pkcs1, Pkcs8RsaPrivateKey.extract(pkcs8))
    }

    @Test
    fun supportsLongFormDerLengths() {
        val pkcs1 = ByteArray(130) { it.toByte() }
        val pkcs8 = sequence(
            byteArrayOf(0x02, 0x01, 0x00),
            byteArrayOf(0x30, 0x00),
            tlv(0x04, pkcs1),
        )

        assertContentEquals(pkcs1, Pkcs8RsaPrivateKey.extract(pkcs8))
    }

    @Test
    fun rejectsInvalidDer() {
        assertFailsWith<IllegalArgumentException> {
            Pkcs8RsaPrivateKey.extract(byteArrayOf(0x31, 0x00))
        }
    }

    private fun sequence(vararg parts: ByteArray): ByteArray = tlv(0x30, parts.fold(ByteArray(0), ByteArray::plus))

    private fun tlv(tag: Int, content: ByteArray): ByteArray {
        val length = if (content.size < 0x80) {
            byteArrayOf(content.size.toByte())
        } else {
            byteArrayOf(0x81.toByte(), content.size.toByte())
        }
        return byteArrayOf(tag.toByte()) + length + content
    }
}
