package com.marinecircuits.doit.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {
    @Test fun returnsTheEntireMatchingReleaseAndNoOtherRelease() {
        val log = """## 1.2.3 (2026-10-28)

### Feat

- one
- two

### Fix

- three

## 1.2.2 (2026-10-27)

- old
"""
        assertEquals("### Feat\n\n- one\n- two\n\n### Fix\n\n- three", ReleaseNotes.forVersion(log, "1.2.3"))
        assertEquals("- old", ReleaseNotes.forVersion(log, "1.2.2"))
        assertEquals("", ReleaseNotes.forVersion(log, "1.2"))
    }
}
