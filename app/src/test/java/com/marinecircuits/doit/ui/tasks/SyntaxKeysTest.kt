package com.marinecircuits.doit.ui.tasks

import org.junit.Assert.assertEquals
import org.junit.Test

class SyntaxKeysTest {
    @Test
    fun insertsAtTheCursorAndSeparatesNeighbouringWords() {
        assertEquals("Buy milk @" to 10, SyntaxKeys.insert("Buy milk", 8, 8, "@"))
        assertEquals("@ Buy milk" to 1, SyntaxKeys.insert("Buy milk", 0, 0, "@"))
        assertEquals("Buy @ milk" to 5, SyntaxKeys.insert("Buy milk", 3, 3, "@"))
        assertEquals("Buy @ milk" to 5, SyntaxKeys.insert("Buy milk", 4, 4, "@"))
    }

    @Test
    fun replacesTheSelection() {
        assertEquals("Buy @ please" to 5, SyntaxKeys.insert("Buy milk please", 4, 8, "@"))
    }
}
