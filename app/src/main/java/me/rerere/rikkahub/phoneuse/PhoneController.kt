package me.rerere.rikkahub.phoneuse

/** Coordinates use screen pixels. Element indexes belong to the most recent capture only. */
data class PhoneElement(
    val index: Int,
    val className: String,
    val label: String,
    val resourceId: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val checked: Boolean?,
    val focused: Boolean,
)

data class PhoneScreen(
    val packageName: String?,
    val appLabel: String?,
    val packages: Set<String>,
    val width: Int,
    val height: Int,
    val keyboardVisible: Boolean,
    val elements: List<PhoneElement>,
    val truncated: Boolean,
)

data class PhoneApp(val packageName: String, val label: String)
enum class PhoneKey { BACK, HOME, RECENTS, NOTIFICATIONS, ENTER }

/** On-demand accessibility operations; never collects screen contents in the background. */
interface PhoneController {
    fun visiblePackages(): Set<String>
    suspend fun captureScreen(): PhoneScreen
    suspend fun screenshot(maxSide: Int): ByteArray
    suspend fun gesture(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long): Boolean
    suspend fun setText(elementIndex: Int?, text: String): Boolean
    suspend fun pressKey(key: PhoneKey): Boolean
    fun listApps(): List<PhoneApp>
    fun openApp(packageName: String): Boolean
}
