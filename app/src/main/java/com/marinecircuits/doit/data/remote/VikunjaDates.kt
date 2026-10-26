package com.marinecircuits.doit.data.remote

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** Vikunja sends RFC 3339 timestamps and uses Go's zero time (`0001-01-01T00:00:00Z`) for "no date". */
object VikunjaDates {
    private val noDateBefore: Instant = Instant.parse("1900-01-01T00:00:00Z")

    fun parse(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        val instant = try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            return null
        }
        return instant.takeIf { it >= noDateBefore }
    }

    fun format(instant: Instant): String =
        DateTimeFormatter.ISO_INSTANT.format(instant.truncatedTo(ChronoUnit.SECONDS))
}
