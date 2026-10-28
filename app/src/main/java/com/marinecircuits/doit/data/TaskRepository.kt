package com.marinecircuits.doit.data

import androidx.room.withTransaction
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.PendingChangeEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.local.TaskLabelEntity
import com.marinecircuits.doit.data.remote.VikunjaDates
import com.marinecircuits.doit.data.sync.PendingChanges
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Temporary ids of tasks captured offline, mapped to the ids the server gave them. Lets an action that still holds
 * the temporary id (an Undo snackbar, an open edit sheet) reach the task after the sync renamed it.
 */
class IdRemap {
    private val ids = ConcurrentHashMap<Long, Long>()

    fun record(temporaryId: Long, serverId: Long) {
        ids[temporaryId] = serverId
    }

    fun resolve(id: Long): Long = ids[id] ?: id
}

/** Every local write: the task row and its pending change are updated in one transaction, then [onLocalChange] runs. */
class TaskRepository(
    private val db: DoitDatabase,
    private val settings: SettingsStore,
    private val idRemap: IdRemap,
    private val clock: Clock,
    private val onLocalChange: () -> Unit,
) {
    val openTasks: Flow<List<TaskEntity>> = db.tasks().observeOpen()
    val projects: Flow<List<ProjectEntity>> = db.projects().observeAll()
    val pendingCount: Flow<Int> = db.pendingChanges().observeCount()
    val labels: Flow<List<LabelEntity>> = db.labels().observeAll()
    val taskLabels: Flow<List<TaskLabelEntity>> = db.taskLabels().observeAll()

    suspend fun openTasks(): List<TaskEntity> = db.tasks().open()

    /** The Vikunja default project if known and usable, otherwise the first non-archived project. */
    suspend fun defaultProjectId(): Long? {
        val projects = db.projects().all().filterNot { it.archived }
        val configured = settings.defaultProjectId.first()
        return projects.firstOrNull { it.id == configured }?.id ?: projects.firstOrNull()?.id
    }

    /**
     * Creates a task in the `#project` named in [text], otherwise [projectId] or the default project.
     * Existing `@labels` become labels (see [CaptureSyntax]); the [description] is kept as it is.
     * Returns the task's (temporary) id, or null when nothing is left of the title.
     */
    suspend fun capture(text: String, description: String? = null, projectId: Long? = null): Long? {
        val (title, labelNames, namedProjectId) = CaptureSyntax.parse(text, db.labels().all(), db.projects().all())
        if (title.isEmpty()) return null
        val destination = namedProjectId ?: projectId ?: defaultProjectId() ?: 0
        val id = db.withTransaction {
            val id = minOf(db.tasks().minId() ?: 0, 0) - 1
            db.tasks().upsert(
                TaskEntity(id, title, dueAt = null, projectId = destination, done = false, createdAt = clock.millis()),
            )
            val fields = buildJsonObject {
                put("title", title)
                if (!description.isNullOrBlank()) put("description", description.trim())
                if (destination > 0) put("project_id", destination)
                if (labelNames.isNotEmpty()) put(LABELS, JsonArray(labelNames.map { JsonPrimitive(it) }))
            }
            db.taskLabels().set(id, labelNames) // also clears what a dropped task with this id may have left
            db.pendingChanges().insert(
                PendingChangeEntity(taskId = id, kind = PendingChangeEntity.CREATE, fields = fields.toString()),
            )
            id
        }
        onLocalChange()
        return id
    }

    suspend fun complete(taskId: Long) {
        write(taskId, fields(DONE to JsonPrimitive(true))) { it.copy(done = true) }
    }

    /** Reopens a task exactly as it was before it was completed, even if the sync already removed it. */
    suspend fun undoComplete(previous: TaskEntity) {
        val id = idRemap.resolve(previous.id)
        db.withTransaction {
            val current = db.tasks().get(id) ?: previous.copy(id = id)
            db.tasks().upsert(current.copy(done = false, dueAt = previous.dueAt))
            record(id, fields(DONE to JsonPrimitive(false), DUE_DATE to dueJson(previous.dueAt)))
        }
        onLocalChange()
    }

    /** Sets the due date to [date] (keeping the time of day, see [DueDates.withDate]) or clears it. */
    suspend fun reschedule(taskId: Long, date: LocalDate?) {
        val task = db.tasks().get(idRemap.resolve(taskId)) ?: return
        val dueAt = date?.let { DueDates.withDate(task.dueAt?.let(Instant::ofEpochMilli), it, clock.zone).toEpochMilli() }
        if (dueAt == task.dueAt) return
        write(task.id, fields(DUE_DATE to dueJson(dueAt))) { it.copy(dueAt = dueAt) }
    }

    suspend fun rename(taskId: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val task = db.tasks().get(idRemap.resolve(taskId)) ?: return
        if (trimmed == task.title) return
        write(task.id, fields(TITLE to JsonPrimitive(trimmed))) { it.copy(title = trimmed) }
    }

    /**
     * Saves a title typed in the edit sheet. Recognized `@labels` are added to the labels the task
     * already has, and a recognized `#project` moves it. The stored title is what remains; an edit
     * that is only tokens keeps the previous title.
     */
    suspend fun applyEditedTitle(taskId: Long, raw: String) {
        if (raw.isBlank()) return
        val id = idRemap.resolve(taskId)
        val written = db.withTransaction {
            val task = db.tasks().get(id) ?: return@withTransaction false
            val parsed = CaptureSyntax.parse(raw, db.labels().all(), db.projects().all())
            val newTitle = parsed.title.ifEmpty { task.title }
            val existing = db.taskLabels().forTask(id)
            val added = parsed.labels.filter { name -> existing.none { it.equals(name, ignoreCase = true) } }
            val projectId = parsed.projectId ?: task.projectId
            val queued = db.pendingChanges().forTask(id)?.let { change ->
                PendingChanges.decode(change.fields)[LABELS]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            }.orEmpty()
            val fields = buildJsonObject {
                if (newTitle != task.title) put(TITLE, newTitle)
                if (projectId != task.projectId) put(PROJECT_ID, projectId)
                if (added.isNotEmpty()) {
                    put(LABELS, JsonArray((queued + added).distinctBy { it.lowercase() }.map { JsonPrimitive(it) }))
                }
            }
            if (fields.isEmpty()) return@withTransaction false
            db.tasks().upsert(task.copy(title = newTitle, projectId = projectId))
            if (added.isNotEmpty()) db.taskLabels().set(id, existing + added)
            record(id, fields)
            true
        }
        if (written) onLocalChange()
    }

    /**
     * Removes the task locally at once. A task the server has never seen drops its unsent create.
     * Anything else queues a delete, replacing an unsent update.
     */
    suspend fun delete(taskId: Long) {
        val id = idRemap.resolve(taskId)
        val removed = db.withTransaction {
            val task = db.tasks().get(id) ?: return@withTransaction false
            val pending = db.pendingChanges().forTask(id)
            db.tasks().delete(id)
            db.taskLabels().deleteForTask(id)
            when {
                pending?.kind == PendingChangeEntity.CREATE -> db.pendingChanges().delete(pending.seq)
                pending != null -> db.pendingChanges().update(
                    pending.copy(kind = PendingChangeEntity.DELETE, fields = "{}", revision = pending.revision + 1),
                )
                id > 0 -> db.pendingChanges().insert(
                    PendingChangeEntity(taskId = id, kind = PendingChangeEntity.DELETE, fields = "{}"),
                )
            }
            true
        }
        if (removed) onLocalChange()
    }

    /**
     * Sets the open project's colour locally and queues it for sync. [hexColor] is six hex digits,
     * with or without a leading #, or empty to clear it. Anything else is ignored.
     */
    suspend fun setProjectColor(projectId: Long, hexColor: String) {
        val hex = normalizeHex(hexColor) ?: return
        val project = db.projects().all().find { it.id == projectId } ?: return
        if (project.hexColor.equals(hex, ignoreCase = true)) return
        db.projects().setColor(projectId, hex)
        settings.setPendingProjectColor(projectId, hex)
        onLocalChange()
    }

    suspend fun move(taskId: Long, projectId: Long) {
        val task = db.tasks().get(idRemap.resolve(taskId)) ?: return
        if (projectId == task.projectId) return
        write(task.id, fields(PROJECT_ID to JsonPrimitive(projectId))) { it.copy(projectId = projectId) }
    }

    /** Applies the light edit sheet; only fields that actually changed are queued. */
    suspend fun edit(taskId: Long, title: String, dueAt: Long?, projectId: Long) {
        val task = db.tasks().get(idRemap.resolve(taskId)) ?: return
        val newTitle = title.trim().ifEmpty { return }
        val changed = buildJsonObject {
            if (newTitle != task.title) put(TITLE, newTitle)
            if (dueAt != task.dueAt) put(DUE_DATE, dueJson(dueAt))
            if (projectId != task.projectId) put(PROJECT_ID, projectId)
        }
        if (changed.isEmpty()) return
        write(task.id, changed) { it.copy(title = newTitle, dueAt = dueAt, projectId = projectId) }
    }

    private fun normalizeHex(hexColor: String): String? {
        val digits = hexColor.trim().removePrefix("#")
        if (digits.isEmpty()) return ""
        if (digits.length != 6 || digits.any { it !in HEX_DIGITS }) return null
        return digits.lowercase()
    }

    private suspend fun write(taskId: Long, fields: JsonObject, update: (TaskEntity) -> TaskEntity) {
        val id = idRemap.resolve(taskId)
        val written = db.withTransaction {
            val task = db.tasks().get(id) ?: return@withTransaction false
            db.tasks().upsert(update(task))
            record(id, fields)
            true
        }
        if (written) onLocalChange()
    }

    private suspend fun record(taskId: Long, fields: JsonObject) {
        val existing = db.pendingChanges().forTask(taskId)
        val merged = PendingChanges.merge(existing, taskId, fields)
        if (existing == null) db.pendingChanges().insert(merged) else db.pendingChanges().update(merged)
    }

    companion object {
        const val TITLE = "title"
        const val DONE = "done"
        const val DUE_DATE = "due_date"
        const val PROJECT_ID = "project_id"

        /** Label names; only ever set by a capture, and sent once the task exists on the server. */
        const val LABELS = "labels"
        private const val HEX_DIGITS = "0123456789abcdefABCDEF"

        fun dueJson(dueAt: Long?): JsonElement =
            dueAt?.let { JsonPrimitive(VikunjaDates.format(Instant.ofEpochMilli(it))) } ?: JsonNull

        private fun fields(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    }
}
