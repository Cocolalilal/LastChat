package me.rerere.rikkahub.ui.components.avatar

import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.data.model.BlobShape
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * One laid-out eye. Positions/sizes are in ball-radius units (renderer scales by
 * the pixel radius). [a],[b],[c],[d] are the sphere tangent matrix for Grok, or
 * identity for the flat Generical glyph.
 */
internal data class LaidEye(
    val cx: Float, val cy: Float,
    val a: Float, val b: Float, val c: Float, val d: Float,
    val hw: Float, val hh: Float,
    val tiltRad: Float,
    val open: Float,
    val visible: Boolean,
)

/** Everything a renderer needs for one frame. Pure data, no Android types. */
internal data class BlobFrame(
    val radii: FloatArray,
    val cx: Float,
    val cy: Float,
    val breath: Float,
    val squashX: Float,
    val squashY: Float,
    val left: LaidEye,
    val right: LaidEye,
    val lid: Float,
    val pack: BlobEyePack,
    val eyeRoundness: Float,
    val colorHex: String,
    val eyeHex: String,
    val accentHex: String,
    val glowHex: String,
    val glowEnabled: Boolean,
    val glowStrength: Float,
    val glowAlpha: Float,
    val flat3d: Boolean,
    /** key-light highlight centre, offset from body centre in radius units */
    val lightX: Float,
    val lightY: Float,
    val lightStrength: Float,
)

internal const val BLOB_MORPH_SECONDS = 0.4f
internal const val BLOB_DONE_HOLD_SECONDS = 1.4f
private const val GROK_FIT = 0.8f
private const val GROK_PAIR_LIMIT = 0.36f
private const val GEN_LOOK_LIMIT = 0.4f
private const val GEN_EYE_LIMIT = 0.6f

/**
 * Stateful sampler. Time is injected so Compose owns the clock and tests /
 * contact sheets are deterministic. [motion] scales all liveliness (drift,
 * blink, idle accents, light tumble): 1 = full, ~0.4 = reduce-motion, 0 = frozen
 * nominal pose (used for the contact sheets).
 */
internal class BlobRuntime(initialLifecycle: BlobLifecycle = BlobLifecycle.Idle) {
    private var shownLifecycle = initialLifecycle
    private var fromFace = faceFor(BlobEyePack.Generical, initialLifecycle)
    private var toFace = fromFace
    private var morphStart = 0f
    private var forceBlinkAt: Float? = null
    private var doneHoldUntil = 0f
    private var fromRadii = BlobShapes.radii(BlobShape.Circle)
    private var toRadii = fromRadii
    private var shapeMorphStart = 0f
    private var currentShape = BlobShape.Circle
    private var currentPack = BlobEyePack.Generical
    private var initialized = false

    fun shownLifecycle(): BlobLifecycle = shownLifecycle

