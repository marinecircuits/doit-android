package com.marinecircuits.doit.ui.tasks

import android.os.Looper
import com.marinecircuits.doit.data.Credentials
import com.marinecircuits.doit.data.IdRemap
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.PendingChangeEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.sync.SyncResult
import com.marinecircuits.doit.inMemoryDatabase
import com.marinecircuits.doit.testClock
import com.marinecircuits.doit.testSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
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
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SavedFiltersIntegrationTest {
    private val server = MockWebServer()
    private lateinit var db: DoitDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repository: TaskRepository
    private lateinit var viewModel: TasksViewModel
    private var localChanges = 0

    @Before
    fun setUp() {
        server.start()
        db = inMemoryDatabase()
        settings = testSettings()
        repository = TaskRepository(db, settings, IdRemap(), testClock) { localChanges++ }
        runBlocking {
            settings.saveCredentials(Credentials(server.url("/api/v2/"), "tk_test"))
            db.projects().insertAll(listOf(ProjectEntity(4, "Work", false, 0.0)))
        }
        viewModel = TasksViewModel(repository, settings, MutableStateFlow(SyncResult.Synced),
            sync = { SyncResult.Synced }, createSubproject = { _, _ -> },
            promoteTask = { _, _, _, _ -> error("Unused") }, signOut = {}, clock = testClock)
        waitUntil { viewModel.state.value.projects.isNotEmpty() }
    }

    @After
    fun tearDown() {
        viewModel.navigateHome()
        server.shutdown()
        db.close()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun page(items: String) = MockResponse().setHeader("Content-Type", "application/json")
        .setBody("""{"items":[$items],"page":1,"total_pages":1}""")
    private fun catalog() = page("""{"id":4,"title":"Work"},{"id":-1,"title":"Favorites"},{"id":-2,"title":"My web filter"}""")

    @Test
    fun rightSwipeAlwaysOpensFiltersAndLabelShortcutsRemainReachable() {
        server.enqueue(catalog())
        viewModel.swipeRight()
        waitUntil { viewModel.savedFilters.value.open && !viewModel.savedFilters.value.loading }
        assertEquals(listOf(-2L), viewModel.savedFilters.value.filters.map { it.id })
        viewModel.swipeRight() // no shortcuts: stay on filters
        assertTrue(viewModel.savedFilters.value.open)
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(7, "Errands")))
            settings.setContextLabelId(1, 7) // first slot disabled
        }
        waitUntil { viewModel.state.value.contextLabelIds[1] == 7L && viewModel.state.value.labels.isNotEmpty() }
        viewModel.swipeRight()
        waitUntil { viewModel.state.value.contextIndex == 1 }
        assertFalse(viewModel.savedFilters.value.open)
        server.enqueue(catalog())
        viewModel.swipeLeft()
        waitUntil { viewModel.savedFilters.value.open && !viewModel.savedFilters.value.loading }
        viewModel.swipeLeft()
        assertFalse(viewModel.savedFilters.value.open)
        waitUntil { !viewModel.state.value.contextScreen && viewModel.state.value.openLabel == null }
    }

    @Test
    fun projectAndDirectLabelSwipesDoNotOpenFilters() {
        viewModel.openProject(4)
        waitUntil { viewModel.state.value.path.isNotEmpty() }
        viewModel.swipeRight()
        assertFalse(viewModel.savedFilters.value.open)
        runBlocking { db.labels().insertAll(listOf(LabelEntity(7, "Errands"))) }
        waitUntil { viewModel.state.value.labels.isNotEmpty() }
        viewModel.openLabel(7)
        waitUntil { viewModel.state.value.openLabel?.id == 7L }
        viewModel.swipeRight()
        assertFalse(viewModel.savedFilters.value.open)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun savedResultsKeepWebOrderAndCompletionLabelsAndUseRealProjects() {
        server.enqueue(catalog())
        server.enqueue(page("""{"id":9,"title":"Later","project_id":4,"due_date":"2027-01-01T12:00:00Z","labels":[{"id":7,"title":"Waiting"}]},{"id":2,"title":"Finished","project_id":4,"done":true,"due_date":"2026-01-01T12:00:00Z","labels":[{"id":7,"title":"Waiting"}]}"""))
        viewModel.openSavedFilters()
        waitUntil { !viewModel.savedFilters.value.loading }
        viewModel.openSavedFilter(-2)
        waitUntil { viewModel.savedFilterRows.value.size == 2 }
        assertEquals(listOf(9L, 2L), viewModel.savedFilterRows.value.map { it.task.id })
        assertTrue(viewModel.savedFilterRows.value.last().completed)
        viewModel.uncomplete(viewModel.savedFilterRows.value.last().task)
        waitUntil { !viewModel.savedFilterRows.value.last().completed }
        runBlocking {
            assertEquals(listOf("Waiting"), db.taskLabels().forTask(2))
            assertEquals(4L, db.tasks().get(2)!!.projectId)
        }
        viewModel.delete(viewModel.savedFilterRows.value.first().task)
        waitUntil { viewModel.savedFilterRows.value.map { it.task.id } == listOf(2L) }
        runBlocking { db.pendingChanges().forTask(9)?.let { db.pendingChanges().delete(it.seq) } }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(2L), viewModel.savedFilterRows.value.map { it.task.id })
        viewModel.navigateUp()
        assertTrue(viewModel.savedFilters.value.open)
        assertNull(viewModel.savedFilters.value.selected)
        viewModel.navigateUp()
        assertFalse(viewModel.savedFilters.value.open)
    }

    @Test
    fun refreshingCachesRemoteTasksWithoutOverwritingQueuedEditsOrRestoringDeletes() = runBlocking {
        db.tasks().upsert(TaskEntity(1, "Local title", null, 4, false, 100, syncGeneration = 7))
        db.taskLabels().set(1, listOf("Local label"))
        db.pendingChanges().insert(PendingChangeEntity(taskId = 1, kind = PendingChangeEntity.UPDATE, fields = """{"title":"Local title"}"""))
        db.pendingChanges().insert(PendingChangeEntity(taskId = 2, kind = PendingChangeEntity.DELETE, fields = "{}"))
        db.tasks().upsert(TaskEntity(99, "Unrelated", null, 4, false, 10, syncGeneration = 7))
        server.enqueue(page("""{"id":1,"title":"Old title","project_id":4},{"id":2,"title":"Deleted","project_id":4},{"id":3,"title":"Server done","project_id":4,"done":true,"labels":[{"id":8,"title":"Remote label"}]}"""))
        val results = repository.savedFilterTasks(-2)
        assertEquals(listOf(1L, 3L), results.map { it.task.id })
        assertEquals("Local title", results.first().task.title)
        assertEquals(listOf("Local label"), results.first().labels.map { it.title })
        assertNull(db.tasks().get(2))
        assertTrue(db.tasks().get(3)!!.done)
        assertEquals(7L, db.tasks().get(3)!!.syncGeneration)
        assertEquals(listOf("Remote label"), db.taskLabels().forTask(3))
        assertEquals("Unrelated", db.tasks().get(99)!!.title)
        assertEquals(2, db.pendingChanges().count())
        assertEquals(0, localChanges)
    }
}
