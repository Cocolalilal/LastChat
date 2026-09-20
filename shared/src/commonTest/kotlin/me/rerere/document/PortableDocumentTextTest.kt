package me.rerere.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortableDocumentTextTest {
    @Test
    fun convertsDocxXmlParagraphsAndHeadingsToMarkdown() {
        val xml = """
            <?xml version="1.0"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p>
                  <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                  <w:r><w:t>Title</w:t></w:r>
                </w:p>
                <w:p>
                  <w:r><w:t>Hello </w:t></w:r>
                  <w:r><w:t>world &amp; friends</w:t></w:r>
                </w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val markdown = PortableDocumentText.docxXmlToMarkdown(xml)
        assertTrue(markdown.contains("# Title"))
        assertTrue(markdown.contains("Hello world & friends"))
    }

    @Test
    fun textFilesDecodeAsUtf8() {
        val parsed = PortableDocumentText.parse(
            fileName = "notes.txt",
            mimeType = "text/plain",
            bytes = "plain notes".encodeToByteArray(),
        )
        assertEquals("plain notes", parsed)
    }

    @Test
    fun unknownTypesStayExplicit() {
        val parsed = PortableDocumentText.parse(
            fileName = "model.bin",
            mimeType = "application/octet-stream",
            bytes = byteArrayOf(1, 2, 3),
        )
        assertTrue(parsed.contains("Unsupported document type"))
    }

    @Test
    fun inflateZlibUnwrapsPngZtxtPayload() {
        val compressed = hexBytes(
            "78daab56ca4bcc4d55b2528a2aa9285170ce482c4a4c2e492d52d2514acb2c2a2e89cf4d2d064a7a642aa415e5e72a5485449400a552528b938b320b4a32f3f380926e209944b09c427246695eb6522d00f6991d56",
        )
        val inflated = inflateZlib(compressed, 1024)
        assertEquals(
            """{"name":"Ztxt Character","first_mes":"Hi from zTXt","description":"From a zTXt chunk"}""",
            inflated?.decodeToString(),
        )
        assertEquals(null, inflateRawDeflate(compressed, 1024))
    }
}

private fun hexBytes(hex: String): ByteArray =
    hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
