package me.rerere.rikkahub.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.components.avatar.animated.AvatarPackData
import me.rerere.rikkahub.ui.components.avatar.animated.BodyPose
import me.rerere.rikkahub.ui.components.avatar.animated.MarkColors
import me.rerere.rikkahub.ui.components.avatar.animated.MarkShapes
import me.rerere.rikkahub.ui.components.avatar.animated.MotionFrame
import me.rerere.rikkahub.ui.components.avatar.animated.drawMark
import me.rerere.rikkahub.ui.components.avatar.animated.turnCapFor
import java.util.Random

/**
 * Renders a deterministic still frame of [Avatar.Animated] into a static [Bitmap]
 * for the Glance home-screen character widget using the real soft-3D mark engine.
 */
object WidgetAvatarRenderer {

    internal const val MIN_BITMAP_PX = 64
    internal const val DEFAULT_BITMAP_PX = 256
    internal const val MAX_BITMAP_PX = 512

    /**
     * Curated no-anger expression pool for widget still frames.
     * Excludes "Angry", "pouty", "Annoyed", "angered or concentrated", "Sad", and "Worried".
     */
    internal val NO_ANGER_FACES: List<String> = listOf(
        "Neutral",
        "Happy",
        "confused",
        "Surprised",
        "Bored",
        "hurt or cute",
        "Sleeping",
    )

    internal data class AttitudeTemplate(
        val yawFraction: Float,
        val pitchFraction: Float,
        val rollDeg: Float,
    )

    /**
     * Settled 3D attitudes expressed as fractions of the per-shape turn cap (within ±60%),
     * including one nearly straight pose so the solid reads as 3D while keeping a clean silhouette.
     */
    internal val SETTLED_ATTITUDES: List<AttitudeTemplate> = listOf(
        AttitudeTemplate(yawFraction = 0.05f, pitchFraction = -0.04f, rollDeg = 0.0f),   // nearly straight
        AttitudeTemplate(yawFraction = -0.44f, pitchFraction = 0.22f, rollDeg = -2.0f),  // settled left-down turn
        AttitudeTemplate(yawFraction = 0.46f, pitchFraction = 0.18f, rollDeg = 2.0f),   // settled right-down turn
        AttitudeTemplate(yawFraction = -0.34f, pitchFraction = -0.28f, rollDeg = 1.8f),  // curious left-up tilt
        AttitudeTemplate(yawFraction = 0.36f, pitchFraction = -0.26f, rollDeg = -1.8f), // curious right-up tilt
        AttitudeTemplate(yawFraction = -0.54f, pitchFraction = -0.06f, rollDeg = 2.5f),  // side glance left
        AttitudeTemplate(yawFraction = 0.52f, pitchFraction = 0.08f, rollDeg = -2.4f),  // side glance right
        AttitudeTemplate(yawFraction = 0.18f, pitchFraction = 0.38f, rollDeg = 1.4f),   // warm downward nod
    )

    private const val TURN_CAP_FRACTION_LIMIT = 0.60f
    private const val MAX_ROLL_DEG = 3.5f

    /**
     * Deterministically selects a face index in [NO_ANGER_FACES] for `(seed, variant)`
     * using `java.util.Random(seed.toLong() * 31 + step)` such that advancing `variant`
     * by 1 always changes the face index (never repeats the previous variant's face).
     */
    internal fun faceIndexFor(seed: Int, variant: Int): Int {
        val count = NO_ANGER_FACES.size
        val steps = Math.floorMod(variant, 256)
        var faceIdx = Random(seed.toLong() * 31L).nextInt(count)
        for (step in 1..steps) {
            val rng = Random(seed.toLong() * 31L + step)
            val pick = rng.nextInt(count)
            faceIdx = if (pick != faceIdx) {
                pick
            } else {
                (faceIdx + 1 + rng.nextInt(count - 1)) % count
            }
        }
        return faceIdx
    }

    /**
     * Returns the face name from [NO_ANGER_FACES] for `(seed, variant)`.
     */
    internal fun faceFor(seed: Int, variant: Int): String {
        return NO_ANGER_FACES[faceIndexFor(seed, variant)]
    }

