package com.marinecircuits.doit.data

import androidx.room.withTransaction
import com.marinecircuits.doit.data.local.DoitDatabase
import com.marinecircuits.doit.data.local.LabelEntity
import com.marinecircuits.doit.data.remote.VikunjaApi
import com.marinecircuits.doit.data.sync.PendingChanges
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*

/** Online-only label administration. The caller holds the sync mutex for the whole operation. */
class LabelManagement(private val db: DoitDatabase, private val settings: SettingsStore, private val api: VikunjaApi) {
    suspend fun save(id: Long?, title: String, color: String) {
        val name = title.trim()
        val hex = color.trim().removePrefix("#").lowercase()
        require(name.isNotEmpty() && '\n' !in name) { "Enter a label name" }
        require(hex.isEmpty() || hex.matches(Regex("[0-9a-f]{6}"))) { "Use a six-digit hex colour, or leave it blank" }
        val labels = db.labels().all()
        require(labels.none { it.id != id && it.title.equals(name, true) }) { "A label with that name already exists" }
        val old = id?.let { wanted -> labels.firstOrNull { it.id == wanted } ?: error("Label is no longer available") }
        val saved = if (id == null) api.createLabel(name, hex) else api.updateLabel(id, name, hex)
        check(saved.id > 0 && (id == null || saved.id == id) && saved.title == name) { "Unexpected label response; refresh before retrying" }
        db.withTransaction {
            if (old != null && old.title != saved.title) rewriteReferences(old, saved.title)
            db.labels().upsert(LabelEntity(saved.id, saved.title, saved.hexColor))
        }
    }

    suspend fun delete(id: Long) {
        val old = db.labels().all().firstOrNull { it.id == id } ?: return
        api.deleteLabel(id) // No local changes on permission, connection or server errors.
        db.withTransaction {
            rewriteReferences(old, null)
            db.labels().delete(id)
        }
        settings.forgetLabel(id)
    }

    /** Re-read after the network call: edits made while it was in flight must also be migrated. */
    private suspend fun rewriteReferences(old: LabelEntity, replacement: String?) {
        fun names(values: List<String>) = values.mapNotNull { if (it == old.title) replacement else it }.distinct()
        fun fields(raw: String): String {
            val fields = PendingChanges.decode(raw).toMutableMap()
            fields[TaskRepository.LABELS]?.jsonArray?.let { value ->
                val updated = names(value.map { it.jsonPrimitive.content })
                if (updated.isEmpty()) fields.remove(TaskRepository.LABELS)
                else fields[TaskRepository.LABELS] = JsonArray(updated.map(::JsonPrimitive))
            }
            if (replacement == null) {
                fields[TaskRepository.LABEL_REMOVALS]?.jsonArray?.let { value ->
                    val updated = value.filterNot { it.jsonPrimitive.longOrNull == old.id }
                    if (updated.isEmpty()) fields.remove(TaskRepository.LABEL_REMOVALS)
                    else fields[TaskRepository.LABEL_REMOVALS] = JsonArray(updated)
                }
                if (fields[TaskRepository.DOING]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull == old.id)
                    fields.remove(TaskRepository.DOING)
            }
            return JsonObject(fields).toString()
        }
        for ((id, rows) in db.taskLabels().observeAll().first().groupBy { it.taskId }) {
            if (rows.any { it.title == old.title }) db.taskLabels().set(id, names(rows.map { it.title }))
        }
        for (pending in db.pendingChanges().all()) {
            val updated = fields(pending.fields)
            if (updated == pending.fields) continue
            if (updated == "{}" && pending.kind == "update") db.pendingChanges().delete(pending.seq)
            else db.pendingChanges().update(pending.copy(fields = updated, revision = pending.revision + 1))
        }
        for (history in db.taskHistory().observeAll().first()) {
            val updatedNames = names(Json.parseToJsonElement(history.labelsJson).jsonArray.map { it.jsonPrimitive.content })
            db.taskHistory().update(history.copy(labelsJson = JsonArray(updatedNames.map(::JsonPrimitive)).toString(),
                pendingJson = fields(history.pendingJson)))
        }
    }
}
