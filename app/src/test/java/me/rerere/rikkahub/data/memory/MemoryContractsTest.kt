package me.rerere.rikkahub.data.memory

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.normalizeMemorySettings
import me.rerere.rikkahub.data.model.Assistant
import org.junit.Assert.*
import org.junit.Test

class MemoryContractsTest {
    @Test fun basicDreamingDoesNotEnableEmbeddingOrEpisodes() {
        val normalized=Settings(assistants=listOf(Assistant(enableMemory=true,memoryExperience="BASIC",memoryDreaming=true))).normalizeMemorySettings().assistants.single()
        assertEquals(MemoryExperience.BASIC,normalized.memoryMode())
        assertTrue(normalized.memoryDreaming)
        assertFalse(normalized.enableMemoryConsolidation)
        assertFalse(normalized.enableRecentChatsReference)
        assertFalse(normalized.ragIncludeEpisodes)
    }
    @Test fun legacyAdvancedMapsOnceWithoutReenablingDisabledMemory() {
        val a=Settings(assistants=listOf(Assistant(enableMemory=false,enableMemoryConsolidation=true))).normalizeMemorySettings().assistants.single()
        assertEquals("ADVANCED",a.memoryExperience)
        assertFalse(a.enableMemory)
        assertEquals(a,Settings(assistants=listOf(a)).normalizeMemorySettings().assistants.single())
    }
    @Test fun plansExpireWithoutBecomingCompletedEvents() {
        val plan=MemoryFact("test","Exam tomorrow",kind="plan",validUntil=100,authored=true)
        validateMemoryFact(plan)
        assertTrue(isCurrentMemory(plan,99))
        assertFalse(isCurrentMemory(plan,100))
        assertEquals("plan",plan.kind)
        assertNull(plan.occurredAt)
    }
    @Test fun durableFactsDoNotDisappearAfterInactivity() {
        val fact=MemoryFact("test","Clara is my sister",authored=true,recordedAt=1)
        assertTrue(isCurrentMemory(fact,Long.MAX_VALUE))
    }
    @Test fun missingEvidenceCannotBeCommittedAsFact() {
        assertThrows(IllegalArgumentException::class.java){validateMemoryFact(MemoryFact("test","A fabricated fact"))}
    }
    @Test fun temporaryMemoryRequiresAnExpiry() {
        assertThrows(IllegalArgumentException::class.java){validateMemoryFact(MemoryFact("test","Going to shower",kind="temporary",authored=true))}
    }
    @Test fun editingOneParagraphDoesNotAuthorizeTheOther() {
        val original=MemoryParagraph("p1","Has four cats",listOf(MemoryEvidence(origin="chat",quote="four cats")))
        val old=MemoryNote("n","Pets",paragraphs=listOf(original,MemoryParagraph("p2","Cat is brown",listOf(MemoryEvidence(origin="chat",quote="brown")))))
        val edited=editNoteParagraphs(old,"Has four cats\n\nCat is orange"){"new"}
        assertEquals(original,edited.first())
        assertFalse(edited.first().authored)
        assertTrue(edited.last().authored)
        assertTrue(edited.last().evidence.isEmpty())
    }
    @Test fun deduplicationOnlyNormalizesWhitespaceAndCase() {
        assertTrue(sameMemoryText(" Likes   tea ","likes tea"))
        assertFalse(sameMemoryText("likes tea","does not like tea"))
        assertFalse(sameMemoryText("Clara is my girlfriend","Clara was my girlfriend"))
    }
    @Test fun historicalRelationshipsAreNotCurrent() {
        assertFalse(isCurrentMemory(MemoryFact("x","Clara is my girlfriend",status="historical",authored=true),1))
    }
}
