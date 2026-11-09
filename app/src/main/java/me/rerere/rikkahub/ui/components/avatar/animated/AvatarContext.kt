package me.rerere.rikkahub.ui.components.avatar.animated

/**
 * Engine21: context appropriateness. What the avatar may do depends on what is happening —
 * one table (context × behaviour), consulted by the director before any face, accent,
 * episode or rest state starts, and re-checked every frame so a state that was fine a
 * moment ago (resting eyes while idle) ends as soon as the context changes (a reply
 * starts generating, the user starts typing).
 */
internal object ContextRules {
    enum class Ctx {
        /** Nothing going on and nobody around for a while. */
        Idle,
        /** The user is interacting (typing, scrolling, tapping, just sent / opened something). */
        UserActive,
        /** Generating: thinking / working before the first token. */
        Thinking,
        /** Generating with tokens streaming in. */
        Streaming,
        /** A reply just finished / a request just failed. */
        Outcome,
        /** The avatar's own sleeping lifecycle. */
        Asleep,
    }

    enum class Behaviour {
        /** Eyes shut for longer than a blink-ish squeeze ([MAX_CLOSED_ATTENTIVE] s). */
        LongEyesClosed,
        /** Resting the eyes / dozing off (idle rest episode). */
        EyeRest,
        /** The Sleeping face or a drowsy state. */
        Sleepy,
        /** The Bored face. */
        Bored,
        /** A yawn. */
        Yawn,
        /** A drowsy accent (heavy lids). */
        DrowsyAccent,
        /** Self-started idle episodes (looking around, tilt chains…). */
        IdleEpisode,
        /** A short closed-eye squeeze (≤ [MAX_CLOSED_ATTENTIVE] s): concentration, a yawn's end. */
        ShortEyesClosed,
        /** Reactions to UI hooks (attachments, model picker, timeline…). */
        UiReaction,
        /** Classic spins (Turn / Top / Flip), still subject to their own cause rules. */
        Spin,
        /**
         * Engine21.1: a brief (≤ [BRIEF_BORED] s) unimpressed Bored beat as an event reaction —
         * "hey, I was reading that" when the user interrupts its timeline reading. Not low energy:
         * allowed whenever it is awake.
         */
        BriefUnimpressed,
    }

    /** Longest Bored beat that still counts as [Behaviour.BriefUnimpressed] (s). */
    const val BRIEF_BORED = 1.8f
    /** Longest an attentive avatar keeps its eyes shut (s). */
    const val MAX_CLOSED_ATTENTIVE = 0.75f
    /** After the user's last interaction they count as active for this long (s). */
    const val USER_ACTIVE_SEC = 12f

    private val B = Behaviour.entries.toSet()
    private val LOW = setOf(Behaviour.LongEyesClosed, Behaviour.EyeRest, Behaviour.Sleepy, Behaviour.Bored, Behaviour.Yawn, Behaviour.DrowsyAccent)

    /** THE table: allowed behaviours per context. */
    val TABLE: Map<Ctx, Set<Behaviour>> = mapOf(
        Ctx.Idle to B,
        Ctx.UserActive to B - LOW,
        Ctx.Thinking to B - LOW - Behaviour.IdleEpisode,
        Ctx.Streaming to B - LOW - Behaviour.IdleEpisode - Behaviour.ShortEyesClosed,
        Ctx.Outcome to B - LOW - Behaviour.IdleEpisode,
        Ctx.Asleep to setOf(Behaviour.LongEyesClosed, Behaviour.EyeRest, Behaviour.Sleepy, Behaviour.ShortEyesClosed, Behaviour.Yawn),
        // (BriefUnimpressed is in B, so every awake context allows it; Asleep does not.)
    )

    fun allowed(ctx: Ctx, b: Behaviour): Boolean = b in TABLE.getValue(ctx)

    /** True for contexts where the avatar must look awake and attentive. */
    fun attentive(ctx: Ctx) = ctx != Ctx.Idle && ctx != Ctx.Asleep

    /** Behaviour a face held for [sec] seconds amounts to (null = always fine). */
    fun faceBehaviour(face: String, sec: Float): Behaviour? = when (face) {
        "Sleeping" -> Behaviour.Sleepy
        "Bored" -> if (sec <= BRIEF_BORED) Behaviour.BriefUnimpressed else Behaviour.Bored
        "Eyes closed" -> if (sec > MAX_CLOSED_ATTENTIVE) Behaviour.LongEyesClosed else Behaviour.ShortEyesClosed
        else -> null
    }

    fun accentBehaviour(a: FaceAccent): Behaviour? = when (a) {
        FaceAccent.Drowsy -> Behaviour.DrowsyAccent
        else -> null
    }

