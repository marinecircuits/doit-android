package com.marinecircuits.doit.data.remote

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VikunjaDatesTest {
    @Test
    fun parsesUtc() {
        assertEquals(Instant.parse("2026-10-26T12:00:00Z"), VikunjaDates.parse("2026-10-26T12:00:00Z"))
    }

    @Test
    fun parsesOffsetsAndFractions() {
        assertEquals(Instant.parse("2026-10-26T12:00:00Z"), VikunjaDates.parse("2026-10-26T14:00:00+02:00"))
        assertEquals(Instant.parse("2026-10-26T12:00:00.5Z"), VikunjaDates.parse("2026-10-26T12:00:00.5Z"))
    }

    @Test
    fun zeroTimeMeansNoDate() {
        assertNull(VikunjaDates.parse("0001-01-01T00:00:00Z"))
    }

    @Test
    fun missingOrGarbageMeansNoDate() {
        assertNull(VikunjaDates.parse(null))
        assertNull(VikunjaDates.parse(""))
        assertNull(VikunjaDates.parse("tomorrow"))
    }

    @Test
    fun formatsWholeSecondsInUtc() {
        assertEquals("2026-10-26T12:00:00Z", VikunjaDates.format(Instant.parse("2026-10-26T12:00:00.123Z")))
    }

    @Test
    fun decodesTaskWithNullsAndUnknownFields() {
        val task = VikunjaJson.decodeFromString(
            TaskDto.serializer(),
            """{"id":7,"title":"Pay invoice","done":false,"due_date":"0001-01-01T00:00:00Z",
               "project_id":3,"labels":null,"description":null,"assignees":[],"new_field":{"x":1}}""",
        )
        assertEquals(7, task.id)
        assertEquals("Pay invoice", task.title)
        assertEquals(3, task.projectId)
        assertNull(VikunjaDates.parse(task.dueDate))
    }
}
