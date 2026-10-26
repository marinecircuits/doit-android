package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.PendingChangeEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.sync.PendingChanges
import com.marinecircuits.doit.inMemoryDatabase
import com.marinecircuits.doit.testClock
import com.marinecircuits.doit.testSettings
import com.marinecircuits.doit.testZone
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TaskRepositoryTest {
    private lateinit var db: DoitDatabase
    private lateinit var settings: SettingsStore
    private val idRemap = IdRemap()
    private var localChanges = 0
    private lateinit var repository: TaskRepository

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        settings = testSettings()
        repository = TaskRepository(db, settings, idRemap, testClock) { localChanges++ }
        db.projects().insertAll(
            listOf(ProjectEntity(1, "Inbox", archived = false, 1.0), ProjectEntity(2, "Work", archived = false, 2.0)),
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun pendingFields(taskId: Long): JsonObject =
        PendingChanges.decode(db.pendingChanges().forTask(taskId)!!.fields)

    private fun millis(local: String): Long = ZonedDateTime.of(java.time.LocalDateTime.parse(local), testZone).toInstant().toEpochMilli()

    private suspend fun serverTask(id: Long, dueAt: Long? = null) =
        TaskEntity(id, "Server task", dueAt = dueAt, projectId = 1, done = false, createdAt = 0).also { db.tasks().upsert(it) }

    @Test
    fun captureCreatesLocalTaskAndQueuedCreateInDefaultProject() = runTest {
        settings.setDefaultProjectId(2)

        val id = repository.capture("  Buy filament  ")!!

        assertTrue(id < 0)
        val task = db.tasks().get(id)!!
        assertEquals("Buy filament", task.title)
        assertEquals(2L, task.projectId)
        val change = db.pendingChanges().forTask(id)!!
        assertEquals(PendingChangeEntity.CREATE, change.kind)
        assertEquals(
            JsonObject(mapOf("title" to JsonPrimitive("Buy filament"), "project_id" to JsonPrimitive(2))),
            PendingChanges.decode(change.fields),
        )
        assertEquals(1, localChanges)
    }

    @Test
    fun captureIntoAGivenProject() = runTest {
        settings.setDefaultProjectId(1)
        val id = repository.capture("Order capacitors", projectId = 2)!!
        assertEquals(2L, db.tasks().get(id)!!.projectId)
        assertEquals(JsonPrimitive(2), pendingFields(id)["project_id"])
    }

    @Test
    fun captureFallsBackToFirstProjectAndIgnoresBlank() = runTest {
        assertNull(repository.capture("   "))
        val id = repository.capture("call +Bob tomorrow !3")!!
        assertEquals(1L, db.tasks().get(id)!!.projectId)
        assertEquals("call +Bob tomorrow !3", db.tasks().get(id)!!.title)
        assertTrue(repository.capture("second")!! < id)
    }

    @Test
    fun completeHidesTaskAndUndoRestoresItWithItsDate() = runTest {
        val due = millis("2026-10-26T09:30")
        val task = serverTask(10, due)

        repository.complete(10)
        assertTrue(db.tasks().open().isEmpty())
        assertEquals(JsonObject(mapOf("done" to JsonPrimitive(true))), pendingFields(10))

        repository.undoComplete(task)
        assertEquals(listOf(10L), db.tasks().open().map { it.id })
        assertEquals(
            JsonObject(mapOf("done" to JsonPrimitive(false), "due_date" to JsonPrimitive("2026-10-26T08:30:00Z"))),
            pendingFields(10),
        )
        assertEquals(1, db.pendingChanges().count())
    }

    @Test
    fun undoAfterSyncRemovedTheTaskRecreatesIt() = runTest {
        val task = serverTask(10)
        repository.complete(10)
        db.pendingChanges().clear()
        db.tasks().delete(10)

        repository.undoComplete(task)

        assertEquals(listOf(10L), db.tasks().open().map { it.id })
        assertEquals(JsonObject(mapOf("done" to JsonPrimitive(false), "due_date" to JsonNull)), pendingFields(10))
    }

    @Test
    fun undoUsesTheServerIdOnceTheCaptureWasSynced() = runTest {
        val task = db.tasks().get(repository.capture("x")!!)!!
        db.tasks().changeId(task.id, 77)
        db.pendingChanges().clear()
        idRemap.record(task.id, 77)

        repository.undoComplete(task)

        assertEquals(listOf(77L), db.tasks().open().map { it.id })
        assertEquals(PendingChangeEntity.UPDATE, db.pendingChanges().forTask(77)!!.kind)
    }

    @Test
    fun rescheduleGivesUndatedTaskNoonAndKeepsTimeOfDatedTask() = runTest {
        serverTask(10)
        serverTask(11, millis("2026-10-26T09:30"))

        repository.reschedule(10, LocalDate.of(2026, 10, 27))
        repository.reschedule(11, LocalDate.of(2026, 10, 27))

        assertEquals(millis("2026-10-27T12:00"), db.tasks().get(10)!!.dueAt)
        assertEquals(millis("2026-10-27T09:30"), db.tasks().get(11)!!.dueAt)
        assertEquals(JsonPrimitive("2026-10-27T11:00:00Z"), pendingFields(10)["due_date"])
    }

    @Test
    fun clearingTheDateQueuesNull() = runTest {
        serverTask(10, millis("2026-10-26T09:30"))
        repository.reschedule(10, null)
        assertNull(db.tasks().get(10)!!.dueAt)
        assertEquals(JsonNull, pendingFields(10)["due_date"])
    }

    @Test
    fun editQueuesOnlyChangedFields() = runTest {
        val task = serverTask(10)

        repository.edit(10, "Server task", task.dueAt, projectId = 2)
        assertEquals(JsonObject(mapOf("project_id" to JsonPrimitive(2))), pendingFields(10))

        repository.edit(10, "   ", null, projectId = 1)
        assertEquals(2L, db.tasks().get(10)!!.projectId)

        val before = localChanges
        repository.edit(10, "Server task", null, projectId = 2)
        assertEquals(before, localChanges)
    }

    @Test
    fun immediateFieldEditsKeepTheOtherTaskFields() = runTest {
        val originalDue = millis("2026-10-26T09:30")
        serverTask(10, originalDue)

        repository.rename(10, "  Revised task  ")
        repository.move(10, 2)
        repository.reschedule(10, LocalDate.parse("2026-10-27"))

        val task = db.tasks().get(10)!!
        assertEquals("Revised task", task.title)
        assertEquals(2L, task.projectId)
        assertEquals(millis("2026-10-27T09:30"), task.dueAt)
        assertEquals(JsonPrimitive("Revised task"), pendingFields(10)["title"])
        assertEquals(JsonPrimitive(2), pendingFields(10)["project_id"])
        assertEquals(JsonPrimitive("2026-10-27T08:30:00Z"), pendingFields(10)["due_date"])
    }
}
