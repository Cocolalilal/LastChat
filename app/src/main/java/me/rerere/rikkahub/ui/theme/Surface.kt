package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

enum class AppSurfaceLevel {
    Flat,
    Container,
    ContainerHigh,
}

fun appSurfaceColor(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    level: AppSurfaceLevel = AppSurfaceLevel.Container,
): Color {
    return when (level) {
        AppSurfaceLevel.Flat -> colorScheme.surface
        AppSurfaceLevel.Container -> if (darkTheme) {
            colorScheme.surfaceContainerLow
        } else {
            colorScheme.surfaceContainerHigh
        }
        AppSurfaceLevel.ContainerHigh -> if (darkTheme) {
            colorScheme.surfaceContainerHigh
        } else {
            colorScheme.surfaceContainerHighest
        }
    }
}

@Composable
@ReadOnlyComposable
fun appSurfaceColor(
    level: AppSurfaceLevel = AppSurfaceLevel.Container,
): Color {
    return appSurfaceColor(
        colorScheme = MaterialTheme.colorScheme,
        darkTheme = LocalDarkMode.current,
        level = level,
    )
}

@Composable
@ReadOnlyComposable
fun placedSurfaceColor(): Color {
    return appSurfaceColor(AppSurfaceLevel.Container)
}

@Composable
@ReadOnlyComposable
fun nestedSurfaceColor(): Color {
    return appSurfaceColor(AppSurfaceLevel.ContainerHigh)
}

fun settingsSurfaceColor(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
): Color {
    return if (darkTheme) {
        colorScheme.surfaceContainer
    } else {
        colorScheme.surfaceContainerHighest
    }
}

@Composable
@ReadOnlyComposable
fun settingsSurfaceColor(): Color {
    return settingsSurfaceColor(
        colorScheme = MaterialTheme.colorScheme,
        darkTheme = LocalDarkMode.current,
    )
}

fun appOutlinedBorderColor(
    colorScheme: ColorScheme,
): Color {
    return colorScheme.background
}

@Composable
@ReadOnlyComposable
fun appOutlinedBorderColor(): Color {
    return appOutlinedBorderColor(MaterialTheme.colorScheme)
}
