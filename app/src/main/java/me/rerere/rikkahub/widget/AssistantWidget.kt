package me.rerere.rikkahub.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.activity.ShortcutHandlerActivity
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

private const val TAG = "AssistantWidget"

class AssistantWidget : GlanceAppWidget() {
    
    // Use Glance's built-in preferences state definition
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    
    companion object {
        val ASSISTANT_ID_KEY = stringPreferencesKey("assistant_id")
        val ASSISTANT_NAME_KEY = stringPreferencesKey("assistant_name")
        val AVATAR_TYPE_KEY = stringPreferencesKey("avatar_type")
        val AVATAR_DATA_KEY = stringPreferencesKey("avatar_data")
        
        // Helper to save widget state and trigger update
        suspend fun updateWidgetState(
            context: Context,
            glanceId: GlanceId,
            assistantId: String,
            assistantName: String,
            avatarType: String,
            avatarData: String
        ) {
            Log.d(TAG, "updateWidgetState: id=$assistantId, name=$assistantName, type=$avatarType")
            
            // Use explicit PreferencesGlanceStateDefinition to ensure state is saved correctly
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[ASSISTANT_ID_KEY] = assistantId
                    this[ASSISTANT_NAME_KEY] = assistantName
                    this[AVATAR_TYPE_KEY] = avatarType
                    this[AVATAR_DATA_KEY] = avatarData
                }
            }
            
            // Verify the state was saved
            val savedPrefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            val savedType = savedPrefs[AVATAR_TYPE_KEY]
            Log.d(TAG, "Verification: saved type=$savedType")
            
            Log.d(TAG, "State saved, calling updateAll()")
            // Use updateAll instead of update - this seems more reliable
            AssistantWidget().updateAll(context)
            Log.d(TAG, "updateAll() returned, waiting for Glance to process...")
            // Give Glance time to actually process the update
            kotlinx.coroutines.delay(1500)
            Log.d(TAG, "Delay completed")
        }
    }
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance called for id: $id")
        
        provideContent {
            // Read state using Glance's currentState - this will be updated automatically
            val prefs = currentState<Preferences>()
            
            val assistantIdStr = prefs[ASSISTANT_ID_KEY]
            val assistantName = prefs[ASSISTANT_NAME_KEY] ?: "Assistant"
            val avatarType = prefs[AVATAR_TYPE_KEY] ?: "dummy"
            val avatarData = prefs[AVATAR_DATA_KEY] ?: ""
            
            Log.d(TAG, "State from prefs: id=$assistantIdStr, name=$assistantName, type=$avatarType")
            
            val assistantId = assistantIdStr?.let { 
                try { Uuid.parse(it) } catch (e: Exception) { null }
            }
            
            GlanceTheme {
                WidgetContent(
                    context = context,
                    assistantId = assistantId,
                    assistantName = assistantName,
                    avatarType = avatarType,
                    avatarData = avatarData
                )
            }
        }
    }
    
    @Composable
    private fun WidgetContent(
        context: Context,
        assistantId: Uuid?,
        assistantName: String,
        avatarType: String,
        avatarData: String
    ) {
        val intent = if (assistantId != null) {
            Intent(context, ShortcutHandlerActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse("lastchat://assistant/${assistantId}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(context, me.rerere.rikkahub.RouteActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        
        // Create avatar bitmap synchronously for display
        val avatarBitmap = createAvatarBitmap(context, avatarType, avatarData, assistantName)
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(4.dp)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(avatarBitmap),
                contentDescription = context.getString(R.string.a11y_assistant_widget),
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
    
    private fun createAvatarBitmap(
        context: Context,
        avatarType: String,
        avatarData: String,
        fallbackName: String
    ): Bitmap {
        Log.d(TAG, "createAvatarBitmap: type=$avatarType, data=$avatarData")
        return when (avatarType) {
            "emoji" -> createEmojiBitmap(avatarData)
            "image" -> loadImageBitmapSync(avatarData) ?: createTextBitmap(fallbackName)
            "resource" -> {
                try {
                    val resId = avatarData.toIntOrNull() ?: R.drawable.default_generical_pfp
                    val sourceBitmap = BitmapFactory.decodeResource(context.resources, resId)
                    if (sourceBitmap != null) {
                        makeCircular(sourceBitmap)
                    } else {
                        createTextBitmap(fallbackName)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading resource", e)
                    createTextBitmap(fallbackName)
                }
            }
            else -> createTextBitmap(fallbackName)
        }
    }
    
    private fun createEmojiBitmap(emoji: String): Bitmap {
        val size = 256
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val bgPaint = Paint().apply {
            color = 0xFFE8E8E8.toInt()
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)
        
        val textPaint = Paint().apply {
            textSize = size * 0.5f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(emoji, 0, emoji.length, bounds)
        val y = size / 2f + bounds.height() / 2f
        canvas.drawText(emoji, size / 2f, y, textPaint)
        
        return bitmap
    }
    
    private fun createTextBitmap(name: String): Bitmap {
        val size = 256
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val bgPaint = Paint().apply {
            color = 0xFF6750A4.toInt()
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)
        
        val letter = name.firstOrNull()?.uppercase() ?: "A"
        val textPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = size * 0.45f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val bounds = Rect()
        textPaint.getTextBounds(letter, 0, letter.length, bounds)
        val y = size / 2f + bounds.height() / 2f
        canvas.drawText(letter, size / 2f, y, textPaint)
        
        return bitmap
    }
    
    private fun makeCircular(sourceBitmap: Bitmap): Bitmap {
        val size = 256
        val scaledBitmap = Bitmap.createScaledBitmap(sourceBitmap, size, size, true)
        val circularBitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(circularBitmap)
        
        val paint = Paint().apply {
            isAntiAlias = true
            shader = android.graphics.BitmapShader(
                scaledBitmap,
                android.graphics.Shader.TileMode.CLAMP,
                android.graphics.Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        if (sourceBitmap != scaledBitmap) {
            scaledBitmap.recycle()
        }
        
        return circularBitmap
    }
    
    private fun loadImageBitmapSync(url: String): Bitmap? {
        return try {
            val response = runBlocking(Dispatchers.IO) {
                withTimeout(5_000L) {
                    GlobalContext.get().get<PlatformHttpClient>().execute(
                        PlatformHttpRequest(
                            method = "GET",
                            url = url,
                        )
                    )
                }
            }
            if (response.statusCode != 200) return null
            val body = response.body
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(body, 0, body.size, options)
            options.inJustDecodeBounds = false
            options.inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                256,
                256
            )
            val bitmap = BitmapFactory.decodeByteArray(body, 0, body.size, options)
            bitmap?.let { makeCircular(it).also { bitmap.recycle() } }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image", e)
            null
        }
    }

    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            var halfHeight = srcHeight / 2
            var halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
