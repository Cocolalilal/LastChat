package me.rerere.rikkahub.ui.components.avatar

import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.data.model.BlobShape
import kotlin.math.abs
import kotlin.math.sin

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
    val wander: Float,
    val lookX: Float = 0f,
    val lookY: Float = 0f,
    val glowAlpha: Float = 0.55f,
)

internal data class BlobFrame(
    val radii: FloatArray,
    val cx: Float,
    val cy: Float,
    val breath: Float,
    val gaze: HeadGaze,
    val split: Float,
    val left: EyeCfg,
    val right: EyeCfg,
    val lid: Float,
    val lookX: Float,
    val lookY: Float,
    val glowAlpha: Float,
    val pack: BlobEyePack,
    val glowEnabled: Boolean,
    val colorHex: String,
    val glowHex: String,
    val accentHex: String,
    val glowStrength: Float,
    val eyeRoundness: Float,
    val squashX: Float,
    val squashY: Float,
    val highlightAngle: Float,
    val flatFill: Boolean,
)

internal const val BLOB_MORPH_SECONDS = 0.4f
internal const val BLOB_DONE_HOLD_SECONDS = 1.35f
internal const val BLOB_GLANCE_DECAY_SECONDS = 0.9f

internal fun faceFor(pack: BlobEyePack, lifecycle: BlobLifecycle): FaceSpec {
    return when (pack) {
        BlobEyePack.Grok -> grokFace(lifecycle)
        BlobEyePack.Generical -> genericalFace(lifecycle)
    }
}

/**
 * Grok pack: sphere-head poses from the measured bloub expressions / video
 * faces. Body stays the user's shape — we do not port silhouette circus
 * (3-dot thinking, comet, orbit, cookie).
 *
 * Idle = fitted rest gaze. Thinking = `curieux` (look down/aside, head roll).
 * Working = tamed `wide` (huge eyes, looking up). Waiting = `blase` slits.
 * Blocked = `colere` mirror tilts. Done = `heureux` squint arcs.
 */
private fun grokFace(lifecycle: BlobLifecycle): FaceSpec {
    fun eye(w: Float, h: Float, tilt: Float = 0f, open: Float = 1f) = EyeCfg(w, h, tilt, open)
    fun pair(w: Float, h: Float, tilt: Float = 0f, open: Float = 1f) =
        eye(w, h, tilt, open) to eye(w, h, -tilt, open)
    return when (lifecycle) {
        BlobLifecycle.Idle -> FaceSpec(
            gaze = REST_GAZE,
            split = EYE_SPLIT,
            left = eye(EYE_W, EYE_H),
            right = eye(EYE_W, EYE_H),
            wander = 1f,
        )
        BlobLifecycle.Thinking -> {
            FaceSpec(
                gaze = HeadGaze(yaw = 16f, pitch = -9f, roll = -15f),
                split = 16.5f,
                left = eye(0.24f, 0.46f, tilt = -8f),
                right = eye(0.20f, 0.38f, tilt = -8f),
                wander = 0.22f,
            )
        }
        BlobLifecycle.Working -> {
            FaceSpec(
                gaze = HeadGaze(yaw = 6.92f, pitch = -21.96f, roll = 11.6f),
                split = 18.43f,
                left = eye(0.32f, 0.64f),
                right = eye(0.32f, 0.64f),
                wander = 0.12f,
            )
        }
        BlobLifecycle.Waiting -> FaceSpec(
            gaze = HeadGaze(yaw = -22f, pitch = 2f, roll = 0f),
            split = 16f,
            left = eye(0.30f, 0.12f),
            right = eye(0.30f, 0.12f),
            wander = 0.12f,
        )
        BlobLifecycle.Blocked -> {
            val (l, r) = pair(0.34f, 0.15f, tilt = 30f)
            FaceSpec(
                gaze = HeadGaze(yaw = 3f, pitch = 7f, roll = 0f),
                split = 17f,
                left = l,
                right = r,
                wander = 0.06f,
            )
        }
        BlobLifecycle.Done -> {
            val (l, r) = pair(0.27f, 0.17f, tilt = 14f)
            FaceSpec(
                gaze = HeadGaze(yaw = 5f, pitch = 9f, roll = 0f),
                split = 17f,
                left = l,
                right = r,
                wander = 0.08f,
            )
        }
    }
}

/**
 * Generical pack: a flat pair of tall rounded rectangles, matching the
 * expression sheet. Idle is centred and untilted. Look-around translates the
 * pair; it does not rotate a sphere or paint Grok tilts.
 *
 * Eyes are glossy white lozenges — not fat glowing pills. Glow, if enabled,
 * is a soft body halo (see renderer), not a bloom on each eye.
 */
