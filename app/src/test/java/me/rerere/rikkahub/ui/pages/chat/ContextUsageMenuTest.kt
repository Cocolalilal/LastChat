package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.chrisbanes.haze.rememberHazeState
import me.rerere.ai.context.ContextMapCategory
import me.rerere.ai.context.ContextUsageBreakdown
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.ui.hooks.PremiumHaptics
import me.rerere.rikkahub.ui.modifier.LastChatBlur
import me.rerere.rikkahub.ui.modifier.LocalLastChatBlur
import me.rerere.rikkahub.ui.modifier.lastChatBlurSource
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.motion.MotionPolicy
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.SeafoamMintThemeId
import me.rerere.rikkahub.ui.theme.Shapes
import me.rerere.rikkahub.ui.theme.Typography
import me.rerere.rikkahub.ui.theme.findPresetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class ContextUsageMenuTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val noHaptics = PremiumHaptics(object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
    }, null, false)

    private fun frames() {
        compose.runOnIdle { Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(1_200)
        compose.waitForIdle()
    }

    @Test fun topMenuRetainsBreakdownAndLimitEditor() = exerciseMenu(ChatToolbarPlacement.Top, true)
    @Test fun bottomMenuRetainsBreakdownAndLimitEditor() = exerciseMenu(ChatToolbarPlacement.Bottom, false)

    @Test
    fun usageMenuDisplaysNewBucketsAndOpensFullContextMap() {
        val usage = ContextUsageBreakdown(
            conversationTokens = 2_000,
            systemPromptTokens = 400,
            customInstructionsTokens = 300,
            memorySummaryTokens = 500,
            featurePromptsTokens = 600,
            totalTokens = 32_000,
            usableInputTokens = 28_000,
        )
        val openedFocus = mutableListOf<ContextMapCategory?>()
        compose.setContent {
            var open by remember { mutableStateOf(true) }
            val haze = rememberHazeState()
            MaterialTheme(
                colorScheme = findPresetTheme(SeafoamMintThemeId).getColorScheme(true),
                shapes = Shapes,
                typography = Typography,
            ) {
                CompositionLocalProvider(
                    LocalLastChatBlur provides LastChatBlur(false, haze),
                    LocalMotionPolicy provides MotionPolicy(reduceMotion = true),
                    LocalDarkMode provides true,
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ContextUsageMenu(
                            visible = open,
                            usage = usage,
                            model = Model(modelId = "chat", contextWindowTokens = 32_000),
                            placement = ChatToolbarPlacement.Top,
                            onUpdateModelLimit = {},
                            onOpenContextMap = { focus -> openedFocus += focus },
                            onDismissRequest = { open = false },
                            haptics = noHaptics,
                        )
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        frames()
        compose.onNodeWithText("Instructions 300").assertIsDisplayed()
        compose.onNodeWithText("Memory profile 500").assertIsDisplayed()
        compose.onNodeWithText("Features 600").assertIsDisplayed()
        compose.onNodeWithText("Full context map").performClick()
        frames()
        assertEquals(listOf<ContextMapCategory?>(null), openedFocus)
    }

    private fun exerciseMenu(placement: ChatToolbarPlacement, dark: Boolean) {
        val usage = ContextUsageBreakdown(conversationTokens = 4_000, systemPromptTokens = 800,
            summaryTokens = 200, totalTokens = 32_000, usableInputTokens = 28_000)
        val limits = mutableListOf<Int?>()
        compose.setContent {
            var open by remember { mutableStateOf(false) }
            val haze = rememberHazeState()
            MaterialTheme(colorScheme = findPresetTheme(SeafoamMintThemeId).getColorScheme(dark),
                shapes = Shapes, typography = Typography) {
                CompositionLocalProvider(LocalLastChatBlur provides LastChatBlur(true, haze),
                    LocalMotionPolicy provides MotionPolicy(reduceMotion = false), LocalDarkMode provides dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        Box(Modifier.fillMaxSize().lastChatBlurSource()) {
                            Button(onClick = { open = true }) { Text("Usage") }
                        }
                        ContextUsageMenu(open, usage, Model(modelId = "chat", contextWindowTokens = 32_000), placement,
                            onUpdateModelLimit = { limits += it }, onDismissRequest = { open = false }, haptics = noHaptics)
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Usage").performClick()
        frames()
        compose.onNodeWithText("5.0K of 32.0K tokens").assertIsDisplayed()
        compose.onNodeWithText("Conversation 4.0K").assertIsDisplayed()
        compose.onNodeWithText("System prompt 800").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit context limit").assertIsDisplayed()
        capture("usage-${if (dark) "dark" else "light"}-${placement.name.lowercase()}")
        compose.onNodeWithContentDescription("Edit context limit").performClick()
        frames()
        compose.onNodeWithText("Context limit: 32.0K").assertIsDisplayed()
        compose.onNodeWithContentDescription("Commit").performClick()
        frames()
        assertEquals(listOf<Int?>(null), limits)
        compose.onAllNodesWithText("Summarize").assertCountEquals(0)
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        frames()
        compose.onNodeWithContentDescription("Edit context limit").assertDoesNotExist()
        compose.onNodeWithText("Usage").performClick()
        frames()
        compose.onNodeWithContentDescription("Edit context limit").assertIsDisplayed()
    }

    private fun capture(name: String) {
        val directory = System.getProperty("lastchat.renderDir")?.let(::File) ?: return
        directory.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
