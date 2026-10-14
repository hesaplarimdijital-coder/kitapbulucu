package com.hesaplarimdijital.kitapbulucu.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.hesaplarimdijital.kitapbulucu.R
import desktopkit.theme.DeskFonts
import desktopkit.theme.DeskType
import desktopkit.theme.InputMode

/**
 * Literata (SIL Open Font License, licenses/Literata-OFL.txt) for book titles, Draft A's serif.
 * Static regular and bold cuts made from the variable font at optical size 14, Latin and
 * Latin Extended-A only (every Turkish letter); about 95 KB each.
 */
object AppFonts {
    val Literata = FontFamily(
        Font(R.font.kb_literata_regular, FontWeight.Normal),
        Font(R.font.kb_literata_bold, FontWeight.Bold),
    )

    /** The kit's IBM Plex for the interface, Literata for page and section headings (Draft A). */
    val desk: DeskFonts = DeskFonts.Plex.copy(heading = Literata)

    /** Keyboard and mouse: every style 1 sp larger than the kit's (the user found them small); touch as the kit has it. */
    val type: (InputMode, DeskFonts) -> DeskType = { mode, fonts -> DeskType.forMode(mode, fonts, pointerStep = 1f) }
}
