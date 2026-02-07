package me.rerere.rikkahub.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant

/**
 * Utility for checking and mapping feature permissions.
 * Used to ensure required permissions are requested after backup import.
 */
object PermissionChecker {

    /**
     * Permission requirements for different features
     */
    enum class FeaturePermission(val permissions: List<String>, val description: String) {
        DEVICE_CONTROL(
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            "Notifications for Device Control tools"
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
     * Check which permissions are missing for the given assistants.
     * @return List of permission strings that need to be requested
     */
    fun getMissingPermissions(context: Context, assistants: List<Assistant>): List<String> {
        val requiredPermissions = mutableSetOf<String>()

        for (assistant in assistants) {
            // Check for Device Control tool
            if (assistant.localTools.contains(LocalToolOption.DeviceControl)) {
                requiredPermissions.addAll(FeaturePermission.DEVICE_CONTROL.permissions)
            }

            // Check for location placeholder in all text fields that support placeholders
            val locationPlaceholderPattern = "\\{\\{?location\\}?\\}".toRegex(RegexOption.IGNORE_CASE)
            val textsToCheck = buildList {
                add(assistant.systemPrompt)
                add(assistant.messageTemplate)
                add(assistant.spontaneousPrompt)
                addAll(assistant.quickMessages.map { it.content })
            }
            
            if (textsToCheck.any { locationPlaceholderPattern.containsMatchIn(it) }) {
                requiredPermissions.addAll(FeaturePermission.LOCATION.permissions)
            }
        }

        // Filter to only permissions not yet granted
        return requiredPermissions.filter { permission ->
            ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
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

    /**
     * Get human-readable descriptions for a list of permissions.
     */
    fun getPermissionDescriptions(permissions: List<String>): List<String> {
        return permissions.mapNotNull { permission ->
            when (permission) {
                Manifest.permission.POST_NOTIFICATIONS -> "Send notifications (for Device Control tools)"
                Manifest.permission.ACCESS_COARSE_LOCATION -> "Approximate location (for {{location}} placeholder)"
                Manifest.permission.ACCESS_FINE_LOCATION -> "Precise location"
                Manifest.permission.CAMERA -> "Camera access"
                else -> null
            }
        }
    }
}
