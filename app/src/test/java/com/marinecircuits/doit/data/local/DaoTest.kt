package com.marinecircuits.doit.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: DoitDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), DoitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun task(id: Long, projectId: Long = 1, done: Boolean = false, generation: Long = 0) =
        TaskEntity(id, "task $id", dueAt = null, projectId = projectId, done = done, createdAt = 0, syncGeneration = generation)

    @Test
    fun openTasksSkipDoneAndArchivedProjects() = runTest {
        db.projects().insertAll(
            listOf(ProjectEntity(1, "Inbox", archived = false, 0.0), ProjectEntity(2, "Old", archived = true, 1.0)),
        )
        db.tasks().upsertAll(listOf(task(1), task(2, done = true), task(3, projectId = 2), task(-1, projectId = 99)))

        assertEquals(setOf(1L, -1L), db.tasks().open().map { it.id }.toSet())
    }

    @Test
    fun sweepKeepsCurrentLocalAndPendingTasks() = runTest {
        db.tasks().upsertAll(listOf(task(1, generation = 5), task(2, generation = 4), task(3, generation = 4), task(-1)))
        db.pendingChanges().insert(PendingChangeEntity(taskId = 3, kind = PendingChangeEntity.UPDATE, fields = "{}"))

        db.tasks().deleteNotSeenIn(5)

        assertEquals(setOf(1L, 3L, -1L), db.tasks().open().map { it.id }.toSet())
        assertNull(db.tasks().get(2))
    }

    @Test
    fun changingIdsMovesTaskAndItsPendingChange() = runTest {
        db.tasks().upsert(task(-3))
        db.pendingChanges().insert(PendingChangeEntity(taskId = -3, kind = PendingChangeEntity.CREATE, fields = "{}"))

        db.tasks().changeId(-3, 42)
        db.pendingChanges().changeTaskId(-3, 42)

        assertEquals("task -3", db.tasks().get(42)?.title)
        assertEquals(42L, db.pendingChanges().all().single().taskId)
        assertEquals(42L, db.tasks().minId())
    }
}
