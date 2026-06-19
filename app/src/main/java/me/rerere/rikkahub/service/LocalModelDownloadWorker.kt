package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.rerere.ai.provider.LITERT_PROVIDER_ID
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.locallm.LocalRuntime
import me.rerere.locallm.LocalRuntimePreferences
import me.rerere.locallm.ModelInstall
import me.rerere.locallm.litert.LiteRtCatalog
import me.rerere.locallm.litert.LiteRtModelMetadata
import me.rerere.rikkahub.LOCAL_MODEL_DOWNLOAD_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.SettingsStore
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.absoluteValue

const val LOCAL_MODEL_DOWNLOAD_WORK_TAG = "local_model_download"

class LocalModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {
    private val prefs: LocalRuntimePreferences by inject()
    private val httpClient: OkHttpClient by inject()
    private val settingsStore: SettingsStore by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val runtime = inputData.getString(KEY_RUNTIME)
            ?.let { value ->
                when (value) {
                    LocalRuntime.LiteRT.displayName -> LocalRuntime.LiteRT
                    else -> null
                }
            }
            ?: return@withContext failure("Unsupported local runtime.")
        val rawUrl = inputData.getString(KEY_URL).orEmpty()
        val url = ModelInstall.normalizeHuggingFaceUrl(rawUrl)
        val fileName = inputData.getString(KEY_FILE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: ModelInstall.extractFileNameFromUrl(url)
        if (!ModelInstall.isValidDownloadUrl(url) || fileName.isBlank()) {
            return@withContext failure(applicationContext.getString(R.string.local_llm_invalid_url))
        }
        if (ModelInstall.runtimeForExtension(fileName.substringAfterLast('.', "")) != runtime) {
            return@withContext failure("This file is not a supported LiteRT model package.")
        }

        var lastPercent = -1
        var lastPublishAt = 0L
        suspend fun publish(progress: SettingProgress) {
            val now = System.currentTimeMillis()
            if (progress.percent != lastPercent || now - lastPublishAt >= 500L) {
                setProgress(
                    workDataOf(
                        KEY_FILE_NAME to fileName,
                        KEY_PERCENT to progress.percent,
                        KEY_BYTES_READ to progress.bytesRead,
                        KEY_TOTAL_BYTES to (progress.totalBytes ?: -1L),
                    )
                )
                setForeground(createForegroundInfo(fileName, progress))
                lastPercent = progress.percent
                lastPublishAt = now
            }
        }

        setForeground(createForegroundInfo(fileName, SettingProgress(0, 0L, null)))

        val baseDir = ModelInstall.localModelsDir(applicationContext)
        val target = ModelInstall.targetFile(baseDir, runtime, fileName)
        var finishedFile: java.io.File? = null
        var failureCause: Throwable? = null

        try {
            ModelInstall.download(httpClient, url, target).collect { event ->
                when (event) {
                    is ModelInstall.Progress.Started -> {
                        publish(SettingProgress(0, 0L, event.totalBytes))
                    }

                    is ModelInstall.Progress.Tick -> {
                        val total = event.totalBytes
                        val percent = if (total != null && total > 0L) {
                            ((event.bytesRead * 100L) / total).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        publish(SettingProgress(percent, event.bytesRead, total))
                    }

                    is ModelInstall.Progress.Done -> {
                        finishedFile = event.file
                        publish(SettingProgress(100, event.file.length(), event.file.length()))
                    }

                    is ModelInstall.Progress.Failed -> {
                        failureCause = event.cause
                    }
                }
            }
        } catch (throwable: Throwable) {
            failureCause = throwable
        }

        failureCause?.let { error ->
            Log.w(TAG, "Local model download failed: $fileName", error)
            return@withContext failure(error.message ?: error::class.simpleName.orEmpty())
        }

        val file = finishedFile
            ?: return@withContext failure("Download finished without a model file.")

        prefs.addInstalledModel(runtime, fileName, file.absolutePath)
        registerInstalledModel(runtime, fileName)

        Result.success(
            workDataOf(
                KEY_FILE_NAME to fileName,
                KEY_PERCENT to 100,
                KEY_BYTES_READ to file.length(),
                KEY_TOTAL_BYTES to file.length(),
            )
        )
    }

    private suspend fun registerInstalledModel(runtime: LocalRuntime, fileName: String) {
        val model = modelForInstalledFile(fileName)
        settingsStore.settingsFlow.first { !it.init }
        settingsStore.update { old ->
            old.copy(
                providers = old.providers.map { provider ->
                    if (provider.id == providerIdForRuntime(runtime)) {
                        provider.addOrReplaceLocalModel(model).copyProvider(enabled = true)
                    } else {
                        provider
                    }
                }
            )
        }
    }

    private fun createForegroundInfo(fileName: String, progress: SettingProgress): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, LOCAL_MODEL_DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.local_llm_download_notification_title))
            .setContentText(
                if (progress.totalBytes != null && progress.totalBytes > 0L) {
                    applicationContext.getString(
                        R.string.local_llm_download_notification_progress,
                        fileName,
                        progress.percent,
                    )
                } else {
                    applicationContext.getString(R.string.local_llm_download_notification_progress_unknown, fileName)
                }
            )
            .setContentIntent(openAppIntent())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply {
                if (progress.totalBytes != null && progress.totalBytes > 0L) {
                    setProgress(100, progress.percent.coerceIn(0, 100), false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()
        val notificationId = BASE_NOTIFICATION_ID + (fileName.hashCode().absoluteValue % 10_000)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun openAppIntent(): PendingIntent? {
        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?: return null
        return PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun failure(message: String): Result =
        Result.failure(workDataOf(KEY_ERROR to message))

    private data class SettingProgress(
        val percent: Int,
        val bytesRead: Long,
        val totalBytes: Long?,
    )

    companion object {
        private const val TAG = "LocalModelDownload"
        private const val BASE_NOTIFICATION_ID = 40_120
        const val KEY_RUNTIME = "runtime"
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_PERCENT = "percent"
        const val KEY_BYTES_READ = "bytes_read"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_ERROR = "error"

        fun enqueue(
            context: Context,
            runtime: LocalRuntime,
            url: String,
            fileName: String = ModelInstall.extractFileNameFromUrl(url),
        ) {
            val normalizedUrl = ModelInstall.normalizeHuggingFaceUrl(url)
            val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
                .addTag(LOCAL_MODEL_DOWNLOAD_WORK_TAG)
                .setInputData(
                    workDataOf(
                        KEY_RUNTIME to runtime.displayName,
                        KEY_URL to normalizedUrl,
                        KEY_FILE_NAME to fileName,
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$LOCAL_MODEL_DOWNLOAD_WORK_TAG:$fileName",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun modelForInstalledFile(fileName: String): Model {
            return LiteRtCatalog.findByModelFile(fileName)
                ?.let(LiteRtModelMetadata::modelForCatalogEntry)
                ?: LiteRtModelMetadata.modelForFile(fileName)
        }

        fun providerIdForRuntime(runtime: LocalRuntime): kotlin.uuid.Uuid = when (runtime) {
            LocalRuntime.LiteRT -> LITERT_PROVIDER_ID
        }
    }
}

private fun ProviderSetting.addOrReplaceLocalModel(model: Model): ProviderSetting {
    val existing = models.firstOrNull { it.modelId == model.modelId }
    return if (existing == null) {
        addModel(model)
    } else {
        editModel(
            model.copy(
                id = existing.id,
                displayName = existing.displayName.takeIf { it.isNotBlank() } ?: model.displayName,
                customIconUri = existing.customIconUri,
            )
        )
    }
}
