package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxEnvironmentTest {
    @Test
    fun `detects proot execve and ptrace startup failures`() {
        val stderr = """
            proot warning: ptrace(PEEKDATA): I/O error
            proot error: execve("/bin/sh"): Function not implemented
            proot error: can't chdir to '/workspa': Function not implemented
        """.trimIndent()

        assertTrue(stderr.isProotStartupFailure())
    }

    @Test
    fun `does not classify ordinary command stderr as proot startup failure`() {
        val stderr = "python: command failed because feature is not implemented"

        assertFalse(stderr.isProotStartupFailure())
    }
}
