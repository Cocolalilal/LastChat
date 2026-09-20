package me.rerere.common.platform.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import me.rerere.rikkahub.data.share.PortableSharePayload
import me.rerere.rikkahub.data.widget.AssistantWidgetSnapshot
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL

/**
 * Resolves the LastChat App Group `NSUserDefaults` suite used by the main app,
 * WidgetKit, and the Share Extension. Falls back to writing the App Group
 * container file when the suite constructor is unavailable.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object IosAppGroupDefaults {
    val suiteName: String = AssistantWidgetSnapshot.USER_DEFAULTS_SUITE

    val userDefaults: NSUserDefaults by lazy { resolveSuiteDefaults() }

    fun writeText(key: String, value: String) {
        userDefaults.setObject(value, forKey = key)
        userDefaults.synchronize()
        writeContainerFile("$key.txt", value)
    }

    fun remove(key: String) {
        userDefaults.removeObjectForKey(key)
        userDefaults.synchronize()
        removeContainerFile("$key.txt")
    }

    fun string(key: String): String? {
        val fromDefaults = userDefaults.stringForKey(key)
        if (!fromDefaults.isNullOrBlank()) return fromDefaults
        return readContainerFile("$key.txt")
    }

    fun consumePendingShareText(): String? {
        val key = PortableSharePayload.PENDING_SHARE_TEXT_KEY
        val value = string(key)
        remove(key)
        return value?.takeIf { it.isNotBlank() }
    }

    private fun resolveSuiteDefaults(): NSUserDefaults {
        val suite = runCatching {
            NSUserDefaults(suiteName = suiteName)
        }.getOrNull()
        return suite ?: NSUserDefaults.standardUserDefaults
    }

    private fun containerUrl(): NSURL? =
        NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(suiteName)

    private fun writeContainerFile(fileName: String, value: String) {
        val url = containerUrl()?.URLByAppendingPathComponent(fileName) ?: return
        @Suppress("CAST_NEVER_SUCCEEDS")
        (value as NSString).writeToURL(
            url = url,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }

    private fun readContainerFile(fileName: String): String? {
        val url = containerUrl()?.URLByAppendingPathComponent(fileName) ?: return null
        return NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, error = null)
    }

    private fun removeContainerFile(fileName: String) {
        val url = containerUrl()?.URLByAppendingPathComponent(fileName) ?: return
        val path = url.path ?: return
        if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }
}
