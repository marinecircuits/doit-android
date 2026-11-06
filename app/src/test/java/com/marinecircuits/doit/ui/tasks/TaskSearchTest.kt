package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskSearchTest {
    @Test fun filtersRequireEveryLabelAndIncludeDescendants() {
        val rows = listOf(row(10, "Root", 1, false, "wait", "next"), row(11, "Child", 2, false, "wait", "next"),
            row(12, "Missing label", 2, false, "wait"), row(13, "Elsewhere", 4, false, "wait", "next"),
            row(14, "Done", 1, true, "wait", "next"))
        val labels = listOf(LabelEntity(8, "wait"), LabelEntity(9, "next"))
        assertEquals(listOf(10L, 11L), TaskSearch.matches("@WAIT @next #Work", rows, listOf(work, firmware), labels).map { it.id })
        assertEquals(emptyList<TaskSearch.Result>(), TaskSearch.matches("@missing #Work", rows, listOf(work), labels))
        assertEquals(emptyList<TaskSearch.Result>(), TaskSearch.matches("@wait #missing", rows, listOf(work), labels))
    }

    @Test fun filtersSupportQuotedNamesAndRejectOrdinaryCaptureText() {
        assertEquals(TaskSearch.Filter("home office", setOf("on hold")), TaskSearch.filter("@\"on hold\" #\"Home office\""))
        for (text in listOf("Call @wait #Work", "@wait", "#Work", "@wait #Work #Other", "@wait#Work", "@\"wait #Work"))
            assertNull(TaskSearch.filter(text))
    }

    private val work = ProjectEntity(1, "Work", false, 0.0)
    private val firmware = ProjectEntity(2, "Firmware", false, 1.0, parentProjectId = 1)
    private val archived = ProjectEntity(3, "Old", true, 2.0)
    private val labels = listOf(LabelEntity(9, "next"))
    private fun row(id: Long, title: String, project: Long, done: Boolean = false, vararg names: String) = TaskRow(
        TaskEntity(id, title, null, project, done, 0), null, false, null,
        labels = names.map { LabelChip(it) },
    )

    @Test fun slashMustLeadCapture() {
        assertEquals("firmware", TaskSearch.query(" /firmware "))
        assertEquals("", TaskSearch.query("/"))
        assertNull(TaskSearch.query("Fix /firmware"))
        assertNull(TaskSearch.query("https://example.com"))
    }

    @Test fun searchUsesAllWordsAcrossTitlePathAndLabels() {
        val results = TaskSearch.matches("work next", listOf(row(10, "Flash board", 2, false, "next")),
            listOf(work, firmware), labels)
        assertEquals(listOf(10L), results.map { it.id })
        assertEquals("Work › Firmware · @next", results.single().context)
    }

    @Test fun resultsAreGroupedAndExcludeCompletedTasksAndArchivedProjects() {
        val rows = listOf(row(10, "Firmware update", 2), row(11, "Firmware done", 2, done = true))
        val results = TaskSearch.matches("firmware", rows, listOf(work, firmware, archived), listOf(LabelEntity(9, "firmware")))
        assertEquals(listOf(TaskSearch.Kind.PROJECT, TaskSearch.Kind.TASK, TaskSearch.Kind.LABEL), results.map { it.kind })
        assertEquals(listOf(2L, 10L, 9L), results.map { it.id })
    }

    @Test fun emptyAndMissingQueriesDoNotReturnResults() {
        assertEquals(emptyList<TaskSearch.Result>(), TaskSearch.matches(" ", listOf(row(10, "Task", 1)), listOf(work), labels))
        assertEquals(emptyList<TaskSearch.Result>(), TaskSearch.matches("missing", listOf(row(10, "Task", 1)), listOf(work), labels))
    }
}
