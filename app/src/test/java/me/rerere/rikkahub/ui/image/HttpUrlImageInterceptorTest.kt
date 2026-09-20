package me.rerere.rikkahub.ui.image

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpUrlImageInterceptorTest {
    @Test
    fun validatesEmptyAndSchemeLessHttpLikeUrls() {
        assertTrue(needsHttpUrlValidation(""))
        assertTrue(needsHttpUrlValidation("   "))
        assertTrue(needsHttpUrlValidation("api.openai.com/v1/icon.png"))
        assertTrue(needsHttpUrlValidation("https://cdn.example.com/a.png"))
        assertFalse(needsHttpUrlValidation("file:///tmp/a.png"))
        assertFalse(needsHttpUrlValidation("content://media/123"))
        assertFalse(needsHttpUrlValidation("android.resource://lastchat.rikkafork.cocolal/1"))
        assertFalse(needsHttpUrlValidation("icons/local.png"))
    }
}
