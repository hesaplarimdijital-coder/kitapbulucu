package com.hesaplarimdijital.kitapbulucu.ui.theme

import androidx.compose.ui.graphics.Color
import desktopkit.theme.Accent
import desktopkit.theme.DeskColors
import desktopkit.theme.ensureContrast
import desktopkit.theme.mixSrgb

/**
 * Draft A "Fiş" colours (docs/design/PALETTE.md), applied through the kit's `colors` hook:
 * the kit computes its palette for the chosen theme and accent, then the surfaces and text are
 * warmed ("Kâğıt" cream with brown ink, "Gece okuması" warm dark, true black for OLED) and the
 * accent, selection and status colours are re-checked against the new surfaces so every pair
 * keeps at least 4.5:1 (AppPaletteTest).
 */
object AppPalette {
    val Bordo = Accent.custom("Bordo", Color(0xFFC2364B))
    val Laciverti = Accent.custom("Mürekkep laciverti", Color(0xFF3F6CC4))
    val Zeytin = Accent.custom("Zeytin yeşili", Color(0xFF93A847))
    val Altin = Accent.custom("Eskitilmiş altın", Color(0xFFD9A32B))

    /** The app's four accents first, then the kit's presets; the first one is the default. */
    val accents: List<Accent> = listOf(Bordo, Laciverti, Zeytin, Altin) + Accent.presets

    /** Surfaces and text of one theme, before the accent is worked in. */
    class Surfaces(
        val background: Color,
        val chrome: Color,
        val surface: Color,
        val surfaceRaised: Color,
        val hover: Color,
        val pressed: Color,
        val border: Color,
        val borderStrong: Color,
        val text: Color,
        val textSecondary: Color,
        val textTertiary: Color,
        val success: Color,
        val warning: Color,
        val danger: Color,
        val scrim: Color,
    ) {
        val textSurfaces: List<Color> get() = listOf(background, chrome, surface, surfaceRaised, hover)
    }

    /** "Kâğıt": cream paper, brown ink. */
    val Paper = Surfaces(
        background = Color(0xFFF3ECDD), chrome = Color(0xFFEBE2CF), surface = Color(0xFFFBF7EE), surfaceRaised = Color(0xFFFFFCF5),
        hover = Color(0xFFE6DCC7), pressed = Color(0xFFDBD0B9), border = Color(0xFFDDD2BC), borderStrong = Color(0xFFC9BCA2),
        text = Color(0xFF2A2018), textSecondary = Color(0xFF4E4034), textTertiary = Color(0xFF63554A),
        success = Color(0xFF2F6B3F), warning = Color(0xFF7A5510), danger = Color(0xFFA3312A), scrim = Color(0x662A2018),
    )

    /** "Gece okuması": warm dark, like a lamp-lit page. */
    val Night = Surfaces(
        background = Color(0xFF1E1915), chrome = Color(0xFF171310), surface = Color(0xFF27211B), surfaceRaised = Color(0xFF302921),
        hover = Color(0xFF372F27), pressed = Color(0xFF41382E), border = Color(0xFF342C25), borderStrong = Color(0xFF43392F),
        text = Color(0xFFF0E8DA), textSecondary = Color(0xFFD3C8B6), textTertiary = Color(0xFFAFA28F),
        success = Color(0xFF7FCB93), warning = Color(0xFFE3B45C), danger = Color(0xFFF08A7C), scrim = Color(0x99000000),
    )

    /** OLED: true black behind everything, the same warm ink on barely raised surfaces. */
    val Oled = Surfaces(
        background = Color(0xFF000000), chrome = Color(0xFF000000), surface = Color(0xFF0E0B09), surfaceRaised = Color(0xFF1A1511),
        hover = Color(0xFF221C17), pressed = Color(0xFF2B241E), border = Color(0xFF1E1915), borderStrong = Color(0xFF2E2721),
        text = Color(0xFFF0E8DA), textSecondary = Color(0xFFD3C8B6), textTertiary = Color(0xFFAFA28F),
        success = Color(0xFF7FCB93), warning = Color(0xFFE3B45C), danger = Color(0xFFF08A7C), scrim = Color(0xB3000000),
    )

    /** The `colors = { … }` hook: [kit] is what the kit computed for the current theme and [accent]. */
    fun warm(kit: DeskColors, accent: Accent): DeskColors {
        val isOled = kit.isDark && kit.background == Color.Black
        val base = if (!kit.isDark) Paper else if (isOled) Oled else Night
        var tone = kit.accent
        for (surface in base.textSurfaces) tone = ensureContrast(tone, surface, lighten = kit.isDark)
        val onAccent: Color
        val selection: Color
        val onSelection: Color
        if (kit.isDark) {
            onAccent = ensureContrast(base.chrome, tone, lighten = false)
            selection = ensureContrast(mixSrgb(accent.base, base.background, 0.20f), base.textTertiary, lighten = false)
            onSelection = ensureContrast(mixSrgb(accent.base, Color.White, 0.3f), selection, lighten = true)
        } else {
            onAccent = Color.White
            selection = ensureContrast(mixSrgb(accent.base, base.surface, 0.20f), base.textTertiary, lighten = true)
            onSelection = base.text
        }
        return kit.copy(
            background = base.background,
            chrome = base.chrome,
            surface = base.surface,
            surfaceRaised = base.surfaceRaised,
            hover = base.hover,
            pressed = base.pressed,
            border = base.border,
            borderStrong = base.borderStrong,
            text = base.text,
            textSecondary = base.textSecondary,
            textTertiary = base.textTertiary,
            accent = tone,
            onAccent = onAccent,
            accentText = tone,
            selection = selection,
            onSelection = onSelection,
            focusRing = tone,
            success = base.success,
            warning = base.warning,
            danger = base.danger,
            brandSecondary = tone,
            scrim = base.scrim,
        )
    }
}
