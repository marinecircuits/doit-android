package com.marinecircuits.doit.data.sync

import com.marinecircuits.doit.data.Credentials
import com.marinecircuits.doit.data.IdRemap
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.DoitDatabase
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
}
