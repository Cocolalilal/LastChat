package me.rerere.rikkahub.data.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URI

/**
 * Routes Google login / OAuth domains out of the in-app WebView into Chrome Custom Tab.
 */
object GoogleAuthRouter {
    private const val TAG = "GoogleAuthRouter"

    private val GOOGLE_AUTH_HOSTS = listOf(
        "accounts.google.com",
        "signin.google.com",
        "myaccount.google.com",
        "oauth2.googleapis.com",
        "accounts.youtube.com",
    )

    fun shouldRouteExternally(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return GOOGLE_AUTH_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    fun openInCustomTab(context: Context, url: String) {
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()
                .launchUrl(context, Uri.parse(url))
            Log.i(TAG, "Opened in Custom Tab: $url")
        } catch (t: Throwable) {
            Log.w(TAG, "Custom Tab launch failed, falling back to ACTION_VIEW: ${t.message}")
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}
