package me.rerere.rikkahub.ui.components.avatar.animated

import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin

/**
 * Desktop Grok Bot 0.68.1 eye seating — full `Hw`/`Vw` + `sx`/`ox`/`ix`/`ax`/`cx`.
 *
 * Qc contour rings → Hw scale → seat branch → euler project via WhMesh.xhHe.
 */
object WhEyeSeat {

    private const val Dw = 0.26f
    private const val Qw = 0.57f
    private const val Mn = 0.64f

    data class FaceTune(
        val width: Float,
        val height: Float,
        val gap: Float,
        val shiftY: Float,
    )

    /** Desktop `Hw` — full table from eager-platform. */
    private val HW: Map<String, FaceTune> = mapOf(
        "cloud" to FaceTune(0.21294f, 0.46683f, 0.470016f, 0.07f),
        "square" to FaceTune(0.25532584f, 0.5597528f, 0.529408f, 0.12f),
        "sparkle" to FaceTune(0.18382f, 0.40299f, 0.41216f, 0f),
        "clover" to FaceTune(0.23296f, 0.51072f, 0.559104f, -0.02f),
        "heartChubby" to FaceTune(0.186576f, 0.409032f, 0.446208f, 0.052f),
        "starFlower" to FaceTune(0.19292f, 0.42294f, 0.382976f, 0.12f),
        "teardrop" to FaceTune(0.207792f, 0.455544f, 0.452608f, 0.18f),
        "tablet" to FaceTune(0.2132f, 0.4674f, 0.587776f, 0.02f),
        "wedge" to FaceTune(0.2132f, 0.4674f, 0.467712f, 0.18f),
        "house" to FaceTune(0.2295124f, 0.5031618f, 0.512512f, 0.18499534f),
        "star6" to FaceTune(0.184782f, 0.44965989f, 0.41913344f, 0.12f),
        "pebble" to FaceTune(0.2392f, 0.5244f, 0.546304f, 0.12f),
        "bean" to FaceTune(0.2028f, 0.4446f, 0.4608f, 0.12f),
        "egg" to FaceTune(0.2392f, 0.5244f, 0.433664f, 0.11299907f),
        "squircle" to FaceTune(0.2392f, 0.5244f, 0.5632f, 0.12f),
        "capsule" to FaceTune(0.2106f, 0.4617f, 0.360448f, 0.12f),
        "cylinder" to FaceTune(0.2392f, 0.5244f, 0.47872f, 0.12f),
        "hex" to FaceTune(0.26f, 0.57f, 0.64f, 0.12f),
        "gem" to FaceTune(0.2392f, 0.5244f, 0.501248f, 0.12f),
        "crystal" to FaceTune(0.1612f, 0.3534f, 0.3712f, 0.12f),
        "shield" to FaceTune(0.267904f, 0.587328f, 0.444928f, 0.12f),
        "dome" to FaceTune(0.2132f, 0.4674f, 0.47872f, 0.10599814f),
        "arch" to FaceTune(0.1924f, 0.4218f, 0.448f, 0.16f),
        "leaf" to FaceTune(0.1872f, 0.4104f, 0.448f, 0.04f),
    )

    fun engineKey(pickerId: String): String = when (pickerId) {
        "blob" -> "pebble"
        "heart" -> "heartChubby"
        "flower" -> "starFlower"
        "star" -> "star6"
        else -> pickerId
    }

    fun faceTune(pickerId: String): FaceTune =
        HW[engineKey(pickerId)] ?: HW.getValue("pebble")

    fun eyeFit(pickerId: String): Triple<Float, Float, Float> {
        val t = faceTune(pickerId)
        return Triple(t.width / Dw, t.height / Qw, t.gap / Mn)
    }

    fun genericalFitScale(pickerId: String): Float {
        val (sx, sy, _) = eyeFit(pickerId)
        // Slightly smaller so SVG eyes seat inside skinny silhouettes.
        return ((sx + sy) * 0.5f * 0.92f).coerceIn(0.5f, 0.98f)
    }

    /**
     * Generical interocular scale (1 = SVG Neutral spacing).
     *
     * Spacing is tied to eye size so the clear space between the eyes stays the
     * same fraction of eye width on every shape (≈0.7, as on the approved circle).
     * Previously gap and fit were tuned independently: square/hex/cloud/house eyes
     * ended up nearly touching while the circle looked right.
     *   space/eyeW = (166·gap − 113·fit) / (113·fit) ≈ 0.68  ⇒  gap ≈ 1.15·fit
     */
    fun genericalGapScale(pickerId: String): Float {
        if (pickerId == "blob") return 1.0f
        return (genericalFitScale(pickerId) * 1.15f).coerceIn(0.78f, 1.05f)
    }

