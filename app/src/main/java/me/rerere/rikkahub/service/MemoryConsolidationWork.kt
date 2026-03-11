package me.rerere.rikkahub.service

import androidx.work.Data
import androidx.work.workDataOf

const val MEMORY_CONSOLIDATION_WORK_NAME = "memory_consolidation"
const val MEMORY_CONSOLIDATION_KEY_ASSISTANT_ID = "ASSISTANT_ID"
const val MEMORY_CONSOLIDATION_KEY_FULL_SCAN = "FULL_SCAN"
const val MEMORY_CONSOLIDATION_KEY_FORCE_CONVERSATION_ID = "FORCE_CONVERSATION_ID"

internal fun buildMemoryConsolidationInputData(
    assistantId: String,
    isFullScan: Boolean = false,
    forceConversationId: String? = null,
): Data {
    return workDataOf(
        MEMORY_CONSOLIDATION_KEY_ASSISTANT_ID to assistantId,
        MEMORY_CONSOLIDATION_KEY_FULL_SCAN to isFullScan,
        MEMORY_CONSOLIDATION_KEY_FORCE_CONVERSATION_ID to forceConversationId,
    )
}

internal fun resolveConsolidationAssistantId(
    explicitAssistantId: String?,
    fallbackAssistantId: String,
): String {
    return explicitAssistantId?.takeIf { it.isNotBlank() } ?: fallbackAssistantId
}
