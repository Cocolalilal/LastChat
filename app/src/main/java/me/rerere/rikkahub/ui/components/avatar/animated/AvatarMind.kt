package me.rerere.rikkahub.ui.components.avatar.animated

import kotlin.math.exp
import kotlin.math.hypot

/*
 * Engine18 — the avatar "mind". See docs/avatar-character-engine.md, "Engine18".
 *
 *   event → Perception (noticed after a mood-varying delay)
 *         → Appraisal (what it means here: repeated poke = annoyed, reply end = satisfied …)
 *         → Intention (lane, priority, cause, expiry, hold) → MindDirector (one owner per lane,
 *           safe points, holds, budget, cross-lane rules) → motion channels.
 *
 * Mood / energy only drift over minutes (MoodEngine); short-term memory keeps it from
 * repeating looks, lets it habituate to pokes and glance back at where something happened.
 */

/** Motion channels. Nothing writes a channel directly; intentions land through the director. */
internal enum class Lane { Body, Gaze, Face, Lids, Spin }

/**
 * Personality knobs for the mind (0…1, 0.5 = average). This is the API LC Smart Core will
 * set later; [fromPersonality] derives defaults from the four art-direction traits.
 */
data class MindKnobs(
    /** How much shows on the face: budget, hold length, accent strength. */
    val expressiveness: Float = 0.5f,
    /** How readily it notices and looks back at things. */
    val curiosity: Float = 0.5f,
    /** How many pokes before it gets cross, how long it holds a look. */
    val patience: Float = 0.5f,
    /** Spins, wiggles and other playful beats. */
    val playfulness: Float = 0.5f,
) {
    fun clamped() = MindKnobs(expressiveness.coerceIn(0f, 1f), curiosity.coerceIn(0f, 1f), patience.coerceIn(0f, 1f), playfulness.coerceIn(0f, 1f))

    companion object {
        val NAMES = listOf("expressiveness", "curiosity", "patience", "playfulness")
        fun fromPersonality(p: Personality) = MindKnobs(
            expressiveness = p.expressiveness,
            curiosity = p.curiosity,
            patience = (0.35f + 0.4f * p.warmth + 0.25f * p.calmness).coerceIn(0f, 1f),
            playfulness = p.playfulness,
        )
        /** Overrides by knob name on top of [base]; unknown keys ignored. */
        fun resolve(base: MindKnobs, overrides: Map<String, Float>?): MindKnobs {
            var k = base
            overrides?.forEach { (n, v) ->
                k = when (n) {
                    "expressiveness" -> k.copy(expressiveness = v)
                    "curiosity" -> k.copy(curiosity = v)
                    "patience" -> k.copy(patience = v)
                    "playfulness" -> k.copy(playfulness = v)
                    else -> k
                }
            }
            return k.clamped()
        }
    }
}

/** A proposal from any system. It lands only when the director says so, or expires. */
internal class Intention(
    val lane: Lane,
    val name: String,
    val priority: Int,
    val cause: String,
    val born: Float,
    val ttl: Float,
    val act: () -> Unit,
) {
    fun expired(now: Float) = now - born > ttl
}

internal object MindRules {
    /** No two eye changes (expression morph start / accent onset) closer than this. */
    const val EYE_GAP = 0.42f
    /** The face follows a gaze shift by this much (gaze leads). */
    const val GAZE_LEAD = 0.16f
    /** Engine19: minimum dwell before a self-started (spontaneous) face change. */
    const val SPONT_DWELL = 1.2f
    const val SPONT_DWELL_REST = 3.2f
    /** Priorities. */
    const val P_IDLE = 0      // spontaneous beats, episodes
    const val P_LIFE = 1      // lifecycle faces, returning to rest
    const val P_EVENT = 2     // perceived app events, editor, reply end
    const val P_USER = 3      // pokes and other direct touches
    const val P_URGENT = 4    // errors

    /** Hold after a face lands: longer when calm / low energy, shorter when lively. */
    fun hold(arousal: Float, k: MindKnobs): Float =
        ((1.0f + 1.1f * (1f - arousal)) * (1.25f - 0.45f * k.expressiveness) * (0.85f + 0.3f * k.patience)).coerceIn(0.7f, 2.6f)

