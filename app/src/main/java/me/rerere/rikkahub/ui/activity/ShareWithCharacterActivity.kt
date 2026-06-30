package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.share.copyShareGrantFlagsFrom
import me.rerere.rikkahub.share.putResolvedSharePayload
import me.rerere.rikkahub.share.resolveSharePayload
import me.rerere.rikkahub.share.toRawSharePayload
import org.koin.android.ext.android.inject

class ShareWithCharacterActivity : ComponentActivity() {
    private val settingsStore by inject<SettingsStore>()
    private val httpClient by inject<PlatformHttpClient>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val payload = resolveSharePayload(
                context = this@ShareWithCharacterActivity,
                settingsStore = settingsStore,
                httpClient = httpClient,
                rawSharePayload = intent.toRawSharePayload()
            )
            if (!payload.hasContent()) {
                finish()
                return@launch
            }

            val routeIntent = Intent(this@ShareWithCharacterActivity, RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                type = payload.mimeType
                putResolvedSharePayload(payload)
                copyShareGrantFlagsFrom(intent)
            }
            startActivity(routeIntent)
            finish()
        }
    }
}
