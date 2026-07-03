package me.rerere.rikkahub.data.memory

/**
 * Pure decision for branch/regenerate demotion (§7.3).
 *
 * Distinguishes an abandoned branch (the classic "this content was wrong" signal — a regenerate or
 * version switch) from a plain message deletion (ambiguous cleanup). In the [MessageNode] model an
 * abandoned branch's messages still exist as *unselected* versions, whereas a deleted message is
 * gone from the conversation entirely — that structural difference is the signal.
 *
 * Rule: a node is demoted to DORMANT only when **all** of its provenance lies beyond the divergence
 * point (none of its evidence survives on the current branch, and none comes from another
 * conversation) **and** at least one piece of that evidence is on an abandoned branch (still exists,
 * just unselected). A fact also evidenced before the divergence, on the surviving branch, or in
 * another conversation keeps its status; a fact whose only evidence was plain-deleted is kept too
 * (diary-burned principle) — deletion never demotes.
 */
object MemoryBranchLogic {

    data class ProvRow(val conversationId: String?, val messageIds: List<String>)

    fun shouldDemote(
        rows: List<ProvRow>,
        conversationId: String,
        survivingMessageIds: Set<String>,
        allExistingMessageIds: Set<String>,
    ): Boolean {
        if (rows.isEmpty()) return false

        // Any surviving evidence — on the current branch, or from another conversation — keeps it.
        val hasSurvivingEvidence = rows.any { row ->
            row.conversationId != conversationId || row.messageIds.any { it in survivingMessageIds }
        }
        if (hasSurvivingEvidence) return false

        // No surviving evidence. Demote only if some evidence is on an abandoned branch (a message
        // that still exists but is not selected). If every non-surviving message is simply gone
        // (plain deletion), keep the node.
        return rows.any { row ->
            row.conversationId == conversationId && row.messageIds.any { it in allExistingMessageIds }
        }
    }
}
