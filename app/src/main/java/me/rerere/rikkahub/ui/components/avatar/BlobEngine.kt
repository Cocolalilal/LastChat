package me.rerere.rikkahub.ui.components.avatar

import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.BlobEyePack
import me.rerere.rikkahub.data.model.BlobShape

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

internal class BlobFrame(
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
)

internal const val BLOB_MORPH_SECONDS = 0.4f
internal const val BLOB_DONE_HOLD_SECONDS = 1.35f

internal fun faceFor(pack: BlobEyePack, lifecycle: BlobLifecycle): FaceSpec {
    return when (pack) {
        BlobEyePack.Grok -> grokFace(lifecycle)
        BlobEyePack.Generical -> genericalFace(lifecycle)
    }
}

/**
 * Grok pack: sphere-head poses. Body stays the user's shape — we do not port
 * bloub's 14-state silhouette morphs (3-dot thinking, comet, orbit, cookie).
 *
 * Informal mapping onto the xAI lifecycle:
 * Idle = rest gaze; Thinking = look up / attentive; Working = wider / kicked in;
 * Waiting = aside + droopy; Blocked = angry mirror tilts; Done = happy squint.
 */
private fun grokFace(lifecycle: BlobLifecycle): FaceSpec {
    fun pair(w: Float, h: Float, tilt: Float = 0f, open: Float = 1f) = EyeCfg(w, h, tilt, open)
    return when (lifecycle) {
        BlobLifecycle.Idle -> FaceSpec(
            gaze = REST_GAZE,
            split = EYE_SPLIT,
            left = pair(EYE_W, EYE_H),
            right = pair(EYE_W, EYE_H),
            wander = 1f,
        )
        BlobLifecycle.Thinking -> FaceSpec(
            gaze = HeadGaze(yaw = 4f, pitch = 18f, roll = -4f),
            split = 16f,
            left = pair(0.21f, 0.44f),
            right = pair(0.21f, 0.44f),
            wander = 0.35f,
        )
        BlobLifecycle.Working -> FaceSpec(
            gaze = HeadGaze(yaw = 6.92f, pitch = -8f, roll = 6f),
            split = 18f,
            left = pair(0.28f, 0.52f),
            right = pair(0.28f, 0.52f),
            wander = 0.2f,
        )
        BlobLifecycle.Waiting -> FaceSpec(
            gaze = HeadGaze(yaw = -18f, pitch = 2f, roll = 0f),
            split = 16f,
            left = pair(0.28f, 0.12f),
            right = pair(0.28f, 0.12f),
            wander = 0.15f,
        )
        BlobLifecycle.Blocked -> FaceSpec(
            gaze = HeadGaze(yaw = 3f, pitch = 7f, roll = 0f),
            split = 17f,
            left = pair(0.34f, 0.15f, tilt = 30f),
            right = pair(0.34f, 0.15f, tilt = -30f),
            wander = 0.08f,
        )
        BlobLifecycle.Done -> FaceSpec(
            gaze = HeadGaze(yaw = 5f, pitch = 9f, roll = 0f),
            split = 17f,
            left = pair(0.27f, 0.17f, tilt = 14f),
            right = pair(0.27f, 0.17f, tilt = -14f),
            wander = 0.1f,
        )
    }
}

/**
 * Generical pack: a flat pair of tall rounded rectangles inside a fixed circle.
 * Idle is centred and untilted. Look-around translates the pair; it does not
 * rotate a sphere. Head-turn is the same translation, clamped to the body.
 */
private fun genericalFace(lifecycle: BlobLifecycle): FaceSpec {
    fun pair(w: Float, h: Float, tilt: Float = 0f) = EyeCfg(w, h, tilt, 1f)
    return when (lifecycle) {
        BlobLifecycle.Idle -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.18f,
            left = pair(0.22f, 0.36f),
            right = pair(0.22f, 0.36f),
            wander = 1f,
            glowAlpha = 0.55f,
        )
        BlobLifecycle.Thinking -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.17f,
            left = pair(0.18f, 0.28f),
            right = pair(0.18f, 0.28f),
            wander = 0.25f,
            lookY = -0.10f,
            glowAlpha = 0.70f,
        )
        BlobLifecycle.Working -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.175f,
            left = pair(0.20f, 0.40f),
            right = pair(0.20f, 0.40f),
            wander = 0.15f,
            glowAlpha = 0.85f,
        )
        BlobLifecycle.Waiting -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.16f,
            left = pair(0.28f, 0.07f),
            right = pair(0.28f, 0.07f),
            wander = 0.1f,
            lookY = 0.04f,
            glowAlpha = 0.40f,
        )
        BlobLifecycle.Blocked -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.17f,
            left = pair(0.20f, 0.22f, tilt = 28f),
            right = pair(0.20f, 0.22f, tilt = -28f),
            wander = 0.05f,
            glowAlpha = 0.25f,
        )
        BlobLifecycle.Done -> FaceSpec(
            gaze = HeadGaze(0f, 0f, 0f),
            split = 0.175f,
            left = pair(0.24f, 0.14f, tilt = 12f),
            right = pair(0.24f, 0.14f, tilt = -12f),
            wander = 0.08f,
            lookY = -0.02f,
            glowAlpha = 0.75f,
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
        val face = blendFace(fromFace, toFace, morphT)
        val shapeT = easeOutQuint(clamp((tSeconds - shapeMorphStart) / BLOB_MORPH_SECONDS))
        val radii = if (shapeT >= 1f) toRadii else blendRadii(fromRadii, toRadii, shapeT)

        val wander = if (reduceMotion) face.wander * 0.2f else face.wander
        val sphereWander = if (spec.eyes == BlobEyePack.Grok) wander else 0f
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

        val gaze = HeadGaze(
            yaw = face.gaze.yaw + live.dYaw,
            pitch = face.gaze.pitch + live.dPitch,
            roll = face.gaze.roll + live.dRoll,
        )

        val lookScale = if (spec.eyes == BlobEyePack.Generical) {
            if (reduceMotion) 0.25f else 1f
        } else {
            0f
        }
        val lookX = face.lookX + loopNoise(tSeconds, 11.3f, 0.4f) * 0.08f * face.wander * lookScale
        val lookY = face.lookY + loopNoise(tSeconds, 9.1f, 1.3f) * 0.06f * face.wander * lookScale

        return BlobFrame(
            radii = radii,
            cx = live.driftX,
            cy = live.driftY,
            breath = live.breath,
            gaze = gaze,
            split = face.split,
            left = face.left,
            right = face.right,
            lid = blinkOverride ?: live.lid,
            lookX = lookX,
            lookY = lookY,
            glowAlpha = face.glowAlpha,
            pack = spec.eyes,
            glowEnabled = spec.glowEnabled && spec.eyes == BlobEyePack.Generical,
            colorHex = spec.color,
            glowHex = spec.glowColor,
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
        // requested is Idle
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
