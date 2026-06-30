package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.share.resolveSharePayload
import me.rerere.rikkahub.share.toRawSharePayload
import me.rerere.rikkahub.ui.components.textselection.TextSelectionSheet
import me.rerere.rikkahub.ui.components.ui.AppToasterHost
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class AskLastChatShareActivity : ComponentActivity() {
    private val highlighter by inject<Highlighter>()
    private val settingsStore by inject<SettingsStore>()
    private val httpClient by inject<PlatformHttpClient>()
    private val viewModel: TextSelectionVM by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val payload = resolveSharePayload(
                context = this@AskLastChatShareActivity,
                settingsStore = settingsStore,
                httpClient = httpClient,
                rawSharePayload = intent.toRawSharePayload()
            )
            if (!payload.hasContent()) {
                finish()
                return@launch
            }

            viewModel.updateInput(
                QuickAskInputData(
                    text = payload.text,
                    attachments = payload.attachments.map { attachment ->
                        QuickAskAttachment(
                            uri = attachment.uri,
                            fileName = attachment.fileName,
                            mimeType = attachment.mimeType
                        )
                    }
                )
            )

            setContent {
                val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
                val toastState = rememberAppToasterState()

                RikkahubTheme {
                    CompositionLocalProvider(
                        LocalSettings provides settings,
                        LocalHighlighter provides highlighter,
                        LocalToaster provides toastState,
                    ) {
                        TextSelectionSheet(
                            viewModel = viewModel,
                            onDismiss = { finish() },
                            onContinueInApp = {
                                val continuationData = viewModel.buildContinuationData()
                                    ?: return@TextSelectionSheet
                                val routeIntent =
                                    Intent(this@AskLastChatShareActivity, RouteActivity::class.java).apply {
                                        flags =
                                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        putQuickAskContinuationData(continuationData)
                                    }
                                startActivity(routeIntent)
                                finish()
                            }
                        )
                        AppToasterHost(state = toastState)
                    }
                }
            }
        }
    }
}
