package me.rerere.rikkahub.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

@RunWith(AndroidJUnit4::class)
class HybridMemorySearchBenchmarkTest {
    private lateinit var database: AppDatabase

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun close() = database.close()

    @Test
    fun localFtsCandidateRetrieval_p95Under300msAt50000Messages() = runBlocking {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            val row = db.compileStatement("INSERT INTO memory_search_row(source_kind,source_ref_id,assistant_id,conversation_id,message_id,speaker,event_start,event_end,recorded_at,active,text,embedding,embedding_blob,embedding_model_id) VALUES('RAW_CHAT',?,?,?,?, 'USER',0,0,?,1,?,NULL,NULL,NULL)")
            val fts = db.compileStatement("INSERT INTO memory_search_fts(rowid,text) VALUES(?,?)")
            repeat(50_000) { index ->
                val text = if (index % 997 == 0) "The user discussed the cobalt telescope project milestone $index" else "Ordinary conversation message $index"
                row.clearBindings(); row.bindString(1, "message-$index"); row.bindString(2, "assistant-1"); row.bindString(3, "conversation-${index / 100}"); row.bindString(4, "message-$index"); row.bindLong(5, index.toLong()); row.bindString(6, text)
                val id = row.executeInsert()
                fts.clearBindings(); fts.bindLong(1, id); fts.bindString(2, text); fts.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }

        database.hybridMemoryDao().search("assistant-1", "\"cobalt\"* OR \"telescope\"*", 20)
        val timings = List(20) {
            measureNanoTime { runBlocking { database.hybridMemoryDao().search("assistant-1", "\"cobalt\"* OR \"telescope\"*", 20) } } / 1_000_000.0
        }.sorted()
        val p95 = timings[(timings.size * 0.95).toInt().coerceAtMost(timings.lastIndex)]
        assertTrue("FTS p95 was ${p95}ms", p95 < 300.0)
    }
}