    /** Spontaneous (priority ≤ P_LIFE) noticeable changes allowed per rolling minute. */
    fun budget(busy: Boolean, arousal: Float, k: MindKnobs): Int =
        ((if (busy) 7f else 4.5f) * (0.65f + 0.7f * k.expressiveness) * (0.75f + 0.5f * arousal)).toInt().coerceIn(2, 9)

    /** Hard cap for everything below P_USER. */
    const val HARD_CAP = 14

    val NEGATIVE_FACES = setOf("Angry", "angered or concentrated", "Sad", "pouty", "Bored", "hurt or cute")
    val NEGATIVE_ACCENTS = setOf(FaceAccent.Annoyed)

    /**
     * Engine20 emotional dwell. A feeling is not a flicker: once shown it holds for a time that
     * fits it (surprise is brief by nature; sadness and anger build and linger), and negative
     * feelings arise only from an appraisal of something that happened, never at random.
     */
    val APPRAISED_NEGATIVE = setOf("Angry", "Sad", "pouty")
    val APPRAISED_NEGATIVE_ACCENTS = setOf("Sad", "Annoyed", "Worried")
    fun minDwell(face: String): Float = when (face) {
        "Neutral", "None", "" -> 0f
        "Surprised", "Startled" -> 0.4f
        "Angry", "Sad", "pouty", "Annoyed", "Worried" -> 3.5f
        "Bored" -> 3f
        "Drowsy", "Sleeping" -> 2.2f
        "angered or concentrated", "Focused", "confused", "confused left", "Puzzled" -> 1.6f
        "WakeSquint", "Dizzy", "Eyes closed" -> 0.6f
        else -> 1.4f
    }
    private val EVENT_CAUSE = Regex("poke|tone|apprais|reply|user|error|message|editor|event|touch|fling|spin|neighbour", RegexOption.IGNORE_CASE)
    fun hasEventCause(prio: Int, cause: String): Boolean = prio >= P_EVENT || EVENT_CAUSE.containsMatchIn(cause)

    /** Perception delay scaling by state: alert = quicker, tired / drowsy = slower. */
    fun perceptionScale(arousal: Float, energy: Float, drowsy: Boolean, k: MindKnobs): Float =
        ((1.4f - 0.55f * arousal - 0.25f * energy) * (if (drowsy) 1.5f else 1f) * (1.12f - 0.24f * k.curiosity)).coerceIn(0.6f, 2.2f)
}

/** What an event means in context (Appraisal). */
internal enum class Meaning { Startled, Amused, Curious, Annoyed, FedUp, Habituated, Satisfied, Relieved, Concerned, Interested, Nothing }

internal object Appraisal {
    /**
     * A poke. [burst] = pokes in the current burst (incl. this), [familiar] = habituation
     * (decaying count over minutes), [valence] = mood.
     */
    fun poke(burst: Int, familiar: Float, valence: Float, k: MindKnobs): Meaning {
        val crossAt = (10f + 5f * k.patience + (if (valence > 0.3f) 2f else 0f)).toInt().coerceIn(10, 16)
        return when {
            burst >= crossAt + 5 -> Meaning.FedUp
            burst >= crossAt -> Meaning.Annoyed
            burst >= 2 -> Meaning.Startled
            // Poked over and over across minutes (not in a burst): it gets used to it.
            familiar > 5.5f -> Meaning.Habituated
            familiar > 3f && valence > 0.2f -> Meaning.Amused
            else -> Meaning.Startled
        }
    }

    /** The reply finished streaming. [busySec] = how long it worked. */
    fun replyEnd(busySec: Float, valence: Float): Meaning = when {
        busySec > 25f -> Meaning.Relieved
        valence > -0.2f -> Meaning.Satisfied
        else -> Meaning.Nothing
    }

    fun event(kind: String): Meaning = when (kind) {
        "Error" -> Meaning.Concerned
        "MessageSent", "MessageArrived" -> Meaning.Interested
        "Deleted", "Regenerated" -> Meaning.Curious
        else -> Meaning.Interested
    }
}

