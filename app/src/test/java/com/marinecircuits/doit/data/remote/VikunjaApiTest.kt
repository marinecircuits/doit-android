package com.marinecircuits.doit.data.remote

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VikunjaApiTest {
    private val server = MockWebServer()
    private lateinit var api: VikunjaApi

    @Before
    fun setUp() {
        server.start()
        api = VikunjaApi(server.url("/api/v2/"), "tk_test")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun openTasksFollowsPagesAndFiltersDone() = runTest {
        server.enqueue(json("""{"items":[{"id":1,"title":"a"}],"total":2,"page":1,"per_page":1,"total_pages":2}"""))
        server.enqueue(json("""{"items":[{"id":2,"title":"b"}],"total":2,"page":2,"per_page":1,"total_pages":2}"""))

        val tasks = api.openTasks()

        assertEquals(listOf(1L, 2L), tasks.map { it.id })
        val first = server.takeRequest()
        assertEquals("/api/v2/tasks", first.requestUrl!!.encodedPath)
        assertEquals("done = false", first.requestUrl!!.queryParameter("filter"))
        assertEquals("1", first.requestUrl!!.queryParameter("page"))
        assertEquals("Bearer tk_test", first.getHeader("Authorization"))
        assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
    }

    @Test
    fun emptyListIsOneRequest() = runTest {
        server.enqueue(json("""{"items":[],"total":0,"page":1,"per_page":50,"total_pages":0}"""))
        assertTrue(api.projects().isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun createPostsToProjectWithMarkdownFormat() = runTest {
        server.enqueue(json("""{"id":9,"title":"Buy milk","project_id":4}""", code = 201))

        val task = api.createTask(4, JsonObject(mapOf("title" to JsonPrimitive("Buy milk"))))

        assertEquals(9, task.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v2/projects/4/tasks", request.requestUrl!!.encodedPath)
        assertEquals("markdown", request.requestUrl!!.queryParameter("format"))
        assertEquals("""{"title":"Buy milk"}""", request.body.readUtf8())
    }

    @Test
    fun updateIsMergePatchAndKeepsNullToClearDate() = runTest {
        server.enqueue(json("""{"id":9,"title":"x","due_date":"0001-01-01T00:00:00Z"}"""))

        api.updateTask(9, JsonObject(mapOf("due_date" to JsonNull, "done" to JsonPrimitive(true))))

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v2/tasks/9", request.requestUrl!!.encodedPath)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/merge-patch+json"))
        assertEquals("""{"due_date":null,"done":true}""", request.body.readUtf8())
    }

    @Test
    fun errorsCarryStatusAndProblemDetail() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(422).setHeader("Content-Type", "application/problem+json")
                .setBody("""{"title":"Unprocessable Entity","status":422,"detail":"title must not be empty"}"""),
        )
        server.enqueue(json("""{"message":"Unauthorized"}""", code = 401))
        server.enqueue(json("""{"title":"Service Unavailable"}""", code = 503))

        val unprocessable = expectApiError { api.updateTask(1, JsonObject(emptyMap())) }
        assertEquals(422, unprocessable.status)
        assertEquals("title must not be empty", unprocessable.problem)

        val unauthorized = expectApiError { api.tokenTest() }
        assertTrue(unauthorized.isUnauthorized)
        assertEquals("Unauthorized", unauthorized.problem)

        assertTrue(expectApiError { api.info() }.isTransient)
    }

    private suspend fun expectApiError(block: suspend () -> Unit): ApiException {
        try {
            block()
        } catch (e: ApiException) {
            return e
        }
        fail("Expected ApiException")
        throw IllegalStateException()
    }
}
