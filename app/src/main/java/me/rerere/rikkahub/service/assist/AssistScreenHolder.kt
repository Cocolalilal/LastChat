package me.rerere.rikkahub.service.assist

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/**
 * Holds the screenshot captured at the moment the assist gesture fired.
 *
 * The system delivers the underlying app's screen to
 * [LastChatVoiceInteractionSession.onHandleScreenshot] *before* our overlay draws.
 * By the time the model (or user) asks for "the current screen", our own UI is on
 * top — so we cache the trigger-time frame here and serve it on demand.
 */
object AssistScreenHolder {

    private const val MAX_DIMEN = 1280
    private const val JPEG_QUALITY = 80
    // Screens captured more than this long ago are considered stale.
    private const val FRESHNESS_WINDOW_MS = 5 * 60 * 1000L

    @Volatile
    private var jpegBytes: ByteArray? = null

    @Volatile
    var capturedAtMillis: Long = 0L
        private set

    // Bumped every time the model actually reads the screen (look_at_screen). The overlay
    // observes this to re-trigger the glow "wave" so the user sees it look.
    private val _screenReadSignal = MutableStateFlow(0L)
    val screenReadSignal: StateFlow<Long> = _screenReadSignal.asStateFlow()

    fun notifyScreenRead() {
        _screenReadSignal.value = System.currentTimeMillis()
    }

    @Synchronized
    fun store(bitmap: Bitmap) {
        runCatching {
            val scaled = downscale(bitmap)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            if (scaled !== bitmap) scaled.recycle()
            jpegBytes = out.toByteArray()
            capturedAtMillis = System.currentTimeMillis()
        }
    }

    @Synchronized
    fun clear() {
        jpegBytes = null
        capturedAtMillis = 0L
    }

    fun hasFreshScreenshot(): Boolean {
        val bytes = jpegBytes ?: return false
        return bytes.isNotEmpty() &&
            (System.currentTimeMillis() - capturedAtMillis) <= FRESHNESS_WINDOW_MS
    }

    /** Base64 data URL (`data:image/jpeg;base64,...`) suitable for a vision model image part. */
    fun dataUrlOrNull(): String? {
        val bytes = jpegBytes?.takeIf { it.isNotEmpty() } ?: return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Decoded bitmap for use as an in-overlay blurred backdrop. */
    fun bitmapOrNull(): Bitmap? {
        val bytes = jpegBytes?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    private fun downscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = maxOf(w, h)
        if (longest <= MAX_DIMEN || longest == 0) return bitmap
        val ratio = MAX_DIMEN.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
}
