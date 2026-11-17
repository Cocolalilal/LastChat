package me.rerere.rikkahub.widget

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import kotlin.uuid.Uuid
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.common.platform.PlatformHttpResponse
import me.rerere.common.platform.PlatformServerEvent
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.activity.ShortcutHandlerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser

/** Launcher-safe native views must exist before Glance's asynchronous work has run. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class WidgetReliabilityTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun providerHasAnInflatableInitialRemoteViewsBeforeGlanceIsReady() {
        context.resources.getXml(R.xml.widget_assistant_info).use { xml ->
            // A fresh binary-resource parser can emit START_DOCUMENT on its first next().
            // Advance to the provider element before asking for its resource attributes.
            while (xml.eventType != XmlPullParser.START_TAG && xml.eventType != XmlPullParser.END_DOCUMENT) {
                xml.next()
            }
            assertEquals(XmlPullParser.START_TAG, xml.eventType)
            assertEquals("appwidget-provider", xml.name)
            val layout = xml.getAttributeResourceValue("http://schemas.android.com/apk/res/android", "initialLayout", 0)
            assertNotEquals("A missing initialLayout makes launchers show their error view during cold starts", 0, layout)
            val view = RemoteViews(context.packageName, layout).apply(context, FrameLayout(context))
            val avatar = view.findViewById<ImageView>(R.id.assistant_widget_avatar)
            assertNotNull(avatar)
            assertNotNull(avatar.drawable)
        }
    }

    @Test fun compositionFallbackStillOpensTheConfiguredCharacter() {
        val id = Uuid.random().toString()
        WidgetPrefs.saveConfig(context, 471, id, "Mira", "dummy", "", seed = 24)
        val view = widgetFallbackViews(context, 471).apply(context, FrameLayout(context))
        assertTrue(view.findViewById<View>(R.id.assistant_widget_avatar).performClick())
        val opened = shadowOf(context).nextStartedActivity
        assertEquals(ShortcutHandlerActivity::class.java.name, opened.component?.className)
        assertEquals("lastchat://assistant/$id", opened.dataString)
        assertEquals(Intent.ACTION_VIEW, opened.action)
    }

    @Test fun missingOrInvalidCharacterConfigurationOpensTheApp() {
        listOf(null, "not-a-character-id").forEach { id ->
            val intent = widgetLaunchIntent(context, id)
            assertEquals("me.rerere.rikkahub.RouteActivity", intent.component?.className)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
    }

    @Test fun aLocalPhotoLoadsWithoutHttpOrKoinAndStaysWithinWidgetBitmapBounds() = runBlocking {
        val source = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        android.graphics.Canvas(source).apply {
            val border = android.graphics.Paint().apply { color = Color.BLUE }
            drawRect(0f, 0f, 512f, 1024f, border)
            drawRect(1536f, 0f, 2048f, 1024f, border)
        }
        val file = java.io.File(context.cacheDir, "widget-photo.png")
        file.outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle()
        try {
            val rendered = AssistantWidget().createAvatarBitmap(context, "image", Uri.fromFile(file).toString(), "Mira")
            assertEquals(256, rendered.width)
            assertEquals(256, rendered.height)
            assertEquals(Color.RED, rendered.getPixel(128, 128))
            assertEquals("Wide photos should crop rather than stretch", Color.RED, rendered.getPixel(32, 128))
            assertEquals(0, Color.alpha(rendered.getPixel(0, 0)))
            rendered.recycle()
        } finally {
            file.delete()
        }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun remoteImageTimeoutFallsBackWhileSessionCancellationStillPropagates() = runTest {
        val client = object : PlatformHttpClient {
            override suspend fun execute(request: PlatformHttpRequest): PlatformHttpResponse = awaitCancellation()
            override fun streamEvents(request: PlatformHttpRequest): Flow<PlatformServerEvent> = emptyFlow()
        }
        startKoin { modules(module { single<PlatformHttpClient> { client } }) }
        try {
            val widget = AssistantWidget()
            val image = widget.createAvatarBitmap(context, "image", "https://example.test/avatar.png", "Mira")
            assertEquals(5_000L, currentTime)
            assertEquals(256, image.width)
            image.recycle()
            val cancelled = runCatching {
                withTimeout(1_000L) {
                    widget.createAvatarBitmap(context, "image", "https://example.test/avatar.png", "Mira")
                }
            }.exceptionOrNull()
            assertTrue(cancelled is TimeoutCancellationException)
        } finally {
            stopKoin()
        }
    }

}
