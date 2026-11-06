package com.marinecircuits.doit.ui.tasks

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.marinecircuits.doit.data.Recurrence
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ScheduleStateTest {
    @Test
    fun selectingWithoutDateWaitsForConfirmationAndCancelPreservesText() {
        val state = ScheduleState()
        var value = TextFieldValue("Water !wee", TextRange(10))
        state.changeText = { value = it }
        state.accept("weekly", value)
        assertEquals("repeat", state.picker)
        assertFalse(state.rule.active)
        assertEquals("Water !wee", value.text)
        state.cancel()
        assertEquals("Water !wee", value.text)
        state.accept("weekly", value)
        state.finish(state.proposedRule, LocalDate.of(2027, 1, 5))
        assertEquals("Water", value.text)
        assertEquals(Recurrence.Rule(Recurrence.WEEK), state.rule)
        assertEquals(LocalDate.of(2027, 1, 5), state.day)
    }

    @Test
    fun multipleTokensBecomeOneChipBeforeCaptureAndCanBeRemoved() {
        val state = ScheduleState(LocalDate.of(2027, 1, 5))
        var value = TextFieldValue("Water !daily !3d")
        state.changeText = { value = it }
        assertFalse(state.prepare(value))
        assertEquals("Water", value.text)
        assertEquals(Recurrence.Rule(3 * Recurrence.DAY), state.rule)
        assertTrue(state.prepare(value))
        state.chooseRule(Recurrence.Rule())
        assertFalse(state.rule.active)
        assertNotNull(state.day)
    }
}
