package me.rerere.rikkahub.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import java.io.File
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.common.platform.PlatformHttpClient
import me.rerere.common.platform.PlatformHttpRequest
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.ui.activity.ShortcutHandlerActivity
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.core.context.GlobalContext

private const val TAG = "AssistantWidget"

class AssistantWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_assistant_loading) {
    
    // Use Glance's built-in preferences state definition
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition
    
    companion object {
        val ASSISTANT_ID_KEY = stringPreferencesKey("assistant_id")
        val ASSISTANT_NAME_KEY = stringPreferencesKey("assistant_name")
        val AVATAR_TYPE_KEY = stringPreferencesKey("avatar_type")
        val AVATAR_DATA_KEY = stringPreferencesKey("avatar_data")
        val AVATAR_PRESET_KEY = stringPreferencesKey("avatar_preset")
        val AVATAR_SEED_KEY = intPreferencesKey("avatar_seed")
        val AVATAR_VARIANT_KEY = intPreferencesKey("avatar_variant")
        
        // Helper to save widget state and trigger update
        suspend fun updateWidgetState(
            context: Context,
            glanceId: GlanceId,
            assistantId: String,
            assistantName: String,
            avatarType: String,
            avatarData: String,
            seed: Int = 0,
            variant: Int = 0,
            presetId: String? = null,
        ) {
            Log.d(TAG, "updateWidgetState: id=$assistantId, name=$assistantName, type=$avatarType")
            
            // Use explicit PreferencesGlanceStateDefinition to ensure state is saved correctly
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[ASSISTANT_ID_KEY] = assistantId
                    this[ASSISTANT_NAME_KEY] = assistantName
                    this[AVATAR_TYPE_KEY] = avatarType
                    this[AVATAR_DATA_KEY] = avatarData
                    this[AVATAR_SEED_KEY] = seed
                    this[AVATAR_VARIANT_KEY] = variant
                    if (presetId != null) this[AVATAR_PRESET_KEY] = presetId else remove(AVATAR_PRESET_KEY)
                }
            }
            
