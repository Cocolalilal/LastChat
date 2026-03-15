package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.WEB_SERVER_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.web.WebServerPhase
import me.rerere.rikkahub.web.WebServerManager
import org.koin.android.ext.android.inject

class WebServerService : Service() {

    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.WEB_SERVER_START"
        const val ACTION_STOP = "me.rerere.rikkahub.action.WEB_SERVER_STOP"
        const val EXTRA_PORT = "port"
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "WebServerService"
        private const val WIFI_LOCK_TAG = "lastchat:web_server"

        fun start(context: android.content.Context, port: Int) {
            val intent = Intent(context, WebServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, WebServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val webServerManager: WebServerManager by inject()
    private val settingsStore: SettingsStore by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateObserverJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                startForegroundCompat(buildStartingNotification())
                startObservingState()
                acquireWifiLock()
                webServerManager.start(port = port)
            }

            ACTION_STOP -> {
                webServerManager.stop()
                serviceScope.launch {
                    settingsStore.update { settings ->
                        settings.copy(webServerEnabled = false)
                    }
                }
            }

            null -> {
                startForegroundCompat(buildStartingNotification())
                serviceScope.launch {
                    val settings = settingsStore.settingsFlowRaw.first()
                    if (settings.webServerEnabled) {
                        startObservingState()
                        acquireWifiLock()
                        webServerManager.start(port = settings.webServerPort)
                    } else {
                        releaseWifiLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stateObserverJob?.cancel()
        releaseWifiLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startObservingState() {
        if (stateObserverJob != null) return

        stateObserverJob = serviceScope.launch {
            var wasRunning = false
            webServerManager.state.collect { state ->
                when (state.phase) {
                    WebServerPhase.Starting -> {
                        acquireWifiLock()
                        updateNotification(buildStartingNotification())
                    }

                    WebServerPhase.Running -> {
                        wasRunning = true
                        acquireWifiLock()
                        updateNotification(buildRunningNotification(state.preferredUrl))
                    }

                    WebServerPhase.Stopping -> {
                        updateNotification(buildStoppingNotification())
                    }

                    WebServerPhase.Error -> {
                        releaseWifiLock()
                        updateNotification(buildErrorNotification(state.error ?: getString(R.string.notification_web_server_error)))
                        settingsStore.update { settings ->
                            settings.copy(webServerEnabled = false)
                        }
                        stopForeground(STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }

                    WebServerPhase.Idle -> {
                        releaseWifiLock()
                        if (!wasRunning) {
                            return@collect
                        }
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun updateNotification(notification: android.app.Notification) {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildStartingNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, WEB_SERVER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_web_server_title))
            .setContentText(getString(R.string.notification_web_server_starting))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun buildStoppingNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, WEB_SERVER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_web_server_title))
            .setContentText(getString(R.string.notification_web_server_stopping))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun buildRunningNotification(url: String?): android.app.Notification {
        val stopIntent = Intent(this, WebServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val openIntent = Intent(this, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("webServerSettings", true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, WEB_SERVER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_web_server_title))
            .setContentText(url ?: getString(R.string.notification_web_server_running))
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.setting_page_web_server_stop), stopPendingIntent)
            .build()
    }

    private fun buildErrorNotification(error: String): android.app.Notification {
        return NotificationCompat.Builder(this, WEB_SERVER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_web_server_error))
            .setContentText(error)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
    }

    private fun acquireWifiLock() {
        val existingLock = wifiLock
        if (existingLock?.isHeld == true) {
            return
        }

        runCatching {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val lock = existingLock ?: wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG).apply {
                setReferenceCounted(false)
            }
            if (!lock.isHeld) {
                lock.acquire()
            }
            wifiLock = lock
        }.onFailure {
            Log.w(TAG, "Failed to acquire Wi-Fi lock", it)
        }
    }

    private fun releaseWifiLock() {
        runCatching {
            wifiLock?.takeIf { it.isHeld }?.release()
        }.onFailure {
            Log.w(TAG, "Failed to release Wi-Fi lock", it)
        }
    }
}
