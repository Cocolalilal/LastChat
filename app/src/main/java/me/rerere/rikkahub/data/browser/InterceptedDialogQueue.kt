package me.rerere.rikkahub.data.browser

/**
 * Bounded record of JS dialogs the agent browser answered with a default instead of showing.
 */
class InterceptedDialogQueue(
    private val maxEvents: Int = DEFAULT_MAX_EVENTS,
) {
    companion object {
        const val DEFAULT_MAX_EVENTS = 20
    }

    data class Event(
        val kind: String,
        val message: String,
        val defaultText: String?,
        val pageURL: String?,
        val defaultResponse: String,
    )

    private val events = mutableListOf<Event>()
    private var dropped = 0
    private val lock = Any()

    fun record(
        kind: String,
        message: String,
        defaultText: String?,
        pageURL: String?,
        defaultResponse: String,
    ) {
        synchronized(lock) {
            if (events.size >= maxEvents) {
                events.removeAt(0)
                dropped++
            }
            events.add(Event(kind, message, defaultText, pageURL, defaultResponse))
        }
    }

    val size: Int get() = synchronized(lock) { events.size }

    fun drainReport(): String? {
        val drainedEvents: List<Event>
        val drainedDropped: Int
        synchronized(lock) {
            if (events.isEmpty()) return null
            drainedEvents = events.toList()
            drainedDropped = dropped
            events.clear()
            dropped = 0
        }

        val lines = drainedEvents.map { e ->
            buildString {
                append("- ${e.kind}(): \"${e.message}\"")
                if (!e.defaultText.isNullOrEmpty()) append(" [default text: \"${e.defaultText}\"]")
                if (e.pageURL != null) append(" on ${e.pageURL}")
                append(" — page received `${e.defaultResponse}`")
            }
        }.toMutableList()
        if (drainedDropped > 0) {
            lines.add(
                "- ($drainedDropped earlier dialog${if (drainedDropped == 1) "" else "s"} " +
                    "dropped; only the most recent $maxEvents are kept)"
            )
        }

        val plural = if (drainedEvents.size == 1) "a dialog" else "${drainedEvents.size} dialogs"
        return "[Browser Dialog Intercepted] The page tried to show $plural, which was " +
            "NOT shown to the user — headless automation cannot block on modal prompts. " +
            "The page was given the default response shown below and continued running.\n" +
            lines.joinToString("\n") + "\n" +
            "If you need a different outcome, use execute_js to drive the page's " +
            "DOM/JS state directly instead of relying on native dialogs.\n\n" +
            "[Original tool result below]\n\n"
    }
}
