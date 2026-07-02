package me.rerere.rikkahub.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R

private const val GITHUB_API_URL = "https://api.github.com/repos/Cocolalilal/LastChat/releases/latest"

class UpdateChecker(private val client: PlatformHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _isForcedCheck = MutableStateFlow(false)
    val isForcedCheck = _isForcedCheck.asStateFlow()

    // Counter incremented every time a fresh check should be triggered
    // Start at 1 so the first subscriber immediately gets a check
    private val _checkTrigger = MutableStateFlow(1)
    val checkTrigger = _checkTrigger.asStateFlow()

    fun forceUpdateCheck() {
        _isForcedCheck.value = true
        _checkTrigger.value++ // re-trigger the update flow
    }

    fun clearForcedCheck() {
        _isForcedCheck.value = false
    }

    fun checkUpdate(): Flow<UiState<UpdateInfo>> = flow {
        emit(UiState.Loading)

        emit(
            UiState.Success(
                data = try {
                    val response = client.execute(
                        PlatformHttpRequest(
                            method = "GET",
                            url = GITHUB_API_URL,
                            headers = mapOf(
                                "Accept" to "application/vnd.github+json",
                                "User-Agent" to "LastChat ${BuildConfig.VERSION_NAME} #${BuildConfig.VERSION_CODE}"
                            )
                        )
                    )
                    if (response.statusCode in 200..299) {
                        val release = json.decodeFromString<GitHubRelease>(response.body.decodeToString())
                        
                        // Convert GitHub release to UpdateInfo
                        val arch = getDeviceArchitecture()
                        val downloads = release.assets
                            .filter { it.name.endsWith(".apk") }
                            .map { asset ->
                                UpdateDownload(
                                    name = asset.name,
                                    url = asset.browser_download_url,
                                    size = formatFileSize(asset.size)
                                )
                            }
                        
                        // Sort downloads to prioritize architecture match
                        val sortedDownloads = downloads.sortedByDescending { download ->
                            when {
                                download.name.contains(arch, ignoreCase = true) -> 2
                                download.name.contains("universal", ignoreCase = true) -> 1
                                else -> 0
                            }
                        }
                        
                        UpdateInfo(
                            version = release.tag_name.removePrefix("v"),
                            publishedAt = release.published_at,
                            changelog = release.body,
                            downloads = sortedDownloads
                        )
                    } else {
                        throw Exception("Failed to fetch update info: ${response.statusCode}")
                    }
                } catch (e: Exception) {
                    throw e
                }
            )
        )
    }.catch {
        emit(UiState.Error(it))
    }.flowOn(Dispatchers.IO)
    
    private fun getDeviceArchitecture(): String {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.any { it.contains("arm64") } -> "arm64-v8a"
            abis.any { it.contains("armeabi") } -> "armeabi-v7a"
            abis.any { it.contains("x86_64") } -> "x86_64"
            abis.any { it.contains("x86") } -> "x86"
            else -> "universal"
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
            bytes >= 1_024 -> String.format("%.1f KB", bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    fun downloadUpdate(context: Context, download: UpdateDownload): Long {
        return runCatching {
            val request = DownloadManager.Request(download.url.toUri()).apply {
                setTitle("LastChat Update")
                setDescription("Downloading ${download.name}...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, download.name)
                setMimeType("application/vnd.android.package-archive")
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.update_download_failed), Toast.LENGTH_SHORT).show()
            context.openUrl(download.url)
        }.getOrDefault(-1L)
    }

    fun observeDownload(context: Context, downloadId: Long): Flow<DownloadProgress> = flow {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        
        while (true) {
            var progress = DownloadProgress()
            var finished = false
            dm.query(query).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    
                    if (bytesDownloadedIndex != -1 && bytesTotalIndex != -1 && statusIndex != -1) {
                        val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                        val bytesTotal = cursor.getLong(bytesTotalIndex)
                        val status = cursor.getInt(statusIndex)
                        
                        progress = progress.copy(
                            bytesDownloaded = bytesDownloaded,
                            totalBytes = bytesTotal,
                            status = status,
                            localUri = if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                dm.getUriForDownloadedFile(downloadId)?.toString()
                            } else if (uriIndex != -1) {
                                cursor.getString(uriIndex)
                            } else {
                                null
                            }
                        )
                        
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            finished = true
                        }
                    }
                } else {
                    finished = true
                }
            }
            emit(progress)
            if (finished) break
            kotlinx.coroutines.delay(250)
        }
    }.flowOn(Dispatchers.IO)
    
    fun ignoreUpdate(context: Context, version: String) {
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ignored_version", version)
            .putLong("ignored_time", System.currentTimeMillis())
            .apply()
    }

    fun canInstallDownloadedUpdate(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    fun installDownloadedUpdate(context: Context, uriString: String): Boolean {
        if (!canInstallDownloadedUpdate(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return false
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriString.toUri(), "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
        return true
    }
    
    fun isUpdateIgnored(context: Context, version: String, forceCheck: Boolean): Boolean {
        if (forceCheck) return false
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val ignoredVersion = prefs.getString("ignored_version", null)
        val ignoredTime = prefs.getLong("ignored_time", 0L)
        val oneWeekMillis = 7 * 24 * 60 * 60 * 1000L
        
        return ignoredVersion == version && (System.currentTimeMillis() - ignoredTime) < oneWeekMillis
    }
}

data class DownloadProgress(
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val status: Int = -1,
    val localUri: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
}

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String,
    val published_at: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long
)

@Serializable
data class UpdateDownload(
    val name: String,
    val url: String,
    val size: String
)

@Serializable
data class UpdateInfo(
    val version: String,
    val publishedAt: String,
    val changelog: String,
    val downloads: List<UpdateDownload>
)

/**
 * 版本号值类，封装版本号字符串并提供比较功能
 */
@JvmInline
value class Version(val value: String) : Comparable<Version> {

    /**
     * 将版本号分解为数字数组
     */
    private fun parseVersion(): List<Int> {
        return value.split(".")
            .map { it.toIntOrNull() ?: 0 }
    }

    /**
     * 实现 Comparable 接口的比较方法
     */
    override fun compareTo(other: Version): Int {
        val thisParts = this.parseVersion()
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
        /**
         * 比较两个版本号字符串
         */
        fun compare(version1: String, version2: String): Int {
            return Version(version1).compareTo(Version(version2))
        }
    }
}

// 扩展操作符函数，使比较更直观
operator fun String.compareTo(other: Version): Int = Version(this).compareTo(other)
operator fun Version.compareTo(other: String): Int = this.compareTo(Version(other))
