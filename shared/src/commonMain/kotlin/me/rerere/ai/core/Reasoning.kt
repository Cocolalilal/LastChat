package me.rerere.ai.core

import kotlin.math.abs

enum class ReasoningLevel(
    val budgetTokens: Int,
    val effort: String
) {
    OFF(0, "minimal"),
    AUTO(-1, "auto"),
    LOW(1024, "low"),
    MEDIUM(16_000, "medium"),
    HIGH(32_000, "high");

    val isEnabled: Boolean
        get() = this != OFF

    companion object {
        fun fromBudgetTokens(budgetTokens: Int?): ReasoningLevel {
            return entries.minByOrNull { abs(it.budgetTokens - (budgetTokens ?: AUTO.budgetTokens)) } ?: AUTO
        }
    }
}

