package com.marinecircuits.doit.data.sync

import com.marinecircuits.doit.data.Credentials
import com.marinecircuits.doit.data.IdRemap
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.PendingChangeEntity
import com.marinecircuits.doit.data.remote.VikunjaApi
import com.marinecircuits.doit.inMemoryDatabase
import com.marinecircuits.doit.testClock
import com.marinecircuits.doit.testSettings
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    private val fake = FakeVikunja()
    private val server = MockWebServer().apply { dispatcher = fake }
    private val idRemap = IdRemap()
    private lateinit var db: DoitDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repository: TaskRepository
    private lateinit var engine: SyncEngine
    private var syncedCallbacks = 0

    @Before
    fun setUp() = runTest {
        server.start()
        db = inMemoryDatabase()
        settings = testSettings()
        settings.saveCredentials(Credentials(server.url("/api/v2/"), "tk_test"))
        repository = TaskRepository(db, settings, idRemap, testClock) {}
        engine = SyncEngine(db, settings, idRemap, { VikunjaApi(it.apiBase, it.token) }) { syncedCallbacks++ }
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
    }

    private suspend fun openTitles() = db.tasks().open().map { it.title }.toSet()

    @Test
    fun shoppingCleanupUploadsDownloadedDoingAndKeepsOtherLabels() = runTest {
        fake.labels += listOf(FakeVikunja.Label(7, "Errand"), FakeVikunja.Label(8, "Doing"))
        fake.tasks[1] = FakeVikunja.Task(1, "Milk [two]", projectId = 2, labelIds = mutableListOf(7, 8))
        settings.setShoppingProject(2, true)
        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(listOf(7L), fake.tasks[1]!!.labelIds)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(1))
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun shoppingCleanupPermissionFailureRetainsRemovalForRetry() = runTest {
        fake.labels += FakeVikunja.Label(8, "Doing")
        fake.tasks[1] = FakeVikunja.Task(1, "Milk", projectId = 2, labelIds = mutableListOf(8))
        settings.setShoppingProject(2, true)
        fake.labelsRemovable = false
        val result = engine.sync()
        assertTrue(result is SyncResult.ServerError && result.message.contains("Milk"))
        assertEquals(1, db.pendingChanges().count())
        fake.labelsRemovable = true
        assertEquals(SyncResult.Synced, engine.sync())
        assertTrue(fake.tasks[1]!!.labelIds.isEmpty())
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun doingStartAndStopPreserveOtherLabels() = runTest {
        fake.labels += listOf(FakeVikunja.Label(7, "Errand"), FakeVikunja.Label(8, "Doing"))
        fake.tasks[1] = FakeVikunja.Task(1, "Work on it", labelIds = mutableListOf(7))
        engine.sync()
        repository.setDoing(1, true)
        assertEquals(listOf("Errand", "Doing"), db.taskLabels().forTask(1))
        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(listOf(7L, 8L), fake.tasks[1]!!.labelIds)
        repository.setDoing(1, false)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(1))
        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(listOf(7L), fake.tasks[1]!!.labelIds)
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun doingRequiresExistingLabelAndIgnoresCompletedTasks() = runTest {
        fake.tasks[1] = FakeVikunja.Task(1, "Work")
        engine.sync()
        repository.setDoing(1, true)
        assertEquals(0, db.pendingChanges().count())
        fake.labels += FakeVikunja.Label(8, "dOiNg")
        engine.sync()
        repository.complete(1)
        repository.setDoing(1, true)
        assertEquals(emptyList<String>(), db.taskLabels().forTask(1))
        assertEquals(SyncResult.Synced, engine.sync())
        assertFalse(fake.tasks[1]!!.labelIds.contains(8))
    }

    @Test
    fun stoppingDoingOnUnsentCapturePreservesOtherLabels() = runTest {
        fake.labels += listOf(FakeVikunja.Label(7, "Errand"), FakeVikunja.Label(8, "Doing"))
        engine.sync()
        val id = repository.capture("Work @doing @errand")!!
        repository.setDoing(id, false)
        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(listOf(7L), fake.tasks[100]!!.labelIds)
        assertEquals(0, db.pendingChanges().count())
        assertTrue(fake.requests.none { it.startsWith("PATCH") && "_doing" in it })
    }

    @Test
    fun completingRemovesDoingBeforeDoneAndUndoRestoresItAfterSync() = runTest {
        fake.labels += listOf(FakeVikunja.Label(7, "Errand"), FakeVikunja.Label(8, "Doing"))
        fake.tasks[1] = FakeVikunja.Task(1, "Work", labelIds = mutableListOf(7, 8))
        engine.sync()
        val original = db.tasks().get(1)!!
        val names = db.taskLabels().forTask(1)
        fake.requests.clear()
        repository.complete(1)
        assertEquals(listOf("Errand"), db.taskLabels().forTask(1))
        assertEquals(SyncResult.Synced, engine.sync())
        val writes = fake.requests.filter { it.startsWith("DELETE") || it.startsWith("PATCH") }
        assertEquals("DELETE tasks/1/labels/8", writes[0])
        assertTrue(writes[1].startsWith("PATCH tasks/1"))
        assertEquals(listOf(7L), fake.tasks[1]!!.labelIds)
        repository.undoComplete(original, names, doingLabelId = 8)
        assertEquals(SyncResult.Synced, engine.sync())
        assertFalse(fake.tasks[1]!!.done)
        assertEquals(listOf(7L, 8L), fake.tasks[1]!!.labelIds)
    }

    @Test
    fun repeatedTaskReopensWithoutDoing() = runTest {
        fake.labels += FakeVikunja.Label(8, "Doing")
        fake.tasks[1] = FakeVikunja.Task(1, "Repeat", labelIds = mutableListOf(8))
        fake.reopenOnComplete = true
        engine.sync()
        repository.complete(1)
        assertEquals(SyncResult.Synced, engine.sync())
        assertFalse(fake.tasks[1]!!.done)
        assertTrue(fake.tasks[1]!!.labelIds.isEmpty())
        assertTrue(db.tasks().open().any { it.id == 1L })
    }

    @Test
    fun newerDoingIntentWinsWhileTheFirstRequestIsInFlight() = runTest {
        fake.labels += FakeVikunja.Label(8, "Doing")
        fake.tasks[1] = FakeVikunja.Task(1, "Work")
        engine.sync()
        repository.setDoing(1, true)
        var changed = false
        fake.intercept = { request ->
            if (!changed && request.method == "POST" && request.requestUrl!!.encodedPath.endsWith("/tasks/1/labels")) {
                changed = true
                runBlocking { repository.setDoing(1, false) }
            }
            null
        }
        assertEquals(SyncResult.Synced, engine.sync())
        assertTrue(changed)
        assertTrue(fake.tasks[1]!!.labelIds.isEmpty())
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun stopDuringCaptureLabelUploadRemovesDoingAfterTheAdd() = runTest {
        fake.labels += listOf(FakeVikunja.Label(7, "Errand"), FakeVikunja.Label(8, "Doing"))
        engine.sync()
        val id = repository.capture("Work @doing @errand")!!
        var changed = false
        fake.intercept = { request ->
            if (!changed && request.method == "POST" && request.requestUrl!!.encodedPath.endsWith("/tasks/100/labels")) {
                changed = true
                runBlocking { repository.setDoing(id, false) }
            }
            null
        }
        assertEquals(SyncResult.Synced, engine.sync())
        assertTrue(changed)
        assertEquals(listOf(7L), fake.tasks[100]!!.labelIds)
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun stoppingDuringCreateKeepsTheNewestIntentAfterIdRemap() = runTest {
        fake.labels += listOf(FakeVikunja.Label(7, "Errand"), FakeVikunja.Label(8, "Doing"))
        engine.sync()
        val id = repository.capture("Work @doing @errand")!!
        var changed = false
        fake.intercept = { request ->
            if (!changed && request.method == "POST" && request.requestUrl!!.encodedPath.endsWith("/projects/1/tasks")) {
                changed = true
                runBlocking { repository.setDoing(id, false) }
            }
            null
        }
        assertEquals(SyncResult.Synced, engine.sync())
        assertTrue(changed)
        assertEquals(listOf(7L), fake.tasks[100]!!.labelIds)
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun editingNewLabelsDuringLabelUploadDoesNotLoseThem() = runTest {
        fake.labels += listOf(FakeVikunja.Label(7, "Errand"), FakeVikunja.Label(8, "Doing"), FakeVikunja.Label(9, "Phone"))
        fake.tasks[1] = FakeVikunja.Task(1, "Work")
        engine.sync()
        repository.applyEditedTitle(1, "Work @errand")
        var changed = false
        fake.intercept = { request ->
            if (!changed && request.method == "POST" && request.requestUrl!!.encodedPath.endsWith("/tasks/1/labels")) {
                changed = true
                runBlocking { repository.applyEditedTitle(1, "Work @phone") }
            }
            null
        }
        assertEquals(SyncResult.Synced, engine.sync())
        assertTrue(changed)
        assertEquals(listOf(7L, 9L), fake.tasks[1]!!.labelIds)
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun deletionDuringDoingPushIsNotLost() = runTest {
        fake.labels += FakeVikunja.Label(8, "Doing")
        fake.tasks[1] = FakeVikunja.Task(1, "Work")
        engine.sync()
        repository.setDoing(1, true)
        var changed = false
        fake.intercept = { request ->
            if (!changed && request.method == "POST" && request.requestUrl!!.encodedPath.endsWith("/tasks/1/labels")) {
                changed = true
                runBlocking { repository.delete(1) }
            }
            null
        }
        assertEquals(SyncResult.Synced, engine.sync())
        assertTrue(changed)
        assertNull(fake.tasks[1])
        assertNull(db.tasks().get(1))
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun rejectedTokenRetainsDoingIntent() = runTest {
        fake.labels += FakeVikunja.Label(8, "Doing")
        fake.tasks[1] = FakeVikunja.Task(1, "Work")
        engine.sync()
        repository.setDoing(1, true)
        fake.tokenValid = false
        assertEquals(SyncResult.TokenRejected, engine.sync())
        assertEquals(1, db.pendingChanges().count())
        assertEquals(listOf("Doing"), db.taskLabels().forTask(1))
    }

    @Test
    fun addingDoingInEditSupersedesAnUnsentStop() = runTest {
        fake.labels += FakeVikunja.Label(8, "Doing")
        fake.tasks[1] = FakeVikunja.Task(1, "Work", labelIds = mutableListOf(8))
        engine.sync()
        repository.setDoing(1, false)
        repository.applyEditedTitle(1, "Work @doing")
        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(listOf(8L), fake.tasks[1]!!.labelIds)
        assertEquals(listOf("Doing"), db.taskLabels().forTask(1))
    }

    @Test
    fun refusedRemovalReportsAndReconcilesWithoutLosingOtherEdits() = runTest {
        fake.labels += listOf(FakeVikunja.Label(7, "Errand"), FakeVikunja.Label(8, "Doing"))
        fake.tasks[1] = FakeVikunja.Task(1, "Work", labelIds = mutableListOf(7, 8))
        engine.sync()
        repository.setDoing(1, false)
        repository.rename(1, "Updated")
        fake.labelsRemovable = false
        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals("Updated", fake.tasks[1]!!.title)
        assertEquals(listOf(7L, 8L), fake.tasks[1]!!.labelIds)
        assertEquals(listOf("Errand", "Doing"), db.taskLabels().forTask(1))
        assertTrue(settings.notices.first().single().contains("stop working"))
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun transientFailureRetainsDoingIntentForAutomaticRetry() = runTest {
        fake.labels += FakeVikunja.Label(8, "Doing")
        fake.tasks[1] = FakeVikunja.Task(1, "Work")
        engine.sync()
        repository.setDoing(1, true)
        fake.intercept = { request ->
            if (request.method == "POST" && request.requestUrl!!.encodedPath.endsWith("/labels")) {
                FakeVikunja.json(503, """{"title":"Unavailable"}""")
            } else null
        }
        assertTrue(engine.sync() is SyncResult.ServerError)
        assertEquals(1, db.pendingChanges().count())
        assertEquals(listOf("Doing"), db.taskLabels().forTask(1))
        fake.intercept = { null }
        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(listOf(8L), fake.tasks[1]!!.labelIds)
    }

    @Test
    fun doingIntentUsesLabelIdAcrossServerRename() = runTest {
        fake.labels += FakeVikunja.Label(8, "Doing")
        fake.tasks[1] = FakeVikunja.Task(1, "Work")
        engine.sync()
        repository.setDoing(1, true)
        fake.labels[0] = FakeVikunja.Label(8, "Focus")
        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(listOf(8L), fake.tasks[1]!!.labelIds)
        assertEquals(listOf("Focus"), db.taskLabels().forTask(1))
        assertTrue(db.labels().all().none { it.title.equals("doing", ignoreCase = true) })
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun deletedDoingLabelIsNotRecreatedAndUserIsNotified() = runTest {
        fake.labels += FakeVikunja.Label(8, "Doing")
        fake.tasks[1] = FakeVikunja.Task(1, "Work")
        engine.sync()
        repository.setDoing(1, true)
        fake.labels.clear()
        assertEquals(SyncResult.Synced, engine.sync())
        assertTrue(fake.tasks[1]!!.labelIds.isEmpty())
        assertTrue(db.labels().all().isEmpty())
        assertTrue(settings.notices.first().single().contains("label no longer exists"))
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun pullsOpenTasksProjectsAndDefaultProject() = runTest {
        fake.tasks[1] = FakeVikunja.Task(1, "Pay invoice", dueDate = "2026-10-20T11:00:00Z")
        fake.tasks[2] = FakeVikunja.Task(2, "Old", done = true)
        fake.defaultProjectId = 2

        assertEquals(SyncResult.Synced, engine.sync())

        assertEquals(setOf("Pay invoice"), openTitles())
        assertEquals(listOf("Inbox", "Work"), db.projects().all().map { it.title })
        assertEquals(2L, settings.defaultProjectId.first())
        assertEquals(1, syncedCallbacks)
    }

    @Test
    fun dateChosenOfflineIsCreatedOnTheServerWithProjectAndLabel() = runTest {
        fake.labels += FakeVikunja.Label(7, "errand")
        engine.sync()
        val id = repository.capture("Order capacitors #work @errand", dueDate = LocalDate.of(2026, 10, 27))!!
        assertEquals(2L, db.tasks().get(id)!!.projectId)
        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals("2026-10-27T11:00:00Z", fake.tasks[100]!!.dueDate)
        assertEquals(2L, fake.tasks[100]!!.projectId)
        assertEquals(listOf(7L), fake.tasks[100]!!.labelIds)
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun storesParentProjects() = runTest {
        fake.intercept = { request ->
            if (request.method == "GET" && request.requestUrl!!.encodedPath.endsWith("/projects")) {
                FakeVikunja.json(
                    200,
                    """{"items":[{"id":1,"title":"Work","parent_project_id":0},{"id":2,"title":"Firmware","parent_project_id":1}],
                       "total":2,"page":1,"per_page":50,"total_pages":1}""",
                )
            } else {
                null
            }
        }

        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(mapOf(1L to 0L, 2L to 1L), db.projects().all().associate { it.id to it.parentProjectId })
    }

    @Test
    fun storesProjectColours() = runTest {
        fake.intercept = { request ->
            if (request.method == "GET" && request.requestUrl!!.encodedPath.endsWith("/projects")) {
                FakeVikunja.json(
                    200,
                    """{"items":[{"id":1,"title":"Work","hex_color":"1973ff"},{"id":2,"title":"Inbox","hex_color":""},
                       {"id":3,"title":"Home"}],"total":3,"page":1,"per_page":50,"total_pages":1}""",
                )
            } else {
                null
            }
        }

        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(mapOf(1L to "1973ff", 2L to "", 3L to ""), db.projects().all().associate { it.id to it.hexColor })
    }

    @Test
    fun projectColourIsSentAndSurvivesTheNextPull() = runTest {
        assertEquals(SyncResult.Synced, engine.sync())
        repository.setProjectColor(2, "#1565C0")
        assertEquals("1565c0", db.projects().all().single { it.id == 2L }.hexColor)

        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals("1565c0", db.projects().all().single { it.id == 2L }.hexColor)
        assertTrue(settings.pendingProjectColors().isEmpty())
        assertTrue(fake.requests.any { it.startsWith("PATCH projects/2") && "1565c0" in it })
    }

    @Test
    fun refusedProjectColourComesBackFromTheServer() = runTest {
        assertEquals(SyncResult.Synced, engine.sync())
        repository.setProjectColor(2, "c62828")
        fake.projectsUpdatable = false

        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals("", db.projects().all().single { it.id == 2L }.hexColor)
        assertTrue(settings.pendingProjectColors().isEmpty())
        assertTrue(settings.notices.first().single().contains("Work"))
    }

    @Test
    fun pullsLabelsAndTaskLabels() = runTest {
        fake.labels += listOf(FakeVikunja.Label(7, "errand", "e8a33d"), FakeVikunja.Label(9, "15 min"))
        fake.tasks[1] = FakeVikunja.Task(1, "Order capacitors", labelIds = mutableListOf(9, 7))
        fake.tasks[2] = FakeVikunja.Task(2, "Plain")

        assertEquals(SyncResult.Synced, engine.sync())

        assertEquals(listOf("15 min", "errand"), db.labels().all().map { it.title })
        assertEquals("e8a33d", db.labels().all().single { it.id == 7L }.hexColor)
        assertEquals(listOf("15 min", "errand"), db.taskLabels().forTask(1))
        assertEquals(emptyList<String>(), db.taskLabels().forTask(2))

        // Removed in the web UI, and a task that is gone takes its labels with it.
        fake.tasks[1]!!.labelIds.remove(9L)
        fake.tasks[2]!!.done = true
        fake.tasks[2]!!.labelIds += 7
        engine.sync()
        assertEquals(listOf("errand"), db.taskLabels().forTask(1))
        assertEquals(emptyList<String>(), db.taskLabels().forTask(2))
    }

    @Test
    fun tokenWithoutLabelReadKeepsKnownLabelsAndSyncs() = runTest {
        fake.labels += FakeVikunja.Label(7, "errand")
        engine.sync()
        fake.labelsReadable = false
        fake.labels.clear()

        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(listOf("errand"), db.labels().all().map { it.title })
    }

    @Test
    fun labelsCapturedOfflineAreAddedAfterTheCreate() = runTest {
        fake.labels += listOf(FakeVikunja.Label(7, "errand"), FakeVikunja.Label(9, "15 min"))
        engine.sync()
        val temporaryId = repository.capture("""Buy filament @errand @"15 min"""")!!
        repository.complete(temporaryId)

        assertEquals(SyncResult.Synced, engine.sync())

        val writes = fake.requests.filter { it.startsWith("POST") || it.startsWith("PATCH") }
        assertEquals(
            listOf(
                """POST projects/1/tasks {"title":"Buy filament","project_id":1}""",
                """POST tasks/100/labels {"label_id":7}""",
                """POST tasks/100/labels {"label_id":9}""",
                """PATCH tasks/100 {"done":true}""",
            ),
            writes,
        )
        assertEquals(listOf(7L, 9L), fake.tasks[100]!!.labelIds)
        assertEquals(0, db.pendingChanges().count())
        assertTrue(settings.notices.first().isEmpty())
    }

    @Test
    fun projectAndLabelsCapturedOfflineReachTheChosenProject() = runTest {
        fake.labels += FakeVikunja.Label(7, "errand")
        engine.sync()
        val id = repository.capture("Order capacitors #work @errand", projectId = 1)!!
        assertEquals(2L, db.tasks().get(id)!!.projectId)
        assertEquals("Order capacitors", db.tasks().get(id)!!.title)

        // The pending create keeps the explicit project even if the user's default changes before syncing.
        fake.defaultProjectId = 1
        assertEquals(SyncResult.Synced, engine.sync())

        val task = fake.tasks[100]!!
        assertEquals(2L, task.projectId)
        assertEquals("Order capacitors", task.title)
        assertEquals(listOf(7L), task.labelIds)
        assertEquals(2L, db.tasks().get(100)!!.projectId)
        assertEquals(0, db.pendingChanges().count())
        assertTrue(fake.requests.contains("""POST projects/2/tasks {"title":"Order capacitors","project_id":2}"""))
    }

    @Test
    fun capturedLabelsShowUnderTheServerIdUntilPulled() = runTest {
        fake.labels += FakeVikunja.Label(7, "errand")
        engine.sync()
        val temporaryId = repository.capture("Buy filament @errand")!!
        assertEquals(listOf("errand"), db.taskLabels().forTask(temporaryId))
        // The label request fails once: the task exists on the server, its labels are still to be sent.
        var failLabel = true
        fake.intercept = { request ->
            if (failLabel && request.requestUrl!!.encodedPath.endsWith("/labels") && request.method == "POST") {
                failLabel = false
                FakeVikunja.json(503, """{"title":"Service Unavailable"}""")
            } else {
                null
            }
        }

        assertTrue(engine.sync() is SyncResult.ServerError)
        assertEquals(emptyList<String>(), db.taskLabels().forTask(temporaryId))
        assertEquals(listOf("errand"), db.taskLabels().forTask(100))

        assertEquals(SyncResult.Synced, engine.sync())
        assertEquals(listOf(7L), fake.tasks[100]!!.labelIds)
        assertEquals(listOf("errand"), db.taskLabels().forTask(100))
    }

    @Test
    fun labelAlreadyOnTheTaskCountsAsAdded() = runTest {
        fake.labels += FakeVikunja.Label(7, "errand")
        fake.tasks[1] = FakeVikunja.Task(1, "Buy filament", labelIds = mutableListOf(7))
        engine.sync()
        db.pendingChanges().insert(
            PendingChangeEntity(
                taskId = 1,
                kind = PendingChangeEntity.UPDATE,
                fields = """{"labels":["errand"],"title":"Buy more filament"}""",
            ),
        )

        assertEquals(SyncResult.Synced, engine.sync())

        assertEquals(listOf(7L), fake.tasks[1]!!.labelIds)
        assertEquals("Buy more filament", fake.tasks[1]!!.title)
        assertEquals(0, db.pendingChanges().count())
        assertTrue(settings.notices.first().isEmpty())
    }

    @Test
    fun labelDeletedMeanwhileIsSkippedWithANotice() = runTest {
        fake.labels += FakeVikunja.Label(7, "errand")
        engine.sync()
        repository.capture("Buy milk @errand")!!
        fake.labels.clear()

        assertEquals(SyncResult.Synced, engine.sync())

        assertEquals(emptyList<Long>(), fake.tasks[100]!!.labelIds)
        assertEquals(listOf("Couldn't add labels to “Buy milk”: “errand” no longer exists"), settings.notices.first())
        assertEquals(0, db.pendingChanges().count())
        assertEquals(emptyList<String>(), db.taskLabels().forTask(100))
    }

    @Test
    fun labelMissingLocallyIsFoundByOneRefetch() = runTest {
        fake.labels += FakeVikunja.Label(7, "errand")
        engine.sync()
        repository.capture("Buy milk @errand")!!
        // Recreated elsewhere under a new id, and not in the local list: one refetch finds it.
        fake.labels[0] = FakeVikunja.Label(8, "Errand")
        db.labels().clear()

        assertEquals(SyncResult.Synced, engine.sync())

        assertEquals(listOf(8L), fake.tasks[100]!!.labelIds)
        assertTrue(settings.notices.first().isEmpty())
    }

    @Test
    fun tokenWithoutLabelPermissionDropsTheLabelsWithANotice() = runTest {
        fake.labels += FakeVikunja.Label(7, "errand")
        engine.sync()
        repository.capture("Buy milk @errand")!!
        fake.labelsAddable = false

        assertEquals(SyncResult.Synced, engine.sync())

        assertEquals("Buy milk", fake.tasks[100]!!.title)
        assertEquals(emptyList<Long>(), fake.tasks[100]!!.labelIds)
        assertEquals(
            listOf("Couldn't add labels to “Buy milk”: the token isn't allowed to change labels"),
            settings.notices.first(),
        )
        assertEquals(0, db.pendingChanges().count())
        assertFalse(settings.tokenRejected.first())
    }

    @Test
    fun rejectedTokenOnTheLabelRouteStopsTheSync() = runTest {
        fake.labels += FakeVikunja.Label(7, "errand")
        engine.sync()
        repository.capture("Buy milk @errand")!!
        fake.intercept = { request ->
            if (request.requestUrl!!.encodedPath.endsWith("/labels") && request.method == "POST") fake.tokenValid = false
            null
        }

        assertEquals(SyncResult.TokenRejected, engine.sync())
        assertTrue(settings.tokenRejected.first())
        assertEquals(1, db.pendingChanges().count())
    }

    @Test
    fun taskDeletedBeforeItsLabelsDropsTheChange() = runTest {
        fake.labels += FakeVikunja.Label(7, "errand")
        fake.tasks[1] = FakeVikunja.Task(1, "Buy filament")
        engine.sync()
        db.pendingChanges().insert(
            PendingChangeEntity(
                taskId = 1,
                kind = PendingChangeEntity.UPDATE,
                fields = """{"labels":["errand"]}""",
            ),
        )
        db.taskLabels().set(1, listOf("errand"))
        fake.tasks.remove(1)

        assertEquals(SyncResult.Synced, engine.sync())

        assertEquals(0, db.pendingChanges().count())
        assertNull(db.tasks().get(1))
        assertEquals(emptyList<String>(), db.taskLabels().forTask(1))
        assertTrue(settings.notices.first().isEmpty())
    }

    @Test
    fun createThenCompleteOfflineCreatesThenCompletesTheSameTask() = runTest {
        engine.sync()
        val temporaryId = repository.capture("Buy milk")!!
        repository.complete(temporaryId)

        assertEquals(SyncResult.Synced, engine.sync())

        val writes = fake.requests.filter { it.startsWith("POST") || it.startsWith("PATCH") }
        assertEquals(listOf("""POST projects/1/tasks {"title":"Buy milk","project_id":1}""", """PATCH tasks/100 {"done":true}"""), writes)
        assertTrue(fake.tasks[100]!!.done)
        assertEquals(0, db.pendingChanges().count())
        assertTrue(db.tasks().open().isEmpty())
        assertEquals(100L, idRemap.resolve(temporaryId))
    }

    @Test
    fun localDateWinsAndOtherFieldsSurvive() = runTest {
        fake.tasks[1] = FakeVikunja.Task(1, "Review PR")
        engine.sync()
        repository.reschedule(1, LocalDate.of(2026, 11, 2))
        fake.tasks[1]!!.title = "Review PR #12"

        engine.sync()

        assertEquals("Review PR #12", fake.tasks[1]!!.title)
        assertEquals("2026-11-02T11:00:00Z", fake.tasks[1]!!.dueDate)
        assertEquals(setOf("Review PR #12"), openTitles())
    }

    @Test
    fun tasksDeletedOnTheServerDisappear() = runTest {
        fake.tasks[1] = FakeVikunja.Task(1, "Gone")
        fake.tasks[2] = FakeVikunja.Task(2, "Also gone")
        engine.sync()
        repository.reschedule(2, LocalDate.of(2026, 10, 27))
        fake.tasks.clear()

        assertEquals(SyncResult.Synced, engine.sync())

        assertTrue(db.tasks().open().isEmpty())
        assertEquals(0, db.pendingChanges().count())
        assertTrue(settings.notices.first().isEmpty())
    }

    @Test
    fun rejectedChangeIsDroppedReportedAndRefreshed() = runTest {
        fake.tasks[1] = FakeVikunja.Task(1, "Call dentist")
        engine.sync()
        repository.complete(1)
        fake.intercept = { request ->
            if (request.method == "PATCH") FakeVikunja.json(422, """{"title":"Unprocessable","detail":"nope"}""") else null
        }

        assertEquals(SyncResult.Synced, engine.sync())

        assertEquals(0, db.pendingChanges().count())
        assertEquals(setOf("Call dentist"), openTitles())
        assertEquals(listOf("Couldn't save “Call dentist”: nope"), settings.notices.first())
    }

    @Test
    fun serverErrorKeepsChangesForLater() = runTest {
        fake.tasks[1] = FakeVikunja.Task(1, "Water plants")
        engine.sync()
        repository.complete(1)
        fake.intercept = { FakeVikunja.json(503, """{"title":"Service Unavailable"}""") }

        assertTrue(engine.sync() is SyncResult.ServerError)
        assertEquals(1, db.pendingChanges().count())
        assertFalse(fake.tasks[1]!!.done)

        fake.intercept = { null }
        assertEquals(SyncResult.Synced, engine.sync())
        assertTrue(fake.tasks[1]!!.done)
    }

    @Test
    fun editDuringPushIsSentAfterwards() = runTest {
        fake.tasks[1] = FakeVikunja.Task(1, "Draft")
        engine.sync()
        repository.reschedule(1, LocalDate.of(2026, 10, 27))
        var edited = false
        fake.intercept = { request ->
            if (request.method == "PATCH" && !edited) {
                edited = true
                runBlocking { repository.edit(1, "Final", db.tasks().get(1)!!.dueAt, 1) }
            }
            null
        }

        assertEquals(SyncResult.Synced, engine.sync())

        assertEquals(2, fake.requests.count { it.startsWith("PATCH") })
        assertEquals("Final", fake.tasks[1]!!.title)
        assertEquals("2026-10-27T11:00:00Z", fake.tasks[1]!!.dueDate)
        assertEquals(0, db.pendingChanges().count())
    }

    @Test
    fun revokedTokenPausesSyncAndKeepsData() = runTest {
        fake.tasks[1] = FakeVikunja.Task(1, "Keep me")
        engine.sync()
        repository.complete(1)
        fake.tokenValid = false

        assertEquals(SyncResult.TokenRejected, engine.sync())
        assertTrue(settings.tokenRejected.first())
        assertEquals(1, db.pendingChanges().count())

        fake.tokenValid = true
        val before = fake.requests.size
        assertEquals(SyncResult.TokenRejected, engine.sync())
        assertEquals(before, fake.requests.size)
    }

    @Test
    fun missingPermissionIsNotARejectedToken() = runTest {
        fake.intercept = { request ->
            if (request.requestUrl!!.encodedPath.endsWith("/tasks")) FakeVikunja.json(401, """{"message":"Unauthorized"}""") else null
        }

        assertEquals(SyncResult.MissingPermission, engine.sync())
        assertFalse(settings.tokenRejected.first())
    }

    @Test
    fun tokenWithoutUserPermissionFallsBackToFirstProject() = runTest {
        fake.userAllowed = false
        assertEquals(SyncResult.Synced, engine.sync())
        assertNull(settings.defaultProjectId.first())
        assertEquals(1L, repository.defaultProjectId())
    }

    @Test
    fun notConnectedDoesNothing() = runTest {
        settings.clear()
        assertEquals(SyncResult.NotConnected, engine.sync())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun deleteRemovesTheTaskOnTheServer() = runTest {
        fake.tasks[1] = FakeVikunja.Task(1, "Buy filament")
        engine.sync()

        repository.delete(1)
        assertNull(db.tasks().get(1))
        assertEquals(SyncResult.Synced, engine.sync())

        assertNull(fake.tasks[1])
        assertEquals(0, db.pendingChanges().count())
        assertTrue(fake.requests.any { it.startsWith("DELETE tasks/1") })
    }

    @Test
    fun deleteBeforeSyncNeverCreatesTheTask() = runTest {
        engine.sync()
        val id = repository.capture("Buy milk")!!

        repository.delete(id)
        assertEquals(SyncResult.Synced, engine.sync())

        assertTrue(fake.tasks.isEmpty())
        assertEquals(0, db.pendingChanges().count())
        assertTrue(fake.requests.none { it.startsWith("POST") && "Buy milk" in it })
    }

    @Test
    fun deleteOfTaskAlreadyGoneStillClearsTheQueue() = runTest {
        fake.tasks[1] = FakeVikunja.Task(1, "Gone")
        engine.sync()
        repository.delete(1)
        fake.tasks.remove(1)

        assertEquals(SyncResult.Synced, engine.sync())

        assertEquals(0, db.pendingChanges().count())
        assertTrue(settings.notices.first().isEmpty())
    }
}