    /**
     * He-centered evenodd eye paths via full sx seat branches.
     */
    fun buildSeatedGrokEyes(
        pickerId: String,
        solidKind: String,
        he: Float,
        gazeX: Float,
        gazeY: Float,
        lid: Float,
        eyeGap: Float,
        eyeScale: Float,
        timeSec: Float,
        awake: Float,
        yawDeg: Float,
        pitchDeg: Float,
        rollDeg: Float,
        shape: GrokShape? = null,
        bodyPath: Path? = null,
        expressionId: String = "neutral",
        /** Pre-blended capsule pair (expression morph, lids applied). */
        eyesOverride: List<WhSphereEyes.EyeState>? = null,
        /** Eyes lead the head on the surface (normalized face units, y down). */
        leadX: Float = 0f,
        leadY: Float = 0f,
    ): Pair<Path, Path> {
        val empty = Path() to Path()
        if (!AvatarPackData.isLoaded()) return empty

        if (solidKind == "sphere" && eyesOverride != null) {
            // Lead = the eyes rolling a little further over the sphere than the head.
            val leadYaw = Math.toDegrees(kotlin.math.asin(leadX.coerceIn(-0.3f, 0.3f)).toDouble()).toFloat()
            val leadPitch = Math.toDegrees(kotlin.math.asin(leadY.coerceIn(-0.3f, 0.3f)).toDouble()).toFloat()
            return WhSphereEyes.projectPair(eyesOverride, pitchDeg + leadPitch, yawDeg + leadYaw, rollDeg, he)
        }

        // Sphere Idle path: desktop Dh/SS (NOT mesh sx). Look-around = euler.
        if (solidKind == "sphere") {
            // Never Grok brand inspecting / looking-to-the-right on sphere.
            val raw = expressionId.lowercase().trim()
            val expr = when {
                lid < 0.08f -> "sleepy"
                raw in setOf(
                    "inspecting", "looking to the right", "looking", "lookright",
                    "looking_right",
                ) -> "neutral"
                else -> expressionId
            }
            // Gaze is euler (caller adds Ou) — not 2D translate.
            @Suppress("UNUSED_VARIABLE") val _g = gazeX + gazeY + timeSec + awake + eyeGap
            val eyes = WhSphereEyes.expressionPair(expr, lid * eyeScale.coerceIn(0.5f, 1.2f))
            return WhSphereEyes.projectPair(eyes, pitchDeg, yawDeg, rollDeg, he)
        }

        val tune = faceTune(pickerId)
        val sxTune = (tune.width / Dw).coerceIn(0.55f, 1.15f)
        val syTune = (tune.height / Qw).coerceIn(0.55f, 1.15f)
        // Desktop Hw.shiftY lowers the face on roofed/pointy silhouettes; keep mild so
        // the face stays visually centred and looking straight ahead.
        val shiftY = tune.shiftY * 0.35f

        val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
        val pitch = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val roll = Math.toRadians(rollDeg.toDouble()).toFloat()
        val widen = if (lid < 0.45f) EyeStyle.lerp(1.2f, 1f, lid / 0.45f) else 1f

        val path = bodyPath ?: shape?.let { AvatarPackData.parseSvgPath(it.pathData).let { raw ->
            Path().apply { addPath(raw, androidx.compose.ui.geometry.Offset(-he, -he)) }
        } }
        val gShape = shape ?: MarkShapes.resolve(pickerId)
        val seat = if (gShape != null && path != null) {
            GenericalSeat.seatFor(gShape, he, path)
        } else {
            fallbackSeat(solidKind)
        }

        // Upright capsule loops (normalized, y-up, face centre = 0,0).
        // NOT Qc pair 0: those rings are the Grok brand pose — centroid up-right of
        // the face and slanted ~63°, which is exactly the rejected "up-right stare". Same desktop En(ur,.57) capsule the sphere path seats.
        val expr = upright(expressionId, lid)
        val eyes = eyesOverride ?: WhSphereEyes.expressionPair(expr, lid)
        val halfGap = (tune.gap / 2f) * eyeGap.coerceIn(0.7f, 1.2f)
        fun loopNorm(side: Int): List<WhMesh.V2> {
            val e = eyes.getOrNull(side) ?: return emptyList()
            val outline = WhSphereEyes.stadiumOutline(e)
            if (outline.isEmpty()) return emptyList()
            val sideSign = if (side == 0) -1f else 1f
            val l = cos(e.tilt); val d = sin(e.tilt)
            // Tiny awake micro-life only (≪ seating motion).
            val driftX = sin(timeSec * 0.42f + sideSign) * 0.004f * awake
            val driftY = sin(timeSec * 0.58f + sideSign) * 0.003f * awake
            return outline.map { pt ->
                val g = pt[0]
                val w = pt[1] * e.lidY
                val sLocal = e.offX + l * g + d * w
                val v = e.offY - d * g + l * w
                // Left eye local +x points outward (desktop J1[0].C = -x) → mirror.
                WhMesh.V2(
                    sideSign * (halfGap + sLocal * sxTune * eyeScale * widen) + driftX + leadX,
                    v * syTune + driftY - leadY,
                )
            }
        }
        @Suppress("UNUSED_VARIABLE") val _g = gazeX + gazeY

        val seated = WhMesh.seatEyePolygons(
            seat = seat,
            loopsNorm = listOf(loopNorm(0), loopNorm(1)),
            shiftY = shiftY,
            yaw = yaw,
            pitch = pitch,
            roll = roll,
            he = he,
        )
        val left = seated.getOrNull(0) ?: Path()
        val right = seated.getOrNull(1) ?: Path()
        return left to right
    }

    /** Never the Grok brand inspecting / looking-right pose; closed lids → sleepy. */
    private fun upright(expressionId: String, lid: Float): String {
        val raw = expressionId.lowercase().trim()
        return when {
            lid < 0.08f -> "sleepy"
            raw in setOf("inspecting", "looking to the right", "looking", "lookright", "looking_right") -> "neutral"
            else -> expressionId
        }
    }

    private fun fallbackSeat(solidKind: String): WhMesh.Seat =
        when (solidKind) {
            "loft" -> WhMesh.Seat.Front(0.36f)
            "extrusion", "roundedSlab" -> WhMesh.Seat.Front(0.36f)
            "pill" -> WhMesh.Seat.Front(0.55f)
            "sphere" -> WhSphere.eyeSeat()
            "crystal" -> WhMesh.Seat.Front(0.64f * (kotlin.math.sqrt(3f) / 2f))
            else -> WhMesh.Seat.Front(0.5f)
        }
}

