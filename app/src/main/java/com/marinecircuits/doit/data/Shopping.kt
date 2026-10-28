package com.marinecircuits.doit.data

import androidx.room.withTransaction
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.sync.PendingChanges
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Quantities are interoperable title text, not a separate server field. */
object Shopping {
    private val suffix = Regex("""^(.*?)\s+\[([^\[\]]*\S[^\[\]]*)]$""")

    fun item(title: String): Pair<String, String?> {
        val match = suffix.matchEntire(title) ?: return title to null
        val name = match.groupValues[1].trimEnd()
        val quantity = match.groupValues[2].trim()
        return if (name.isEmpty() || quantity.isEmpty()) title to null else name to quantity
    }

    fun parse(text: String, labels: List<com.marinecircuits.doit.data.local.LabelEntity>, projects: List<com.marinecircuits.doit.data.local.ProjectEntity>, fallback: Long, shopping: Set<Long>): CaptureSyntax.Parsed {
        val (item, quantity) = item(text.trim())
        val prefix = CaptureSyntax.parse(item, labels, projects)
        if (quantity != null && (prefix.projectId ?: fallback) in shopping) {
            return prefix.copy(title = if (prefix.title.isEmpty()) "" else "${prefix.title} [$quantity]")
        }
        return CaptureSyntax.parse(text, labels, projects)
    }

    /** Queue required removals without overwriting pending edits, including an in-flight label add. */
    suspend fun enforce(db: DoitDatabase, settings: SettingsStore): Boolean {
        val projects = settings.shoppingProjects.first()
        if (projects.isEmpty()) return false
        return db.withTransaction {
            val label = Doing.label(db.labels().all()) ?: return@withTransaction false
            var changed = false
            for (task in db.tasks().open().filter { it.projectId in projects }) {
                val names = db.taskLabels().forTask(task.id)
                val pending = db.pendingChanges().forTask(task.id)
                val fields = pending?.let { PendingChanges.decode(it.fields) }
                val queuedDoing = fields?.get(TaskRepository.LABELS)?.jsonArray?.any {
                    it.jsonPrimitive.contentOrNull?.equals(label.title, ignoreCase = true) == true
                } == true
                val activeIntent = fields?.get(TaskRepository.DOING)?.jsonObject?.get("active")?.jsonPrimitive?.booleanOrNull == true
                if (!Doing.contains(names, label) && !queuedDoing && !activeIntent) continue
                db.taskLabels().set(task.id, names.filterNot { it.equals(label.title, ignoreCase = true) })
                val cleanup = buildJsonObject {
                    put(TaskRepository.DOING, buildJsonObject { put("id", label.id); put("active", false); put("required", true) })
                    fields?.get(TaskRepository.LABELS)?.jsonArray?.let { queued ->
                        put(TaskRepository.LABELS, JsonArray(queued.filterNot { it.jsonPrimitive.contentOrNull?.equals(label.title, ignoreCase = true) == true }))
                    }
                }
                val updated = PendingChanges.merge(pending, task.id, cleanup)
                if (pending == null) db.pendingChanges().insert(updated) else db.pendingChanges().update(updated)
                changed = true
            }
            changed
        }
    }
}
