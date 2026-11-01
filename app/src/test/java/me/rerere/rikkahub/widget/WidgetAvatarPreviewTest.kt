package me.rerere.rikkahub.widget

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.avatar.animated.AvatarPackData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Writes what the character widget actually paints: one frame per (seed, variant) on a widget-like
 * surface, so the result can be looked at instead of only asserted on. Needs -Dlastchat.renderDir.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetAvatarPreviewTest {

    @Test
    fun renderWidgetFrames() {
        val dir = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() } ?: return
        val context = RuntimeEnvironment.getApplication()
        AvatarPackData.ensureLoaded(context)
        File(dir).mkdirs()
        val px = 256
        val avatar = Avatar.Animated.defaultGenerical()
        for (seed in listOf(1, 2, 3)) {
            for (variant in 0 until 7) {
                val bmp = WidgetAvatarRenderer.render(context, avatar, seed, px, variant) ?: continue
                // Compose a widget-ish tile: the mark centred on a surface, like the real widget card.
                val img = ImageBitmap(px + 64, px + 64)
                val canvas = Canvas(img)
                CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, canvas, Size((px + 64).toFloat(), (px + 64).toFloat())) {
                    drawRect(Color(0xFF17181C), size = size)
                    drawImage(
                        image = bmp.asImageBitmap(),
                        topLeft = androidx.compose.ui.geometry.Offset(32f, 32f),
                    )
                }
                File(dir, "widget_seed${seed}_v$variant.png").outputStream()
                    .use { img.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test
    fun renderHandpickedWidgetOptions() {
        val dir = System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() } ?: return
        val context = RuntimeEnvironment.getApplication()
        File(dir).mkdirs()
        for (eyeType in listOf("generical", "grok")) {
            for (preset in WidgetAvatarPreset.entries) {
                val avatar = Avatar.Animated.defaultGenerical().copy(eyeType = eyeType)
                val bitmap = requireNotNull(WidgetAvatarRenderer.render(
                    context, avatar, seed = 0, sizePx = 256, variant = 0, presetId = preset.id,
                ))
                File(dir, "widget_${eyeType}_${preset.id}.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }

}
