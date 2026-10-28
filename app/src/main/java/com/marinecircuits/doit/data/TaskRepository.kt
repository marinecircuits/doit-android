package com.marinecircuits.doit.data

import androidx.room.withTransaction
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.PendingChangeEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.local.TaskHistoryEntity
import com.marinecircuits.doit.data.local.TaskLabelEntity
import com.marinecircuits.doit.data.remote.VikunjaDates
import com.marinecircuits.doit.data.remote.VikunjaApi
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
    val taskHistory: Flow<List<TaskHistoryEntity>> = db.taskHistory().observeAll()

    suspend fun openTasks(): List<TaskEntity> = db.tasks().open()

    /** Resolve a task captured offline after the server assigned its permanent ID. */
    fun resolveTaskId(id: Long): Long = idRemap.resolve(id)

    /** Recent server-completed tasks, including completions from other clients. */
    suspend fun recentCompleted(): List<TaskHistoryEntity> {
        val credentials = settings.credentials() ?: return emptyList()
        val excluded = db.pendingChanges().all().filter { change ->
            change.kind == PendingChangeEntity.DELETE || PendingChanges.decode(change.fields)[DONE] == JsonPrimitive(false)
        }.map { it.taskId }.toSet()
        return VikunjaApi(credentials.apiBase, credentials.token).recentCompletedTasks()
            .filter { it.done && it.id > 0 && it.id !in excluded }
            .map { task ->
                TaskHistoryEntity(taskId = task.id, title = task.title, projectId = task.projectId,
                    kind = TaskHistoryEntity.COMPLETED,
                    happenedAt = VikunjaDates.parse(task.doneAt)?.toEpochMilli() ?: 0)
            }
    }

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
    suspend fun capture(text: String, description: String? = null, projectId: Long? = null, dueDate: LocalDate? = null): Long? {
        val fallback = projectId ?: defaultProjectId() ?: 0
        val shopping = settings.shoppingProjects.first()
        val (title, parsedLabels, namedProjectId) = Shopping.parse(text, db.labels().all(), db.projects().all(), fallback, shopping)
        if (title.isEmpty()) return null
        val destination = namedProjectId ?: fallback
        val labelNames = if (destination in shopping) parsedLabels.filterNot { it.equals("doing", ignoreCase = true) } else parsedLabels
        if (labelNames.size != parsedLabels.size) settings.addNotice("Added “$title” without @doing: this project is a shopping list")
        val dueAt = dueDate?.let { DueDates.withDate(null, it, clock.zone).toEpochMilli() }
        val id = db.withTransaction {
            val id = minOf(db.tasks().minId() ?: 0, 0) - 1
            db.tasks().upsert(
                TaskEntity(id, title, dueAt = dueAt, projectId = destination, done = false, createdAt = clock.millis()),
            )
            val fields = buildJsonObject {
                put("title", title)
                if (!description.isNullOrBlank()) put("description", description.trim())
                if (destination > 0) put("project_id", destination)
                if (dueAt != null) put(DUE_DATE, dueJson(dueAt))
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
        val id = idRemap.resolve(taskId)
        val written = db.withTransaction {
            val task = db.tasks().get(id) ?: return@withTransaction false
            if (task.done || db.pendingChanges().forTask(id)?.kind == PendingChangeEntity.DELETE) return@withTransaction false
            val label = Doing.label(db.labels().all())
            if (label != null && Doing.contains(db.taskLabels().forTask(id), label)) recordDoing(id, label, false)
            db.taskHistory().insert(TaskHistoryEntity(taskId = task.id, title = task.title, projectId = task.projectId, kind = TaskHistoryEntity.COMPLETED, happenedAt = clock.millis()))
            db.taskHistory().trim(TaskHistoryEntity.COMPLETED)
            db.tasks().upsert(task.copy(done = true))
            record(id, fields(DONE to JsonPrimitive(true)))
            true
        }
        if (written) onLocalChange()
    }

    /** Reopens a task exactly as it was before it was completed, even if the sync already removed it. */
    suspend fun undoComplete(previous: TaskEntity, labels: List<String>? = null, doingLabelId: Long? = null) {
        val id = idRemap.resolve(previous.id)
        val written = db.withTransaction {
            if (db.pendingChanges().forTask(id)?.kind == PendingChangeEntity.DELETE) return@withTransaction false
            val existing = db.tasks().get(id)
            val current = existing ?: previous.copy(id = id)
            if (existing == null && labels != null) db.taskLabels().set(id, labels)
            db.taskHistory().deleteForTask(id, TaskHistoryEntity.COMPLETED)
            db.tasks().upsert(current.copy(done = false, dueAt = previous.dueAt))
            record(id, fields(DONE to JsonPrimitive(false), DUE_DATE to dueJson(previous.dueAt)))
            val label = db.labels().all().firstOrNull { it.id == doingLabelId }
            if (label != null && current.projectId !in settings.shoppingProjects.first()) recordDoing(id, label, true)
            true
        }
        if (written) { Shopping.enforce(db, settings); onLocalChange() }
    }

    /** Changes only the existing doing label, storing the latest desired membership with the task. */
    suspend fun setDoing(taskId: Long, active: Boolean) {
        val id = idRemap.resolve(taskId)
        val changed = db.withTransaction {
            val task = db.tasks().get(id) ?: return@withTransaction false
            if (task.done || db.pendingChanges().forTask(id)?.kind == PendingChangeEntity.DELETE) return@withTransaction false
            if (active && task.projectId in settings.shoppingProjects.first()) return@withTransaction false
            val label = Doing.label(db.labels().all()) ?: return@withTransaction false
            if (Doing.contains(db.taskLabels().forTask(id), label) == active) return@withTransaction false
            recordDoing(id, label, active)
            true
        }
        if (changed) onLocalChange()
    }

    private suspend fun recordDoing(id: Long, label: LabelEntity, active: Boolean) {
        val names = db.taskLabels().forTask(id).filterNot { it.equals(label.title, ignoreCase = true) }
        db.taskLabels().set(id, names + if (active) listOf(label.title) else emptyList())
        val pending = db.pendingChanges().forTask(id)?.let { PendingChanges.decode(it.fields) }
        val fields = buildJsonObject {
            put(DOING, buildJsonObject { put("id", label.id); put("active", active) })
            pending?.get(LABELS)?.jsonArray?.let { queued ->
                put(LABELS, JsonArray(queued.filterNot { it.jsonPrimitive.contentOrNull?.equals(label.title, ignoreCase = true) == true }))
            }
        }
        record(id, fields)
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
            val shopping = settings.shoppingProjects.first()
            val parsed = Shopping.parse(raw, db.labels().all(), db.projects().all(), task.projectId, shopping)
            val newTitle = parsed.title.ifEmpty { task.title }
            val existing = db.taskLabels().forTask(id)
            val projectId = parsed.projectId ?: task.projectId
            val added = parsed.labels.filter { name -> (projectId !in shopping || !name.equals("doing", ignoreCase = true)) && existing.none { it.equals(name, ignoreCase = true) } }
            val queued = db.pendingChanges().forTask(id)?.let { change ->
                PendingChanges.decode(change.fields)[LABELS]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
            }.orEmpty()
            val doing = Doing.label(db.labels().all())
            val supersedesDoing = doing != null && added.any { it.equals(doing.title, ignoreCase = true) } &&
                db.pendingChanges().forTask(id)?.let { DOING in PendingChanges.decode(it.fields) } == true
            val fields = buildJsonObject {
                if (newTitle != task.title) put(TITLE, newTitle)
                if (projectId != task.projectId) put(PROJECT_ID, projectId)
                if (added.isNotEmpty()) {
                    val names = (queued + added).distinctBy { it.lowercase() }
                    put(LABELS, JsonArray(names.filterNot { supersedesDoing && it.equals(doing?.title, ignoreCase = true) }.map { JsonPrimitive(it) }))
                }
                if (supersedesDoing && doing != null) put(DOING, buildJsonObject { put("id", doing.id); put("active", true) })
            }
            if (fields.isEmpty()) return@withTransaction false
            db.tasks().upsert(task.copy(title = newTitle, projectId = projectId))
            if (added.isNotEmpty()) db.taskLabels().set(id, existing + added)
            record(id, fields)
            true
        }
        if (written) { Shopping.enforce(db, settings); onLocalChange() }
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
            db.taskHistory().insert(TaskHistoryEntity(taskId = task.id, title = task.title, projectId = task.projectId, kind = TaskHistoryEntity.DELETED, happenedAt = clock.millis()))
            db.taskHistory().trim(TaskHistoryEntity.DELETED)
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
    suspend fun setShoppingProject(projectId: Long, active: Boolean) {
        if (db.projects().all().none { it.id == projectId }) return
        settings.setShoppingProject(projectId, active)
        if (active) Shopping.enforce(db, settings)
        onLocalChange()
    }

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
        if (written) { Shopping.enforce(db, settings); onLocalChange() }
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
        /** Desired membership of the existing doing label, sent only through task-label routes. */
        const val DOING = "_doing"
        private const val HEX_DIGITS = "0123456789abcdefABCDEF"

        fun dueJson(dueAt: Long?): JsonElement =
            dueAt?.let { JsonPrimitive(VikunjaDates.format(Instant.ofEpochMilli(it))) } ?: JsonNull

        private fun fields(vararg pairs: Pair<String, JsonElement>) = JsonObject(mapOf(*pairs))
    }
}
