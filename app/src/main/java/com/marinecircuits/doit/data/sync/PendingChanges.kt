package com.marinecircuits.doit.data.sync

import com.marinecircuits.doit.data.local.PendingChangeEntity
import com.marinecircuits.doit.data.remote.VikunjaJson
import kotlinx.serialization.json.JsonObject

object PendingChanges {
    /**
     * Folds new [fields] for [taskId] into its existing pending change, if any. Later values win per field,
     * the kind of an unsent create stays `create`, and every merge bumps the revision.
     */
    fun merge(existing: PendingChangeEntity?, taskId: Long, fields: JsonObject): PendingChangeEntity {
        if (existing == null) {
            return PendingChangeEntity(taskId = taskId, kind = PendingChangeEntity.UPDATE, fields = fields.toString())
        }
        val merged = JsonObject(decode(existing.fields) + fields)
        return existing.copy(fields = merged.toString(), revision = existing.revision + 1)
    }

    fun decode(fields: String): JsonObject = VikunjaJson.decodeFromString(JsonObject.serializer(), fields)
}