    /**
     * Deterministically selects a 3D [BodyPose] for `(seed, variant)` within ±60% of
     * `turnCapFor(shapeId, solidKind)`.
     */
    internal fun attitudeFor(
        shapeId: String,
        solidKind: String,
        seed: Int,
        variant: Int,
    ): BodyPose {
        val attitudeCount = SETTLED_ATTITUDES.size
        val steps = Math.floorMod(variant, 256)
        val baseRng = Random(seed.toLong() * 31L)
        baseRng.nextInt(NO_ANGER_FACES.size) // consume face draw
        var attitudeIdx = baseRng.nextInt(attitudeCount)
        var stepRng = baseRng

        for (step in 1..steps) {
            stepRng = Random(seed.toLong() * 31L + step)
            val facePick = stepRng.nextInt(NO_ANGER_FACES.size)
            // Keep RNG stream aligned with faceIndexFor
            if (facePick < 0) stepRng.nextInt(NO_ANGER_FACES.size - 1)
            val pick = stepRng.nextInt(attitudeCount)
            attitudeIdx = if (pick != attitudeIdx) {
                pick
            } else {
                (attitudeIdx + 1 + stepRng.nextInt(attitudeCount - 1)) % attitudeCount
            }
        }

        val template = SETTLED_ATTITUDES[attitudeIdx]
        // Deterministic per-(seed, variant) micro-variation so distinct seeds never produce identical angles
        val jitterRng = Random(seed.toLong() * 31L + variant)
        jitterRng.nextInt(NO_ANGER_FACES.size)
        jitterRng.nextInt(attitudeCount)
        val yawJitter = (jitterRng.nextFloat() - 0.5f) * 0.08f
        val pitchJitter = (jitterRng.nextFloat() - 0.5f) * 0.08f
        val rollJitter = (jitterRng.nextFloat() - 0.5f) * 0.6f

        val (yawCap, pitchCap) = turnCapFor(shapeId, solidKind)
        val yawFrac = (template.yawFraction + yawJitter).coerceIn(-TURN_CAP_FRACTION_LIMIT, TURN_CAP_FRACTION_LIMIT)
        val pitchFrac = (template.pitchFraction + pitchJitter).coerceIn(-TURN_CAP_FRACTION_LIMIT, TURN_CAP_FRACTION_LIMIT)
        val rollDeg = (template.rollDeg + rollJitter).coerceIn(-MAX_ROLL_DEG, MAX_ROLL_DEG)

        return BodyPose(
            yaw = yawFrac * yawCap,
            pitch = pitchFrac * pitchCap,
            roll = rollDeg,
        )
    }

    /**
     * Renders a still frame of [avatar] into an `ARGB_8888` [Bitmap] of [sizePx] (clamped to
     * 64..512 px) with a transparent background, or returns `null` if assets cannot be loaded
     * or rendering fails.
     */
    fun render(
        context: Context,
        avatar: Avatar.Animated,
        seed: Int,
        sizePx: Int,
        variant: Int,
    ): Bitmap? {
        if (sizePx <= 0) return null
        return runCatching {
            AvatarPackData.ensureLoaded(context)
            if (!AvatarPackData.isLoaded()) return null

            val shapeId = MarkShapes.migrate(avatar.shape)
            val shape = MarkShapes.resolve(shapeId) ?: return null

            val body = MarkColors.bodyColor(avatar.colorHex, avatar.colorPreset, avatar.eyeType)
            val eye = MarkColors.eyeColor(avatar.eyeType, avatar.eyeColorHex, avatar.colorHex, avatar.colorPreset)
            val eyeFade = MarkColors.genericalEyeFade(body, eye, if (avatar.eyeShade) 1f else 0f)

            val face = faceFor(seed, variant)
            val bodyPose = attitudeFor(shapeId, shape.solid.kind, seed, variant)
            val frame = MotionFrame(
                body = bodyPose,
                exprFrom = face,
                exprTo = face,
                exprT = 1f,
            )

            val targetPx = sizePx.coerceIn(MIN_BITMAP_PX, MAX_BITMAP_PX)
            val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
            val bitmap = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
            val drawSize = Size(targetPx.toFloat(), targetPx.toFloat())

            CanvasDrawScope().draw(
                density = Density(density),
                layoutDirection = LayoutDirection.Ltr,
                canvas = Canvas(android.graphics.Canvas(bitmap)),
                size = drawSize,
            ) {
                drawMark(
                    shape = shape,
                    shapeId = shapeId,
                    isGrok = avatar.eyeType.equals("grok", ignoreCase = true),
                    f = frame,
                    calm = true,
                    morph = 0f,
                    body = body,
                    eye = eye,
                    eyeFade = eyeFade,
                )
            }
            bitmap
        }.getOrNull()
    }
}
