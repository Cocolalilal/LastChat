package me.rerere.rikkahub.phoneuse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect

/** Keep image-grid coordinates aligned with accessibility screen coordinates, including insets. */
internal fun alignWindowScreenshot(window: Bitmap, bounds: Rect, width: Int, height: Int): Bitmap =
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { result ->
        Canvas(result).apply {
            drawColor(Color.BLACK)
            drawBitmap(window, null, bounds, null)
        }
    }
