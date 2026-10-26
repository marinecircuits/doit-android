package com.marinecircuits.doit.ui.tasks

import android.os.Looper
import com.marinecircuits.doit.data.IdRemap
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.sync.SyncResult
import com.marinecircuits.doit.inMemoryDatabase
import com.marinecircuits.doit.testClock
import com.marinecircuits.doit.testSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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

    @Test
    fun completedTaskStaysStruckThroughUntilProjectChangesAndCanBeUndone() {
        runBlocking {
            db.projects().insertAll(listOf(ProjectEntity(1, "Work", false, 0.0)))
            db.tasks().upsert(TaskEntity(7, "Review", null, 1, false, testClock.millis()))
        }
        waitUntil { viewModel.state.value.rows.any { it.task.id == 7L } }
        viewModel.openProject(1)
        waitUntil { viewModel.state.value.projectPath == listOf(1L) }

        viewModel.complete(viewModel.state.value.rows.single().task)
        waitUntil { viewModel.state.value.rows.single().task.done }
        waitUntil { runBlocking { db.tasks().get(7)?.done == true } }
        viewModel.undoComplete(viewModel.state.value.rows.single().task)
        waitUntil { viewModel.state.value.rows.single().task.done.not() }

        viewModel.complete(viewModel.state.value.rows.single().task)
        waitUntil { viewModel.state.value.rows.single().task.done }
        viewModel.navigateBack()
        waitUntil { viewModel.state.value.projectPath.isEmpty() && viewModel.state.value.rows.isEmpty() }
    }

    @Test
    fun projectNavigationDrillsIntoChildrenAndCaptureUsesSelectedProject() {
        runBlocking {
            db.projects().insertAll(listOf(
                ProjectEntity(1, "Work", false, 0.0),
                ProjectEntity(2, "Firmware", false, 1.0, parentProjectId = 1),
            ))
        }
        waitUntil { viewModel.state.value.projects.size == 2 }
        viewModel.openProject(1)
        viewModel.openProject(2)
        waitUntil { viewModel.state.value.projectPath == listOf(1L, 2L) }
        var created: Long? = null
        viewModel.capture("Fix board") { created = it }
        waitUntil { created != null && viewModel.state.value.rows.any { it.task.id == created } }
        assertEquals(2L, runBlocking { db.tasks().get(created!!)?.projectId })
        viewModel.navigateBack()
        waitUntil { viewModel.state.value.projectPath == listOf(1L) }
    }
}
