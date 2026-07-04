package me.rerere.rikkahub.data.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Frame-scoped retrieval (§6.2): episodes are labelled by their frame so roleplay and chat memories
 * read differently ("*[roleplay, about a week ago]* …" vs. a plain chat episode). The verbalization
 * is pure ([MemoryRecallLogic]); the frame set itself comes from the character profile
 * ([CharacterProfileLogic]), so both are exercised here without Room or a model.
 */
class MemoryFrameRetrievalTest {

    // ---------------- episode line formatting ----------------

    @Test
    fun roleplayEpisodeIsLabelledWithFrameAndAge() {
        val line = MemoryRecallLogic.episodeLine(
            content = "we played at learning together",
            frameLabel = "roleplay",
            ageLabel = "about a week ago",
        )
        assertEquals("*[roleplay, about a week ago]* we played at learning together", line)
    }

    @Test
    fun chatEpisodeWithoutFrameHasNoBrackets() {
        val line = MemoryRecallLogic.episodeLine("we talked about your exams", frameLabel = null, ageLabel = "yesterday")
        assertEquals("*[yesterday]* we talked about your exams", line)
    }

    @Test
    fun timeAwarenessOffDropsAgeButKeepsFrame() {
        // With no age label (time awareness off) only the frame remains — the roleplay/chat split.
        assertEquals("*[roleplay]* content", MemoryRecallLogic.episodeLine("content", "roleplay", null))
        assertEquals("content", MemoryRecallLogic.episodeLine("content", null, null))
    }

    @Test
    fun prefixTrimsAndCombinesInOrder() {
        assertEquals("*[study-buddy chat, a few days ago]* ", MemoryRecallLogic.episodePrefix(" study-buddy chat ", " a few days ago "))
        assertEquals("", MemoryRecallLogic.episodePrefix(null, null))
        assertEquals("", MemoryRecallLogic.episodePrefix("  ", ""))
    }

    // ---------------- profile → frame set (roleplay vs chat) ----------------

    @Test
    fun profileParsesRoleplayAndChatFramesDistinctly() {
        val profile = CharacterProfileLogic.parse(
            """
            {
              "frames": [
                {"label": "roleplay", "descriptor": "immersive scenes", "roleplay": true},
                {"label": "study-buddy chat", "descriptor": "helping with exams", "roleplay": false}
              ],
              "persona_relation": "a close study partner",
              "care_abouts": ["exams", "sleep schedule"],
              "curiosity_tone": "warm and low-pressure"
            }
            """.trimIndent()
        )
        assertNotNull(profile)
        requireNotNull(profile)
        assertEquals(2, profile.frames.size)
        val roleplay = profile.frames.first { it.label == "roleplay" }
        val chat = profile.frames.first { it.label == "study-buddy chat" }
        assertTrue(roleplay.roleplay)
        assertTrue(!chat.roleplay)
        assertEquals("a close study partner", profile.personaRelation)
        assertTrue(profile.careAbouts.contains("exams"))
    }

    @Test
    fun framelessProfileDegradesToSingleChatFrame() {
        val profile = CharacterProfileLogic.parse("""{"persona_relation":"a friend"}""")
        requireNotNull(profile)
        assertEquals(1, profile.frames.size)
        assertEquals(CharacterProfileLogic.DEFAULT_FRAME_LABEL, profile.frames.first().label)
        assertTrue(!profile.frames.first().roleplay)
    }

    @Test
    fun profileHashIsStableAndChangesWithPersona() {
        val a = CharacterProfileLogic.profileHash("Mira", "You are Mira, a tutor.")
        val b = CharacterProfileLogic.profileHash("Mira", "You are Mira, a tutor.")
        val c = CharacterProfileLogic.profileHash("Mira", "You are Mira, a pirate.")
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun defaultProfileIsFrameless() {
        val d = CharacterProfileLogic.defaultProfile()
        assertEquals(1, d.frames.size)
        assertTrue(d.isDefault)
    }
}
