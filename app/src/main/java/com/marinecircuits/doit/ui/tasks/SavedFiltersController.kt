package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.data.SavedFilterTask
import com.marinecircuits.doit.data.remote.ApiException
import com.marinecircuits.doit.data.remote.ProjectDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SavedFiltersError { CONNECTION, PERMISSION, REMOVED, OTHER }

data class SavedFiltersState(
    val open: Boolean = false,
    val filters: List<ProjectDto> = emptyList(),
    val selected: ProjectDto? = null,
    val tasks: List<SavedFilterTask> = emptyList(),
    val loading: Boolean = false,
    val error: SavedFiltersError? = null,
)

/** Keeps page navigation and request cancellation independent of the normal project/label views. */
class SavedFiltersController(
    private val scope: CoroutineScope,
    private val loadFilters: suspend () -> List<ProjectDto>,
    private val loadTasks: suspend (Long) -> List<SavedFilterTask>,
) {
    private val mutableState = MutableStateFlow(SavedFiltersState())
    val state: StateFlow<SavedFiltersState> = mutableState
    private var request: Job? = null
    private var generation = 0

    fun open() {
        mutableState.update { it.copy(open = true, selected = null, tasks = emptyList()) }
        refresh()
    }

    fun select(id: Long) {
        val filter = state.value.filters.firstOrNull { it.id == id && id < -1 } ?: return
        mutableState.update { it.copy(selected = filter, tasks = emptyList()) }
        refresh()
    }

    fun refresh() {
        if (!state.value.open) return
        request?.cancel()
        val current = ++generation
        val selected = state.value.selected
        mutableState.update { it.copy(loading = true, error = null) }
        request = scope.launch {
            try {
                if (selected == null) {
                    val filters = loadFilters()
                    if (current == generation) mutableState.update { it.copy(filters = filters, loading = false) }
                } else {
                    val tasks = loadTasks(selected.id)
                    if (current == generation) mutableState.update { it.copy(tasks = tasks, loading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = when {
                    e is java.io.IOException -> SavedFiltersError.CONNECTION
                    e is ApiException && (e.isUnauthorized || e.isForbidden) -> SavedFiltersError.PERMISSION
                    selected != null && e is ApiException && e.isNotFound -> SavedFiltersError.REMOVED
                    else -> SavedFiltersError.OTHER
                }
                if (current == generation) mutableState.update {
                    it.copy(loading = false, error = error,
                        tasks = if (error == SavedFiltersError.REMOVED) emptyList() else it.tasks)
                }
            }
        }
    }

    fun back() {
        if (state.value.selected != null) {
            cancelRequest()
            mutableState.update { it.copy(selected = null, tasks = emptyList(), loading = false, error = null) }
        } else close()
    }

    fun close() {
        cancelRequest()
        mutableState.update { it.copy(open = false, selected = null, tasks = emptyList(), loading = false, error = null) }
    }

    fun forgetTask(id: Long) {
        mutableState.update { it.copy(tasks = it.tasks.filterNot { result -> result.task.id == id }) }
    }

    private fun cancelRequest() {
        ++generation
        request?.cancel()
    }
}
