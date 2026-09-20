package me.rerere.common.archive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PortableTarBz2Test {
    @Test
    fun extractsRequiredFilesAndSkipsTraversal() {
        val archive = FIXTURE_HEX.hexToByteArray()
        val extracted = PortableTarBz2.extractRequiredFiles(
            archive,
            setOf("encoder.onnx", "tokens.txt"),
        )
        assertEquals(setOf("encoder.onnx", "tokens.txt"), extracted.keys)
        assertEquals("onnx-bytes", extracted.getValue("encoder.onnx").decodeToString())
        assertEquals("hello-tokens", extracted.getValue("tokens.txt").decodeToString())
    }

    @Test
    fun extractsEverySafeFileWhenRequiredSetIsEmpty() {
        val extracted = PortableTarBz2.extractRequiredFiles(FIXTURE_HEX.hexToByteArray(), emptySet())
        assertEquals("onnx-bytes", extracted.getValue("model/encoder.onnx").decodeToString())
        assertEquals("hello-tokens", extracted.getValue("tokens.txt").decodeToString())
        assertEquals("nope", extracted.getValue("skip.me").decodeToString())
        assertTrue(extracted.keys.none { it.contains("..") })
    }

    @Test
    fun reportsMissingRequiredFiles() {
        val error = assertFailsWith<IllegalStateException> {
            PortableTarBz2.extractRequiredFiles(
                FIXTURE_HEX.hexToByteArray(),
                setOf("missing.bin"),
            )
        }
        assertTrue(error.message.orEmpty().contains("missing.bin"))
    }

    @Test
    fun rejectsNonBz2Bytes() {
        assertFailsWith<IllegalStateException> {
            PortableTarBz2.extractRequiredFiles("not-bzip".encodeToByteArray(), setOf("tokens.txt"))
        }
    }

    @Test
    fun pathSafetyRejectsParentSegments() {
        assertTrue(PortableTarBz2.isSafeRelativePath("encoder.onnx"))
        assertTrue(!PortableTarBz2.isSafeRelativePath("../evil.bin"))
        assertTrue(!PortableTarBz2.isSafeRelativePath("foo/../bar"))
    }
}

private const val FIXTURE_HEX =
    "425a6839314159265359442fd3990000d1ff80cb8010004003fd80000103207e6fdf6028283000b882554f5369a1ea434f48c9ea64c99069e9a8f50c69a0d0032680c8d343134605514d46483d4d347a04c65320f4134efd39637e732c9318a9508facd10885da6cbb1285a2635682b33110909867fdefaede7e7a299359e27ac90f224437df861942eaea2faa265f192670a69a55a8dea58576f816246e38e38387bbde9e1e48915abe8f6218c0d0c9bf16b609b46933615093328a2008b52a3f81f5045d4ee8820870e7706c0fe2ee48a70a120885fa7320"