    fun sample(
        tSeconds: Float,
        spec: Avatar.Blob,
        requested: BlobLifecycle,
        motion: Float = 1f,
    ): BlobFrame {
        val life = clamp(motion)
        if (!initialized) {
            currentPack = spec.eyes
            currentShape = spec.shape
            shownLifecycle = requested
            fromFace = faceFor(spec.eyes, requested)
            toFace = fromFace
            fromRadii = BlobShapes.radii(spec.shape)
            toRadii = fromRadii
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
        val radii = if (shapeT >= 1f) toRadii else blendRadii(fromRadii, toRadii, shapeT)

        val isGrok = spec.eyes == BlobEyePack.Grok
        val lookAround = spec.clampedLookAround() * life
        val eyeSize = spec.clampedEyeSize()
        val eyeSpacing = spec.clampedEyeSpacing()

        if (life > 0f && shownLifecycle == BlobLifecycle.Idle) {
            val (kind, env) = idleAccentEnvelope(tSeconds)
            face = applyIdleAccent(spec.eyes, face, kind, env * life)
        }

        val wander = face.wander * life
        val live = liveliness(
            t = tSeconds,
            wander = if (isGrok) wander * lookAround else 0f,
            blink = life > 0f,
            float = life > 0f,
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
        val lid = if (life <= 0f) 1f else (blinkOverride ?: live.lid)

        // Grok gaze = pose + drift + a little state-specific motion (never a body spin)
        var yaw = face.gaze.yaw + live.dYaw
        var pitch = face.gaze.pitch + live.dPitch
        var roll = face.gaze.roll + live.dRoll
        if (isGrok && life > 0f) {
            when (shownLifecycle) {
                BlobLifecycle.Thinking -> roll += sin(tSeconds * TAU / 2.6f) * 3.5f * lookAround
                BlobLifecycle.Working -> {
                    yaw += sin(tSeconds * TAU / 1.15f) * 2.2f
                    pitch += sin(tSeconds * TAU / 0.92f) * 1.6f
                }
                BlobLifecycle.Waiting -> yaw += sin(tSeconds * TAU / 5.2f) * 4f * lookAround
                BlobLifecycle.Blocked -> yaw += sin(tSeconds * 26f) * 2.2f
                else -> Unit
            }
        }
        val gaze = HeadGaze(yaw, pitch, roll)

        // Generical flat gaze translation
        val genLook = if (isGrok) 0f else lookAround
        var lookX = face.lookX + loopNoise(tSeconds, 11.3f, 0.4f) * 0.055f * face.wander * genLook
        var lookY = face.lookY + loopNoise(tSeconds, 9.1f, 1.3f) * 0.04f * face.wander * genLook

        val driftX = live.driftX
        val driftY = live.driftY
        val (left, right) = if (isGrok) {
            grokEyes(gaze, face, radii, eyeSize, eyeSpacing, driftX, driftY)
        } else {
            genericalEyes(face, radii, eyeSize, eyeSpacing, lookX, lookY, driftX, driftY)
        }

        val breathMul = if (life <= 0f) 1f else when (shownLifecycle) {
            BlobLifecycle.Working -> 1f + sin(tSeconds * TAU / 1.15f) * 0.012f
            BlobLifecycle.Thinking -> 1f + sin(tSeconds * TAU / 2.4f) * 0.008f
            BlobLifecycle.Blocked -> 1f + sin(tSeconds * TAU * 6f) * 0.004f
            else -> 1f
        }

        // Subtle 3-D: a key light offset toward the top-left that slowly drifts
        // (a lighting "tumble"), NOT a silhouette rotation.
        val organic = spec.shape == BlobShape.Cloud || spec.shape == BlobShape.Droplet || spec.shape == BlobShape.Pebble
        val tumbleSpeed = when {
            !isActiveState(shownLifecycle) && organic -> 0.12f
            isActiveState(shownLifecycle) -> 0.35f
            else -> 0.06f
        }
        val baseAngle = -2.25f
        val lightAngle = baseAngle + loopNoise(tSeconds, 9f, 0.6f) * 0.35f * life + tSeconds * tumbleSpeed * life * 0.15f
        val lightR = 0.34f
        val lightX = cos(lightAngle) * lightR
        val lightY = sin(lightAngle) * lightR

        return BlobFrame(
            radii = radii,
            cx = driftX,
            cy = driftY,
            breath = live.breath * breathMul,
            squashX = 1f,
            squashY = 1f,
            left = left,
            right = right,
            lid = lid,
            pack = spec.eyes,
            eyeRoundness = spec.clampedEyeRoundness(),
            colorHex = spec.color,
            eyeHex = spec.eyeColor,
            accentHex = spec.accentColor,
            glowHex = spec.glowColor,
            glowEnabled = spec.glowEnabled,
            glowStrength = spec.clampedGlowStrength(),
            glowAlpha = face.glowAlpha,
            flat3d = spec.flat3d,
            lightX = lightX,
            lightY = lightY,
            lightStrength = if (spec.flat3d) 1f else 0f,
        )
    }

    private fun morphProgress(t: Float) = clamp((t - morphStart) / BLOB_MORPH_SECONDS)

    private fun resolveLifecycle(t: Float, requested: BlobLifecycle): BlobLifecycle {
        if (requested == BlobLifecycle.Done) {
            doneHoldUntil = 0f
            return BlobLifecycle.Done
        }
        if (requested.isActiveWork()) {
            doneHoldUntil = 0f
            return requested
        }
        if (shownLifecycle.isActiveWork()) {
            doneHoldUntil = t + BLOB_DONE_HOLD_SECONDS
            return BlobLifecycle.Done
        }
        if (shownLifecycle == BlobLifecycle.Done && t < doneHoldUntil) return BlobLifecycle.Done
        return BlobLifecycle.Idle
    }
}

private fun isActiveState(l: BlobLifecycle) =
    l == BlobLifecycle.Thinking || l == BlobLifecycle.Working

private fun grokEyes(
    gaze: HeadGaze,
    face: FaceSpec,
    radii: FloatArray,
    eyeSize: Float,
    eyeSpacing: Float,
    driftX: Float,
    driftY: Float,
): Pair<LaidEye, LaidEye> {
    val (lp, rp) = eyePoses(gaze, 1f, face.split * eyeSpacing)
    var left = grokEye(lp, face.left, radii, eyeSize, driftX, driftY)
    var right = grokEye(rp, face.right, radii, eyeSize, driftX, driftY)
    // keep the pair's midpoint inside the body so organic rims never eat an eye
    val mx = (left.cx + right.cx) * 0.5f
    val my = (left.cy + right.cy) * 0.5f
    val d = hypot(mx, my)
    if (d > GROK_PAIR_LIMIT && d > 1e-5f) {
        val s = (d - GROK_PAIR_LIMIT) / d
        left = left.copy(cx = left.cx - mx * s, cy = left.cy - my * s)
        right = right.copy(cx = right.cx - mx * s, cy = right.cy - my * s)
    }
    return left to right
}

private fun grokEye(
    pose: EyePose,
    cfg: EyeCfg,
    radii: FloatArray,
    eyeSize: Float,
    driftX: Float,
    driftY: Float,
): LaidEye {
    val fit = BlobShapes.radiusAtAngle(radii, atan2(pose.y, pose.x)) * GROK_FIT
    return LaidEye(
        cx = pose.x * fit + driftX,
        cy = pose.y * fit + driftY,
        a = pose.a, b = pose.b, c = pose.c, d = pose.d,
        hw = cfg.w * eyeSize / 2f,
        hh = cfg.h * eyeSize / 2f,
        tiltRad = deg(cfg.tilt),
        open = cfg.open,
        visible = pose.depth > 0.02f,
    )
}

private fun genericalEyes(
    face: FaceSpec,
    radii: FloatArray,
    eyeSize: Float,
    eyeSpacing: Float,
    lookX: Float,
    lookY: Float,
    driftX: Float,
    driftY: Float,
): Pair<LaidEye, LaidEye> {
    val (mx, my) = nearestInside(lookX, lookY, radii, GEN_LOOK_LIMIT)
    fun one(side: Float, cfg: EyeCfg): LaidEye {
        val (x, y) = nearestInside(side * face.split * eyeSpacing + mx, my, radii, GEN_EYE_LIMIT)
        return LaidEye(
            cx = x + driftX, cy = y + driftY,
            a = 1f, b = 0f, c = 0f, d = 1f,
            hw = cfg.w * eyeSize / 2f,
            hh = cfg.h * eyeSize / 2f,
            tiltRad = deg(cfg.tilt),
            open = cfg.open,
            visible = true,
        )
    }
    return one(-1f, face.left) to one(1f, face.right)
}

private fun blendRadii(a: FloatArray, b: FloatArray, t: Float): FloatArray {
    val n = minOf(a.size, b.size)
    val out = FloatArray(n)
    val k = clamp(t)
    for (i in 0 until n) out[i] = lerp(a[i], b[i], k)
    return out
}

/** True once the pair's centre stays inside the body (used only by tests). */
internal fun BlobFrame.eyePairInside(): Boolean {
    val mx = (left.cx + right.cx) * 0.5f
    val my = (left.cy + right.cy) * 0.5f
    return hypot(mx, my) <= GROK_PAIR_LIMIT + 1e-3f
}
