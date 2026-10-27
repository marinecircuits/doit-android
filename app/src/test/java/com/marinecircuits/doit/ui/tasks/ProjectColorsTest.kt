package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.ui.tasks.ProjectColors.DARK_TEXT
import com.marinecircuits.doit.ui.tasks.ProjectColors.LIGHT_TEXT
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
    fun lightensThirtyPercentTowardsWhite() {
        assertEquals(argb(0x5E9DFF), ProjectColors.lighten(argb(0x1973FF)))
        assertEquals(argb(0x4D4D4D), ProjectColors.lighten(argb(0x000000)))
        assertEquals(argb(0xFFFFFF), ProjectColors.lighten(argb(0xFFFFFF)))
    }

    @Test
    fun darkColourGetsLightText() {
        assertEquals(TileColors(argb(0x4D4DA6), LIGHT_TEXT), ProjectColors.tile("000080"))
    }

    @Test
    fun lightColourGetsDarkText() {
        assertEquals(TileColors(argb(0xFFE14D), DARK_TEXT), ProjectColors.tile("ffd400"))
    }

    @Test
    fun contrastRatioMatchesWcag() {
        assertEquals(21.0, ProjectColors.contrast(argb(0x000000), argb(0xFFFFFF)), 0.01)
        assertEquals(1.0, ProjectColors.contrast(argb(0x1973FF), argb(0x1973FF)), 0.0)
    }
}
