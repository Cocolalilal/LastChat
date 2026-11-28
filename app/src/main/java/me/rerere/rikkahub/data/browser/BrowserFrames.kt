package me.rerere.rikkahub.data.browser

import android.graphics.Bitmap
import android.util.LruCache

/**
 * In-memory LRU frame cache for browser tool calls and live snapshots.
 * Dynamically resized from `BrowserSettings.frameCacheCapMb` (default 8 MB).
 */
object BrowserFrames {
    private const val DEFAULT_CAP_MB = 8
    private const val MIN_CAP_MB = 4
    private const val MAX_CAP_MB = 24
    private const val PREVIEW_MAX_WIDTH_PX = 360

    @Volatile
    private var currentCapMb = DEFAULT_CAP_MB

    private val lock = Any()
    private val cache: LruCache<String, Bitmap> = createCache(DEFAULT_CAP_MB)

    private fun createCache(capMb: Int): LruCache<String, Bitmap> {
        val maxKb = capMb.coerceIn(MIN_CAP_MB, MAX_CAP_MB) * 1024
        return object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    fun updateCapacityMb(capMb: Int) {
        val clamped = capMb.coerceIn(MIN_CAP_MB, MAX_CAP_MB)
        synchronized(lock) {
            if (clamped == currentCapMb) return
            currentCapMb = clamped
            cache.resize(clamped * 1024)
        }
    }

    fun remember(blockId: String, frame: Bitmap) {
        if (blockId.isBlank() || frame.isRecycled) return
        val scaled = downscaleForCache(frame)
        synchronized(lock) {
            cache.put(blockId, scaled)
        }
    }

    fun get(blockId: String): Bitmap? {
        if (blockId.isBlank()) return null
        return synchronized(lock) {
            cache.get(blockId)?.takeIf { !it.isRecycled }
        }
    }

    fun clear() {
        synchronized(lock) {
            cache.evictAll()
        }
    }

    private fun downscaleForCache(source: Bitmap): Bitmap {
        if (source.width <= PREVIEW_MAX_WIDTH_PX || source.width <= 0 || source.height <= 0) {
            return source
        }
        val ratio = PREVIEW_MAX_WIDTH_PX.toFloat() / source.width.toFloat()
        val targetW = PREVIEW_MAX_WIDTH_PX
        val targetH = (source.height * ratio).toInt().coerceAtLeast(1)
        return runCatching {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        }.getOrDefault(source)
    }
}
