package com.marinecircuits.doit.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marinecircuits.doit.data.CaptureSyntax
import com.marinecircuits.doit.data.SettingsStore
import com.marinecircuits.doit.data.TaskRepository
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.sync.SyncResult
import java.time.Clock
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
    /** The open project and its ancestors, outermost first; empty on the main view. */
    val path: List<ProjectEntity> = emptyList(),
    /** Top-level projects on the main view, the open project's subprojects on a project screen. */
    val tiles: List<ProjectTile> = emptyList(),
    /** The user's labels as of the last sync, for `@` suggestions in the capture box. */
    val labels: List<LabelEntity> = emptyList(),
    val status: List<StatusPart> = emptyList(),
    val syncedOnce: Boolean = false,
    val refreshing: Boolean = false,
    val tokenRejected: Boolean = false,
    val notices: List<String> = emptyList(),
    /** Number of unsent changes the user must confirm losing before signing out. */
    val signOutConfirmation: Int? = null,
    /** Set when the capture box opened a label. Mutually exclusive with [path]. */
    val openLabel: LabelEntity? = null,
)

private data class ListInputs(
    val open: List<TaskEntity>,
    val projects: List<ProjectEntity>,
    val defaultProjectId: Long?,
    val today: LocalDate,
    val completed: Map<Long, TaskEntity>,
)

private data class LabelInputs(val labels: List<LabelEntity>, val byTask: Map<Long, List<String>>)

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

    /** Ids of the projects opened from the main view, outermost first; empty on the main view. */
    private val projectPath = MutableStateFlow<List<Long>>(emptyList())

    /** The label opened from the capture box, or null when a project or the main view is showing. */
    private val openLabelId = MutableStateFlow<Long?>(null)

    private val inputs = combine(
        repository.openTasks,
        repository.projects,
        settings.defaultProjectId,
        today,
        completedHere,
    ) { open, projects, defaultProjectId, day, completed -> ListInputs(open, projects, defaultProjectId, day, completed) }

    private val labelInputs = combine(repository.labels, repository.taskLabels) { labels, taskLabels ->
        LabelInputs(labels, taskLabels.groupBy({ it.taskId }, { it.title }))
    }

    private val list = combine(inputs, projectPath, labelInputs, openLabelId) { latest, path, labels, labelId ->
        view(latest, path, labels, labelId)
    }

    private fun view(inputs: ListInputs, requestedPath: List<Long>, labels: LabelInputs, labelId: Long?): TasksUiState {
        val (open, projects, defaultProjectId, day, completed) = inputs
        val visible = projects.filterNot { it.archived }
        val byId = visible.associateBy { it.id }
        // Falls back to the nearest remaining ancestor when the open project was deleted or archived elsewhere.
        val path = ProjectTree.validPath(requestedPath, projects).map(byId::getValue)
        val current = path.lastOrNull()

        // A repeating task the server reopened shows as the open task, not as a struck-through copy.
        val openIds = open.mapTo(HashSet()) { it.id }
        val tasks = open + completed.values.filter { it.id !in openIds }.map { it.copy(done = true) }
        val openLabel = labelId?.let { id -> labels.labels.find { it.id == id } }
        if (openLabel != null) {
            val shown = tasks.filter { task ->
                labels.byTask[task.id].orEmpty().any { it.equals(openLabel.title, ignoreCase = true) }
            }
            return TasksUiState(
                today = day,
                rows = TaskListModel.rows(
                    shown,
                    projects,
                    null,
                    day,
                    clock.zone,
                    taskLabels = labels.byTask,
                    labels = labels.labels,
                ),
                loaded = true,
                projects = visible,
                labels = labels.labels,
                openLabel = openLabel,
            )
        }
        val shown = if (current == null) tasks else tasks.filter { it.projectId == current.id }
        val tileProjects = if (current == null) ProjectTree.topLevel(projects) else ProjectTree.children(projects, current.id)
        return TasksUiState(
            today = day,
            // On a project screen every task is in the open project, so passing it as the "default" hides all tags.
            rows = TaskListModel.rows(
                shown,
                projects,
                current?.id ?: defaultProjectId,
                day,
                clock.zone,
                taskLabels = labels.byTask,
                labels = labels.labels,
            ),
            loaded = true,
            projects = visible,
            path = path,
            tiles = ProjectTree.tiles(tileProjects, open),
            labels = labels.labels,
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

    private val openPath: List<Long> get() = state.value.path.map { it.id }

    /** Opens a project shown as a tile in the current view. */
    fun openProject(projectId: Long) {
        onLeave()
        openLabelId.value = null
        projectPath.value = openPath + projectId
    }

    /** Back from a label or project screen: closes the label, or returns to the parent project or the main view. */
    fun navigateUp() {
        if (openLabelId.value != null) {
            onLeave()
            openLabelId.value = null
            return
        }
        if (openPath.isEmpty()) return
        onLeave()
        projectPath.value = openPath.dropLast(1)
    }

    fun navigateHome() {
        if (openPath.isEmpty() && openLabelId.value == null) return
        onLeave()
        openLabelId.value = null
        projectPath.value = emptyList()
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

    /**
     * A capture that is only `#project` or `@label` opens that view.
     * Anything else is captured into a named `#project`, otherwise the open project or the default on the main view.
     */
    fun capture(text: String, onCreated: (Long) -> Unit) {
        val current = state.value
        when (val destination = CaptureSyntax.destination(text, current.labels, current.projects)) {
            is CaptureSyntax.Destination.Project -> {
                onLeave()
                openLabelId.value = null
                projectPath.value = ProjectTree.pathTo(destination.id, current.projects)
            }
            is CaptureSyntax.Destination.Label -> {
                onLeave()
                projectPath.value = emptyList()
                openLabelId.value = destination.id
            }
            null -> {
                val projectId = current.path.lastOrNull()?.id
                viewModelScope.launch { repository.capture(text, projectId = projectId)?.let(onCreated) }
            }
        }
    }

    /** Sets the colour of the open project. Ignored on the main view and on a label screen. */
    fun setProjectColor(hexColor: String) {
        val projectId = state.value.path.lastOrNull()?.id ?: return
        viewModelScope.launch { repository.setProjectColor(projectId, hexColor) }
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

    fun rename(task: TaskEntity, title: String) {
        viewModelScope.launch { repository.rename(task.id, title) }
    }

    /** Saves an edited title, including `@label` and `#project` tokens. */
    fun applyEditedTitle(task: TaskEntity, raw: String) {
        viewModelScope.launch { repository.applyEditedTitle(task.id, raw) }
    }

    fun move(task: TaskEntity, projectId: Long) {
        viewModelScope.launch { repository.move(task.id, projectId) }
    }

    fun delete(task: TaskEntity) {
        completedHere.update { it - task.id }
        viewModelScope.launch { repository.delete(task.id) }
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
