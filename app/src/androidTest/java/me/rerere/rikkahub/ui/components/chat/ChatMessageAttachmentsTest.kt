package me.rerere.rikkahub.ui.components.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.context.LocalSettings
import org.junit.Rule
import org.junit.Test

class ChatMessageAttachmentsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun userTurn_rendersPdfAttachmentAlongsideText() {
        val group = MessageTurnGroup(
            nodes = listOf(
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(
                            UIMessagePart.Document(
                                url = "file:///tmp/scan.pdf",
                                fileName = "scan.pdf",
                                mime = "application/pdf",
                            ),
                            UIMessagePart.Text("Please review this PDF."),
                        )
                    )
                )
            ),
            role = MessageRole.USER,
        )

        setContent(group = group)

        composeRule.onNodeWithText("scan.pdf").assertExists()
    }

    @Test
    fun assistantTurn_rendersAttachments() {
        val group = MessageTurnGroup(
            nodes = listOf(
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(
                            UIMessagePart.Image(url = "file:///tmp/image.png"),
                            UIMessagePart.Document(
                                url = "file:///tmp/spec.pdf",
                                fileName = "spec.pdf",
                                mime = "application/pdf",
                            ),
                            UIMessagePart.Text("Here is the referenced file."),
                        )
                    )
                )
            ),
            role = MessageRole.ASSISTANT,
        )

        setContent(group = group)

        composeRule.onNodeWithText("spec.pdf").assertExists()
    }

    @Test
    fun documentOnlyTurn_stillRendersAttachmentChip() {
        val group = MessageTurnGroup(
            nodes = listOf(
                MessageNode.of(
                    UIMessage(
                        role = MessageRole.USER,
                        parts = listOf(
                            UIMessagePart.Document(
                                url = "file:///tmp/brief.pdf",
                                fileName = "brief.pdf",
                                mime = "application/pdf",
                            )
                        )
                    )
                )
            ),
            role = MessageRole.USER,
        )

        setContent(group = group)

        composeRule.onNodeWithText("brief.pdf").assertExists()
    }

    private fun setContent(group: MessageTurnGroup) {
        composeRule.setContent {
            CompositionLocalProvider(LocalSettings provides Settings()) {
                MaterialTheme {
                    ChatMessageTurn(
                        group = group,
                        isLastTurn = false,
                        onCitationClick = {},
                        loading = false,
                        showRegenerate = false,
                    )
                }
            }
        }
    }
}
