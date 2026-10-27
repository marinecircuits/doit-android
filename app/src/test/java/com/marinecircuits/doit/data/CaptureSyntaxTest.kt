package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.CaptureSyntax.Completion
import com.marinecircuits.doit.data.CaptureSyntax.Kind
import com.marinecircuits.doit.data.CaptureSyntax.Parsed
import com.marinecircuits.doit.data.CaptureSyntax.Typing
import com.marinecircuits.doit.data.local.LabelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureSyntaxTest {
    private val known = listOf(LabelEntity(7, "Errand"), LabelEntity(8, "home"), LabelEntity(9, "15 min"))

    private fun parse(text: String) = CaptureSyntax.parse(text, known)

    @Test
    fun labelsLeaveTheTitle() {
        assertEquals(Parsed("Order capacitors tomorrow", listOf("Errand")), parse("Order capacitors @errand tomorrow"))
    }

    @Test
    fun quotedAndLeadingLabels() {
        assertEquals(Parsed("Call bank", listOf("home", "15 min")), parse("""@home Call bank @"15 min""""))
    }

    @Test
    fun unknownNamesStayText() {
        assertEquals(Parsed("Call @mum about", listOf("home")), parse("Call @mum about @home"))
    }

    @Test
    fun atSignsThatAreNotLabels() {
        val text = "mail bob@example.com about the meeting @ 5pm"
        assertEquals(Parsed(text, emptyList()), parse(text))
    }

    @Test
    fun otherSyntaxStaysPlain() {
        assertEquals(Parsed("call +Bob tomorrow !3 *x", emptyList()), parse("call +Bob tomorrow !3 *x"))
    }

    @Test
    fun duplicatesIgnoreCase() {
        assertEquals(Parsed("x", listOf("Errand")), parse("x @Errand @errand"))
    }

    @Test
    fun onlyLabelsLeaveNoTitle() {
        assertEquals(Parsed("", listOf("Errand")), parse("  @errand "))
    }

    @Test
    fun onlyAKnownLabelIsADestination() {
        assertEquals(CaptureSyntax.Destination.Label(7), CaptureSyntax.destination("@errand", known))
        assertEquals(CaptureSyntax.Destination.Label(7), CaptureSyntax.destination("  @Errand  ", known))
        assertEquals(CaptureSyntax.Destination.Label(9), CaptureSyntax.destination("@\"15 min\"", known))
        assertNull(CaptureSyntax.destination("@missing", known))
        assertNull(CaptureSyntax.destination("@errand tomorrow", known))
        assertNull(CaptureSyntax.destination("@\"15 min", known))
    }

    @Test
    fun withoutKnownLabelsNothingIsParsed() {
        assertEquals(Parsed("Buy milk @errand", emptyList()), CaptureSyntax.parse("Buy milk @errand", emptyList()))
    }

    @Test
    fun labelBeingTyped() {
        assertEquals(Typing(9, "er", quoted = false, kind = Kind.LABEL), CaptureSyntax.typing("Buy milk @er"))
        assertEquals(Typing(0, "", quoted = false, kind = Kind.LABEL), CaptureSyntax.typing("@"))
        assertEquals(Typing(5, "15 m", quoted = true, kind = Kind.LABEL), CaptureSyntax.typing("""Call @"15 m"""))
    }

    @Test
    fun notALabelBeingTyped() {
        assertNull(CaptureSyntax.typing("mail bob@exa"))
        assertNull(CaptureSyntax.typing("@errand "))
    }

    private val all = listOf(
        LabelEntity(1, "Errand"),
        LabelEntity(2, "phone"),
        LabelEntity(3, "Waiting for reply"),
        LabelEntity(4, "Repair"),
    )

    @Test
    fun prefixMatchesFirstThenContains() {
        assertEquals(listOf("Repair", "Errand", "Waiting for reply"), CaptureSyntax.suggestionsAt("@r", 2, all).map { it.title })
    }

    @Test
    fun emptyQueryListsEveryLabel() {
        assertEquals(4, CaptureSyntax.suggestionsAt("@", 1, all).size)
        val many = (1L..8L).map { LabelEntity(it, "label $it") }
        assertEquals(many.map { it.title }, CaptureSyntax.suggestionsAt("@", 1, many).map { it.title })
    }

    @Test
    fun suggestionsOnlyWhileTyping() {
        assertEquals(listOf("phone"), CaptureSyntax.suggestionsAt("Call @ph", 8, all).map { it.title })
        assertEquals(emptyList<CaptureSyntax.Suggestion>(), CaptureSyntax.suggestionsAt("Call @ph", 4, all))
        assertEquals(emptyList<CaptureSyntax.Suggestion>(), CaptureSyntax.suggestionsAt("Ask @zz", 7, all))
    }

    @Test
    fun tokens() {
        assertEquals("@errand ", CaptureSyntax.token("errand", Kind.LABEL))
        assertEquals("@\"15 min\" ", CaptureSyntax.token("15 min", Kind.LABEL))
    }

    @Test
    fun completionReplacesThePartialName() {
        assertEquals(Completion("Buy filament @errand ", 21), CaptureSyntax.complete("Buy filament @er", 16, "errand"))
        assertEquals(
            Completion("Call bank @\"15 min\" ", 20),
            CaptureSyntax.complete("Call bank @15", 13, "15 min"),
        )
        // In the middle of the text: no double space after the name.
        assertEquals(Completion("@errand buy", 8), CaptureSyntax.complete("@er buy", 3, "errand"))
        assertNull(CaptureSyntax.complete("no label", 8, "errand"))
    }
}
