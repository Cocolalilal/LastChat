package me.rerere.rikkahub.widget

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.utils.JsonInstant

internal data class WidgetConfig(
    val assistantId: String?,
    val assistantName: String = "Assistant",
    val avatarType: String = "dummy",
    val avatarData: String = "",
    val seed: Int = 0,
    val variant: Int = 0,
    val presetId: String? = null,
) {
    fun migrateStockAvatar(): WidgetConfig {
        if (assistantId != DEFAULT_ASSISTANT_ID.toString()) return this
        val legacyResource = avatarType == "resource" &&
            (avatarData == R.drawable.default_generical_pfp.toString() ||
                // Verified stock resource ID in the shipped stable 1.4.9 APKs. Numeric IDs
                // can change after an upgrade; new widget configurations store resource names.
                avatarData == 0x7f080099.toString() || avatarData == "default_generical_pfp")
        val oldMark = if (avatarType == "animated") runCatching {
            JsonInstant.decodeFromString(Avatar.Animated.serializer(), avatarData)
        }.getOrNull() else null
        val stockMarks = listOf(
            Avatar.Animated(colorHex = "#7C5CFF", colorPreset = "indigo"),
            Avatar.Animated(
                colorHex = Avatar.Animated.GENERICAL_BLUE_HEX,
                colorPreset = Avatar.Animated.GENERICAL_BLUE_PRESET,
            ),
            Avatar.Animated(
                colorHex = Avatar.Animated.GENERICAL_BLUE_HEX,
                colorPreset = Avatar.Animated.GENERICAL_BLUE_PRESET,
                stockColorVersion = 1,
            ),
        )
        return if (legacyResource || oldMark in stockMarks) copy(
            avatarType = "animated",
            avatarData = JsonInstant.encodeToString(Avatar.Animated.serializer(), Avatar.Animated.defaultGenerical()),
        ) else this
    }
}

/**
 * Simple SharedPreferences-based storage for widget configuration.
 * Uses synchronous SharedPreferences for reliability.
 */
object WidgetPrefs {
    private const val PREFS_NAME = "assistant_widget_prefs"
    
