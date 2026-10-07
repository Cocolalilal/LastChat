package me.rerere.rikkahub.ui.components.avatar.animated

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ViewBox(val minX: Float, val minY: Float, val width: Float, val height: Float)

data class GrokShape(
    val id: String,
    val pathData: String,
    val scale: Float,
    val solid: SolidSpec,
    val beltRadius: Float? = null,
    val top: Float? = null,
    val bottom: Float? = null,
)

data class GrokGeometry(
    val headCenter: Float,
    val viewBox: ViewBox,
    val shapes: Map<String, GrokShape>,
    val pickerShapeIds: List<String>,
    val gradients: Map<String, PersonaGradient>,
) {
    val shapeIds: List<String> get() = pickerShapeIds.ifEmpty { shapes.keys.toList() }
}

data class PersonaGradient(val light: String, val dark: String)

data class GenericalEye(
    val kind: String,
    val pathData: String?,
    val x: Float?,
    val y: Float?,
    val width: Float?,
    val height: Float?,
    val rx: Float?,
    val rotateAngle: Float?,
    val rotateCx: Float?,
    val rotateCy: Float?,
    val strokeWidth: Float,
    val gradientFrom: Color,
    val gradientTo: Color,
    val gradX1: Float,
    val gradY1: Float,
    val gradX2: Float,
    val gradY2: Float,
)

data class GenericalExpression(
    val id: String,
    val left: GenericalEye,
    val right: GenericalEye,
    val bodyFill: String,
)

data class GenericalEyesPack(
    val viewBox: ViewBox,
    val expressions: Map<String, GenericalExpression>,
)

/**
 * Loads Grok Bot desktop 0.68.1 carved assets:
 * shapes068 (C_ picker 13 + solid specs), clips068, eyes068 (Qc rings), generical-eyes.
 */
object AvatarPackData {
    private const val SHAPES_ASSET = "avatar/shapes068.json"
    private const val CLIPS_ASSET = "avatar/clips068.json"
    private const val EYES_ASSET = "avatar/eyes068.json"
    private const val GENERICAL_ASSET = "avatar/generical-eyes.json"
    private const val COLORS_ASSET = "avatar/colors068.json"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var loaded = false

    lateinit var grok: GrokGeometry
        private set
    lateinit var generical: GenericalEyesPack
        private set

    /** Clip name → MarkClip */
    var clips: Map<String, MarkClip> = emptyMap()
        private set
    var clipStateMap: Map<String, List<String>> = emptyMap()
        private set

    /** Qc[pair][eye][point] = [x,y] — pair 0 is resting. */
    var qcRings: List<List<List<FloatArray>>> = emptyList()
        private set

    const val He = 114.2705f

