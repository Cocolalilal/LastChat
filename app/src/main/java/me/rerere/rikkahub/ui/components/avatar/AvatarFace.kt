package me.rerere.rikkahub.ui.components.avatar

import me.rerere.rikkahub.data.model.BlobEyePack

/**
 * Per-pack, per-lifecycle face poses. Pure Kotlin.
 *
 * Generical poses are authored from Julian's painted refs (base vertical
 * squircles, look-right, sad slanted-tops, wink, happy squint, horizontal
 * pills, look-up-right, goggles, neutral rounded). Personality is ONLY the two
 * eye glyphs morphing — no mouth/nose.
 *
 * Grok poses are small slanted black slits sitting on the flat coloured mark
 * (Grokky Character shots), not sphere-projected capsules.
 */

internal data class EyeCfg(
    val w: Float,
    val h: Float,
    val tilt: Float = 0f,
    val open: Float = 1f,
    /** Extra offset from the pair layout, ball-radius units. */
    val ox: Float = 0f,
    val oy: Float = 0f,
    /** Top-corner roundness: 0 = almost square, 1 = stadium. */
    val topRound: Float = 1f,
    /** Bottom-corner roundness. */
    val bottomRound: Float = 1f,
    /** Drop the inner-top corner as a fraction of full height (sad/worried). */
    val innerTopDrop: Float = 0f,
    /** Drop the outer-top corner as a fraction of full height. */
    val outerTopDrop: Float = 0f,
)

internal data class FaceSpec(
    val gaze: HeadGaze,
    val split: Float,
    val left: EyeCfg,
    val right: EyeCfg,
    /** how much idle gaze-drift applies (1 = full, 0 = locked pose) */
    val wander: Float,
    /** flat gaze translation, ball-radius units */
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val glowAlpha: Float = 0.5f,
)

internal enum class GenericalPose {
    Base,
    LookRight,
    LookLeft,
    Sad,
    Wink,
    Happy,
    Pills,
    LookUpRight,
    Neutral,
    Goggle,
}

internal fun faceFor(pack: BlobEyePack, lifecycle: BlobLifecycle): FaceSpec = when (pack) {
    BlobEyePack.Grok -> grokFace(lifecycle)
    BlobEyePack.Generical -> genericalFace(lifecycle)
}

private fun eye(
    w: Float,
    h: Float,
    tilt: Float = 0f,
    open: Float = 1f,
    ox: Float = 0f,
    oy: Float = 0f,
    topRound: Float = 1f,
    bottomRound: Float = 1f,
    innerTopDrop: Float = 0f,
    outerTopDrop: Float = 0f,
) = EyeCfg(w, h, tilt, open, ox, oy, topRound, bottomRound, innerTopDrop, outerTopDrop)

private fun mirror(
    w: Float,
    h: Float,
    tilt: Float = 0f,
    topRound: Float = 1f,
    bottomRound: Float = 1f,
    innerTopDrop: Float = 0f,
    outerTopDrop: Float = 0f,
) = eye(w, h, tilt, topRound = topRound, bottomRound = bottomRound, innerTopDrop = innerTopDrop, outerTopDrop = outerTopDrop) to
    eye(w, h, -tilt, topRound = topRound, bottomRound = bottomRound, innerTopDrop = innerTopDrop, outerTopDrop = outerTopDrop)

private val FLAT = HeadGaze(0f, 0f, 0f)

/* ---------------------------------------------------------- Generical sheet */

