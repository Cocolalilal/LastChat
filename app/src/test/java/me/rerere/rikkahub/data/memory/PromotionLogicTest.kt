package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemSensitivity
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.model.MemoryFactCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-logic tests for the scope-promotion whitelist (§6.5). The one rule that must never leak a
 * secret: only ACTIVE, NORMAL user facts on the closed identity whitelist auto-promote; SENSITIVE
 * never promotes or chips; everything else is an opt-in suggestion.
 */
class PromotionLogicTest {

    private fun classify(
        scope: Int = MemScope.CHARACTER,
        status: Int = MemStatus.ACTIVE,
        sensitivity: Int = MemSensitivity.NORMAL,
        type: Int = MemNodeType.FACT,
        aboutUser: Boolean = true,
        category: String? = MemoryFactCategory.OCCUPATION_STUDY,
    ) = PromotionLogic.classify(scope, status, sensitivity, type, aboutUser, category)

    @Test
    fun identityCategoriesAutoPromote() {
        for (cat in MemoryFactCategory.PROMOTION_WHITELIST) {
            assertEquals("category $cat should auto-promote", PromotionLogic.Action.AUTO, classify(category = cat))
        }
    }

    @Test
    fun whitelistIsCaseInsensitive() {
        assertEquals(PromotionLogic.Action.AUTO, classify(category = "Name"))
        assertEquals(PromotionLogic.Action.AUTO, classify(category = "OCCUPATION_STUDY"))
    }

    @Test
    fun nonWhitelistUserFactsBecomeSuggestions() {
        assertEquals(PromotionLogic.Action.SUGGEST, classify(category = MemoryFactCategory.OTHER))
        assertEquals(PromotionLogic.Action.SUGGEST, classify(category = null))
        assertEquals(PromotionLogic.Action.SUGGEST, classify(category = "favourite_food"))
    }

    @Test
    fun sensitiveNeverPromotesAndNeverChips() {
        // Even a whitelisted category is skipped entirely when marked sensitive.
        assertEquals(PromotionLogic.Action.SKIP, classify(sensitivity = MemSensitivity.SENSITIVE, category = MemoryFactCategory.NAME))
        assertEquals(PromotionLogic.Action.SKIP, classify(sensitivity = MemSensitivity.SENSITIVE, category = MemoryFactCategory.OTHER))
    }

    @Test
    fun onlyLiveCharacterUserFactsAreEligible() {
        assertEquals(PromotionLogic.Action.SKIP, classify(scope = MemScope.GLOBAL_USER)) // already global
        assertEquals(PromotionLogic.Action.SKIP, classify(status = MemStatus.PROVISIONAL))
        assertEquals(PromotionLogic.Action.SKIP, classify(status = MemStatus.DORMANT))
        assertEquals(PromotionLogic.Action.SKIP, classify(type = MemNodeType.EPISODE))
        assertEquals(PromotionLogic.Action.SKIP, classify(aboutUser = false))
    }
}
