package com.marinecircuits.doit.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object DueDates {
    /** Time of day given to a task that gets its first due date. */
    val DEFAULT_TIME: LocalTime = LocalTime.NOON

    /** Moves a due date to [date], keeping the current time of day, or [DEFAULT_TIME] for an undated task. */
    fun withDate(current: Instant?, date: LocalDate, zone: ZoneId): Instant {
        val time = current?.atZone(zone)?.toLocalTime() ?: DEFAULT_TIME
        return date.atTime(time).atZone(zone).toInstant()
    }

    fun localDate(epochMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** "Next week" in the quick reschedule sheet: the coming Monday, never today. */
    fun nextMonday(today: LocalDate): LocalDate = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
}
