package me.rerere.rikkahub.data.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.uuid.Uuid

/**
 * Quick settings cache using SharedPreferences for synchronous initial load.
 * This caches key settings values so the app can show the correct UI immediately
 * on startup without waiting for DataStore to load.
 */
class QuickSettingsCache(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "quick_settings_cache"
        
        // Cache keys
        private const val KEY_ASSISTANT_ID = "cached_assistant_id"
        private const val KEY_NEW_CHAT_HEADER_STYLE = "cached_new_chat_header"
        private const val KEY_NEW_CHAT_CONTENT_STYLE = "cached_new_chat_content"
        private const val KEY_HAS_CACHED_VALUES = "has_cached_values"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if we have cached values available
     */
    val hasCachedValues: Boolean
        get() = prefs.getBoolean(KEY_HAS_CACHED_VALUES, false)
    
    /**
     * Get cached assistant ID, or null if not cached
     */
    val assistantId: Uuid?
        get() = prefs.getString(KEY_ASSISTANT_ID, null)?.let { 
            try { Uuid.parse(it) } catch (e: Exception) { null }
        }
    
    /**
     * Get cached new chat header style, or null if not cached
     */
    val newChatHeaderStyle: NewChatHeaderStyle?
        get() = prefs.getString(KEY_NEW_CHAT_HEADER_STYLE, null)?.let {
            try { NewChatHeaderStyle.valueOf(it) } catch (e: Exception) { null }
        }
    
    /**
     * Get cached new chat content style, or null if not cached
     */
    val newChatContentStyle: NewChatContentStyle?
        get() = prefs.getString(KEY_NEW_CHAT_CONTENT_STYLE, null)?.let {
            try { NewChatContentStyle.valueOf(it) } catch (e: Exception) { null }
        }
    
    /**
     * Update the cache with current settings values.
     * Call this whenever settings are updated.
     */
    fun updateCache(settings: Settings) {
        if (settings.init) return // Don't cache dummy settings
        
        prefs.edit {
            putString(KEY_ASSISTANT_ID, settings.assistantId.toString())
            putString(KEY_NEW_CHAT_HEADER_STYLE, settings.displaySetting.newChatHeaderStyle.name)
            putString(KEY_NEW_CHAT_CONTENT_STYLE, settings.displaySetting.newChatContentStyle.name)
            putBoolean(KEY_HAS_CACHED_VALUES, true)
        }
    }
    
    /**
     * Create a Settings object with cached values merged into defaults.
     * This provides a better initial state than dummy() while DataStore loads.
     */
    fun createCachedSettings(): Settings {
        val cachedAssistantId = assistantId ?: DEFAULT_ASSISTANT_ID
        val cachedHeaderStyle = newChatHeaderStyle ?: NewChatHeaderStyle.GREETING
        val cachedContentStyle = newChatContentStyle ?: NewChatContentStyle.ACTIONS
        
        return Settings(
            init = true, // Still marked as init so updates don't save it
            assistantId = cachedAssistantId,
            displaySetting = DisplaySetting(
                newChatHeaderStyle = cachedHeaderStyle,
                newChatContentStyle = cachedContentStyle
            )
        )
    }
}