    fun isLoaded(): Boolean = loaded

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val assets = context.applicationContext.assets
            grok = parseShapes(assets.open(SHAPES_ASSET).bufferedReader().use { it.readText() })
            parseClips(assets.open(CLIPS_ASSET).bufferedReader().use { it.readText() })
            parseEyes(assets.open(EYES_ASSET).bufferedReader().use { it.readText() })
            generical = parseGenerical(assets.open(GENERICAL_ASSET).bufferedReader().use { it.readText() })
            runCatching {
                parseColors(assets.open(COLORS_ASSET).bufferedReader().use { it.readText() })
            }
            loaded = true
        }
    }

    fun parseSvgPath(pathData: String): Path =
        PathParser().parsePathString(pathData).toPath()

    fun pathBounds(pathData: String): Rect = parseSvgPath(pathData).getBounds()

    private fun parseShapes(text: String): GrokGeometry {
        val root = json.parseToJsonElement(text).jsonObject
        val coord = root["coordinateSpace"]?.jsonObject
        val vbParts = coord?.get("viewBoxParts")?.jsonObject
        val viewBox = if (vbParts != null) {
            ViewBox(vbParts.float("minX"), vbParts.float("minY"), vbParts.float("width"), vbParts.float("height"))
        } else {
            ViewBox(-15f, -15f, 259f, 259f)
        }
        val headCenter = coord?.floatOr("b", He) ?: He
        val pickerIds = (root["pickerShapeIds"]?.jsonArray?.mapNotNull {
            it.jsonPrimitive.contentOrNull
        } ?: MarkShapes.PICKER_IDS)
            .filter { it != "teardrop" && it != "droplet" }
            .ifEmpty { MarkShapes.PICKER_IDS }
        val shapesObj = root.getValue("shapes").jsonObject
        val shapes = linkedMapOf<String, GrokShape>()
        for ((id, el) in shapesObj) {
            val s = el.jsonObject
            val solidObj = s["solid"]?.jsonObject
            val solid = SolidSpec(
                kind = solidObj?.stringOrNull("kind") ?: "sphere",
                halfDepth = solidObj?.floatOr("halfDepth", 0.36f) ?: 0.36f,
                bevel = solidObj?.floatOr("bevel", 0.22f) ?: 0.22f,
                depth = solidObj?.floatOr("depth", 0.62f) ?: 0.62f,
                axis = solidObj?.floatOr("axis", 0f)?.toInt() ?: 0,
            )
            shapes[id] = GrokShape(
                id = id,
                pathData = s.string("path"),
                scale = s.floatOr("scale", 1f),
                solid = solid,
                beltRadius = s.floatOrNull("beltRadius"),
                top = s.floatOrNull("top"),
                bottom = s.floatOrNull("bottom"),
            )
        }
        return GrokGeometry(
            headCenter = headCenter,
            viewBox = viewBox,
            shapes = shapes,
            pickerShapeIds = pickerIds,
            gradients = emptyMap(),
        )
    }

    /** Internal for JVM motion tests (no Android assets needed). */
    internal fun parseClips(text: String) {
        val root = json.parseToJsonElement(text).jsonObject
        val list = root["lifecycleClips"]?.jsonArray ?: return
        val map = linkedMapOf<String, MarkClip>()
        for (el in list) {
            val c = el.jsonObject
            val name = c.string("name")
            val tracksObj = c["tracks"]?.jsonObject ?: JsonObject(emptyMap())
            val tracks = mutableMapOf<String, List<ClipKeyframe>>()
            for ((tk, tv) in tracksObj) {
                val arr = tv.jsonArray
                tracks[tk] = arr.map { kfEl ->
                    val kf = kfEl.jsonObject
                    val t = kf.float("t")
                    val easeArr = kf["ease"]?.jsonArray?.mapNotNull {
                        it.jsonPrimitive.doubleOrNull?.toFloat()
                    }?.toFloatArray() ?: floatArrayOf(0.42f, 0f, 0.58f, 1f)
                    val vEl = kf["v"]
                    when {
                        vEl == null -> ClipKeyframe(t = t, ease = easeArr)
                        vEl is JsonArray -> ClipKeyframe(
                            t = t,
                            values = vEl.mapNotNull { it.jsonPrimitive.doubleOrNull?.toFloat() }.toFloatArray(),
                            ease = easeArr,
                        )
                        else -> ClipKeyframe(
                            t = t,
                            expression = vEl.jsonPrimitive.contentOrNull,
                            ease = easeArr,
                        )
                    }
                }
            }
            val inspect = c["inspect"]?.jsonObject
            map[name] = MarkClip(
                name = name,
                duration = c.floatOr("duration", 4f),
                body = c.stringOrNull("body") ?: "sphere",
                squashAnchor = c.floatOr("squashAnchor", 0f),
                inspectFollow = inspect?.let {
                    it["follow"]?.jsonPrimitive?.contentOrNull != "false"
                } ?: true,
                tracks = tracks,
            )
        }
        clips = map
        val sm = root["stateMap"]?.jsonObject ?: return
        clipStateMap = sm.mapValues { (_, v) ->
            v.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        }
    }

    private fun parseEyes(text: String) {
        val root = json.parseToJsonElement(text).jsonObject
        val qc = root["Qc"]?.jsonArray ?: return
        qcRings = qc.map { pair ->
            pair.jsonArray.map { eye ->
                eye.jsonArray.map { pt ->
                    val xy = pt.jsonArray
                    floatArrayOf(
                        xy[0].jsonPrimitive.doubleOrNull?.toFloat() ?: 0f,
                        xy[1].jsonPrimitive.doubleOrNull?.toFloat() ?: 0f,
                    )
                }
            }
        }
    }

    private fun parseColors(text: String) {
        val root = json.parseToJsonElement(text).jsonObject
        val persona = root["persona"]?.jsonObject ?: root["Mie"]?.jsonObject ?: return
        val grads = linkedMapOf<String, PersonaGradient>()
        for ((id, el) in persona) {
            val g = el.jsonObject
            val light = g.stringOrNull("light") ?: g["light"]?.jsonObject?.stringOrNull("from")
            val dark = g.stringOrNull("dark") ?: g["dark"]?.jsonObject?.stringOrNull("from")
            if (light != null && dark != null) {
                grads[id] = PersonaGradient(light, dark)
            }
        }
        if (grads.isNotEmpty()) {
            grok = grok.copy(gradients = grads)
        }
    }

    private fun parseGenerical(text: String): GenericalEyesPack {
        val root = json.parseToJsonElement(text).jsonObject
        val vbRaw = root["viewBox"]?.jsonPrimitive?.contentOrNull ?: "0 0 500 500"
        val vb = vbRaw.split(Regex("\\s+"))
        val viewBox = if (vb.size == 4) {
            ViewBox(vb[0].toFloat(), vb[1].toFloat(), vb[2].toFloat(), vb[3].toFloat())
        } else ViewBox(0f, 0f, 500f, 500f)
        val exprsObj = root.getValue("expressions").jsonObject
        val expressions = linkedMapOf<String, GenericalExpression>()
        for ((id, el) in exprsObj) {
            val e = el.jsonObject
            val bodyFill = e["body"]?.jsonObject?.stringOrNull("fill") ?: "#009FE0"
            expressions[id] = GenericalExpression(
                id = id,
                left = parseGenericalEye(e.getValue("left").jsonObject),
                right = parseGenericalEye(e.getValue("right").jsonObject),
                bodyFill = bodyFill,
            )
        }
        return GenericalEyesPack(viewBox = viewBox, expressions = expressions)
    }

    private fun parseGenericalEye(obj: JsonObject): GenericalEye {
        val kind = obj.string("kind")
        // Designer craft: stroke 5 on 500×500; never ≥7 (rejected thick outlines)
        val strokeWidth = obj.floatOr("strokeWidth", 5f).coerceIn(3.5f, 5.5f)
        var from = Color.White
        var to = Color(0xFF87D2E9)
        var gx1 = 0f; var gy1 = 0f; var gx2 = 0f; var gy2 = 100f
        val grad = obj["gradient"]?.jsonObject
        if (grad != null) {
            gx1 = grad.stringOrNull("x1")?.toFloatOrNull() ?: 0f
            gy1 = grad.stringOrNull("y1")?.toFloatOrNull() ?: 0f
            gx2 = grad.stringOrNull("x2")?.toFloatOrNull() ?: 0f
            gy2 = grad.stringOrNull("y2")?.toFloatOrNull() ?: 100f
            val stops = grad["stops"] as? JsonArray
            if (stops != null && stops.size >= 2) {
                from = parseCssColor(stops.first().jsonObject.stringOrNull("color") ?: "white")
                to = parseCssColor(stops.last().jsonObject.stringOrNull("color") ?: "#87D2E9")
            }
        }
        var rotAngle: Float? = null; var rotCx: Float? = null; var rotCy: Float? = null
        val transform = obj.stringOrNull("transform")
        if (!transform.isNullOrBlank() && transform != "null") {
            val m = Regex("""rotate\(\s*([-.\d]+)\s+([-.\d]+)\s+([-.\d]+)\s*\)""").find(transform)
            if (m != null) {
                rotAngle = m.groupValues[1].toFloat()
                rotCx = m.groupValues[2].toFloat()
                rotCy = m.groupValues[3].toFloat()
            }
        }
        return GenericalEye(
            kind = kind,
            pathData = obj.stringOrNull("d")?.takeIf { it.isNotBlank() },
            x = obj.floatOrNull("x"), y = obj.floatOrNull("y"),
            width = obj.floatOrNull("width"), height = obj.floatOrNull("height"),
            rx = obj.floatOrNull("rx"),
            rotateAngle = rotAngle, rotateCx = rotCx, rotateCy = rotCy,
            strokeWidth = strokeWidth,
            gradientFrom = from, gradientTo = to,
            gradX1 = gx1, gradY1 = gy1, gradX2 = gx2, gradY2 = gy2,
        )
    }

    private fun parseCssColor(raw: String): Color {
        val s = raw.trim()
        return when {
            s.equals("white", true) -> Color.White
            s.equals("black", true) -> Color.Black
            s.startsWith("#") -> {
                val h = s.removePrefix("#")
                try {
                    when (h.length) {
                        6 -> {
                            val v = h.toLong(16)
                            Color(((v shr 16) and 0xFF) / 255f, ((v shr 8) and 0xFF) / 255f, (v and 0xFF) / 255f, 1f)
                        }
                        else -> Color.White
                    }
                } catch (_: Exception) { Color.White }
            }
            else -> Color.White
        }
    }

    private fun JsonObject.float(key: String): Float =
        this[key]?.jsonPrimitive?.doubleOrNull?.toFloat()
            ?: error("Missing float $key")
    private fun JsonObject.floatOr(key: String, default: Float): Float =
        this[key]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: default
    private fun JsonObject.floatOrNull(key: String): Float? =
        this[key]?.jsonPrimitive?.doubleOrNull?.toFloat()
    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.contentOrNull ?: error("Missing string $key")
    private fun JsonObject.stringOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}
