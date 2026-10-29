package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.PendingChangeEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.local.TaskHistoryEntity
import com.marinecircuits.doit.data.sync.PendingChanges
import com.marinecircuits.doit.inMemoryDatabase
import com.marinecircuits.doit.testClock
import com.marinecircuits.doit.testSettings
import com.marinecircuits.doit.testZone
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
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

    private suspend fun markShoppingProject() {
        val projects = db.projects().all()
        db.projects().clear()
        db.projects().insertAll(projects.map { if (it.id == 1L) it.copy(description = "<p>doit:type=shopping</p>") else it })
        settings.setSyncedShoppingProjects(setOf(1))
        db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Doing")))
    }

    @Test
    fun shoppingCaptureKeepsFreeQuantityAndBlocksDoing() = runTest {
        markShoppingProject()
        val id = repository.capture("Milk #Inbox @doing @errand [ask @doing or #Work for two bags]", projectId = 2)!!
        assertEquals("Milk [ask @doing or #Work for two bags]", db.tasks().get(id)!!.title)
        assertEquals(1L, db.tasks().get(id)!!.projectId)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(id))
        repository.setDoing(id, true)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(id))
    }

    @Test
    fun movingAndEditingIntoShoppingRemoveOnlyDoing() = runTest {
        markShoppingProject()
        val id = repository.capture("Work #Work @doing @errand")!!
        repository.move(id, 1)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(id))
        repository.applyEditedTitle(id, "Milk @doing [family pack]")
        assertEquals("Milk [family pack]", db.tasks().get(id)!!.title)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(id))
        val other = repository.capture("Work #Work @doing @errand")!!
        repository.applyEditedTitle(other, "Apples #Inbox @doing [a couple]")
        assertEquals(1L, db.tasks().get(other)!!.projectId)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(other))
    }

    @Test
    fun undoCannotRestoreDoingInShoppingProject() = runTest {
        markShoppingProject()
        val id = repository.capture("Milk @errand")!!
        val task = db.tasks().get(id)!!
        repository.complete(id)
        repository.undoComplete(task, listOf("Errand", "Doing"), doingLabelId = 8)
        assertEquals(false, db.tasks().get(id)!!.done)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(id))
        assertTrue(pendingFields(id)[TaskRepository.DOING]?.toString()?.contains("\"active\":true") != true)
    }

    private suspend fun pendingFields(taskId: Long): JsonObject =
        PendingChanges.decode(db.pendingChanges().forTask(taskId)!!.fields)

    private fun millis(local: String): Long = ZonedDateTime.of(java.time.LocalDateTime.parse(local), testZone).toInstant().toEpochMilli()

    private suspend fun serverTask(id: Long, dueAt: Long? = null) =
        TaskEntity(id, "Server task", dueAt = dueAt, projectId = 1, done = false, createdAt = 0).also { db.tasks().upsert(it) }

    @Test
    fun undoDeletedTaskRecreatesCachedDetailsWithoutCancellingOriginalDelete() = runTest {
        val due = millis("2026-11-03T12:00")
        db.labels().insertAll(listOf(LabelEntity(7, "Errand")))
        val task = serverTask(10, due)
        db.taskLabels().set(10, listOf("Errand"))
        repository.delete(10)
        val entry = db.taskHistory().observeAll().first().single()
        assertTrue(repository.undoHistoryDeletion(entry.id))
        val restored = db.tasks().open().single()
        assertTrue(restored.id < 0)
        assertEquals(task.title, restored.title)
        assertEquals(due, restored.dueAt)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(restored.id))
        assertEquals(PendingChangeEntity.DELETE, db.pendingChanges().forTask(10)!!.kind)
        assertEquals(PendingChangeEntity.CREATE, db.pendingChanges().forTask(restored.id)!!.kind)
        assertTrue(db.taskHistory().observeAll().first().isEmpty())
        assertTrue(!repository.undoHistoryDeletion(entry.id))
    }

    @Test
    fun undoServerCompletionNeverQueuesUnknownFieldsOrClearsDueDate() = runTest {
        val due = millis("2026-11-03T12:00")
        val entry = TaskHistoryEntity(taskId = 10, title = "Milk", projectId = 1,
            kind = TaskHistoryEntity.COMPLETED, happenedAt = 1, dueAt = due)
        assertTrue(repository.undoHistoryCompletion(entry))
        assertEquals(due, db.tasks().get(10)!!.dueAt)
        assertEquals(JsonObject(mapOf("done" to JsonPrimitive(false))), pendingFields(10))
    }

    @Test
    fun undoUnsentCaptureRetainsDescriptionAndAvoidsReusingItsId() = runTest {
        val id = repository.capture("Milk", description = "Oat milk", projectId = 1)!!
        repository.delete(id)
        val entry = db.taskHistory().observeAll().first().single()
        assertTrue(repository.undoHistoryDeletion(entry.id))
        val restored = db.tasks().open().single()
        assertTrue(restored.id < id)
        assertEquals(JsonPrimitive("Oat milk"), pendingFields(restored.id)["description"])
    }

    @Test
    fun historyRecordsLocalCompletionUndoAndDeletion() = runTest {
        val task = serverTask(10)
        repository.complete(10)
        val completed = db.taskHistory().observeAll().first().single()
        assertEquals("completed", completed.kind)
        assertEquals(task.title, completed.title)
        repository.undoComplete(task)
        assertTrue(db.taskHistory().observeAll().first().isEmpty())
        repository.delete(10)
        val deleted = db.taskHistory().observeAll().first().single()
        assertEquals("deleted", deleted.kind)
        assertEquals(task.title, deleted.title)
        assertNull(db.tasks().get(10))
    }

    @Test
    fun shoppingCaptureKeepsQuantityLiteralAndOmitsDoing() = runTest {
        db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Doing")))
        repository.setShoppingProject(2, true)
        val id = repository.capture("Milk #Work @errand @doing [ask @doing or #Inbox]")!!
        assertEquals("Milk [ask @doing or #Inbox]", db.tasks().get(id)!!.title)
        assertEquals(2L, db.tasks().get(id)!!.projectId)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(id))
        assertTrue(settings.notices.first().any { it.contains("without @doing") })
    }

    @Test
    fun movingOrMarkingShoppingProjectRemovesDoingAndUndoCannotRestoreIt() = runTest {
        db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Doing")))
        val task = serverTask(10)
        db.taskLabels().set(10, listOf("Errand", "Doing"))
        repository.setShoppingProject(2, true)
        repository.move(10, 2)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(10))
        repository.setDoing(10, true)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(10))
        repository.complete(10)
        repository.undoComplete(task.copy(projectId = 2), listOf("Errand", "Doing"), 8)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(10))
        assertEquals(JsonPrimitive(false), (pendingFields(10)[TaskRepository.DOING] as JsonObject)["active"])
        assertEquals(JsonPrimitive(true), (pendingFields(10)[TaskRepository.DOING] as JsonObject)["required"])
        settings.clear()
        assertTrue(settings.shoppingProjects.first().isEmpty())
    }

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
    fun capturePersistsSelectedDueDateInLocalTaskAndPendingCreate() = runTest {
        db.labels().insertAll(listOf(LabelEntity(7, "Errand")))
        val id = repository.capture("Order capacitors #work @errand", dueDate = LocalDate.of(2026, 10, 27))!!
        val task = db.tasks().get(id)!!
        assertEquals("Order capacitors", task.title)
        assertEquals(2L, task.projectId)
        assertEquals(millis("2026-10-27T12:00"), task.dueAt)
        assertEquals(JsonPrimitive("2026-10-27T11:00:00Z"), pendingFields(id)["due_date"])
        assertEquals(listOf("Errand"), db.taskLabels().forTask(id))
    }

    @Test
    fun namedProjectOverridesTheDefaultAndTheOpenProject() = runTest {
        settings.setDefaultProjectId(1)
        for (openProject in listOf(null, 1L)) {
            val id = repository.capture("Order capacitors #work", projectId = openProject)!!
            assertEquals("Order capacitors", db.tasks().get(id)!!.title)
            assertEquals(2L, db.tasks().get(id)!!.projectId)
            assertEquals(JsonPrimitive(2), pendingFields(id)["project_id"])
        }
    }

    @Test
    fun sharedCaptureCombinesProjectsAndLabelsWithoutParsingTheDescription() = runTest {
        db.projects().insertAll(listOf(ProjectEntity(3, "Home office", false, 3.0)))
        db.labels().insertAll(listOf(LabelEntity(7, "Errand")))
        val description = "Read #Work @Errand at https://example.com/#Inbox"
        val id = repository.capture("Buy lamp #\"Home office\" @errand", description)!!

        assertEquals("Buy lamp", db.tasks().get(id)!!.title)
        assertEquals(3L, db.tasks().get(id)!!.projectId)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(id))
        val fields = pendingFields(id)
        assertEquals(JsonPrimitive(3), fields["project_id"])
        assertEquals(JsonArray(listOf(JsonPrimitive("Errand"))), fields[TaskRepository.LABELS])
        assertEquals(JsonPrimitive(description), fields["description"])
    }

    @Test
    fun unknownAndArchivedProjectNamesKeepTheUsualDestination() = runTest {
        db.projects().insertAll(listOf(ProjectEntity(3, "Old", true, 3.0)))
        settings.setDefaultProjectId(1)
        val text = "Buy lamp #unknown #Old"
        val defaultId = repository.capture(text)!!
        val projectId = repository.capture(text, projectId = 2)!!
        assertEquals(text, db.tasks().get(defaultId)!!.title)
        assertEquals(text, db.tasks().get(projectId)!!.title)
        assertEquals(1L, db.tasks().get(defaultId)!!.projectId)
        assertEquals(2L, db.tasks().get(projectId)!!.projectId)
    }

    @Test
    fun onlyProjectAndLabelTokensCreateNothing() = runTest {
        db.labels().insertAll(listOf(LabelEntity(7, "Errand")))
        assertNull(repository.capture("#Work"))
        assertNull(repository.capture("#Work @errand"))
        assertTrue(db.tasks().open().isEmpty())
        assertEquals(0, db.pendingChanges().count())
        assertEquals(0, localChanges)
    }

    @Test
    fun captureTurnsKnownLabelsIntoLabels() = runTest {
        db.labels().insertAll(listOf(LabelEntity(7, "Errand", "e8a33d"), LabelEntity(9, "15 min")))

        val id = repository.capture("""Order capacitors @errand @"15 min" @phone""", description = "@errand stays")!!

        assertEquals("Order capacitors @phone", db.tasks().get(id)!!.title)
        assertEquals(listOf("Errand", "15 min"), db.taskLabels().forTask(id))
        val fields = pendingFields(id)
        assertEquals(JsonPrimitive("Order capacitors @phone"), fields["title"])
        assertEquals(JsonArray(listOf(JsonPrimitive("Errand"), JsonPrimitive("15 min"))), fields[TaskRepository.LABELS])
        assertEquals(JsonPrimitive("@errand stays"), fields["description"])
    }

    @Test
    fun captureOfOnlyLabelsCreatesNothing() = runTest {
        db.labels().insertAll(listOf(LabelEntity(7, "Errand")))
        assertNull(repository.capture("@errand"))
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun captureWithoutLabelsQueuesNone() = runTest {
        val id = repository.capture("mail bob@example.com @errand")!!
        assertEquals("mail bob@example.com @errand", db.tasks().get(id)!!.title)
        assertNull(pendingFields(id)[TaskRepository.LABELS])
        assertEquals(emptyList<String>(), db.taskLabels().forTask(id))
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

    @Test
    fun editedTitleAddsLabelsAndMovesProjectWithoutDroppingExistingLabels() = runTest {
        db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Phone")))
        serverTask(10)
        db.taskLabels().set(10, listOf("Errand"))

        repository.applyEditedTitle(10, "Call bank @phone #work")

        val task = db.tasks().get(10)!!
        assertEquals("Call bank", task.title)
        assertEquals(2L, task.projectId)
        assertEquals(listOf("Errand", "Phone"), db.taskLabels().forTask(10))
        assertEquals(JsonArray(listOf(JsonPrimitive("Phone"))), pendingFields(10)[TaskRepository.LABELS])
        assertEquals(JsonPrimitive(2), pendingFields(10)["project_id"])
    }

    @Test
    fun tokensAloneKeepThePreviousTitleAndQueueLabelsAlongsideAnUnsentCapture() = runTest {
        db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Phone")))
        settings.setDefaultProjectId(1)
        val id = repository.capture("Buy lamp @errand")!!

        repository.applyEditedTitle(id, "@phone")

        assertEquals("Buy lamp", db.tasks().get(id)!!.title)
        assertEquals(listOf("Errand", "Phone"), db.taskLabels().forTask(id))
        assertEquals(
            JsonArray(listOf(JsonPrimitive("Errand"), JsonPrimitive("Phone"))),
            pendingFields(id)[TaskRepository.LABELS],
        )
    }

    @Test
    fun unknownEditTokensStayInTheTitle() = runTest {
        serverTask(10)
        repository.applyEditedTitle(10, "Call bank @missing")
        assertEquals("Call bank @missing", db.tasks().get(10)!!.title)
        assertTrue(db.taskLabels().forTask(10).isEmpty())
    }

    @Test
    fun deleteSyncedTaskReplacesAPendingUpdate() = runTest {
        serverTask(10)
        db.taskLabels().set(10, listOf("Errand"))
        repository.rename(10, "Renamed")

        repository.delete(10)

        assertNull(db.tasks().get(10))
        assertTrue(db.taskLabels().forTask(10).isEmpty())
        val change = db.pendingChanges().forTask(10)!!
        assertEquals(PendingChangeEntity.DELETE, change.kind)
        assertEquals("{}", change.fields)
    }

    @Test
    fun deleteUnsentCaptureDropsTheCreate() = runTest {
        settings.setDefaultProjectId(1)
        val id = repository.capture("Buy milk")!!

        repository.delete(id)

        assertNull(db.tasks().get(id))
        assertEquals(0, db.pendingChanges().count())
    }
}
