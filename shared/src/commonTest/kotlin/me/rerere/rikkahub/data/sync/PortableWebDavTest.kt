package me.rerere.rikkahub.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortableWebDavTest {
    @Test
    fun collectionUrlJoinsBaseAndPath() {
        val url = PortableWebDavClient.collectionUrl(
            PortableWebDavConfig(url = "https://dav.example.com/remote.php/dav", path = "lastchat_backups"),
        )
        assertEquals("https://dav.example.com/remote.php/dav/lastchat_backups/", url)
    }

    @Test
    fun propfindSkipsTheCollectionItselfAndParsesFileHrefs() {
        val xml = """
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/lastchat_backups/</d:href>
                <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/lastchat_backups/LastChat_ios.zip</d:href>
                <d:propstat>
                  <d:prop>
                    <d:displayname>LastChat_ios.zip</d:displayname>
                    <d:getcontentlength>42</d:getcontentlength>
                    <d:resourcetype/>
                  </d:prop>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()
        val items = PortableWebDavClient.parsePropfind(
            xml,
            "https://dav.example.com/remote.php/dav/lastchat_backups/",
        )
        assertEquals(1, items.size)
        assertEquals("LastChat_ios.zip", items[0].displayName)
        assertEquals(42L, items[0].contentLength)
        assertTrue(items[0].href.endsWith("/lastchat_backups/LastChat_ios.zip"))
    }
}
