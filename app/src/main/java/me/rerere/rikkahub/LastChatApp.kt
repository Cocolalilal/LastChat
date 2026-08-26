package me.rerere.rikkahub

import android.app.Application
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.common.android.appTempFolder
import me.rerere.rikkahub.di.appModule
import me.rerere.rikkahub.di.dataSourceModule
import me.rerere.rikkahub.di.repositoryModule
import me.rerere.rikkahub.di.viewModelModule
import me.rerere.rikkahub.utils.DatabaseUtil
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.syncInstalledLocalModelsToSettings
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.data.ai.models.mergeCatalogIntoSettings
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import me.rerere.rikkahub.service.CHAT_STORAGE_MAINTENANCE_WORK_NAME
import me.rerere.rikkahub.service.ChatStorageMaintenanceWorker
import me.rerere.rikkahub.service.MemoryConsolidationWorker
import me.rerere.rikkahub.service.SPONTANEOUS_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.service.SPONTANEOUS_WORK_INTERVAL_MINUTES
import me.rerere.rikkahub.service.SPONTANEOUS_WORK_NAME
import me.rerere.rikkahub.service.SpontaneousWorker
import me.rerere.rikkahub.service.WebServerService
import me.rerere.rikkahub.data.search.AndroidBingSearchClient
import java.util.concurrent.TimeUnit
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.inference.LocalInferenceManager
import me.rerere.rikkahub.di.SEARCH_PLATFORM_HTTP_CLIENT
import me.rerere.rikkahub.utils.acceptLanguageHeader
import me.rerere.search.SearchService
import org.koin.core.qualifier.named

private const val TAG = "LastChatApp"
private const val MEMORY_MAINTENANCE_WORK_NAME = "memory_consolidation_automatic"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"
const val WEB_SERVER_NOTIFICATION_CHANNEL_ID = "web_server"
const val LOCAL_MODEL_DOWNLOAD_NOTIFICATION_CHANNEL_ID = "local_model_download"

