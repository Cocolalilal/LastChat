package me.rerere.lastchat.ios

import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.share.PortableSharePayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IosLocalProviderTest {
    @Test
    fun liteRtProviderMapsToLocalType() {
        val provider = ProviderSetting.LiteRtLocal(
            name = "Local",
            models = listOf(Model(modelId = "on-device", displayName = "On-device", type = ModelType.CHAT)),
        )
        assertEquals(IosProviderType.LOCAL, chatProviderType(provider))
        val keyed = provider.withApiKey("", provider.models.first())
        assertIs<ProviderSetting.LiteRtLocal>(keyed)
        assertEquals("on-device", keyed.models.single().modelId)
    }

    @Test
    fun sharePayloadPromptIsReadyForIngest() {
        val payload = PortableSharePayload(text = "hello from share", subject = "Page")
        assertTrue(payload.hasContent())
        assertTrue(payload.promptText().contains("hello from share"))
    }
}
