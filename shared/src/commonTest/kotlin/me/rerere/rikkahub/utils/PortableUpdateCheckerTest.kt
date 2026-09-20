package me.rerere.rikkahub.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortableUpdateCheckerTest {
    @Test
    fun versionCompareSplitsOnDots() {
        assertTrue(Version("1.4.7") > Version("1.4.6"))
        assertTrue(Version("1.5") > Version("1.4.9"))
        assertEquals(0, Version.compare("1.4.0", "1.4"))
        assertTrue(Version("2.0.0") > Version("1.99.99"))
    }

    @Test
    fun ignoredVersionIsSuppressedForOneWeekUnlessForced() {
        val now = 1_000_000L
        assertTrue(
            isUpdateIgnored("1.5.0", now - 1_000, "1.5.0", now, forceCheck = false),
        )
        assertFalse(
            isUpdateIgnored("1.5.0", now - UPDATE_IGNORE_WINDOW_MS - 1, "1.5.0", now, forceCheck = false),
        )
        assertFalse(
            isUpdateIgnored("1.5.0", now, "1.5.0", now, forceCheck = true),
        )
    }

    @Test
    fun updatePillFollowsAndroidEmptyChatAndKnobRules() {
        val latest = UpdateInfo("1.5.0", "2026-01-01", "notes", emptyList())
        assertTrue(
            shouldShowUpdatePill(
                checkForUpdates = true,
                forceCheck = false,
                isNewChat = true,
                currentVersion = "1.4.7",
                latest = latest,
                ignoredVersion = null,
                ignoredTimeEpochMs = 0,
                nowEpochMs = 1,
                dismissedVersion = null,
            ),
        )
        assertFalse(
            shouldShowUpdatePill(
                checkForUpdates = false,
                forceCheck = false,
                isNewChat = true,
                currentVersion = "1.4.7",
                latest = latest,
                ignoredVersion = null,
                ignoredTimeEpochMs = 0,
                nowEpochMs = 1,
                dismissedVersion = null,
            ),
        )
        assertFalse(
            shouldShowUpdatePill(
                checkForUpdates = true,
                forceCheck = false,
                isNewChat = false,
                currentVersion = "1.4.7",
                latest = latest,
                ignoredVersion = null,
                ignoredTimeEpochMs = 0,
                nowEpochMs = 1,
                dismissedVersion = null,
            ),
        )
    }

    @Test
    fun fetchParsesGithubReleaseAndFiltersAssets() = runBlocking {
        val payload = """
            {
              "tag_name": "v1.5.0",
              "name": "1.5.0",
              "body": "Fixes",
              "published_at": "2026-09-20T00:00:00Z",
              "assets": [
                {"name": "LastChat_1.5.0_arm64-v8a.apk", "browser_download_url": "https://example.test/arm.apk", "size": 1048576},
                {"name": "LastChat_1.5.0.ipa", "browser_download_url": "https://example.test/app.ipa", "size": 2048},
                {"name": "notes.txt", "browser_download_url": "https://example.test/notes.txt", "size": 12}
              ]
            }
        """.trimIndent()
        val client = object : PlatformHttpClient {
            override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse {
                assertEquals(GITHUB_LATEST_RELEASE_URL, request.url)
                assertEquals("application/vnd.github+json", request.headers["Accept"])
                assertEquals("LastChat 1.4.7 iOS", request.headers["User-Agent"])
                return PlatformHttpResponse(statusCode = 200, body = payload.encodeToByteArray())
            }

            override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
        }
        val info = fetchGithubLatestRelease(
            client = client,
            userAgent = "LastChat 1.4.7 iOS",
            assetNameFilter = { it.endsWith(".ipa", ignoreCase = true) || it.endsWith(".apk", ignoreCase = true) },
            preferredArchitecture = "arm64-v8a",
        )
        assertEquals("1.5.0", info.version)
        assertEquals("Fixes", info.changelog)
        assertEquals(2, info.downloads.size)
        assertEquals("LastChat_1.5.0_arm64-v8a.apk", info.downloads.first().name)
        assertEquals("1.0 MB", info.downloads.first().size)
    }
}
