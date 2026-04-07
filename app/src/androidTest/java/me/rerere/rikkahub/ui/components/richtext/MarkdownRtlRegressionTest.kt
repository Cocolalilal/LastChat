package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkdownRtlRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun arabic_recipe_keeps_tables_and_list_markers_in_rtl_order() {
        val markdown = """
            ## المكونات

            | الصنف | الكمية |
            | --- | --- |
            | طماطم | 3 |
            | كرفس | 1 |

            - ملح حسب الذوق
            - كزبرة مجففة

            1. اغسل الخضروات
            2. قطّع الطماطم
        """.trimIndent()

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalSettings provides Settings()) {
                    MarkdownBlock(content = markdown)
                }
            }
        }

        composeRule.waitForIdle()

        val itemHeader = composeRule
            .onNodeWithText("الصنف", useUnmergedTree = true)
            .fetchSemanticsNode()
        val quantityHeader = composeRule
            .onNodeWithText("الكمية", useUnmergedTree = true)
            .fetchSemanticsNode()
        val saltItem = composeRule
            .onNodeWithText("ملح حسب الذوق", useUnmergedTree = true)
            .fetchSemanticsNode()
        val bullet = composeRule
            .onAllNodesWithText("\u2022", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()
        val firstStep = composeRule
            .onNodeWithText("اغسل الخضروات", useUnmergedTree = true)
            .fetchSemanticsNode()
        val numberMarker = composeRule
            .onAllNodesWithText("1.", substring = true, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()

        assertTrue(itemHeader.boundsInRoot.left > quantityHeader.boundsInRoot.left)
        assertTrue(bullet.boundsInRoot.left > saltItem.boundsInRoot.left)
        assertTrue(numberMarker.boundsInRoot.left > firstStep.boundsInRoot.left)
    }
}
