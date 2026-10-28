package com.marinecircuits.doit.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marinecircuits.doit.data.CaptureSyntax
import com.marinecircuits.doit.data.Doing
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
    /** Details of the latest server error, cleared when a later sync has a different outcome. */
    val syncError: String? = null,
    val notices: List<String> = emptyList(),
    /** Number of unsent changes the user must confirm losing before signing out. */
    val signOutConfirmation: Int? = null,
    /** Set when the capture box opened a label. Mutually exclusive with [path]. */
    val openLabel: LabelEntity? = null,
    val contextLabelIds: List<Long?> = listOf(null, null, null),
    val contextIndex: Int? = null,
    val contextScreen: Boolean = false,
    val serverAddress: String? = null,
    val doingLabel: LabelEntity? = null,
    val doingCount: Int = 0,
    val doingTaskIds: Set<Long> = emptySet(),
)

private data class CompletedSnapshot(val task: TaskEntity, val labels: List<String>, val doingLabelId: Long?)

private data class ListInputs(
    val open: List<TaskEntity>,
    val projects: List<ProjectEntity>,
    val defaultProjectId: Long?,
    val today: LocalDate,
    val completed: Map<Long, CompletedSnapshot>,
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
    private val completedHere = MutableStateFlow<Map<Long, CompletedSnapshot>>(emptyMap())

    /** Ids of the projects opened from the main view, outermost first; empty on the main view. */
    private val projectPath = MutableStateFlow<List<Long>>(emptyList())

    /** The label opened from the capture box, or null when a project or the main view is showing. */
    private val openLabelId = MutableStateFlow<Long?>(null)
    private val contextIndex = MutableStateFlow<Int?>(null)

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

    private val list = combine(inputs, projectPath, labelInputs, openLabelId, settings.contextLabelIds, contextIndex) { values ->
        val latest = values[0] as ListInputs
        val path = values[1] as List<Long>
        val labels = values[2] as LabelInputs
        val labelId = values[3] as Long?
        @Suppress("UNCHECKED_CAST")
        val contextIds = values[4] as List<Long?>
        val index = (values[5] as Int?)?.takeIf { slot ->
            labels.labels.any { it.id == contextIds.getOrNull(slot) }
        }
        val doing = Doing.label(labels.labels)
        val doingIds = if (doing == null) emptySet() else latest.open.filter { task ->
            Doing.contains(labels.byTask[task.id].orEmpty(), doing)
        }.mapTo(HashSet()) { it.id }
        view(latest, path, labels, if (index != null) contextIds[index] else labelId).copy(
            contextLabelIds = contextIds,
            contextIndex = index,
            contextScreen = index != null,
            doingLabel = doing,
            doingCount = doingIds.size,
            doingTaskIds = doingIds,
        )
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
        val tasks = open + completed.values.filter { it.task.id !in openIds }.map { it.task.copy(done = true) }
        val doing = Doing.label(labels.labels)
        val retained = completed.filterKeys { it !in openIds }
        val labelsForMatch = labels.byTask + retained.mapValues { (_, snapshot) -> snapshot.labels }
        val taskLabels = labels.byTask + retained.mapValues { (_, snapshot) ->
            snapshot.labels.filterNot { name -> doing != null && name.equals(doing.title, ignoreCase = true) }
        }
        val openLabel = labelId?.let { id -> labels.labels.find { it.id == id } }
        if (openLabel != null) {
            val shown = tasks.filter { task ->
                labelsForMatch[task.id].orEmpty().any { it.equals(openLabel.title, ignoreCase = true) }
            }
            return TasksUiState(
                today = day,
                rows = TaskListModel.rows(
                    shown,
                    projects,
                    null,
                    day,
                    clock.zone,
                    taskLabels = taskLabels,
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
                taskLabels = taskLabels,
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
                syncError = (lastResult as? SyncResult.ServerError)?.message?.takeIf { it.isNotBlank() },
                notices = notices,
            )
        }
            .combine(refreshing) { state, refreshing -> state.copy(refreshing = refreshing) }
            .combine(signOutConfirmation) { state, pending -> state.copy(signOutConfirmation = pending) }
            .combine(settings.serverAddress) { state, address -> state.copy(serverAddress = address) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, TasksUiState(today = today.value))

    /** The user left the view: tasks completed here leave the list. */
    fun onLeave() {
        completedHere.value = emptyMap()
    }

    private val openPath: List<Long> get() = state.value.path.map { it.id }

    /** Opens a project shown as a tile in the current view. */
    fun openProject(projectId: Long) {
        contextIndex.value = null
        onLeave()
        openLabelId.value = null
        projectPath.value = openPath + projectId
    }

    /** Back from a label or project screen: closes the label, or returns to the parent project or the main view. */
    fun navigateUp() {
        if (state.value.contextScreen) {
            onLeave()
            contextIndex.value = null
            return
        }
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
        if (openPath.isEmpty() && openLabelId.value == null && contextIndex.value == null) return
        contextIndex.value = null
        onLeave()
        openLabelId.value = null
        projectPath.value = emptyList()
    }

    /** Swipe right from home through configured label screens; missing and disabled slots are skipped. */
    fun nextContext() {
        val current = state.value
        if (current.path.isNotEmpty() || (!current.contextScreen && openLabelId.value != null)) return
        val active = current.contextLabelIds.indices.filter { slot ->
            current.labels.any { it.id == current.contextLabelIds[slot] }
        }
        val next = active.firstOrNull { it > (current.contextIndex ?: -1) } ?: return
        onLeave()
        contextIndex.value = next
    }

    /** Swipe left to the previous configured screen, or home from the first one. */
    fun previousContext() {
        val current = state.value
        val index = current.contextIndex ?: return
        val previous = current.contextLabelIds.indices.lastOrNull { slot ->
            slot < index && current.labels.any { it.id == current.contextLabelIds[slot] }
        }
        onLeave()
        contextIndex.value = previous
    }

    fun setContextLabel(slot: Int, id: Long?) {
        if (state.value.contextIndex == slot && id == null) contextIndex.value = null
        viewModelScope.launch { settings.setContextLabelId(slot, id) }
    }

    /** The app came to the foreground: the date may have changed, and the list should catch up with the server. */
    fun onForeground() {
        today.value = LocalDate.now(clock)
        viewModelScope.launch { sync() }
    }

    /** Pull to refresh shows the refresh indicator while its sync is running. */
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
    fun capture(text: String, dueDate: LocalDate? = null, onCreated: (Long) -> Unit) {
        val current = state.value
        when (val destination = CaptureSyntax.destination(text, current.labels, current.projects)) {
            is CaptureSyntax.Destination.Project -> {
                contextIndex.value = null
                onLeave()
                openLabelId.value = null
                projectPath.value = ProjectTree.pathTo(destination.id, current.projects)
            }
            is CaptureSyntax.Destination.Label -> {
                contextIndex.value = null
                onLeave()
                projectPath.value = emptyList()
                openLabelId.value = destination.id
            }
            null -> {
                val projectId = current.path.lastOrNull()?.id
                viewModelScope.launch { repository.capture(text, projectId = projectId, dueDate = dueDate)?.let(onCreated) }
            }
        }
    }

    /** Sets the colour of the open project. Ignored on the main view and on a label screen. */
    fun setProjectColor(hexColor: String) {
        val projectId = state.value.path.lastOrNull()?.id ?: return
        viewModelScope.launch { repository.setProjectColor(projectId, hexColor) }
    }

    fun complete(task: TaskEntity) {
        val current = state.value
        val names = current.rows.firstOrNull { it.task.id == task.id }?.labels?.map { it.title }.orEmpty()
        val doingId = current.doingLabel?.takeIf { Doing.contains(names, it) }?.id
        completedHere.update { it + (task.id to CompletedSnapshot(task, names, doingId)) }
        viewModelScope.launch { repository.complete(task.id) }
    }

    /** Unchecking a struck-through row restores the task as it was before it was completed. */
    fun uncomplete(task: TaskEntity) {
        val original = completedHere.value[task.id] ?: CompletedSnapshot(task.copy(done = false), emptyList(), null)
        viewModelScope.launch {
            repository.undoComplete(original.task, original.labels, original.doingLabelId)
            // Dropped only now: the reopened task is in the open list, so the row never flickers out.
            completedHere.update { it - task.id }
        }
    }

    fun setDoing(task: TaskEntity, active: Boolean) {
        viewModelScope.launch { repository.setDoing(task.id, active) }
    }

    /** Open the cross-project list of tasks bearing the doing label. */
    fun reviewDoing() {
        val label = state.value.doingLabel ?: return
        onLeave()
        contextIndex.value = null
        projectPath.value = emptyList()
        openLabelId.value = label.id
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
