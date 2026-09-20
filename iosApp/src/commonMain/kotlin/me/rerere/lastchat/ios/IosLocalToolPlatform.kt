package me.rerere.lastchat.ios

data class IosLocalNotificationResult(
    val status: String,
    val scheduledAtEpochMs: Long? = null,
)

interface IosLocalNotificationPlatform {
    suspend fun requestAuthorization(): Boolean

    suspend fun ensureCategories() {}

    suspend fun post(
        identifier: String,
        title: String,
        content: String,
        delayMinutes: Long = 0,
        category: String = IosNotificationCategory.CHAT_COMPLETED,
    ): IosLocalNotificationResult
}

object IosNotificationCategory {
    const val CHAT_COMPLETED = "chat_completed"
    const val WEB_SERVER = "web_server"
    const val SPONTANEOUS = "assistant_spontaneous"
    const val SCHEDULED = "assistant_scheduled"
    const val LOCAL_MODEL_DOWNLOAD = "local_model_download"
}
