package me.rerere.common.platform.android

import android.content.Context
import android.content.Intent
import me.rerere.common.platform.PlatformShareSheet

class AndroidPlatformShareSheet(
    private val context: Context,
) : PlatformShareSheet {
    override val available: Boolean = true

    override fun shareText(title: String, text: String) {
        if (text.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(send, title.ifBlank { "Share" })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
