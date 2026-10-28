package com.marinecircuits.doit.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lenient JSON for Vikunja: unknown fields are ignored and nulls fall back to defaults. */
val VikunjaJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class TaskDto(
    val id: Long = 0,
    val title: String = "",
    val done: Boolean = false,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("project_id") val projectId: Long = 0,
    @SerialName("repeat_after") val repeatAfter: Long = 0,
    val created: String? = null,
    /** Vikunja sends null for a task without labels; [VikunjaJson] turns that into the empty default. */
    val labels: List<LabelDto> = emptyList(),
)

@Serializable
data class LabelDto(
    val id: Long = 0,
    val title: String = "",
    /** Six hex digits, normally without a leading #; empty for no colour. */
    @SerialName("hex_color") val hexColor: String = "",
)

@Serializable
data class ProjectDto(
    val id: Long = 0,
    val title: String = "",
    @SerialName("is_archived") val isArchived: Boolean = false,
    val position: Double = 0.0,
    /** 0 for a top-level project. */
    @SerialName("parent_project_id") val parentProjectId: Long = 0,
    /** Six hex digits, normally without a leading #; empty for no colour. */
    @SerialName("hex_color") val hexColor: String = "",
    val description: String = "",
)

@Serializable
data class UserDto(
    val id: Long = 0,
    val username: String = "",
    val settings: UserSettingsDto? = null,
)

@Serializable
data class UserSettingsDto(
    @SerialName("default_project_id") val defaultProjectId: Long = 0,
)

@Serializable
data class InfoDto(
    val version: String = "",
)

/** API v2 list envelope. */
@Serializable
data class PageDto<T>(
    val items: List<T> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    @SerialName("total_pages") val totalPages: Long = 0,
)

/** RFC 9457 problem details (v2), with v1's `message` as a fallback. */
@Serializable
data class ProblemDto(
    val title: String? = null,
    val detail: String? = null,
    val message: String? = null,
)