/** Short-term memory: recent looks, poke habituation, where things happened. */
internal class ShortTermMemory {
    private class Look(val x: Float, val y: Float, val t: Float)
    class Place(val x: Float, val y: Float, val t: Float, val what: String, var revisited: Boolean = false)
    private val looks = ArrayDeque<Look>()
    val places = ArrayDeque<Place>()
    /** Poke familiarity: +1 per poke, decays with τ = 150 s. */
    var pokeFamiliar = 0f; private set
    var lastPokeAt = -999f; private set

    fun update(dt: Float) { pokeFamiliar *= exp(-dt / 150f) }

    fun poked(now: Float) { pokeFamiliar += 1f; lastPokeAt = now }

    fun looked(x: Float, y: Float, now: Float) {
        looks.addLast(Look(x, y, now)); while (looks.size > 12) looks.removeFirst()
    }

    /** Did it already look (near) there within [sec]? */
    fun lookedRecently(x: Float, y: Float, now: Float, sec: Float = 25f, r: Float = 0.16f) =
        looks.any { now - it.t < sec && hypot(it.x - x, it.y - y) < r }

    fun happened(x: Float, y: Float, now: Float, what: String) {
        places.addLast(Place(x, y, now, what)); while (places.size > 6) places.removeFirst()
    }

    /** A place worth glancing back at: 8–45 s ago, not yet revisited, away from rest. */
    fun glanceBackCandidate(now: Float): Place? =
        places.lastOrNull { !it.revisited && now - it.t in 8f..45f && hypot(it.x, it.y) > 0.25f }
}

/** One audit record. */
internal class MindEvent(val t: Float, val lane: Lane, val name: String, val priority: Int, val cause: String)

/**
 * The director. Owns the face lane (one intention at a time), holds every landed change,
 * waits for safe points (never cuts a morph), enforces the per-minute budget, the
 * cross-lane rules and the long rare cooldowns, and records everything for the audit.
 */
internal class MindDirector {
    var knobs = MindKnobs()
    var now = 0f; private set

    // ---- face lane ownership
    private var ownerPrio = -1
    private var ownerName = "Neutral"
    private var holdUntil = 0f
    private var pendingHold = 0f
    var lastEyeChange = -99f; private set
    private var wasMorphing = false
    var lastGazeShift = -99f; private set
    private val queue = ArrayList<Intention>()

    // ---- budget
    private val acts = ArrayDeque<Pair<Float, Int>>()

    // ---- audit
    val log = ArrayList<MindEvent>()
    var interruptsPrevented = 0; private set
    var budgetDeferred = 0; private set
    var expired = 0; private set
    var spinsBlockedIdle = 0; private set
    var spinsBlockedNegative = 0; private set
    var blinksDeferred = 0; private set
    var maxQueue = 0; private set
    var budgetViolations = 0; private set
    var ownerFlips = 0; private set
    private val flipTimes = ArrayDeque<Float>()
    val owner: String get() = ownerName
    val ownerPriority: Int get() = ownerPrio
    var lastBusy = false
    var lastArousal = 0.5f

    private fun count(priority: Int) {
        acts.addLast(now to priority)
        if (priority <= MindRules.P_LIFE && spontaneousCount() > MindRules.budget(lastBusy, lastArousal, knobs) + 1) budgetViolations++
        if (priority < MindRules.P_USER && totalCount() > MindRules.HARD_CAP + 1) budgetViolations++
    }

    fun update(dt: Float, morphing: Boolean, gazeBigShift: Boolean) {
        now += dt
        if (gazeBigShift) lastGazeShift = now
        // Engine19: the owner's hold runs from the moment it took the face (the morph runs
        // concurrently); afterwards the face is free — it keeps its last target, never Neutral.
        wasMorphing = morphing
        if (now >= holdUntil) ownerPrio = -1
        while (acts.isNotEmpty() && now - acts.first().first > 60f) acts.removeFirst()
        val it = queue.iterator()
        while (it.hasNext()) if (it.next().expired(now)) { it.remove(); expired++ }
    }

