package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.appLocale
import me.rerere.rikkahub.utils.currentAppLocale
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.Temporal
import java.util.TimeZone

internal object AndroidPlaceholderRuntimeValues {
    /** Location and other platform services are touched only for the tag that asks for them. */
    fun from(context: Context): PlaceholderRuntime = object : PlaceholderRuntime {
        private val now by lazy { LocalDateTime.now() }
        override val currentDate by lazy { now.toLocalDate().toDateString() }
        override val currentTime by lazy { now.toLocalTime().toTimeString() }
        override val currentDateTime by lazy { now.toDateTimeString() }
        override val timezone by lazy { TimeZone.getDefault().displayName }
        override val systemVersion by lazy { "Android SDK v${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})" }
        override val deviceInfo by lazy { "${Build.BRAND} ${Build.MODEL}" }
        override val batteryLevel by lazy { context.batteryLevel() }
        override val location by lazy { context.locationPlaceholder() }
    }

    private fun Temporal.toDateString() = DateTimeFormatter
        .ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(currentAppLocale())
        .format(this)

    private fun Temporal.toTimeString() = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(currentAppLocale())
        .format(this)

    private fun Temporal.toDateTimeString() = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(currentAppLocale())
        .format(this)

    private fun Context.batteryLevel(): String = try {
        (getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.toString() ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

    private fun Context.locationPlaceholder(): String {
        val unavailable = getString(R.string.placeholder_location_unavailable)
        return try {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(
                    this, android.Manifest.permission.ACCESS_COARSE_LOCATION,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return unavailable

            val manager = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
                ?: return unavailable
            // Provider availability and permissions can change between the permission check and lookup.
            val location = listOf(android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.GPS_PROVIDER).firstNotNullOfOrNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            } ?: return unavailable

            try {
                val geocoder = android.location.Geocoder(this, appLocale())
                @Suppress("DEPRECATION")
                val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                address?.getAddressLine(0) ?: "${location.latitude},${location.longitude}"
            } catch (_: Exception) {
                "${location.latitude},${location.longitude}"
            }
        } catch (_: Exception) {
            unavailable
        }
    }
}
