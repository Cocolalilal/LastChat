package me.rerere.rikkahub.service.assist

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import me.rerere.rikkahub.ui.activity.AssistantOverlayActivity

/**
 * A single assist invocation.
 *
 * We don't draw the assistant UI inside the session window itself — instead we cache
 * the trigger-time screenshot and immediately launch [AssistantOverlayActivity], which
 * hosts the full Compose overlay (glow + reply panel + input) with access to Koin,
 * ViewModels and the generation pipeline. The system session window is then hidden.
 *
 * Rationale: hosting a full Compose + Koin + ViewModelStore stack inside a
 * VoiceInteractionSession window is fragile across OEMs; a translucent activity reuses
 * the app's existing single-activity rails and behaves consistently.
 */
class LastChatVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        if (screenshot != null) {
            AssistScreenHolder.store(screenshot)
        } else {
            AssistScreenHolder.clear()
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, AssistantOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        }
        runCatching { context.startActivity(intent) }
        // Dismiss the (empty) system session window; the activity owns the UX now.
        hide()
    }
}
