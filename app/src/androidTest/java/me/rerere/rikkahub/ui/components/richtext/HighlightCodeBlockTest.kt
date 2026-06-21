package me.rerere.rikkahub.ui.components.richtext

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import me.rerere.highlight.Highlighter
import me.rerere.highlight.HighlightToken
import me.rerere.highlight.LocalHighlighter
import me.rerere.highlight.android.AndroidHighlighter
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.Rule
import org.junit.Test

class HighlightCodeBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun streamingPreviewUsesCollapsedHeightAndExpandAffordance() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val highlighter = AndroidHighlighter(context)
        val expandLabel = context.getString(R.string.code_block_expand)
        val longCode = (1..40).joinToString("\n") { "println($it)" }

        composeRule.setContent {
            CompositionLocalProvider(
                LocalSettings provides Settings(),
                LocalHighlighter provides highlighter
            ) {
                MaterialTheme {
                    HighlightCodeBlock(
                        code = longCode,
                        language = "kotlin",
                        completeCodeBlock = false
                    )
                }
            }
        }

        composeRule.onNodeWithText(expandLabel).assertExists()
        composeRule.onNodeWithTag(CODE_BLOCK_FOOTER_TAG)
            .assert(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    CodeBlockFooterAction.Expand.name
                )
            )
        composeRule.onNodeWithTag(CODE_BLOCK_BODY_TAG).assertHeightIsEqualTo(108.dp)
    }

    @Test
    fun expandedStreamingCodeBlockRequestsParentFollowWhenContentGrows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val highlighter = AndroidHighlighter(context)
        val collapseLabel = context.getString(R.string.code_block_collapse)
        var code by mutableStateOf("println(1)")
        var followRequests = 0

        composeRule.setContent {
            CompositionLocalProvider(
                LocalSettings provides Settings(),
                LocalHighlighter provides highlighter
            ) {
                MaterialTheme {
                    HighlightCodeBlock(
                        code = code,
                        language = "kotlin",
                        completeCodeBlock = false,
                        onExpandedStreamingContentChanged = { followRequests++ }
                    )
                }
            }
        }

        composeRule.onNodeWithTag(CODE_BLOCK_FOOTER_TAG).performClick()
        composeRule.onNodeWithText(collapseLabel).assertExists()
        composeRule.onNodeWithTag(CODE_BLOCK_FOOTER_TAG)
            .assert(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    CodeBlockFooterAction.Collapse.name
                )
            )

        composeRule.runOnUiThread {
            code = (1..50).joinToString("\n") { "println($it)" }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            followRequests > 0
        }
    }

    @Test
    fun highlighterFailureFallsBackToPlainText() {
        val throwingHighlighter = object : Highlighter {
            override suspend fun highlight(code: String, language: String): List<HighlightToken> {
                throw IllegalArgumentException("boom")
            }
        }

        composeRule.setContent {
            CompositionLocalProvider(
                LocalSettings provides Settings(),
                LocalHighlighter provides throwingHighlighter
            ) {
                MaterialTheme {
                    HighlightCodeBlock(
                        code = "echo hello",
                        language = "ts title=\"demo\"",
                        completeCodeBlock = true
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("echo hello").assertExists()
    }
}
