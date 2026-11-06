package com.marinecircuits.doit.ui.tasks

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTriggerTest {
    private fun value(text: String, cursor: Int = text.length) = TextFieldValue(text, TextRange(cursor))

    @Test
    fun typedMarkerOffersSchedulingWithoutTakingFocusIntoACalendar() {
        val state = ScheduleState()
        assertEquals(listOf("date", "daily", "weekly", "monthly"), state.suggestions(value("Buy !")).map { it.token })
        assertNull(state.picker)
        assertEquals("weekly", state.suggestions(value("Buy !wee")).single().token)
    }

    @Test
    fun punctuationQuotedNamesQuantitiesAndSelectionRemainLiteral() {
        val state = ScheduleState()
        for (text in listOf("Hello!", "Buy @\"urgent !", "Buy #\"home !", "Milk [!", "/task !")) {
            assertTrue(state.suggestions(value(text)).isEmpty())
        }
        assertTrue(state.suggestions(TextFieldValue("Buy !weekly", TextRange(4, 11))).isEmpty())
    }
}