private fun genericalFace(lifecycle: BlobLifecycle): FaceSpec {
    fun eye(w: Float, h: Float, open: Float = 1f) = EyeCfg(w, h, tilt = 0f, open = open)
    return when (lifecycle) {
        BlobLifecycle.Idle -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.155f,
            left = eye(0.145f, 0.34f),
            right = eye(0.145f, 0.34f),
            wander = 1f,
            glowAlpha = 0.28f,
        )
        BlobLifecycle.Thinking -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.145f,
            left = eye(0.12f, 0.22f),
            right = eye(0.12f, 0.22f),
            wander = 0.18f,
            lookX = 0.05f,
            lookY = -0.13f,
            glowAlpha = 0.22f,
        )
        BlobLifecycle.Working -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.15f,
            left = eye(0.155f, 0.30f),
            right = eye(0.155f, 0.30f),
            wander = 0.12f,
            glowAlpha = 0.34f,
        )
        BlobLifecycle.Waiting -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.15f,
            left = eye(0.20f, 0.07f),
            right = eye(0.20f, 0.07f),
            wander = 0.08f,
            lookX = 0.11f,
            lookY = 0.03f,
            glowAlpha = 0.16f,
        )
        BlobLifecycle.Blocked -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.155f,
            left = eye(0.145f, 0.30f),
            right = eye(0.22f, 0.055f),
            wander = 0.04f,
            glowAlpha = 0.12f,
        )
        BlobLifecycle.Done -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.16f,
            left = eye(0.18f, 0.13f),
            right = eye(0.18f, 0.13f),
            wander = 0.06f,
            lookY = -0.02f,
            glowAlpha = 0.30f,
        )
    }
}

