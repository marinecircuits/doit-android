package com.marinecircuits.doit.data.sync

import com.marinecircuits.doit.data.local.PendingChangeEntity
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingChangesTest {
    private fun fields(vararg pairs: Pair<String, Any?>) = JsonObject(
        pairs.associate { (key, value) ->
            key to when (value) {
                null -> JsonNull
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        },
    )

    @Test
    fun firstChangeIsAnUpdate() {
        val change = PendingChanges.merge(null, 5, fields("done" to true))
        assertEquals(PendingChangeEntity.UPDATE, change.kind)
        assertEquals(5L, change.taskId)
        assertEquals(0, change.revision)
        assertEquals("""{"done":true}""", change.fields)
    }

    @Test
    fun laterValuesWinAndRevisionGrows() {
        val first = PendingChanges.merge(null, 5, fields("due_date" to "2026-10-27T11:00:00Z", "title" to "a"))
        val second = PendingChanges.merge(first.copy(seq = 3), 5, fields("due_date" to "2026-11-02T11:00:00Z"))
        assertEquals(3L, second.seq)
        assertEquals(1, second.revision)
        assertEquals(
            fields("due_date" to "2026-11-02T11:00:00Z", "title" to "a"),
            PendingChanges.decode(second.fields),
        )
    }

    @Test
    fun unsentCreateStaysACreate() {
        val create = PendingChangeEntity(seq = 1, taskId = -1, kind = PendingChangeEntity.CREATE, fields = """{"title":"x"}""")
        val merged = PendingChanges.merge(create, -1, fields("done" to true))
        assertEquals(PendingChangeEntity.CREATE, merged.kind)
        assertEquals(fields("title" to "x", "done" to true), PendingChanges.decode(merged.fields))
    }

    @Test
    fun completeThenUndoCollapsesIntoOneHarmlessUpdate() {
        val complete = PendingChanges.merge(null, 7, fields("done" to true)).copy(seq = 1)
        val undo = PendingChanges.merge(complete, 7, fields("done" to false, "due_date" to "2026-10-26T11:00:00Z"))
        assertEquals(1L, undo.seq)
        assertEquals(
            fields("done" to false, "due_date" to "2026-10-26T11:00:00Z"),
            PendingChanges.decode(undo.fields),
        )
    }
}
