package com.marinecircuits.doit.data

import androidx.room.withTransaction
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.remote.VikunjaApi
import kotlinx.coroutines.flow.first

class ProjectUnavailable : IllegalStateException("The destination project is no longer available")

/** Called under SyncEngine's mutex. No deletion is queued or retried automatically. */
class ProjectDeletion(private val db: DoitDatabase, private val settings: SettingsStore,
                      private val apiFor: (Credentials) -> VikunjaApi) {
    data class Project(val id: Long, val parent: Long, val title: String, val archived: Boolean)
    data class Task(val id: Long, val project: Long, val title: String, val done: Boolean)
    data class Preview(val id: Long, val credentials: Credentials, val projects: List<Project>, val tasks: List<Task>) {
        val title get() = projects.first { it.id == id }.title
        val nonempty get() = projects.size > 1 || tasks.isNotEmpty()
        val counts get() = "${projects.size - 1} subprojects and ${tasks.size} tasks (${tasks.count { it.done }} completed)"
    }
    class Changed(val preview: Preview) : Exception("Contents changed. Review the new counts before deleting.")

    suspend fun preview(id: Long): Preview {
        require(id > 0) { "Only ordinary projects can be deleted" }
        val credentials = checkNotNull(settings.credentials()) { "Connect before deleting a project" }
        val api = apiFor(credentials)
        val all = api.projectsForDeletion().filter { it.id > 0 }
        check(all.any { it.id == id }) { "Project is no longer available. Sync and try again." }
        check(all.map { it.id }.distinct().size == all.size) { "Could not count project contents" }
        val ids = mutableSetOf(id)
        do { val changed = ids.addAll(all.filter { it.parentProjectId in ids }.map { it.id }) } while (changed)
        val projects = all.filter { it.id in ids }.map { Project(it.id, it.parentProjectId, it.title, it.isArchived) }.sortedBy { it.id }
        val tasks = projects.flatMap { project ->
            api.projectTasks(project.id).map {
                check(it.id > 0 && it.projectId == project.id) { "Could not count project tasks" }
                Task(it.id, it.projectId, it.title, it.done)
            }
        }.sortedBy { it.id }
        check(tasks.map { it.id }.distinct().size == tasks.size) { "Project tasks changed while counting" }
        check(settings.credentials() == credentials) { "Connection changed; reopen Delete project" }
        return Preview(id, credentials, projects, tasks)
    }

    suspend fun delete(expected: Preview) {
        check(settings.credentials() == expected.credentials) { "Connection changed; reopen Delete project" }
        val fresh = preview(expected.id)
        if (fresh != expected) throw Changed(fresh)
        // Serialize the final local check and request with task writes. Failed requests retain the cache.
        val files = db.withTransaction {
            check(settings.credentials() == expected.credentials) { "Connection changed; reopen Delete project" }
            check(db.pendingChanges().all().isEmpty() && db.attachments().pending().isEmpty() &&
                settings.pendingProjectMarkers.first().isEmpty() && settings.pendingProjectColors().isEmpty() &&
                settings.pendingProjectArchives().isEmpty() && settings.pendingProjectTitles().isEmpty()) {
                "Let unsent changes finish syncing before deleting a project"
            }
            apiFor(expected.credentials).deleteProject(expected.id)
            val ids = expected.projects.map { it.id }
            val taskIds = (expected.tasks.map { it.id } + db.tasks().inProjects(ids).map { it.id }).distinct()
            val files = taskIds.flatMap { db.attachments().forTask(it) }.map { it.localPath }.filter { it.isNotEmpty() }
            for (taskId in taskIds) {
                db.attachments().deleteForTask(taskId)
                db.taskLabels().deleteForTask(taskId)
            }
            db.taskHistory().deleteInProjects(ids)
            db.tasks().deleteInProjects(ids)
            db.taskLabels().deleteOrphans()
            db.projects().deleteIds(ids)
            files
        }
        files.forEach { runCatching { java.io.File(it).delete() } }
    }
}
