package me.rerere.rikkahub.ui.components.avatar

import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.data.model.BlobShape
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * One laid-out eye. Positions/sizes are in ball-radius units (renderer scales by
 * the pixel radius). Both packs are flat glyphs on the mark; [a],[b],[c],[d]
 * stay identity and [tiltRad] carries any lean.
 */
internal data class LaidEye(
    val cx: Float, val cy: Float,
    val a: Float, val b: Float, val c: Float, val d: Float,
    val hw: Float, val hh: Float,
    val tiltRad: Float,
    val open: Float,
    val visible: Boolean,
    val topRound: Float = 1f,
    val bottomRound: Float = 1f,
    val innerTopDrop: Float = 0f,
    val outerTopDrop: Float = 0f,
    val innerSign: Float = 1f,
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
private const val LOOK_LIMIT = 0.52f
private const val EYE_LIMIT = 0.72f
private const val PAIR_MID_LIMIT = 0.55f

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
            wander = 0f,
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

        // Both packs look by translating the pair on the flat mark (never a body spin).
        var lookX = face.lookX + loopNoise(tSeconds, 11.3f, 0.4f) * 0.055f * wander * lookAround
        var lookY = face.lookY + loopNoise(tSeconds, 9.1f, 1.3f) * 0.04f * wander * lookAround
        if (life > 0f) {
            when (shownLifecycle) {
                BlobLifecycle.Thinking -> lookX += sin(tSeconds * TAU / 2.6f) * 0.035f * lookAround
                BlobLifecycle.Working -> lookY += sin(tSeconds * TAU / 1.15f) * 0.018f
                BlobLifecycle.Waiting -> lookX += sin(tSeconds * TAU / 5.2f) * 0.045f * lookAround
                BlobLifecycle.Blocked -> lookX += sin(tSeconds * 26f) * 0.012f
                else -> Unit
            }
        }

        val driftX = live.driftX
        val driftY = live.driftY
        val (left, right) = layFlatEyes(face, radii, eyeSize, eyeSpacing, lookX, lookY, driftX, driftY)

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

private fun layFlatEyes(
    face: FaceSpec,
    radii: FloatArray,
    eyeSize: Float,
    eyeSpacing: Float,
    lookX: Float,
    lookY: Float,
    driftX: Float,
    driftY: Float,
): Pair<LaidEye, LaidEye> {
    val (mx, my) = nearestInside(lookX, lookY, radii, LOOK_LIMIT)
    fun one(side: Float, cfg: EyeCfg): LaidEye {
        val (x, y) = nearestInside(
            side * face.split * eyeSpacing + mx + cfg.ox,
            my + cfg.oy,
            radii,
            EYE_LIMIT,
        )
        return LaidEye(
            cx = x + driftX, cy = y + driftY,
            a = 1f, b = 0f, c = 0f, d = 1f,
            hw = cfg.w * eyeSize / 2f,
            hh = cfg.h * eyeSize / 2f,
            tiltRad = deg(cfg.tilt),
            open = cfg.open,
            visible = true,
            topRound = cfg.topRound,
            bottomRound = cfg.bottomRound,
            innerTopDrop = cfg.innerTopDrop,
            outerTopDrop = cfg.outerTopDrop,
            innerSign = if (side < 0f) 1f else -1f,
        )
    }
    return one(-1f, face.left) to one(1f, face.right)
}

/** Frozen frame from an authored face (contact sheets of Julian's expression poses). */
internal fun frozenFaceFrame(spec: Avatar.Blob, face: FaceSpec): BlobFrame {
    val radii = BlobShapes.radii(spec.shape)
    val (left, right) = layFlatEyes(
        face, radii, spec.clampedEyeSize(), spec.clampedEyeSpacing(),
        face.lookX, face.lookY, 0f, 0f,
    )
    return BlobFrame(
        radii = radii,
        cx = 0f,
        cy = 0f,
        breath = 1f,
        squashX = 1f,
        squashY = 1f,
        left = left,
        right = right,
        lid = 1f,
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
        lightX = -0.24f,
        lightY = -0.24f,
        lightStrength = if (spec.flat3d) 1f else 0f,
    )
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
    return hypot(mx, my) <= PAIR_MID_LIMIT + 1e-3f
}
