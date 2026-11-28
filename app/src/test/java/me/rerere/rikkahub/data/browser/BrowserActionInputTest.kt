package me.rerere.rikkahub.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserActionInputTest {

    @Test
    fun parsesAll26Actions() {
        assertEquals(26, BrowserAction.entries.size)
        for (action in BrowserAction.entries) {
            val json = """{"action":"${action.value}","tool_title":"Test step"}"""
            val parsed = BrowserActionInput.parse(json)
            assertNotNull("Failed to parse action ${action.value}", parsed)
            assertEquals(action, parsed!!.action)
            assertEquals("Test step", parsed.toolTitle)
        }
    }

    @Test
    fun unknownActionReturnsNull() {
        assertNull(BrowserActionInput.parse("""{"action":"does_not_exist"}"""))
    }

    @Test
    fun parsesCookiesAsJsonArray() {
        val json = """
            {
              "action": "set_cookies",
              "cookies": [
                {"name": "sid", "value": "abc123", "domain": ".example.com", "httpOnly": true, "secure": true, "expirationDate": 1800000000}
              ]
            }
        """.trimIndent()
        val parsed = BrowserActionInput.parse(json)!!
        assertEquals(BrowserAction.SET_COOKIES, parsed.action)
        assertEquals(1, parsed.cookies?.size)
        assertEquals("sid", parsed.cookies!![0]["name"])
        assertEquals("abc123", parsed.cookies!![0]["value"])
        assertEquals(true, parsed.cookies!![0]["httpOnly"])
    }

    @Test
    fun parsesCookiesAsJsonEncodedString() {
        val json = """
            {
              "action": "set_cookies",
              "cookies": "[{\"name\":\"token\",\"value\":\"xyz\",\"http_only\":true}]"
            }
        """.trimIndent()
        val parsed = BrowserActionInput.parse(json)!!
        assertEquals(BrowserAction.SET_COOKIES, parsed.action)
        assertEquals(1, parsed.cookies?.size)
        assertEquals("token", parsed.cookies!![0]["name"])
        assertEquals("xyz", parsed.cookies!![0]["value"])
    }

    @Test
    fun parsesKeywordsAsArrayAndString() {
        val fromArray = BrowserActionInput.parse("""{"action":"get_cookies","keywords":["session","auth"],"fuzzy":true}""")!!
        assertEquals(listOf("session", "auth"), fromArray.keywords)
        assertTrue(fromArray.fuzzy)

        val fromString = BrowserActionInput.parse("""{"action":"save_cookies_as_secret","key":"GH_SESSION","keywords":"user_session logged_in","overwrite":true}""")!!
        assertEquals(listOf("user_session", "logged_in"), fromString.keywords)
        assertEquals("GH_SESSION", fromString.key)
        assertTrue(fromString.overwrite)
    }

    @Test
    fun parsesSecretAndHandOverFields() {
        val handOver = BrowserActionInput.parse("""{"action":"hand_over","reason":"Complete 2FA","url":"https://example.com/2fa"}""")!!
        assertEquals(BrowserAction.HAND_OVER, handOver.action)
        assertEquals("Complete 2FA", handOver.reason)
        assertEquals("https://example.com/2fa", handOver.url)

        val saveSecret = BrowserActionInput.parse("""{"action":"save_secret","key":"MY_KEY","value":"topsecret","domain":"example.com","note":"main login","overwrite":true}""")!!
        assertEquals(BrowserAction.SAVE_SECRET, saveSecret.action)
        assertEquals("MY_KEY", saveSecret.key)
        assertEquals("topsecret", saveSecret.value)
        assertEquals("example.com", saveSecret.domain)
        assertEquals("main login", saveSecret.note)
        assertTrue(saveSecret.overwrite)

        val restore = BrowserActionInput.parse("""{"action":"restore_cookies_from_secret","key":"MY_COOKIES","url":"https://example.com"}""")!!
        assertEquals(BrowserAction.RESTORE_COOKIES_FROM_SECRET, restore.action)
        assertEquals("MY_COOKIES", restore.key)
    }
}
