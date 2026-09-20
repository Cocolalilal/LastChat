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
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.rikkahub.BuildConfig
import me.rerere.rikkahub.R

class UpdateChecker(private val client: PlatformHttpClient) {
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
                data = fetchGithubLatestRelease(
                    client = client,
                    userAgent = "LastChat ${BuildConfig.VERSION_NAME} #${BuildConfig.VERSION_CODE}",
                    assetNameFilter = { it.endsWith(".apk", ignoreCase = true) },
                    preferredArchitecture = getDeviceArchitecture(),
                )
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
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val ignoredVersion = prefs.getString("ignored_version", null)
        val ignoredTime = prefs.getLong("ignored_time", 0L)
        return me.rerere.rikkahub.utils.isUpdateIgnored(
            ignoredVersion = ignoredVersion,
            ignoredTimeEpochMs = ignoredTime,
            version = version,
            nowEpochMs = System.currentTimeMillis(),
            forceCheck = forceCheck,
        )
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
