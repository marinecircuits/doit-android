package com.marinecircuits.doit.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A task as the app shows it. Tasks captured offline have negative ids until the server assigns one.
 * Times are epoch milliseconds.
 */
@Entity(tableName = "tasks", indices = [Index("projectId")])
data class TaskEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val dueAt: Long?,
    val projectId: Long,
    val done: Boolean,
    val createdAt: Long,
    /** The sync that last saw this task on the server; used to sweep tasks the server no longer lists. */
    val syncGeneration: Long = 0,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val archived: Boolean,
    val position: Double,
)

/**
 * The unsent local changes of one task. Later changes are merged into the same row, so there is at most one row
 * per task; [revision] grows with every merge so the sync can tell whether the row changed while it was being sent.
 */
@Entity(tableName = "pending_changes", indices = [Index(value = ["taskId"], unique = true)])
data class PendingChangeEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val taskId: Long,
    val kind: String,
    /** A JSON object with Vikunja field names, e.g. `{"done":true,"due_date":null}`. */
    val fields: String,
    val revision: Int = 0,
) {
    companion object {
        const val CREATE = "create"
        const val UPDATE = "update"
    }
}
