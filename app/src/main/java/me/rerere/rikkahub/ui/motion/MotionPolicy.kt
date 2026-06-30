package me.rerere.rikkahub.ui.motion

import android.animation.ValueAnimator
import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import me.rerere.rikkahub.Screen

private const val TOP_LEVEL_FADE_IN_DURATION_MS = 120
private const val TOP_LEVEL_FADE_OUT_DURATION_MS = 90
private const val FORWARD_BACK_SLIDE_DURATION_MS = 200
private const val FORWARD_BACK_FADE_IN_DURATION_MS = 150
private const val FORWARD_BACK_FADE_OUT_DURATION_MS = 100

internal val CHAT_ROUTE_BASE: String = Screen.Chat.serializer().descriptor.serialName
internal val MENU_ROUTE: String = Screen.Menu.serializer().descriptor.serialName
internal val SETTING_ROUTE: String = Screen.Setting.serializer().descriptor.serialName
internal val SETTING_DISPLAY_ROUTE: String = Screen.SettingDisplay.serializer().descriptor.serialName

@Stable
data class MotionPolicy(
    val reduceMotion: Boolean
)

val LocalMotionPolicy = compositionLocalOf { MotionPolicy(reduceMotion = false) }

@Composable
fun rememberSystemMotionPolicy(): MotionPolicy {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var scales by remember(resolver) { mutableStateOf(readSystemAnimationScales(resolver)) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scales = readSystemAnimationScales(resolver)
            }
        }

        listOf(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE)
        ).forEach { uri ->
            resolver.registerContentObserver(uri, false, observer)
        }

        onDispose {
            resolver.unregisterContentObserver(observer)
        }
    }

    val reduceMotion = remember(scales) {
        !ValueAnimator.areAnimatorsEnabled() ||
            scales.animatorScale <= 0f ||
            scales.transitionScale <= 0f ||
            scales.windowScale <= 0f
    }

    return remember(reduceMotion) { MotionPolicy(reduceMotion = reduceMotion) }
}

internal fun shouldUseTopLevelFade(initialRoute: String?, targetRoute: String?): Boolean {
    return isTopLevelRootRoute(initialRoute) && isTopLevelRootRoute(targetRoute)
}

internal fun isTopLevelRootRoute(route: String?): Boolean {
    if (route == null) return false
    return isChatRoute(route) || route == MENU_ROUTE
}

