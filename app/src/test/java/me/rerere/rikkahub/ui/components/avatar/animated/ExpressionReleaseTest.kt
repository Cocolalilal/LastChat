package me.rerere.rikkahub.ui.components.avatar.animated

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExpressionReleaseTest {
    @Test fun asymmetricQuestionResolvesInOnePromptMonotonicMovement() {
        for (source in listOf("confused", "confused left")) {
            val face = ExpressionDirector(Random(8))
            face.jump(source)
            face.hint = if (TransitionRules.promptRelease(source, "Neutral")) {
                ExpressionDirector.Style.Soft
            } else ExpressionDirector.Style.Settle
            face.want("Neutral")
            var previous = 0f
            repeat(42) {
                face.update(1f / 120f)
                val weight = face.weightOf("Neutral")
                assertTrue("release reverses", weight >= previous)
                assertTrue("release overshoots", weight <= 1f)
                previous = weight
            }
            assertTrue("asymmetric eye lingers beyond 350 ms", previous > 0.96f)
        }
        assertTrue("sleep should retain its gradual release", !TransitionRules.promptRelease("Sleeping", "Neutral"))
    }
}
