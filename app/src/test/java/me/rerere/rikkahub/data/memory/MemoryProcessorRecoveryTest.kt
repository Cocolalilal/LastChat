package me.rerere.rikkahub.data.memory

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.rag.EmbeddingResult
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.CancellationException
import kotlin.uuid.Uuid

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,manifest=Config.NONE)
class MemoryProcessorRecoveryTest {
    private lateinit var db:AppDatabase
    private lateinit var repository:CharacterMemoryRepository
    private var assistant=Assistant(enableMemory=true,memoryExperience="ADVANCED")
    private val owner get()=assistant.id.toString()
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(),AppDatabase::class.java).allowMainThreadQueries().build()
        repository=CharacterMemoryRepository(db,object:MemoryEmbeddingClient {
            override fun getEmbeddingModelId(assistantId:String?)="test"
            override suspend fun embedWithModelId(text:String,assistantId:String?)=EmbeddingResult(listOf(List(768){0f}),"test")
        }){if(it==owner)assistant else null}
        repository.initialise(owner)
    }
    @After fun close(){db.close()}
    private suspend fun input(text:String):MemoryInput {
        val message=UIMessage.user(text)
        val conversation=ConversationEntity(Uuid.random().toString(),owner,"source",JsonInstant.encodeToString(listOf(MessageNode.of(message))),1,2,-1,"[]",false)
        db.conversationDao().insert(conversation)
        return MemoryInput("source",text,MemoryEvidence(conversation.id,message.id.toString(),memoryHash(text),text))
    }
    private fun processor(proposal:MemoryProposal)=MemoryProcessor(repository,object:MemoryTextClient {
        override suspend fun generate(owner:String,prompt:String,maxTokens:Int)=JsonInstant.encodeToString(proposal)
    })
    private suspend fun writeNote(note:MemoryNote) {
        repository.saveNote(owner,note,null)
    }
    private fun durable(text:String="Has a cat named Milo",quote:String="My cat is Milo")=
        MemoryCandidate(text=text,source="source",quote=quote)

    @Test fun missingTemporaryExpiryDoesNotAbortSupportedAdvancedSibling()=runBlocking {
        val source=input("I might shower later. My cat is Milo.")
        val draft=processor(MemoryProposal(memories=listOf(
            MemoryCandidate(text="Will shower later",kind="temporary",source="source",quote="I might shower later"),durable()
        ))).extract(owner,listOf(source))
        repository.apply(owner,draft)
        assertEquals(listOf("Has a cat named Milo"),repository.view(owner).facts.map{it.text})
        assertTrue(draft.reviewNote.orEmpty().contains("expiry"))
    }
    @Test fun missingTemporaryExpiryDoesNotAbortSupportedBasicSibling()=runBlocking {
        assistant=assistant.copy(memoryExperience="BASIC")
        val source=input("I might shower later. My cat is Milo.")
        val draft=processor(MemoryProposal(memories=listOf(
            MemoryCandidate(text="Will shower later",kind="plan",source="source",quote="I might shower later"),durable()
        ))).extract(owner,listOf(source))
        repository.apply(owner,draft)
        assertEquals("Has a cat named Milo",repository.view(owner).notes.single().body)
    }
    @Test fun unusedUnsupportedEntityDoesNotPreventHealthyFactAndIsNotStored()=runBlocking {
        val source=input("My cat is Milo.")
        val draft=processor(MemoryProposal(memories=listOf(durable()),nodes=listOf(MemoryNode("invented","Oxford","Place")))).extract(owner,listOf(source))
        repository.apply(owner,draft)
        assertEquals(1,repository.view(owner).facts.size)
        assertTrue(repository.view(owner).nodes.isEmpty())
    }
    @Test fun factReferencingRejectedEntityIsOmittedWithoutAssigningItToUser()=runBlocking {
        val source=input("My cat is Milo.")
        val draft=processor(MemoryProposal(memories=listOf(durable(),durable("Studies in Oxford").copy(subject="invented")),
            nodes=listOf(MemoryNode("invented","Oxford","Place")))).extract(owner,listOf(source))
        repository.apply(owner,draft)
        assertEquals(listOf("Has a cat named Milo"),repository.view(owner).facts.map{it.text})
    }
    @Test fun equivalentQuoteFormattingUsesExactOriginalSpanAndHostAssignedNodeId()=runBlocking {
        val source=input("Clára studies at TU   Wien.")
        val proposal=MemoryProposal(memories=listOf(MemoryCandidate(text="Clara studies at TU Wien",source="source",quote="clara studies at TU Wien.",subject="clara-new")),
            nodes=listOf(MemoryNode("clara-new","Clara","Person")))
        val draft=processor(proposal).extract(owner,listOf(source))
        repository.apply(owner,draft)
        val view=repository.view(owner)
        assertEquals("Clára studies at TU   Wien.",view.facts.single().evidence.single().quote)
        assertNotEquals("clara-new",view.nodes.single().id)
        assertEquals(view.nodes.single().id,view.facts.single().subject)
    }
    @Test fun substringInsideAnotherWordCannotGroundNewPerson()=runBlocking {
        val source=input("My cat is named Malice.")
        val draft=processor(MemoryProposal(memories=listOf(durable("Knows Ali","My cat is named Malice").copy(objectId="ali")),
            nodes=listOf(MemoryNode("ali","Ali","Person")))).extract(owner,listOf(source))
        assertTrue(draft.changes.isEmpty())
    }
    @Test fun aKnownUniqueAliasResolvesWithoutCreatingAnotherPerson()=runBlocking {
        repository.put(owner,"node","clara",MemoryNode("clara","Clara","Person",aliases=listOf("CJ")))
        val source=input("I play chess with CJ.")
        val draft=processor(MemoryProposal(memories=listOf(MemoryCandidate(text="Plays chess with Clara",source="source",quote="I play chess with CJ",objectId="CJ")))).extract(owner,listOf(source))
        repository.apply(owner,draft)
        assertEquals("clara",repository.view(owner).facts.single().objectId)
        assertEquals(1,repository.view(owner).nodes.size)
    }
    @Test fun ambiguousAliasesDoNotMergeOrChooseAPerson()=runBlocking {
        repository.put(owner,"node","clara",MemoryNode("clara","Clara","Person",aliases=listOf("CJ")))
        repository.put(owner,"node","corin",MemoryNode("corin","Corin","Person",aliases=listOf("CJ")))
        val source=input("I play chess with CJ. My cat is Milo.")
        val draft=processor(MemoryProposal(memories=listOf(durable(),MemoryCandidate(text="Plays chess with CJ",source="source",quote="I play chess with CJ",objectId="CJ")))).extract(owner,listOf(source))
        repository.apply(owner,draft)
        assertEquals(listOf("Has a cat named Milo"),repository.view(owner).facts.map{it.text})
        assertEquals(2,repository.view(owner).nodes.size)
    }
    @Test fun badQuoteDoesNotBlockOtherSupportedFacts()=runBlocking {
        val source=input("I dislike hiking. My cat is Milo.")
        val draft=processor(MemoryProposal(memories=listOf(durable(),durable("Likes hiking","I like hiking")))).extract(owner,listOf(source))
        repository.apply(owner,draft)
        assertEquals(listOf("Has a cat named Milo"),repository.view(owner).facts.map{it.text})
    }
    @Test fun sourceAndHostDetermineStoryContextInsteadOfModelDefaultReal()=runBlocking {
        assistant=assistant.copy(memoryContext="story")
        val source=input("Clara has a cat named Milo.")
        val draft=processor(MemoryProposal(memories=listOf(durable("Clara has a cat named Milo","Clara has a cat named Milo").copy(subject="clara")),
            nodes=listOf(MemoryNode("clara","Clara","Person")))).extract(owner,listOf(source))
        repository.apply(owner,draft)
        assertEquals("story",repository.view(owner).facts.single().context)
        assertEquals("story",repository.view(owner).nodes.single().context)
    }
    @Test fun basicCorrectionsAndAdditionsDoNotResurrectReplacedParagraph()=runBlocking {
        assistant=assistant.copy(memoryExperience="BASIC")
        val imported=listOf(MemoryEvidence(origin="imported note"))
        writeNote(MemoryNote("note","Preferences",paragraphs=listOf(MemoryParagraph("tea","Likes tea",imported),MemoryParagraph("coffee","Likes coffee",imported))))
        val source=input("Now I prefer cocoa. I like jazz.")
        val proposal=MemoryProposal(memories=listOf(
            MemoryCandidate(id="note",topic="Preferences",text="Prefers cocoa",source="source",quote="Now I prefer cocoa",replaces="tea"),
            MemoryCandidate(id="note",topic="Preferences",text="Likes jazz",source="source",quote="I like jazz")))
        val draft=processor(proposal).extract(owner,listOf(source))
        repository.apply(owner,draft)
        val note=repository.view(owner).notes.single()
        assertEquals(listOf("Likes coffee","Prefers cocoa","Likes jazz"),note.paragraphs.map{it.text})
        assertEquals(imported,note.paragraphs.first().evidence)
    }
    @Test fun organizingLegacyNoteCreatesDerivedTopicWithoutChangingItsEvidenceSource()=runBlocking {
        assistant=assistant.copy(memoryExperience="BASIC")
        writeNote(MemoryNote("legacy-7","Saved memory",paragraphs=listOf(MemoryParagraph("p","My cat is Milo.",listOf(MemoryEvidence(origin="imported note"))))))
        val source=MemoryInput("source","My cat is Milo.",MemoryEvidence(messageId="legacy-7",revision=memoryHash("My cat is Milo."),quote="My cat is Milo.",origin="saved note",paragraphId="p"))
        val draft=processor(MemoryProposal(memories=listOf(durable().copy(id="legacy-7",topic="Pets")))).extract(owner,listOf(source))
        repository.apply(owner,draft)
        val notes=repository.view(owner).notes
        assertEquals("My cat is Milo.",notes.first{it.id=="legacy-7"}.body)
        val derived=notes.first{it.id!="legacy-7"}
        assertEquals("Pets",derived.title)
        assertEquals("legacy-7",derived.paragraphs.single().evidence.single().messageId)
        assertTrue(repository.evidenceValid(owner,derived.paragraphs.single().evidence.single()))
    }
    @Test fun malformedIndividualItemDoesNotDiscardHealthySiblingOrRetryWholeCall()=runBlocking {
        val source=input("My cat is Milo.")
        var calls=0
        val processor=MemoryProcessor(repository,object:MemoryTextClient {
            override suspend fun generate(owner:String,prompt:String,maxTokens:Int):String {
                calls++
                return """{"memories":[{"text":{}},{"text":"Has a cat named Milo","source":"source","quote":"My cat is Milo"}],"nodes":[],"forget":[],"extra_hint":"ignored data"}"""
            }
        })
        val draft=processor.extract(owner,listOf(source))
        repository.apply(owner,draft)
        assertEquals(1,calls)
        assertEquals(1,repository.view(owner).facts.size)
    }
    @Test fun malformedWholeOutputGetsOnlyOneRepairAttempt()=runBlocking {
        val source=input("My cat is Milo.")
        var calls=0
        val processor=MemoryProcessor(repository,object:MemoryTextClient {
            override suspend fun generate(owner:String,prompt:String,maxTokens:Int):String {
                calls++
                return if(calls==1)"{broken JSON" else JsonInstant.encodeToString(MemoryProposal(memories=listOf(durable())))
            }
        })
        repository.apply(owner,processor.extract(owner,listOf(source)))
        assertEquals(2,calls)
        assertEquals(1,repository.view(owner).facts.size)
    }
    @Test fun persistentMalformedOutputIsActionableAndDoesNotLoopOrWrite()=runBlocking {
        val source=input("My cat is Milo.")
        var calls=0
        val processor=MemoryProcessor(repository,object:MemoryTextClient {
            override suspend fun generate(owner:String,prompt:String,maxTokens:Int):String{calls++;return "not JSON"}
        })
        try{processor.extract(owner,listOf(source));fail("Expected output failure")}catch(error:MemoryOutputException){assertTrue(error.message.orEmpty().contains("JSON"))}
        assertEquals(2,calls)
        assertTrue(repository.view(owner).facts.isEmpty())
    }
    @Test fun cancelledRequestCannotPersistALateDraft()=runBlocking {
        val processor=processor(MemoryProposal(memories=listOf(MemoryCandidate(text="Likes tea",source="request",quote="like tea"))))
        try{processor.request(owner,"Remember I like tea",taskId="request-task",commitGuard={throw CancellationException("Cancelled by user")});fail("Expected cancellation")}
        catch(_:CancellationException){}
        assertTrue(repository.view(owner).drafts.isEmpty())
        assertTrue(repository.view(owner).facts.isEmpty())
    }
    @Test fun repeatedSupportedFactConsolidatesSourcesInOneBatch()=runBlocking {
        val first=input("My cat is Milo.").copy(id="first")
        val second=input("My cat is Milo.").copy(id="second")
        val draft=processor(MemoryProposal(memories=listOf(durable().copy(source="first"),durable().copy(source="second")))).extract(owner,listOf(first,second))
        repository.apply(owner,draft)
        val facts=repository.view(owner).facts
        assertEquals(1,facts.size)
        assertEquals(2,facts.single().evidence.size)
        assertEquals(2,facts.single().evidence.map{it.conversationId}.distinct().size)
    }
    @Test fun malformedOverviewFallsBackToBoundedSupportedFacts()=runBlocking {
        repository.put(owner,"fact","tea",MemoryFact("tea","Likes tea",authored=true))
        val processor=MemoryProcessor(repository,object:MemoryTextClient {
            override suspend fun generate(owner:String,prompt:String,maxTokens:Int)="not a summary JSON object"
        })
        processor.refreshSummary(owner)
        assertEquals("Likes tea",repository.metadata(owner).summary.single().text)
        assertEquals(listOf("tea"),repository.metadata(owner).summary.single().factIds)
    }
}
