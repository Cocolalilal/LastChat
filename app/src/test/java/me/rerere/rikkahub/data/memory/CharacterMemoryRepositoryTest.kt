package me.rerere.rikkahub.data.memory

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.rag.EmbeddingResult
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.uuid.Uuid

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, manifest = Config.NONE)
class CharacterMemoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: CharacterMemoryRepository
    private lateinit var assistant: Assistant
    private lateinit var other: Assistant
    private val owner get()=assistant.id.toString()
    private val embedding=object: MemoryEmbeddingClient {
        override fun getEmbeddingModelId(assistantId:String?)="test-recipe"
        override suspend fun embedWithModelId(text:String,assistantId:String?)=EmbeddingResult(listOf(List(768){if(it==0)1f else 0f}),"test-recipe")
    }
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).allowMainThreadQueries().build()
        assistant=Assistant(enableMemory=true,memoryExperience="BASIC")
        other=Assistant(enableMemory=true,memoryExperience="BASIC")
        repository=CharacterMemoryRepository(db,embedding){id->listOf(assistant,other).firstOrNull{it.id.toString()==id}}
    }
    @After fun close(){db.close()}
    private fun note(id:String="n",text:String="User likes tea")=MemoryNote(id,"Preferences",paragraphs=listOf(MemoryParagraph("p",text,authored=true)))
    private suspend fun draft(changes:List<MemoryChange>)=MemoryDraft(Uuid.random().toString(),"test",changes,repository.metadata(owner).policyRevision)
    private suspend fun source(text:String):Pair<ConversationEntity,MemoryEvidence> {
        val message=UIMessage.user(text)
        val row=ConversationEntity("conversation",owner,"test",JsonInstant.encodeToString(listOf(MessageNode.of(message))),1,2,-1,"[]",false)
        db.conversationDao().insert(row)
        return row to MemoryEvidence(row.id,message.id.toString(),memoryHash(text),text)
    }

    @Test fun importsLegacyCoreOnceAndRemovesOldCopies()=runBlocking {
        db.memoryDao().insertMemory(MemoryEntity(assistantId=owner,content="Has four cats"))
        db.memoryDao().insertMemory(MemoryEntity(assistantId=owner,content="Old episode",type=1))
        repository.initialise(owner)
        repository.initialise(owner)
        assertEquals(listOf("Has four cats"),repository.view(owner).notes.map{it.body})
        assertTrue(db.memoryDao().getMemoriesOfAssistant(owner).isEmpty())
    }
    @Test fun staleManualSaveCannotOverwriteNewerEdit()=runBlocking {
        repository.initialise(owner)
        repository.saveNote(owner,note(),null)
        repository.saveNote(owner,note(text="User likes coffee"),1)
        try{repository.saveNote(owner,note(text="Stale text"),1);fail("Expected conflict")}catch(_:IllegalStateException){}
        assertEquals("User likes coffee",repository.view(owner).notes.single().body)
    }
    @Test fun unrelatedEditsDoNotInvalidateDraft()=runBlocking {
        repository.initialise(owner)
        repository.saveNote(owner,note(),null)
        val edit=draft(listOf(MemoryChange("note","n",1,note=note(text="New preference"))))
        repository.stage(owner,edit)
        repository.saveNote(owner,note(id="other"),null)
        repository.apply(owner,edit)
        assertEquals("New preference",repository.view(owner).notes.first{it.id=="n"}.body)
    }
    @Test fun multiRecordConflictCommitsNothing()=runBlocking {
        repository.initialise(owner)
        repository.saveNote(owner,note(),null)
        val edits=draft(listOf(MemoryChange("note","new",note=note("new")),MemoryChange("note","n",9,note=note(text="wrong"))))
        try{repository.apply(owner,edits);fail("Expected conflict")}catch(_:IllegalStateException){}
        assertEquals(listOf("n"),repository.view(owner).notes.map{it.id})
    }
    @Test fun ownerScopeDoesNotExposeAnotherCharactersNote()=runBlocking {
        repository.initialise(owner)
        repository.saveNote(owner,note(),null)
        assertTrue(repository.view(other.id.toString()).notes.isEmpty())
        val forged=MemoryDraft("forged","edit",listOf(MemoryChange("note","n",1,note=note())),0)
        try{repository.apply(other.id.toString(),forged);fail("Expected conflict")}catch(_:IllegalStateException){}
    }
    @Test fun removedOwnerCannotCommit()=runBlocking {
        val oldOwner=owner
        assistant=assistant.copy(id=Uuid.random())
        try{repository.saveNote(oldOwner,note(),null);fail("Expected missing owner")}catch(_:IllegalStateException){}
        assertTrue(db.characterMemoryDao().all(oldOwner).isEmpty())
    }
    @Test fun policyChangeRejectsPreparedDraft()=runBlocking {
        repository.initialise(owner)
        val pending=draft(listOf(MemoryChange("note","n",note=note())))
        repository.stage(owner,pending)
        repository.configure(owner){it.copy(rules="Never store addresses")}
        try{repository.apply(owner,pending);fail("Expected policy fence")}catch(_:IllegalStateException){}
        assertTrue(repository.view(owner).notes.isEmpty())
    }
    @Test fun selectingAnotherMessageVersionRetractsSupport()=runBlocking {
        repository.initialise(owner)
        val (row,evidence)=source("I live in Vienna")
        val fact=MemoryFact("f","Lives in Vienna",evidence=listOf(evidence))
        repository.apply(owner,draft(listOf(MemoryChange("fact","f",fact=fact))))
        assertEquals(1,repository.view(owner).facts.size)
        db.conversationDao().update(row.copy(nodes=JsonInstant.encodeToString(listOf(MessageNode.of(UIMessage.user("I live in Zurich"))))))
        assertTrue(repository.view(owner).facts.isEmpty())
    }
    @Test fun crossCharacterSourceIsRejectedBeforeCommit()=runBlocking {
        repository.initialise(owner)
        val (row,evidence)=source("Secret address")
        db.conversationDao().update(row.copy(assistantId=other.id.toString()))
        try{repository.apply(owner,draft(listOf(MemoryChange("fact","f",fact=MemoryFact("f","Secret address",evidence=listOf(evidence))))));fail("Expected invalid source")}catch(_:IllegalArgumentException){}
        assertTrue(repository.view(owner).facts.isEmpty())
    }
    @Test fun candidateIsInvisibleUntilAtomicActivation()=runBlocking {
        repository.initialise(owner)
        repository.saveNote(owner,note(),null)
        assistant=assistant.copy(memoryExperience="ADVANCED")
        repository.startPreparation(owner)
        val metadata=repository.metadata(owner)
        repository.applyCandidate(owner,draft(listOf(MemoryChange("fact","f",fact=MemoryFact("f","Likes tea",authored=true)))))
        assertTrue(repository.view(owner).facts.isEmpty())
        assertEquals(1,repository.candidateView(owner).facts.size)
        repository.activateCandidate(owner,metadata.preparationId)
        assertTrue(repository.view(owner).metadata.activeAdvanced)
        assertEquals(1,repository.view(owner).facts.size)
    }
    @Test fun pauseFencesCandidateResponse()=runBlocking {
        repository.initialise(owner)
        repository.startPreparation(owner)
        val pending=draft(listOf(MemoryChange("fact","f",fact=MemoryFact("f","Likes tea",authored=true))))
        repository.configure(owner){it.copy(paused=true)}
        try{repository.applyCandidate(owner,pending);fail("Expected pause fence")}catch(_:IllegalStateException){}
        assertTrue(repository.candidateView(owner).facts.isEmpty())
    }
    @Test fun forgetRedactsOnlyItsSourceFragmentAndDoesNotRetainPlaintextInReceipt()=runBlocking {
        repository.initialise(owner)
        val (_,evidence)=source("My address is Secret Road. My cat is Milo.")
        val address=evidence.copy(quote="My address is Secret Road.")
        repository.apply(owner,draft(listOf(MemoryChange("fact","f",fact=MemoryFact("f","Lives at Secret Road",evidence=listOf(address))))))
        repository.apply(owner,draft(listOf(MemoryChange("fact","f",1,delete=true))))
        val redacted=repository.redact(owner,evidence,"My address is Secret Road. My cat is Milo.")
        assertFalse(redacted.contains("Secret Road"))
        assertTrue(redacted.contains("My cat is Milo."))
        assertFalse(db.characterMemoryDao().all(owner).any{it.payload.contains("Secret Road")})
    }
    @Test fun undoRejectsConcurrentEditInsteadOfOverwritingIt()=runBlocking {
        repository.initialise(owner)
        repository.saveNote(owner,note(),null)
        val commit=repository.saveNote(owner,note(text="Coffee"),1)
        repository.saveNote(owner,note(text="Cocoa"),2)
        try{repository.undo(owner,commit);fail("Expected undo conflict")}catch(_:IllegalStateException){}
        assertEquals("Cocoa",repository.view(owner).notes.single().body)
    }
    @Test fun conversionToBasicPreservesAdvancedFactsAsReadableNotes()=runBlocking {
        repository.initialise(owner)
        repository.apply(owner,draft(listOf(MemoryChange("fact","f",fact=MemoryFact("f","Likes tea",authored=true)))))
        repository.materializeBasic(owner)
        val projected=repository.view(owner).notes.single{!it.sourceOnly}
        assertEquals("Likes tea",projected.body)
        assertEquals("f",projected.paragraphs.single().originFact)
    }
    @Test fun forgettingConvertedBasicNoteErasesItsRetainedAdvancedFact()=runBlocking {
        repository.initialise(owner)
        repository.apply(owner,draft(listOf(MemoryChange("fact","f",fact=MemoryFact("f","Likes tea",authored=true)))))
        repository.materializeBasic(owner)
        val projected=repository.view(owner).notes.single{!it.sourceOnly}
        repository.apply(owner,draft(listOf(MemoryChange("note",projected.id,projected.revision,delete=true))))
        assertTrue(repository.view(owner).facts.isEmpty())
        assertTrue(repository.view(owner).notes.isEmpty())
    }
    @Test fun pinningANoteDoesNotRetractUnchangedParagraphEvidence()=runBlocking {
        repository.initialise(owner)
        repository.saveNote(owner,note(),null)
        val evidence=MemoryEvidence(messageId="n",revision=memoryHash("User likes tea"),quote="likes tea",origin="saved note",paragraphId="p")
        assertTrue(repository.evidenceValid(owner,evidence))
        repository.saveNote(owner,note().copy(pinned=true),1)
        assertTrue(repository.evidenceValid(owner,evidence))
    }
    @Test fun basicNotesAreNotRecalledInAnotherStoryContext()=runBlocking {
        repository.initialise(owner)
        repository.saveNote(owner,note().copy(category="You"),null)
        assertEquals(1,repository.context(owner,"tea",false).size)
        assistant=assistant.copy(memoryContext="story")
        assertTrue(repository.context(owner,"tea",false).isEmpty())
    }
    @Test fun activationWaitsForNewChatsAndPreservesExistingNotes()=runBlocking {
        repository.initialise(owner)
        repository.saveNote(owner,note(),null)
        assistant=assistant.copy(memoryExperience="ADVANCED")
        repository.startPreparation(owner)
        val metadata=repository.metadata(owner)
        source("I now live in Zurich")
        assertFalse(repository.activateCandidate(owner,metadata.preparationId,requireCatchUp=true))
        assertTrue(repository.metadata(owner).preparing)
        assertEquals("User likes tea",repository.view(owner).notes.single().body)
    }
    @Test fun cancelledPreparationCannotActivateLateResponse()=runBlocking {
        repository.initialise(owner)
        repository.startPreparation(owner)
        val metadata=repository.metadata(owner)
        repository.applyCandidate(owner,draft(listOf(MemoryChange("fact","f",fact=MemoryFact("f","Likes tea",authored=true)))))
        repository.cancelPreparation(owner)
        try{repository.activateCandidate(owner,metadata.preparationId);fail("Expected cancelled preparation")}catch(_:IllegalStateException){}
        assertTrue(repository.view(owner).facts.isEmpty())
        assertTrue(repository.candidateView(owner).facts.isEmpty())
    }
    @Test fun twoNewBasicFactsShareOneTopicAndDuplicateProposalIsCollapsed()=runBlocking {
        repository.initialise(owner)
        val text="I like tea and coffee"
        val (_,evidence)=source(text)
        val proposal=MemoryProposal(memories=listOf(
            MemoryCandidate(text="Likes tea",topic="Preferences",source="source",quote="like tea"),
            MemoryCandidate(text="Likes coffee",topic="Preferences",source="source",quote="coffee"),
            MemoryCandidate(text="Likes tea",topic="Preferences",source="source",quote="like tea")))
        val processor=MemoryProcessor(repository,object:MemoryTextClient{override suspend fun generate(owner:String,prompt:String,maxTokens:Int)=JsonInstant.encodeToString(proposal)})
        val draft=processor.extract(owner,listOf(MemoryInput("source",text,evidence)))
        repository.apply(owner,draft)
        assertEquals(1,repository.view(owner).notes.size)
        assertEquals(2,repository.view(owner).notes.single().paragraphs.size)
    }
    @Test fun malformedModelResponseNeverCreatesDraftOrWritesMemory()=runBlocking {
        repository.initialise(owner)
        val processor=MemoryProcessor(repository,object:MemoryTextClient{override suspend fun generate(owner:String,prompt:String,maxTokens:Int)="{broken json"})
        try{processor.request(owner,"Remember I like tea");fail("Expected malformed output rejection")}catch(_:kotlinx.serialization.SerializationException){}
        assertTrue(repository.view(owner).notes.isEmpty())
        assertTrue(repository.view(owner).drafts.isEmpty())
    }
    @Test fun listAndReadToolsCannotExposeAnotherContext()=runBlocking {
        repository.initialise(owner)
        repository.saveNote(owner,note("real"),null)
        repository.saveNote(owner,note("story","Secret fictional history").copy(context="story"),null)
        val processor=MemoryProcessor(repository,object:MemoryTextClient{override suspend fun generate(owner:String,prompt:String,maxTokens:Int)=error("Reads must not invoke a model")})
        val tools=processor.chatTools(owner,"")
        val list=tools.first{it.name=="memory_list"}.execute(kotlinx.serialization.json.buildJsonObject{})
        assertFalse(list.toString().contains("Secret fictional history"))
        try{tools.first{it.name=="memory_read"}.execute(kotlinx.serialization.json.buildJsonObject{put("id",kotlinx.serialization.json.JsonPrimitive("story"))});fail("Expected scoped read rejection")}catch(_:IllegalStateException){}
    }
    @Test fun historicalSearchIsExplicitAndReadOnly()=runBlocking {
        repository.initialise(owner)
        repository.apply(owner,draft(listOf(MemoryChange("fact","old",fact=MemoryFact("old","Used to live in Vienna",status="historical",authored=true)))))
        val processor=MemoryProcessor(repository,object:MemoryTextClient{override suspend fun generate(owner:String,prompt:String,maxTokens:Int)=error("Reads must not invoke a model")})
        val search=processor.chatTools(owner,"").first{it.name=="memory_search"}
        val current=search.execute(kotlinx.serialization.json.buildJsonObject{put("query",kotlinx.serialization.json.JsonPrimitive("Vienna"))})
        assertFalse(current.toString().contains("Used to live"))
        val past=search.execute(kotlinx.serialization.json.buildJsonObject{put("query",kotlinx.serialization.json.JsonPrimitive("Vienna"));put("include_history",kotlinx.serialization.json.JsonPrimitive(true))})
        assertTrue(past.toString().contains("Used to live"))
        assertEquals(1,repository.view(owner).facts.size)
    }
    @Test fun modelCannotReplaceHostUserIdentityWithANewEntity()=runBlocking {
        repository.initialise(owner)
        try{repository.apply(owner,draft(listOf(MemoryChange("node","user",node=MemoryNode("user","Wrong person","Person")))));fail("Expected reserved identity rejection")}catch(_:IllegalArgumentException){}
        assertTrue(repository.view(owner).nodes.isEmpty())
    }
}
