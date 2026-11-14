package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.phoneuse.PhoneUseManager
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

/** Permission and outside-tap owner. The assistant scene survives this Activity yielding. */
class AssistantOverlayActivity : ComponentActivity() {
    private val manager by inject<PhoneUseManager>()
    private val settings by inject<SettingsStore>()
    private var session: AssistantOverlaySession? = null
    private var fallback: AssistantFloatingWindow? = null
    private var invocationJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setWindowAnimations(0)
        installOutsideTapOwner()
        handleInvocation(intent, recreating = savedInstanceState != null)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                manager.assistantOverlayHostReady.collect { hosted ->
                    val scene = session ?: return@collect
                    if (hosted && fallback != null) {
                        fallback?.close()
                        fallback = null
                        installOutsideTapOwner()
                    } else if (!hosted && fallback == null) {
                        // Let the service finish detaching its Window before adopting the scene.
                        window.decorView.post {
                            if (!manager.assistantOverlayHostReady.value && fallback == null &&
                                session === scene && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                                scene.presentation.value != AssistantPresentation.CLOSED) {
                                fallback = AssistantFloatingWindow(this@AssistantOverlayActivity, scene)
                                scene.setPresented(true)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleInvocation(intent: Intent, recreating: Boolean = false) {
        invocationJob?.cancel()
        invocationJob = lifecycleScope.launch {
            val configured = settings.settingsFlow.awaitAssistantOverlayAssistant()
            val requested = intent.getStringExtra(RouteActivity.EXTRA_CONVERSATION_ID)?.let {
                runCatching { Uuid.parse(it) }.getOrNull()
            }
            val previous = manager.assistantOverlay.value
            val retained = previous?.takeUnless { it.isClosing }
            val reuse = shouldReuseAssistantScene(requested, retained?.viewModel?.conversationId?.value,
                retained?.presentation?.value, configured.id, manager.session.value, recreating)
            val scene = if (reuse) requireNotNull(retained) else {
                fallback?.close()
                fallback = null
                // A new invocation takes control instead of sharing an unrelated phone task.
                // Do not finish this singleTask Activity while replacing its retained scene.
                previous?.close(finishActivity = false)
                if (requested == null || manager.session.value?.conversationId != requested) manager.stop()
                AssistantOverlaySession(this@AssistantOverlayActivity, requested).also {
                    manager.assistantOverlay.value = it
                }
            }
            session = scene
            scene.bindActivity(this@AssistantOverlayActivity)
            if (requested != null) scene.openConversation(requested)
            else if (!recreating) scene.viewModel.conversationId.value?.let(manager::pauseForAssistantInvocation)
            if (manager.assistantOverlayHostReady.value) installOutsideTapOwner()
            else if (fallback == null) fallback = AssistantFloatingWindow(this@AssistantOverlayActivity, scene)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumeSession(scene)
        }
    }

    private fun installOutsideTapOwner() {
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { session?.onBackPressedDispatcher?.onBackPressed() ?: finish() }
        })
    }

    private fun resumeSession(scene: AssistantOverlaySession) {
        scene.bindActivity(this)
        scene.onActivityResumed()
        manager.assistantReturning.value = false
        manager.assistantDocked.value = null
    }

    override fun onResume() {
        super.onResume()
        session?.let(::resumeSession)
    }

    override fun onPause() {
        session?.onActivityPaused()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleInvocation(intent)
    }

    override fun finish() {
        val scene = session
        if (scene != null && scene.presentation.value != AssistantPresentation.CLOSED) scene.close()
        else super.finish()
    }

    internal fun finishOverlaySession(scene: AssistantOverlaySession) {
        // A cancelled/replaced scene cannot finish the Activity now showing its successor.
        if (session === scene) super.finish()
    }

    override fun onDestroy() {
        fallback?.close()
        session?.let { scene ->
            scene.unbindActivity(this)
            if (isFinishing && scene.presentation.value != AssistantPresentation.PHONE) scene.close()
        }
        super.onDestroy()
    }
}
