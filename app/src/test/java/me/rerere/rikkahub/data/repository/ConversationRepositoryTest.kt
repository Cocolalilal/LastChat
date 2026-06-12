package me.rerere.rikkahub.data.repository

import me.rerere.rikkahub.data.model.Conversation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class ConversationRepositoryTest {
    @Test
    fun shouldInvalidateConsolidationInvalidatesNormalConsolidatedUpdates() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
        ).copy(isConsolidated = true)

        assertTrue(
            shouldInvalidateConsolidation(
                conversation = conversation,
                preserveConsolidation = false,
            )
        )
    }

    @Test
    fun shouldInvalidateConsolidationPreservesExplicitMetadataUpdates() {
        val conversation = Conversation.ofId(
            id = Uuid.random(),
        ).copy(isConsolidated = true)

        assertFalse(
            shouldInvalidateConsolidation(
                conversation = conversation,
                preserveConsolidation = true,
            )
        )
    }
}
