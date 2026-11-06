package com.marinecircuits.doit.data.local

import androidx.room.ColumnInfo
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
    /** Vikunja description, including any markup. Empty when the task has none. */
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(defaultValue = "0") val repeatAfter: Long = 0,
    @ColumnInfo(defaultValue = "0") val repeatMode: Int = 0,
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val archived: Boolean,
    val position: Double,
    /** 0 for a top-level project. */
    @ColumnInfo(defaultValue = "0") val parentProjectId: Long = 0,
    /** As Vikunja sends it; empty for no colour. See [com.marinecircuits.doit.ui.tasks.ProjectColors]. */
    @ColumnInfo(defaultValue = "") val hexColor: String = "",
    @ColumnInfo(defaultValue = "") val description: String = "",
)

/** The Vikunja inbox is the default project. A project still titled Inbox counts too. */
fun ProjectEntity.isInbox(defaultProjectId: Long?): Boolean =
    id == defaultProjectId || title.equals("Inbox", ignoreCase = true)

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
        const val DELETE = "delete"
    }
}

/** One of the user's labels, as of the last sync. */
@Entity(tableName = "labels")
data class LabelEntity(
    @PrimaryKey val id: Long,
    val title: String,
    /** As Vikunja sends it; empty for no colour. */
    val hexColor: String = "",
)

/**
 * A label on a task, by name: a task captured offline can carry labels before any id is known. Colours come from
 * [LabelEntity] with the same title, ignoring case.
 */
@Entity(tableName = "task_labels", primaryKeys = ["taskId", "title"])
data class TaskLabelEntity(
    val taskId: Long,
    val title: String,
    /** Order of the labels on the task. */
    val position: Int,
)

/** A durable, device-local record of an action, stored in the same transaction as the task change. */
@Entity(tableName = "task_history", indices = [Index("taskId")])
data class TaskHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val projectId: Long,
    val kind: String,
    val happenedAt: Long,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    val dueAt: Long? = null,
    @ColumnInfo(defaultValue = "'[]'") val labelsJson: String = "[]",
    @ColumnInfo(defaultValue = "'{}'") val pendingJson: String = "{}",
    @ColumnInfo(defaultValue = "0") val repeatAfter: Long = 0,
    @ColumnInfo(defaultValue = "0") val repeatMode: Int = 0,
) {
    companion object {
        const val COMPLETED = "completed"
        const val DELETED = "deleted"
    }
}


/**
 * A PDF attached to a task. Offline tasks keep a local file until the task exists on the server.
 * [pending] is empty when idle, "upload" until the file is sent, or "delete" until the server copy is removed.
 */
@Entity(tableName = "attachments", indices = [Index("taskId")])
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val filename: String,
    @ColumnInfo(defaultValue = "'application/pdf'") val mime: String = "application/pdf",
    @ColumnInfo(defaultValue = "0") val size: Long = 0,
    /** Server attachment id, null until uploaded. */
    val remoteId: Long? = null,
    /** Absolute path to the local file. Empty when only metadata is known. */
    @ColumnInfo(defaultValue = "''") val localPath: String = "",
    @ColumnInfo(defaultValue = "''") val pending: String = "",
) {
    companion object {
        const val UPLOAD = "upload"
        const val DELETE = "delete"
        const val CANCEL = "cancel"
    }
}
