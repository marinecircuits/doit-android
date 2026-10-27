package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.data.local.ProjectEntity
import com.marinecircuits.doit.data.local.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectTreeTest {
    private val work = ProjectEntity(1, "Work", archived = false, position = 0.0)
    private val firmware = ProjectEntity(2, "Firmware", archived = false, position = 1.0, parentProjectId = 1)
    private val drivers = ProjectEntity(3, "Drivers", archived = false, position = 2.0, parentProjectId = 2)
    private val home = ProjectEntity(4, "Home", archived = false, position = 3.0)
    private val old = ProjectEntity(5, "Old", archived = true, position = 4.0)
    private val notes = ProjectEntity(6, "Notes", archived = false, position = 5.0, parentProjectId = 5)
    private val shared = ProjectEntity(7, "Shared child", archived = false, position = 6.0, parentProjectId = 99)
    private val projects = listOf(work, firmware, drivers, home, old, notes, shared)

    @Test
    fun topLevelHasNoNestedProjectsButKeepsOrphansReachable() {
        assertEquals(listOf(work, home, notes, shared), ProjectTree.topLevel(projects))
    }

    @Test
    fun childrenAreImmediateAndNotArchived() {
        val archivedChild = ProjectEntity(8, "Archived child", archived = true, position = 7.0, parentProjectId = 1)
        assertEquals(listOf(firmware), ProjectTree.children(projects + archivedChild, 1))
        assertEquals(listOf(drivers), ProjectTree.children(projects, 2))
        assertEquals(emptyList<ProjectEntity>(), ProjectTree.children(projects, 4))
    }

    @Test
    fun pathIsCutWhereAProjectDisappearedOrMoved() {
        assertEquals(listOf(1L, 2L, 3L), ProjectTree.validPath(listOf(1, 2, 3), projects))
        assertEquals(listOf(1L), ProjectTree.validPath(listOf(1, 2, 3), projects - firmware))
        assertEquals(listOf(1L, 2L), ProjectTree.validPath(listOf(1, 2, 3), projects - drivers + drivers.copy(parentProjectId = 4)))
        assertEquals(emptyList<Long>(), ProjectTree.validPath(listOf(2), projects))
        assertEquals(emptyList<Long>(), ProjectTree.validPath(listOf(5), projects))
    }

    @Test
    fun pathToFollowsVisibleParents() {
        assertEquals(listOf(1L, 2L, 3L), ProjectTree.pathTo(3, projects))
        assertEquals(listOf(4L), ProjectTree.pathTo(4, projects))
        assertEquals(listOf(6L), ProjectTree.pathTo(6, projects))
        assertEquals(listOf(7L), ProjectTree.pathTo(7, projects))
        assertEquals(emptyList<Long>(), ProjectTree.pathTo(5, projects))
        assertEquals(emptyList<Long>(), ProjectTree.pathTo(99, projects))
    }

    @Test
    fun tilesCountOpenTasksDirectlyInTheProject() {
        val tasks = listOf(
            TaskEntity(1, "a", null, projectId = 1, done = false, createdAt = 0),
            TaskEntity(2, "b", null, projectId = 1, done = false, createdAt = 0),
            TaskEntity(3, "c", null, projectId = 1, done = true, createdAt = 0),
            TaskEntity(4, "d", null, projectId = 2, done = false, createdAt = 0),
        )
        assertEquals(
            listOf(ProjectTile(work, 2), ProjectTile(home, 0)),
            ProjectTree.tiles(listOf(work, home), tasks),
        )
    }
}
