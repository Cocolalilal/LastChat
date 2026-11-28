package me.rerere.rikkahub.data.browser

import me.rerere.rikkahub.data.datastore.BrowserSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserGuardTest {

    private fun passwordTarget() = BrowserGuard.Target(
        found = true,
        tag = "INPUT",
        type = "password",
        name = "password",
        id = "login_password",
        placeholder = "Password",
        autocomplete = "current-password",
        inputMode = "text",
        aria = "",
        label = "Password",
        text = "",
        href = "",
        maxLength = 64,
        url = "https://example.com/login",
        host = "example.com",
    )

    private fun payButtonTarget() = BrowserGuard.Target(
        found = true,
        tag = "BUTTON",
        type = "submit",
        name = "",
        id = "pay_btn",
        placeholder = "",
        autocomplete = "",
        inputMode = "",
        aria = "Pay now",
        label = "",
        text = "Pay now",
        href = "",
        maxLength = 0,
        url = "https://shop.example.com/checkout",
        host = "shop.example.com",
    )

    @Test
    fun judgeTypeRefusesSecretFieldByDefaultAndAllowsInAutonomyModes() {
        val defaultSettings = BrowserSettings()
        val refused = BrowserGuard.judgeType(passwordTarget(), defaultSettings)
        assertTrue(refused is BrowserGuard.Verdict.Refuse)
        assertTrue((refused as BrowserGuard.Verdict.Refuse).text.startsWith(BrowserGuard.HANDOFF_PREFIX))

        val autoHandoffOff = defaultSettings.copy(autoHandoffOnSecretFields = false)
        assertTrue(BrowserGuard.judgeType(passwordTarget(), autoHandoffOff) is BrowserGuard.Verdict.Proceed)

        val fullAutonomy = defaultSettings.copy(fullAutonomyMode = true)
        assertTrue(BrowserGuard.judgeType(passwordTarget(), fullAutonomy) is BrowserGuard.Verdict.Proceed)
    }

    @Test
    fun judgeClickAsksForRiskyTapByDefaultAndAllowsInAutonomyModes() {
        val defaultSettings = BrowserSettings()
        val ask = BrowserGuard.judgeClick(payButtonTarget(), defaultSettings)
        assertTrue(ask is BrowserGuard.Verdict.Ask)
        assertTrue((ask as BrowserGuard.Verdict.Ask).riskClass == BrowserRiskClass.MONEY)

        val approvalOff = defaultSettings.copy(requireApprovalForRiskyClicks = false)
        assertTrue(BrowserGuard.judgeClick(payButtonTarget(), approvalOff) is BrowserGuard.Verdict.Proceed)

        val fullAutonomy = defaultSettings.copy(fullAutonomyMode = true)
        assertTrue(BrowserGuard.judgeClick(payButtonTarget(), fullAutonomy) is BrowserGuard.Verdict.Proceed)
    }

    @Test
    fun domainBlocklistMatchesExactAndSubdomains() {
        val blocked = listOf("ads.example.com", "*.tracker.net")
        assertTrue(BrowserGuard.isDomainBlocked("https://ads.example.com/pixel", blocked))
        assertTrue(BrowserGuard.isDomainBlocked("https://sub.ads.example.com/", blocked))
        assertTrue(BrowserGuard.isDomainBlocked("https://x.tracker.net/event", blocked))
        assertFalse(BrowserGuard.isDomainBlocked("https://example.com/", blocked))
    }
}
