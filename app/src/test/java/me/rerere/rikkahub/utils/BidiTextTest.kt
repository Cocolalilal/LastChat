package me.rerere.rikkahub.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class BidiTextTest {
    @Test
    fun `arabic text resolves to rtl`() {
        assertEquals(
            BidiDirection.Rtl,
            resolveBidiDirection("مرحبا بالعالم", fallbackLocale = Locale.ENGLISH)
        )
    }

    @Test
    fun `english text resolves to ltr`() {
        assertEquals(
            BidiDirection.Ltr,
            resolveBidiDirection("Hello world", fallbackLocale = Locale("ar"))
        )
    }

    @Test
    fun `mixed text uses the first strong character`() {
        assertEquals(
            BidiDirection.Rtl,
            resolveBidiDirection("123 - مرحبا Hello", fallbackLocale = Locale.ENGLISH)
        )
    }

    @Test
    fun `neutral text falls back to rtl locale`() {
        assertEquals(
            BidiDirection.Rtl,
            resolveBidiDirection("12345 / ...", fallbackLocale = Locale("ar"))
        )
    }

    @Test
    fun `heading markers do not block rtl detection`() {
        assertEquals(
            BidiDirection.Rtl,
            resolveBidiDirection("### مرحبا", fallbackLocale = Locale.ENGLISH)
        )
    }

    @Test
    fun `list markers do not block ltr detection`() {
        assertEquals(
            BidiDirection.Ltr,
            resolveBidiDirection("1. Hello", fallbackLocale = Locale("ar"))
        )
    }

    @Test
    fun `unordered list markers do not block rtl detection`() {
        assertEquals(
            BidiDirection.Rtl,
            resolveBidiDirection("- مرحبا بالعالم", fallbackLocale = Locale.ENGLISH)
        )
    }

    @Test
    fun `blockquote markers do not block rtl detection`() {
        assertEquals(
            BidiDirection.Rtl,
            resolveBidiDirection("> مرحبا بالعالم", fallbackLocale = Locale.ENGLISH)
        )
    }

    @Test
    fun `table pipes do not block rtl detection`() {
        assertEquals(
            BidiDirection.Rtl,
            resolveBidiDirection("| الصنف | الكمية |", fallbackLocale = Locale.ENGLISH)
        )
    }
}
