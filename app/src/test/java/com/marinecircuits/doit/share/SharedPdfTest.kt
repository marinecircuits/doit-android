package com.marinecircuits.doit.share

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SharedPdfTest {
    @Test
    fun displayNameBecomesAnEditableTitleAndKeepsItsExtension() {
        assertEquals(
            SharedPdf("Flight report.PDF", "Flight report"),
            SharedPdf.fromDisplayName("Downloads/Flight report.PDF"),
        )
    }

    @Test
    fun missingOrOddNamesGetSafeDefaults() {
        assertEquals(SharedPdf("document.pdf", "document"), SharedPdf.fromDisplayName(null))
        assertEquals(SharedPdf("document.pdf", "document"), SharedPdf.fromDisplayName(".pdf"))
        assertEquals(SharedPdf("document.pdf", "document"), SharedPdf.fromDisplayName("../"))
        assertEquals(SharedPdf("notes.pdf", "notes"), SharedPdf.fromDisplayName("notes"))
    }

    @Test
    fun readsAValidSharedStream() {
        val bytes = byteArrayOf(37, 80, 68, 70, 45, 49)
        assertArrayEquals(bytes, SharedPdf.read(ByteArrayInputStream(bytes)))
    }

    @Test
    fun rejectsOversizedSharedStreams() {
        val tooLarge = ByteArray(SharedPdf.MAX_BYTES + 1)
        try {
            SharedPdf.read(ByteArrayInputStream(tooLarge))
            fail("Expected PdfTooLargeException")
        } catch (_: PdfTooLargeException) {
            // Report a useful error instead of exhausting memory for an arbitrarily large share.
        }
    }
}
