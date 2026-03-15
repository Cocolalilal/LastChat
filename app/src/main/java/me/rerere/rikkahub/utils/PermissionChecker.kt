package me.rerere.rikkahub.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant

/**
 * Utility for checking and mapping feature permissions.
 * Used to ensure required permissions are requested after backup import.
 */
object PermissionChecker {
    data class MissingFeatureAccess(
        val runtimePermissions: List<String> = emptyList(),
        val specialAccesses: List<SpecialAccess> = emptyList()
    ) {
        val isEmpty: Boolean
            get() = runtimePermissions.isEmpty() && specialAccesses.isEmpty()
    }

    enum class SpecialAccess(val description: String) {
        NotificationListener("Read device notifications (for notification-reading tools)")
    }

    /**
     * Permission requirements for different features
     */
    enum class FeaturePermission(val permissions: List<String>, val description: String) {
        NOTIFICATIONS(
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            "Notifications for notification tools"
        ),
        LOCATION(
            listOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            "Location for {{location}} placeholder"
        ),
        FINE_LOCATION(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            "Precise location"
        )
    }

    /**
     * Check which runtime permissions and special accesses are missing for the given assistants.
     */
    fun getMissingFeatureAccess(context: Context, assistants: List<Assistant>): MissingFeatureAccess {
        val requiredPermissions = mutableSetOf<String>()
        val specialAccesses = mutableSetOf<SpecialAccess>()

        for (assistant in assistants) {
            if (assistant.localTools.contains(LocalToolOption.Notifications)) {
                requiredPermissions.addAll(FeaturePermission.NOTIFICATIONS.permissions)
                if (!hasNotificationListenerAccess(context)) {
                    specialAccesses.add(SpecialAccess.NotificationListener)
                }
            }

            val locationPlaceholderPattern = "\\{\\{?location\\}?\\}".toRegex(RegexOption.IGNORE_CASE)
            val textsToCheck = buildList {
                add(assistant.systemPrompt)
                add(assistant.messageTemplate)
                addAll(assistant.quickMessages.map { it.content })
            }

            if (textsToCheck.any { locationPlaceholderPattern.containsMatchIn(it) }) {
                requiredPermissions.addAll(FeaturePermission.LOCATION.permissions)
            }
        }

        val missingPermissions = requiredPermissions.filter { permission ->
            ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

        return MissingFeatureAccess(
            runtimePermissions = missingPermissions,
            specialAccesses = specialAccesses.toList()
        )
    }

    /**
     * Check missing access specifically for notification tool onboarding.
     */
    fun getMissingNotificationAccess(context: Context): MissingFeatureAccess {
        val runtimePermissions = FeaturePermission.NOTIFICATIONS.permissions.filter { permission ->
            ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        val specialAccesses = buildList {
            if (!hasNotificationListenerAccess(context)) {
                add(SpecialAccess.NotificationListener)
            }
        }

        return MissingFeatureAccess(
            runtimePermissions = runtimePermissions,
            specialAccesses = specialAccesses
        )
    }

    /**
     * Check which permissions are missing for the given assistants.
     * @return List of permission strings that need to be requested
     */
    fun getMissingPermissions(context: Context, assistants: List<Assistant>): List<String> {
        return getMissingFeatureAccess(context, assistants).runtimePermissions
    }

    /**
     * Check if a specific permission is granted.
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if location permission is granted (either coarse or fine).
     */
    fun hasLocationPermission(context: Context): Boolean {
        return isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
               isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun hasNotificationListenerAccess(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun createSpecialAccessIntent(access: SpecialAccess): Intent {
        return when (access) {
            SpecialAccess.NotificationListener -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }

    /**
     * Get human-readable descriptions for a list of permissions.
     */
    fun getPermissionDescriptions(permissions: List<String>): List<String> {
        return permissions.mapNotNull { permission ->
            when (permission) {
                Manifest.permission.POST_NOTIFICATIONS -> "Send notifications (for notification tools)"
                Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate location (for {{location}} placeholder)"
                Manifest.permission.ACCESS_FINE_LOCATION -> "Precise location"
                Manifest.permission.CAMERA -> "Camera access"
                else -> null
            }
        }
    }

    fun getFeatureAccessDescriptions(access: MissingFeatureAccess): List<String> {
        return buildList {
            addAll(getPermissionDescriptions(access.runtimePermissions))
            addAll(access.specialAccesses.map { it.description })
        }
    }
}
