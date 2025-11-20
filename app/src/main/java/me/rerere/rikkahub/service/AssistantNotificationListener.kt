package me.rerere.rikkahub.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NotificationInfo(
    val id: Int,
    val packageName: String,
    val title: String,
    val content: String,
    val postTime: Long
)

class AssistantNotificationListener : NotificationListenerService() {
    companion object {
        private val _notifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
        val notifications = _notifications.asStateFlow()
        
        // Keep last 20 notifications
        private const val MAX_NOTIFICATIONS = 20
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { notification ->
            val extras = notification.notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
            val content = extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
            
            if (title.isBlank() && content.isBlank()) return

            val info = NotificationInfo(
                id = notification.id,
                packageName = notification.packageName,
                title = title,
                content = content,
                postTime = notification.postTime
            )

            _notifications.update { current ->
                (listOf(info) + current).take(MAX_NOTIFICATIONS)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
