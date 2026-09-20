package me.rerere.lastchat.ios

import me.rerere.rikkahub.data.ai.models.ModelCatalogSource
import me.rerere.rikkahub.data.ai.models.ModelCatalogStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class IosDisplayCatalogTest {
    @Test
    fun notificationCategoriesMatchAndroidChannelIds() {
        assertEquals("chat_completed", IosNotificationCategory.CHAT_COMPLETED)
        assertEquals("web_server", IosNotificationCategory.WEB_SERVER)
        assertEquals("assistant_spontaneous", IosNotificationCategory.SPONTANEOUS)
        assertEquals("assistant_scheduled", IosNotificationCategory.SCHEDULED)
        assertEquals("local_model_download", IosNotificationCategory.LOCAL_MODEL_DOWNLOAD)
        assertEquals("web_server:status", IosNotificationCategory.WEB_SERVER_STATUS_ID)
    }

    @Test
    fun catalogSubtitleIncludesSourceAndCounts() {
        val subtitle = iosCatalogSubtitle(
            ModelCatalogStatus(
                source = ModelCatalogSource.DOWNLOADED,
                entryCount = 12,
                providerCount = 4,
                isRefreshing = true,
            ),
        )
        assertEquals("downloaded · 12 models · 4 providers · refreshing", subtitle)
    }
}
