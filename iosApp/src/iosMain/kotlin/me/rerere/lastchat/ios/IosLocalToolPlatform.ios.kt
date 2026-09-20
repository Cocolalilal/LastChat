package me.rerere.lastchat.ios

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Clock

class IosUserNotificationPlatform : IosLocalNotificationPlatform {
    override suspend fun requestAuthorization(): Boolean {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        return suspendCancellableCoroutine { continuation ->
            center.requestAuthorizationWithOptions(
                options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
            ) { allowed, _ ->
                if (continuation.isActive) continuation.resume(allowed)
            }
        }
    }

    override suspend fun ensureCategories() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val categories = listOf(
            IosNotificationCategory.CHAT_COMPLETED,
            IosNotificationCategory.WEB_SERVER,
            IosNotificationCategory.SPONTANEOUS,
            IosNotificationCategory.SCHEDULED,
            IosNotificationCategory.LOCAL_MODEL_DOWNLOAD,
        ).map { id ->
            platform.UserNotifications.UNNotificationCategory.categoryWithIdentifier(
                identifier = id,
                actions = emptyList<platform.UserNotifications.UNNotificationAction>(),
                intentIdentifiers = emptyList<String>(),
                options = 0u,
            )
        }.toSet()
        center.setNotificationCategories(categories)
    }

    override suspend fun post(
        identifier: String,
        title: String,
        content: String,
        delayMinutes: Long,
        category: String,
    ): IosLocalNotificationResult {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        ensureCategories()
        val granted = requestAuthorization()
        if (!granted) return IosLocalNotificationResult("error: permission denied")

        val notificationContent = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(content)
            setSound(UNNotificationSound.defaultSound())
            setCategoryIdentifier(category)
            setUserInfo(mapOf("conversation_id" to identifier.substringBefore(':')))
        }
        val delaySeconds = delayMinutes.coerceAtLeast(0) * 60
        val trigger = if (delaySeconds > 0) {
            UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(delaySeconds.toDouble(), repeats = false)
        } else null
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = notificationContent,
            trigger = trigger,
        )
        val errorMessage = suspendCancellableCoroutine { continuation ->
            center.addNotificationRequest(request) { error ->
                if (continuation.isActive) continuation.resume(error?.localizedDescription)
            }
        }
        if (errorMessage != null) return IosLocalNotificationResult("error: $errorMessage")
        val scheduledAt = if (delaySeconds > 0) {
            Clock.System.now().toEpochMilliseconds() + delaySeconds * 1_000
        } else null
        return IosLocalNotificationResult("success", scheduledAt)
    }

    override suspend fun cancel(identifier: String) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(identifier))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(identifier))
    }
}
