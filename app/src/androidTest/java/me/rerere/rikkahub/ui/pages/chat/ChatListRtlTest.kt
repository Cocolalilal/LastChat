package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class ChatListRtlTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chat_preview_keeps_user_bubbles_on_the_right_in_rtl() {
        val userText = "USER RTL anchor"
        val assistantText = "ASSISTANT RTL anchor"
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user(userText)),
                MessageNode.of(UIMessage.assistant(assistantText)),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val state = rememberLazyListState()
                    ChatList(
                        innerPadding = PaddingValues(0.dp),
                        conversation = conversation,
                        state = state,
                        loading = false,
                        previewMode = true,
                        settings = Settings(),
                        onJumpToMessage = {},
                    )
                }
            }
        }

        val userNode = composeRule.onNodeWithText(userText).fetchSemanticsNode()
        val assistantNode = composeRule.onNodeWithText(assistantText).fetchSemanticsNode()

        assertTrue(userNode.boundsInRoot.left > assistantNode.boundsInRoot.left)
    }

    @Test
    fun chat_preview_keeps_arabic_user_bubbles_on_the_right_in_rtl() {
        val userText = "مرحبا من المستخدم"
        val assistantText = "مرحبًا من المساعد"
        val conversation = Conversation.ofId(
            id = Uuid.random(),
            messages = listOf(
                MessageNode.of(UIMessage.user(userText)),
                MessageNode.of(UIMessage.assistant(assistantText)),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val state = rememberLazyListState()
                    ChatList(
                        innerPadding = PaddingValues(0.dp),
                        conversation = conversation,
                        state = state,
                        loading = false,
                        previewMode = true,
                        settings = Settings(),
                        onJumpToMessage = {},
                    )
                }
            }
        }

        val userNode = composeRule.onNodeWithText(userText).fetchSemanticsNode()
        val assistantNode = composeRule.onNodeWithText(assistantText).fetchSemanticsNode()

        assertTrue(userNode.boundsInRoot.left > assistantNode.boundsInRoot.left)
    }
}
