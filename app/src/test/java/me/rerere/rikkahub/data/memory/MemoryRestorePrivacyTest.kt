package me.rerere.rikkahub.data.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.encodeToString
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.utils.JsonInstant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=android.app.Application::class, manifest=Config.NONE)
class MemoryRestorePrivacyTest {
    private fun database(block:(File,SQLiteDatabase)->Unit) {
        val file=File.createTempFile("memory-restore-", ".db")
        val db=SQLiteDatabase.openOrCreateDatabase(file,null)
        try {
            db.execSQL("CREATE TABLE character_memory(owner TEXT,kind TEXT,id TEXT,payload TEXT,PRIMARY KEY(owner,kind,id))")
            db.execSQL("CREATE TABLE ConversationEntity(id TEXT,assistant_id TEXT,nodes TEXT)")
            db.execSQL("CREATE TABLE MemoryEntity(id INTEGER PRIMARY KEY,assistant_id TEXT,type INTEGER,content TEXT,embedding TEXT,embedding_blob BLOB,embedding_model_id TEXT)")
            block(file,db)
        } finally { db.close(); file.delete() }
    }
    private fun row(db:SQLiteDatabase,kind:String,id:String,payload:String) {
        db.insertOrThrow("character_memory",null,ContentValues().apply{put("owner","owner");put("kind",kind);put("id",id);put("payload",payload)})
    }
    private fun payload(db:SQLiteDatabase,kind:String,id:String):String? =
        db.rawQuery("SELECT payload FROM character_memory WHERE kind=? AND id=?",arrayOf(kind,id)).use { if(it.moveToFirst())it.getString(0) else null }

    @Test fun restoreKeepsUnrelatedFactWhileRemovingErasedSourceSpanAndOldDrafts() = database { file,db ->
        val text="My address is Secret Road. My cat is Milo."
        val message=UIMessage.user(text)
        db.execSQL("INSERT INTO ConversationEntity VALUES(?,?,?)",arrayOf("chat","owner",JsonInstant.encodeToString(listOf(MessageNode.of(message)))))
        val source=MemoryEvidence("chat",message.id.toString(),memoryHash(text),"My address is Secret Road.")
        val address=MemoryFact("address","Lives at Secret Road",evidence=listOf(source))
        val cat=MemoryFact("cat","Has a cat named Milo",evidence=listOf(source.copy(quote="My cat is Milo.")))
        row(db,"fact",address.id,JsonInstant.encodeToString(address))
        row(db,"fact",cat.id,JsonInstant.encodeToString(cat))
        row(db,"draft","old","private draft")
        val receipt=MemorySuppression(listOf(MemoryExclusion("chat",source.messageId,source.revision,0,source.quote.length,memoryHash(source.quote))),memoryHash(address.text))
        MemoryRestorePrivacy.merge(file,listOf(MemoryRecord("owner","suppression","address",JsonInstant.encodeToString(receipt))))
        assertNull(payload(db,"fact","address"))
        assertNotNull(payload(db,"fact","cat"))
        assertNull(payload(db,"draft","old"))
        assertFalse(payload(db,"suppression","address")!!.contains("Secret Road"))
        assertTrue(JsonInstant.decodeFromString<MemoryMetadata>(payload(db,"meta","meta")!!).paused)
    }
    @Test fun restoreRedactsSavedNoteAndItsPreUpgradeCoreCopy() = database { file,db ->
        val text="Secret Road. Has a cat."
        val note=MemoryNote("legacy-7","Saved memory",paragraphs=listOf(MemoryParagraph("legacy-7",text,listOf(MemoryEvidence(origin="imported note")))))
        row(db,"note",note.id,JsonInstant.encodeToString(note))
        db.execSQL("INSERT INTO MemoryEntity(id,assistant_id,type,content) VALUES(7,'owner',0,?)",arrayOf(text))
        val quote="Secret Road."
        val receipt=MemorySuppression(listOf(MemoryExclusion("note:legacy-7","legacy-7",memoryHash(text),0,quote.length,memoryHash(quote))),memoryHash("Lives at Secret Road"))
        MemoryRestorePrivacy.merge(file,listOf(MemoryRecord("owner","suppression","address",JsonInstant.encodeToString(receipt))))
        assertEquals("Has a cat.",JsonInstant.decodeFromString<MemoryNote>(payload(db,"note",note.id)!!).body)
        db.rawQuery("SELECT content FROM MemoryEntity WHERE id=7",null).use{assertTrue(it.moveToFirst());assertEquals("Has a cat.",it.getString(0))}
    }
    @Test fun overlappingErasuresDoNotConsumeNeighboringUnrelatedText() {
        val text="Secret Road. Has a cat."
        fun exclusion(start:Int,end:Int)=MemoryExclusion("note:n","p",memoryHash(text),start,end,memoryHash(text.substring(start,end)))
        assertEquals(" Has a cat.",redactMemoryRanges(text,listOf(exclusion(0,6),exclusion(0,12),exclusion(7,11))))
    }
    @Test fun restoreDoesNotReviveForgottenEntity() = database { file,db ->
        row(db,"node","clara",JsonInstant.encodeToString(MemoryNode("clara","Clara","Person")))
        MemoryRestorePrivacy.merge(file,listOf(MemoryRecord("owner","suppression","clara",JsonInstant.encodeToString(MemorySuppression()))))
        assertNull(payload(db,"node","clara"))
    }
}
