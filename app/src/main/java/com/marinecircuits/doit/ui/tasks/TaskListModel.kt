package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.data.DueDates
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import com.marinecircuits.doit.data.sync.SyncResult
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One row of the list: the task plus what the row shows next to its title. */
data class TaskRow(
    val task: TaskEntity,
    /** The local due date, or null for an undated task. */
    val dueDate: LocalDate?,
    val overdue: Boolean,
    /** Shown under the title for tasks outside the default project. */
    val projectTitle: String?,
    /** Completed in the current view: shown struck through until the user leaves the view. */
    val completed: Boolean = false,
    /** Shown as chips under the title. */
    val labels: List<LabelChip> = emptyList(),
)

/** A label on a row; [hexColor] is empty for a label without a colour or one the app doesn't know. */
data class LabelChip(val title: String, val hexColor: String = "")

object TaskListModel {
    /**
     * Overdue first, then today and later (both by due time), then undated tasks newest first.
     * Sorting dated tasks by instant puts overdue ones first on its own. Done tasks (completed in the current view)
     * keep the place they had while open. [taskLabels] maps task ids to their label names, in order.
     */
    fun rows(
        tasks: List<TaskEntity>,
        projects: List<ProjectEntity>,
        defaultProjectId: Long?,
        today: LocalDate,
        zone: ZoneId,
        taskLabels: Map<Long, List<String>> = emptyMap(),
        labels: List<LabelEntity> = emptyList(),
    ): List<TaskRow> {
        val titles = projects.associate { it.id to it.title }
        val colors = labels.reversed().associate { it.title.lowercase() to it.hexColor } // the first of equal names wins
        val (dated, undated) = tasks.partition { it.dueAt != null }
        val ordered = dated.sortedWith(compareBy<TaskEntity> { it.dueAt }.thenBy { it.id }) +
            undated.sortedWith(compareByDescending<TaskEntity> { it.createdAt }.thenBy { it.id })
        return ordered.map { task ->
            val dueDate = task.dueAt?.let { DueDates.localDate(it, zone) }
            TaskRow(
                task = task,
                dueDate = dueDate,
                overdue = !task.done && dueDate != null && dueDate < today,
                projectTitle = titles[task.projectId]?.takeIf { task.projectId != defaultProjectId },
                completed = task.done,
                labels = taskLabels[task.id].orEmpty().map { LabelChip(it, colors[it.lowercase()].orEmpty()) },
            )
        }
    }
}

/** The words a due label uses, so the formatting can be tested without Android resources. */
data class DueWords(val today: String, val tomorrow: String, val yesterday: String)

object DueLabels {
    fun label(date: LocalDate, today: LocalDate, words: DueWords, locale: Locale): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> words.today
            days == 1L -> words.tomorrow
            days == -1L -> words.yesterday
            days in 2..6 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM", locale))
            else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
        }
    }
}

/** What the status line under the capture box says. Empty when everything is synced. */
sealed interface StatusPart {
    data object Offline : StatusPart
    data object Failed : StatusPart
    data object MissingPermission : StatusPart
    data class Waiting(val count: Int) : StatusPart
}

object SyncStatus {
    fun parts(lastResult: SyncResult?, pendingCount: Int): List<StatusPart> = listOfNotNull(
        when (lastResult) {
            SyncResult.Offline -> StatusPart.Offline
            is SyncResult.ServerError -> StatusPart.Failed
            SyncResult.MissingPermission -> StatusPart.MissingPermission
            else -> null
        },
        StatusPart.Waiting(pendingCount).takeIf { pendingCount > 0 },
    )

    /** Waiting changes alone are normal; an offline, rejected-token or failed sync needs attention. */
    fun needsAttention(parts: List<StatusPart>, tokenRejected: Boolean): Boolean =
        tokenRejected || parts.any { it == StatusPart.Offline || it == StatusPart.Failed || it == StatusPart.MissingPermission }
}

/** The fixed choices of the quick reschedule sheet. */
enum class QuickDate { TODAY, TOMORROW, NEXT_WEEK }

object QuickDates {
    fun dateFor(choice: QuickDate, today: LocalDate): LocalDate = when (choice) {
        QuickDate.TODAY -> today
        QuickDate.TOMORROW -> today.plusDays(1)
        QuickDate.NEXT_WEEK -> DueDates.nextMonday(today)
    }
}
