package me.rerere.ai.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextTurnEstimatorTest {

    @Test
    fun asymmetricEwma_attacksFastOnSpikeAndDecaysSlowly() {
        // Start at 200 tokens
        val counts = listOf(200, 2000) // 10x spike
        val velocityAfterSpike = ContextTurnEstimator.estimateTurnVelocity(counts)

        // With alpha = 0.50: 0.5 * 2000 + 0.5 * 200 = 1100
        assertEquals(1100.0, velocityAfterSpike, 0.1)

        // Short turns: 10 tokens, 10 tokens
        val withDecay = ContextTurnEstimator.estimateTurnVelocity(listOf(200, 2000, 10, 10))
        // After 10: 0.12 * 10 + 0.88 * 1100 = 1.2 + 968 = 969.2
        // After next 10: 0.12 * 10 + 0.88 * 969.2 = 1.2 + 852.896 = 854.096
        // It decayed slowly (still > 800 tokens!)
        assertTrue("Velocity should decay slowly on short turns: $withDecay", withDecay > 800.0)
    }

    @Test
    fun floorEnforcement_neverDropsBelow150Tokens() {
        val tinyTurns = listOf(5, 10, 2, 8, 1)
        val velocity = ContextTurnEstimator.estimateTurnVelocity(tinyTurns)
        assertEquals(150.0, velocity, 0.001)
    }

    @Test
    fun runwayEstimation_computesSafeTurnRunway() {
        // Usable input: 10,000. Used: 4,000. Velocity: 500.
        // Remaining: 6,000. Runway: 6000 / 500 = 12 turns.
        val runway = ContextTurnEstimator.estimateRunwayTurns(
            usableInputTokens = 10_000,
            usedTokens = 4_000,
            velocityTokensPerTurn = 500.0,
        )
        assertEquals(12, runway)
    }

    @Test
    fun cooldownThresholds_differByArchetype() {
        assertEquals(4, ContextTurnEstimator.minimumCooldownTurns(ContextScaleArchetype.MICRO))
        assertEquals(6, ContextTurnEstimator.minimumCooldownTurns(ContextScaleArchetype.COMPACT))
        assertEquals(10, ContextTurnEstimator.minimumCooldownTurns(ContextScaleArchetype.VAST))

        assertEquals(512, ContextTurnEstimator.minimumCooldownTokens(ContextScaleArchetype.MICRO))
        assertEquals(1_500, ContextTurnEstimator.minimumCooldownTokens(ContextScaleArchetype.COMPACT))
        assertEquals(3_000, ContextTurnEstimator.minimumCooldownTokens(ContextScaleArchetype.VAST))
    }
}
