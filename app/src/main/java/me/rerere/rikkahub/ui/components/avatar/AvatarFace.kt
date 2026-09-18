package me.rerere.rikkahub.ui.components.avatar

import me.rerere.rikkahub.data.model.BlobEyePack

/**
 * Per-pack, per-lifecycle face poses. Pure Kotlin.
 *
 * Grok poses are the MEASURED expressions from `bloub` (see
 * docs/create-avatar-research.md): `split` is the half-gap in degrees on the
 * sphere, eyes are projected. Generical poses are authored from the attached
 * expression sheet: flat upright rounded-rect eyes, `split` is the half-distance
 * of the eye centres in ball-radius units, look-around is a translation.
 */

internal data class EyeCfg(
    val w: Float,
    val h: Float,
    val tilt: Float = 0f,
    val open: Float = 1f,
)

internal data class FaceSpec(
    val gaze: HeadGaze,
    val split: Float,
    val left: EyeCfg,
    val right: EyeCfg,
    /** how much idle gaze-drift applies (1 = full, 0 = locked pose) */
    val wander: Float,
    /** flat gaze translation, ball-radius units (Generical only) */
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val glowAlpha: Float = 0.5f,
)

internal fun faceFor(pack: BlobEyePack, lifecycle: BlobLifecycle): FaceSpec = when (pack) {
    BlobEyePack.Grok -> grokFace(lifecycle)
    BlobEyePack.Generical -> genericalFace(lifecycle)
}

private fun eye(w: Float, h: Float, tilt: Float = 0f, open: Float = 1f) = EyeCfg(w, h, tilt, open)
private fun mirror(w: Float, h: Float, tilt: Float = 0f, open: Float = 1f) =
    eye(w, h, tilt, open) to eye(w, h, -tilt, open)

/** Grok pack — measured sphere expressions mapped to the lifecycle. */
private fun grokFace(lifecycle: BlobLifecycle): FaceSpec = when (lifecycle) {
    BlobLifecycle.Idle -> FaceSpec(
        gaze = REST_GAZE, split = EYE_SPLIT,
        left = eye(EYE_W, EYE_H), right = eye(EYE_W, EYE_H), wander = 1f,
    )
    BlobLifecycle.Thinking -> FaceSpec(
        gaze = HeadGaze(16f, -9f, -15f), split = 16.5f,
        left = eye(0.24f, 0.46f, -8f), right = eye(0.20f, 0.38f, -8f), wander = 0.25f,
    )
    BlobLifecycle.Working -> FaceSpec(
        gaze = HeadGaze(6.92f, -21.96f, 11.6f), split = 18.43f,
        left = eye(0.356f, 0.75f), right = eye(0.356f, 0.75f), wander = 0.12f,
    )
    BlobLifecycle.Waiting -> FaceSpec(
        gaze = HeadGaze(-22f, 2f, 0f), split = 16f,
        left = eye(0.30f, 0.12f), right = eye(0.30f, 0.12f), wander = 0.14f,
    )
    BlobLifecycle.Blocked -> {
        val (l, r) = mirror(0.34f, 0.15f, 30f)
        FaceSpec(gaze = HeadGaze(3f, 7f, 0f), split = 17f, left = l, right = r, wander = 0.06f)
    }
    BlobLifecycle.Done -> {
        val (l, r) = mirror(0.27f, 0.17f, 14f)
        FaceSpec(gaze = HeadGaze(5f, 9f, 0f), split = 17f, left = l, right = r, wander = 0.1f)
    }
}

/** Generical pack — flat upright rounded-rect glyphs authored from the sheet. */
private fun genericalFace(lifecycle: BlobLifecycle): FaceSpec = when (lifecycle) {
    BlobLifecycle.Idle -> FaceSpec(
        gaze = HeadGaze(0f, 0f, 0f), split = 0.28f,
        left = eye(0.185f, 0.42f), right = eye(0.185f, 0.42f), wander = 1f, glowAlpha = 0.30f,
    )
    BlobLifecycle.Thinking -> FaceSpec(
        gaze = HeadGaze(0f, 0f, 0f), split = 0.27f,
        left = eye(0.165f, 0.34f), right = eye(0.165f, 0.34f), wander = 0.22f,
        lookX = 0.06f, lookY = -0.12f, glowAlpha = 0.24f,
    )
    BlobLifecycle.Working -> FaceSpec(
        gaze = HeadGaze(0f, 0f, 0f), split = 0.28f,
        left = eye(0.19f, 0.38f), right = eye(0.19f, 0.38f), wander = 0.14f, glowAlpha = 0.34f,
    )
    BlobLifecycle.Waiting -> FaceSpec(
        gaze = HeadGaze(0f, 0f, 0f), split = 0.28f,
        left = eye(0.24f, 0.09f), right = eye(0.24f, 0.09f), wander = 0.1f,
        lookX = 0.1f, lookY = 0.02f, glowAlpha = 0.18f,
    )
    BlobLifecycle.Blocked -> FaceSpec(
        // raised-brow "hmm?" — one tall pill, one dash
        gaze = HeadGaze(0f, 0f, 0f), split = 0.28f,
        left = eye(0.185f, 0.40f), right = eye(0.24f, 0.085f), wander = 0.05f,
        lookY = -0.03f, glowAlpha = 0.14f,
    )
    BlobLifecycle.Done -> {
        // happy squint ^^ — short, upturned mirrored tilt
        val (l, r) = mirror(0.22f, 0.115f, 20f)
        FaceSpec(gaze = HeadGaze(0f, 0f, 0f), split = 0.29f, left = l, right = r, wander = 0.08f, lookY = -0.02f, glowAlpha = 0.32f)
    }
}

internal fun blendEye(a: EyeCfg, b: EyeCfg, t: Float) = EyeCfg(
    w = lerp(a.w, b.w, t),
    h = lerp(a.h, b.h, t),
    tilt = lerp(a.tilt, b.tilt, t),
    open = lerp(a.open, b.open, t),
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
        out.add(AccentBeat(t, dur, kind % 5))
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
            0 -> face.copy(gaze = HeadGaze(face.gaze.yaw - 15f, face.gaze.pitch - 8f, face.gaze.roll + 6f))
            1 -> face.copy(gaze = HeadGaze(face.gaze.yaw + 13f, face.gaze.pitch - 5f, face.gaze.roll - 5f))
            2 -> face.copy(
                left = face.left.copy(w = face.left.w * 1.2f, h = face.left.h * 1.24f),
                right = face.right.copy(w = face.right.w * 1.2f, h = face.right.h * 1.24f),
            )
            3 -> face.copy(right = face.right.copy(h = face.right.h * 0.16f, w = face.right.w * 1.1f)) // wink
            else -> face.copy(
                left = face.left.copy(h = face.left.h * 0.42f, tilt = 10f),
                right = face.right.copy(h = face.right.h * 0.42f, tilt = -10f),
            )
        }
        BlobEyePack.Generical -> when (kind) {
            0 -> face.copy(lookX = face.lookX - 0.14f)
            1 -> face.copy(lookX = face.lookX + 0.14f)
            2 -> face.copy(lookY = face.lookY - 0.12f)
            3 -> face.copy(
                left = face.left.copy(h = face.left.h * 0.5f),
                right = face.right.copy(h = face.right.h * 0.5f),
            )
            else -> face.copy(right = face.right.copy(h = 0.085f, w = 0.24f)) // wink
        }
    }
    return blendFace(face, target, weight)
}
