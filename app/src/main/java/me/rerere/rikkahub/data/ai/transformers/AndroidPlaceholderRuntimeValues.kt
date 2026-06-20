package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import me.rerere.rikkahub.R
import me.rerere.rikkahub.utils.appLocale
import me.rerere.rikkahub.utils.currentAppLocale
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.Temporal
import java.util.TimeZone

internal object AndroidPlaceholderRuntimeValues {
    fun from(context: Context): PlaceholderRuntimeValues {
        return PlaceholderRuntimeValues(
            currentDate = LocalDate.now().toDateString(),
            currentTime = LocalTime.now().toTimeString(),
            currentDateTime = LocalDateTime.now().toDateTimeString(),
            timezone = TimeZone.getDefault().displayName,
            systemVersion = "Android SDK v${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
            deviceInfo = "${Build.BRAND} ${Build.MODEL}",
            batteryLevel = context.batteryLevel().toString(),
            location = context.locationPlaceholder(),
        )
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

    private fun Context.batteryLevel(): Int {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun Context.locationPlaceholder(): String {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return getString(R.string.placeholder_location_unavailable)
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            ?: return "unknown"

        return try {
            val geocoder = android.location.Geocoder(this, appLocale())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            val address = addresses?.firstOrNull()
            address?.getAddressLine(0) ?: "${location.latitude},${location.longitude}"
        } catch (_: Exception) {
            "${location.latitude},${location.longitude}"
        }
    }
}
