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
}
