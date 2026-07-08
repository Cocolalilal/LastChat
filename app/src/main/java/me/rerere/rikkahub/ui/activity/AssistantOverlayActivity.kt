package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.assist.AssistScreenHolder
import me.rerere.rikkahub.ui.components.ui.AppToasterHost
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Translucent floating activity that hosts the digital-assistant overlay.
 *
 * Launched by [me.rerere.rikkahub.service.assist.LastChatVoiceInteractionSession] when the
 * device assist gesture fires. Draws over the current app using the trigger-time
 * screenshot (see [AssistScreenHolder]) as a blurred backdrop.
 */
class AssistantOverlayActivity : ComponentActivity() {
    private val highlighter by inject<Highlighter>()
    private val settingsStore by inject<SettingsStore>()
    private val viewModel: AssistantOverlayVM by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
            val toastState = rememberAppToasterState()

            RikkahubTheme {
                // The overlay has no NavHost; provide a no-op controller so composables
                // that read LocalNavController (e.g. ModelList → ModelItem long-press to
                // provider settings) don't crash. Long-press navigation is simply a no-op.
                val noopNavController = remember {
                    NavHostController(this).also {
                        // No graph set — navigate() calls will be no-ops
                    }
                }
                CompositionLocalProvider(
                    LocalSettings provides settings,
                    LocalHighlighter provides highlighter,
                    LocalToaster provides toastState,
                    LocalNavController provides noopNavController,
                ) {
                    AssistantOverlayScreen(
                        viewModel = viewModel,
                        onDismiss = { finish() },
                        onOpenInApp = {
                            val data = viewModel.buildContinuationData()
                            val routeIntent = Intent(this, RouteActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                if (data != null) putQuickAskContinuationData(data)
                            }
                            startActivity(routeIntent)
                            finish()
                        },
                    )
                    AppToasterHost(state = toastState)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AssistScreenHolder.clear()
    }
}
