package com.marinecircuits.doit.data

import androidx.room.withTransaction
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.PendingChangeEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.remote.VikunjaDates
import com.marinecircuits.doit.data.sync.PendingChanges
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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

    suspend fun openTasks(): List<TaskEntity> = db.tasks().open()

    /** A task captured offline can receive its server id while its completed row remains visible. */
    fun resolveTaskId(id: Long): Long = idRemap.resolve(id)

    /** The Vikunja default project if known and usable, otherwise the first non-archived project. */
    suspend fun defaultProjectId(): Long? {
        val projects = db.projects().all().filterNot { it.archived }
        val configured = settings.defaultProjectId.first()
        return projects.firstOrNull { it.id == configured }?.id ?: projects.firstOrNull()?.id
    }

    /** Creates a task in the default project. Returns its (temporary) id, or null for a blank title. */
    suspend fun capture(title: String, description: String? = null, targetProjectId: Long? = null): Long? {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return null
        val projectId = targetProjectId ?: defaultProjectId() ?: 0
        val id = db.withTransaction {
            val id = minOf(db.tasks().minId() ?: 0, 0) - 1
            db.tasks().upsert(
                TaskEntity(id, trimmed, dueAt = null, projectId = projectId, done = false, createdAt = clock.millis()),
            )
            val fields = buildJsonObject {
                put("title", trimmed)
                if (!description.isNullOrBlank()) put("description", description.trim())
                if (projectId > 0) put("project_id", projectId)
            }
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

        fun dueJson(dueAt: Long?): JsonElement =
            dueAt?.let { JsonPrimitive(VikunjaDates.format(Instant.ofEpochMilli(it))) } ?: JsonNull

        private fun fields(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    }
}
