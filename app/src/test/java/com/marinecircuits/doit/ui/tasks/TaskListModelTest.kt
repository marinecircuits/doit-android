package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.sync.SyncResult
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskListModelTest {
    private val zone = ZoneId.of("Europe/Oslo")
    private val today = LocalDate.of(2026, 10, 26) // a Monday
    private val words = DueWords("today", "tomorrow", "yesterday")

    private fun at(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    private fun task(id: Long, title: String, due: String? = null, createdAt: Long = 0, projectId: Long = 1) =
        TaskEntity(id, title, due?.let(::at), projectId, done = false, createdAt = createdAt)

    @Test
    fun quantitiesAppearOnlyForShoppingProjectsIncludingCompletedRows() {
        val rows = TaskListModel.rows(
            listOf(task(1, "Milk [one carton]", projectId = 2).copy(done = true), task(2, "Draft [one carton]")),
            emptyList(), 1, today, zone, shoppingProjects = setOf(2),
        ).associateBy { it.task.id }
        assertEquals("Milk", rows[1]!!.displayTitle)
        assertEquals("one carton", rows[1]!!.quantity)
        assertEquals("Milk [one carton]", rows[1]!!.task.title)
        assertEquals("Draft [one carton]", rows[2]!!.displayTitle)
        assertNull(rows[2]!!.quantity)
    }

    @Test
    fun showsTheDescriptionAndOneIconTargetPerAddress() {
        val rows = TaskListModel.rows(
            listOf(task(1, "Read").copy(description = "See https://example.com/a and https://example.com/a")),
            emptyList(),
            1,
            today,
            zone,
        )
        assertEquals("See https://example.com/a and https://example.com/a", rows.single().description)
        assertEquals(listOf("https://example.com/a"), rows.single().links)
    }

    @Test
    fun ordersOverdueThenDatedThenUndatedNewestFirst() {
        val rows = TaskListModel.rows(
            listOf(
                task(1, "undated old", createdAt = 1),
                task(2, "friday", "2026-10-30T12:00"),
                task(3, "today", "2026-10-26T12:00"),
                task(4, "yesterday", "2026-10-25T12:00"),
                task(5, "undated new", createdAt = 2),
                task(6, "today early", "2026-10-26T08:00"),
            ),
            projects = emptyList(),
            defaultProjectId = 1,
            today = today,
            zone = zone,
        )
        assertEquals(
            listOf("yesterday", "today early", "today", "friday", "undated new", "undated old"),
            rows.map { it.task.title },
        )
        assertTrue(rows.first().overdue)
        assertFalse(rows[1].overdue)
    }

    @Test
    fun completedTasksKeepTheirPlaceAndAreNeverOverdue() {
        val rows = TaskListModel.rows(
            listOf(
                task(1, "late", "2026-10-20T12:00"),
                task(2, "done late", "2026-10-21T12:00").copy(done = true),
                task(3, "today", "2026-10-26T12:00"),
            ),
            emptyList(),
            1,
            today,
            zone,
        )
        assertEquals(listOf("late", "done late", "today"), rows.map { it.task.title })
        assertTrue(rows[1].completed)
        assertFalse(rows[1].overdue)
        assertFalse(rows[0].completed)
        assertTrue(rows[0].overdue)
    }

    @Test
    fun dueTodayIsNotOverdueAfterItsTime() {
        val row = TaskListModel.rows(listOf(task(1, "a", "2026-10-26T00:30")), emptyList(), 1, today, zone).single()
        assertFalse(row.overdue)
        assertEquals(today, row.dueDate)
    }

    @Test
    fun projectTagOnlyOutsideTheDefaultProject() {
        val projects = listOf(ProjectEntity(1, "Inbox", false, 0.0), ProjectEntity(2, "Work", false, 1.0))
        val rows = TaskListModel.rows(
            listOf(task(1, "a", projectId = 1, createdAt = 2), task(2, "b", projectId = 2, createdAt = 1)),
            projects,
            defaultProjectId = 1,
            today = today,
            zone = zone,
        )
        assertNull(rows[0].projectTitle)
        assertEquals("Work", rows[1].projectTitle)
    }

    @Test
    fun labelChipsTakeTheLabelColourIgnoringCase() {
        val rows = TaskListModel.rows(
            listOf(task(1, "a", createdAt = 2), task(2, "b", createdAt = 1)),
            emptyList(),
            defaultProjectId = 1,
            today = today,
            zone = zone,
            taskLabels = mapOf(1L to listOf("Errand", "15 min", "gone")),
            labels = listOf(LabelEntity(7, "errand", "e8a33d"), LabelEntity(9, "15 min")),
        )
        assertEquals(
            listOf(LabelChip("Errand", "e8a33d"), LabelChip("15 min", ""), LabelChip("gone", "")),
            rows[0].labels,
        )
        assertEquals(emptyList<LabelChip>(), rows[1].labels)
    }

    @Test
    fun dueLabels() {
        fun label(date: String) = DueLabels.label(LocalDate.parse(date), today, words, Locale.ENGLISH)
        assertEquals("today", label("2026-10-26"))
        assertEquals("tomorrow", label("2026-10-27"))
        assertEquals("yesterday", label("2026-10-25"))
        assertEquals("Thu", label("2026-10-29"))
        assertEquals("Sun", label("2026-11-01"))
        assertEquals("2 Nov", label("2026-11-02"))
        assertEquals("20 Oct", label("2026-10-20"))
        assertEquals("5 Jan 2027", label("2027-01-05"))
    }

    @Test
    fun statusLine() {
        assertEquals(emptyList<StatusPart>(), SyncStatus.parts(SyncResult.Synced, 0))
        assertEquals(emptyList<StatusPart>(), SyncStatus.parts(null, 0))
        assertEquals(
            listOf(StatusPart.Offline, StatusPart.Waiting(2)),
            SyncStatus.parts(SyncResult.Offline, 2),
        )
        assertEquals(listOf(StatusPart.Waiting(1)), SyncStatus.parts(SyncResult.Synced, 1))
        assertEquals(listOf(StatusPart.Failed), SyncStatus.parts(SyncResult.ServerError("503"), 0))
    }

    @Test
    fun aboutMenuMarksFailuresButNotOrdinaryWaitingChanges() {
        assertFalse(SyncStatus.needsAttention(emptyList(), tokenRejected = false))
        assertFalse(SyncStatus.needsAttention(listOf(StatusPart.Waiting(1)), tokenRejected = false))
        assertTrue(SyncStatus.needsAttention(listOf(StatusPart.Offline), tokenRejected = false))
        assertTrue(SyncStatus.needsAttention(listOf(StatusPart.Failed, StatusPart.Waiting(2)), tokenRejected = false))
        assertTrue(SyncStatus.needsAttention(listOf(StatusPart.MissingPermission), tokenRejected = false))
        assertTrue(SyncStatus.needsAttention(emptyList(), tokenRejected = true))
    }


}
