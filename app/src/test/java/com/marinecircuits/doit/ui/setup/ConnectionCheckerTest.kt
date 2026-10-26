package com.marinecircuits.doit.ui.setup

import com.marinecircuits.doit.data.remote.VikunjaApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ConnectionCheckerTest {
    private val server = MockWebServer()
    private val checker = ConnectionChecker { base, token -> VikunjaApi(base, token) }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.shutdown()

    private fun respond(code: Int, body: String = "{}") =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private suspend fun check() = checker.check(server.url("/api/v2/"), "tk_x")

    @Test
    fun validServerAndToken() = runTest {
        respond(200, """{"version":"v2.7.1"}""")
        respond(200, """{"message":"ok"}""")
        respond(200, """{"items":[],"total":0,"page":1,"per_page":1,"total_pages":0}""")

        assertEquals(ConnectionChecker.Result.Ok, check())
        assertNull("info is public and gets no token", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer tk_x", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun serverWithoutApiV2IsUnreachable() = runTest {
        respond(404)
        assertEquals(ConnectionChecker.Result.Unreachable, check())
    }

    @Test
    fun deadServerIsUnreachable() = runTest {
        server.shutdown()
        assertEquals(ConnectionChecker.Result.Unreachable, check())
    }

    @Test
    fun badTokenIsRejected() = runTest {
        respond(200, """{"version":"v2.7.1"}""")
        respond(401, """{"message":"Unauthorized"}""")
        assertEquals(ConnectionChecker.Result.TokenRejected, check())
    }

    @Test
    fun validTokenWithoutProjectPermission() = runTest {
        respond(200, """{"version":"v2.7.1"}""")
        respond(200, """{"message":"ok"}""")
        respond(401, """{"message":"Unauthorized"}""")
        assertEquals(ConnectionChecker.Result.MissingPermission, check())
    }
}
