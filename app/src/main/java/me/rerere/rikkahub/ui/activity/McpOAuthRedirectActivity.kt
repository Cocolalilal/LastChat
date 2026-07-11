package me.rerere.rikkahub.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.mcp.McpOAuthManager
import org.koin.android.ext.android.inject

class McpOAuthRedirectActivity : ComponentActivity() {
    private val oauthManager by inject<McpOAuthManager>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oauthManager.handleRedirect(intent?.data)
        startActivity(
            Intent(this, RouteActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }
}
