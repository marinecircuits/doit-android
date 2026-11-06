package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.local.*
import com.marinecircuits.doit.data.remote.*
import com.marinecircuits.doit.inMemoryDatabase
import com.marinecircuits.doit.testClock
import com.marinecircuits.doit.testSettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProjectDeletionTest {
    private lateinit var db: DoitDatabase
    private lateinit var settings: SettingsStore
    private lateinit var deletion: ProjectDeletion
    private val server = MockWebServer()
    private var deletes = 0
    private var deleteStatus = 204
    private var projectPage: String? = null
    private var taskPage: String? = null
    private val projects = mutableListOf(
        """{"id":1,"title":"Home"}""",
        """{"id":2,"title":"Archive","parent_project_id":1,"is_archived":true}""",
        """{"id":3,"title":"Other"}""",
    )
    private val tasks = mutableMapOf(1L to mutableListOf("""{"id":10,"title":"Open","project_id":1}"""),
        2L to mutableListOf("""{"id":20,"title":"Done","project_id":2,"done":true}"""))
    private fun page(items: List<String>) = """{"items":[${items.joinToString(",")}],"total":${items.size},"page":1,"total_pages":1}"""

    @Before fun setUp() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                if (request.method == "DELETE") {
                    assertEquals("/api/v2/projects/1", url.encodedPath)
                    deletes++
                    return MockResponse().setResponseCode(deleteStatus)
                }
                val body = when {
                    url.encodedPath == "/api/v2/projects" -> {
                        assertEquals("true", url.queryParameter("is_archived"))
                        projectPage ?: page(projects)
                    }
                    url.encodedPath.endsWith("/tasks") -> {
                        assertNull(url.queryParameter("filter")) // completed tasks must be counted too
                        taskPage ?: page(tasks[url.pathSegments[3].toLong()].orEmpty())
                    }
                    else -> return MockResponse().setResponseCode(404)
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            }
        }
        server.start()
        db = inMemoryDatabase()
        settings = testSettings()
        settings.saveCredentials(Credentials(server.url("/api/v2/"), "tk_test"))
        deletion = ProjectDeletion(db, settings) { VikunjaApi(it.apiBase, it.token) }
        db.projects().insertAll(listOf(ProjectEntity(1,"Home",false,0.0),
            ProjectEntity(2,"Archive",true,0.0,parentProjectId=1), ProjectEntity(3,"Other",false,0.0)))
        db.tasks().upsertAll(listOf(TaskEntity(10,"Open",null,1,false,0), TaskEntity(20,"Done",null,2,true,0),
            TaskEntity(30,"Keep",null,3,false,0), TaskEntity(11,"Stale cache",null,1,false,0)))
        db.taskHistory().insert(TaskHistoryEntity(taskId=20,title="Done",projectId=2,kind="completed",happenedAt=0))
        db.taskHistory().insert(TaskHistoryEntity(taskId=30,title="Keep",projectId=3,kind="completed",happenedAt=0))
        db.taskLabels().set(10,listOf("next"))
        db.taskLabels().set(30,listOf("next"))
    }

    @After fun tearDown() { server.shutdown(); db.close() }

    private suspend fun failure(action: suspend () -> Unit): Exception {
        try { action() } catch (e: Exception) { return e }
        error("Expected failure")
    }

    @Test fun previewIncludesArchivedDescendantsAndCompletedTasksWithoutDeleting() = runTest {
        val preview = deletion.preview(1)
        assertTrue(preview.nonempty)
        assertEquals(listOf(1L,2L),preview.projects.map { it.id })
        assertEquals(listOf(10L,20L),preview.tasks.map { it.id })
        assertTrue(preview.projects.last().archived)
        assertTrue(preview.tasks.last().done)
        assertEquals(0,deletes)
        assertFalse(deletion.preview(3).nonempty)
    }

    @Test fun changedServerContentsRequireAnotherConfirmation() = runTest {
        val preview = deletion.preview(1)
        tasks.getValue(1) += """{"id":12,"title":"New","project_id":1}"""
        val changed = failure { deletion.delete(preview) }
        assertTrue(changed is ProjectDeletion.Changed)
        assertEquals(3,(changed as ProjectDeletion.Changed).preview.tasks.size)
        assertEquals(0,deletes)
        assertNotNull(db.tasks().get(10))
    }

    @Test fun pendingTaskAttachmentAndProjectChangesBlockDeletion() = runTest {
        val preview = deletion.preview(1)
        db.pendingChanges().insert(PendingChangeEntity(taskId=30,kind="update",fields="{}"))
        failure { deletion.delete(preview) }
        db.pendingChanges().clear()
        val attachment = db.attachments().insert(AttachmentEntity(taskId=10,filename="test",pending="upload"))
        failure { deletion.delete(preview) }
        db.attachments().delete(attachment)
        settings.setPendingProjectColor(3,"ff0000")
        failure { deletion.delete(preview) }
        assertEquals(0,deletes)
        assertEquals(3,db.projects().all().size)
    }

    @Test fun connectionChangeBlocksDeletion() = runTest {
        val preview = deletion.preview(1)
        settings.saveCredentials(Credentials(server.url("/api/v2/"),"tk_other"))
        failure { deletion.delete(preview) }
        assertEquals(0,deletes)
    }

    @Test fun incompleteAndMalformedResponsesFailClosed() = runTest {
        val preview = deletion.preview(1)
        taskPage = """{"items":[],"total":3,"page":1,"total_pages":2}"""
        failure { deletion.delete(preview) }
        taskPage = "{}"
        failure { deletion.delete(preview) }
        taskPage = null
        projectPage = """{"items":[],"total":0,"page":1,"total_pages":201}"""
        failure { deletion.delete(preview) }
        assertEquals(0,deletes)
    }

    @Test fun permissionRefusalKeepsCacheAndDoesNotRetry() = runTest {
        deleteStatus = 403
        assertTrue(failure { deletion.delete(deletion.preview(1)) } is ApiException)
        assertEquals(1,deletes)
        assertEquals(3,db.projects().all().size)
        assertNotNull(db.tasks().get(10))
        assertEquals(2,db.taskHistory().observeAll().first().size)
    }

    @Test fun successfulDeletionPurgesOnlyThatTreeIncludingStaleCacheAndFiles() = runTest {
        val file = File.createTempFile("deleted-attachment", ".png")
        val id = db.attachments().insert(AttachmentEntity(taskId=11,filename="image.png",localPath=file.absolutePath))
        deletion.delete(deletion.preview(1))
        assertEquals(1,deletes)
        assertEquals(listOf(3L),db.projects().all().map { it.id })
        assertNull(db.tasks().get(10)); assertNull(db.tasks().get(11)); assertNull(db.tasks().get(20))
        assertNotNull(db.tasks().get(30))
        assertEquals(listOf(30L),db.taskHistory().observeAll().first().map { it.taskId })
        assertTrue(db.taskLabels().forTask(10).isEmpty())
        assertEquals(listOf("next"),db.taskLabels().forTask(30))
        assertNull(db.attachments().get(id)); assertFalse(file.exists())
    }

    @Test fun staleCaptureAndMoveCannotWriteIntoDeletedProject() = runTest {
        deletion.delete(deletion.preview(1))
        val repository = TaskRepository(db,settings,IdRemap(),testClock) {}
        assertTrue(failure { repository.capture("Late capture",projectId=1) } is ProjectUnavailable)
        repository.move(30,1)
        assertEquals(3L,db.tasks().get(30)!!.projectId)
        assertTrue(db.pendingChanges().all().isEmpty())
    }
}
