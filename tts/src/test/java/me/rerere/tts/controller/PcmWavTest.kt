package me.rerere.tts.controller

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmWavTest {
    @Test
    fun `pcmToWavBytes writes little endian wav header and payload`() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = pcmToWavBytes(
            pcm = pcm,
            sampleRate = 24_000,
            channels = 1,
            bitsPerSample = 16,
        )

        assertEquals(48, wav.size)
        assertEquals("RIFF", wav.asAscii(0, 4))
        assertEquals(40, wav.readIntLe(4))
        assertEquals("WAVE", wav.asAscii(8, 4))
        assertEquals("fmt ", wav.asAscii(12, 4))
        assertEquals(16, wav.readIntLe(16))
        assertEquals(1, wav.readShortLe(20))
        assertEquals(1, wav.readShortLe(22))
        assertEquals(24_000, wav.readIntLe(24))
        assertEquals(48_000, wav.readIntLe(28))
        assertEquals(2, wav.readShortLe(32))
        assertEquals(16, wav.readShortLe(34))
        assertEquals("data", wav.asAscii(36, 4))
        assertEquals(4, wav.readIntLe(40))
        assertArrayEquals(pcm, wav.copyOfRange(44, 48))
    }

    private fun ByteArray.asAscii(offset: Int, length: Int): String {
        return copyOfRange(offset, offset + length).decodeToString()
    }

    private fun ByteArray.readIntLe(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun ByteArray.readShortLe(offset: Int): Int {
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8)
    }
}
