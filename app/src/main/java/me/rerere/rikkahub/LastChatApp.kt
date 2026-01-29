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
import me.rerere.rikkahub.data.datastore.SettingsStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import me.rerere.rikkahub.service.MemoryConsolidationWorker
import me.rerere.rikkahub.service.SpontaneousWorker
import java.util.concurrent.TimeUnit
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

private const val TAG = "LastChatApp"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"

class LastChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@LastChatApp)
            workManagerFactory()
            modules(appModule, viewModelModule, dataSourceModule, repositoryModule)
        }
        this.createNotificationChannel()

        // Initialize Python runtime (Chaquopy)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

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

        // Schedule Spontaneous Worker
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "spontaneous_notification",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<SpontaneousWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
        )

        // Schedule Memory Consolidation Worker dynamically
        get<AppScope>().launch {
            get<SettingsStore>().settingsFlow
                .map { it.consolidationWorkerIntervalMinutes to it.consolidationRequiresDeviceIdle }
                .distinctUntilChanged()
                .collect { (interval, idle) ->
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .apply {
                            if (idle) setRequiresDeviceIdle(true)
                        }
                        .build()

                    WorkManager.getInstance(this@LastChatApp).enqueueUniquePeriodicWork(
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
        
        // One-time migration: populate DailyActivityEntity from existing conversation dates
        // This preserves existing streaks when upgrading to the new persistent activity tracking
        get<AppScope>().launch(Dispatchers.IO) {
            val prefs = getSharedPreferences("app_migrations", MODE_PRIVATE)
            if (!prefs.getBoolean("daily_activity_migrated_v1", false)) {
                try {
                    val conversationRepo = get<me.rerere.rikkahub.data.repository.ConversationRepository>()
                    conversationRepo.migrateConversationDatesToActivity()
                    prefs.edit().putBoolean("daily_activity_migrated_v1", true).apply()
                    Log.d(TAG, "Daily activity migration completed successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Daily activity migration failed", e)
                }
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
        notificationManager.createNotificationChannel(chatCompletedChannel)
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