class LastChatApp : Application() {
    companion object {
        lateinit var instance: LastChatApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        startKoin {
            androidLogger()
            androidContext(this@LastChatApp)
            workManagerFactory()
            modules(appModule, viewModelModule, dataSourceModule, repositoryModule)
        }
        val searchHttpClient = get<PlatformHttpClient>(named(SEARCH_PLATFORM_HTTP_CLIENT))
        SearchService.installPlatformHttpClient(searchHttpClient)
        SearchService.installBingSearchClient(AndroidBingSearchClient(searchHttpClient))
        SearchService.installAcceptLanguageProvider { acceptLanguageHeader() }
        this.createNotificationChannel()

        // set cursor window size (4MB avoids native virtual memory exhaustion)
        DatabaseUtil.setCursorWindowSize(4 * 1024 * 1024)

        // delete temp files
        deleteTempFiles()

        // Init remote config
        get<FirebaseRemoteConfig>().apply {
            setConfigSettingsAsync(remoteConfigSettings {
                minimumFetchIntervalInSeconds = 1800
            })
            setDefaultsAsync(R.xml.remote_config_defaults)
            fetchAndActivate()
        }

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SPONTANEOUS_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SpontaneousWorker>(
                SPONTANEOUS_WORK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CHAT_STORAGE_MAINTENANCE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<ChatStorageMaintenanceWorker>(
                1,
                TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )

        // Post-reply jobs do the normal Core + Episodic consolidation. This periodic scan is the
        // durable safety net for process death, provider failures, and restored data.
        WorkManager.getInstance(this).apply {
            cancelUniqueWork("memory_consolidation")
            cancelUniqueWork("memory_maintenance_v3")
            enqueueUniquePeriodicWork(
                MEMORY_MAINTENANCE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(6, TimeUnit.HOURS).build(),
            )
        }
        
        // Update app shortcuts when recently used assistants change
        val appShortcutManager = me.rerere.rikkahub.utils.AppShortcutManager(this)
        get<AppScope>().launch {
            get<SettingsStore>().settingsFlow
                .map { Triple(it.recentlyUsedAssistants, it.assistants, it.init) }
                .distinctUntilChanged()
                .collect { (recentlyUsed, assistants, isInit) ->
                    if (!isInit) {
                        withContext(Dispatchers.IO) {
                            appShortcutManager.updateAssistantShortcuts(recentlyUsed, assistants)
                        }
                    }
                }
        }

        get<AppScope>().launch {
            val settings = get<SettingsStore>().settingsFlowRaw.first()
            if (settings.webServerEnabled) {
                WebServerService.start(this@LastChatApp, settings.webServerPort)
            }
        }
        
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                val catalogService = get<ModelCatalogService>()
                catalogService.warmUp()
                val snapshot = catalogService.snapshotOrNull() ?: return@runCatching
                val settingsStore = get<SettingsStore>()
                val settings = settingsStore.settingsFlow.first { !it.init }
                settingsStore.update(
                    mergeCatalogIntoSettings(
                        settings = settings,
                        snapshot = snapshot,
                        resolver = get<ModelMetadataResolver>(),
                    )
                )
            }.onFailure {
                Log.w(TAG, "Model catalog warm-up failed", it)
            }
            runCatching {
                syncInstalledLocalModelsToSettings(
                    installed = get<me.rerere.locallm.LocalModelStore>().current(),
                    totalRamGb = me.rerere.locallm.MemoryGuard.deviceTotalRamGb(this@LastChatApp),
                    settingsStore = get<SettingsStore>(),
                    catalogSnapshot = get<ModelCatalogService>().snapshotFlow.value,
                )
            }.onFailure {
                Log.w(TAG, "Local model metadata sync failed", it)
            }
        }
    }

    private fun deleteTempFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            val dir = appTempFolder
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = NotificationManagerCompat.from(this)
        val chatCompletedChannel = NotificationChannelCompat
            .Builder(
                CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
            .setName(getString(R.string.notification_channel_chat_completed))
            .setVibrationEnabled(true)
            .build()
        val webServerChannel = NotificationChannelCompat
            .Builder(
                WEB_SERVER_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(getString(R.string.notification_channel_web_server))
            .setVibrationEnabled(false)
            .build()
        val spontaneousChannel = NotificationChannelCompat
            .Builder(
                SPONTANEOUS_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_DEFAULT
            )
            .setName(getString(R.string.notification_channel_spontaneous))
            .setVibrationEnabled(true)
            .build()
        val localModelDownloadChannel = NotificationChannelCompat
            .Builder(
                LOCAL_MODEL_DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(getString(R.string.notification_channel_local_model_downloads))
            .setVibrationEnabled(false)
            .build()
        notificationManager.createNotificationChannel(chatCompletedChannel)
        notificationManager.createNotificationChannel(webServerChannel)
        notificationManager.createNotificationChannel(spontaneousChannel)
        notificationManager.createNotificationChannel(localModelDownloadChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        get<AppScope>().cancel()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            runCatching { get<LocalInferenceManager>().requestEviction() }
            runCatching { coil3.SingletonImageLoader.get(this).memoryCache?.clear() }
            runCatching { get<me.rerere.rikkahub.service.ChatService>().checkAllConversationsReferences() }
            runCatching { get<me.rerere.rikkahub.data.ai.AILoggingManager>().clearLogs() }
            runCatching { me.rerere.rikkahub.service.assist.AssistScreenHolder.clear() }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        runCatching { get<LocalInferenceManager>().requestEviction() }
        runCatching { coil3.SingletonImageLoader.get(this).memoryCache?.clear() }
        runCatching { get<me.rerere.rikkahub.service.ChatService>().checkAllConversationsReferences() }
        runCatching { get<me.rerere.rikkahub.data.ai.AILoggingManager>().clearLogs() }
        runCatching { me.rerere.rikkahub.service.assist.AssistScreenHolder.clear() }
    }
}

class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Default
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "AppScope exception", e)
        }
)
