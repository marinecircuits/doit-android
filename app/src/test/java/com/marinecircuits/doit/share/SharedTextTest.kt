package com.marinecircuits.doit.share

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTextTest {
    @Test
    fun webPageUsesSubjectAsTitleAndUrlAsDescription() {
        assertEquals(
            SharedTask("Rust book", "https://doc.rust-lang.org/book/"),
            SharedText.propose("Rust book", "https://doc.rust-lang.org/book/"),
        )
    }

    @Test
    fun plainTextBecomesTheTitle() {
        assertEquals(
            SharedTask("Pick up parcel at post office", null),
            SharedText.propose(null, "  Pick up parcel at post office \n"),
        )
    }

    @Test
    fun furtherLinesBecomeTheDescription() {
        assertEquals(
            SharedTask("Call the garage", "About the winter tyres\nAsk for Tuesday"),
            SharedText.propose("", "\nCall the garage\nAbout the winter tyres\nAsk for Tuesday"),
        )
    }

    @Test
    fun subjectRepeatedInTextIsNotADescription() {
        assertEquals(SharedTask("Same", null), SharedText.propose("Same", "Same"))
    }

    @Test
    fun nothingSharedGivesAnEmptyTitle() {
        assertEquals(SharedTask("", null), SharedText.propose(null, null))
    }
}
