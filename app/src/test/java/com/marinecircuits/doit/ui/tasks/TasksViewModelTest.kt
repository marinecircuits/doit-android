package com.marinecircuits.doit.ui.tasks

import android.os.Looper
import com.marinecircuits.doit.data.IdRemap
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.DoitDatabase
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
