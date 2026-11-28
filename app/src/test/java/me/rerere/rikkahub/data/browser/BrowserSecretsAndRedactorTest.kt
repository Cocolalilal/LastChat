package me.rerere.rikkahub.data.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class BrowserSecretsAndRedactorTest {

    @Test
    fun placeholderSubstitutionAndOverwriteProtection() {
        val tmpDir = Files.createTempDirectory("browser_secrets_test").toFile()
        try {
            val store = BrowserSecretsStore(
                metadataDir = tmpDir,
                valueStorage = InMemorySecretValueStorage(),
            )
            val firstSave = store.saveSecret(
                key = "GITHUB_PASS",
                value = "hunter2_secret",
                domain = "github.com",
                kind = BrowserSecretKind.SITE_LOGIN,
            )
            assertTrue(firstSave.isSuccess)
            assertEquals(1, store.revision.value)

            // Duplicate without overwrite fails
            val dupFail = store.saveSecret(
                key = "GITHUB_PASS",
                value = "new_secret",
                overwrite = false,
            )
            assertTrue(dupFail.isFailure)
            assertEquals("hunter2_secret", store.getValue("GITHUB_PASS"))

            // With overwrite=true succeeds and increments revision
            val dupOk = store.saveSecret(
                key = "GITHUB_PASS",
                value = "new_secret_123",
                overwrite = true,
            )
            assertTrue(dupOk.isSuccess)
            assertEquals(2, store.revision.value)

            val resolved = store.resolvePlaceholders("pw={{SECRET:GITHUB_PASS}}")
            assertEquals("pw=new_secret_123", resolved)

            assertFalse(store.formatPromptManifest().contains("new_secret_123"))
            assertTrue(store.formatPromptManifest().contains("GITHUB_PASS"))
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun redactorLongestFirstAndSkipsShortValues() {
        val values = listOf("true", "pass", "sk-short", "sk-short-longer-token-99")
        val input = "flag=true pass=pass a=sk-short-longer-token-99 b=sk-short"
        val (masked, hits) = BrowserSecretRedactor.redact(input, values)
        assertEquals(2, hits)
        assertTrue(masked.contains("flag=true"))
        assertTrue(masked.contains("pass=pass"))
        assertFalse(masked.contains("sk-short-longer-token-99"))
        assertFalse(masked.contains("=sk-short"))
        assertTrue(masked.contains(BrowserSecretRedactor.mask("sk-short-longer-token-99")))
    }
}