    /** Is a face change at [priority] allowed this frame? */
    fun faceSafe(priority: Int, @Suppress("UNUSED_PARAMETER") morphing: Boolean): Boolean {
        // Engine19 arbiter: one owner per channel, salience-weighted.
        //  - reactive (≥ P_EVENT): retargets immediately, from the current in-between pose;
        //    only a stronger owner still inside its hold keeps the face.
        //  - spontaneous: waits for the owner's dwell, plus a minimum dwell (hysteresis).
        if (priority >= MindRules.P_EVENT) {
            if (now < holdUntil && priority < ownerPrio) { interruptsPrevented++; return false }
            return true
        }
        if (now < holdUntil && priority <= ownerPrio) return false
        if (now - lastEyeChange < MindRules.SPONT_DWELL) return false
        // A face that has relaxed to rest stays at rest a while before a self-started face
        // (rest, then something new — never a blip of Neutral between two faces).
        if (ownerName == "Neutral" && now - lastEyeChange < MindRules.SPONT_DWELL_REST) return false
        // Gaze leads: a self-started face follows a big eye move a beat later.
        if (now - lastGazeShift < MindRules.GAZE_LEAD) return false
        return true
    }

    fun spontaneousCount() = acts.count { it.second <= MindRules.P_LIFE }
    fun totalCount() = acts.count { it.second < MindRules.P_USER }

    /** Budget check for a new noticeable change. Returning to rest is not new activity. */
    fun budgetOk(priority: Int, busy: Boolean, arousal: Float, settle: Boolean = false): Boolean {
        lastBusy = busy; lastArousal = arousal
        if (settle || priority >= MindRules.P_USER) return true
        val ok = if (priority <= MindRules.P_LIFE) spontaneousCount() < MindRules.budget(busy, arousal, knobs) && totalCount() < MindRules.HARD_CAP
            else totalCount() < MindRules.HARD_CAP
        if (!ok) budgetDeferred++
        return ok
    }

    /** A face change lands now: it owns the face, then holds for [hold] once settled. */
    fun landFace(name: String, priority: Int, cause: String, hold: Float, countBudget: Boolean = true) {
        lastEyeChange = now
        if (name != ownerName) { ownerFlips++; flipTimes.addLast(now) }
        while (flipTimes.isNotEmpty() && now - flipTimes.first() > 10f) flipTimes.removeFirst()
        ownerPrio = priority; ownerName = name
        holdUntil = now + 0.35f + hold
        if (countBudget) count(priority)
        log += MindEvent(now, Lane.Face, name, priority, cause)
    }

    /** An overlay (accent) on a settled face: owns the face for its duration. */
    fun landAccent(name: String, priority: Int, cause: String, sec: Float) {
        lastEyeChange = now
        ownerPrio = maxOf(ownerPrio, priority); ownerName = name
        holdUntil = maxOf(holdUntil, now + sec)
        count(priority)
        log += MindEvent(now, Lane.Face, "accent:$name", priority, cause)
    }

    fun noteAct(lane: Lane, name: String, priority: Int, cause: String, countBudget: Boolean = true) {
        if (countBudget) count(priority)
        log += MindEvent(now, lane, name, priority, cause)
    }

    fun blockedSpin(idle: Boolean) { if (idle) spinsBlockedIdle++ else spinsBlockedNegative++ }
    fun deferredBlink() { blinksDeferred++ }

    fun propose(i: Intention) {
        // Same lane + name supersedes the older proposal.
        queue.removeAll { it.lane == i.lane && it.name == i.name }
        queue += i
        if (queue.size > maxQueue) maxQueue = queue.size
    }

    /** Run the best ready intention for [lane] if [ready] says it may land now. */
    fun pump(lane: Lane, ready: (Intention) -> Boolean) {
        val best = queue.filter { it.lane == lane }.maxWithOrNull(compareBy<Intention> { it.priority }.thenBy { -it.born }) ?: return
        if (!ready(best)) return
        queue.remove(best)
        best.act()
    }

    fun queued(lane: Lane) = queue.count { it.lane == lane }
}
