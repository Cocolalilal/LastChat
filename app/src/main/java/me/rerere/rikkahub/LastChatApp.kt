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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.models.ModelMetadataResolver
import me.rerere.rikkahub.data.ai.models.ModelCatalogService
import me.rerere.rikkahub.data.ai.models.mergeCatalogIntoSettings
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import me.rerere.rikkahub.data.memory.MemorySleepWorker
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
import me.rerere.rikkahub.di.SEARCH_PLATFORM_HTTP_CLIENT
import me.rerere.rikkahub.utils.acceptLanguageHeader
import me.rerere.search.SearchService
import org.koin.core.qualifier.named

private const val TAG = "LastChatApp"

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

        // set cursor window size
        DatabaseUtil.setCursorWindowSize(16 * 1024 * 1024)

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

        // One-shot import of legacy memories into the graph store (v34), enqueued the first time the
        // user turns the memory system on (KEEP → runs once, resumes across restarts via its own
        // watermark). Gating on enable avoids populating the store for users who never opt in.
        get<AppScope>().launch {
            get<SettingsStore>().settingsFlow
                .map { it.memory.enabled }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    WorkManager.getInstance(this@LastChatApp).enqueueUniqueWork(
                        me.rerere.rikkahub.data.memory.MemoryImportWorker.WORK_NAME,
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<me.rerere.rikkahub.data.memory.MemoryImportWorker>()
                            .setConstraints(
                                Constraints.Builder()
                                    .setRequiresBatteryNotLow(true)
                                    .build()
                            )
                            .build()
                    )
                }
        }

        // Memory maintenance scheduling. When the graph memory system is enabled, the periodic sleep
        // pass (P3) owns decay/consolidation/bounded-growth and the legacy MemoryConsolidationWorker is
        // retired; when it is off, existing installs keep the legacy consolidation behaviour until they
        // opt in. Exactly one of the two is scheduled at any time.
        get<AppScope>().launch {
            get<SettingsStore>().settingsFlow
                .map { Triple(it.memory.enabled, it.consolidationWorkerIntervalMinutes, it.consolidationRequiresDeviceIdle) }
                .distinctUntilChanged()
                .collect { (memoryEnabled, interval, idle) ->
                    val wm = WorkManager.getInstance(this@LastChatApp)
                    if (memoryEnabled) {
                        // Sleep pass every ~12h, battery-not-low only: the deterministic decay/expiry/
                        // size stages must age the graph even offline; model stages self-skip.
                        wm.cancelUniqueWork("memory_consolidation")
                        wm.enqueueUniquePeriodicWork(
                            MemorySleepWorker.PERIODIC_WORK_NAME,
                            ExistingPeriodicWorkPolicy.UPDATE,
                            PeriodicWorkRequestBuilder<MemorySleepWorker>(12, TimeUnit.HOURS)
                                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                                .build()
                        )
                    } else {
                        wm.cancelUniqueWork(MemorySleepWorker.PERIODIC_WORK_NAME)
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .apply {
                                if (idle) setRequiresDeviceIdle(true)
                            }
                            .build()
                        wm.enqueueUniquePeriodicWork(
                            "memory_consolidation",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(
                                interval.toLong().coerceAtLeast(15), TimeUnit.MINUTES
                            )
                                .setConstraints(constraints)
                                .build()
                        )
                    }
                }
        }
        
        // Embedding backfill (§12.4). When the memory system is on and the configured embedding model
        // changes — or is configured for the first time — re-embed mismatched/missing-vector ACTIVE
        // nodes in the background. FTS covers every recall/dedup path in the interim, so a model
        // switch is a temporary recall-quality dip, never data loss. The worker no-ops (and stops
        // re-enqueuing) once the store is aligned with the current model.
        get<AppScope>().launch {
            get<SettingsStore>().settingsFlow
                .map { it.memory.enabled to it.embeddingModelId }
                .distinctUntilChanged()
                .filter { (enabled, _) -> enabled }
                .collect {
                    me.rerere.rikkahub.data.memory.MemoryEmbeddingBackfillWorker.enqueue(this@LastChatApp)
                }
        }

        // Update app shortcuts when recently used assistants change
        val appShortcutManager = me.rerere.rikkahub.utils.AppShortcutManager(this)
        get<AppScope>().launch {
            get<SettingsStore>().settingsFlow
                .map { Triple(it.recentlyUsedAssistants, it.assistants, it.init) }
                .distinctUntilChanged()
                .collect { (recentlyUsed, assistants, isInit) ->
                    if (!isInit) {
                        appShortcutManager.updateAssistantShortcuts(recentlyUsed, assistants)
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
}

class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Default
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "AppScope exception", e)
        }
)
