package com.marinecircuits.doit.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marinecircuits.doit.data.DueDates
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.sync.SyncResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksUiState(
    val today: LocalDate,
    val rows: List<TaskRow> = emptyList(),
    /** False until the database answered once, so an empty list isn't mistaken for "nothing to do". */
    val loaded: Boolean = false,
    /** Non-archived projects, for the edit sheet. */
    val projects: List<ProjectEntity> = emptyList(),
    val projectPath: List<Long> = emptyList(),
    val defaultProjectId: Long? = null,
    val status: List<StatusPart> = emptyList(),
    val syncedOnce: Boolean = false,
    val refreshing: Boolean = false,
    val tokenRejected: Boolean = false,
    val notices: List<String> = emptyList(),
    /** Number of unsent changes the user must confirm losing before signing out. */
    val signOutConfirmation: Int? = null,
)

private data class RetainedCompleted(val row: TaskRow, val index: Int)

class TasksViewModel(
    private val repository: TaskRepository,
    private val settings: SettingsStore,
    syncResults: StateFlow<SyncResult?>,
    private val sync: suspend () -> SyncResult,
    private val signOut: suspend () -> Unit,
    private val clock: Clock,
) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now(clock))
    private val refreshing = MutableStateFlow(false)
    private val signOutConfirmation = MutableStateFlow<Int?>(null)
    private val projectPath = MutableStateFlow<List<Long>>(emptyList())
    private val retainedCompleted = MutableStateFlow<Map<Long, RetainedCompleted>>(emptyMap())

    private val openList = combine(repository.openTasks, repository.projects, settings.defaultProjectId, today) {
            tasks, projects, defaultProjectId, day ->
        TasksUiState(
            today = day,
            rows = TaskListModel.rows(tasks, projects, defaultProjectId, day, clock.zone),
            loaded = true,
            projects = projects.filterNot { it.archived },
            defaultProjectId = defaultProjectId,
        )
    }

    private val list = combine(openList, retainedCompleted, projectPath) { base, completed, path ->
        val retained = completed.values.map { entry ->
            val id = repository.resolveTaskId(entry.row.task.id)
            entry.copy(row = entry.row.copy(task = entry.row.task.copy(id = id)))
        }
        val retainedIds = retained.mapTo(mutableSetOf()) { it.row.task.id }
        val rows = base.rows.filterNotTo(mutableListOf()) { it.task.id in retainedIds }
        retained.sortedBy { it.index }.forEach { entry ->
            rows.add(entry.index.coerceIn(0, rows.size), entry.row)
        }
        base.copy(
            rows = rows,
            projectPath = path,
        )
    }

    val state: StateFlow<TasksUiState> =
        combine(list, repository.pendingCount, syncResults, settings.tokenRejected, settings.notices) {
                base, pending, lastResult, tokenRejected, notices ->
            base.copy(
                status = SyncStatus.parts(lastResult, pending),
                syncedOnce = lastResult != null,
                tokenRejected = tokenRejected,
                notices = notices,
            )
        }
            .combine(refreshing) { state, refreshing -> state.copy(refreshing = refreshing) }
            .combine(signOutConfirmation) { state, pending -> state.copy(signOutConfirmation = pending) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TasksUiState(today = today.value))

    /** The app came to the foreground: the date may have changed, and the list should catch up with the server. */
    fun onForeground() {
        today.value = LocalDate.now(clock)
        viewModelScope.launch { sync() }
    }

    /** Pull to refresh and "Sync now": the only syncs that show the refresh indicator. */
    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                sync()
            } finally {
                refreshing.value = false
            }
        }
    }

    fun capture(text: String, onCreated: (Long) -> Unit) {
        val targetProjectId = projectPath.value.lastOrNull()
        viewModelScope.launch {
            val projectId = targetProjectId ?: repository.defaultProjectId()
            repository.capture(text, targetProjectId = projectId)?.let { id ->
                if (targetProjectId == null && projectId != null) openProject(projectId)
                onCreated(id)
            }
        }
    }

    fun complete(task: TaskEntity) {
        if (task.done) return
        val index = state.value.rows.indexOfFirst { it.task.id == task.id }
        val row = state.value.rows.getOrNull(index)
        if (row != null) {
            retainedCompleted.value = retainedCompleted.value +
                (task.id to RetainedCompleted(row.copy(task = task.copy(done = true)), index))
        }
        viewModelScope.launch { repository.complete(task.id) }
    }

    fun undoComplete(task: TaskEntity) {
        viewModelScope.launch {
            repository.undoComplete(task)
            val resolvedId = repository.resolveTaskId(task.id)
            retainedCompleted.value = retainedCompleted.value.filterKeys {
                repository.resolveTaskId(it) != resolvedId
            }
        }
    }

    fun openProject(projectId: Long) {
        if (projectId !in state.value.projects.map { it.id }) return
        retainedCompleted.value = emptyMap()
        projectPath.value = projectPath.value + projectId
    }

    fun navigateBack() {
        if (projectPath.value.isEmpty()) return
        retainedCompleted.value = emptyMap()
        projectPath.value = projectPath.value.dropLast(1)
    }

    fun reschedule(task: TaskEntity, date: LocalDate?) {
        viewModelScope.launch { repository.reschedule(task.id, date) }
    }

    /** From the edit sheet, which only picks dates: an unchanged date keeps the exact due time. */
    fun edit(task: TaskEntity, title: String, date: LocalDate?, projectId: Long) {
        val dueAt = when {
            date == null -> null
            task.dueAt != null && DueDates.localDate(task.dueAt, clock.zone) == date -> task.dueAt
            else -> DueDates.withDate(task.dueAt?.let(Instant::ofEpochMilli), date, clock.zone).toEpochMilli()
        }
        viewModelScope.launch { repository.edit(task.id, title, dueAt, projectId) }
    }

    fun requestSignOut() {
        viewModelScope.launch {
            val pending = repository.pendingCount.first()
            if (pending > 0) signOutConfirmation.value = pending else signOut()
        }
    }

    fun confirmSignOut() {
        signOutConfirmation.value = null
        viewModelScope.launch { signOut() }
    }

    fun dismissSignOut() {
        signOutConfirmation.value = null
    }

    fun clearNotices() {
        viewModelScope.launch { settings.clearNotices() }
    }
}
