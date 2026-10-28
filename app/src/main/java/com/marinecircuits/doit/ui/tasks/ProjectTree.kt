package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity

/** A project tile: the project and the number of open tasks directly in it. */
data class ProjectTile(val project: ProjectEntity, val openCount: Int)

/** A project-picker row, with a zero-based visible nesting depth. */
data class ProjectPickerItem(val project: ProjectEntity, val depth: Int)

/** The project hierarchy as the app shows it: archived projects are left out everywhere. */
object ProjectTree {
    /**
     * Projects without a visible parent: top-level ones, and subprojects of archived or unshared projects, which would
     * otherwise be unreachable.
     */
    fun topLevel(projects: List<ProjectEntity>): List<ProjectEntity> {
        val visible = projects.filterNot { it.archived }
        val ids = visible.mapTo(HashSet()) { it.id }
        return visible.filter { it.parentProjectId == 0L || it.parentProjectId == it.id || it.parentProjectId !in ids }
    }

    fun children(projects: List<ProjectEntity>, parentId: Long): List<ProjectEntity> =
        projects.filter { !it.archived && it.parentProjectId == parentId && it.id != parentId }

    /**
     * Parent-first rows, preserving the supplied order among roots and siblings.
     * Orphans remain selectable, and malformed cycles cannot hide projects or loop forever.
     */
    fun pickerItems(projects: List<ProjectEntity>): List<ProjectPickerItem> {
        val visible = projects.filterNot { it.archived }
        val byParent = visible.groupBy { it.parentProjectId }
        val seen = HashSet<Long>()
        val result = mutableListOf<ProjectPickerItem>()

        fun appendTree(root: ProjectEntity) {
            val pending = ArrayDeque<ProjectPickerItem>()
            pending.addLast(ProjectPickerItem(root, 0))
            while (pending.isNotEmpty()) {
                val item = pending.removeLast()
                if (!seen.add(item.project.id)) continue
                result += item
                byParent[item.project.id].orEmpty().asReversed().forEach { child ->
                    pending.addLast(ProjectPickerItem(child, item.depth + 1))
                }
            }
        }

        topLevel(visible).forEach(::appendTree)
        // A cycle has no root: still expose each remaining project exactly once.
        visible.filterNot { it.id in seen }.forEach(::appendTree)
        return result
    }

    /** Cuts [path] at the first project that is gone, archived, or no longer below the one before it. */
    fun validPath(path: List<Long>, projects: List<ProjectEntity>): List<Long> {
        val topLevelIds = topLevel(projects).mapTo(HashSet()) { it.id }
        val visible = projects.filterNot { it.archived }.associateBy { it.id }
        val valid = mutableListOf<Long>()
        for (id in path) {
            val project = visible[id] ?: break
            val placed = if (valid.isEmpty()) id in topLevelIds else project.parentProjectId == valid.last()
            if (!placed) break
            valid += id
        }
        return valid
    }

    /** Ancestors of [projectId], outermost first, or empty when that project is gone or archived. */
    fun pathTo(projectId: Long, projects: List<ProjectEntity>): List<Long> {
        val visible = projects.filterNot { it.archived }.associateBy { it.id }
        val chain = ArrayDeque<Long>()
        val seen = HashSet<Long>()
        var id: Long? = projectId
        while (id != null && id in visible && seen.add(id)) {
            chain.addFirst(id)
            val parent = visible.getValue(id).parentProjectId
            id = parent.takeIf { it != 0L && it != id && it in visible }
        }
        return validPath(chain.toList(), projects)
    }

    fun tiles(projects: List<ProjectEntity>, openTasks: List<TaskEntity>): List<ProjectTile> {
        val counts = openTasks.filterNot { it.done }.groupingBy { it.projectId }.eachCount()
        return projects.map { ProjectTile(it, counts[it.id] ?: 0) }
    }
}
