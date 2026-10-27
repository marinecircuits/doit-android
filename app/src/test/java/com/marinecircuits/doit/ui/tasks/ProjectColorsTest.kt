package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.ui.tasks.ProjectColors.DARK_TEXT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectColorsTest {
    private fun argb(rgb: Int) = rgb or 0xFF000000.toInt()

    @Test
    fun parsesSixDigitsWithOrWithoutHash() {
        assertEquals(argb(0x1973FF), ProjectColors.parse("1973ff"))
        assertEquals(argb(0x1973FF), ProjectColors.parse("#1973FF"))
    }

    @Test
    fun expandsThreeDigits() {
        assertEquals(argb(0xFFCC00), ProjectColors.parse("fc0"))
    }

    @Test
    fun noColourForEmptyOrUnreadableValues() {
        listOf("", "#", "12345", "1973ffa", "zzzzzz", "e8e8e").forEach { assertNull(it, ProjectColors.tile(it)) }
    }

    @Test
    fun lightensTowardsWhite() {
        assertEquals(argb(0x98C0FF), ProjectColors.lighten(argb(0x1973FF)))
        assertEquals(argb(0x8C8C8C), ProjectColors.lighten(argb(0x000000)))
        assertEquals(argb(0xFFFFFF), ProjectColors.lighten(argb(0xFFFFFF)))
    }

    @Test
    fun paleNavyGetsDarkText() {
        assertEquals(TileColors(argb(0x8C8CC6), DARK_TEXT), ProjectColors.tile("000080"))
    }

    @Test
    fun lightColourGetsDarkText() {
        assertEquals(TileColors(argb(0xFFEC8C), DARK_TEXT), ProjectColors.tile("ffd400"))
    }

    @Test
    fun contrastRatioMatchesWcag() {
        assertEquals(21.0, ProjectColors.contrast(argb(0x000000), argb(0xFFFFFF)), 0.01)
        assertEquals(1.0, ProjectColors.contrast(argb(0x1973FF), argb(0x1973FF)), 0.0)
    }
}
