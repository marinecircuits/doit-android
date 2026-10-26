package com.marinecircuits.doit.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlTest {
    private fun apiBase(input: String): String =
        (ServerUrl.normalize(input) as ServerUrl.Result.Valid).apiBase.toString()

    @Test
    fun stripsApiSuffixAndTrailingSlash() {
        assertEquals("https://todo.example.com/api/v2/", apiBase("https://todo.example.com/api/v1/"))
        assertEquals("https://todo.example.com/api/v2/", apiBase("https://todo.example.com/api/v2"))
        assertEquals("https://todo.example.com/api/v2/", apiBase("https://todo.example.com/"))
    }

    @Test
    fun defaultsToHttps() {
        assertEquals("https://todo.example.com/api/v2/", apiBase("todo.example.com"))
        assertEquals("https://todo.example.com/api/v2/", apiBase("  todo.example.com  "))
    }

    @Test
    fun keepsSubpathAndPort() {
        assertEquals("https://example.com:8443/vikunja/api/v2/", apiBase("example.com:8443/vikunja/"))
    }

    @Test
    fun dropsQueryAndFragment() {
        assertEquals("https://todo.example.com/api/v2/", apiBase("https://todo.example.com/?x=1#tasks"))
    }

    @Test
    fun rejectsHttpBlankAndGarbage() {
        assertEquals(ServerUrl.Result.InsecureHttp, ServerUrl.normalize("http://todo.example.com"))
        assertEquals(ServerUrl.Result.Blank, ServerUrl.normalize("   "))
        assertEquals(ServerUrl.Result.Invalid, ServerUrl.normalize("ftp://todo.example.com"))
        assertEquals(ServerUrl.Result.Invalid, ServerUrl.normalize("https://"))
    }
}
