package me.rerere.lastchat.ios

data class IosLocalNotificationResult(
    val status: String,
    val scheduledAtEpochMs: Long? = null,
)

interface IosLocalNotificationPlatform {
    suspend fun requestAuthorization(): Boolean

    suspend fun post(
        identifier: String,
        title: String,
        content: String,
        delayMinutes: Long = 0,
    ): IosLocalNotificationResult
}
