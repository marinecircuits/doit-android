package com.marinecircuits.doit.data.sync

import com.marinecircuits.doit.data.remote.VikunjaJson
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

/** An in-memory stand-in for the handful of Vikunja API v2 endpoints doit uses. */
class FakeVikunja : Dispatcher() {
    data class Task(
        val id: Long,
        var title: String,
        var done: Boolean = false,
        var dueDate: String? = null,
        var projectId: Long = 1,
        val labelIds: MutableList<Long> = mutableListOf(),
    )

    data class Label(val id: Long, val title: String, val hexColor: String = "")

    val tasks = linkedMapOf<Long, Task>()
    val projects = mutableListOf(1L to "Inbox", 2L to "Work")
    val projectColors = mutableMapOf<Long, String>()

    /** False: the token may not update projects (403 on that route). */
    var projectsUpdatable = true
    var defaultProjectId = 1L
    var tokenValid = true
    var userAllowed = true
    val labels = mutableListOf<Label>()

    /** False: the token may not read labels (401 on the label list). */
    var labelsReadable = true

    /** False: the token may not add labels to tasks (401 on that route). */
    var labelsAddable = true
    var labelsRemovable = true
    var reopenOnComplete = false
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
            request.method == "GET" && path == "projects" -> page(
                projects.map { (id, title) ->
                    """{"id":$id,"title":"$title","hex_color":"${projectColors[id].orEmpty()}"}"""
                },
            )
            request.method == "PATCH" && path.matches(Regex("projects/\\d+")) -> {
                if (!projectsUpdatable) return json(403, """{"title":"Forbidden","detail":"missing permission to update projects"}""")
                val id = path.removePrefix("projects/").toLong()
                val title = projects.find { it.first == id }?.second ?: return json(404, """{"title":"Not Found"}""")
                val hex = parse(body)["hex_color"]?.jsonPrimitive?.contentOrNull.orEmpty()
                projectColors[id] = hex
                json(200, """{"id":$id,"title":"$title","hex_color":"$hex"}""")
            }
            request.method == "GET" && path == "tasks" -> page(tasks.values.filterNot { it.done }.map(::encode))
            request.method == "GET" && path == "labels" ->
                if (labelsReadable) page(labels.map(::encode)) else json(401, """{"message":"Unauthorized"}""")
            request.method == "POST" && path.matches(Regex("tasks/\\d+/labels")) -> {
                if (!labelsAddable) return json(401, """{"message":"Unauthorized"}""")
                val task = tasks[path.split("/")[1].toLong()] ?: return json(404, """{"title":"Not Found"}""")
                val labelId = parse(body)["label_id"]!!.jsonPrimitive.long
                when {
                    labels.none { it.id == labelId } -> json(404, """{"title":"Not Found"}""")
                    labelId in task.labelIds -> json(400, """{"title":"Bad Request","detail":"already on the task"}""")
                    else -> {
                        task.labelIds += labelId
                        json(201, """{"label_id":$labelId}""")
                    }
                }
            }
            request.method == "DELETE" && path.matches(Regex("tasks/\\d+/labels/\\d+")) -> {
                if (!labelsRemovable) return json(403, """{"title":"Forbidden","detail":"missing label permission"}""")
                val parts = path.split("/")
                val task = tasks[parts[1].toLong()] ?: return json(404, """{"title":"Not Found"}""")
                if (!task.labelIds.remove(parts[3].toLong())) return json(404, """{"title":"Not Found"}""")
                MockResponse().setResponseCode(204)
            }
            request.method == "POST" && path.matches(Regex("projects/\\d+/tasks")) -> {
                val fields = parse(body)
                val task = Task(nextId++, fields["title"]!!.jsonPrimitive.content, projectId = path.split("/")[1].toLong())
                apply(task, fields)
                tasks[task.id] = task
                json(201, encode(task))
            }
            request.method == "DELETE" && path.matches(Regex("tasks/\\d+")) -> {
                val id = path.removePrefix("tasks/").toLong()
                val task = tasks.remove(id) ?: return json(404, """{"title":"Not Found"}""")
                json(200, encode(task))
            }
            request.method == "PATCH" && path.startsWith("tasks/") -> {
                val task = tasks[path.removePrefix("tasks/").toLong()] ?: return json(404, """{"title":"Not Found"}""")
                apply(task, parse(body))
                if (reopenOnComplete && task.done) task.done = false
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

    private fun encode(task: Task): String {
        // Vikunja sends null rather than an empty list for a task without labels.
        val taskLabels = task.labelIds.mapNotNull { id -> labels.firstOrNull { it.id == id } }
        val labelsJson = if (taskLabels.isEmpty()) "null" else taskLabels.joinToString(",", "[", "]") { encode(it) }
        return """{"id":${task.id},"title":"${task.title}","done":${task.done},"project_id":${task.projectId},""" +
            """"due_date":"${task.dueDate ?: "0001-01-01T00:00:00Z"}","created":"2026-10-26T10:00:00Z",""" +
            """"labels":$labelsJson}"""
    }

    private fun encode(label: Label) = """{"id":${label.id},"title":"${label.title}","hex_color":"${label.hexColor}"}"""

    private fun page(items: List<String>) =
        json(200, """{"items":[${items.joinToString(",")}],"total":${items.size},"page":1,"per_page":50,"total_pages":1}""")

    companion object {
        fun json(code: Int, body: String): MockResponse =
            MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    }
}
