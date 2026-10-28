package me.rerere.rikkahub.data.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import me.rerere.ai.ui.MessageNode
import me.rerere.ai.ui.currentVersionMessages
import me.rerere.rikkahub.utils.JsonInstant
import kotlinx.serialization.encodeToString
import java.io.File

/** Merge newer local erasure receipts into staging BEFORE the restored database becomes readable. */
internal object MemoryRestorePrivacy {
    fun receipts(db: SupportSQLiteDatabase): List<MemoryRecord> = db.query(
        "SELECT owner, kind, id, payload FROM character_memory WHERE kind = 'suppression'"
    ).use { cursor -> buildList {
        while(cursor.moveToNext()) add(MemoryRecord(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getString(3)))
    } }

    fun merge(file: File, local: List<MemoryRecord>) {
        if(local.isEmpty())return
        SQLiteDatabase.openDatabase(file.path,null,SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.beginTransaction()
            try {
                val incoming=db.rawQuery("SELECT owner, kind, id, payload FROM character_memory",null).use { c->buildList {
                    while(c.moveToNext())add(MemoryRecord(c.getString(0),c.getString(1),c.getString(2),c.getString(3)))
                } }
                val all=(incoming.filter{it.kind=="suppression"}+local).groupBy{it.owner}
                for((owner,records) in all) {
                    val receipts=records.map { JsonInstant.decodeFromString<MemorySuppression>(it.payload) }
                    val exclusions=receipts.flatMap{it.exclusions}
                    val hashes=receipts.map{it.textHash}.toSet()
                    val erasedIds=records.map{it.id}.toSet()
                    fun excluded(e:MemoryEvidence):Boolean {
                        val relevant=exclusions.filter{
                            if(e.origin=="saved note")it.conversationId=="note:${e.messageId}"&&(it.messageId==e.paragraphId||it.messageId=="*")
                            else it.conversationId==e.conversationId&&it.messageId==e.messageId
                        }
                        if(relevant.isEmpty())return false
                        val source=if(e.origin=="saved note"){
                            incoming.firstOrNull{it.owner==owner&&it.kind=="note"&&it.id==e.messageId}?.let{row->
                                val note=JsonInstant.decodeFromString<MemoryNote>(row.payload)
                                if(e.paragraphId!=null)note.paragraphs.firstOrNull{it.id==e.paragraphId}?.text else note.body
                            }
                        }else db.rawQuery("SELECT nodes FROM ConversationEntity WHERE id = ? AND assistant_id = ?",arrayOf(e.conversationId,owner)).use { c->if(c.moveToFirst())c.getString(0)else null }
                            ?.let{JsonInstant.decodeFromString<List<MessageNode>>(it).currentVersionMessages().firstOrNull{m->m.id.toString()==e.messageId}?.toText()}
                        if(source==null)return true
                        if(memoryHash(source)!=e.revision)return true
                        val start=source.indexOf(e.quote)
                        if(start<0)return true
                        val end=start+e.quote.length
                        return relevant.any{start<it.end&&end>it.start}
                    }
                    incoming.filter{it.owner==owner}.forEach { row ->
                        when(row.kind) {
                            "fact","candidate-fact"->{
                                val f=JsonInstant.decodeFromString<MemoryFact>(row.payload)
                                if(f.id in erasedIds||memoryHash(f.text) in hashes||f.evidence.any(::excluded))db.delete("character_memory","owner=? AND kind=? AND id=?",arrayOf(owner,row.kind,row.id))
                            }
                            "note","candidate-note"->{
                                val n=JsonInstant.decodeFromString<MemoryNote>(row.payload)
                                val safe=n.paragraphs.mapNotNull{p->
                                    if(memoryHash(p.text) in hashes||p.evidence.any(::excluded))return@mapNotNull null
                                    val fragments=exclusions.filter{it.conversationId=="note:${n.id}"&&(it.messageId==p.id||it.messageId=="*")}
                                    val text=redactMemoryRanges(p.text,fragments)?:return@mapNotNull null
                                    if(text.isBlank())null else p.copy(text=text.trim())
                                }
                                if(n.id in erasedIds||safe.isEmpty())db.delete("character_memory","owner=? AND kind=? AND id=?",arrayOf(owner,row.kind,row.id))
                                else if(safe!=n.paragraphs)db.update("character_memory",ContentValues().apply{put("payload",JsonInstant.encodeToString(n.copy(paragraphs=safe)))},"owner=? AND kind=? AND id=?",arrayOf(owner,row.kind,row.id))
                            }
                            "node","candidate-node"->if(row.id in erasedIds)db.delete("character_memory","owner=? AND kind=? AND id=?",arrayOf(owner,row.kind,row.id))
                            "history","draft","task","vector","candidate-receipt"->db.delete("character_memory","owner=? AND kind=? AND id=?",arrayOf(owner,row.kind,row.id))
                            "meta"->{
                                val meta=JsonInstant.decodeFromString<MemoryMetadata>(row.payload)
                                db.update("character_memory",ContentValues().apply{put("payload",JsonInstant.encodeToString(meta.copy(summary=emptyList(),paused=true,progress="",policyRevision=meta.policyRevision+1)))},"owner=? AND kind=? AND id=?",arrayOf(owner,row.kind,row.id))
                            }
                        }
                    }
                    // A pre-upgrade backup may still hold the original Core note rather than its
                    // imported note record. Apply its paragraph exclusions before first import.
                    db.rawQuery("SELECT id, content FROM MemoryEntity WHERE assistant_id = ? AND type = 0",arrayOf(owner)).use { cursor ->
                        while(cursor.moveToNext()) {
                            val legacyId="legacy-${cursor.getLong(0)}"
                            val original=cursor.getString(1)
                            val fragments=exclusions.filter{it.conversationId=="note:$legacyId"}
                            val text=redactMemoryRanges(original,fragments).orEmpty()
                            if(legacyId in erasedIds||memoryHash(original) in hashes||text.isBlank())db.delete("MemoryEntity","id=?",arrayOf(cursor.getLong(0).toString()))
                            else if(text!=original)db.update("MemoryEntity",ContentValues().apply{put("content",text.trim());putNull("embedding");putNull("embedding_blob");putNull("embedding_model_id")},"id=?",arrayOf(cursor.getLong(0).toString()))
                        }
                    }
                    records.groupBy{it.id}.forEach{(id,group)->
                        val merged=group.map{JsonInstant.decodeFromString<MemorySuppression>(it.payload)}
                        db.insertWithOnConflict("character_memory",null,ContentValues().apply{
                            put("owner",owner);put("kind","suppression");put("id",id)
                            put("payload",JsonInstant.encodeToString(merged.last().copy(exclusions=merged.flatMap{it.exclusions}.distinct())))
                        },SQLiteDatabase.CONFLICT_REPLACE)
                    }
                    if(incoming.none{it.owner==owner&&it.kind=="meta"})db.insertWithOnConflict("character_memory",null,ContentValues().apply{
                        put("owner",owner);put("kind","meta");put("id","meta")
                        put("payload",JsonInstant.encodeToString(MemoryMetadata(paused=true,error="Memory was paused after restoring an older backup. Review it before resuming.")))
                    },SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            }finally{db.endTransaction()}
        }
    }
}
