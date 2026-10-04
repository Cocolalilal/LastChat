package me.rerere.rikkahub.ui.components.richtext

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownBubbleSplitTest {
    @Test
    fun proseStaysOneBubble() {
        assertEquals(1, markdownBubbleRunCount("Hello there."))
    }

    @Test
    fun codeAndImagesBreakOutOfTheText() {
        val markdown = """
            Here is the snippet.

            ```kotlin
            println("hi")
            ```

            And a picture.

            ![shot](https://example.com/a.png)
        """.trimIndent()
        assertEquals(4, markdownBubbleRunCount(markdown))
    }

    @Test
    fun blankContentHasNoBubble() {
        assertEquals(0, markdownBubbleRunCount("   "))
    }
}
