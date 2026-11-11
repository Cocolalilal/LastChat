package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import me.rerere.rikkahub.phoneuse.createPhoneOverlayDialog
import androidx.activity.enableEdgeToEdge
import androidx.core.view.doOnPreDraw
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.service.assist.AssistScreenHolder
import me.rerere.rikkahub.ui.components.ui.FloatingOverlayLayout
import me.rerere.rikkahub.ui.components.ui.AppToasterHost
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.ui.theme.AssistantChatTheme
import me.rerere.rikkahub.data.datastore.resolveAssistantOverlayAssistant
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Translucent floating activity that hosts the digital-assistant overlay.
 *
 * Launched by [me.rerere.rikkahub.service.assist.LastChatVoiceInteractionSession] when the
 * device assist gesture fires. A fixed transparent window hosts the floating panels
 * over the live app, with compositor blur where supported. Screenshots are only model input.
 */
class AssistantOverlayActivity : ComponentActivity() {
    private val highlighter by inject<Highlighter>()
    private val settingsStore by inject<SettingsStore>()
    private val phoneManager by inject<me.rerere.rikkahub.phoneuse.PhoneUseManager>()
    private val viewModel: AssistantOverlayVM by viewModel()
    private var nativeBlurActive by mutableStateOf(false)
    private lateinit var floatingWindow: AssistantFloatingWindow
    private lateinit var controlsDialog: ComponentDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        floatingWindow = AssistantFloatingWindow(this) { nativeBlurActive = it }
        floatingWindow.attach()
        val controls = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@AssistantOverlayActivity)
            setViewTreeSavedStateRegistryOwner(this@AssistantOverlayActivity)
        }
        controlsDialog = createPhoneOverlayDialog(this, controls).apply {
            setOwnerActivity(this@AssistantOverlayActivity)
            window!!.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION)
        }
        floatingWindow.setContentWindow(controlsDialog.window!!)
        floatingWindow.setBlurEnabled(settingsStore.settingsFlow.value.displaySetting.enableBlurEffect)
        setContentView(android.widget.FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setOnClickListener { controlsDialog.onBackPressedDispatcher.onBackPressed() }
        })

        val existingId = intent.getStringExtra(RouteActivity.EXTRA_CONVERSATION_ID)?.let {
            runCatching { kotlin.uuid.Uuid.parse(it) }.getOrNull()
        }
        intent.getStringExtra(RouteActivity.EXTRA_ASSISTANT_ID)?.let { value ->
            runCatching { kotlin.uuid.Uuid.parse(value) }.getOrNull()?.let { id ->
                if (settingsStore.settingsFlow.value.assistants.any { it.id == id }) viewModel.overlayAssistantId = id
            }
        }
        viewModel.initialize(existingId)
        controls.setContent {
            val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
            val conversationId by viewModel.conversationId.collectAsStateWithLifecycle()
            val assistant = remember(settings.assistants, conversationId) {
                settings.assistants.firstOrNull { it.id == viewModel.overlayAssistantId }
                    ?: settings.resolveAssistantOverlayAssistant()
            }
            val toastState = rememberAppToasterState()
            SideEffect { floatingWindow.setBlurEnabled(settings.displaySetting.enableBlurEffect) }

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
                    AssistantChatTheme(assistant) {
                        FloatingOverlayLayout(content = {
                            AssistantOverlayScreen(
                                viewModel = viewModel,
                                autoStartListening = existingId == null,
                                nativeBlurActive = nativeBlurActive,
                                floatingContent = true,
                                onSurfaceOpacity = { alpha -> controlsDialog.window?.decorView?.background?.alpha = (alpha * 255).toInt() },
                                onPhoneUseYield = { moveTaskToBack(true) },
                                onDismiss = { finish() },
                                onOpenInApp = {
                                    // The overlay runs in its own task. Reuse the app task and carry
                                    // both IDs: if the ChatService state is cleaned up during the
                                    // hand-off, RouteActivity can still recreate the chat with the
                                    // overlay's character rather than whichever character was selected
                                    // before the assist gesture.
                                    val conversationId = viewModel.conversationId.value ?: return@AssistantOverlayScreen
                                    val routeIntent = Intent(this, RouteActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        putExtra(RouteActivity.EXTRA_CONVERSATION_ID, conversationId.toString())
                                        putExtra(RouteActivity.EXTRA_ASSISTANT_ID, viewModel.overlayAssistantId.toString())
                                    }
                                    runCatching { startActivity(routeIntent) }
                                        .onSuccess { finishAndRemoveTask() }
                                        .onFailure { error ->
                                            android.util.Log.e(TAG, "Unable to open overlay conversation", error)
                                        }
                                },
                            )
                        }, decoration = { AppToasterHost(state = toastState) })
                    }
                }
            }
        }
        controlsDialog.show()
        floatingWindow.updateContentBounds()
        completePhoneReturnOnDraw()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        floatingWindow.updateContentBounds()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        completePhoneReturnOnDraw()
        intent.getStringExtra(RouteActivity.EXTRA_CONVERSATION_ID)?.let { value ->
            runCatching { kotlin.uuid.Uuid.parse(value) }.getOrNull()?.let(viewModel::initialize)
        }
    }

    private fun completePhoneReturnOnDraw() {
        controlsDialog.window!!.decorView.doOnPreDraw {
            if (phoneManager.assistantReturning.value) phoneManager.assistantDocked.value = null
            phoneManager.assistantReturning.value = false
        }
        controlsDialog.window!!.decorView.invalidate()
    }

    override fun onDestroy() {
        floatingWindow.close()
        if (::controlsDialog.isInitialized) controlsDialog.dismiss()
        super.onDestroy()
    }

    // NOTE: we intentionally do NOT clear AssistScreenHolder here. After "Open in app" the
    // in-app chat can still call look_at_screen, and the 5-minute freshness window (plus the
    // next summon overwriting it) is enough to keep it from going stale.

    private companion object {
        const val TAG = "AssistantOverlayActivity"
    }
}