internal fun isChatRoute(route: String?): Boolean {
    if (route == null) return false
    return route == CHAT_ROUTE_BASE || route.startsWith("$CHAT_ROUTE_BASE/")
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.rootEnterTransition(
    motionPolicy: MotionPolicy
): EnterTransition {
    return if (
        motionPolicy.reduceMotion ||
        shouldUseTopLevelFade(initialState.destination.route, targetState.destination.route)
    ) {
        fadeIn(animationSpec = tween(TOP_LEVEL_FADE_IN_DURATION_MS))
    } else {
        slideInHorizontally(
            animationSpec = tween(
                durationMillis = FORWARD_BACK_SLIDE_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        ) { it / 2 } + fadeIn(animationSpec = tween(FORWARD_BACK_FADE_IN_DURATION_MS))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.rootExitTransition(
    motionPolicy: MotionPolicy
): ExitTransition {
    return if (
        motionPolicy.reduceMotion ||
        shouldUseTopLevelFade(initialState.destination.route, targetState.destination.route)
    ) {
        fadeOut(animationSpec = tween(TOP_LEVEL_FADE_OUT_DURATION_MS))
    } else {
        slideOutHorizontally(
            animationSpec = tween(
                durationMillis = FORWARD_BACK_SLIDE_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        ) { -it / 4 } + fadeOut(animationSpec = tween(FORWARD_BACK_FADE_OUT_DURATION_MS))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.rootPopEnterTransition(
    motionPolicy: MotionPolicy
): EnterTransition {
    return if (
        motionPolicy.reduceMotion ||
        shouldUseTopLevelFade(initialState.destination.route, targetState.destination.route)
    ) {
        fadeIn(animationSpec = tween(TOP_LEVEL_FADE_IN_DURATION_MS))
    } else {
        slideInHorizontally(
            animationSpec = tween(
                durationMillis = FORWARD_BACK_SLIDE_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        ) { -it / 4 } + fadeIn(animationSpec = tween(FORWARD_BACK_FADE_IN_DURATION_MS))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.rootPopExitTransition(
    motionPolicy: MotionPolicy
): ExitTransition {
    return if (
        motionPolicy.reduceMotion ||
        shouldUseTopLevelFade(initialState.destination.route, targetState.destination.route)
    ) {
        fadeOut(animationSpec = tween(TOP_LEVEL_FADE_OUT_DURATION_MS))
    } else {
        slideOutHorizontally(
            animationSpec = tween(
                durationMillis = FORWARD_BACK_SLIDE_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        ) { it / 2 } + fadeOut(animationSpec = tween(FORWARD_BACK_FADE_OUT_DURATION_MS))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hierarchicalEnterTransition(
    direction: AnimatedContentTransitionScope.SlideDirection,
    motionPolicy: MotionPolicy
): EnterTransition {
    return if (motionPolicy.reduceMotion) {
        fadeIn(animationSpec = tween(TOP_LEVEL_FADE_IN_DURATION_MS))
    } else {
        slideInHorizontally(
            animationSpec = tween(
                durationMillis = FORWARD_BACK_SLIDE_DURATION_MS,
                easing = FastOutSlowInEasing
            ),
            initialOffsetX = { fullWidth ->
                if (direction == AnimatedContentTransitionScope.SlideDirection.Left) {
                    fullWidth
                } else {
                    -fullWidth
                }
            }
        ) + fadeIn(animationSpec = tween(FORWARD_BACK_FADE_IN_DURATION_MS))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.hierarchicalExitTransition(
    direction: AnimatedContentTransitionScope.SlideDirection,
    motionPolicy: MotionPolicy
): ExitTransition {
    return if (motionPolicy.reduceMotion) {
        fadeOut(animationSpec = tween(TOP_LEVEL_FADE_OUT_DURATION_MS))
    } else {
        slideOutHorizontally(
            animationSpec = tween(
                durationMillis = FORWARD_BACK_SLIDE_DURATION_MS,
                easing = FastOutSlowInEasing
            ),
            targetOffsetX = { fullWidth ->
                if (direction == AnimatedContentTransitionScope.SlideDirection.Left) {
                    -fullWidth
                } else {
                    fullWidth
                }
            }
        ) + fadeOut(animationSpec = tween(FORWARD_BACK_FADE_OUT_DURATION_MS))
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.lateralEnterTransition(
    offset: (fullWidth: Int) -> Int,
    motionPolicy: MotionPolicy
): EnterTransition {
    return if (motionPolicy.reduceMotion) {
        fadeIn(animationSpec = tween(TOP_LEVEL_FADE_IN_DURATION_MS))
    } else {
        slideInHorizontally(
            animationSpec = tween(
                durationMillis = FORWARD_BACK_SLIDE_DURATION_MS,
                easing = FastOutSlowInEasing
            ),
            initialOffsetX = offset
        )
    }
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.lateralExitTransition(
    offset: (fullWidth: Int) -> Int,
    motionPolicy: MotionPolicy
): ExitTransition {
    return if (motionPolicy.reduceMotion) {
        fadeOut(animationSpec = tween(TOP_LEVEL_FADE_OUT_DURATION_MS))
    } else {
        slideOutHorizontally(
            animationSpec = tween(
                durationMillis = FORWARD_BACK_SLIDE_DURATION_MS,
                easing = FastOutSlowInEasing
            ),
            targetOffsetX = offset
        )
    }
}

private fun readSystemAnimationScales(resolver: ContentResolver): SystemAnimationScales {
    return SystemAnimationScales(
        animatorScale = readGlobalFloatSetting(
            resolver = resolver,
            name = Settings.Global.ANIMATOR_DURATION_SCALE
        ),
        transitionScale = readGlobalFloatSetting(
            resolver = resolver,
            name = Settings.Global.TRANSITION_ANIMATION_SCALE
        ),
        windowScale = readGlobalFloatSetting(
            resolver = resolver,
            name = Settings.Global.WINDOW_ANIMATION_SCALE
        )
    )
}

private fun readGlobalFloatSetting(
    resolver: ContentResolver,
    name: String
): Float {
    return runCatching { Settings.Global.getFloat(resolver, name, 1f) }
        .getOrDefault(1f)
}

private data class SystemAnimationScales(
    val animatorScale: Float,
    val transitionScale: Float,
    val windowScale: Float
)
