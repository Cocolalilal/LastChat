package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class CodexOAuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, me.rerere.rikkahub.RouteActivity::class.java).apply {
                // Provider detail routes require a UUID. "codex" is the provider type,
                // so navigating to it as an ID crashed the app after OAuth completed.
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
        )
        finish()
    }
}
