package me.rerere.rikkahub.data.memory

import android.util.Log
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.entity.MemEdgeType
import me.rerere.rikkahub.data.db.entity.MemNodeType
import me.rerere.rikkahub.data.db.entity.MemScope
import me.rerere.rikkahub.data.db.entity.MemSource
import me.rerere.rikkahub.data.db.entity.MemStatus
import me.rerere.rikkahub.data.db.entity.MemoryEdgeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeEntity
import me.rerere.rikkahub.data.db.entity.MemoryNodeFtsEntity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

/**
 * §7.1 / §13 retrieval-latency benchmark on a 5k-node store.
 *
 * §7.1 sets a <50ms local-DB budget for pre-generation retrieval (core sheet cached, FTS + one
 * vector scan + 1-hop over indexed edges — no model calls). This test builds a realistic 5,000-node
 * graph in an in-memory Room database and measures the store-side cost of the pieces `MemoryRecall`
 * assembles synchronously before generation:
 *   core-sheet candidates → FTS query recall → 1-hop edge expansion → recent-episode strip.
 *
 * The store scales to 5k without the retrieval set growing because the injectable set is bounded to
 * ACTIVE nodes and reads go through the §3.2 indices. The hard assertion is a *loose* upper bound so
 * it is a regression guard, not a flaky perf gate on slow emulators; the actual average is logged so
 * a real device number can be read off (`adb logcat -s MemoryRetrievalLatency`).
 */
@RunWith(AndroidJUnit4::class)
class MemoryRetrievalLatencyTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: MemoryGraphRepository

    private val assistantId = "00000000-0000-0000-0000-0000000000aa"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = MemoryGraphRepository(
            db = db,
            nodeDao = db.memoryNodeDao(),
            edgeDao = db.memoryEdgeDao(),
            provenanceDao = db.memoryProvenanceDao(),
            activityDao = db.memoryActivityDao(),
            conversationStateDao = db.memoryConversationStateDao(),
            scopeLocks = MemoryScopeLocks(),
        )
        seedStore()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun retrieval_on5kNodeStore_staysWithinLooseBudget() = runBlocking {
        // Warm up (page caches, prepared statements) before measuring.
        repeat(5) { retrievalRound(it) }

        val iterations = 25
        val start = System.nanoTime()
        repeat(iterations) { retrievalRound(it) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        val avgMs = elapsedMs / iterations

        Log.i(TAG, "avg composite retrieval over 5k-node store: %.2f ms (%d iterations)".format(avgMs, iterations))
        assertTrue(
            "average retrieval ${avgMs}ms exceeded the loose regression budget ${MAX_AVG_MS}ms",
            avgMs < MAX_AVG_MS,
        )
    }

    /** One composite retrieval, mirroring what `MemoryRecall` reads from the store per turn. */
    private suspend fun retrievalRound(i: Int) {
        // Core-sheet candidates (bounded to ACTIVE/PROVISIONAL by the visibility query).
        repo.getVisibleInjectable(assistantId)
        // Query-relevant recall: FTS seed → 1-hop edge expansion.
        val seeds = repo.searchFts(assistantId, "topic${i % TOPIC_COUNT}", limit = 20)
        repo.getEdgesTouchingAny(seeds)
        // Recent-episode strip.
        repo.getRecentEpisodes(assistantId, limit = 3)
    }

    private fun seedStore() = runBlocking {
        val nodeDao = db.memoryNodeDao()
        val edgeDao = db.memoryEdgeDao()
        val now = System.currentTimeMillis()

        db.withTransaction {
            // ~200 entity hubs.
            val entityIds = ArrayList<String>(ENTITY_COUNT)
            repeat(ENTITY_COUNT) { e ->
                val id = Uuid.random().toString()
                entityIds.add(id)
                nodeDao.upsert(
                    node(id, MemNodeType.ENTITY, "entity $e", now, importance = 3, label = "entity$e")
                )
                nodeDao.insertFts(MemoryNodeFtsEntity(id, "entity $e", "entity$e"))
            }

            // The rest split across facts + episodes, each ABOUT a random entity, with FTS tokens.
            val nonEntity = TOTAL_NODES - ENTITY_COUNT
            repeat(nonEntity) { k ->
                val id = Uuid.random().toString()
                val isEpisode = k % 3 == 0
                val type = if (isEpisode) MemNodeType.EPISODE else MemNodeType.FACT
                // A few % stay global-scope to exercise the (scope=0 OR owner=..) predicate.
                val global = k % 20 == 0
                val content = "topic${k % TOPIC_COUNT} detail $k about the user's life and studies"
                nodeDao.upsert(
                    node(
                        id = id,
                        type = type,
                        content = content,
                        now = now - k * 1000L,
                        importance = 1 + (k % 5),
                        global = global,
                        episode = isEpisode,
                    )
                )
                nodeDao.insertFts(MemoryNodeFtsEntity(id, content, ""))
                val entityId = entityIds[k % entityIds.size]
                edgeDao.upsert(
                    MemoryEdgeEntity(
                        id = Uuid.random().toString(),
                        fromId = id,
                        toId = entityId,
                        type = MemEdgeType.ABOUT,
                        createdAt = now,
                    )
                )
            }
        }
    }

    private fun node(
        id: String,
        type: Int,
        content: String,
        now: Long,
        importance: Int,
        label: String? = null,
        global: Boolean = false,
        episode: Boolean = false,
    ) = MemoryNodeEntity(
        id = id,
        type = type,
        scope = if (global) MemScope.GLOBAL_USER else MemScope.CHARACTER,
        ownerAssistantId = if (global) null else assistantId,
        content = content,
        displayLabel = label,
        importance = importance,
        status = MemStatus.ACTIVE,
        eventStart = if (episode) now else null,
        recordedAt = now,
        lastConfirmedAt = now,
        lastAccessedAt = now,
        source = MemSource.EXTRACTED,
    )

    companion object {
        private const val TAG = "MemoryRetrievalLatency"
        private const val TOTAL_NODES = 5_000
        private const val ENTITY_COUNT = 200
        private const val TOPIC_COUNT = 64

        /** Loose regression bound; the §7.1 target is 50ms but emulators run far slower than devices. */
        private const val MAX_AVG_MS = 400.0
    }
}