internal fun genericalPose(pose: GenericalPose): FaceSpec = when (pose) {
    // Locked BASE rest: two identical vertical rounded-rects, centered.
    // Proportions from generical-base-face.jpg (fat squircles, not thin capsules).
    GenericalPose.Base -> FaceSpec(
        gaze = FLAT, split = 0.30f,
        left = eye(0.34f, 0.68f), right = eye(0.34f, 0.68f),
        wander = 1f, lookY = 0f, glowAlpha = 0.18f,
    )
    // sheet-4up (a): both vertical eyes shifted right; left a touch taller.
    GenericalPose.LookRight -> FaceSpec(
        gaze = FLAT, split = 0.26f,
        left = eye(0.30f, 0.62f), right = eye(0.26f, 0.50f),
        wander = 0.2f, lookX = 0.24f, lookY = -0.04f, glowAlpha = 0.16f,
    )
    GenericalPose.LookLeft -> FaceSpec(
        gaze = FLAT, split = 0.26f,
        left = eye(0.26f, 0.50f), right = eye(0.30f, 0.62f),
        wander = 0.2f, lookX = -0.24f, lookY = -0.04f, glowAlpha = 0.16f,
    )
    // sheet-4up (b): shorter eyes, inner tops slanted toward center.
    GenericalPose.Sad -> FaceSpec(
        gaze = FLAT, split = 0.28f,
        left = eye(0.30f, 0.26f, topRound = 0.55f, bottomRound = 0.92f, innerTopDrop = 0.42f, outerTopDrop = 0.04f),
        right = eye(0.30f, 0.26f, topRound = 0.55f, bottomRound = 0.92f, innerTopDrop = 0.42f, outerTopDrop = 0.04f),
        wander = 0.06f, lookY = -0.02f, glowAlpha = 0.12f,
    )
    // sheet-4up (c): one tall rounded glyph + one thin horizontal pill.
    GenericalPose.Wink -> FaceSpec(
        gaze = FLAT, split = 0.30f,
        left = eye(0.30f, 0.34f, topRound = 0.88f, bottomRound = 0.95f),
        right = eye(0.40f, 0.072f),
        wander = 0.04f, lookX = -0.04f, lookY = 0.04f, glowAlpha = 0.12f,
    )
    // sheet-4up (d): two thin slightly smiling horizontal pills.
    GenericalPose.Happy -> {
        val (l, r) = mirror(0.40f, 0.068f, tilt = 11f)
        FaceSpec(gaze = FLAT, split = 0.30f, left = l, right = r, wander = 0.08f, lookY = 0.02f, glowAlpha = 0.16f)
    }
    // eyes-horizontal-pills.jpg: long thin white capsules, centered.
    GenericalPose.Pills -> FaceSpec(
        gaze = FLAT, split = 0.30f,
        left = eye(0.44f, 0.078f), right = eye(0.44f, 0.078f),
        wander = 0.1f, glowAlpha = 0.10f,
    )
    // look-up-right.jpg: irregular tilted glyphs clustered upper-right.
    GenericalPose.LookUpRight -> FaceSpec(
        gaze = FLAT, split = 0.18f,
        left = eye(0.30f, 0.32f, tilt = -22f, ox = -0.02f, oy = 0.05f, topRound = 0.72f, bottomRound = 0.88f),
        right = eye(0.28f, 0.40f, tilt = -10f, ox = 0.05f, oy = -0.07f, topRound = 0.85f, bottomRound = 0.92f),
        wander = 0.12f, lookX = 0.26f, lookY = -0.30f, glowAlpha = 0.14f,
    )
    // neutral-rounded.jpg: shorter, wider rounded rects, centered.
    GenericalPose.Neutral -> FaceSpec(
        gaze = FLAT, split = 0.31f,
        left = eye(0.36f, 0.20f, topRound = 0.82f, bottomRound = 0.82f),
        right = eye(0.36f, 0.20f, topRound = 0.82f, bottomRound = 0.82f),
        wander = 0.18f, glowAlpha = 0.14f,
    )
    // goggle-flat-top.jpg: flatter top, deeply rounded bottom.
    GenericalPose.Goggle -> FaceSpec(
        gaze = FLAT, split = 0.30f,
        left = eye(0.34f, 0.28f, topRound = 0.22f, bottomRound = 1f),
        right = eye(0.34f, 0.28f, topRound = 0.22f, bottomRound = 1f),
        wander = 0.14f, lookY = 0.02f, glowAlpha = 0.16f,
    )
}

private fun genericalFace(lifecycle: BlobLifecycle): FaceSpec = when (lifecycle) {
    BlobLifecycle.Idle -> genericalPose(GenericalPose.Base)
    BlobLifecycle.Thinking -> genericalPose(GenericalPose.LookUpRight)
    BlobLifecycle.Working -> genericalPose(GenericalPose.Goggle)
    BlobLifecycle.Waiting -> genericalPose(GenericalPose.Pills)
    BlobLifecycle.Blocked -> genericalPose(GenericalPose.Sad)
    BlobLifecycle.Done -> genericalPose(GenericalPose.Happy)
}

/* ---------------------------------------------------------- Grok slits */

/** Grok pack — small slanted dark ovals painted on the flat mark, upper half. */
private fun grokFace(lifecycle: BlobLifecycle): FaceSpec = when (lifecycle) {
    BlobLifecycle.Idle -> FaceSpec(
        gaze = FLAT, split = 0.155f,
        left = eye(0.145f, 0.052f, tilt = -20f),
        right = eye(0.145f, 0.052f, tilt = -20f),
        wander = 1f, lookY = -0.24f, glowAlpha = 0f,
    )
    BlobLifecycle.Thinking -> FaceSpec(
        gaze = FLAT, split = 0.145f,
        left = eye(0.130f, 0.046f, tilt = -14f),
        right = eye(0.118f, 0.040f, tilt = -16f),
        wander = 0.22f, lookX = 0.14f, lookY = -0.30f, glowAlpha = 0f,
    )
    BlobLifecycle.Working -> FaceSpec(
        gaze = FLAT, split = 0.16f,
        left = eye(0.150f, 0.078f, tilt = -12f),
        right = eye(0.150f, 0.078f, tilt = -12f),
        wander = 0.12f, lookY = -0.20f, glowAlpha = 0f,
    )
    BlobLifecycle.Waiting -> FaceSpec(
        gaze = FLAT, split = 0.16f,
        left = eye(0.165f, 0.026f, tilt = -8f),
        right = eye(0.165f, 0.026f, tilt = -8f),
        wander = 0.14f, lookY = -0.22f, glowAlpha = 0f,
    )
    BlobLifecycle.Blocked -> {
        val (l, r) = mirror(0.140f, 0.038f, tilt = 26f)
        FaceSpec(gaze = FLAT, split = 0.16f, left = l, right = r, wander = 0.06f, lookY = -0.18f, glowAlpha = 0f)
    }
    BlobLifecycle.Done -> {
        val (l, r) = mirror(0.150f, 0.030f, tilt = 14f)
        FaceSpec(gaze = FLAT, split = 0.16f, left = l, right = r, wander = 0.1f, lookY = -0.18f, glowAlpha = 0f)
    }
}

