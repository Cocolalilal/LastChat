package me.rerere.rikkahub.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import kotlinx.coroutines.launch
import me.rerere.rikkahub.PYTHON_SERVER_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.repository.PythonPluginManager
import org.koin.android.ext.android.inject

class PythonServerService : Service() {
    companion object {
        const val ACTION_START = "me.rerere.rikkahub.action.PYTHON_SERVER_START"
        const val ACTION_STOP = "me.rerere.rikkahub.action.PYTHON_SERVER_STOP"
        const val EXTRA_NAME = "name"
        const val EXTRA_PORT = "port"
        private const val NOTIFICATION_ID = 2003
        private const val TAG = "PythonServerService"
        private const val WIFI_LOCK_TAG = "lastchat:python_server"

        fun start(context: Context, name: String, port: Int? = null) {
            val intent = Intent(context, PythonServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_NAME, name)
                if (port != null && port > 0) putExtra(EXTRA_PORT, port)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start PythonServerService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PythonServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val pluginManager: PythonPluginManager by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wifiLock: WifiManager.WifiLock? = null
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Python Server"
                val port = if (intent.hasExtra(EXTRA_PORT)) intent.getIntExtra(EXTRA_PORT, 0) else null
                acquireWifiLock()
                startForegroundCompat(buildNotification(name, port))
                startObserving()
            }
            ACTION_STOP -> {
                pluginManager.stopAllServers()
                releaseWifiLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                if (!pluginManager.isAnyServerRunning()) {
                    releaseWifiLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    acquireWifiLock()
                    val first = pluginManager.runningServers.value.values.firstOrNull()
                    startForegroundCompat(buildNotification(first?.name ?: "Python Server", first?.port))
                    startObserving()
                }
            }
        }
        return START_STICKY
    }

    private fun startObserving() {
        if (observerJob != null) return
        observerJob = serviceScope.launch {
            pluginManager.runningServers.collect { servers ->
                if (servers.isEmpty()) {
                    releaseWifiLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val first = servers.values.first()
                    updateNotification(buildNotification(first.name, first.port))
                }
            }
        }
    }

    private fun startForegroundCompat(notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to promote Python server to foreground", e)
            stopSelf()
            false
        }
    }

    private fun updateNotification(notification: Notification) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(name: String, port: Int?): Notification {
        val stopIntent = Intent(this, PythonServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = Intent(this, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val portText = if (port != null && port > 0) " (port $port)" else ""
        return NotificationCompat.Builder(this, PYTHON_SERVER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Python Server Running")
            .setContentText("$name$portText • PRoot Sandboxed")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        runCatching {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val lock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, WIFI_LOCK_TAG).apply {
                setReferenceCounted(false)
            }
            if (!lock.isHeld) lock.acquire()
            wifiLock = lock
        }.onFailure { Log.w(TAG, "Failed to acquire Wi-Fi lock", it) }
    }

    private fun releaseWifiLock() {
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
    }

    override fun onDestroy() {
        observerJob?.cancel()
        releaseWifiLock()
        serviceScope.cancel()
        super.onDestroy()
    }
}
