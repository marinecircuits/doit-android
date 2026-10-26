package com.marinecircuits.doit.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val OPEN_TASKS = """
    SELECT tasks.* FROM tasks LEFT JOIN projects ON projects.id = tasks.projectId
    WHERE tasks.done = 0 AND (projects.archived IS NULL OR projects.archived = 0)
"""

@Dao
interface TaskDao {
    @Query(OPEN_TASKS)
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query(OPEN_TASKS)
    suspend fun open(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Upsert
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT MIN(id) FROM tasks")
    suspend fun minId(): Long?

    @Query("UPDATE tasks SET id = :newId WHERE id = :oldId")
    suspend fun changeId(oldId: Long, newId: Long)

    /** Removes server tasks that the latest pull did not return, unless they still have unsent changes. */
    @Query(
        """DELETE FROM tasks WHERE id > 0 AND syncGeneration != :generation
           AND id NOT IN (SELECT taskId FROM pending_changes)""",
    )
    suspend fun deleteNotSeenIn(generation: Long)

    @Query("DELETE FROM tasks")
    suspend fun clear()
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY position, title")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY position, title")
    suspend fun all(): List<ProjectEntity>

    @Insert
    suspend fun insertAll(projects: List<ProjectEntity>)

    @Query("DELETE FROM projects")
    suspend fun clear()
}

@Dao
interface PendingChangeDao {
    @Query("SELECT * FROM pending_changes ORDER BY seq")
    suspend fun all(): List<PendingChangeEntity>

    @Query("SELECT * FROM pending_changes WHERE taskId = :taskId")
    suspend fun forTask(taskId: Long): PendingChangeEntity?

    @Query("SELECT * FROM pending_changes WHERE seq = :seq")
    suspend fun get(seq: Long): PendingChangeEntity?

    @Query("SELECT COUNT(*) FROM pending_changes")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_changes")
    suspend fun count(): Int

    @Insert
    suspend fun insert(change: PendingChangeEntity): Long

    @Update
    suspend fun update(change: PendingChangeEntity)

    @Query("DELETE FROM pending_changes WHERE seq = :seq")
    suspend fun delete(seq: Long)

    @Query("UPDATE pending_changes SET taskId = :newId WHERE taskId = :oldId")
    suspend fun changeTaskId(oldId: Long, newId: Long)

    @Query("DELETE FROM pending_changes")
    suspend fun clear()
}