private fun blendEye(a: EyeCfg, b: EyeCfg, t: Float) = EyeCfg(
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

private fun blendRadii(a: FloatArray, b: FloatArray, t: Float): FloatArray {
    val n = minOf(a.size, b.size)
    val out = FloatArray(n)
    val k = clamp(t)
    for (i in 0 until n) out[i] = lerp(a[i], b[i], k)
    return out
}

private data class AccentBeat(val start: Float, val dur: Float, val kind: Int)

private val IDLE_ACCENTS: List<AccentBeat> = run {
    val rng = createRng(0xA11E)
    val out = ArrayList<AccentBeat>(180)
    var t = 3.2f
    var kind = 0
    while (t < 900f) {
        val dur = 0.55f + rng() * 0.75f
        out.add(AccentBeat(t, dur, kind % 5))
        kind++
        t += dur + 3.6f + rng() * 4.8f
    }
    out
}

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

private fun applyIdleAccent(pack: BlobEyePack, face: FaceSpec, kind: Int, weight: Float): FaceSpec {
    if (weight <= 0.001f) return face
    val target = when (pack) {
        BlobEyePack.Grok -> when (kind) {
            0 -> face.copy(
                gaze = HeadGaze(face.gaze.yaw - 14f, face.gaze.pitch - 8f, face.gaze.roll + 6f),
            )
            1 -> face.copy(
                gaze = HeadGaze(face.gaze.yaw + 12f, face.gaze.pitch - 5f, face.gaze.roll - 5f),
            )
            2 -> face.copy(
                left = face.left.copy(w = face.left.w * 1.18f, h = face.left.h * 1.22f),
                right = face.right.copy(w = face.right.w * 1.18f, h = face.right.h * 1.22f),
            )
            3 -> face.copy(
                right = face.right.copy(h = face.right.h * 0.18f, w = face.right.w * 1.08f),
            )
            else -> face.copy(
                left = face.left.copy(h = face.left.h * 0.42f, tilt = 10f),
                right = face.right.copy(h = face.right.h * 0.42f, tilt = -10f),
            )
        }
        BlobEyePack.Generical -> when (kind) {
            0 -> face.copy(lookX = face.lookX - 0.13f)
            1 -> face.copy(lookX = face.lookX + 0.13f)
            2 -> face.copy(lookY = face.lookY - 0.11f)
            3 -> face.copy(
                left = face.left.copy(h = face.left.h * 0.55f, w = face.left.w * 1.08f),
                right = face.right.copy(h = face.right.h * 0.55f, w = face.right.w * 1.08f),
            )
            else -> face.copy(
                right = face.right.copy(h = 0.055f, w = 0.22f),
            )
        }
    }
    return blendFace(face, target, weight)
}

private fun scaleEye(cfg: EyeCfg, size: Float) = cfg.copy(w = cfg.w * size, h = cfg.h * size)

/**
 * Stateful sampler. Time is injected ([tSeconds]) so tests are deterministic
 * and Compose owns the clock.
 */
internal class BlobRuntime {
    private var shownLifecycle: BlobLifecycle = BlobLifecycle.Idle
    private var fromFace: FaceSpec = faceFor(BlobEyePack.Generical, BlobLifecycle.Idle)
    private var toFace: FaceSpec = fromFace
    private var morphStart = 0f
    private var forceBlinkAt: Float? = null
    private var doneHoldUntil = 0f
    private var fromRadii: FloatArray = BlobShapes.radii(BlobShape.Circle)
    private var toRadii: FloatArray = fromRadii
    private var shapeMorphStart = 0f
    private var currentShape: BlobShape = BlobShape.Circle
    private var currentPack: BlobEyePack = BlobEyePack.Generical
    private var initialized = false

    fun sample(
        tSeconds: Float,
        spec: Avatar.Blob,
        requested: BlobLifecycle,
        reduceMotion: Boolean = false,
        glance: BlobGlance = BlobGlance(),
    ): BlobFrame {
        if (!initialized) {
            currentPack = spec.eyes
            currentShape = spec.shape
            fromFace = faceFor(spec.eyes, requested)
            toFace = fromFace
            fromRadii = BlobShapes.radii(spec.shape)
            toRadii = fromRadii
            shownLifecycle = requested
            initialized = true
        }

        if (spec.eyes != currentPack) {
            currentPack = spec.eyes
            fromFace = toFace
            toFace = faceFor(spec.eyes, shownLifecycle)
            morphStart = tSeconds
            forceBlinkAt = tSeconds
        }
        if (spec.shape != currentShape) {
            currentShape = spec.shape
            fromRadii = toRadii.copyOf()
            toRadii = BlobShapes.radii(spec.shape)
            shapeMorphStart = tSeconds
        }

        val effective = resolveLifecycle(tSeconds, requested)
        if (effective != shownLifecycle) {
            fromFace = blendFace(fromFace, toFace, easeOutQuint(morphProgress(tSeconds)))
            toFace = faceFor(spec.eyes, effective)
            morphStart = tSeconds
            forceBlinkAt = tSeconds
            shownLifecycle = effective
        }

        val morphT = easeOutQuint(morphProgress(tSeconds))
        var face = blendFace(fromFace, toFace, morphT)
        val shapeT = easeOutQuint(clamp((tSeconds - shapeMorphStart) / BLOB_MORPH_SECONDS))
        val baseRadii = if (shapeT >= 1f) toRadii else blendRadii(fromRadii, toRadii, shapeT)

        val lookAround = if (reduceMotion) spec.clampedLookAround() * 0.25f else spec.clampedLookAround()
        val eyeSize = spec.clampedEyeSize()
        val eyeSpacing = spec.clampedEyeSpacing()
        val isGrok = spec.eyes == BlobEyePack.Grok

        if (!reduceMotion && shownLifecycle == BlobLifecycle.Idle) {
            val (kind, env) = idleAccentEnvelope(tSeconds)
            face = applyIdleAccent(spec.eyes, face, kind, env)
        }

        val wander = if (reduceMotion) face.wander * 0.2f else face.wander
        val sphereWander = if (isGrok) wander * lookAround else 0f
        val live = liveliness(
            t = tSeconds,
            wander = sphereWander,
            blink = true,
            float = !reduceMotion,
        )
        val blinkOverride = forceBlinkAt?.let { start ->
            val k = (tSeconds - start) / BLINK_DUR
            if (k in 0f..1f) {
                if (k < 0.45f) 1f - k / 0.45f else (k - 0.45f) / 0.55f
            } else {
                if (k > 1f) forceBlinkAt = null
                null
            }
        }

        val glanceAmt = glance.strength.coerceIn(0f, 1f) * lookAround
        var yaw = face.gaze.yaw + live.dYaw
        var pitch = face.gaze.pitch + live.dPitch
        var roll = face.gaze.roll + live.dRoll
        if (isGrok && glanceAmt > 0f) {
            yaw += glance.x * glanceAmt * 16f
            pitch += glance.y * glanceAmt * 12f
        }

        when (shownLifecycle) {
            BlobLifecycle.Working -> {
                val pulse = sin(tSeconds * TAU / 1.15f)
                if (isGrok) {
                    yaw += pulse * 2.2f
                    pitch += sin(tSeconds * TAU / 0.92f) * 1.6f
                }
            }
            BlobLifecycle.Thinking -> {
                roll += sin(tSeconds * TAU / 2.6f) * 3.5f * lookAround
            }
            BlobLifecycle.Blocked -> {
                yaw += sin(tSeconds * 26f) * 2.4f
            }
            BlobLifecycle.Waiting -> {
                yaw += sin(tSeconds * TAU / 5.2f) * 4f * lookAround
            }
            else -> Unit
        }

        val gaze = HeadGaze(yaw = yaw, pitch = pitch, roll = roll)

        val genLook = if (isGrok) 0f else if (reduceMotion) 0.25f else lookAround
        var lookX = face.lookX + loopNoise(tSeconds, 11.3f, 0.4f) * 0.055f * face.wander * genLook
        var lookY = face.lookY + loopNoise(tSeconds, 9.1f, 1.3f) * 0.04f * face.wander * genLook
        if (!isGrok && glanceAmt > 0f) {
            lookX += glance.x * glanceAmt * 0.22f
            lookY += glance.y * glanceAmt * 0.18f
        }

        val spin = bodySpin(spec.shape, isGrok, shownLifecycle, tSeconds, gaze, reduceMotion)
        val radii = if (abs(spin) < 1e-4f) baseRadii else BlobShapes.rotated(baseRadii, spin)

        val squash = bodySquash(isGrok, shownLifecycle, gaze, tSeconds, reduceMotion)
        val breathMul = when (shownLifecycle) {
            BlobLifecycle.Working -> 1f + sin(tSeconds * TAU / 1.15f) * 0.012f
            BlobLifecycle.Thinking -> 1f + sin(tSeconds * TAU / 2.4f) * 0.008f
            BlobLifecycle.Blocked -> 1f + sin(tSeconds * TAU * 8f) * 0.005f
            else -> 1f
        }

        return BlobFrame(
            radii = radii,
            cx = live.driftX,
            cy = live.driftY,
            breath = live.breath * breathMul,
            gaze = gaze,
            split = face.split * eyeSpacing,
            left = scaleEye(face.left, eyeSize),
            right = scaleEye(face.right, eyeSize),
            lid = blinkOverride ?: live.lid,
            lookX = lookX,
            lookY = lookY,
            glowAlpha = face.glowAlpha * spec.clampedGlowStrength(),
            pack = spec.eyes,
            glowEnabled = spec.glowEnabled && spec.eyes == BlobEyePack.Generical,
            colorHex = spec.color,
            glowHex = spec.glowColor,
            accentHex = spec.accentColor,
            glowStrength = spec.clampedGlowStrength(),
            eyeRoundness = spec.clampedEyeRoundness(),
            squashX = squash.first,
            squashY = squash.second,
            highlightAngle = spin + tSeconds * 0.45f,
            flatFill = isGrok,
        )
    }

    internal fun shownLifecycle(): BlobLifecycle = shownLifecycle

    private fun morphProgress(tSeconds: Float): Float {
        return clamp((tSeconds - morphStart) / BLOB_MORPH_SECONDS)
    }

    private fun resolveLifecycle(tSeconds: Float, requested: BlobLifecycle): BlobLifecycle {
        if (requested == BlobLifecycle.Done) {
            doneHoldUntil = 0f
            return BlobLifecycle.Done
        }
        if (requested.isActiveWork()) {
            doneHoldUntil = 0f
            return requested
        }
        if (shownLifecycle.isActiveWork()) {
            doneHoldUntil = tSeconds + BLOB_DONE_HOLD_SECONDS
            return BlobLifecycle.Done
        }
        if (shownLifecycle == BlobLifecycle.Done && tSeconds < doneHoldUntil) {
            return BlobLifecycle.Done
        }
        return BlobLifecycle.Idle
    }
}

private fun bodySpin(
    shape: BlobShape,
    isGrok: Boolean,
    lifecycle: BlobLifecycle,
    t: Float,
    gaze: HeadGaze,
    reduceMotion: Boolean,
): Float {
    if (reduceMotion) return 0f
    val tumble = when (shape) {
        BlobShape.Cloud -> t * 0.62f
        BlobShape.Droplet -> t * 0.28f
        BlobShape.Cookie -> t * 0.22f
        BlobShape.Pebble, BlobShape.SoftHex, BlobShape.Triangle, BlobShape.Arch -> {
            if (isGrok) t * 0.12f else t * 0.04f
        }
        else -> 0f
    }
    val gazeSpin = if (isGrok) deg(gaze.yaw) * 0.12f else 0f
    val workSpin = if (lifecycle == BlobLifecycle.Working && isGrok) t * 0.18f else 0f
    return tumble + gazeSpin + workSpin
}

private fun bodySquash(
    isGrok: Boolean,
    lifecycle: BlobLifecycle,
    gaze: HeadGaze,
    t: Float,
    reduceMotion: Boolean,
): Pair<Float, Float> {
    if (!isGrok || reduceMotion) return 1f to 1f
    val sx = (1f - 0.055f * (abs(gaze.yaw) / 45f).coerceAtMost(1f)).coerceIn(0.88f, 1f)
    var sy = (1f - 0.045f * (abs(gaze.pitch) / 45f).coerceAtMost(1f)).coerceIn(0.88f, 1f)
    if (lifecycle == BlobLifecycle.Working) {
        sy *= 1f + sin(t * TAU / 1.15f) * 0.02f
    }
    if (lifecycle == BlobLifecycle.Done) {
        sy *= 0.97f
        return (sx * 1.03f) to sy
    }
    return sx to sy
}