internal fun blendEye(a: EyeCfg, b: EyeCfg, t: Float) = EyeCfg(
    w = lerp(a.w, b.w, t),
    h = lerp(a.h, b.h, t),
    tilt = lerp(a.tilt, b.tilt, t),
    open = lerp(a.open, b.open, t),
    ox = lerp(a.ox, b.ox, t),
    oy = lerp(a.oy, b.oy, t),
    topRound = lerp(a.topRound, b.topRound, t),
    bottomRound = lerp(a.bottomRound, b.bottomRound, t),
    innerTopDrop = lerp(a.innerTopDrop, b.innerTopDrop, t),
    outerTopDrop = lerp(a.outerTopDrop, b.outerTopDrop, t),
)

internal fun blendFace(a: FaceSpec, b: FaceSpec, t: Float): FaceSpec {
    val k = clamp(t)
    return FaceSpec(
        gaze = HeadGaze(
            yaw = lerp(a.gaze.yaw, b.gaze.yaw, k),
            pitch = lerp(a.gaze.pitch, b.gaze.pitch, k),
            roll = lerp(a.gaze.roll, b.gaze.roll, k),
        ),
        split = lerp(a.split, b.split, k),
        left = blendEye(a.left, b.left, k),
        right = blendEye(a.right, b.right, k),
        wander = lerp(a.wander, b.wander, k),
        lookX = lerp(a.lookX, b.lookX, k),
        lookY = lerp(a.lookY, b.lookY, k),
        glowAlpha = lerp(a.glowAlpha, b.glowAlpha, k),
    )
}

/* ---------------------------------------------------------- idle micro-accents */

private data class AccentBeat(val start: Float, val dur: Float, val kind: Int)

private val IDLE_ACCENTS: List<AccentBeat> = run {
    val rng = createRng(0xA11E)
    val out = ArrayList<AccentBeat>(220)
    var t = 3.2f
    var kind = 0
    while (t < 1200f) {
        val dur = 0.55f + rng() * 0.8f
        out.add(AccentBeat(t, dur, kind % 6))
        kind++
        t += dur + 3.4f + rng() * 4.6f
    }
    out
}

/** (kind, envelope 0..1) of the current idle accent, if any. Deterministic. */
internal fun idleAccentEnvelope(t: Float): Pair<Int, Float> {
    for (beat in IDLE_ACCENTS) {
        if (t < beat.start) break
        val k = (t - beat.start) / beat.dur
        if (k in 0f..1f) {
            val env = when {
                k < 0.32f -> k / 0.32f
                k > 0.68f -> (1f - k) / 0.32f
                else -> 1f
            }
            return beat.kind to clamp(env)
        }
    }
    return 0 to 0f
}

internal fun applyIdleAccent(pack: BlobEyePack, face: FaceSpec, kind: Int, weight: Float): FaceSpec {
    if (weight <= 0.001f) return face
    val target = when (pack) {
        BlobEyePack.Grok -> when (kind) {
            0 -> face.copy(lookX = face.lookX - 0.12f)
            1 -> face.copy(lookX = face.lookX + 0.12f)
            2 -> face.copy(lookY = face.lookY - 0.08f)
            3 -> face.copy(right = face.right.copy(h = face.right.h * 0.28f, w = face.right.w * 1.15f))
            4 -> face.copy(
                left = face.left.copy(h = face.left.h * 0.45f, tilt = 12f),
                right = face.right.copy(h = face.right.h * 0.45f, tilt = -12f),
            )
            else -> face.copy(
                left = face.left.copy(w = face.left.w * 1.12f, h = face.left.h * 1.35f),
                right = face.right.copy(w = face.right.w * 1.12f, h = face.right.h * 1.35f),
            )
        }
        BlobEyePack.Generical -> when (kind) {
            0 -> genericalPose(GenericalPose.LookRight)
            1 -> genericalPose(GenericalPose.LookLeft)
            2 -> genericalPose(GenericalPose.LookUpRight)
            3 -> genericalPose(GenericalPose.Wink)
            4 -> genericalPose(GenericalPose.Happy)
            else -> genericalPose(GenericalPose.Goggle)
        }
    }
    return blendFace(face, target, weight)
}
