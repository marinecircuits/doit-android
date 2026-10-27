package com.marinecircuits.doit.data.sync

import androidx.room.withTransaction
import com.marinecircuits.doit.data.Credentials
import com.marinecircuits.doit.data.IdRemap
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.PendingChangeEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.remote.ApiException
import com.marinecircuits.doit.data.remote.ProjectDto
import com.marinecircuits.doit.data.remote.TaskDto
import com.marinecircuits.doit.data.remote.VikunjaApi
import com.marinecircuits.doit.data.remote.VikunjaDates
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface SyncResult {
    data object Synced : SyncResult
    data object NotConnected : SyncResult
    data object TokenRejected : SyncResult
    data object MissingPermission : SyncResult
    data object Offline : SyncResult
    data class ServerError(val message: String) : SyncResult
}

/**
 * Sends pending changes in order, then replaces local data with the server's open tasks, projects and labels.
 * One sync runs at a time; a second request waits for the first to finish.
 */
class SyncEngine(
    private val db: DoitDatabase,
    private val settings: SettingsStore,
    private val idRemap: IdRemap,
    private val apiFor: (Credentials) -> VikunjaApi,
    private val onSynced: () -> Unit,
) {
    private val mutex = Mutex()

    /** Set once the label list was fetched again to resolve a name, so that happens at most once per sync. */
    private var labelsRefetched = false
    private val _lastResult = MutableStateFlow<SyncResult?>(null)

    /** Outcome of the latest sync in this process; null until one ran. */
    val lastResult: StateFlow<SyncResult?> = _lastResult.asStateFlow()

    suspend fun sync(): SyncResult = mutex.withLock {
        val result = runSync()
        _lastResult.value = result
        if (result != SyncResult.NotConnected) onSynced()
        result
    }

    private suspend fun runSync(): SyncResult {
        val credentials = settings.credentials() ?: return SyncResult.NotConnected
        if (settings.tokenRejected.first()) return SyncResult.TokenRejected
        val api = apiFor(credentials)
        labelsRefetched = false
        return try {
            push(api)
            pull(api)
            SyncResult.Synced
        } catch (e: ApiException) {
            if (e.isUnauthorized) unauthorized(api) else SyncResult.ServerError(e.message.orEmpty())
        } catch (e: IOException) {
            SyncResult.Offline
        }
    }

    /** A 401 means either a bad token or a token missing a permission; the token test tells which. */
    private suspend fun unauthorized(api: VikunjaApi): SyncResult = try {
        api.tokenTest()
        SyncResult.MissingPermission
    } catch (e: ApiException) {
        if (e.isUnauthorized) {
            settings.setTokenRejected()
            SyncResult.TokenRejected
        } else {
            SyncResult.ServerError(e.message.orEmpty())
        }
    } catch (e: IOException) {
        SyncResult.Offline
    }

    private suspend fun push(api: VikunjaApi) {
        // A change kept because it was edited mid-flight is sent again on the next turn; the cap only guards
        // against a server that keeps accepting without the queue shrinking.
        repeat(MAX_PUSHES) {
            val change = db.pendingChanges().first() ?: return
            pushOne(api, change)
        }
    }

    private suspend fun pushOne(api: VikunjaApi, change: PendingChangeEntity) {
        val fields = PendingChanges.decode(change.fields)
        try {
            when {
                change.kind == PendingChangeEntity.CREATE -> {
                    // `done` and `labels` go in follow-up updates: a completion is recorded like any other, and labels
                    // need the task's id.
                    val later = setOf(TaskRepository.DONE, TaskRepository.LABELS)
                    val projectId = fields[TaskRepository.PROJECT_ID]?.jsonPrimitive?.longOrNull ?: fallbackProjectId(api)
                    val created = api.createTask(projectId, JsonObject(fields - later))
                    applyCreated(change, created, leftover = JsonObject(fields.filterKeys { it in later }))
                }
                TaskRepository.LABELS in fields -> pushLabels(api, change, fields)
                else -> applyUpdated(change, api.updateTask(change.taskId, fields))
            }
        } catch (e: ApiException) {
            if (e.isUnauthorized || e.isTransient || e.status !in 400..499) throw e
            reject(change, e)
        }
    }

    /** Adds a capture's labels to its task, then takes them off the pending change; other fields go next turn. */
    private suspend fun pushLabels(api: VikunjaApi, change: PendingChangeEntity, fields: JsonObject) {
        val names = fields.getValue(TaskRepository.LABELS).jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }
        try {
            // Names whose label was deleted on the server since the capture.
            val gone = names.filterNot { name ->
                val labelId = labelId(api, name)
                labelId != null && addLabel(api, change.taskId, labelId)
            }
            if (gone.isNotEmpty()) {
                val quoted = gone.joinToString(", ") { "“$it”" }
                labelNotice(change, "$quoted no longer exist${if (gone.size == 1) "s" else ""}")
            }
        } catch (e: ApiException) {
            when {
                e.isNotFound -> return reject(change, e) // the task was deleted elsewhere
                e.isUnauthorized || e.isForbidden -> {
                    // A bad token fails here and is handled like any 401; one without label permissions passes.
                    api.tokenTest()
                    labelNotice(change, "the token isn't allowed to change labels")
                }
                e.isTransient || e.status !in 400..499 -> throw e
                else -> labelNotice(change, e.problem ?: "the server refused them (${e.status})")
            }
        }
        db.withTransaction {
            // Re-read: the change may have been edited while the labels were sent.
            val current = db.pendingChanges().get(change.seq) ?: return@withTransaction
            val rest = PendingChanges.decode(current.fields) - TaskRepository.LABELS
            if (rest.isEmpty()) {
                db.pendingChanges().delete(change.seq)
            } else {
                db.pendingChanges().update(current.copy(fields = JsonObject(rest).toString()))
            }
        }
    }

    /** The id of the label called [name], ignoring case, or null when it no longer exists. */
    private suspend fun labelId(api: VikunjaApi, name: String): Long? {
        db.labels().all().firstOrNull { it.title.equals(name, ignoreCase = true) }?.let { return it.id }
        if (labelsRefetched) return null
        labelsRefetched = true // it may have been renamed on another device since the last pull
        val fetched = fetchLabels(api) ?: return null
        replaceLabels(fetched)
        return fetched.firstOrNull { it.title.equals(name, ignoreCase = true) }?.id
    }

    /** True once the label is on the task, false when the label no longer exists. */
    private suspend fun addLabel(api: VikunjaApi, taskId: Long, labelId: Long): Boolean {
        try {
            api.addLabel(taskId, labelId)
        } catch (e: ApiException) {
            if (e.isNotFound) {
                // Either the task or the label is gone; only a missing task should drop the change.
                val fetched = fetchLabels(api)
                if (fetched != null) {
                    replaceLabels(fetched)
                    if (fetched.none { it.id == labelId }) return false
                }
                throw e
            }
            if (e.isUnauthorized || e.isForbidden || e.isTransient || e.status !in 400..499) throw e
            // Any other 4xx: the label is on the task already, which is what we wanted.
        }
        return true
    }

    /** The user's labels, or null when the token may not read them. */
    private suspend fun fetchLabels(api: VikunjaApi): List<LabelEntity>? = try {
        api.labels().filter { it.id > 0 }.distinctBy { it.id }.map { LabelEntity(it.id, it.title, it.hexColor) }
    } catch (e: ApiException) {
        if (e.isUnauthorized || e.isForbidden) null else throw e
    }

    private suspend fun replaceLabels(labels: List<LabelEntity>) = db.withTransaction {
        db.labels().clear()
        db.labels().insertAll(labels)
    }

    private suspend fun labelNotice(change: PendingChangeEntity, reason: String) {
        val title = db.tasks().get(change.taskId)?.title ?: return
        settings.addNotice("Couldn't add labels to “$title”: $reason")
    }

    private suspend fun fallbackProjectId(api: VikunjaApi): Long {
        val known = db.projects().all().filterNot { it.archived }
        val configured = settings.defaultProjectId.first()
        known.firstOrNull { it.id == configured }?.let { return it.id }
        known.firstOrNull()?.let { return it.id }
        return api.projects().firstOrNull { it.id > 0 && !it.isArchived }?.id ?: 0
    }

    private suspend fun applyCreated(change: PendingChangeEntity, created: TaskDto, leftover: JsonObject) {
        val temporaryId = change.taskId
        db.withTransaction {
            val current = db.pendingChanges().get(change.seq)
            db.tasks().delete(created.id)
            db.taskLabels().deleteForTask(created.id)
            db.tasks().changeId(temporaryId, created.id)
            db.taskLabels().changeTaskId(temporaryId, created.id)
            db.pendingChanges().changeTaskId(temporaryId, created.id)
            when {
                current != null && current.revision != change.revision ->
                    // Edited while the create was in flight: send everything except the one-off description again.
                    db.pendingChanges().update(
                        current.copy(
                            taskId = created.id,
                            kind = PendingChangeEntity.UPDATE,
                            fields = JsonObject(PendingChanges.decode(current.fields) - DESCRIPTION).toString(),
                        ),
                    )
                leftover.isNotEmpty() ->
                    db.pendingChanges().update(
                        change.copy(taskId = created.id, kind = PendingChangeEntity.UPDATE, fields = leftover.toString()),
                    )
                else -> {
                    db.pendingChanges().delete(change.seq)
                    db.tasks().upsert(created.toEntity(fallbackCreatedAt = db.tasks().get(created.id)?.createdAt))
                }
            }
        }
        idRemap.record(temporaryId, created.id)
    }

    private suspend fun applyUpdated(change: PendingChangeEntity, updated: TaskDto) {
        db.withTransaction {
            val current = db.pendingChanges().get(change.seq)
            if (current == null || current.revision == change.revision) {
                db.pendingChanges().delete(change.seq)
                db.tasks().upsert(updated.toEntity(fallbackCreatedAt = db.tasks().get(updated.id)?.createdAt))
            }
        }
    }

    /** The server refused the change: drop it and tell the user once. A refused create leaves nothing behind. */
    private suspend fun reject(change: PendingChangeEntity, error: ApiException) {
        val title = db.tasks().get(change.taskId)?.title
        db.withTransaction {
            db.pendingChanges().delete(change.seq)
            if (change.kind == PendingChangeEntity.CREATE || error.isNotFound) {
                db.tasks().delete(change.taskId)
                db.taskLabels().deleteForTask(change.taskId)
            }
        }
        // A 404 on an update just means the task was deleted elsewhere; anything else is worth telling.
        if (title != null && (!error.isNotFound || change.kind == PendingChangeEntity.CREATE)) {
            settings.addNotice("Couldn't save “$title”: ${error.problem ?: "the server refused the change (${error.status})"}")
        }
    }

    private suspend fun pull(api: VikunjaApi) {
        val defaultProjectId = try {
            api.currentUser().settings?.defaultProjectId
        } catch (e: ApiException) {
            // Tokens without the `other → user` permission can't read settings; fall back to the first project.
            if (e.isUnauthorized || e.isForbidden) null else throw e
        }
        val projects = api.projects()
        val tasks = api.openTasks()
        // Optional: a token that may not read labels keeps the list it has.
        val labels = fetchLabels(api)

        db.withTransaction {
            val generation = db.tasks().maxSyncGeneration() + 1
            db.projects().clear()
            db.projects().insertAll(projects.filter { it.id > 0 }.map { it.toEntity() })
            if (labels != null) replaceLabels(labels)
            val pending = db.pendingChanges().all().map { it.taskId }.toSet()
            val pulled = tasks.filter { it.id !in pending }
            db.tasks().upsertAll(
                pulled.map { it.toEntity(fallbackCreatedAt = db.tasks().get(it.id)?.createdAt, generation = generation) },
            )
            pulled.forEach { task -> db.taskLabels().set(task.id, task.labels.map { it.title }) }
            db.tasks().deleteNotSeenIn(generation)
            db.taskLabels().deleteOrphans()
        }
        if (defaultProjectId != null && defaultProjectId > 0) settings.setDefaultProjectId(defaultProjectId)
    }

    private companion object {
        const val MAX_PUSHES = 500
        const val DESCRIPTION = "description"

        fun TaskDto.toEntity(fallbackCreatedAt: Long?, generation: Long = 0) = TaskEntity(
            id = id,
            title = title,
            dueAt = VikunjaDates.parse(dueDate)?.toEpochMilli(),
            projectId = projectId,
            done = done,
            createdAt = VikunjaDates.parse(created)?.toEpochMilli() ?: fallbackCreatedAt ?: 0,
            syncGeneration = generation,
        )

        fun ProjectDto.toEntity() = ProjectEntity(id, title, isArchived, position, parentProjectId, hexColor)
    }
}
