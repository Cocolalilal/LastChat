package me.rerere.rikkahub.service

enum class ContextManagementActivity {
    QUEUED,
    SUMMARIZING,
    WAITING,
    COMPLETE,
    FAILED,
    CANCELLED;

    val isRunning: Boolean get() = this == QUEUED || this == SUMMARIZING || this == WAITING
}

data class ContextCompressionStatus(
    val activity: ContextManagementActivity,
    val completedBatches: Int = 0,
    val totalBatches: Int = 0,
    val tokensSaved: Int = 0,
    val messagesCompressed: Int = 0,
    val errorResId: Int? = null,
    val errorArgs: List<Any> = emptyList(),
    val completedAt: Long = 0L,
) {
    val isRunning: Boolean get() = activity.isRunning
}
