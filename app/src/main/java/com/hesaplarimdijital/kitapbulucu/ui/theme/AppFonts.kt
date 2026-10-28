package com.hesaplarimdijital.kitapbulucu.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.hesaplarimdijital.kitapbulucu.R
import desktopkit.shell.WidthClass
import desktopkit.theme.DeskFonts
import desktopkit.theme.DeskTheme
import desktopkit.theme.LocalDeskType
import desktopkit.theme.grow

/**
 * Literata (SIL Open Font License, licenses/Literata-OFL.txt) for book titles, Draft A's serif.
 * Static regular and bold cuts made from the variable font at optical size 14, Latin and
 * Latin Extended-A only (every Turkish letter); about 95 KB each.
 *
 * Text sizes: the kit's shell (0.2.0) sets every style 1 sp larger on tablet and DeX windows
 * (`DesktopShell(largeWindowTextStep = 1f)`), which is what the user asked for on DeX; the app
 * adds nothing of its own inside the shell. Screens drawn outside the shell (setup, column
 * mapping, the book dialogs) get the same step from [LargeWindowText].
 */
object AppFonts {
    val Literata = FontFamily(
        Font(R.font.kb_literata_regular, FontWeight.Normal),
        Font(R.font.kb_literata_bold, FontWeight.Bold),
    )

    /** The kit's IBM Plex for the interface, Literata for page and section headings (Draft A). */
    val desk: DeskFonts = DeskFonts.Plex.copy(heading = Literata)

    /** The kit shell's default `largeWindowTextStep`. */
    const val LARGE_WINDOW_TEXT_STEP = 1f
}

/**
 * For content outside the kit's shell: on tablet and DeX windows every text style is
 * [AppFonts.LARGE_WINDOW_TEXT_STEP] sp larger, as inside the shell; phones keep the kit's sizes.
 * Never put it around the shell itself, or the step would count twice.
 */
@Composable
fun LargeWindowText(widthClass: WidthClass, content: @Composable () -> Unit) {
    if (widthClass == WidthClass.Compact) {
        content()
        return
    }
    val base = DeskTheme.type
    val grown = remember(base) { base.grow(AppFonts.LARGE_WINDOW_TEXT_STEP) }
    CompositionLocalProvider(LocalDeskType provides grown, content = content)
}
