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
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.components.textselection.TextSelectionSheet
import me.rerere.rikkahub.ui.components.ui.AppToasterHost
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Floating activity that handles Android text selection toolbar integration.
 * Appears as an overlay when user selects text and taps "Ask LastChat".
 */
class TextSelectionActivity : ComponentActivity() {
    private val highlighter by inject<Highlighter>()
    private val settingsStore by inject<SettingsStore>()
    private val viewModel: TextSelectionVM by viewModel()
    private var isContinuingInApp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val selectedText = intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        if (selectedText.isBlank()) {
            finish()
            return
        }

        viewModel.updateInput(QuickAskInputData(text = selectedText))

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
                            if (isContinuingInApp) return@TextSelectionSheet
                            isContinuingInApp = true
                            lifecycleScope.launch {
                                val target = runCatching {
                                    viewModel.persistAndPrepareChatContinuation()
                                }.getOrNull()
                                if (target == null) {
                                    isContinuingInApp = false
                                    return@launch
                                }
                                val (conversationId, assistantId) = target
                                val routeIntent = Intent(this@TextSelectionActivity, RouteActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra(RouteActivity.EXTRA_CONVERSATION_ID, conversationId.toString())
                                    putExtra(RouteActivity.EXTRA_ASSISTANT_ID, assistantId.toString())
                                }
                                startActivity(routeIntent)
                                finish()
                            }
                        },
                        onOpenInStudio = { itemId ->
                            val routeIntent = Intent(this@TextSelectionActivity, RouteActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(RouteActivity.EXTRA_OPEN_STUDIO, true)
                                putExtra(RouteActivity.EXTRA_STUDIO_ITEM_ID, itemId)
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
}
