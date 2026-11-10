package me.rerere.rikkahub.data.config

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import me.rerere.rikkahub.R
import org.xmlpull.v1.XmlPullParser

/** Bundled configuration is available even in builds without google-services.json. */
class AppRemoteConfig(context: Context) {
    private val defaults = buildMap<String, String> {
        context.resources.getXml(R.xml.remote_config_defaults).use { parser ->
            var key: String? = null
            var value: String? = null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "key" -> key = parser.nextText().trim()
                        "value" -> value = parser.nextText()
                    }
                    XmlPullParser.END_TAG -> if (parser.name == "entry") {
                        val entryKey = key
                        val entryValue = value
                        if (entryKey != null && entryValue != null) put(entryKey, entryValue)
                        key = null
                        value = null
                    }
                }
                parser.next()
            }
        }
    }
    // FirebaseInitProvider initializes the default app before Application.onCreate,
    // but intentionally leaves it absent when the build has no Firebase resources.
    private val firebase = FirebaseApp.getApps(context)
        .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        ?.let { FirebaseRemoteConfig.getInstance(it) }

    fun initialize() {
        firebase?.apply {
            setConfigSettingsAsync(remoteConfigSettings {
                minimumFetchIntervalInSeconds = 1800
            })
            setDefaultsAsync(defaults)
            fetchAndActivate()
        }
    }

    fun getString(key: String): String {
        val value = firebase?.getValue(key)
        // A fetched empty value is intentional and must not resurrect a bundled value.
        return if (value != null && value.source != FirebaseRemoteConfig.VALUE_SOURCE_STATIC) {
            value.asString()
        } else {
            defaults[key].orEmpty()
        }
    }
}
