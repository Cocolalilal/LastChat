package me.rerere.rikkahub.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "AssistantWidgetReceiver"

/**
 * Receiver for the Assistant Widget.
 * This is the entry point for the widget system.
 */
class AssistantWidgetReceiver : GlanceAppWidgetReceiver() {
    
    override val glanceAppWidget: GlanceAppWidget = AssistantWidget()
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for widgets: ${appWidgetIds.joinToString()}")
        // Advance each widget's stored variant by 1 (and pick a non-repeating face index)
        // synchronously in WidgetPrefs before Glance redraws, at most once per widget per update.
        val updatedVariants = LinkedHashMap<Int, Int>(appWidgetIds.size)
        for (appWidgetId in appWidgetIds) {
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && !updatedVariants.containsKey(appWidgetId)) {
                updatedVariants[appWidgetId] = WidgetPrefs.advanceVariant(context, appWidgetId)
            }
        }

        super.onUpdate(context, appWidgetManager, appWidgetIds)
        
        // Explicitly update each widget
        scope.launch {
            val glanceManager = GlanceAppWidgetManager(context)
            updatedVariants.forEach { (appWidgetId, newVariant) ->
                try {
                    Log.d(TAG, "Updating widget $appWidgetId (variant=$newVariant)")
                    val glanceId = glanceManager.getGlanceIdBy(appWidgetId)
                    val seed = WidgetPrefs.getSeed(context, appWidgetId)
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            this[AssistantWidget.AVATAR_SEED_KEY] = seed
                            this[AssistantWidget.AVATAR_VARIANT_KEY] = newVariant
                        }
                    }
                    glanceAppWidget.update(context, glanceId)
                    Log.d(TAG, "Widget $appWidgetId updated")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating widget $appWidgetId", e)
                }
            }
        }
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        Log.d(TAG, "onDeleted called for widgets: ${appWidgetIds.joinToString()}")
        // Clean up widget data when widgets are deleted
        scope.launch {
            appWidgetIds.forEach { widgetId ->
                WidgetPrefs.deleteConfig(context, widgetId)
            }
        }
    }
}
