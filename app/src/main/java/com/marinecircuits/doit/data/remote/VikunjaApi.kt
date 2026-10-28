package com.marinecircuits.doit.data.remote

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The server answered with a non-2xx status. Network failures stay [java.io.IOException]s. */
class ApiException(val status: Int, val problem: String?) : Exception("HTTP $status${problem?.let { ": $it" } ?: ""}") {
    val isUnauthorized get() = status == 401
    val isForbidden get() = status == 403
    val isNotFound get() = status == 404

    /** Worth retrying later: rate limits and server-side failures. */
    val isTransient get() = status == 408 || status == 429 || status >= 500
}

/**
 * Thin client for the parts of Vikunja's API v2 that doit needs.
 * [apiBase] ends in `/api/v2/`; [token] is a Vikunja API token (`tk_…`).
 */
class VikunjaApi(
    val apiBase: HttpUrl,
    private val token: String?,
    private val client: OkHttpClient = defaultClient,
) {
    /** Public endpoint; used to check that the server speaks API v2. */
    suspend fun info(): InfoDto = get(url("info"), InfoDto.serializer())

    /**
     * Succeeds for any valid token regardless of its permissions. Vikunja answers 401 both for a bad
     * token and for a valid token missing a route permission, so this tells the two apart.
     */
    suspend fun tokenTest() {
        get(url("token/test"), JsonObject.serializer())
    }

    suspend fun currentUser(): UserDto = get(url("user"), UserDto.serializer())

    suspend fun projects(): List<ProjectDto> = getAllPages(url("projects"), ProjectDto.serializer())

    /** JSON merge patch. [fields] uses Vikunja's names, e.g. `hex_color`. */
    suspend fun updateProject(projectId: Long, fields: JsonObject): ProjectDto {
        val request = authorized(url("projects/$projectId")).patch(fields.toString().toRequestBody(MERGE_PATCH)).build()
        return execute(request, ProjectDto.serializer())
    }

    /** Lists one page of projects; used to check the token during setup. */
    suspend fun firstProjects(): List<ProjectDto> =
        get(url("projects").newBuilder().addQueryParameter("per_page", "1").build(), PageDto.serializer(ProjectDto.serializer())).items

    suspend fun openTasks(): List<TaskDto> =
        getAllPages(url("tasks").newBuilder().addQueryParameter("filter", "done = false").build(), TaskDto.serializer())

    /** Latest completed tasks across projects; one bounded page keeps this view quick. */
    suspend fun recentCompletedTasks(): List<TaskDto> {
        val request = url("tasks").newBuilder().addQueryParameter("filter", "done = true")
            .addQueryParameter("sort_by", "done_at").addQueryParameter("order_by", "desc")
            .addQueryParameter("per_page", "100").build()
        return get(request, PageDto.serializer(TaskDto.serializer())).items
    }

    /** [fields] uses Vikunja's JSON names, e.g. `title`, `due_date`. Descriptions are sent as Markdown. */
    suspend fun createTask(projectId: Long, fields: JsonObject): TaskDto {
        val url = url("projects/$projectId/tasks").newBuilder().addQueryParameter("format", "markdown").build()
        val request = authorized(url).post(fields.toString().toRequestBody(JSON)).build()
        return execute(request, TaskDto.serializer())
    }

    suspend fun labels(): List<LabelDto> = getAllPages(url("labels"), LabelDto.serializer())

    /** Vikunja answers with a 4xx (400) when the label is on the task already. */
    suspend fun addLabel(taskId: Long, labelId: Long) {
        val body = buildJsonObject { put("label_id", labelId) }.toString().toRequestBody(JSON)
        execute(authorized(url("tasks/$taskId/labels")).post(body).build(), JsonObject.serializer())
    }

    /** Removes one task-label association; Vikunja may return an empty response. */
    suspend fun removeLabel(taskId: Long, labelId: Long) {
        val request = authorized(url("tasks/$taskId/labels/$labelId")).delete().build()
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(response.code, problemText(body))
            }
        }
    }

    /** Gone already (404) is reported as [ApiException.isNotFound]; the caller treats that as success. */
    suspend fun deleteTask(taskId: Long) {
        execute(authorized(url("tasks/$taskId")).delete().build(), JsonObject.serializer())
    }

    /** JSON merge patch: only [fields] change; a JSON `null` clears a field. */
    suspend fun updateTask(taskId: Long, fields: JsonObject): TaskDto {
        val request = authorized(url("tasks/$taskId")).patch(fields.toString().toRequestBody(MERGE_PATCH)).build()
        return execute(request, TaskDto.serializer())
    }

    private fun url(path: String): HttpUrl = apiBase.resolve(path) ?: error("Bad API path $path")

    private suspend fun <T> getAllPages(url: HttpUrl, item: KSerializer<T>): List<T> {
        val all = mutableListOf<T>()
        var page = 1
        while (true) {
            val pageUrl = url.newBuilder().addQueryParameter("page", page.toString()).build()
            val result = get(pageUrl, PageDto.serializer(item))
            all += result.items
            if (page >= result.totalPages || result.items.isEmpty() || page >= MAX_PAGES) return all
            page++
        }
    }

    private suspend fun <T> get(url: HttpUrl, serializer: KSerializer<T>): T =
        execute(authorized(url).get().build(), serializer)

    private fun authorized(url: HttpUrl): Request.Builder =
        Request.Builder().url(url).header("Accept", "application/json").apply {
            if (token != null) header("Authorization", "Bearer $token")
        }

    private suspend fun <T> execute(request: Request, serializer: KSerializer<T>): T = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, problemText(body))
            try {
                VikunjaJson.decodeFromString(serializer, body)
            } catch (e: SerializationException) {
                throw ApiException(response.code, "Unexpected response: ${e.message}")
            }
        }
    }

    private fun problemText(body: String): String? = try {
        val problem = VikunjaJson.decodeFromString(ProblemDto.serializer(), body)
        problem.detail ?: problem.message ?: problem.title
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val MERGE_PATCH = "application/merge-patch+json".toMediaType()
        private const val MAX_PAGES = 200

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
