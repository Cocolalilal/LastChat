package me.rerere.rikkahub.data.codex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodexAccountRepositoryTest {
    @Test
    fun `legacy account pool keeps only the most recently enabled account`() {
        val older = account(id = "older", enabled = true)
        val selected = account(id = "selected", enabled = true)

        val normalized = normalizeCodexAccountState(
            stored = CodexAccountState(accounts = listOf(older, selected)),
            nowMillis = 1_000L,
        )

        assertEquals(listOf("selected"), normalized.accounts.map(CodexAccount::id))
        assertTrue(normalized.accounts.single().enabled)
    }

    @Test
    fun `legacy disabled accounts fall back to the most recent sign in`() {
        val normalized = normalizeCodexAccountState(
            stored = CodexAccountState(
                accounts = listOf(
                    account(id = "older", enabled = false),
                    account(id = "latest", enabled = false),
                )
            ),
            nowMillis = 1_000L,
        )

        assertEquals("latest", normalized.accounts.single().id)
        assertTrue(normalized.accounts.single().enabled)
    }

    @Test
    fun `expired legacy account is marked expired during normalization`() {
        val normalized = normalizeCodexAccountState(
            stored = CodexAccountState(
                accounts = listOf(account(id = "expired", expiresAt = 999L))
            ),
            nowMillis = 1_000L,
        )

        assertEquals(CodexTokenStatus.EXPIRED, normalized.accounts.single().tokenStatus)
    }

    private fun account(
        id: String,
        enabled: Boolean = true,
        expiresAt: Long = 10_000L,
    ) = CodexAccount(
        id = id,
        userId = id,
        name = id,
        email = "$id@example.com",
        chatgptAccountId = "chatgpt-$id",
        accessToken = "access-$id",
        refreshToken = "refresh-$id",
        expiresAt = expiresAt,
        enabled = enabled,
        tokenStatus = CodexTokenStatus.AVAILABLE,
    )
}
