package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Lightweight shared "stage" so on-screen animated avatars know where each other
 * are (window coordinates) and can react to app events as if they were there.
 *
 * Main-thread only (registration happens from layout callbacks, reads from frame
 * callbacks), so no locking. Nothing here allocates per frame.
 */
class AvatarStage(private val rng: Random = Random.Default) {

    class Entry(
        val id: Long,
        var windowKey: Int,
        var center: Offset,
        var radius: Float,
        /** shape + eye type — twins are more likely to look puzzled at each other. */
        var signature: String,
        var calm: Boolean = true,
        var windowW: Float = 0f,
        var windowH: Float = 0f,
    )

    enum class EventKind { MessageArrived, Scroll, UserTyping }

    class Event(
        val seq: Long,
        val kind: EventKind,
        /** Window-space point the event happened at (null = bottom of the window). */
        val at: Offset?,
        /** Scroll: +1 content moving up (reading down), -1 the other way. */
        val direction: Int = 0,
        val atMs: Long,
    )

    /** A pending mutual glance: [from] looks at [to]; [skeptic] (if any) goes 🤨. */
    class Glance(val from: Long, val to: Long, val skeptic: Long?, val startedMs: Long)

    private val entries = LinkedHashMap<Long, Entry>()
    private val events = ArrayList<Event>()
    private var seq = 0L
    private var nextId = 1L
    // First moment can come ~20–40 s after launch, then the regular long gaps.
    private var lastGlanceMs = System.currentTimeMillis()
    private var glanceGapMs = 20_000L + rng.nextLong(20_000L)
    private val glances = HashMap<Long, Glance>()

    fun newId(): Long = nextId++

    fun update(
        id: Long,
        windowKey: Int,
        center: Offset,
        radius: Float,
        signature: String,
        windowW: Float = 0f,
        windowH: Float = 0f,
    ) {
        val e = entries[id] ?: Entry(id, windowKey, center, radius, signature).also { entries[id] = it }
        e.windowKey = windowKey; e.center = center; e.radius = radius; e.signature = signature
        e.windowW = windowW; e.windowH = windowH
    }

    fun setCalm(id: Long, calm: Boolean) {
        entries[id]?.calm = calm
    }

    fun remove(id: Long) {
        entries.remove(id)
        glances.remove(id)
    }

    fun entry(id: Long): Entry? = entries[id]

    /** Other visible avatars in the same window, nearest first. */
    fun neighbors(id: Long, maxDistancePx: Float): List<Entry> {
        val me = entries[id] ?: return emptyList()
        return entries.values
            .filter { it.id != id && it.windowKey == me.windowKey }
            .filter { hypot(it.center.x - me.center.x, it.center.y - me.center.y) <= maxDistancePx }
            .sortedBy { hypot(it.center.x - me.center.x, it.center.y - me.center.y) }
    }

    fun emit(kind: EventKind, at: Offset? = null, direction: Int = 0, nowMs: Long = System.currentTimeMillis()) {
        events += Event(++seq, kind, at, direction, nowMs)
        while (events.size > 16) events.removeAt(0)
    }

    /** Events newer than [afterSeq]. */
    fun eventsAfter(afterSeq: Long): List<Event> =
        if (events.isEmpty() || events.last().seq <= afterSeq) emptyList() else events.filter { it.seq > afterSeq }

    val latestSeq: Long get() = seq

    /**
     * Ask to start a mutual glance. Globally throttled (one every ~45–110 s across
     * all avatars) so it stays a rare little moment, never a routine.
     */
    fun tryStartGlance(from: Long, maxDistancePx: Float, nowMs: Long): Glance? {
        if (nowMs - lastGlanceMs < glanceGapMs) return null
        val me = entries[from] ?: return null
        val target = neighbors(from, maxDistancePx).firstOrNull { it.calm && !glances.containsKey(it.id) } ?: return null
        val twins = target.signature == me.signature
        val skepticChance = if (twins) 0.6f else 0.4f
        val skeptic = when {
            rng.nextFloat() >= skepticChance -> null
            rng.nextBoolean() -> from
            else -> target.id
        }
        val g = Glance(from, target.id, skeptic, nowMs)
        glances[target.id] = g
        lastGlanceMs = nowMs
        glanceGapMs = (45_000L + rng.nextLong(65_000L))
        return g
    }

    /** Glance addressed to [id] (consumed). */
    fun takeGlanceFor(id: Long): Glance? = glances.remove(id)

    companion object {
        /** App-wide default; tests can provide their own via [LocalAvatarStage]. */
        val Global = AvatarStage()
    }
}

val LocalAvatarStage = staticCompositionLocalOf { AvatarStage.Global }
