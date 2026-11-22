package me.rerere.rikkahub.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest

/** Private, bounded last-good photo cache, independent of the app process and Glance sessions. */
internal object WidgetAvatarCache {
    private fun directory(context: Context) = File(context.filesDir, "widget-avatars")

    private fun file(context: Context, widgetId: Int, config: WidgetConfig): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(config.copy(seed = 0, variant = 0, presetId = null).toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(directory(context), "${widgetId}_$digest.png")
    }

    @Synchronized
    fun read(context: Context, widgetId: Int, config: WidgetConfig): Bitmap? = runCatching {
        val target = AtomicFile(file(context, widgetId, config))
        if (!target.baseFile.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        target.openRead().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth !in 1..WidgetAvatarRenderer.MAX_BITMAP_PX ||
            bounds.outHeight !in 1..WidgetAvatarRenderer.MAX_BITMAP_PX) return null
        target.openRead().use { BitmapFactory.decodeStream(it) }
    }.getOrNull()

    @Synchronized
    fun write(context: Context, widgetId: Int, config: WidgetConfig, bitmap: Bitmap) {
        // A download finishing after reconfiguration/deletion must not resurrect the old avatar.
        if (WidgetPrefs.getConfig(context, widgetId) != config) return
        runCatching {
            directory(context).mkdirs()
            val target = AtomicFile(file(context, widgetId, config))
            val stream = target.startWrite()
            try {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                target.finishWrite(stream)
            } catch (error: Exception) {
                target.failWrite(stream)
                throw error
            }
            directory(context).listFiles()?.filter {
                it.name.startsWith("${widgetId}_") && it != target.baseFile
            }?.forEach { it.delete() }
        }
    }

    @Synchronized
    fun delete(context: Context, widgetId: Int) {
        directory(context).listFiles()?.filter { it.name.startsWith("${widgetId}_") }?.forEach { it.delete() }
    }
}
