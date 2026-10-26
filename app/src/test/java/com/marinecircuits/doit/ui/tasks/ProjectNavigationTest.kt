package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.data.local.ProjectEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectNavigationTest {
    @Test
    fun rootTilesExcludeChildrenButIncludeOrphans() {
        val projects = listOf(
            ProjectEntity(1, "Work", false, 0.0),
            ProjectEntity(2, "Firmware", false, 1.0, parentProjectId = 1),
            ProjectEntity(3, "Orphan", false, 2.0, parentProjectId = 99),
            ProjectEntity(4, "Archived", true, 3.0),
            ProjectEntity(5, "Child of archived", false, 4.0, parentProjectId = 4),
        )

        assertEquals(listOf(1L, 3L, 5L), ProjectNavigation.topLevel(projects).map { it.id })
    }
}
