package com.hesaplarimdijital.kitapbulucu.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.hesaplarimdijital.kitapbulucu.R

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
}
