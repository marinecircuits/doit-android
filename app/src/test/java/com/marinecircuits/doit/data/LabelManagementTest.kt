package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.remote.ApiException
import com.marinecircuits.doit.data.remote.VikunjaApi
import com.marinecircuits.doit.inMemoryDatabase
import com.marinecircuits.doit.testSettings
import com.marinecircuits.doit.testClock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LabelManagementTest {
    private val db = inMemoryDatabase()
    private val settings = testSettings()
    private val server = MockWebServer()
    private val repository = TaskRepository(db, settings, IdRemap(), testClock) {}
    private val manager by lazy { LabelManagement(db, settings, VikunjaApi(server.url("/api/v2/"), "tk_test")) }

    @Before fun setup() = runTest {
        db.projects().insertAll(listOf(ProjectEntity(1, "Inbox", false, 0.0)))
        db.labels().insertAll(listOf(LabelEntity(7, "old"), LabelEntity(8, "other")))
    }
    @After fun close() { db.close(); server.shutdown() }

    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test fun renamePreservesQueuedCaptureAndOrder() = runTest {
        val id = repository.capture("Task @old @other")!!
        server.enqueue(response("""{"id":7,"title":"new","hex_color":"aabbcc"}"""))
        manager.save(7, " new ", "#AABBCC")
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v2/labels/7", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/merge-patch+json"))
        assertEquals("""{"title":"new","hex_color":"aabbcc"}""", request.body.readUtf8())
        assertEquals(listOf("new", "other"), db.taskLabels().forTask(id))
        assertTrue(db.pendingChanges().forTask(id)!!.fields.contains("new"))
        assertFalse(db.pendingChanges().forTask(id)!!.fields.contains("old"))
        assertEquals("Task", db.tasks().get(id)!!.title)
    }

    @Test fun deletionRemovesLabelFromQueuedEditsNotTasksAndClearsPreferences() = runTest {
        val id = repository.capture("Task @old @other")!!
        settings.setContextLabelId(0, 7)
        settings.setNextActionsLabel(7)
        settings.setSwipeDownLabelId(7)
        server.enqueue(MockResponse().setResponseCode(204))
        manager.delete(7)
        assertEquals("DELETE", server.takeRequest().method)
        assertEquals(listOf("other"), db.taskLabels().forTask(id))
        assertNotNull(db.tasks().get(id))
        assertFalse(db.pendingChanges().forTask(id)!!.fields.contains("old"))
        assertEquals(listOf<Long?>(null, null, null), settings.contextLabelIds.first())
        assertNull(settings.swipeDownLabelId.first())
        assertFalse(settings.nextActionsChoice.first().configured)
    }

    @Test fun failureDoesNotMutateLocalState() = runTest {
        val id = repository.capture("Task @old")!!
        server.enqueue(MockResponse().setResponseCode(403))
        try { manager.delete(7); fail("Expected forbidden") } catch (_: ApiException) { }
        assertEquals(listOf("old"), db.taskLabels().forTask(id))
        assertTrue(db.labels().all().any { it.id == 7L })
    }

    @Test fun createAndColourOnlyEditUseTheCorrectEndpoints() = runTest {
        server.enqueue(response("""{"id":9,"title":"new","hex_color":""}"""))
        manager.save(null, "new", "")
        assertEquals("POST", server.takeRequest().method)
        assertEquals("new", db.labels().all().first { it.id == 9L }.title)
        server.enqueue(response("""{"id":9,"title":"new","hex_color":"112233"}"""))
        manager.save(9, "new", "112233")
        assertEquals("112233", db.labels().all().first { it.id == 9L }.hexColor)
    }

    @Test fun invalidAndDuplicateNamesNeverReachTheNetwork() = runTest {
        for ((name, color) in listOf("" to "", "other" to "", "new" to "bad-color")) {
            try { manager.save(7, name, color); fail("Expected validation") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, server.requestCount)
    }
}
