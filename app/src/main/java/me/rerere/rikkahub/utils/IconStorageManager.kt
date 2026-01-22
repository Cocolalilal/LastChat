package me.rerere.rikkahub.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * Manages persistent storage for auto-fetched model/provider icons.
 * Icons are downloaded once and stored locally for instant loading.
 * 
 * Storage location: filesDir/auto_icons/
 * This is separate from user-chosen icons (filesDir/avatars/) to allow
 * automatic cleanup of unused auto-fetched icons without affecting user choices.
 */
class IconStorageManager private constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    private val iconsDir = File(context.filesDir, "auto_icons")
    private val downloadMutex = Mutex()
    private val activeDownloads = mutableSetOf<String>()
    
    init {
        if (!iconsDir.exists()) {
            iconsDir.mkdirs()
        }
    }
    
    companion object {
        @Volatile
        private var instance: IconStorageManager? = null
        
        fun getInstance(context: Context, okHttpClient: OkHttpClient): IconStorageManager {
            return instance ?: synchronized(this) {
                instance ?: IconStorageManager(context.applicationContext, okHttpClient).also {
                    instance = it
                }
            }
        }
        
        /**
         * Generate a stable, filesystem-safe key from provider slug and model info.
         * @param providerSlug The provider slug (e.g., "openai", "anthropic")
         * @param modelId The model ID for model-specific icons, null for provider icons
         * @param darkMode Whether this is for dark mode (affects which CDN URL is used)
         */
        fun generateIconKey(providerSlug: String?, modelId: String? = null, darkMode: Boolean = false): String {
            val base = when {
                modelId != null && providerSlug != null -> "${providerSlug}_${modelId.hashCode()}"
                providerSlug != null -> "provider_$providerSlug"
                modelId != null -> "model_${modelId.hashCode()}"
                else -> "unknown"
            }
            // Include dark mode in key since LobeHub has different icons per theme
            return if (darkMode) "${base}_dark" else "${base}_light"
        }
    }
    
    /**
     * Check if a local icon exists for the given key.
     */
    fun hasLocalIcon(iconKey: String): Boolean {
        return getIconFile(iconKey).exists()
    }
    
    /**
     * Get the local file URI for an icon if it exists.
     * Returns a file:// URI that can be used directly with AsyncImage.
     */
    fun getLocalIconUri(iconKey: String): String? {
        val file = getIconFile(iconKey)
        return if (file.exists()) {
            Uri.fromFile(file).toString()
        } else {
            null
        }
    }
    
    /**
     * Get the local File for an icon.
     */
    private fun getIconFile(iconKey: String): File {
        // Sanitize the key for filesystem
        val safeKey = iconKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(iconsDir, "$safeKey.png")
    }
    
    /**
     * Download an icon from URL and save it locally.
     * Returns the local file URI if successful, null otherwise.
     * 
     * This method is thread-safe and prevents duplicate downloads of the same icon.
     */
    suspend fun downloadAndSaveIcon(iconKey: String, url: String): String? {
        // Check if already exists
        getLocalIconUri(iconKey)?.let { return it }
        
        // Prevent concurrent downloads of the same icon
        val shouldDownload = downloadMutex.withLock {
            if (activeDownloads.contains(iconKey)) {
                false
            } else {
                activeDownloads.add(iconKey)
                true
            }
        }
        
        if (!shouldDownload) {
            // Wait for the other download to complete and return the result
            while (downloadMutex.withLock { activeDownloads.contains(iconKey) }) {
                kotlinx.coroutines.delay(50)
            }
            return getLocalIconUri(iconKey)
        }
        
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    null
                } else {
                    response.body?.byteStream()?.use { inputStream ->
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        if (bitmap != null) {
                            // Resize if too large (max 256x256 for icons)
                            val resized = resizeIfNeeded(bitmap, 256)
                            
                            val file = getIconFile(iconKey)
                            FileOutputStream(file).use { output ->
                                resized.compress(Bitmap.CompressFormat.PNG, 100, output)
                            }
                            
                            // Recycle bitmaps if we created a new one
                            if (resized != bitmap) {
                                bitmap.recycle()
                            }
                            resized.recycle()
                            
                            Uri.fromFile(file).toString()
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            downloadMutex.withLock {
                activeDownloads.remove(iconKey)
            }
        }
    }
    
    /**
     * Resize bitmap if larger than maxSize while maintaining aspect ratio.
     */
    private fun resizeIfNeeded(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (width > height) {
            newWidth = maxSize
            newHeight = (maxSize / ratio).toInt()
        } else {
            newHeight = maxSize
            newWidth = (maxSize * ratio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Remove icons that are no longer used.
     * Call this when models/providers are deleted.
     * 
     * @param usedIconKeys Set of icon keys that are still in use
     */
    fun cleanupUnusedIcons(usedIconKeys: Set<String>) {
        try {
            iconsDir.listFiles()?.forEach { file ->
                // Extract key from filename (remove .png extension)
                val fileKey = file.nameWithoutExtension
                if (!usedIconKeys.any { key ->
                    val safeKey = key.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    safeKey == fileKey
                }) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Get the total size of stored icons in bytes.
     */
    fun getStorageSize(): Long {
        return try {
            iconsDir.listFiles()?.sumOf { it.length() } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * Get the count of stored icons.
     */
    fun getIconCount(): Int {
        return try {
            iconsDir.listFiles()?.size ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Clear all auto-fetched icons.
     */
    fun clearAllIcons() {
        try {
            iconsDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
