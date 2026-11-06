package com.marinecircuits.doit.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskDescriptionsTest {
    @Test
    fun plainTextKeepsWordsAndFindsEachAddressOnce() {
        val description = "See https://example.com/a. and again https://example.com/a then https://example.com/b"
        assertEquals(
            "See https://example.com/a. and again https://example.com/a then https://example.com/b",
            TaskDescriptions.plain(description),
        )
        assertEquals(listOf("https://example.com/a", "https://example.com/b"), TaskDescriptions.links(description))
    }

    @Test
    fun htmlAndMarkdownAddressesAreIconsNotTextLinks() {
        val description = """<p>Notes</p><a href="https://example.com/html">site</a> and [doc](https://example.com/doc)"""
        assertEquals("Notes site and doc", TaskDescriptions.plain(description))
        assertEquals(listOf("https://example.com/html", "https://example.com/doc"), TaskDescriptions.links(description))
    }

    @Test
    fun blankDescriptionHasNothingToShow() {
        assertEquals("", TaskDescriptions.plain("  <p> </p> "))
        assertEquals(emptyList<String>(), TaskDescriptions.links(""))
    }
}
