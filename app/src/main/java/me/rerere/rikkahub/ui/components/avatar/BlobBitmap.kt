package me.rerere.rikkahub.ui.components.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import me.rerere.rikkahub.data.model.Avatar

/**
 * Deterministic still render of a blob avatar to an [android.graphics.Bitmap].
 * Used where there's no Compose clock: assistant export/import PNGs, home-screen
 * widgets, launcher shortcuts. Renders one frozen nominal pose (no drift/blink).
 */
object BlobBitmap {
    fun render(
        spec: Avatar.Blob,
        size: Int,
        lifecycle: BlobLifecycle = BlobLifecycle.Idle,
        atSeconds: Float = 0f,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val runtime = BlobRuntime(lifecycle)
        val frame = runtime.sample(atSeconds, spec, lifecycle, motion = 0f)
        AvatarDraw.draw(canvas, frame, size.toFloat(), size.toFloat())
        return bitmap
    }
}