            // Updating this id preserves the other widgets and lets Glance own its work lifetime.
            AssistantWidget().update(context, glanceId)
        }
    }
    
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance called for id: $id")
        val appWidgetId = runCatching {
            GlanceAppWidgetManager(context).getAppWidgetId(id)
        }.getOrDefault(AppWidgetManager.INVALID_APPWIDGET_ID)
        
        provideContent {
            // Read state using Glance's currentState - this will be updated automatically
            val prefs = currentState<Preferences>()
            val hasValidWidgetId = appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID

            val assistantIdStr = prefs[ASSISTANT_ID_KEY]
                ?: if (hasValidWidgetId) WidgetPrefs.getAssistantId(context, appWidgetId) else null
            val assistantName = prefs[ASSISTANT_NAME_KEY]
                ?: if (hasValidWidgetId) WidgetPrefs.getAssistantName(context, appWidgetId) else "Assistant"
            val avatarType = prefs[AVATAR_TYPE_KEY]
                ?: if (hasValidWidgetId) WidgetPrefs.getAvatarType(context, appWidgetId) else "dummy"
            val avatarData = prefs[AVATAR_DATA_KEY]
                ?: if (hasValidWidgetId) WidgetPrefs.getAvatarData(context, appWidgetId) else ""
            val presetId = if (hasValidWidgetId) WidgetPrefs.getPresetId(context, appWidgetId)
                else prefs[AVATAR_PRESET_KEY]
            val prefSeed = prefs[AVATAR_SEED_KEY]
            val prefVariant = prefs[AVATAR_VARIANT_KEY]
            val seed = if (hasValidWidgetId) WidgetPrefs.getSeed(context, appWidgetId) else (prefSeed ?: 0)
            val variant = if (hasValidWidgetId) WidgetPrefs.getVariant(context, appWidgetId) else (prefVariant ?: 0)
            
            Log.d(TAG, "State from prefs: id=$assistantIdStr, name=$assistantName, type=$avatarType, seed=$seed, variant=$variant")
            
            val assistantId = assistantIdStr?.let { 
                try { Uuid.parse(it) } catch (e: Exception) { null }
            }
            
            GlanceTheme {
                WidgetContent(
                    context = context,
                    assistantId = assistantId,
                    assistantName = assistantName,
                    avatarType = avatarType,
                    avatarData = avatarData,
                    seed = seed,
                    variant = variant,
                    presetId = presetId,
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
        avatarData: String,
        seed: Int,
        variant: Int,
        presetId: String?,
    ) {
        val intent = widgetLaunchIntent(context, assistantId?.toString())

        val localSize = LocalSize.current
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val minDp = minOf(localSize.width.value, localSize.height.value)
        val sizePx = if (minDp > 0f && minDp.isFinite()) {
            (minDp * density).toInt().coerceIn(WidgetAvatarRenderer.DEFAULT_BITMAP_PX, WidgetAvatarRenderer.MAX_BITMAP_PX)
        } else {
            WidgetAvatarRenderer.DEFAULT_BITMAP_PX
        }

        // Never block the Glance composition thread on network or bitmap rendering.
        // The native avatar is already available while the selected image is prepared.
        val request = WidgetAvatarRequest(avatarType, avatarData, assistantName, seed, variant, sizePx, presetId)
        val placeholder = remember(context) {
            val source = BitmapFactory.decodeResource(context.resources, R.drawable.default_generical_pfp)
            if (source != null) makeCircular(source).also { source.recycle() } else createTextBitmap("LastChat")
        }
        val avatarBitmap by produceState(placeholder, request) {
            value = withContext(Dispatchers.IO) {
                createAvatarBitmap(context, request.avatarType, request.avatarData, request.fallbackName,
                    request.seed, request.variant, request.sizePx, request.presetId)
            }
        }
        val image = ImageProvider(avatarBitmap)

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(4.dp)
                .clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = image,
                contentDescription = context.getString(R.string.a11y_assistant_widget),
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
    
    internal suspend fun createAvatarBitmap(
        context: Context,
        avatarType: String,
        avatarData: String,
        fallbackName: String,
        seed: Int = 0,
        variant: Int = 0,
        sizePx: Int = WidgetAvatarRenderer.DEFAULT_BITMAP_PX,
        presetId: String? = null,
    ): Bitmap {
        return when (avatarType) {
            "animated" -> {
                try {
                    val animated = JsonInstant.decodeFromString(Avatar.Animated.serializer(), avatarData)
                    WidgetAvatarRenderer.render(
                        context = context,
                        avatar = animated,
                        seed = seed,
                        sizePx = sizePx,
                        variant = variant,
                        presetId = presetId,
                    ) ?: createTextBitmap(fallbackName)
                } catch (e: Exception) {
                    Log.e(TAG, "Error rendering animated avatar", e)
                    createTextBitmap(fallbackName)
                }
            }
            "emoji" -> createEmojiBitmap(avatarData)
            "image" -> loadImageBitmap(context, avatarData) ?: createTextBitmap(fallbackName)
            "resource" -> {
                try {
                    val resId = avatarData.toIntOrNull() ?: R.drawable.default_generical_pfp
                    val sourceBitmap = BitmapFactory.decodeResource(context.resources, resId)
                    if (sourceBitmap != null) {
                        makeCircular(sourceBitmap).also { sourceBitmap.recycle() }
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
        val circularBitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = maxOf(size.toFloat() / sourceBitmap.width, size.toFloat() / sourceBitmap.height)
        val bitmapShader = android.graphics.BitmapShader(
            sourceBitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP,
        ).apply {
            setLocalMatrix(android.graphics.Matrix().apply {
                setScale(scale, scale)
                postTranslate((size - sourceBitmap.width * scale) / 2f, (size - sourceBitmap.height * scale) / 2f)
            })
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = bitmapShader }
        Canvas(circularBitmap).drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return circularBitmap
    }

    private suspend fun loadImageBitmap(context: Context, source: String): Bitmap? {
        return try {
            val uri = Uri.parse(source)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val decoded = when (uri.scheme?.lowercase()) {
                "http", "https" -> {
                    val response = withTimeoutOrNull(5_000L) {
                        GlobalContext.get().get<PlatformHttpClient>().execute(
                            PlatformHttpRequest(method = "GET", url = source)
                        )
                    } ?: return null
                    if (response.statusCode != 200) return null
                    val body = response.body
                    BitmapFactory.decodeByteArray(body, 0, body.size, options)
                    options.inJustDecodeBounds = false
                    options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, 256, 256)
                    BitmapFactory.decodeByteArray(body, 0, body.size, options)
                }
                "content", "file", null -> {
                    val localUri = if (uri.scheme == null) Uri.fromFile(File(source)) else uri
                    context.contentResolver.openInputStream(localUri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    options.inJustDecodeBounds = false
                    options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, 256, 256)
                    context.contentResolver.openInputStream(localUri)?.use { BitmapFactory.decodeStream(it, null, options) }
                }
                else -> null
            }
            decoded?.let { bitmap -> makeCircular(bitmap).also { bitmap.recycle() } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error loading widget image", e)
            null
        }
    }

    override fun onCompositionError(context: Context, glanceId: GlanceId, appWidgetId: Int, throwable: Throwable) {
        Log.e(TAG, "Widget composition failed; showing launchable fallback", throwable)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, widgetFallbackViews(context, appWidgetId))
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

private data class WidgetAvatarRequest(
    val avatarType: String,
    val avatarData: String,
    val fallbackName: String,
    val seed: Int,
    val variant: Int,
    val sizePx: Int,
    val presetId: String?,
)

internal fun widgetLaunchIntent(context: Context, assistantId: String?): Intent {
    val id = assistantId?.let { runCatching { Uuid.parse(it) }.getOrNull() }
    return if (id != null) {
        Intent(context, ShortcutHandlerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("lastchat://assistant/$id")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    } else {
        Intent(context, me.rerere.rikkahub.RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}

internal fun widgetFallbackViews(context: Context, appWidgetId: Int): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_assistant_loading).apply {
        setOnClickPendingIntent(
            R.id.assistant_widget_avatar,
            PendingIntent.getActivity(context, appWidgetId,
                widgetLaunchIntent(context, WidgetPrefs.getAssistantId(context, appWidgetId)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
    }
