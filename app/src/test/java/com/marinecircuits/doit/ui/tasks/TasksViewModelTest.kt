package com.marinecircuits.doit.ui.tasks

import android.os.Looper
import com.marinecircuits.doit.data.Credentials
import com.marinecircuits.doit.data.IdRemap
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.LabelEntity
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
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    fun serverAddressFollowsCredentialsWithoutExposingSecrets() {
        runBlocking { settings.saveCredentials(Credentials("https://user:password@example.com/vikunja/api/v2/".toHttpUrl(), "tk_secret")) }
        waitUntil { viewModel.state.value.serverAddress == "https://example.com/vikunja/" }
        runBlocking { settings.clear() }
        waitUntil { viewModel.state.value.serverAddress == null }
    }

    @Test
    fun recentTasksTracksCompletionUndoAndDeletion() {
        seedProjects()
        waitUntil { row(10) != null && row(11) != null }
        viewModel.complete(row(10)!!.task)
        waitUntil { viewModel.recentTasks.value.completed.any { it.taskId == 10L } }
        viewModel.uncomplete(row(10)!!.task)
        waitUntil { viewModel.recentTasks.value.completed.none { it.taskId == 10L } }
        viewModel.delete(row(11)!!.task)
        waitUntil { viewModel.recentTasks.value.deleted.any { it.taskId == 11L } }
        assertEquals("Review PR", viewModel.recentTasks.value.deleted.single().title)
    }

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
    fun aProjectOnItsOwnOpensThatProject() {
        seedProjects()
        waitUntil { tiles().size == 2 }
        viewModel.openProject(2)
        waitUntil { viewModel.state.value.path.map { it.id } == listOf(2L) }

        var created: Long? = null
        viewModel.capture("#Firmware") { created = it }
        waitUntil { viewModel.state.value.path.map { it.id } == listOf(2L, 3L) }
        assertEquals(null, created)
        assertEquals(listOf("Flash board"), titles())

        viewModel.capture("#Inbox") { created = it }
        waitUntil { viewModel.state.value.path.map { it.id } == listOf(1L) }
        assertEquals(null, created)
        assertEquals(listOf("Buy milk"), titles())
    }

    @Test
    fun aLabelOnItsOwnOpensThatLabel() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(8, "15 min")))
            db.taskLabels().set(11, listOf("Errand"))
            db.taskLabels().set(10, listOf("15 min"))
        }
        waitUntil { viewModel.state.value.labels.size == 2 }

        var created: Long? = null
        viewModel.capture("  @errand  ") { created = it }
        waitUntil { viewModel.state.value.openLabel?.id == 7L }
        assertEquals(null, created)
        assertEquals(listOf("Review PR"), titles())
        assertEquals("Work", viewModel.state.value.rows.single().projectTitle)
        assertTrue(viewModel.state.value.path.isEmpty())

        viewModel.capture("@\"15 min\"") { created = it }
        waitUntil { viewModel.state.value.openLabel?.title == "15 min" }
        assertEquals(listOf("Buy milk"), titles())

        viewModel.navigateUp()
        waitUntil { viewModel.state.value.openLabel == null && titles().size == 3 }
    }

    @Test
    fun homeLabelShortcutOpensTasksAcrossProjectsWithoutCreatingATask() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(9, "Unused"), LabelEntity(7, "Errand"), LabelEntity(8, "home")))
            db.taskLabels().set(10, listOf("errand"))
            db.taskLabels().set(11, listOf("Errand"))
        }
        waitUntil { viewModel.state.value.labels.size == 3 }
        assertEquals(listOf("Errand", "home", "Unused"), viewModel.state.value.labels.map { it.title })
        viewModel.openLabel(7)
        waitUntil { viewModel.state.value.openLabel?.id == 7L }
        assertEquals(setOf("Buy milk", "Review PR"), titles().toSet())
        assertEquals(0, runBlocking { db.pendingChanges().count() })
        viewModel.navigateUp()
        waitUntil { viewModel.state.value.openLabel == null && viewModel.state.value.path.isEmpty() }

        viewModel.openLabel(9)
        waitUntil { viewModel.state.value.openLabel?.id == 9L }
        assertTrue(viewModel.state.value.rows.isEmpty())
        viewModel.openLabel(999)
        assertEquals(9L, viewModel.state.value.openLabel!!.id)
    }

    @Test
    fun removedShortcutReturnsToHomeAndAllowsContextNavigation() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Home")))
            settings.setContextLabelId(0, 8)
        }
        waitUntil { viewModel.state.value.labels.size == 2 && viewModel.state.value.contextLabelIds[0] == 8L }
        viewModel.openLabel(7)
        waitUntil { viewModel.state.value.openLabel?.id == 7L }
        runBlocking {
            db.labels().clear()
            db.labels().insertAll(listOf(LabelEntity(8, "Home")))
        }
        waitUntil { viewModel.state.value.openLabel == null && viewModel.state.value.labels.size == 1 }
        viewModel.nextContext()
        waitUntil { viewModel.state.value.contextIndex == 0 && viewModel.state.value.openLabel?.id == 8L }
    }

    @Test
    fun movingDoingTaskIntoShoppingProjectExcludesItEvenFromTheCurrentDoingView() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(8, "Doing")))
            db.taskLabels().set(10, listOf("Doing"))
            settings.setShoppingProject(2, true)
        }
        waitUntil { viewModel.state.value.shoppingProjects == setOf(2L) && viewModel.state.value.doingCount == 1 }
        viewModel.reviewDoing()
        waitUntil { viewModel.state.value.openLabel?.id == 8L && row(10) != null }
        viewModel.move(row(10)!!.task, 2)
        waitUntil { row(10) == null && viewModel.state.value.doingCount == 0 }
        assertTrue(viewModel.state.value.rows.isEmpty())
    }

    @Test
    fun shoppingProjectsAreExcludedFromDoingViewsAndCounts() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(8, "Doing")))
            db.taskLabels().set(10, listOf("Doing"))
            db.taskLabels().set(11, listOf("Doing"))
            settings.setShoppingProject(2, true)
        }
        waitUntil { viewModel.state.value.shoppingProjects == setOf(2L) && viewModel.state.value.doingCount == 1 }
        assertEquals(setOf(10L), viewModel.state.value.doingTaskIds)
        viewModel.reviewDoing()
        waitUntil { viewModel.state.value.openLabel?.id == 8L }
        assertEquals(listOf(10L), viewModel.state.value.rows.map { it.task.id })
    }

    @Test
    fun doingCountReviewAndCompletionUndoPreserveWorkingState() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Doing")))
            db.taskLabels().set(10, listOf("Doing"))
            db.taskLabels().set(11, listOf("Errand"))
        }
        waitUntil { viewModel.state.value.doingCount == 1 && row(11) != null }
        viewModel.setDoing(row(11)!!.task, true)
        waitUntil { viewModel.state.value.doingCount == 2 }
        assertEquals(setOf(10L, 11L), viewModel.state.value.doingTaskIds)
        viewModel.reviewDoing()
        waitUntil { viewModel.state.value.openLabel?.id == 8L && viewModel.state.value.rows.size == 2 }
        val task = row(11)!!.task
        viewModel.complete(task)
        waitUntil { row(11)?.completed == true && viewModel.state.value.doingCount == 1 }
        assertFalse(row(11)!!.labels.any { it.title.equals("doing", ignoreCase = true) })
        viewModel.uncomplete(task)
        waitUntil { row(11)?.completed == false && viewModel.state.value.doingCount == 2 }
        assertTrue(row(11)!!.labels.any { it.title == "Doing" })
    }

    @Test
    fun removingDoingKeepsTheTaskUntilLeavingItsLabelView() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(8, "Doing")))
            db.taskLabels().set(10, listOf("Doing"))
            db.taskLabels().set(11, listOf("Doing"))
        }
        waitUntil { viewModel.state.value.doingCount == 2 }
        viewModel.reviewDoing()
        waitUntil { viewModel.state.value.openLabel?.id == 8L && row(10) != null && row(11) != null }

        viewModel.setDoing(row(10)!!.task, false)
        waitUntil { viewModel.state.value.doingCount == 1 && row(10)?.labels?.isEmpty() == true }
        assertEquals(setOf(10L, 11L), viewModel.state.value.rows.map { it.task.id }.toSet())
        viewModel.onLeave() // Backgrounding does not change the view.
        viewModel.reviewDoing() // Opening the same view does not change it either.
        waitUntil { row(10) != null && viewModel.state.value.doingCount == 1 }

        viewModel.navigateHome()
        waitUntil { viewModel.state.value.openLabel == null }
        viewModel.openLabel(8)
        waitUntil { viewModel.state.value.openLabel?.id == 8L }
        assertEquals(listOf(11L), viewModel.state.value.rows.map { it.task.id })
    }

    @Test
    fun syncLabelChangesRemainVisibleUntilSwitchingContextScreens() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Home")))
            db.taskLabels().set(10, listOf("Errand"))
            settings.setContextLabelId(0, 7)
            settings.setContextLabelId(1, 8)
        }
        waitUntil { viewModel.state.value.contextLabelIds == listOf(7L, 8L, null) && viewModel.state.value.labels.size == 2 }
        viewModel.nextContext()
        waitUntil { viewModel.state.value.contextIndex == 0 && row(10) != null }
        runBlocking { db.taskLabels().set(11, listOf("Errand")) }
        waitUntil { row(11) != null }
        runBlocking {
            db.taskLabels().set(10, emptyList())
            db.taskLabels().set(11, emptyList())
        }
        waitUntil { row(10)?.labels?.isEmpty() == true && row(11)?.labels?.isEmpty() == true }
        viewModel.delete(row(10)!!.task)
        waitUntil { row(10) == null }
        assertTrue(row(11) != null)

        viewModel.nextContext()
        waitUntil { viewModel.state.value.contextIndex == 1 }
        viewModel.previousContext()
        waitUntil { viewModel.state.value.contextIndex == 0 }
        assertTrue(viewModel.state.value.rows.isEmpty())
    }

    @Test
    fun doingHeaderFollowsOpenTasksAcrossProjectsAndNormalTaskOrder() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(8, "Doing")))
            db.taskLabels().set(10, listOf("Doing"))
        }
        waitUntil { viewModel.state.value.doingCount == 1 && viewModel.state.value.doingTask?.task?.id == 10L }
        assertEquals("Inbox", viewModel.state.value.doingTask!!.projectTitle)

        viewModel.openProject(2)
        waitUntil { viewModel.state.value.path.lastOrNull()?.id == 2L }
        assertEquals(10L, viewModel.state.value.doingTask!!.task.id)
        viewModel.setDoing(row(11)!!.task, true)
        waitUntil { viewModel.state.value.doingCount == 2 }
        assertEquals(10L, viewModel.state.value.doingTask!!.task.id) // newer undated task is first

        viewModel.setDoing(viewModel.state.value.doingTask!!.task, false)
        waitUntil { viewModel.state.value.doingCount == 1 && viewModel.state.value.doingTask?.task?.id == 11L }
        viewModel.complete(row(11)!!.task)
        waitUntil { viewModel.state.value.doingCount == 0 && viewModel.state.value.doingTask == null }
    }

    @Test
    fun configuredLabelScreensNavigateInOrderAndHomeReturnsDirectly() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(8, "Home"), LabelEntity(9, "Work")))
            db.taskLabels().set(10, listOf("Home"))
            db.taskLabels().set(11, listOf("Errand"))
            db.taskLabels().set(12, listOf("Work"))
            settings.setContextLabelId(0, 7)
            settings.setContextLabelId(1, 8)
            settings.setContextLabelId(2, 9)
        }
        waitUntil { viewModel.state.value.contextLabelIds == listOf(7L, 8L, 9L) && viewModel.state.value.labels.size == 3 }
        viewModel.nextContext()
        waitUntil { viewModel.state.value.contextIndex == 0 }
        assertEquals(listOf("Review PR"), titles())
        viewModel.nextContext()
        waitUntil { viewModel.state.value.contextIndex == 1 }
        assertEquals(listOf("Buy milk"), titles())
        viewModel.nextContext()
        waitUntil { viewModel.state.value.contextIndex == 2 }
        assertEquals(listOf("Flash board"), titles())
        viewModel.previousContext()
        waitUntil { viewModel.state.value.contextIndex == 1 }
        viewModel.navigateHome()
        waitUntil { !viewModel.state.value.contextScreen && viewModel.state.value.openLabel == null }
        assertTrue(viewModel.state.value.path.isEmpty())
    }

    @Test
    fun navigationSkipsUnconfiguredAndDeletedLabels() {
        seedProjects()
        runBlocking {
            db.labels().insertAll(listOf(LabelEntity(7, "Errand"), LabelEntity(9, "Work")))
            settings.setContextLabelId(0, 7)
            settings.setContextLabelId(1, 8) // deleted since it was configured
            settings.setContextLabelId(2, 9)
        }
        waitUntil { viewModel.state.value.contextLabelIds == listOf(7L, 8L, 9L) && viewModel.state.value.labels.size == 2 }
        viewModel.nextContext()
        waitUntil { viewModel.state.value.contextIndex == 0 }
        viewModel.nextContext()
        waitUntil { viewModel.state.value.contextIndex == 2 }
        viewModel.previousContext()
        waitUntil { viewModel.state.value.contextIndex == 0 }
        viewModel.previousContext()
        waitUntil { !viewModel.state.value.contextScreen }
        viewModel.openProject(2)
        waitUntil { viewModel.state.value.path.isNotEmpty() }
        viewModel.nextContext()
        assertFalse(viewModel.state.value.contextScreen)
        viewModel.navigateHome()
        waitUntil { viewModel.state.value.path.isEmpty() }
    }

    @Test
    fun anUnknownProjectOnItsOwnStaysATask() {
        seedProjects()
        waitUntil { tiles().size == 2 }
        var created: Long? = null
        viewModel.capture("#Nope") { created = it }
        waitUntil { created != null }
        assertEquals("#Nope", runBlocking { db.tasks().get(created!!) }!!.title)
        assertTrue(viewModel.state.value.path.isEmpty())
    }

    @Test
    fun settingAProjectColourUpdatesTheOpenProject() {
        seedProjects()
        waitUntil { tiles().size == 2 }
        viewModel.openProject(2)
        waitUntil { viewModel.state.value.path.lastOrNull()?.id == 2L }

        viewModel.setProjectColor("#1565C0")
        waitUntil { viewModel.state.value.path.last().hexColor == "1565c0" }
        assertEquals("1565c0", viewModel.state.value.projects.single { it.id == 2L }.hexColor)

        viewModel.setProjectColor("")
        waitUntil { viewModel.state.value.path.last().hexColor.isEmpty() }
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
    fun serverFailureDetailsFollowTheLatestSyncResult() {
        syncResults.value = SyncResult.ServerError("HTTP 503: Service unavailable")
        waitUntil { viewModel.state.value.syncError == "HTTP 503: Service unavailable" }
        assertTrue(viewModel.state.value.status.contains(StatusPart.Failed))

        syncResults.value = SyncResult.Offline
        waitUntil { viewModel.state.value.syncError == null && viewModel.state.value.status.contains(StatusPart.Offline) }

        syncResults.value = SyncResult.Synced
        waitUntil { viewModel.state.value.syncError == null && viewModel.state.value.status.isEmpty() }
        assertTrue(viewModel.state.value.syncedOnce)
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
