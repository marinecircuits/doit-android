package com.marinecircuits.doit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecurrenceTest {
    @Test
    fun onlyExactExplicitShortcutsChangeTheSchedule() {
        for (text in listOf("Write weekly report", "Task !weelky", "Task !0d", "Task !10000w", "Task!weekly",
            "https://host/!weekly", "Task \"!weekly\"", "Milk [!weekly]", "Task @\"Review !weekly\"",
            "Task \"unfinished !weekly", "Task [unfinished !weekly")) {
            assertEquals(Recurrence.Parsed(text, null), Recurrence.parse(text))
        }
        assertEquals(Recurrence.Parsed("Plan #Work @home", Recurrence.Rule(Recurrence.WEEK)), Recurrence.parse("Plan !daily #Work !WEEKLY @home"))
        assertEquals(Recurrence.Rule(0, 1), Recurrence.parse("Pay !monthly").rule)
        assertEquals(Recurrence.Rule(2 * Recurrence.WEEK), Recurrence.parse("Clean !2w").rule)
        assertEquals(Recurrence.Rule(3 * Recurrence.DAY), Recurrence.parse("Water !3d").rule)
    }

    @Test
    fun partialAndMisspelledShortcutsOfferExplicitChoices() {
        assertEquals("weekly", Recurrence.suggestions("wee").single().token)
        assertEquals("Did you mean !weekly?", Recurrence.suggestions("weelky").single().title)
        assertEquals(Recurrence.Typing(5, "wee"), Recurrence.typing("Task !wee", 9))
        for (text in listOf("Task \"!wee", "Milk [!wee", "Task!wee", "Task !weekly ")) assertNull(Recurrence.typing(text, text.length))
    }
}
