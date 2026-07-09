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
 *
 * Two copies are kept: a high-resolution one for the on-screen backdrop (so the screen
 * behind the overlay stays crisp), and a smaller one for the `look_at_screen` tool (so the
 * base64 image sent to the model stays cheap).
 */
object AssistScreenHolder {

    // Backdrop: keep close to native resolution so the screen doesn't look downscaled.
    private const val DISPLAY_MAX_DIMEN = 2560
    private const val DISPLAY_QUALITY = 92
    // Tool image: a vision model doesn't need full resolution; keep it small/cheap.
    private const val TOOL_MAX_DIMEN = 1280
    private const val TOOL_QUALITY = 80
    // Screens captured more than this long ago are considered stale.
    private const val FRESHNESS_WINDOW_MS = 5 * 60 * 1000L

    @Volatile
    private var displayJpeg: ByteArray? = null

    @Volatile
    private var toolJpeg: ByteArray? = null

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
            displayJpeg = encode(bitmap, DISPLAY_MAX_DIMEN, DISPLAY_QUALITY)
            toolJpeg = encode(bitmap, TOOL_MAX_DIMEN, TOOL_QUALITY)
            capturedAtMillis = System.currentTimeMillis()
        }
    }

    @Synchronized
    fun clear() {
        displayJpeg = null
        toolJpeg = null
        capturedAtMillis = 0L
    }

    fun hasFreshScreenshot(): Boolean {
        val bytes = toolJpeg ?: return false
        return bytes.isNotEmpty() &&
            (System.currentTimeMillis() - capturedAtMillis) <= FRESHNESS_WINDOW_MS
    }

    /** Base64 data URL (`data:image/jpeg;base64,...`) suitable for a vision model image part. */
    fun dataUrlOrNull(): String? {
        val bytes = toolJpeg?.takeIf { it.isNotEmpty() } ?: return null
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Decoded (high-resolution) bitmap for use as the in-overlay backdrop. */
    fun bitmapOrNull(): Bitmap? {
        val bytes = displayJpeg?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
    }

    private fun encode(bitmap: Bitmap, maxDimen: Int, quality: Int): ByteArray {
        val scaled = downscale(bitmap, maxDimen)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (scaled !== bitmap) scaled.recycle()
        return out.toByteArray()
    }

    private fun downscale(bitmap: Bitmap, maxDimen: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = maxOf(w, h)
        if (longest <= maxDimen || longest == 0) return bitmap
        val ratio = maxDimen.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
}
