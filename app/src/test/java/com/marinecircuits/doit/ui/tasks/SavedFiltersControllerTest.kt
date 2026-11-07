package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.data.SavedFilterTask
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.remote.ApiException
import com.marinecircuits.doit.data.remote.ProjectDto
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SavedFiltersControllerTest {
    private val filters = listOf(ProjectDto(-2, "Today"), ProjectDto(-3, "Waiting"))
    private fun task(id: Long) = SavedFilterTask(TaskEntity(id, "Task $id", null, 4, false, 0), emptyList())

    @Test
    fun lateResponseCannotReplaceAnotherFilterOrReopenAClosedPage() = runTest {
        val delayed = CompletableDeferred<List<SavedFilterTask>>()
        val controller = SavedFiltersController(backgroundScope, { filters }, { id ->
            if (id == -2L) withContext(NonCancellable) { delayed.await() } else listOf(task(3))
        })
        controller.open()
        runCurrent()
        controller.select(-2)
        runCurrent()
        controller.select(-3)
        runCurrent()
        delayed.complete(listOf(task(2)))
        runCurrent()
        assertEquals(-3L, controller.state.value.selected!!.id)
        assertEquals(listOf(3L), controller.state.value.tasks.map { it.task.id })
        controller.close()
        runCurrent()
        assertFalse(controller.state.value.open)
        assertTrue(controller.state.value.tasks.isEmpty())
    }

    @Test
    fun leavingWhileLoadingDiscardsThePendingResult() = runTest {
        val delayed = CompletableDeferred<List<ProjectDto>>()
        val controller = SavedFiltersController(backgroundScope, { withContext(NonCancellable) { delayed.await() } }, { emptyList() })
        controller.open()
        runCurrent()
        controller.close()
        delayed.complete(filters)
        runCurrent()
        assertFalse(controller.state.value.open)
        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.filters.isEmpty())
    }

    @Test
    fun refreshFailureKeepsResultsButRemovedFilterClearsThem() = runTest {
        var failure: Exception? = null
        val controller = SavedFiltersController(backgroundScope, { filters }, {
            failure?.let { throw it }
            listOf(task(9))
        })
        controller.open()
        runCurrent()
        controller.select(-2)
        runCurrent()
        failure = IOException("offline")
        controller.refresh()
        runCurrent()
        assertEquals(SavedFiltersError.CONNECTION, controller.state.value.error)
        assertEquals(9L, controller.state.value.tasks.single().task.id)
        failure = ApiException(403, "Forbidden")
        controller.refresh()
        runCurrent()
        assertEquals(SavedFiltersError.PERMISSION, controller.state.value.error)
        failure = ApiException(404, "Gone")
        controller.refresh()
        runCurrent()
        assertEquals(SavedFiltersError.REMOVED, controller.state.value.error)
        assertTrue(controller.state.value.tasks.isEmpty())
        assertFalse(controller.state.value.loading)
    }

    @Test
    fun backReturnsToCatalogThenHomeAndCanRetryAfterFailure() = runTest {
        var attempts = 0
        val controller = SavedFiltersController(backgroundScope, {
            if (++attempts == 1) throw IOException("offline") else filters
        }, { listOf(task(5)) })
        controller.open()
        runCurrent()
        assertEquals(SavedFiltersError.CONNECTION, controller.state.value.error)
        controller.refresh()
        runCurrent()
        assertEquals(filters, controller.state.value.filters)
        assertEquals(null, controller.state.value.error)
        controller.select(-2)
        runCurrent()
        controller.back()
        assertTrue(controller.state.value.open)
        assertEquals(null, controller.state.value.selected)
        assertEquals(filters, controller.state.value.filters)
        controller.back()
        assertFalse(controller.state.value.open)
    }
}
