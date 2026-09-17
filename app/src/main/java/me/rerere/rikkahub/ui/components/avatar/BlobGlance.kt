package me.rerere.rikkahub.ui.components.avatar

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A rare, intentional glance toward UI the user just touched.
 * x/y are in [-1, 1] (right, down). strength 0 = ignore.
 * [id] bumps on each pulse so renderers can decay locally without
 * recomposing the whole tree every frame.
 */
@Immutable
data class BlobGlance(
    val x: Float = 0f,
    val y: Float = 0f,
    val strength: Float = 0f,
    val id: Int = 0,
)

val LocalBlobGlance = compositionLocalOf { BlobGlance() }

fun interface BlobGlancePulse {
    fun pulse(x: Float, y: Float)
}

val LocalBlobGlancePulse = compositionLocalOf<BlobGlancePulse> {
    BlobGlancePulse { _, _ -> }
}

/** Chat cooldown so glances feel intended, not twitchy. */
private const val CHAT_COOLDOWN_MS = 3200L

@Stable
class BlobGlanceController(
    private val cooldownMs: Long = CHAT_COOLDOWN_MS,
) {
    var glance by mutableStateOf(BlobGlance())
        private set

    private var seq = 0
    private var lastPulseMs = 0L

    fun pulse(x: Float, y: Float, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPulseMs < cooldownMs) return
        lastPulseMs = now
        seq += 1
        glance = BlobGlance(
            x = x.coerceIn(-1f, 1f),
            y = y.coerceIn(-1f, 1f),
            strength = 1f,
            id = seq,
        )
    }
}
