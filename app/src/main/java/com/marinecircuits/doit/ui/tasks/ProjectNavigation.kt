package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.data.local.ProjectEntity

/** Keep orphaned projects visible at the root rather than making them unreachable. */
object ProjectNavigation {
    fun topLevel(projects: List<ProjectEntity>): List<ProjectEntity> {
        val active = projects.filterNot { it.archived }
        val ids = active.mapTo(mutableSetOf()) { it.id }
        return active.filter { it.parentProjectId <= 0 || it.parentProjectId !in ids || it.parentProjectId == it.id }
    }
}
