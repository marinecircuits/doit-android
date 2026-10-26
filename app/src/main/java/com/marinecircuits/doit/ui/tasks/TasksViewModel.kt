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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TasksUiState(
    val today: LocalDate,
    val rows: List<TaskRow> = emptyList(),
    /** False until the database answered once, so an empty list isn't mistaken for "nothing to do". */
    val loaded: Boolean = false,
    /** Non-archived projects, for the edit sheet. */
    val projects: List<ProjectEntity> = emptyList(),
    val status: List<StatusPart> = emptyList(),
    val syncedOnce: Boolean = false,
    val refreshing: Boolean = false,
    val tokenRejected: Boolean = false,
    val notices: List<String> = emptyList(),
    /** Number of unsent changes the user must confirm losing before signing out. */
    val signOutConfirmation: Int? = null,
)

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

    /**
     * Tasks completed in this view, as they were before completion. They stay listed, struck through, until the user
     * leaves the view, even after a sync removed them from the database.
     */
    private val completedHere = MutableStateFlow<Map<Long, TaskEntity>>(emptyMap())

    private val list = combine(
        repository.openTasks,
        repository.projects,
        settings.defaultProjectId,
        today,
        completedHere,
    ) { open, projects, defaultProjectId, day, completed ->
        // A repeating task the server reopened shows as the open task, not as a struck-through copy.
        val openIds = open.mapTo(HashSet()) { it.id }
        val tasks = open + completed.values.filter { it.id !in openIds }.map { it.copy(done = true) }
        TasksUiState(
            today = day,
            rows = TaskListModel.rows(tasks, projects, defaultProjectId, day, clock.zone),
            loaded = true,
            projects = projects.filterNot { it.archived },
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

    /** The user left the view: tasks completed here leave the list. */
    fun onLeave() {
        completedHere.value = emptyMap()
    }

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
        viewModelScope.launch { repository.capture(text)?.let(onCreated) }
    }

    fun complete(task: TaskEntity) {
        completedHere.update { it + (task.id to task) }
        viewModelScope.launch { repository.complete(task.id) }
    }

    /** Unchecking a struck-through row restores the task as it was before it was completed. */
    fun uncomplete(task: TaskEntity) {
        val original = completedHere.value[task.id] ?: task.copy(done = false)
        viewModelScope.launch {
            repository.undoComplete(original)
            // Dropped only now: the reopened task is in the open list, so the row never flickers out.
            completedHere.update { it - task.id }
        }
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
