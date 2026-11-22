package me.rerere.rikkahub.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

private const val TAG = "AssistantWidgetReceiver"

/**
 * Receiver for the Assistant Widget.
 * This is the entry point for the widget system.
 */
class AssistantWidgetReceiver : GlanceAppWidgetReceiver() {
    
    override val glanceAppWidget: GlanceAppWidget = AssistantWidget()
    
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for widgets: ${appWidgetIds.joinToString()}")
        // Legacy random widgets advance once per update. WidgetPrefs keeps user-picked presets fixed.
        val widgetIds = appWidgetIds.distinct()
            .filter { it != AppWidgetManager.INVALID_APPWIDGET_ID }
            .toIntArray()
        for (appWidgetId in widgetIds) {
            WidgetPrefs.advanceVariant(context, appWidgetId)
        }

        // Publish the local variation before Glance reads it. Glance owns the broadcast's
        // asynchronous lifetime and schedules the only update for each widget.
        super.onUpdate(context, appWidgetManager, widgetIds)
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "onDeleted called for widgets: ${appWidgetIds.joinToString()}")
        // Enqueue preference removal while this receiver is still running, rather than
        // launching work that can be lost after the broadcast completes.
        appWidgetIds.distinct().forEach { widgetId ->
            WidgetPrefs.deleteConfig(context, widgetId)
        }
        super.onDeleted(context, appWidgetIds)
    }
}
