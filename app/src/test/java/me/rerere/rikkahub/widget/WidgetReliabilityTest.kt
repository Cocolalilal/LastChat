package me.rerere.rikkahub.widget

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.datastore.preferences.core.preferencesOf
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.utils.JsonInstant
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
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

    @Test fun loadingLayoutUsesTransparentCurrentGenericalStill() {
        val view = RemoteViews(context.packageName, R.layout.widget_assistant_loading)
            .apply(context, FrameLayout(context))
        val image = view.findViewById<ImageView>(R.id.assistant_widget_avatar)
        assertEquals(null, image.background)
        val actual = (image.drawable as BitmapDrawable).bitmap
        assertEquals(0, Color.alpha(actual.getPixel(0, 0)))
        val expected = requireNotNull(WidgetAvatarRenderer.render(
            context, Avatar.Animated.defaultGenerical(), seed = 0, sizePx = 256,
            variant = 0, presetId = "calm",
        ))
        assertTrue("Initial layout must use the actual current renderer", actual.sameAs(expected))
    }

    @Test fun compositionRecoveryKeepsExactConfiguredExpressionWithoutPurpleBackdrop() {
        val data = JsonInstant.encodeToString(Avatar.Animated.serializer(), Avatar.Animated.defaultGenerical())
        WidgetPrefs.saveConfig(context, 472, Uuid.random().toString(), "Generical", "animated", data,
            seed = 29, variant = 4, presetId = "curious")
        val expected = requireNotNull(WidgetAvatarRenderer.render(
            context, Avatar.Animated.defaultGenerical(), seed = 29, sizePx = 256, variant = 4, presetId = "curious",
        ))
        repeat(2) { // New RemoteViews instances model fresh sessions/process recovery.
            val view = widgetFallbackViews(context, 472).apply(context, FrameLayout(context))
            val image = view.findViewById<ImageView>(R.id.assistant_widget_avatar)
            assertEquals(null, image.background)
            assertTrue(expected.sameAs((image.drawable as BitmapDrawable).bitmap))
        }
    }

    @Test fun committedConfigurationWinsOverEntireStaleGlanceSnapshot() {
        val currentId = Uuid.random().toString()
        WidgetPrefs.saveConfig(context, 473, currentId, "Mira", "emoji", "🌸",
            seed = 17, variant = 8, presetId = "happy")
        val stale = preferencesOf(
            AssistantWidget.ASSISTANT_ID_KEY to DEFAULT_ASSISTANT_ID.toString(),
            AssistantWidget.ASSISTANT_NAME_KEY to "Generical",
            AssistantWidget.AVATAR_TYPE_KEY to "resource",
            AssistantWidget.AVATAR_DATA_KEY to R.drawable.default_generical_pfp.toString(),
            AssistantWidget.AVATAR_PRESET_KEY to "calm",
            AssistantWidget.AVATAR_SEED_KEY to 3,
            AssistantWidget.AVATAR_VARIANT_KEY to 2,
        )
        assertEquals(WidgetConfig(currentId, "Mira", "emoji", "🌸", 17, 8, "happy"),
            resolveWidgetConfig(context, 473, stale))
        val legacy = resolveWidgetConfig(context, 474, stale)
        assertEquals("animated", legacy.avatarType)
        assertEquals("calm", legacy.presetId)
        assertEquals(3, legacy.seed)
        assertEquals(2, legacy.variant)
        assertEquals(Avatar.Animated.defaultGenerical(),
            JsonInstant.decodeFromString(Avatar.Animated.serializer(), legacy.avatarData))
    }

    @Test fun legacyStockMigrationPreservesCustomAvatarsAndWidgetExpression() {
        WidgetPrefs.saveConfig(context, 475, DEFAULT_ASSISTANT_ID.toString(), "Generical", "resource",
            R.drawable.default_generical_pfp.toString(), seed = 14, variant = 6, presetId = "cute")
        val migrated = requireNotNull(WidgetPrefs.getConfig(context, 475))
        assertEquals("animated", migrated.avatarType)
        assertEquals(14, migrated.seed)
        assertEquals(6, migrated.variant)
        assertEquals("cute", migrated.presetId)
        val custom = migrated.copy(avatarType = "image", avatarData = "file:///custom.png")
        assertEquals(custom, custom.migrateStockAvatar())
        val another = migrated.copy(assistantId = Uuid.random().toString(), avatarType = "resource",
            avatarData = R.drawable.default_generical_pfp.toString())
        assertEquals(another, another.migrateStockAvatar())
        assertEquals("animated", another.copy(assistantId = DEFAULT_ASSISTANT_ID.toString(),
            avatarData = 0x7f080099.toString()).migrateStockAvatar().avatarType)
        for (version in listOf(0, 1)) {
            val stock = Avatar.Animated(colorHex = Avatar.Animated.GENERICAL_BLUE_HEX,
                colorPreset = Avatar.Animated.GENERICAL_BLUE_PRESET, stockColorVersion = version)
            val old = migrated.copy(avatarData = JsonInstant.encodeToString(Avatar.Animated.serializer(), stock))
            assertEquals(migrated.avatarData, old.migrateStockAvatar().avatarData)
        }
    }

    @Test fun lastGoodPhotoSurvivesRefreshAndDoesNotLeakAcrossReconfigurationOrDeletion() {
        val id = Uuid.random().toString()
        WidgetPrefs.saveConfig(context, 476, id, "Mira", "image", "https://example.test/photo.png", seed = 1)
        val config = requireNotNull(WidgetPrefs.getConfig(context, 476))
        val photo = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        WidgetAvatarCache.write(context, 476, config, photo)
        WidgetPrefs.advanceVariant(context, 476)
        val view = widgetFallbackViews(context, 476).apply(context, FrameLayout(context))
        assertTrue(photo.sameAs((view.findViewById<ImageView>(R.id.assistant_widget_avatar).drawable as BitmapDrawable).bitmap))
        WidgetPrefs.saveConfig(context, 476, id, "Mira", "image", "https://example.test/new.png", seed = 1)
        assertEquals(null, WidgetAvatarCache.read(context, 476, requireNotNull(WidgetPrefs.getConfig(context, 476))))
        // An older in-flight download cannot overwrite the new configuration.
        WidgetAvatarCache.delete(context, 476)
        WidgetAvatarCache.write(context, 476, config, photo)
        assertEquals(null, WidgetAvatarCache.read(context, 476, config))
        val current = requireNotNull(WidgetPrefs.getConfig(context, 476))
        WidgetAvatarCache.write(context, 476, current, photo)
        WidgetPrefs.deleteConfig(context, 476)
        assertEquals(null, WidgetAvatarCache.read(context, 476, current))
        WidgetAvatarCache.write(context, 476, current, photo)
        assertEquals(null, WidgetAvatarCache.read(context, 476, current))
    }

    @Test fun activeSessionObservesCanonicalConfigChangesWithoutGlanceStateMutation() = runTest {
        val id = Uuid.random().toString()
        WidgetPrefs.saveConfig(context, 477, id, "Mira", "emoji", "🌸", seed = 1)
        val snapshots = mutableListOf<WidgetConfig?>()
        val observation = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            WidgetPrefs.configFlow(context, 477).take(2).toList(snapshots)
        }
        testScheduler.runCurrent()
        WidgetPrefs.saveConfig(context, 477, id, "Mira", "emoji", "🌙", seed = 21, presetId = "cute")
        shadowOf(android.os.Looper.getMainLooper()).idle()
        testScheduler.runCurrent()
        assertEquals(2, snapshots.size)
        assertEquals("🌸", snapshots[0]?.avatarData)
        assertEquals(WidgetPrefs.getConfig(context, 477), snapshots[1])
        assertEquals(21, snapshots[1]?.seed)
        observation.cancel()
    }

    @Test fun corruptPhotoCacheFallsBackToConfiguredCharacterWithoutCrashing() {
        WidgetPrefs.saveConfig(context, 478, Uuid.random().toString(), "Mira", "image", "https://example.test/photo.png")
        val config = requireNotNull(WidgetPrefs.getConfig(context, 478))
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        WidgetAvatarCache.write(context, 478, config, bitmap)
        java.io.File(context.filesDir, "widget-avatars").listFiles()!!
            .single { it.name.startsWith("478_") }.writeText("incomplete image")
        assertEquals(null, WidgetAvatarCache.read(context, 478, config))
        val view = widgetFallbackViews(context, 478).apply(context, FrameLayout(context))
        val expected = AssistantWidget().createLocalAvatarBitmap(context, "dummy", "", "Mira")
        assertTrue(requireNotNull(expected).sameAs(
            (view.findViewById<ImageView>(R.id.assistant_widget_avatar).drawable as BitmapDrawable).bitmap))
    }

}