    fun faceOk(ctx: Ctx, face: String, sec: Float): Boolean = faceBehaviour(face, sec)?.let { allowed(ctx, it) } ?: true
    fun accentOk(ctx: Ctx, a: FaceAccent): Boolean = accentBehaviour(a)?.let { allowed(ctx, it) } ?: true

    /**
     * The attentive stand-in for a face the context does not allow: concentration while
     * generating, the rest face otherwise.
     */
    fun substitute(ctx: Ctx, face: String): String = when (ctx) {
        Ctx.Thinking -> "angered or concentrated"
        else -> "Neutral"
    }

    fun of(life: MarkLifecycle, userActive: Boolean): Ctx = when (life) {
        MarkLifecycle.Thinking, MarkLifecycle.Working -> Ctx.Thinking
        MarkLifecycle.Streaming -> Ctx.Streaming
        MarkLifecycle.Finished, MarkLifecycle.Failed -> Ctx.Outcome
        MarkLifecycle.Sleeping -> Ctx.Asleep
        MarkLifecycle.Listening -> Ctx.UserActive
        MarkLifecycle.Idle -> if (userActive) Ctx.UserActive else Ctx.Idle
    }
}

/**
 * Engine21: per-transition timing and the blink-mask rule.
 *
 * Every face sits at a point in a small valence × arousal space; the distance between two
 * faces is how big the emotional shift is. Default (Soft) changes get a spring speed by
 * that distance: small shifts stay quick (ω ≈ 15, ~0.25 s), big swings take more time
 * (ω ≈ 8.5, ~0.45 s) so they never look abrupt. Snap (poke, startle), Gradual (sleepy) and
 * Settle (relaxing home) keep their own profiles.
 */
internal object TransitionRules {
    private val VA: Map<String, Pair<Float, Float>> = mapOf(
        "Neutral" to (0f to 0f),
        "Happy" to (0.8f to 0.35f),
        "hurt or cute" to (0.5f to 0.3f),
        "Surprised" to (0.2f to 0.9f),
        "Sad" to (-0.7f to -0.3f),
        "Angry" to (-0.75f to 0.6f),
        "angered or concentrated" to (-0.1f to 0.3f),
        "pouty" to (-0.45f to 0.1f),
        "Bored" to (-0.2f to -0.6f),
        "Eyes closed" to (0f to -0.5f),
        "Sleeping" to (0f to -0.9f),
        "confused" to (-0.1f to 0.35f),
        "confused left" to (-0.1f to 0.35f),
    )

    fun distance(from: String, to: String): Float {
        val a = VA[from] ?: (0f to 0f); val b = VA[to] ?: (0f to 0f)
        return kotlin.math.hypot(a.first - b.first, a.second - b.second)
    }

    const val OMEGA_SMALL = 15f
    const val OMEGA_BIG = 8.5f

    /** Spring ω for a default (Soft) change of emotional size [d]. */
    fun softOmega(d: Float): Float {
        val u = ((d - 0.3f) / 1.0f).coerceIn(0f, 1f)
        val s = u * u * (3f - 2f * u)
        return OMEGA_SMALL + (OMEGA_BIG - OMEGA_SMALL) * s
    }

    /** Below this emotional distance a change is an intensity tweak: always a direct morph. */
    const val TINY = 0.25f
    private val CLOSED = setOf("Eyes closed", "Sleeping", "Bored")

    enum class Mask { Blink, Direct }

    /**
     * THE blink-mask rule: most face changes happen behind a blink (lids close on one face,
     * open on the next). A direct morph is kept where a blink would look wrong:
     *  - fast reactions (Snap style: poke surprise, startles; or user-priority causes),
     *  - closed-eye targets or sources (nothing to hide; the lids are the expression),
     *  - Happy as the source (Grok's happy is a closed arc: the swap would pop),
     *  - tiny intensity tweaks (distance < [TINY]),
     *  - while drowsy or spinning (caller), or when a blink cannot start right now.
     */
    fun mask(from: String, to: String, snap: Boolean, userPrio: Boolean): Mask = when {
        snap || userPrio -> Mask.Direct
        to in CLOSED || from in CLOSED -> Mask.Direct
        from == "Happy" -> Mask.Direct
        distance(from, to) < TINY -> Mask.Direct
        else -> Mask.Blink
    }

    /** A masking blink may follow the last blink this soon (s): it reads as part of the change. */
    const val MASK_MIN_GAP = 0.6f

    /** Chance a Blink-eligible change really uses a blink (a few stay plain morphs for variety). */
    fun maskChance(d: Float): Float = if (d >= 0.8f) 0.85f else 0.72f
}
