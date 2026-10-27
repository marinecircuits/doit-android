package com.marinecircuits.doit.ui.tasks

import android.os.Looper
import com.marinecircuits.doit.data.IdRemap
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.sync.PendingChanges
import com.marinecircuits.doit.data.sync.SyncResult
import com.marinecircuits.doit.inMemoryDatabase
import com.marinecircuits.doit.testClock
import com.marinecircuits.doit.testSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TasksViewModelTest {
    private lateinit var db: DoitDatabase
    private lateinit var settings: SettingsStore
    private lateinit var repository: TaskRepository
    private val syncResults = MutableStateFlow<SyncResult?>(null)
    private var syncs = 0
    private var signedOut = false
    private lateinit var viewModel: TasksViewModel

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        settings = testSettings()
        repository = TaskRepository(db, settings, IdRemap(), testClock) {}
        viewModel = TasksViewModel(
            repository = repository,
            settings = settings,
            syncResults = syncResults,
            sync = { syncs++; SyncResult.Synced },
            signOut = { signedOut = true },
            clock = testClock,
        )
    }

    @After
    fun tearDown() = db.close()

    /** Room and DataStore answer on their own threads; their results reach the view model via the main looper. */
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun row(id: Long) = viewModel.state.value.rows.firstOrNull { it.task.id == id }

    @Test
    fun completedTaskStaysStruckThroughUntilTheUserLeaves() {
        runBlocking { db.tasks().upsert(TaskEntity(10, "Call dentist", dueAt = null, projectId = 1, done = false, createdAt = 0)) }
        waitUntil { row(10) != null }

        viewModel.complete(row(10)!!.task)
        waitUntil { row(10)?.completed == true }

        // A sync that removes the done task, plus a marker task to know the list has been rebuilt since.
        runBlocking {
            db.pendingChanges().clear()
            db.tasks().delete(10)
            db.tasks().upsert(TaskEntity(11, "Marker", dueAt = null, projectId = 1, done = false, createdAt = 1))
        }
        waitUntil { row(11) != null }
        assertEquals(true, row(10)?.completed)

        viewModel.onLeave()
        waitUntil { row(10) == null }
        assertEquals(listOf(11L), viewModel.state.value.rows.map { it.task.id })
    }

    @Test
    fun uncheckingRestoresTheTaskWithItsDate() {
        val due = 1_793_000_000_000L
        runBlocking { db.tasks().upsert(TaskEntity(10, "Water plants", dueAt = due, projectId = 1, done = false, createdAt = 0)) }
        waitUntil { row(10) != null }
        viewModel.complete(row(10)!!.task)
        waitUntil { row(10)?.completed == true }

        viewModel.uncomplete(row(10)!!.task)
        waitUntil { row(10)?.completed == false && runBlocking { db.tasks().get(10) }?.done == false }

        assertEquals(due, row(10)!!.task.dueAt)
        val fields = PendingChanges.decode(runBlocking { db.pendingChanges().forTask(10) }!!.fields)
        assertEquals(JsonPrimitive(false), fields["done"])
        viewModel.onLeave()
        waitUntil { row(10)?.completed == false }
    }

    private fun seedProjects() = runBlocking {
        db.projects().insertAll(
            listOf(
                ProjectEntity(1, "Inbox", false, 0.0),
                ProjectEntity(2, "Work", false, 1.0),
                ProjectEntity(3, "Firmware", false, 2.0, parentProjectId = 2),
            ),
        )
        db.tasks().upsertAll(
            listOf(
                TaskEntity(10, "Buy milk", null, projectId = 1, done = false, createdAt = 3),
                TaskEntity(11, "Review PR", null, projectId = 2, done = false, createdAt = 2),
                TaskEntity(12, "Flash board", null, projectId = 3, done = false, createdAt = 1),
            ),
        )
        settings.setDefaultProjectId(1)
    }

    private fun titles() = viewModel.state.value.rows.map { it.task.title }
    private fun tiles() = viewModel.state.value.tiles.map { it.project.title to it.openCount }

    @Test
    fun mainViewListsEverythingWithTopLevelTiles() {
        seedProjects()
        waitUntil { titles().size == 3 && tiles().size == 2 }
        assertEquals(listOf("Inbox" to 1, "Work" to 1), tiles())
        assertEquals("Work", viewModel.state.value.rows.single { it.task.id == 11L }.projectTitle)
    }

    @Test
    fun projectScreenShowsItsOwnTasksAndSubprojects() {
        seedProjects()
        waitUntil { tiles().size == 2 }

        viewModel.openProject(2)
        waitUntil { viewModel.state.value.path.map { it.id } == listOf(2L) }
        assertEquals(listOf("Review PR"), titles())
        assertEquals(listOf("Firmware" to 1), tiles())
        assertEquals(null, viewModel.state.value.rows.single().projectTitle)

        viewModel.openProject(3)
        waitUntil { viewModel.state.value.path.map { it.id } == listOf(2L, 3L) }
        assertEquals(listOf("Flash board"), titles())
        assertTrue(viewModel.state.value.tiles.isEmpty())

        viewModel.navigateUp()
        waitUntil { viewModel.state.value.path.map { it.id } == listOf(2L) }
        viewModel.navigateUp()
        waitUntil { viewModel.state.value.path.isEmpty() && titles().size == 3 }
    }

    @Test
    fun captureGoesToTheOpenProject() {
        seedProjects()
        waitUntil { tiles().size == 2 }
        viewModel.openProject(2)
        waitUntil { viewModel.state.value.path.size == 1 }
        viewModel.openProject(3)
        waitUntil { viewModel.state.value.path.size == 2 }

        var created: Long? = null
        viewModel.capture("Order capacitors") { created = it }
        waitUntil { created != null && titles().contains("Order capacitors") }
        assertEquals(3L, runBlocking { db.tasks().get(created!!) }!!.projectId)
    }

    @Test
    fun namedProjectOverridesTheOpenProjectWithoutNavigatingAway() {
        seedProjects()
        waitUntil { tiles().size == 2 }
        viewModel.openProject(2)
        waitUntil { viewModel.state.value.path.map { it.id } == listOf(2L) }

        var created: Long? = null
        viewModel.capture("Flash board #Firmware") { created = it }
        waitUntil { created != null }
        val task = runBlocking { db.tasks().get(created!!) }!!
        assertEquals(3L, task.projectId)
        assertEquals("Flash board", task.title)
        assertEquals(listOf(2L), viewModel.state.value.path.map { it.id })
        assertEquals(listOf("Review PR"), titles())
    }

    @Test
    fun navigatingClearsStruckThroughRows() {
        seedProjects()
        waitUntil { tiles().size == 2 }
        viewModel.openProject(2)
        waitUntil { titles() == listOf("Review PR") }
        viewModel.complete(viewModel.state.value.rows.single().task)
        waitUntil { viewModel.state.value.rows.singleOrNull()?.completed == true }

        viewModel.openProject(3)
        waitUntil { titles() == listOf("Flash board") }
        viewModel.navigateUp()
        waitUntil { viewModel.state.value.path.size == 1 }
        waitUntil { titles().isEmpty() }
    }

    @Test
    fun removedProjectFallsBackToItsParentAndHomeLeavesProjects() {
        seedProjects()
        waitUntil { tiles().size == 2 }
        viewModel.openProject(2)
        waitUntil { viewModel.state.value.path.size == 1 }
        viewModel.openProject(3)
        waitUntil { viewModel.state.value.path.size == 2 }

        runBlocking {
            db.projects().clear()
            db.projects().insertAll(listOf(ProjectEntity(1, "Inbox", false, 0.0), ProjectEntity(2, "Work", false, 1.0)))
        }
        waitUntil { viewModel.state.value.path.map { it.id } == listOf(2L) }

        viewModel.navigateHome()
        waitUntil { viewModel.state.value.path.isEmpty() }
    }

    @Test
    fun signOutWithoutPendingChangesHappensAtOnce() {
        viewModel.requestSignOut()
        waitUntil { signedOut }
        assertEquals(null, viewModel.state.value.signOutConfirmation)
    }

    @Test
    fun signOutWithPendingChangesAsksFirst() {
        runBlocking {
            repository.capture("one")
            repository.capture("two")
        }
        waitUntil { viewModel.state.value.status == listOf(StatusPart.Waiting(2)) }

        viewModel.requestSignOut()
        waitUntil { viewModel.state.value.signOutConfirmation == 2 }
        assertFalse(signedOut)

        viewModel.confirmSignOut()
        waitUntil { signedOut }
    }

    @Test
    fun rejectedTokenShowsTheBanner() {
        runBlocking { settings.setTokenRejected() }
        waitUntil { viewModel.state.value.tokenRejected }
    }

    @Test
    fun capturedTasksAppearAndForegroundSyncs() {
        var created: Long? = null
        viewModel.capture("Buy milk") { created = it }
        waitUntil { created != null && viewModel.state.value.rows.any { it.task.id == created } }

        viewModel.onForeground()
        waitUntil { syncs == 1 }
        syncResults.value = SyncResult.Offline
        waitUntil { viewModel.state.value.status == listOf(StatusPart.Offline, StatusPart.Waiting(1)) }
        assertTrue(viewModel.state.value.syncedOnce)
    }
}
