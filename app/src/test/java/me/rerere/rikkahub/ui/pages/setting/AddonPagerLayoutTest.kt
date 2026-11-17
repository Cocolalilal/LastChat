package me.rerere.rikkahub.ui.pages.setting

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.QuickSettingsCache
import me.rerere.rikkahub.data.datastore.SecretKeyManager
import me.rerere.rikkahub.data.datastore.SecureStore
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.AdditionalContext
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.nav.OneUITopAppBar
import me.rerere.rikkahub.ui.components.ui.ItemPosition
import me.rerere.rikkahub.ui.components.ui.rememberAppToasterState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.setting.components.AddonListItem
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.SeafoamMintThemeId
import me.rerere.rikkahub.ui.theme.Shapes
import me.rerere.rikkahub.ui.theme.Typography
import me.rerere.rikkahub.ui.theme.findPresetTheme
import me.rerere.rikkahub.ui.theme.withLastChatAmoledSurface
import me.rerere.rikkahub.utils.plus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Exercise the actual pager/header combination: isolated content renders missed short-list centering. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class AddonPagerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val appScope = AppScope()
    private var dark by mutableStateOf(false)
    private var contexts by mutableStateOf(emptyList<AdditionalContext>())
    private lateinit var pagerState: PagerState
    private lateinit var compositionScope: CoroutineScope
    private var expectedFirstRowTop = 0f
    private var contentTop = 0f
    private var contentBottomPadding = 0f

    @Before fun setUp() {
        val context = compose.activity.applicationContext
        val store = SettingsStore(context, appScope, QuickSettingsCache(context), SecretKeyManager(SecureStore(context)),
            StandardTestDispatcher())
        startKoin { modules(module { single { store } }) }
    }

    @After fun tearDown() {
        appScope.cancel()
        stopKoin()
    }

    private fun content(initialContexts: List<AdditionalContext>) {
        contexts = initialContexts
        compose.setContent {
            CompositionLocalProvider(
                LocalSettings provides Settings(additionalContexts = contexts),
                LocalDarkMode provides dark,
                LocalNavController provides rememberNavController(),
                LocalToaster provides rememberAppToasterState(),
            ) {
                MaterialTheme(
                    colorScheme = findPresetTheme(SeafoamMintThemeId).getColorScheme(dark).withLastChatAmoledSurface(dark),
                    typography = Typography, shapes = Shapes,
                ) {
                    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                    pagerState = rememberPagerState(initialPage = 1) { 3 }
                    compositionScope = rememberCoroutineScope()
                    Scaffold(
                        topBar = {
                            OneUITopAppBar(
                                title = when (pagerState.currentPage) {
                                    0 -> "Skills"
                                    1 -> "Additional context"
                                    else -> "Lorebooks"
                                },
                                scrollBehavior = scrollBehavior,
                                navigationIcon = { BackButton() },
                            )
                        },
                    ) { padding ->
                        val density = LocalDensity.current
                        SideEffect {
                            contentTop = with(density) { padding.calculateTopPadding().toPx() }
                            contentBottomPadding = with(density) { padding.calculateBottomPadding().toPx() }
                            expectedFirstRowTop = contentTop + with(density) { 16.dp.toPx() }
                        }
                        Box(Modifier.fillMaxSize().testTag("pagerViewport")) {
                            AddonPager(state = pagerState, useWideLayout = false) { page ->
                                if (page == 1) {
                                    AdditionalContextContent(
                                        settings = Settings(additionalContexts = contexts), onEdit = {}, contentPadding = padding,
                                    )
                                } else {
                                    // Same list density and scaffold padding as neighboring add-on lists.
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                                        contentPadding = padding + PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        item {
                                            AddonListItem(
                                                title = "Neighbor add-on $page", subtitle = "A neighboring add-on list item",
                                                position = ItemPosition.ONLY, leading = { Box(Modifier.size(40.dp)) },
                                                onClick = {},
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun firstRowTop(title: String): Float =
        compose.onNode(hasText(title) and hasClickAction()).fetchSemanticsNode().boundsInRoot.top

    private fun page(index: Int) {
        compose.runOnIdle { compositionScope.launch { pagerState.scrollToPage(index) } }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        System.getProperty("lastchat.renderDir")?.takeIf { it.isNotBlank() }?.let { directory ->
            compose.runOnIdle {
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val file = File(directory, "addon-layout-$name-${if (dark) "dark" else "light"}.png")
                file.parentFile?.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }

    @Test fun shortContextListStartsAtTheSameTopInsetAsItsNeighborInBothThemes() {
        val snippet = AdditionalContext(name = "Writing style", prompt = "Use short paragraphs and natural dialogue.")
        content(listOf(snippet))
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            compose.waitForIdle()
            val contextTop = firstRowTop(snippet.name)
            assertEquals("A short context list must not be centered inside the pager", expectedFirstRowTop, contextTop, 1f)
            capture("short-context")
            page(0)
            assertEquals("Switching tabs must preserve the first-row alignment", contextTop, firstRowTop("Neighbor add-on 0"), 1f)
            page(1)
            assertEquals(expectedFirstRowTop, firstRowTop(snippet.name), 1f)
        }
    }

    @Test fun emptyAndPopulatedContextListsRespectTheSameScaffoldContentArea() {
        content(emptyList())
        for (theme in listOf(false, true)) {
            compose.runOnIdle { dark = theme }
            compose.waitForIdle()
            val empty = compose.onNodeWithText("No additional context").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("pagerViewport").fetchSemanticsNode().boundsInRoot
            assertTrue("Empty state must stay below the expanded header", empty.top >= contentTop)
            assertTrue("Empty state must stay inside the content area", empty.bottom <= viewport.bottom - contentBottomPadding)
            capture("empty-context")
        }
        val populated = (1..24).map { index ->
            AdditionalContext(name = "Context $index", prompt = "A long context preview that wraps naturally across lines. ".repeat(4))
        }
        compose.runOnIdle { contexts = populated; dark = false }
        compose.waitForIdle()
        assertEquals(expectedFirstRowTop, firstRowTop(populated.first().name), 1f)
        capture("populated-context")
        compose.runOnIdle { dark = true }
        capture("populated-context")
        page(2)
        assertEquals(expectedFirstRowTop, firstRowTop("Neighbor add-on 2"), 1f)
    }
}
