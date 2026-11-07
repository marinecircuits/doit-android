package com.marinecircuits.doit.ui.tasks

import com.marinecircuits.doit.ui.tasks.ProjectColors.DARK_TEXT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun paletteColoursStayDistinctOnceLightened() {
        assertEquals(8, ProjectColors.PALETTE.size)
        assertTrue(ProjectColors.PALETTE.all { ProjectColors.parse(it) != null && it.length == 6 })
        val containers = ProjectColors.PALETTE.map { ProjectColors.tile(it)!!.container }
        assertEquals(containers.size, containers.distinct().size)
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

    @Test
    fun darkTilesAndPagesKeepReadableTextEvenAtColourExtremes() {
        (ProjectColors.PALETTE + listOf("000000", "ffffff", "00ff00", "0000ff", "ffff00")).forEach { hex ->
            val tile = ProjectColors.tile(hex, dark = true)!!
            val page = ProjectColors.page(hex, dark = true)!!
            assertTrue("Tile text on $hex", ProjectColors.contrast(tile.container, tile.content) >= 4.5)
            assertTrue("Muted tile count on $hex", ProjectColors.contrast(tile.container, muted(tile.content, tile.container)) >= 4.5)
            assertTrue("Page text on $hex", ProjectColors.contrast(page.container, page.content) >= 4.5)
            assertTrue("Page is darker than its tile", ProjectColors.contrast(page.container, 0xFFFFFFFF.toInt()) >
                ProjectColors.contrast(tile.container, 0xFFFFFFFF.toInt()))
        }
        assertEquals(ProjectColors.PALETTE.size,
            ProjectColors.PALETTE.map { ProjectColors.tile(it, dark = true)!!.container }.distinct().size)
        assertNull(ProjectColors.tile("invalid", dark = true))
        assertNull(ProjectColors.page("", dark = true))
    }

    private fun muted(foreground: Int, background: Int): Int {
        fun channel(shift: Int) = (((foreground shr shift and 0xFF) * 0.82f +
            (background shr shift and 0xFF) * 0.18f).toInt() shl shift)
        return argb(channel(16) or channel(8) or channel(0))
    }
}
