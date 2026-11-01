package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.local.ProjectEntity

/** Search is a capture mode only when slash is the first non-space character. */
object TaskSearch {
    enum class Kind { PROJECT, TASK, LABEL }

    data class Result(val kind: Kind, val id: Long, val title: String, val context: String)

    fun query(text: String): String? = text.trimStart().takeIf { it.startsWith('/') }?.drop(1)?.trim()

    fun matches(query: String, rows: List<TaskRow>, projects: List<ProjectEntity>, labels: List<LabelEntity>): List<Result> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val visible = projects.filterNot { it.archived }
        val byId = visible.associateBy { it.id }
        val paths = visible.associate { project ->
            project.id to ProjectTree.pathTo(project.id, visible).mapNotNull { byId[it]?.title }.joinToString(" › ")
        }
        fun path(id: Long): String = paths[id].orEmpty()

        val projectResults = visible.map { Result(Kind.PROJECT, it.id, it.title, path(it.id)) }
        val taskResults = rows.filterNot { it.task.done || it.completed }.map { row ->
            val context = listOf(path(row.task.projectId), row.labels.joinToString(" ") { "@${it.title}" })
                .filter { it.isNotEmpty() }.joinToString(" · ")
            Result(Kind.TASK, row.task.id, row.task.title, context)
        }
        val labelResults = labels.map { Result(Kind.LABEL, it.id, "@${it.title}", "Label") }
        return (projectResults + taskResults + labelResults).filter { result ->
            val searchable = "${result.title} ${result.context}".lowercase()
            words.all(searchable::contains)
        }
    }
}
