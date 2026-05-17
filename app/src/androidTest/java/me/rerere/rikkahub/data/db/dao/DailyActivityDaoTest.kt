package me.rerere.rikkahub.data.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.db.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyActivityDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: DailyActivityDAO

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        dao = database.dailyActivityDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recordActivityCreatesFirstCount() = runBlocking {
        dao.recordActivity("2026-05-12", 1_000L)

        val activity = dao.getActivityForDate("2026-05-12")
        assertEquals(1, activity?.messageCount)
        assertEquals(1_000L, activity?.lastMessageTime)
    }

    @Test
    fun recordActivityIncrementsExistingCountAndKeepsLatestTimestamp() = runBlocking {
        dao.recordActivity("2026-05-12", 2_000L)
        dao.recordActivity("2026-05-12", 1_000L)
        dao.recordActivity("2026-05-12", 3_000L)

        val activity = dao.getActivityForDate("2026-05-12")
        assertEquals(3, activity?.messageCount)
        assertEquals(3_000L, activity?.lastMessageTime)
    }

    @Test
    fun backfilledActivityMergeStillKeepsMaxValues() = runBlocking {
        dao.insertBackfilledActivityIfMissing("2026-05-12", 5, 2_000L)
        dao.mergeBackfilledActivity("2026-05-12", 3, 1_000L)
        dao.mergeBackfilledActivity("2026-05-12", 7, 4_000L)

        val activity = dao.getActivityForDate("2026-05-12")
        assertEquals(7, activity?.messageCount)
        assertEquals(4_000L, activity?.lastMessageTime)
    }
}
