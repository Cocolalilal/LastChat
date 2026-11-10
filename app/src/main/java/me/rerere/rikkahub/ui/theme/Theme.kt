package me.rerere.rikkahub.ui.theme

import android.app.Activity
import android.app.UiModeManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable

import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.data.datastore.FontSettings
import me.rerere.rikkahub.ui.motion.LocalMotionPolicy
import me.rerere.rikkahub.ui.motion.rememberSystemMotionPolicy
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }
val LocalSystemContrast = compositionLocalOf { 0f }

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

private data class ThemeAppearance(
    val dynamicColor: Boolean,
    val themeId: String,
    val fontSettings: FontSettings,
)

@Composable
fun RikkahubTheme(
    content: @Composable () -> Unit
) {
    val settingsState = rememberUserSettingsState()
    // A search toggle, provider edit or save acknowledgement must not rebuild the
    // palette, typography, system bars and every themed control in the window.
    val appearance by remember(settingsState) { derivedStateOf {
        val settings = settingsState.value
        ThemeAppearance(settings.dynamicColor, settings.themeId, settings.displaySetting.fontSettings)
    } }
    val motionPolicy = rememberSystemMotionPolicy()

    val colorMode by rememberColorMode()
    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }


    val context = LocalContext.current
    var systemContrast by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        if (Build.VERSION.SDK_INT >= 34) {
            val manager = context.getSystemService(UiModeManager::class.java)
            systemContrast = manager?.contrast ?: 0f
            val listener = UiModeManager.ContrastChangeListener { systemContrast = it }
            manager?.addContrastChangeListener(context.mainExecutor, listener)
            onDispose { manager?.removeContrastChangeListener(listener) }
        } else onDispose { }
    }
    val configuration = LocalConfiguration.current
    val colorScheme = remember(context, configuration, darkTheme, appearance.dynamicColor, appearance.themeId) {
        when {
            appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> findPresetTheme(appearance.themeId).getColorScheme(dark = true)
            else -> findPresetTheme(appearance.themeId).getColorScheme(dark = false)
        }
    }
    val colorSchemeConverted = remember(darkTheme, colorScheme, systemContrast) {
        colorScheme.withLastChatContrast(darkTheme, systemContrast)
    }
    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors
    val statusBarColor = colorSchemeConverted.background

    // 更新状态栏图标颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = statusBarColor.luminance() > 0.5f
                isAppearanceLightNavigationBars = !darkTheme
            }
            view.layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.statusBarColor = statusBarColor.toArgb()
        }
    }

    CompositionLocalProvider(
        LocalMotionPolicy provides motionPolicy,
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalDarkMode provides darkTheme,
        LocalSystemContrast provides systemContrast,
        LocalExtendColors provides extendColors
    ) {
        // Create typography from font settings
        val typography = rememberTypographyFromFontSettings(appearance.fontSettings)
        
        MaterialTheme(
            colorScheme = colorSchemeConverted,
            typography = typography,
            shapes = Shapes,
            content = content
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
