package com.marinecircuits.doit.data.sync

import com.marinecircuits.doit.data.remote.VikunjaJson
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

/** An in-memory stand-in for the handful of Vikunja API v2 endpoints doit uses. */
class FakeVikunja : Dispatcher() {
    data class Task(val id: Long, var title: String, var done: Boolean = false, var dueDate: String? = null, var projectId: Long = 1)

    val tasks = linkedMapOf<Long, Task>()
    val projects = mutableListOf(1L to "Inbox", 2L to "Work")
    var defaultProjectId = 1L
    var tokenValid = true
    var userAllowed = true
    private var nextId = 100L

    /** Return a response here to override the normal handling of a request. */
    var intercept: (RecordedRequest) -> MockResponse? = { null }
    val requests = CopyOnWriteArrayList<String>()

    @Synchronized
    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.requestUrl!!.encodedPath.removePrefix("/api/v2/")
        val body = request.body.readUtf8()
        requests += "${request.method} $path ${if (body.isEmpty()) "" else body}".trim()
        intercept(request)?.let { return it }
        if (!tokenValid) return json(401, """{"message":"Unauthorized"}""")
        return when {
            request.method == "GET" && path == "token/test" -> json(200, """{"message":"ok"}""")
            request.method == "GET" && path == "user" ->
                if (userAllowed) json(200, """{"id":1,"settings":{"default_project_id":$defaultProjectId}}""")
                else json(401, """{"message":"Unauthorized"}""")
            request.method == "GET" && path == "projects" -> page(projects.map { (id, title) -> """{"id":$id,"title":"$title"}""" })
            request.method == "GET" && path == "tasks" -> page(tasks.values.filterNot { it.done }.map(::encode))
            request.method == "POST" && path.matches(Regex("projects/\\d+/tasks")) -> {
                val fields = parse(body)
                val task = Task(nextId++, fields["title"]!!.jsonPrimitive.content, projectId = path.split("/")[1].toLong())
                apply(task, fields)
                tasks[task.id] = task
                json(201, encode(task))
            }
            request.method == "PATCH" && path.startsWith("tasks/") -> {
                val task = tasks[path.removePrefix("tasks/").toLong()] ?: return json(404, """{"title":"Not Found"}""")
                apply(task, parse(body))
                json(200, encode(task))
            }
            else -> json(404, """{"title":"Not Found"}""")
        }
    }

    private fun apply(task: Task, fields: JsonObject) {
        fields["title"]?.jsonPrimitive?.contentOrNull?.let { task.title = it }
        fields["done"]?.jsonPrimitive?.booleanOrNull?.let { task.done = it }
        fields["project_id"]?.jsonPrimitive?.longOrNull?.let { task.projectId = it }
        if ("due_date" in fields) task.dueDate = fields["due_date"]!!.jsonPrimitive.contentOrNull
    }

    private fun parse(body: String) = VikunjaJson.decodeFromString(JsonObject.serializer(), body)

    private fun encode(task: Task) =
        """{"id":${task.id},"title":"${task.title}","done":${task.done},"project_id":${task.projectId},""" +
            """"due_date":"${task.dueDate ?: "0001-01-01T00:00:00Z"}","created":"2026-10-26T10:00:00Z"}"""

    private fun page(items: List<String>) =
        json(200, """{"items":[${items.joinToString(",")}],"total":${items.size},"page":1,"per_page":50,"total_pages":1}""")

    companion object {
        fun json(code: Int, body: String): MockResponse =
            MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    }
}