    private fun prefs(context: Context): SharedPreferences {
        // Use applicationContext to ensure the same SharedPreferences instance
        // is accessed from both the config activity and the widget
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    internal fun configFlow(context: Context, widgetId: Int): Flow<WidgetConfig?> = callbackFlow {
        val preferences = prefs(context)
        val prefix = "widget_${widgetId}_"
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key.startsWith(prefix)) trySend(getConfig(context, widgetId))
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(getConfig(context, widgetId))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    // Read one committed snapshot: never combine different configurations during reconfiguration.
    internal fun getConfig(context: Context, widgetId: Int): WidgetConfig? {
        val values = prefs(context).all
        val prefix = "widget_${widgetId}_"
        val id = values[prefix + "id"] as? String ?: return null
        return WidgetConfig(
            assistantId = id,
            assistantName = values[prefix + "name"] as? String ?: "Assistant",
            avatarType = values[prefix + "avatar_type"] as? String ?: "dummy",
            avatarData = values[prefix + "avatar_data"] as? String ?: "",
            seed = values[prefix + "seed"] as? Int ?: defaultSeedFor(widgetId),
            variant = values[prefix + "variant"] as? Int ?: 0,
            presetId = values[prefix + "preset"] as? String,
        ).migrateStockAvatar()
    }

    fun defaultSeedFor(widgetId: Int): Int {
        val mixed = (widgetId * 1_103_515_245) xor 0x3C6EF35F
        return if (mixed != 0) mixed else 0x5A5A5A5A
    }

    fun generateSeed(widgetId: Int): Int {
        val salt = kotlin.random.Random.nextInt()
        val mixed = (widgetId * 31) xor salt
        return if (mixed != 0) mixed else defaultSeedFor(widgetId)
    }

    fun saveConfig(
        context: Context,
        widgetId: Int,
        assistantId: String,
        assistantName: String,
        avatarType: String,  // "dummy", "emoji", "image", "resource", "animated"
        avatarData: String,  // emoji content, image URL, resource ID, or animated avatar JSON
        seed: Int = generateSeed(widgetId),
        variant: Int = 0,
        faceIndex: Int = WidgetAvatarRenderer.faceIndexFor(seed, variant),
        presetId: String? = null,
    ): Boolean {
        // Use commit() instead of apply() to ensure synchronous write
        // This is critical because the widget updates immediately after
        return prefs(context).edit()
            .putString("widget_${widgetId}_id", assistantId)
            .putString("widget_${widgetId}_name", assistantName)
            .putString("widget_${widgetId}_avatar_type", avatarType)
            .putString("widget_${widgetId}_avatar_data", avatarData)
            .putInt("widget_${widgetId}_seed", seed)
            .putInt("widget_${widgetId}_variant", variant)
            .putString("widget_${widgetId}_preset", presetId)
            .putInt("widget_${widgetId}_face_index", faceIndex)
            .commit()
    }
    
    fun getAssistantId(context: Context, widgetId: Int): String? {
        return prefs(context).getString("widget_${widgetId}_id", null)
    }
    
    fun getAssistantName(context: Context, widgetId: Int): String {
        return prefs(context).getString("widget_${widgetId}_name", null) ?: "Assistant"
    }
    
    fun getAvatarType(context: Context, widgetId: Int): String {
        return prefs(context).getString("widget_${widgetId}_avatar_type", null) ?: "dummy"
    }
    
    fun getAvatarData(context: Context, widgetId: Int): String {
        return prefs(context).getString("widget_${widgetId}_avatar_data", null) ?: ""
    }

    fun getPresetId(context: Context, widgetId: Int): String? {
        return prefs(context).getString("widget_${widgetId}_preset", null)
    }

    fun getSeed(context: Context, widgetId: Int): Int {
        val p = prefs(context)
        val key = "widget_${widgetId}_seed"
        return if (p.contains(key)) p.getInt(key, defaultSeedFor(widgetId)) else defaultSeedFor(widgetId)
    }

    fun getVariant(context: Context, widgetId: Int): Int {
        return prefs(context).getInt("widget_${widgetId}_variant", 0)
    }

    fun getFaceIndex(context: Context, widgetId: Int): Int {
        val p = prefs(context)
        val key = "widget_${widgetId}_face_index"
        return if (p.contains(key)) {
            p.getInt(key, 0)
        } else {
            WidgetAvatarRenderer.faceIndexFor(getSeed(context, widgetId), getVariant(context, widgetId))
        }
    }

    /**
     * Advances the stored `variant` for [widgetId] by 1 (synchronously via `commit()`)
     * and updates the stored `faceIndex` so it is never equal to the previously stored face index.
     */
    fun advanceVariant(context: Context, widgetId: Int): Int {
        val p = prefs(context)
        val seed = getSeed(context, widgetId)
        val currentVariant = getVariant(context, widgetId)
        // User-picked expressions stay fixed through launcher refreshes and app restarts.
        if (WidgetAvatarPreset.fromId(getPresetId(context, widgetId)) != null) return currentVariant
        val currentFaceIndex = getFaceIndex(context, widgetId)
        val nextVariant = currentVariant + 1
        var nextFaceIndex = WidgetAvatarRenderer.faceIndexFor(seed, nextVariant)
        if (nextFaceIndex == currentFaceIndex) {
            nextFaceIndex = (currentFaceIndex + 1) % WidgetAvatarRenderer.NO_ANGER_FACES.size
        }
        p.edit()
            .putInt("widget_${widgetId}_seed", seed)
            .putInt("widget_${widgetId}_variant", nextVariant)
            .putInt("widget_${widgetId}_face_index", nextFaceIndex)
            .commit()
        return nextVariant
    }
    
    fun deleteConfig(context: Context, widgetId: Int) {
        prefs(context).edit()
            .remove("widget_${widgetId}_id")
            .remove("widget_${widgetId}_name")
            .remove("widget_${widgetId}_avatar_type")
            .remove("widget_${widgetId}_avatar_data")
            .remove("widget_${widgetId}_preset")
            .remove("widget_${widgetId}_seed")
            .remove("widget_${widgetId}_variant")
            .remove("widget_${widgetId}_face_index")
            .commit()
        WidgetAvatarCache.delete(context, widgetId)
    }
}
