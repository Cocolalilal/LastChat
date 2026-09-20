package me.rerere.rikkahub.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest

const val GITHUB_LATEST_RELEASE_URL =
    "https://api.github.com/repos/Cocolalilal/LastChat/releases/latest"

const val UPDATE_IGNORE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

private val githubJson = Json { ignoreUnknownKeys = true }

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String = "",
    val body: String = "",
    val published_at: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long = 0,
)

@Serializable
data class UpdateDownload(
    val name: String,
    val url: String,
    val size: String,
)

@Serializable
data class UpdateInfo(
    val version: String,
    val publishedAt: String,
    val changelog: String,
    val downloads: List<UpdateDownload>,
)

@kotlin.jvm.JvmInline
value class Version(val value: String) : Comparable<Version> {
    private fun parseVersion(): List<Int> {
        return value.split(".").map { it.toIntOrNull() ?: 0 }
    }

    override fun compareTo(other: Version): Int {
        val thisParts = parseVersion()
        val otherParts = other.parseVersion()
        val maxLength = maxOf(thisParts.size, otherParts.size)
        for (i in 0 until maxLength) {
            val thisPart = if (i < thisParts.size) thisParts[i] else 0
            val otherPart = if (i < otherParts.size) otherParts[i] else 0
            when {
                thisPart > otherPart -> return 1
                thisPart < otherPart -> return -1
            }
        }
        return 0
    }

    companion object {
        fun compare(version1: String, version2: String): Int {
            return Version(version1).compareTo(Version(version2))
        }
    }
}

operator fun String.compareTo(other: Version): Int = Version(this).compareTo(other)

operator fun Version.compareTo(other: String): Int = this.compareTo(Version(other))

fun formatUpdateFileSize(bytes: Long): String {
    return when {
        bytes >= 1_048_576 -> "${formatOneDecimal(bytes / 1_048_576.0)} MB"
        bytes >= 1_024 -> "${formatOneDecimal(bytes / 1_024.0)} KB"
        else -> "$bytes B"
    }
}

fun isUpdateIgnored(
    ignoredVersion: String?,
    ignoredTimeEpochMs: Long,
    version: String,
    nowEpochMs: Long,
    forceCheck: Boolean,
): Boolean {
    if (forceCheck) return false
    if (ignoredVersion.isNullOrBlank() || ignoredVersion != version) return false
    return (nowEpochMs - ignoredTimeEpochMs) < UPDATE_IGNORE_WINDOW_MS
}

fun shouldShowUpdatePill(
    checkForUpdates: Boolean,
    forceCheck: Boolean,
    isNewChat: Boolean,
    currentVersion: String,
    latest: UpdateInfo?,
    ignoredVersion: String?,
    ignoredTimeEpochMs: Long,
    nowEpochMs: Long,
    dismissedVersion: String?,
): Boolean {
    if (latest == null) return false
    if (!(checkForUpdates || forceCheck) || !isNewChat) return false
    val newer = Version(latest.version) > Version(currentVersion)
    if (!(newer || forceCheck)) return false
    if (isUpdateIgnored(ignoredVersion, ignoredTimeEpochMs, latest.version, nowEpochMs, forceCheck)) {
        return false
    }
    return dismissedVersion != latest.version
}

suspend fun fetchGithubLatestRelease(
    client: PlatformHttpClient,
    userAgent: String,
    assetNameFilter: (String) -> Boolean,
    preferredArchitecture: String? = null,
): UpdateInfo {
    val response = client.execute(
        PlatformHttpRequest(
            method = "GET",
            url = GITHUB_LATEST_RELEASE_URL,
            headers = mapOf(
                "Accept" to "application/vnd.github+json",
                "User-Agent" to userAgent,
            ),
        ),
    )
    if (response.statusCode !in 200..299) {
        error("Failed to fetch update info: ${response.statusCode}")
    }
    val release = githubJson.decodeFromString<GitHubRelease>(response.body.decodeToString())
    val downloads = release.assets
        .filter { assetNameFilter(it.name) }
        .map { asset ->
            UpdateDownload(
                name = asset.name,
                url = asset.browser_download_url,
                size = formatUpdateFileSize(asset.size),
            )
        }
    val sorted = if (preferredArchitecture.isNullOrBlank()) {
        downloads
    } else {
        downloads.sortedByDescending { download ->
            when {
                download.name.contains(preferredArchitecture, ignoreCase = true) -> 2
                download.name.contains("universal", ignoreCase = true) -> 1
                else -> 0
            }
        }
    }
    return UpdateInfo(
        version = release.tag_name.removePrefix("v"),
        publishedAt = release.published_at,
        changelog = release.body,
        downloads = sorted,
    )
}

private fun formatOneDecimal(value: Double): String {
    val tenths = ((value * 10.0) + 0.5).toInt()
    return "${tenths / 10}.${tenths % 10}"
}
