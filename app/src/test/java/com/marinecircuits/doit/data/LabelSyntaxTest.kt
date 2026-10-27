package com.marinecircuits.doit.data

import com.marinecircuits.doit.data.LabelSyntax.Completion
import com.marinecircuits.doit.data.LabelSyntax.Parsed
import com.marinecircuits.doit.data.LabelSyntax.Typing
import com.marinecircuits.doit.data.local.LabelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LabelSyntaxTest {
    private val known = listOf(LabelEntity(7, "Errand"), LabelEntity(8, "home"), LabelEntity(9, "15 min"))

    private fun parse(text: String) = LabelSyntax.parse(text, known)

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
    fun withoutKnownLabelsNothingIsParsed() {
        assertEquals(Parsed("Buy milk @errand", emptyList()), LabelSyntax.parse("Buy milk @errand", emptyList()))
    }

    @Test
    fun labelBeingTyped() {
        assertEquals(Typing(9, "er", quoted = false), LabelSyntax.typing("Buy milk @er"))
        assertEquals(Typing(0, "", quoted = false), LabelSyntax.typing("@"))
        assertEquals(Typing(5, "15 m", quoted = true), LabelSyntax.typing("""Call @"15 m"""))
    }

    @Test
    fun notALabelBeingTyped() {
        assertNull(LabelSyntax.typing("mail bob@exa"))
        assertNull(LabelSyntax.typing("@errand "))
    }

    private val all = listOf(
        LabelEntity(1, "Errand"),
        LabelEntity(2, "phone"),
        LabelEntity(3, "Waiting for reply"),
        LabelEntity(4, "Repair"),
    )

    @Test
    fun prefixMatchesFirstThenContains() {
        assertEquals(listOf("Repair", "Errand", "Waiting for reply"), LabelSyntax.suggest(all, "r").map { it.title })
    }

    @Test
    fun emptyQueryListsAllUpToFive() {
        assertEquals(4, LabelSyntax.suggest(all, "").size)
        val many = (1L..8L).map { LabelEntity(it, "label $it") }
        assertEquals(LabelSyntax.SUGGESTIONS, LabelSyntax.suggest(many, "").size)
    }

    @Test
    fun suggestionsOnlyWhileTyping() {
        assertEquals(listOf("phone"), LabelSyntax.suggestionsAt("Call @ph", 8, all).map { it.title })
        assertEquals(emptyList<LabelEntity>(), LabelSyntax.suggestionsAt("Call @ph", 4, all))
        assertEquals(emptyList<LabelEntity>(), LabelSyntax.suggestionsAt("Ask @zz", 7, all))
    }

    @Test
    fun tokens() {
        assertEquals("@errand ", LabelSyntax.token("errand"))
        assertEquals("@\"15 min\" ", LabelSyntax.token("15 min"))
    }

    @Test
    fun completionReplacesThePartialName() {
        assertEquals(Completion("Buy filament @errand ", 21), LabelSyntax.complete("Buy filament @er", 16, "errand"))
        assertEquals(
            Completion("Call bank @\"15 min\" ", 20),
            LabelSyntax.complete("Call bank @15", 13, "15 min"),
        )
        // In the middle of the text: no double space after the name.
        assertEquals(Completion("@errand buy", 8), LabelSyntax.complete("@er buy", 3, "errand"))
        assertNull(LabelSyntax.complete("no label", 8, "errand"))
    }
}
