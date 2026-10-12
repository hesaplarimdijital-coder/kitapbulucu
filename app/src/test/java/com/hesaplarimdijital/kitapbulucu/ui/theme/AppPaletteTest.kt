package com.hesaplarimdijital.kitapbulucu.ui.theme

import androidx.compose.ui.graphics.Color
import desktopkit.theme.Accent
import desktopkit.theme.DeskColors
import desktopkit.theme.DeskPalette
import desktopkit.theme.ThemeMode
import desktopkit.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text/background pair of every theme × accent keeps WCAG AA (4.5:1). */
class AppPaletteTest {

    private val modes = listOf(ThemeMode.Light, ThemeMode.Dark, ThemeMode.Oled)

    private fun palettes(): List<Pair<String, DeskColors>> = modes.flatMap { mode ->
        AppPalette.accents.map { accent -> "$mode/${accent.name}" to AppPalette.warm(DeskPalette.colors(mode, systemDark = false, accent = accent), accent) }
    }

    /** The pairs the app draws, the same list docs/design/PALETTE.md was built from. */
    private fun pairs(c: DeskColors): List<Triple<String, Color, Color>> {
        val surfaces = listOf("background" to c.background, "chrome" to c.chrome, "surface" to c.surface, "raised" to c.surfaceRaised, "hover" to c.hover)
        val texts = listOf("text" to c.text, "secondary" to c.textSecondary, "tertiary" to c.textTertiary, "accentText" to c.accentText)
        val pairs = ArrayList<Triple<String, Color, Color>>()
        for ((t, fg) in texts) for ((s, bg) in surfaces) pairs.add(Triple("$t on $s", fg, bg))
        pairs.add(Triple("onAccent on accent", c.onAccent, c.accent))
        pairs.add(Triple("onSelection on selection", c.onSelection, c.selection))
        pairs.add(Triple("secondary on selection", c.textSecondary, c.selection))
        pairs.add(Triple("tertiary on selection", c.textTertiary, c.selection))
        pairs.add(Triple("accentText on selection", c.accentText, c.selection))
        for ((name, color) in listOf("success" to c.success, "warning" to c.warning, "danger" to c.danger)) {
            for ((s, bg) in surfaces.take(4)) pairs.add(Triple("$name on $s", color, bg))
        }
        return pairs
    }

    @Test
    fun everyPairKeepsContrast() {
        val failures = ArrayList<String>()
        var lowest = 21.0
        for ((name, colors) in palettes()) {
            for ((label, fg, bg) in pairs(colors)) {
                val ratio = contrastRatio(fg, bg)
                if (ratio < lowest) lowest = ratio
                if (ratio < 4.5) failures.add("%s %s: %.2f:1".format(name, label, ratio))
            }
        }
        println("lowest contrast across ${palettes().size} palettes: %.2f:1".format(lowest))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun surfacesAreTheDraftAOnes() {
        val light = AppPalette.warm(DeskPalette.colors(ThemeMode.Light, systemDark = false, accent = AppPalette.Bordo), AppPalette.Bordo)
        assertEquals(Color(0xFFF3ECDD), light.background)
        assertEquals(Color(0xFF2A2018), light.text)
        assertTrue(contrastRatio(light.accent, Color.White) >= 4.5) // a deep bordo that carries white text
        assertTrue(!light.isDark)

        val dark = AppPalette.warm(DeskPalette.colors(ThemeMode.Dark, systemDark = false, accent = AppPalette.Bordo), AppPalette.Bordo)
        assertEquals(Color(0xFF1E1915), dark.background)
        assertEquals(Color(0xFFF0E8DA), dark.text)
        assertTrue(dark.isDark)

        val oled = AppPalette.warm(DeskPalette.colors(ThemeMode.Oled, systemDark = false, accent = AppPalette.Bordo), AppPalette.Bordo)
        assertEquals(Color.Black, oled.background)
        assertEquals(Color.Black, oled.chrome)
        assertEquals(dark.text, oled.text)

        val system = AppPalette.warm(DeskPalette.colors(ThemeMode.System, systemDark = true, accent = AppPalette.Bordo), AppPalette.Bordo)
        assertEquals(dark.background, system.background)
    }

    @Test
    fun offersTheFourAppAccentsThenTheKitPresets() {
        assertEquals(listOf("Bordo", "Mürekkep laciverti", "Zeytin yeşili", "Eskitilmiş altın", "Blue", "Amber", "Green", "Coral"), AppPalette.accents.map { it.name })
        assertEquals(AppPalette.Bordo, Accent.fromName("Bordo", extra = AppPalette.accents))
        assertEquals(8, AppPalette.accents.map { it.base }.toSet().size)
    }
}
