package me.rerere.rikkahub.widget

import me.rerere.rikkahub.ui.components.avatar.animated.BodyPose
import me.rerere.rikkahub.ui.components.avatar.animated.turnCapFor

/** Stable IDs are persisted per widget; angles scale to the character's shape. */
enum class WidgetAvatarPreset(
    val id: String,
    val label: String,
    val expression: String,
    private val yawFraction: Float,
    private val pitchFraction: Float,
    private val rollDeg: Float,
) {
    Calm("calm", "Calm", "Neutral", 0.05f, -0.04f, 0f),
    Happy("happy", "Happy", "Happy", -0.34f, -0.12f, -2f),
    Cheerful("cheerful", "Cheerful", "Happy", 0.36f, 0.18f, 2f),
    Curious("curious", "Curious", "confused", -0.34f, -0.28f, 2.5f),
    Surprised("surprised", "Surprised", "Surprised", 0.18f, -0.26f, -1.8f),
    Cute("cute", "Cute", "hurt or cute", 0.18f, 0.30f, 1.4f),
    Unimpressed("unimpressed", "Unimpressed", "Bored", -0.44f, 0.08f, -2f),
    Sleepy("sleepy", "Sleepy", "Sleeping", 0.18f, 0.38f, 2.5f);

    fun bodyPose(shapeId: String, solidKind: String): BodyPose {
        val (yawCap, pitchCap) = turnCapFor(shapeId, solidKind)
        return BodyPose(yaw = yawFraction * yawCap, pitch = pitchFraction * pitchCap, roll = rollDeg)
    }

    companion object {
        fun fromId(id: String?): WidgetAvatarPreset? = entries.firstOrNull { it.id == id }
    }
}
