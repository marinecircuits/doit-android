package com.marinecircuits.doit.ui.tasks

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateTriggerTest {
    private fun value(text: String, cursor: Int = text.length) = TextFieldValue(text, TextRange(cursor))

    @Test
    fun consumesTypedMarkerAtAWordBoundaryAndPreservesTheCursor() {
        assertEquals(value("Buy "), DateTrigger.consume(value("Buy "), value("Buy !")))
        assertEquals(value("Buy milk", 4), DateTrigger.consume(value("Buy milk", 4), value("Buy !milk", 5)))
        assertEquals(value(""), DateTrigger.consume(value(""), value("!")))
    }

    @Test
    fun punctuationPastedTextAndQuotedNamesRemainLiteral() {
        assertNull(DateTrigger.consume(value("Hello"), value("Hello!")))
        assertNull(DateTrigger.consume(value("Buy "), value("Buy !3")))
        assertNull(DateTrigger.consume(value("Buy @\"urgent "), value("Buy @\"urgent !")))
        assertNull(DateTrigger.consume(value("Buy #\"home "), value("Buy #\"home !")))
        assertNull(DateTrigger.consume(TextFieldValue("Buy milk", TextRange(4, 8)), value("Buy !")))
    }
}
