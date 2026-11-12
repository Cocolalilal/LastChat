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
import kotlinx.coroutines.launch
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.phoneuse.PhoneUseManager
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

/** Permission and outside-tap owner. The assistant scene survives this Activity yielding. */
class AssistantOverlayActivity : ComponentActivity() {
    private val manager by inject<PhoneUseManager>()
    private lateinit var session: AssistantOverlaySession
    private var fallback: AssistantFloatingWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setWindowAnimations(0)
        val existing = intent.getStringExtra(RouteActivity.EXTRA_CONVERSATION_ID)?.let {
            runCatching { Uuid.parse(it) }.getOrNull()
        }
        session = manager.assistantOverlay.value?.takeIf { it.presentation.value != AssistantPresentation.CLOSED }
            ?: AssistantOverlaySession(this, existing).also { manager.assistantOverlay.value = it }
        session.bindActivity(this)
        existing?.let(session::openConversation)
        if (manager.assistantOverlayHostReady.value) {
            installOutsideTapOwner()
        } else {
            fallback = AssistantFloatingWindow(this, session)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                manager.assistantOverlayHostReady.collect { hosted ->
                    if (hosted && fallback != null) {
                        fallback?.close()
                        fallback = null
                        installOutsideTapOwner()
                    } else if (!hosted && fallback == null) {
                        // Let the service finish detaching its Window before adopting the scene.
                        window.decorView.post {
                            if (!manager.assistantOverlayHostReady.value && fallback == null &&
                                lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                                session.presentation.value != AssistantPresentation.CLOSED) {
                                fallback = AssistantFloatingWindow(this@AssistantOverlayActivity, session)
                                session.setPresented(true)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun installOutsideTapOwner() {
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { session.onBackPressedDispatcher.onBackPressed() }
        })
    }

    override fun onResume() {
        super.onResume()
        session.bindActivity(this)
        session.onActivityResumed()
        manager.assistantReturning.value = false
        manager.assistantDocked.value = null
    }
    override fun onPause() {
        session.onActivityPaused()
        super.onPause()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(RouteActivity.EXTRA_CONVERSATION_ID)?.let {
            runCatching { Uuid.parse(it) }.getOrNull()?.let(session::openConversation)
        }
    }
    override fun onDestroy() {
        fallback?.close()
        session.unbindActivity(this)
        // The service retains the session during phone use; lifecycle and VM are session-owned.
        if (isFinishing && session.presentation.value != AssistantPresentation.PHONE) session.close()
        super.onDestroy()
    }
}
