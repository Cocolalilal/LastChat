package me.rerere.rikkahub.ui.components.nav

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class OneUITopAppBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val title = "A very long character and lorebook title that must remain inside the app bar at larger font sizes"
    private lateinit var scrollBehavior: TopAppBarScrollBehavior

    private fun content() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                MaterialTheme(typography = Typography) {
                    scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                    OneUITopAppBar(
                        title = title,
                        scrollBehavior = scrollBehavior,
                        modifier = Modifier.testTag("header"),
                        navigationIcon = { Box(Modifier.size(48.dp).testTag("navigation")) },
                        actions = { Box(Modifier.size(96.dp, 48.dp).testTag("actions")) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun titleLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }

    @Test fun collapsedLongTitleEllipsizesBetweenNavigationAndActionsAtLargeFont() {
        content()
        compose.runOnIdle { scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit }
        compose.waitForIdle()
        val layout = titleLayout()
        assertEquals(1, layout.lineCount)
        assertTrue(layout.isLineEllipsized(0))
        val bounds = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("actions").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= navigation.right)
        assertTrue(bounds.right <= actions.left)
    }

    @Test fun expandedLongTitleUsesAtMostTwoLinesInsideItsHeaderAtLargeFont() {
        content()
        val layout = titleLayout()
        assertEquals(2, layout.lineCount)
        assertTrue(layout.isLineEllipsized(1))
        val bounds = compose.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val header = compose.onNodeWithTag("header").fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= navigation.bottom)
        assertTrue(bounds.bottom <= header.bottom)
    }
}
