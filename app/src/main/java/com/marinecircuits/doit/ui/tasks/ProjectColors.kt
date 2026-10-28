package com.marinecircuits.doit.ui.tasks

import kotlin.math.pow
import kotlin.math.roundToInt

/** Colours of a tile filled with its project's colour, as ARGB. */
data class TileColors(val container: Int, val content: Int)

/** Turns Vikunja's project colours into tile colours. */
object ProjectColors {
    /**
     * The colours a project can be set to from the app, as six hex digits without a leading #.
     * Saturated enough that the lightened tile and page colours stay distinct.
     */
    val PALETTE = listOf(
        "c62828",
        "ef6c00",
        "f9a825",
        "2e7d32",
        "00897b",
        "1565c0",
        "6a1b9a",
        "ad1457",
    )

    /**
     * How far each channel moves towards white. 0.75 is about twice as bright as 0.55:
     * a black project becomes grey 191 instead of 140.
     */
    const val LIGHTEN = 0.75f
    const val DARK_TEXT = 0xFF1D1B20.toInt()
    const val LIGHT_TEXT = 0xFFFFFFFF.toInt()
    private const val HEX_DIGITS = "0123456789abcdefABCDEF"

    /** The tile colours for a project colour, or null for an empty or unreadable one. */
    fun tile(hexColor: String): TileColors? {
        val container = lighten(parse(hexColor) ?: return null)
        val content = if (contrast(container, DARK_TEXT) >= contrast(container, LIGHT_TEXT)) DARK_TEXT else LIGHT_TEXT
        return TileColors(container, content)
    }

    /** Six or three hex digits, with or without a leading #, as opaque ARGB. */
    fun parse(hexColor: String): Int? {
        val digits = hexColor.trim().removePrefix("#")
        if (!digits.all { it in HEX_DIGITS }) return null
        val rgb = when (digits.length) {
            6 -> digits
            3 -> digits.map { "$it$it" }.joinToString("")
            else -> return null
        }
        return rgb.toInt(16) or 0xFF000000.toInt()
    }

    fun lighten(color: Int): Int {
        fun channel(shift: Int): Int {
            val value = color shr shift and 0xFF
            return (value + (255 - value) * LIGHTEN).roundToInt() shl shift
        }
        return 0xFF000000.toInt() or channel(16) or channel(8) or channel(0)
    }

    /** WCAG contrast ratio, from 1 (same luminance) to 21 (black on white). */
    fun contrast(a: Int, b: Int): Double {
        val (lighter, darker) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = (color shr shift and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
